package com.ismile.core.chronovcs.dto.pullrequest;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MergePullRequestRequest {

    @Size(max = 500, message = "Merge message must not exceed 500 characters")
    private String message;

    @Pattern(regexp = "^(fast-forward|no-ff|squash)$",
            message = "Merge strategy must be 'fast-forward', 'no-ff', or 'squash'")
    private String strategy;

    private boolean autoResolve;
}
