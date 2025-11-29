package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chronovcs_commits",
        indexes = {
                @Index(name = "idx_commit_repo_commit_id", columnList = "repository_id, commit_id"),
                @Index(name = "idx_commit_repo_branch", columnList = "repository_id, branch")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Which repository this commit belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "repository_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_commit_repository")
    )
    private RepositoryEntity repository;

    /**
     * Commit id (hash) generated on client side.
     */
    @Column(name = "commit_id", nullable = false, length = 128)
    private String commitId;

    /**
     * Parent commit id (may be null for first commit).
     */
    @Column(name = "parent_commit_id", length = 128)
    private String parentCommitId;

    /**
     * Branch name, e.g. "main".
     */
    @Column(name = "branch", nullable = false, length = 255)
    private String branch;

    /**
     * Commit message.
     */
    @Column(name = "message", length = 2000)
    private String message;

    /**
     * Timestamp string (ISO-8601).
     */
    @Column(name = "timestamp", length = 64)
    private String timestamp;

    /**
     * Snapshot of files: JSON serialized map (filename -> blobHash).
     */
    @Lob
    @Column(name = "files_json", nullable = false)
    private String filesJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}