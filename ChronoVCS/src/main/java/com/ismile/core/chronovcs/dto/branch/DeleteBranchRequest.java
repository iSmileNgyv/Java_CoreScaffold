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
public class DeleteBranchRequest {

    /**
     * Name of the branch to delete.
     */
    @NotBlank(message = "Branch name is required")
    @Size(min = 1, max = 100, message = "Branch name must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_/-]+$", message = "Branch name can only contain letters, numbers, underscores, hyphens and slashes")
    private String branchName;

    /**
     * Force delete even if the branch has unmerged changes.
     * Default: false
     */
    @Builder.Default
    private boolean force = false;
}
