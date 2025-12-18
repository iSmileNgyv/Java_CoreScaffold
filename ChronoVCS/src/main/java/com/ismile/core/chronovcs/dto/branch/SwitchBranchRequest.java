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
public class SwitchBranchRequest {

    /**
     * Name of the branch to switch to.
     */
    @NotBlank(message = "Branch name is required")
    @Size(min = 1, max = 100, message = "Branch name must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_/-]+$", message = "Branch name can only contain letters, numbers, underscores, hyphens and slashes")
    private String branchName;

    /**
     * Whether to create the branch if it doesn't exist.
     * Default: false
     */
    @Builder.Default
    private boolean createIfNotExists = false;

    /**
     * If createIfNotExists is true, this specifies the commit to create from.
     * If not specified, uses current HEAD.
     */
    @Size(max = 128, message = "Commit ID must not exceed 128 characters")
    private String fromCommitId;
}
