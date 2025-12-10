package com.ismile.core.chronovcscli.core.log;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a single commit log entry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    /**
     * Commit ID (hash)
     */
    private String commitId;

    /**
     * Parent commit ID
     */
    private String parentId;

    /**
     * Merge parent commit ID (for merge commits)
     */
    private String mergeParentId;

    /**
     * Commit message
     */
    private String message;

    /**
     * Timestamp
     */
    private String timestamp;

    /**
     * Files in this commit (path -> blob hash)
     */
    private Map<String, String> files;

    /**
     * Whether this is the current HEAD
     */
    private boolean isHead;

    /**
     * Branch references pointing to this commit
     */
    @Builder.Default
    private List<String> branches = new ArrayList<>();

    /**
     * Whether this is a merge commit
     */
    public boolean isMergeCommit() {
        return mergeParentId != null;
    }

    /**
     * Get short commit ID (first 8 chars)
     */
    public String getShortCommitId() {
        return commitId != null && commitId.length() >= 8
            ? commitId.substring(0, 8)
            : commitId;
    }
}
