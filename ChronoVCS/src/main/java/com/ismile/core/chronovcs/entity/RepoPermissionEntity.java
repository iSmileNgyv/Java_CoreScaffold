package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chronovcs_repo_permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_repo_permission_user_repo",
                        columnNames = {"user_id", "repository_id"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepoPermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Which user/system this permission belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_repo_permission_users")
    )
    private UserEntity userSettings;

    /**
     * Which repository this permission is for.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "repository_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_repo_permission_repository")
    )
    private RepositoryEntity repository;

    // ---- Permission flags ----

    @Column(name = "can_read", nullable = false)
    private boolean canRead;

    @Column(name = "can_pull", nullable = false)
    private boolean canPull;

    @Column(name = "can_push", nullable = false)
    private boolean canPush;

    @Column(name = "can_create_branch", nullable = false)
    private boolean canCreateBranch;

    @Column(name = "can_delete_branch", nullable = false)
    private boolean canDeleteBranch;

    @Column(name = "can_merge", nullable = false)
    private boolean canMerge;

    @Column(name = "can_create_tag", nullable = false)
    private boolean canCreateTag;

    @Column(name = "can_delete_tag", nullable = false)
    private boolean canDeleteTag;

    @Column(name = "can_manage_repo", nullable = false)
    private boolean canManageRepo;

    @Column(name = "can_bypass_task_policy", nullable = false)
    private boolean canBypassTaskPolicy;

    // ---- Audit fields ----

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}