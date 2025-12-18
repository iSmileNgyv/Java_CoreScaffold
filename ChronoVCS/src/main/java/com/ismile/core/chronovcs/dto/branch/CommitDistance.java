package com.ismile.core.chronovcs.dto.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitDistance {

    /**
     * Number of commits ahead.
     */
    private int ahead;

    /**
     * Number of commits behind.
     */
    private int behind;

    /**
     * Common ancestor commit ID.
     */
    private String mergeBase;
}
