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

    private enum DiffOpType {
        EQUAL,
        INSERT,
        DELETE
    }

    private static class LineInfo {
        private final String original;
        private final String key;

        private LineInfo(String original, String key) {
            this.original = original;
            this.key = key;
        }
    }

    private static class DiffOp {
        private final DiffOpType type;
        private final String line;

        private DiffOp(DiffOpType type, String line) {
            this.type = type;
            this.line = line;
        }
    }

    private static class DiffHunk {
        private final int baseStart;
        private final int baseEnd;
        private final List<String> insertedLines;

        private DiffHunk(int baseStart, int baseEnd, List<String> insertedLines) {
            this.baseStart = baseStart;
            this.baseEnd = baseEnd;
            this.insertedLines = insertedLines;
        }
    }

    private final CommitRepository commitRepository;
    private final BranchHeadRepository branchHeadRepository;
    private final RepositoryService repositoryService;
    private final BlobStorageService blobStorageService;
    private final ObjectMapper objectMapper;

    /**
     * Compare two references (commits, branches, or tags).
     */
    @Transactional(readOnly = true)
    public DiffResponse compare(String repoKey,
                                String base,
                                String head,
                                boolean includePatch,
                                boolean ignoreWhitespace) {
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
        List<FileDiff> fileDiffs = calculateFileDiffs(
                repository,
                baseFiles,
                headFiles,
                includePatch,
                ignoreWhitespace
        );

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
    public DiffResponse getCommitDiff(String repoKey,
                                      String commitId,
                                      boolean includePatch,
                                      boolean ignoreWhitespace) {
        log.info("Getting diff for commit {} in repository {}", commitId, repoKey);

        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);

        CommitEntity commit = commitRepository.findByRepositoryAndCommitId(repository, commitId)
                .orElseThrow(() -> new BranchOperationException("Commit not found: " + commitId));

        String parentCommitId = commit.getParentCommitId();

        if (parentCommitId == null) {
            // First commit - compare with empty state
            Map<String, String> emptyFiles = new HashMap<>();
            Map<String, String> commitFiles = parseFilesJson(commit.getFilesJson());

            List<FileDiff> fileDiffs = calculateFileDiffs(
                    repository,
                    emptyFiles,
                    commitFiles,
                    includePatch,
                    ignoreWhitespace
            );
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
        return compare(repoKey, parentCommitId, commitId, includePatch, ignoreWhitespace);
    }

    /**
     * Result of a three-way merge attempt
     */
    public static class MergeAttempt {
        public final boolean hasConflict;
        public final byte[] mergedContent;

        public MergeAttempt(boolean hasConflict, byte[] mergedContent) {
            this.hasConflict = hasConflict;
            this.mergedContent = mergedContent;
        }

        public static MergeAttempt success(byte[] content) {
            return new MergeAttempt(false, content);
        }

        public static MergeAttempt conflict() {
            return new MergeAttempt(true, null);
        }
    }

    /**
     * Attempt to merge three versions of a file
     *
     * @param baseContent   content from common ancestor
     * @param localContent  content from local (target) branch
     * @param remoteContent content from remote (source) branch
     * @return merge attempt result
     */
    public MergeAttempt merge(byte[] baseContent, byte[] localContent, byte[] remoteContent) {

        // If local and remote are identical, no conflict
        if (Arrays.equals(localContent, remoteContent)) {
            log.debug("No changes between local and remote");
            return MergeAttempt.success(localContent);
        }

        // If local unchanged, take remote
        if (Arrays.equals(baseContent, localContent)) {
            log.debug("Local unchanged, taking remote version");
            return MergeAttempt.success(remoteContent);
        }

        // If remote unchanged, keep local
        if (Arrays.equals(baseContent, remoteContent)) {
            log.debug("Remote unchanged, keeping local version");
            return MergeAttempt.success(localContent);
        }

        // Both changed differently - conflict
        log.debug("Both local and remote changed - conflict detected");
        return MergeAttempt.conflict();
    }

    /**
     * Calculate file differences between two snapshots.
     */
    private List<FileDiff> calculateFileDiffs(
            RepositoryEntity repository,
            Map<String, String> baseFiles,
            Map<String, String> headFiles,
            boolean includePatch,
            boolean ignoreWhitespace) {

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
            // Load content and generate patch if requested
            if (includePatch) {
                try {
                    String[] patchData = generatePatch(repository, baseHash, headHash, path, ignoreWhitespace);
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
                    if (headBlob != null && (isTextFile(headBlob.getContentType()) || isTextExtension(path))) {
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
                    if (baseBlob != null && (isTextFile(baseBlob.getContentType()) || isTextExtension(path))) {
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
    private String[] generatePatch(RepositoryEntity repository,
                                   String oldHash,
                                   String newHash,
                                   String path,
                                   boolean ignoreWhitespace) {
        try {
            BlobEntity oldBlob = oldHash != null ? blobStorageService.findByHash(repository, oldHash).orElse(null) : null;
            BlobEntity newBlob = newHash != null ? blobStorageService.findByHash(repository, newHash).orElse(null) : null;

            if (oldBlob == null && newBlob == null) {
                return null;
            }

            // Check if text files (by content type AND extension)
            boolean isText = true;
            if (oldBlob != null) {
                isText = isText && (isTextFile(oldBlob.getContentType()) || isTextExtension(path));
            }
            if (newBlob != null) {
                isText = isText && (isTextFile(newBlob.getContentType()) || isTextExtension(path));
            }

            if (!isText) {
                return null; // Binary
            }

            byte[] oldContent = oldBlob != null ? blobStorageService.loadContent(oldBlob) : new byte[0];
            byte[] newContent = newBlob != null ? blobStorageService.loadContent(newBlob) : new byte[0];

            String oldText = normalizeLineEndings(new String(oldContent, StandardCharsets.UTF_8));
            String newText = normalizeLineEndings(new String(newContent, StandardCharsets.UTF_8));

            String[] oldLines = oldText.split("\n", -1);
            String[] newLines = newText.split("\n", -1);

            LineInfo[] oldInfos = toLineInfos(oldLines, ignoreWhitespace);
            LineInfo[] newInfos = toLineInfos(newLines, ignoreWhitespace);

            List<DiffOp> ops = diffLines(oldInfos, newInfos);

            StringBuilder patch = new StringBuilder();
            int linesAdded = 0;
            int linesDeleted = 0;

            for (DiffOp op : ops) {
                switch (op.type) {
                    case INSERT:
                        patch.append("+").append(op.line).append("\n");
                        linesAdded++;
                        break;
                    case DELETE:
                        patch.append("-").append(op.line).append("\n");
                        linesDeleted++;
                        break;
                    case EQUAL:
                        patch.append(" ").append(op.line).append("\n");
                        break;
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

    private boolean isTextExtension(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".java") ||
                lower.endsWith(".kt") || lower.endsWith(".js") || lower.endsWith(".ts") ||
                lower.endsWith(".tsx") || lower.endsWith(".jsx") || lower.endsWith(".css") ||
                lower.endsWith(".scss") || lower.endsWith(".html") || lower.endsWith(".xml") ||
                lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml") ||
                lower.endsWith(".properties") || lower.endsWith(".gradle") || lower.endsWith(".sql") ||
                lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".gitignore");
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

    private String normalizeLineEndings(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    public boolean canAutoMergeText(String baseText,
                                    String targetText,
                                    String sourceText,
                                    boolean ignoreWhitespace) {
        String baseNormalized = normalizeLineEndings(baseText == null ? "" : baseText);
        String targetNormalized = normalizeLineEndings(targetText == null ? "" : targetText);
        String sourceNormalized = normalizeLineEndings(sourceText == null ? "" : sourceText);

        LineInfo[] baseLines = toLineInfos(baseNormalized.split("\n", -1), ignoreWhitespace);
        LineInfo[] targetLines = toLineInfos(targetNormalized.split("\n", -1), ignoreWhitespace);
        LineInfo[] sourceLines = toLineInfos(sourceNormalized.split("\n", -1), ignoreWhitespace);

        List<DiffHunk> targetHunks = buildHunks(baseLines, targetLines);
        List<DiffHunk> sourceHunks = buildHunks(baseLines, sourceLines);

        return !hasOverlapConflicts(targetHunks, sourceHunks);
    }

    private List<DiffOp> diffLines(LineInfo[] oldLines, LineInfo[] newLines) {
        int n = oldLines.length;
        int m = newLines.length;
        int max = n + m;
        int offset = max;

        int[] v = new int[2 * max + 3];
        Arrays.fill(v, -1);
        v[offset + 1] = 0;

        List<int[]> trace = new ArrayList<>();

        for (int d = 0; d <= max; d++) {
            int[] vSnapshot = v.clone();
            for (int k = -d; k <= d; k += 2) {
                int kIndex = offset + k;
                int x;
                if (k == -d || (k != d && v[kIndex - 1] < v[kIndex + 1])) {
                    x = v[kIndex + 1];
                } else {
                    x = v[kIndex - 1] + 1;
                }

                int y = x - k;
                while (x < n && y < m && Objects.equals(oldLines[x].key, newLines[y].key)) {
                    x++;
                    y++;
                }

                vSnapshot[kIndex] = x;

                if (x >= n && y >= m) {
                    trace.add(vSnapshot);
                    return buildDiffFromTrace(trace, oldLines, newLines, offset);
                }
            }
            trace.add(vSnapshot);
            v = vSnapshot;
        }

        return Collections.emptyList();
    }

    private List<DiffOp> buildDiffFromTrace(List<int[]> trace,
                                            LineInfo[] oldLines,
                                            LineInfo[] newLines,
                                            int offset) {
        List<DiffOp> ops = new ArrayList<>();
        int x = oldLines.length;
        int y = newLines.length;

        for (int d = trace.size() - 1; d >= 0; d--) {
            int[] v = trace.get(d);
            int k = x - y;
            int kIndex = offset + k;
            int prevK;

            if (k == -d || (k != d && v[kIndex - 1] < v[kIndex + 1])) {
                prevK = k + 1;
            } else {
                prevK = k - 1;
            }

            int prevX = v[offset + prevK];
            int prevY = prevX - prevK;

            while (x > prevX && y > prevY) {
                ops.add(new DiffOp(DiffOpType.EQUAL, newLines[y - 1].original));
                x--;
                y--;
            }

            if (d == 0) {
                break;
            }

            if (x == prevX) {
                ops.add(new DiffOp(DiffOpType.INSERT, newLines[prevY].original));
            } else {
                ops.add(new DiffOp(DiffOpType.DELETE, oldLines[prevX].original));
            }

            x = prevX;
            y = prevY;
        }

        Collections.reverse(ops);
        return ops;
    }

    private List<DiffHunk> buildHunks(LineInfo[] baseLines, LineInfo[] otherLines) {
        List<DiffOp> ops = diffLines(baseLines, otherLines);
        List<DiffHunk> hunks = new ArrayList<>();

        int baseIndex = 0;
        int hunkStart = -1;
        int deletedCount = 0;
        List<String> inserted = new ArrayList<>();

        for (DiffOp op : ops) {
            switch (op.type) {
                case EQUAL:
                    if (hunkStart >= 0) {
                        hunks.add(new DiffHunk(hunkStart, hunkStart + deletedCount, new ArrayList<>(inserted)));
                        hunkStart = -1;
                        deletedCount = 0;
                        inserted.clear();
                    }
                    baseIndex++;
                    break;
                case DELETE:
                    if (hunkStart < 0) {
                        hunkStart = baseIndex;
                    }
                    deletedCount++;
                    baseIndex++;
                    break;
                case INSERT:
                    if (hunkStart < 0) {
                        hunkStart = baseIndex;
                    }
                    inserted.add(op.line);
                    break;
            }
        }

        if (hunkStart >= 0) {
            hunks.add(new DiffHunk(hunkStart, hunkStart + deletedCount, new ArrayList<>(inserted)));
        }

        return hunks;
    }

    private boolean hasOverlapConflicts(List<DiffHunk> targetHunks, List<DiffHunk> sourceHunks) {
        for (DiffHunk target : targetHunks) {
            for (DiffHunk source : sourceHunks) {
                if (!rangesOverlap(target, source)) {
                    continue;
                }
                if (!hunksEquivalent(target, source)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean rangesOverlap(DiffHunk first, DiffHunk second) {
        boolean firstInsertion = first.baseStart == first.baseEnd;
        boolean secondInsertion = second.baseStart == second.baseEnd;

        if (firstInsertion && secondInsertion) {
            return first.baseStart == second.baseStart;
        }

        if (firstInsertion) {
            return first.baseStart >= second.baseStart && first.baseStart <= second.baseEnd;
        }

        if (secondInsertion) {
            return second.baseStart >= first.baseStart && second.baseStart <= first.baseEnd;
        }

        return first.baseStart < second.baseEnd && second.baseStart < first.baseEnd;
    }

    private boolean hunksEquivalent(DiffHunk first, DiffHunk second) {
        if (first.baseStart != second.baseStart || first.baseEnd != second.baseEnd) {
            return false;
        }
        return first.insertedLines.equals(second.insertedLines);
    }

    private LineInfo[] toLineInfos(String[] lines, boolean ignoreWhitespace) {
        LineInfo[] infos = new LineInfo[lines.length];
        for (int i = 0; i < lines.length; i++) {
            String original = lines[i];
            String key = normalizeLineForCompare(original, ignoreWhitespace);
            infos[i] = new LineInfo(original, key);
        }
        return infos;
    }

    private String normalizeLineForCompare(String line, boolean ignoreWhitespace) {
        if (line == null) {
            return "";
        }
        String normalized = trimTrailingWhitespace(line);
        if (ignoreWhitespace) {
            normalized = normalized.replaceAll("\\s+", " ").trim();
        }
        return normalized;
    }

    private String trimTrailingWhitespace(String line) {
        int end = line.length();
        while (end > 0) {
            char ch = line.charAt(end - 1);
            if (ch != ' ' && ch != '\t') {
                break;
            }
            end--;
        }
        return line.substring(0, end);
    }
}
