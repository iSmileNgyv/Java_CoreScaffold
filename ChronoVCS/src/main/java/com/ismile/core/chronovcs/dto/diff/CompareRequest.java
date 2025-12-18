package com.ismile.core.chronovcs.dto.diff;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareRequest {

    /**
     * Base reference (commit ID, branch name, or tag).
     */
    private String base;

    /**
     * Head reference (commit ID, branch name, or tag).
     */
    private String head;

    /**
     * Whether to include patch/diff content for each file.
     * Default: false (only show file list and stats).
     */
    @Builder.Default
    private boolean includePatch = false;

    /**
     * Maximum number of files to return.
     * Default: 300
     */
    @Builder.Default
    private int maxFiles = 300;
}
