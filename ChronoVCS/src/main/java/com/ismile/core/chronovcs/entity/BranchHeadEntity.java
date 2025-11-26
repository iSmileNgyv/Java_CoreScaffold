package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chronovcs_branch_heads",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_branch_head_repo_branch",
                        columnNames = {"repository_id", "branch"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchHeadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Repository this branch belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "repository_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_branch_head_repository")
    )
    private RepositoryEntity repository;

    /**
     * Branch name.
     */
    @Column(name = "branch", nullable = false, length = 255)
    private String branch;

    /**
     * HEAD commit id for this branch (may be null if empty).
     */
    @Column(name = "head_commit_id", length = 128)
    private String headCommitId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void preUpdate() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        } else {
            updatedAt = LocalDateTime.now();
        }
    }
}