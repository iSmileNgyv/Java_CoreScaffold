package com.ismile.core.chronovcs.dto.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeAnalysisResponse {

    /**
     * Whether the merge can be performed automatically.
     */
    private boolean canAutoMerge;

    /**
     * Whether fast-forward merge is possible.
     */
    private boolean canFastForward;

    /**
     * Common ancestor commit ID.
     */
    private String mergeBase;

    /**
     * Number of commits source is ahead of target.
     */
    private int commitsAhead;

    /**
     * Number of commits source is behind target.
     */
    private int commitsBehind;

    /**
     * List of conflicting files.
     */
    private List<MergeConflict> conflicts;

    /**
     * Total number of files changed in source branch.
     */
    private int filesChangedInSource;

    /**
     * Total number of files changed in target branch.
     */
    private int filesChangedInTarget;

    /**
     * Human-readable merge summary.
     */
    private String summary;
}
