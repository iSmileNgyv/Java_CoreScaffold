package com.ismile.core.chronovcs.dto.diff;

/**
 * Type of change made to a file.
 */
public enum ChangeType {
    /**
     * File was added in the new version.
     */
    ADDED,

    /**
     * File was modified between versions.
     */
    MODIFIED,

    /**
     * File was deleted in the new version.
     */
    DELETED,

    /**
     * File was renamed (path changed but content similar).
     */
    RENAMED,

    /**
     * File was copied from another file.
     */
    COPIED
}
