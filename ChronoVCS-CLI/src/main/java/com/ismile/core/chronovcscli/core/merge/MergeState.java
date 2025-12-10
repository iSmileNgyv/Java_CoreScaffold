package com.ismile.core.chronovcscli.core.merge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an ongoing merge state
 * Stored in .vcs/MERGE_STATE
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeState {

    /**
     * Local commit being merged from
     */
    private String localCommitId;

    /**
     * Remote commit being merged in
     */
    private String remoteCommitId;

    /**
     * Base commit (common ancestor)
     */
    private String baseCommitId;

    /**
     * Current branch name
     */
    private String branch;

    /**
     * Merge message
     */
    private String mergeMessage;

    /**
     * List of conflicted files
     */
    @Builder.Default
    private List<String> conflictedFiles = new ArrayList<>();

    /**
     * Whether merge is in progress
     */
    private boolean inProgress;
}
