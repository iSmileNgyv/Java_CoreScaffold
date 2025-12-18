package com.ismile.core.chronovcs.service.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcs.dto.branch.CommitDistance;
import com.ismile.core.chronovcs.dto.branch.MergeAnalysisResponse;
import com.ismile.core.chronovcs.dto.branch.MergeConflict;
import com.ismile.core.chronovcs.entity.CommitEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.exception.BranchOperationException;
import com.ismile.core.chronovcs.repository.CommitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommitGraphService {

    private final CommitRepository commitRepository;
    private final ObjectMapper objectMapper;

    /**
     * Find the common ancestor (merge base) of two commits using BFS.
     */
    public String findCommonAncestor(RepositoryEntity repository, String commit1Id, String commit2Id) {
        log.info("Finding common ancestor of {} and {}", commit1Id, commit2Id);

        if (commit1Id == null || commit2Id == null) {
            return null;
        }

        if (commit1Id.equals(commit2Id)) {
            return commit1Id;
        }

        // Get all ancestors of commit1
        Set<String> ancestors1 = getAllAncestors(repository, commit1Id);
        ancestors1.add(commit1Id);

        // Traverse commit2's ancestors until we find one in ancestors1
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        queue.add(commit2Id);
        visited.add(commit2Id);

        while (!queue.isEmpty()) {
            String currentCommitId = queue.poll();

            // If this commit is in commit1's ancestors, it's the common ancestor
            if (ancestors1.contains(currentCommitId)) {
                log.info("Found common ancestor: {}", currentCommitId);
                return currentCommitId;
            }

            // Get parent and continue traversal
            CommitEntity commit = commitRepository
                    .findByRepositoryAndCommitId(repository, currentCommitId)
                    .orElse(null);

            if (commit != null && commit.getParentCommitId() != null) {
                if (!visited.contains(commit.getParentCommitId())) {
                    queue.add(commit.getParentCommitId());
                    visited.add(commit.getParentCommitId());
                }
            }
        }

        log.warn("No common ancestor found between {} and {}", commit1Id, commit2Id);
        return null;
    }

    /**
     * Get all ancestors of a commit (traversing parent chain).
     */
    public Set<String> getAllAncestors(RepositoryEntity repository, String commitId) {
        Set<String> ancestors = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(commitId);

        while (!queue.isEmpty()) {
            String currentCommitId = queue.poll();

            CommitEntity commit = commitRepository
                    .findByRepositoryAndCommitId(repository, currentCommitId)
                    .orElse(null);

            if (commit != null && commit.getParentCommitId() != null) {
                String parentId = commit.getParentCommitId();
                if (!ancestors.contains(parentId)) {
                    ancestors.add(parentId);
                    queue.add(parentId);
                }
            }
        }

        return ancestors;
    }

    /**
     * Calculate distance between two commits (ahead/behind).
     */
    public CommitDistance calculateDistance(RepositoryEntity repository, String targetCommitId, String sourceCommitId) {
        log.info("Calculating distance between target={} and source={}", targetCommitId, sourceCommitId);

        String mergeBase = findCommonAncestor(repository, targetCommitId, sourceCommitId);

        if (mergeBase == null) {
            log.warn("No common ancestor, commits are in separate histories");
            return CommitDistance.builder()
                    .ahead(0)
                    .behind(0)
                    .mergeBase(null)
                    .build();
        }

        int ahead = countCommitsBetween(repository, mergeBase, sourceCommitId);
        int behind = countCommitsBetween(repository, mergeBase, targetCommitId);

        log.info("Distance: {} ahead, {} behind (merge base: {})", ahead, behind, mergeBase);

        return CommitDistance.builder()
                .ahead(ahead)
                .behind(behind)
                .mergeBase(mergeBase)
                .build();
    }

    /**
     * Count number of commits between two commits (from ancestor to descendant).
     */
    public int countCommitsBetween(RepositoryEntity repository, String ancestorId, String descendantId) {
        if (ancestorId == null || descendantId == null) {
            return 0;
        }

        if (ancestorId.equals(descendantId)) {
            return 0;
        }

        int count = 0;
        String currentCommitId = descendantId;

        while (currentCommitId != null && !currentCommitId.equals(ancestorId)) {
            CommitEntity commit = commitRepository
                    .findByRepositoryAndCommitId(repository, currentCommitId)
                    .orElse(null);

            if (commit == null) {
                break;
            }

            count++;
            currentCommitId = commit.getParentCommitId();

            // Safety check to prevent infinite loops
            if (count > 10000) {
                log.warn("Commit chain too long, stopping at 10000");
                break;
            }
        }

        return count;
    }

    /**
     * Check if fast-forward merge is possible.
     */
    public boolean canFastForward(RepositoryEntity repository, String targetCommitId, String sourceCommitId) {
        if (targetCommitId == null || sourceCommitId == null) {
            return false;
        }

        // Fast-forward is possible if target is an ancestor of source
        Set<String> sourceAncestors = getAllAncestors(repository, sourceCommitId);
        return sourceAncestors.contains(targetCommitId);
    }

    /**
     * Analyze merge and detect conflicts.
     */
    public MergeAnalysisResponse analyzeMerge(
            RepositoryEntity repository,
            String targetCommitId,
            String sourceCommitId) {

        log.info("Analyzing merge: target={}, source={}", targetCommitId, sourceCommitId);

        // Find common ancestor
        String mergeBase = findCommonAncestor(repository, targetCommitId, sourceCommitId);

        if (mergeBase == null) {
            throw new BranchOperationException("Cannot merge: no common ancestor found");
        }

        // Calculate distance
        CommitDistance distance = calculateDistance(repository, targetCommitId, sourceCommitId);

        // Check fast-forward
        boolean canFastForward = canFastForward(repository, targetCommitId, sourceCommitId);

        // Get commits
        CommitEntity baseCommit = commitRepository.findByRepositoryAndCommitId(repository, mergeBase).orElse(null);
        CommitEntity targetCommit = commitRepository.findByRepositoryAndCommitId(repository, targetCommitId).orElse(null);
        CommitEntity sourceCommit = commitRepository.findByRepositoryAndCommitId(repository, sourceCommitId).orElse(null);

        if (baseCommit == null || targetCommit == null || sourceCommit == null) {
            throw new BranchOperationException("Cannot analyze merge: commits not found");
        }

        // Parse file snapshots
        Map<String, String> baseFiles = parseFilesJson(baseCommit.getFilesJson());
        Map<String, String> targetFiles = parseFilesJson(targetCommit.getFilesJson());
        Map<String, String> sourceFiles = parseFilesJson(sourceCommit.getFilesJson());

        // Detect conflicts
        List<MergeConflict> conflicts = detectConflicts(baseFiles, targetFiles, sourceFiles);

        // Calculate file changes
        int filesChangedInTarget = countChangedFiles(baseFiles, targetFiles);
        int filesChangedInSource = countChangedFiles(baseFiles, sourceFiles);

        // Build summary
        String summary = buildMergeSummary(distance, conflicts, canFastForward);

        return MergeAnalysisResponse.builder()
                .canAutoMerge(conflicts.isEmpty())
                .canFastForward(canFastForward)
                .mergeBase(mergeBase)
                .commitsAhead(distance.getAhead())
                .commitsBehind(distance.getBehind())
                .conflicts(conflicts)
                .filesChangedInSource(filesChangedInSource)
                .filesChangedInTarget(filesChangedInTarget)
                .summary(summary)
                .build();
    }

    /**
     * Detect conflicts between three file snapshots (3-way merge).
     */
    private List<MergeConflict> detectConflicts(
            Map<String, String> baseFiles,
            Map<String, String> targetFiles,
            Map<String, String> sourceFiles) {

        List<MergeConflict> conflicts = new ArrayList<>();

        // Get all unique file paths
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(baseFiles.keySet());
        allFiles.addAll(targetFiles.keySet());
        allFiles.addAll(sourceFiles.keySet());

        for (String filePath : allFiles) {
            String baseBlob = baseFiles.get(filePath);
            String targetBlob = targetFiles.get(filePath);
            String sourceBlob = sourceFiles.get(filePath);

            // Determine conflict type
            MergeConflict.ConflictType conflictType = null;
            String description = null;

            if (baseBlob != null && targetBlob != null && sourceBlob != null) {
                // File exists in all three
                if (!baseBlob.equals(targetBlob) && !baseBlob.equals(sourceBlob)) {
                    // Both branches modified the file
                    if (!targetBlob.equals(sourceBlob)) {
                        conflictType = MergeConflict.ConflictType.MODIFIED_MODIFIED;
                        description = "Both branches modified this file differently";
                    }
                    // else: both modified to same content, no conflict
                }
            } else if (baseBlob != null && targetBlob == null && sourceBlob != null) {
                // Target deleted, source modified
                conflictType = MergeConflict.ConflictType.DELETED_MODIFIED;
                description = "Target branch deleted this file, but source branch modified it";
            } else if (baseBlob != null && targetBlob != null && sourceBlob == null) {
                // Target modified, source deleted
                conflictType = MergeConflict.ConflictType.MODIFIED_DELETED;
                description = "Target branch modified this file, but source branch deleted it";
            } else if (baseBlob == null && targetBlob != null && sourceBlob != null) {
                // Both branches added the same file
                if (!targetBlob.equals(sourceBlob)) {
                    conflictType = MergeConflict.ConflictType.ADDED_ADDED;
                    description = "Both branches added this file with different content";
                }
            }

            // Add conflict if detected
            if (conflictType != null) {
                conflicts.add(MergeConflict.builder()
                        .filePath(filePath)
                        .baseBlob(baseBlob)
                        .targetBlob(targetBlob)
                        .sourceBlob(sourceBlob)
                        .conflictType(conflictType)
                        .description(description)
                        .build());
            }
        }

        log.info("Detected {} conflicts", conflicts.size());
        return conflicts;
    }

    /**
     * Count number of files changed between two snapshots.
     */
    private int countChangedFiles(Map<String, String> baseFiles, Map<String, String> changedFiles) {
        int count = 0;

        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(baseFiles.keySet());
        allFiles.addAll(changedFiles.keySet());

        for (String filePath : allFiles) {
            String baseBlob = baseFiles.get(filePath);
            String changedBlob = changedFiles.get(filePath);

            if (!Objects.equals(baseBlob, changedBlob)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Parse filesJson from commit.
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
     * Build human-readable merge summary.
     */
    private String buildMergeSummary(CommitDistance distance, List<MergeConflict> conflicts, boolean canFastForward) {
        StringBuilder summary = new StringBuilder();

        if (canFastForward) {
            summary.append("Fast-forward merge is possible. ");
        }

        summary.append(String.format("Source is %d commits ahead and %d commits behind target. ",
                distance.getAhead(), distance.getBehind()));

        if (conflicts.isEmpty()) {
            summary.append("No conflicts detected. Merge can be performed automatically.");
        } else {
            summary.append(String.format("Found %d conflict(s) that need manual resolution.", conflicts.size()));
        }

        return summary.toString();
    }

    /**
     * Create merged file snapshot (auto-merge without conflicts).
     */
    public Map<String, String> createMergedSnapshot(
            Map<String, String> baseFiles,
            Map<String, String> targetFiles,
            Map<String, String> sourceFiles) {

        Map<String, String> mergedFiles = new HashMap<>(targetFiles);

        // Get all file paths
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(baseFiles.keySet());
        allFiles.addAll(targetFiles.keySet());
        allFiles.addAll(sourceFiles.keySet());

        for (String filePath : allFiles) {
            String baseBlob = baseFiles.get(filePath);
            String targetBlob = targetFiles.get(filePath);
            String sourceBlob = sourceFiles.get(filePath);

            // Apply source changes if target didn't change
            if (Objects.equals(baseBlob, targetBlob) && !Objects.equals(baseBlob, sourceBlob)) {
                // Target didn't change, but source did -> use source version
                if (sourceBlob != null) {
                    mergedFiles.put(filePath, sourceBlob);
                } else {
                    mergedFiles.remove(filePath); // Source deleted the file
                }
            }
            // If both changed to the same value, that's fine (already in mergedFiles)
            // If target changed and source didn't, keep target (already in mergedFiles)
        }

        return mergedFiles;
    }
}
