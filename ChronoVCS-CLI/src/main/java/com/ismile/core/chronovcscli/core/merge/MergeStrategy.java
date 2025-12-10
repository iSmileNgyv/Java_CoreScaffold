package com.ismile.core.chronovcscli.core.merge;

/**
 * Merge strategy types
 */
public enum MergeStrategy {
    /**
     * Fast-forward merge - no merge commit needed
     * Remote is ahead, local has no new commits
     */
    FAST_FORWARD,

    /**
     * Three-way merge - merge commit needed
     * Both local and remote have new commits
     */
    THREE_WAY,

    /**
     * Already up to date - no action needed
     */
    UP_TO_DATE,

    /**
     * Cannot merge - manual intervention required
     */
    CONFLICT
}
