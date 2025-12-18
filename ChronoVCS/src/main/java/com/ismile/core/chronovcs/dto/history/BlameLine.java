package com.ismile.core.chronovcs.dto.history;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlameLine {

    /**
     * Line number (1-indexed).
     */
    private int lineNumber;

    /**
     * Commit ID that introduced this line.
     */
    private String commitId;

    /**
     * Commit message (shortened).
     */
    private String commitMessage;

    /**
     * Author who wrote this line.
     */
    private String author;

    /**
     * When this line was written.
     */
    private String timestamp;

    /**
     * The actual line content.
     */
    private String content;

    /**
     * Number of commits ago this line was written (0 = latest commit).
     */
    private int age;
}
