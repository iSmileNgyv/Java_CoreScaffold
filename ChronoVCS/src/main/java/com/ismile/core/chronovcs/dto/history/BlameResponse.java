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
public class BlameResponse {

    /**
     * File path.
     */
    private String filePath;

    /**
     * Repository key.
     */
    private String repoKey;

    /**
     * Commit ID being blamed.
     */
    private String commitId;

    /**
     * Branch name (if applicable).
     */
    private String branch;

    /**
     * Total number of lines in the file.
     */
    private int totalLines;

    /**
     * List of blame information for each line.
     */
    private List<BlameLine> lines;

    /**
     * Whether the file is binary.
     */
    private boolean binary;

    /**
     * Number of unique commits that contributed to this file.
     */
    private int uniqueCommits;

    /**
     * Number of unique authors.
     */
    private int uniqueAuthors;
}
