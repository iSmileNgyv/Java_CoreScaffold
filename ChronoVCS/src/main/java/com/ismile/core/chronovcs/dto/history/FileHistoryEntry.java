package com.ismile.core.chronovcs.dto.history;

import com.ismile.core.chronovcs.dto.diff.ChangeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileHistoryEntry {

    /**
     * Commit ID.
     */
    private String commitId;

    /**
     * Commit message.
     */
    private String message;

    /**
     * Commit author (if available).
     */
    private String author;

    /**
     * Commit timestamp.
     */
    private String timestamp;

    /**
     * Branch name.
     */
    private String branch;

    /**
     * Type of change to this file in this commit.
     */
    private ChangeType changeType;

    /**
     * Old file path (if renamed).
     */
    private String oldPath;

    /**
     * New file path.
     */
    private String newPath;

    /**
     * Blob hash of the file in this commit.
     */
    private String blobHash;

    /**
     * Lines added in this commit.
     */
    private Integer linesAdded;

    /**
     * Lines deleted in this commit.
     */
    private Integer linesDeleted;

    /**
     * When this commit was created.
     */
    private LocalDateTime createdAt;
}
