package com.ismile.core.chronovcs.dto.branch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBranchRequest {

    /**
     * Name of the new branch.
     */
    @NotBlank(message = "Branch name is required")
    @Size(min = 1, max = 100, message = "Branch name must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_/-]+$", message = "Branch name can only contain letters, numbers, underscores, hyphens and slashes")
    private String branchName;

    /**
     * Starting commit ID for the new branch.
     * If not specified, uses the current HEAD of the repository's default branch.
     */
    @Size(max = 128, message = "Commit ID must not exceed 128 characters")
    private String fromCommitId;

    /**
     * Source branch to create from.
     * If specified, uses the HEAD of this branch. Takes precedence over fromCommitId.
     */
    @Size(max = 100, message = "Source branch name must not exceed 100 characters")
    private String fromBranch;
}
