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
public class MergeBranchRequest {

    /**
     * Name of the source branch to merge from.
     */
    @NotBlank(message = "Source branch name is required")
    @Size(min = 1, max = 100, message = "Branch name must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_/-]+$", message = "Branch name can only contain letters, numbers, underscores, hyphens and slashes")
    private String sourceBranch;

    /**
     * Name of the target branch to merge into.
     * If not specified, merges into current branch.
     */
    @Size(max = 100, message = "Target branch name must not exceed 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_/-]+$", message = "Branch name can only contain letters, numbers, underscores, hyphens and slashes")
    private String targetBranch;

    /**
     * Merge commit message.
     */
    @Size(max = 500, message = "Merge message must not exceed 500 characters")
    private String message;

    /**
     * Merge strategy: "fast-forward", "no-ff" (no fast-forward), "squash".
     * Default: "no-ff"
     */
    @Pattern(regexp = "^(fast-forward|no-ff|squash)$", message = "Merge strategy must be 'fast-forward', 'no-ff', or 'squash'")
    @Builder.Default
    private String strategy = "no-ff";

    /**
     * Whether to automatically resolve conflicts (if possible).
     * Default: false
     */
    @Builder.Default
    private boolean autoResolve = false;
}
