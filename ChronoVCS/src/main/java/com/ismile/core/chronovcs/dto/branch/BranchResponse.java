package com.ismile.core.chronovcs.dto.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchResponse {

    /**
     * Branch name.
     */
    private String branchName;

    /**
     * Current HEAD commit ID of this branch.
     */
    private String headCommitId;

    /**
     * Whether this is the default branch of the repository.
     */
    private boolean isDefault;

    /**
     * Whether this is the currently active/checked-out branch.
     */
    private boolean isCurrent;

    /**
     * Last update timestamp.
     */
    private LocalDateTime updatedAt;

    /**
     * Number of commits ahead of default branch (optional).
     */
    private Integer commitsAhead;

    /**
     * Number of commits behind default branch (optional).
     */
    private Integer commitsBehind;
}
