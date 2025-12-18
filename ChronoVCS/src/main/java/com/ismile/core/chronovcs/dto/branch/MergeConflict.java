package com.ismile.core.chronovcs.dto.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeConflict {

    /**
     * File path that has a conflict.
     */
    private String filePath;

    /**
     * Blob hash from the base (common ancestor) commit.
     */
    private String baseBlob;

    /**
     * Blob hash from the target branch.
     */
    private String targetBlob;

    /**
     * Blob hash from the source branch.
     */
    private String sourceBlob;

    /**
     * Conflict type: MODIFIED_MODIFIED, DELETED_MODIFIED, MODIFIED_DELETED, etc.
     */
    private ConflictType conflictType;

    /**
     * Human-readable conflict description.
     */
    private String description;

    public enum ConflictType {
        MODIFIED_MODIFIED,  // Both branches modified the same file
        DELETED_MODIFIED,   // One deleted, one modified
        MODIFIED_DELETED,   // One modified, one deleted
        ADDED_ADDED         // Both branches added the same file with different content
    }
}
