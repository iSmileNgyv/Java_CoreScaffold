package com.ismile.core.chronovcs.service.history;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcs.dto.diff.ChangeType;
import com.ismile.core.chronovcs.dto.history.*;
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
public class FileHistoryService {

    private final CommitRepository commitRepository;
    private final BranchHeadRepository branchHeadRepository;
    private final RepositoryService repositoryService;
    private final BlobStorageService blobStorageService;
    private final ObjectMapper objectMapper;

    /**
     * Get file history for a specific file path.
     */
    @Transactional(readOnly = true)
    public FileHistoryResponse getFileHistory(String repoKey, String filePath, String branch, Integer limit) {
        log.info("Getting file history for {} in repository {} (branch={})", filePath, repoKey, branch);

        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        // Resolve branch to commit ID
        String branchName = branch != null ? branch : repository.getDefaultBranch();
        BranchHeadEntity branchHead = branchHeadRepository
                .findByRepositoryAndBranch(repository, branchName)
                .orElseThrow(() -> new BranchNotFoundException(branchName));

        String headCommitId = branchHead.getHeadCommitId();
        if (headCommitId == null) {
            throw new BranchOperationException("Branch has no commits: " + branchName);
        }

        // Traverse commit history and find commits that modified this file
        List<FileHistoryEntry> history = new ArrayList<>();
        Set<String> visitedCommits = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(headCommitId);
        visitedCommits.add(headCommitId);

        Map<String, String> previousFiles = null;
        int count = 0;
        int maxLimit = limit != null ? limit : 100;

        while (!queue.isEmpty() && count < maxLimit) {
            String commitId = queue.poll();

            CommitEntity commit = commitRepository
                    .findByRepositoryAndCommitId(repository, commitId)
                    .orElse(null);

            if (commit == null) {
                continue;
            }

            // Parse files snapshot
            Map<String, String> currentFiles = parseFilesJson(commit.getFilesJson());

            // Check if this file exists in current commit
            String currentBlobHash = currentFiles.get(filePath);

            // Determine change type
            ChangeType changeType = null;
            String oldPath = filePath;
            Integer linesAdded = null;
            Integer linesDeleted = null;

            if (previousFiles != null) {
                String previousBlobHash = previousFiles.get(filePath);

                if (previousBlobHash == null && currentBlobHash != null) {
                    // File was deleted in later commit, exists here
                    changeType = ChangeType.DELETED;
                } else if (previousBlobHash != null && currentBlobHash == null) {
                    // File was added in later commit, doesn't exist here
                    changeType = ChangeType.ADDED;
                } else if (previousBlobHash != null && currentBlobHash != null
                        && !previousBlobHash.equals(currentBlobHash)) {
                    // File was modified
                    changeType = ChangeType.MODIFIED;

                    // Try to calculate line changes
                    try {
                        int[] changes = calculateLineChanges(repository, currentBlobHash, previousBlobHash);
                        linesAdded = changes[0];
                        linesDeleted = changes[1];
                    } catch (Exception e) {
                        log.warn("Failed to calculate line changes: {}", e.getMessage());
                    }
                }
            } else {
                // First commit in history
                if (currentBlobHash != null) {
                    changeType = ChangeType.ADDED;
                }
            }

            // Add to history if file was affected
            if (changeType != null) {
                history.add(FileHistoryEntry.builder()
                        .commitId(commitId)
                        .message(commit.getMessage())
                        .author(extractAuthor(commit))
                        .timestamp(commit.getTimestamp())
                        .branch(commit.getBranch())
                        .changeType(changeType)
                        .oldPath(oldPath)
                        .newPath(filePath)
                        .blobHash(currentBlobHash)
                        .linesAdded(linesAdded)
                        .linesDeleted(linesDeleted)
                        .createdAt(commit.getCreatedAt())
                        .build());

                count++;
            }

            // Continue to parent
            if (commit.getParentCommitId() != null && !visitedCommits.contains(commit.getParentCommitId())) {
                queue.add(commit.getParentCommitId());
                visitedCommits.add(commit.getParentCommitId());
            }

            previousFiles = currentFiles;
        }

        // Check if file exists in HEAD
        CommitEntity headCommit = commitRepository
                .findByRepositoryAndCommitId(repository, headCommitId)
                .orElseThrow(() -> new BranchOperationException("HEAD commit not found"));

        Map<String, String> headFiles = parseFilesJson(headCommit.getFilesJson());
        boolean exists = headFiles.containsKey(filePath);
        String currentBlobHash = headFiles.get(filePath);

        return FileHistoryResponse.builder()
                .filePath(filePath)
                .repoKey(repoKey)
                .totalCommits(history.size())
                .commits(history)
                .exists(exists)
                .currentBlobHash(currentBlobHash)
                .build();
    }

    /**
     * Get blame/annotation for a file at a specific commit.
     */
    @Transactional(readOnly = true)
    public BlameResponse getBlame(String repoKey, String filePath, String commitIdOrBranch) {
        log.info("Getting blame for {} in repository {} at {}", filePath, repoKey, commitIdOrBranch);

        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        // Resolve reference to commit ID
        String commitId = resolveReference(repository, commitIdOrBranch);
        if (commitId == null) {
            throw new BranchOperationException("Could not resolve reference: " + commitIdOrBranch);
        }

        CommitEntity commit = commitRepository
                .findByRepositoryAndCommitId(repository, commitId)
                .orElseThrow(() -> new BranchOperationException("Commit not found: " + commitId));

        // Get file content
        Map<String, String> files = parseFilesJson(commit.getFilesJson());
        String blobHash = files.get(filePath);

        if (blobHash == null) {
            throw new BranchOperationException("File not found in commit: " + filePath);
        }

        // Load blob
        BlobEntity blob = blobStorageService.findByHash(repository, blobHash)
                .orElseThrow(() -> new BranchOperationException("Blob not found: " + blobHash));

        // Check if binary
        if (!isTextFile(blob.getContentType())) {
            return BlameResponse.builder()
                    .filePath(filePath)
                    .repoKey(repoKey)
                    .commitId(commitId)
                    .branch(commit.getBranch())
                    .binary(true)
                    .lines(Collections.emptyList())
                    .totalLines(0)
                    .uniqueCommits(0)
                    .uniqueAuthors(0)
                    .build();
        }

        // Load content
        byte[] content = blobStorageService.loadContent(blob);
        String fileContent = new String(content, StandardCharsets.UTF_8);
        String[] lines = fileContent.split("\n", -1);

        // Build blame information
        List<BlameLine> blameLines = new ArrayList<>();
        Set<String> uniqueCommits = new HashSet<>();
        Set<String> uniqueAuthors = new HashSet<>();

        // For now, simple implementation: all lines attributed to current commit
        // TODO: Implement proper line-by-line blame tracking through history
        for (int i = 0; i < lines.length; i++) {
            blameLines.add(BlameLine.builder()
                    .lineNumber(i + 1)
                    .commitId(commitId)
                    .commitMessage(truncate(commit.getMessage(), 50))
                    .author(extractAuthor(commit))
                    .timestamp(commit.getTimestamp())
                    .content(lines[i])
                    .age(0)
                    .build());

            uniqueCommits.add(commitId);
            uniqueAuthors.add(extractAuthor(commit));
        }

        return BlameResponse.builder()
                .filePath(filePath)
                .repoKey(repoKey)
                .commitId(commitId)
                .branch(commit.getBranch())
                .totalLines(lines.length)
                .lines(blameLines)
                .binary(false)
                .uniqueCommits(uniqueCommits.size())
                .uniqueAuthors(uniqueAuthors.size())
                .build();
    }

    /**
     * Calculate line changes between two blobs.
     * Returns [linesAdded, linesDeleted].
     */
    private int[] calculateLineChanges(RepositoryEntity repository, String oldHash, String newHash) {
        try {
            BlobEntity oldBlob = blobStorageService.findByHash(repository, oldHash).orElse(null);
            BlobEntity newBlob = blobStorageService.findByHash(repository, newHash).orElse(null);

            if (oldBlob == null || newBlob == null) {
                return new int[]{0, 0};
            }

            if (!isTextFile(oldBlob.getContentType()) || !isTextFile(newBlob.getContentType())) {
                return new int[]{0, 0};
            }

            byte[] oldContent = blobStorageService.loadContent(oldBlob);
            byte[] newContent = blobStorageService.loadContent(newBlob);

            String oldText = new String(oldContent, StandardCharsets.UTF_8);
            String newText = new String(newContent, StandardCharsets.UTF_8);

            String[] oldLines = oldText.split("\n", -1);
            String[] newLines = newText.split("\n", -1);

            int linesAdded = 0;
            int linesDeleted = 0;

            // Simple diff
            Set<String> oldSet = new HashSet<>(Arrays.asList(oldLines));
            Set<String> newSet = new HashSet<>(Arrays.asList(newLines));

            for (String line : newLines) {
                if (!oldSet.contains(line)) {
                    linesAdded++;
                }
            }

            for (String line : oldLines) {
                if (!newSet.contains(line)) {
                    linesDeleted++;
                }
            }

            return new int[]{linesAdded, linesDeleted};

        } catch (Exception e) {
            log.error("Failed to calculate line changes: {}", e.getMessage());
            return new int[]{0, 0};
        }
    }

    /**
     * Resolve reference (commit ID or branch name) to commit ID.
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
     * Extract author from commit (placeholder for now).
     */
    private String extractAuthor(CommitEntity commit) {
        // TODO: Add author field to CommitEntity
        return "Unknown";
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
     * Truncate string to max length.
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}
