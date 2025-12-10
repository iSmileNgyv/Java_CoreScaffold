package com.ismile.core.chronovcscli.core.merge;

import java.io.File;

/**
 * Core merge engine interface
 */
public interface MergeEngine {

    /**
     * Merge remote commit into local branch
     *
     * @param projectRoot    project root directory
     * @param localCommitId  local commit hash (HEAD)
     * @param remoteCommitId remote commit hash
     * @param branch         current branch name
     * @return merge result
     */
    MergeResult merge(File projectRoot, String localCommitId, String remoteCommitId, String branch);

    /**
     * Continue merge after conflicts are resolved
     *
     * @param projectRoot project root directory
     * @return merge result
     */
    MergeResult continueMerge(File projectRoot);

    /**
     * Abort ongoing merge
     *
     * @param projectRoot project root directory
     * @return true if aborted successfully
     */
    boolean abortMerge(File projectRoot);

    /**
     * Check if merge is in progress
     *
     * @param projectRoot project root directory
     * @return true if merge is in progress
     */
    boolean isMergeInProgress(File projectRoot);

    /**
     * Get current merge state
     *
     * @param projectRoot project root directory
     * @return merge state, or null if no merge in progress
     */
    MergeState getMergeState(File projectRoot);
}
