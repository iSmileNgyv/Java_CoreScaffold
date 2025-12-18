package com.ismile.core.chronovcs.dto.diff;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffStats {

    /**
     * Total number of files changed.
     */
    private int filesChanged;

    /**
     * Number of files added.
     */
    private int filesAdded;

    /**
     * Number of files modified.
     */
    private int filesModified;

    /**
     * Number of files deleted.
     */
    private int filesDeleted;

    /**
     * Total lines added across all files.
     */
    private int totalLinesAdded;

    /**
     * Total lines deleted across all files.
     */
    private int totalLinesDeleted;

    /**
     * Total number of changes (additions + deletions).
     */
    private int totalChanges;
}
