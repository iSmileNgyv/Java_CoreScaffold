package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chronovcs_repositories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_repository_key", columnNames = "repo_key")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique repository key (slug), e.g. "core-backend", "erp-main".
     */
    @Column(name = "repo_key", nullable = false, unique = true, length = 255)
    private String repoKey;

    /**
     * Human-readable repository name.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Optional description for UI and documentation.
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Whether this repository is private.
     * If false, read operations may not require token (depending on your policy).
     */
    @Column(name = "is_private", nullable = false)
    private boolean privateRepo;

    /**
     * Versioning mode for this repository.
     * PROJECT = classic VCS
     * OBJECT  = object-level history enabled
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "versioning_mode", nullable = false, length = 32)
    private VersioningMode versioningMode;

    /**
     * Default branch name, e.g. "main", "master", "trunk".
     */
    @Column(name = "default_branch", nullable = false, length = 255)
    private String defaultBranch;

    /**
     * Owner of the repository (user or system account).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "owner_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_repository_owner")
    )
    private UserEntity owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 32)
    private StorageType storageType = StorageType.LOCAL;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = true)
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