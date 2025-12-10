package com.ismile.core.chronovcscli.core.merge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Information about a merge conflict in a file
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictInfo {

    /**
     * File path relative to project root
     */
    private String filePath;

    /**
     * Blob hash in base commit (common ancestor)
     */
    private String baseHash;

    /**
     * Blob hash in local commit
     */
    private String localHash;

    /**
     * Blob hash in remote commit
     */
    private String remoteHash;

    /**
     * Type of conflict
     */
    private ConflictType conflictType;

    /**
     * Whether conflict was auto-resolved
     */
    private boolean autoResolved;

    public enum ConflictType {
        /**
         * Both sides modified the same file
         */
        MODIFY_MODIFY,

        /**
         * File deleted in one branch, modified in other
         */
        DELETE_MODIFY,

        /**
         * File added in both branches with different content
         */
        ADD_ADD,

        /**
         * File renamed in both branches to different names
         */
        RENAME_RENAME
    }
}
