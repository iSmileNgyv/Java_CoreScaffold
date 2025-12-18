package com.ismile.core.chronovcs.dto.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchOperationResponse {

    /**
     * Whether the operation was successful.
     */
    private boolean success;

    /**
     * Operation message.
     */
    private String message;

    /**
     * The branch that was affected by the operation.
     */
    private BranchResponse branch;

    /**
     * Additional operation details (e.g., merge conflicts, commits affected).
     */
    private String details;
}
