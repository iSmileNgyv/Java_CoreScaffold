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
public class BranchListResponse {

    /**
     * List of branches in the repository.
     */
    private List<BranchResponse> branches;

    /**
     * Name of the default branch.
     */
    private String defaultBranch;

    /**
     * Name of the currently active branch (if applicable).
     */
    private String currentBranch;

    /**
     * Total number of branches.
     */
    private int totalCount;
}
