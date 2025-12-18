package com.ismile.core.chronovcs.dto.diff;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffResponse {

    /**
     * Base commit ID (old version).
     */
    private String baseCommitId;

    /**
     * Head commit ID (new version).
     */
    private String headCommitId;

    /**
     * Base commit message.
     */
    private String baseCommitMessage;

    /**
     * Head commit message.
     */
    private String headCommitMessage;

    /**
     * List of file differences.
     */
    private List<FileDiff> files;

    /**
     * Diff statistics summary.
     */
    private DiffStats stats;

    /**
     * Whether the commits are identical (no changes).
     */
    private boolean identical;
}
