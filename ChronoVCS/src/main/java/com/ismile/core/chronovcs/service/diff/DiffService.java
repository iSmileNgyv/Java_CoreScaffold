package com.ismile.core.chronovcs.service.diff;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcs.dto.diff.*;
import com.ismile.core.chronovcs.entity.BlobEntity;
import com.ismile.core.chronovcs.entity.BranchHeadEntity;
import com.ismile.core.chronovcs.entity.CommitEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.exception.BranchNotFoundException;
import com.ismile.core.chronovcs.exception.BranchOperationException;
import com.ismile.core.chronovcs.repository.BranchHeadRepository;
import com.ismile.core.chronovcs.repository.CommitRepository;
import com.ismile.core.chronovcs.service.repository.RepositoryService;
import com.ismile.core.chronovcs.service.storage.BlobStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiffService {

    private final CommitRepository commitRepository;
    private final BranchHeadRepository branchHeadRepository;
    private final RepositoryService repositoryService;
    private final BlobStorageService blobStorageService;
    private final ObjectMapper objectMapper;

    /**
     * Compare two references (commits, branches, or tags).
     */
    @Transactional(readOnly = true)
    public DiffResponse compare(String repoKey, String base, String head, boolean includePatch) {
        log.info("Comparing {} ... {} in repository {}", base, head, repoKey);

        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        // Resolve references to commit IDs
        String baseCommitId = resolveReference(repository, base);
        String headCommitId = resolveReference(repository, head);

        if (baseCommitId == null || headCommitId == null) {
            throw new BranchOperationException("Could not resolve references: base=" + base + ", head=" + head);
        }

        // Get commits
        CommitEntity baseCommit = commitRepository.findByRepositoryAndCommitId(repository, baseCommitId)
                .orElseThrow(() -> new BranchOperationException("Base commit not found: " + baseCommitId));
        CommitEntity headCommit = commitRepository.findByRepositoryAndCommitId(repository, headCommitId)
                .orElseThrow(() -> new BranchOperationException("Head commit not found: " + headCommitId));

        // Parse file snapshots
        Map<String, String> baseFiles = parseFilesJson(baseCommit.getFilesJson());
        Map<String, String> headFiles = parseFilesJson(headCommit.getFilesJson());

        // Calculate diff
        List<FileDiff> fileDiffs = calculateFileDiffs(repository, baseFiles, headFiles, includePatch);

        // Calculate statistics
        DiffStats stats = calculateStats(fileDiffs);

        // Check if identical
        boolean identical = fileDiffs.isEmpty();

        return DiffResponse.builder()
                .baseCommitId(baseCommitId)
                .headCommitId(headCommitId)
                .baseCommitMessage(baseCommit.getMessage())
                .headCommitMessage(headCommit.getMessage())
                .files(fileDiffs)
                .stats(stats)
                .identical(identical)
                .build();
    }

    /**
     * Get diff for a single commit (compare with its parent).
     */
    @Transactional(readOnly = true)
    public DiffResponse getCommitDiff(String repoKey, String commitId, boolean includePatch) {
        log.info("Getting diff for commit {} in repository {}", commitId, repoKey);

        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        CommitEntity commit = commitRepository.findByRepositoryAndCommitId(repository, commitId)
                .orElseThrow(() -> new BranchOperationException("Commit not found: " + commitId));

        String parentCommitId = commit.getParentCommitId();

        if (parentCommitId == null) {
            // First commit - compare with empty state
            Map<String, String> emptyFiles = new HashMap<>();
            Map<String, String> commitFiles = parseFilesJson(commit.getFilesJson());

            List<FileDiff> fileDiffs = calculateFileDiffs(repository, emptyFiles, commitFiles, includePatch);
            DiffStats stats = calculateStats(fileDiffs);

            return DiffResponse.builder()
                    .baseCommitId(null)
                    .headCommitId(commitId)
                    .baseCommitMessage("(empty)")
                    .headCommitMessage(commit.getMessage())
                    .files(fileDiffs)
                    .stats(stats)
                    .identical(false)
                    .build();
        }

        // Compare with parent
        return compare(repoKey, parentCommitId, commitId, includePatch);
    }

    /**
     * Calculate file differences between two snapshots.
     */
    private List<FileDiff> calculateFileDiffs(
            RepositoryEntity repository,
            Map<String, String> baseFiles,
            Map<String, String> headFiles,
            boolean includePatch) {

        List<FileDiff> diffs = new ArrayList<>();

        // Get all unique file paths
        Set<String> allPaths = new HashSet<>();
        allPaths.addAll(baseFiles.keySet());
        allPaths.addAll(headFiles.keySet());

        for (String path : allPaths) {
            String baseHash = baseFiles.get(path);
            String headHash = headFiles.get(path);

            FileDiff.FileDiffBuilder diffBuilder = FileDiff.builder();

            // Determine change type
            ChangeType changeType;
            if (baseHash == null && headHash != null) {
                // File added
                changeType = ChangeType.ADDED;
                diffBuilder.oldPath(null)
                        .newPath(path)
                        .oldBlobHash(null)
                        .newBlobHash(headHash);
            } else if (baseHash != null && headHash == null) {
                // File deleted
                changeType = ChangeType.DELETED;
                diffBuilder.oldPath(path)
                        .newPath(null)
                        .oldBlobHash(baseHash)
                        .newBlobHash(null);
            } else if (!baseHash.equals(headHash)) {
                // File modified
                changeType = ChangeType.MODIFIED;
                diffBuilder.oldPath(path)
                        .newPath(path)
                        .oldBlobHash(baseHash)
                        .newBlobHash(headHash);
            } else {
                // No change, skip
                continue;
            }

            diffBuilder.changeType(changeType);

            // Load content and generate patch if requested
            if (includePatch && changeType != ChangeType.DELETED && changeType != ChangeType.ADDED) {
                try {
                    String[] patchData = generatePatch(repository, baseHash, headHash);
                    if (patchData != null) {
                        diffBuilder.patch(patchData[0]);
                        diffBuilder.linesAdded(Integer.parseInt(patchData[1]));
                        diffBuilder.linesDeleted(Integer.parseInt(patchData[2]));
                        diffBuilder.totalChanges(Integer.parseInt(patchData[1]) + Integer.parseInt(patchData[2]));
                        diffBuilder.binary(false);
                    } else {
                        diffBuilder.binary(true);
                    }
                } catch (Exception e) {
                    log.warn("Failed to generate patch for {}: {}", path, e.getMessage());
                    diffBuilder.binary(true);
                }
            } else if (changeType == ChangeType.ADDED) {
                // For added files, count lines if text
                try {
                    BlobEntity headBlob = blobStorageService.findByHash(repository, headHash).orElse(null);
                    if (headBlob != null && isTextFile(headBlob.getContentType())) {
                        byte[] content = blobStorageService.loadContent(headBlob);
                        int lineCount = countLines(new String(content, StandardCharsets.UTF_8));
                        diffBuilder.linesAdded(lineCount);
                        diffBuilder.linesDeleted(0);
                        diffBuilder.totalChanges(lineCount);
                        diffBuilder.binary(false);
                    } else {
                        diffBuilder.binary(true);
                    }
                } catch (Exception e) {
                    log.warn("Failed to count lines for added file {}: {}", path, e.getMessage());
                    diffBuilder.binary(true);
                }
            } else if (changeType == ChangeType.DELETED) {
                // For deleted files, count lines if text
                try {
                    BlobEntity baseBlob = blobStorageService.findByHash(repository, baseHash).orElse(null);
                    if (baseBlob != null && isTextFile(baseBlob.getContentType())) {
                        byte[] content = blobStorageService.loadContent(baseBlob);
                        int lineCount = countLines(new String(content, StandardCharsets.UTF_8));
                        diffBuilder.linesAdded(0);
                        diffBuilder.linesDeleted(lineCount);
                        diffBuilder.totalChanges(lineCount);
                        diffBuilder.binary(false);
                    } else {
                        diffBuilder.binary(true);
                    }
                } catch (Exception e) {
                    log.warn("Failed to count lines for deleted file {}: {}", path, e.getMessage());
                    diffBuilder.binary(true);
                }
            }

            diffs.add(diffBuilder.build());
        }

        // Sort by path
        diffs.sort(Comparator.comparing(d -> d.getNewPath() != null ? d.getNewPath() : d.getOldPath()));

        return diffs;
    }

    /**
     * Generate unified diff patch between two blobs.
     * Returns [patch, linesAdded, linesDeleted] or null if binary.
     */
    private String[] generatePatch(RepositoryEntity repository, String oldHash, String newHash) {
        try {
            BlobEntity oldBlob = blobStorageService.findByHash(repository, oldHash).orElse(null);
            BlobEntity newBlob = blobStorageService.findByHash(repository, newHash).orElse(null);

            if (oldBlob == null || newBlob == null) {
                return null;
            }

            // Check if text files
            if (!isTextFile(oldBlob.getContentType()) || !isTextFile(newBlob.getContentType())) {
                return null; // Binary
            }

            byte[] oldContent = blobStorageService.loadContent(oldBlob);
            byte[] newContent = blobStorageService.loadContent(newBlob);

            String oldText = new String(oldContent, StandardCharsets.UTF_8);
            String newText = new String(newContent, StandardCharsets.UTF_8);

            // Simple line-by-line diff
            String[] oldLines = oldText.split("\n", -1);
            String[] newLines = newText.split("\n", -1);

            StringBuilder patch = new StringBuilder();
            int linesAdded = 0;
            int linesDeleted = 0;

            // Very basic diff (not Myers algorithm, just simple comparison)
            int maxLen = Math.max(oldLines.length, newLines.length);
            for (int i = 0; i < maxLen; i++) {
                String oldLine = i < oldLines.length ? oldLines[i] : null;
                String newLine = i < newLines.length ? newLines[i] : null;

                if (oldLine == null) {
                    patch.append("+").append(newLine).append("\n");
                    linesAdded++;
                } else if (newLine == null) {
                    patch.append("-").append(oldLine).append("\n");
                    linesDeleted++;
                } else if (!oldLine.equals(newLine)) {
                    patch.append("-").append(oldLine).append("\n");
                    patch.append("+").append(newLine).append("\n");
                    linesDeleted++;
                    linesAdded++;
                } else {
                    patch.append(" ").append(oldLine).append("\n");
                }
            }

            return new String[]{patch.toString(), String.valueOf(linesAdded), String.valueOf(linesDeleted)};

        } catch (Exception e) {
            log.error("Failed to generate patch: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Calculate diff statistics.
     */
    private DiffStats calculateStats(List<FileDiff> diffs) {
        int filesAdded = 0;
        int filesModified = 0;
        int filesDeleted = 0;
        int totalLinesAdded = 0;
        int totalLinesDeleted = 0;

        for (FileDiff diff : diffs) {
            switch (diff.getChangeType()) {
                case ADDED:
                    filesAdded++;
                    break;
                case MODIFIED:
                    filesModified++;
                    break;
                case DELETED:
                    filesDeleted++;
                    break;
            }

            if (diff.getLinesAdded() != null) {
                totalLinesAdded += diff.getLinesAdded();
            }
            if (diff.getLinesDeleted() != null) {
                totalLinesDeleted += diff.getLinesDeleted();
            }
        }

        return DiffStats.builder()
                .filesChanged(diffs.size())
                .filesAdded(filesAdded)
                .filesModified(filesModified)
                .filesDeleted(filesDeleted)
                .totalLinesAdded(totalLinesAdded)
                .totalLinesDeleted(totalLinesDeleted)
                .totalChanges(totalLinesAdded + totalLinesDeleted)
                .build();
    }

    /**
     * Resolve reference (commit ID, branch name, or tag) to commit ID.
     */
    private String resolveReference(RepositoryEntity repository, String reference) {
        // Try as commit ID first
        if (commitRepository.existsByRepositoryAndCommitId(repository, reference)) {
            return reference;
        }

        // Try as branch name
        Optional<BranchHeadEntity> branch = branchHeadRepository.findByRepositoryAndBranch(repository, reference);
        if (branch.isPresent()) {
            return branch.get().getHeadCommitId();
        }

        // TODO: Try as tag name when tags are implemented

        return null;
    }

    /**
     * Parse filesJson to map.
     */
    private Map<String, String> parseFilesJson(String filesJson) {
        try {
            return objectMapper.readValue(filesJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse filesJson: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Check if content type indicates a text file.
     */
    private boolean isTextFile(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.startsWith("text/") ||
                contentType.contains("json") ||
                contentType.contains("xml") ||
                contentType.contains("javascript") ||
                contentType.contains("java") ||
                contentType.contains("python");
    }

    /**
     * Count lines in text.
     */
    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\n", -1).length;
    }
}
