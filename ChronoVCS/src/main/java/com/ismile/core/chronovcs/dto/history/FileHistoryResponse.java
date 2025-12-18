package com.ismile.core.chronovcs.dto.history;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileHistoryResponse {

    /**
     * File path.
     */
    private String filePath;

    /**
     * Repository key.
     */
    private String repoKey;

    /**
     * Total number of commits affecting this file.
     */
    private int totalCommits;

    /**
     * List of commits that modified this file (ordered newest first).
     */
    private List<FileHistoryEntry> commits;

    /**
     * Whether this file currently exists in the HEAD.
     */
    private boolean exists;

    /**
     * Current blob hash (if exists).
     */
    private String currentBlobHash;
}
