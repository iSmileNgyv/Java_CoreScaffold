package com.ismile.core.chronovcscli.core.merge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a merge operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeResult {

    /**
     * Whether merge was successful
     */
    private boolean success;

    /**
     * Merge strategy used
     */
    private MergeStrategy strategy;

    /**
     * Base commit hash (common ancestor)
     */
    private String baseCommitId;

    /**
     * Local commit hash
     */
    private String localCommitId;

    /**
     * Remote commit hash
     */
    private String remoteCommitId;

    /**
     * Resulting merge commit hash (if created)
     */
    private String mergeCommitId;

    /**
     * List of files with conflicts
     */
    @Builder.Default
    private List<ConflictInfo> conflicts = new ArrayList<>();

    /**
     * List of files that were auto-merged successfully
     */
    @Builder.Default
    private List<String> autoMergedFiles = new ArrayList<>();

    /**
     * Message describing the result
     */
    private String message;

    /**
     * Whether there are unresolved conflicts
     */
    public boolean hasConflicts() {
        return conflicts.stream().anyMatch(c -> !c.isAutoResolved());
    }

    /**
     * Get list of conflicted file paths
     */
    public List<String> getConflictedFiles() {
        return conflicts.stream()
                .filter(c -> !c.isAutoResolved())
                .map(ConflictInfo::getFilePath)
                .toList();
    }

    /**
     * Create a successful merge result
     */
    public static MergeResult success(MergeStrategy strategy, String message) {
        return MergeResult.builder()
                .success(true)
                .strategy(strategy)
                .message(message)
                .build();
    }

    /**
     * Create a conflict merge result
     */
    public static MergeResult conflict(String message, List<ConflictInfo> conflicts) {
        return MergeResult.builder()
                .success(false)
                .strategy(MergeStrategy.CONFLICT)
                .message(message)
                .conflicts(conflicts)
                .build();
    }

    /**
     * Create an error result
     */
    public static MergeResult error(String message) {
        return MergeResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
