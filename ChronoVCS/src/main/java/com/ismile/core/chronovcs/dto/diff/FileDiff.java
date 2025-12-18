package com.ismile.core.chronovcs.dto.diff;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDiff {

    /**
     * Path of the file in the old version.
     */
    private String oldPath;

    /**
     * Path of the file in the new version.
     */
    private String newPath;

    /**
     * Type of change (ADDED, MODIFIED, DELETED, RENAMED).
     */
    private ChangeType changeType;

    /**
     * Blob hash in the old version (null if added).
     */
    private String oldBlobHash;

    /**
     * Blob hash in the new version (null if deleted).
     */
    private String newBlobHash;

    /**
     * Number of lines added (if content is available).
     */
    private Integer linesAdded;

    /**
     * Number of lines deleted (if content is available).
     */
    private Integer linesDeleted;

    /**
     * Total number of changes (additions + deletions).
     */
    private Integer totalChanges;

    /**
     * Unified diff patch format (optional, only if content is available).
     */
    private String patch;

    /**
     * Whether the file is binary.
     */
    private boolean binary;
}
