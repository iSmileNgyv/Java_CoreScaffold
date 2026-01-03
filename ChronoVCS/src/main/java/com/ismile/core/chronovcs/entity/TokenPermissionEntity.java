package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chronovcs_token_permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_token_permission_token_repo",
                        columnNames = {"token_id", "repository_id"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenPermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "token_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_token_permission_token"))
    private UserTokenEntity token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_token_permission_repository"))
    private RepositoryEntity repository;

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
