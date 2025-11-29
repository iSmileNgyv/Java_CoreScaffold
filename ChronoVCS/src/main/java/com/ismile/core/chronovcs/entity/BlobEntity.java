package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chronovcs_blobs",
        indexes = {
                @Index(name = "idx_blob_repo_hash", columnList = "repository_id, hash")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_blob_repo_hash",
                        columnNames = {"repository_id", "hash"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Repository that owns this blob.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "repository_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_blob_repository")
    )
    private RepositoryEntity repository;

    /**
     * Content hash (e.g. SHA-256) in hex form.
     */
    @Column(name = "hash", nullable = false, length = 128)
    private String hash;

    /**
     * Where the actual bytes are stored and how.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 32)
    private StorageType storageType;

    /**
     * Storage-specific key/path.
     * For LOCAL this can be relative path from base directory:
     *   <repoKey>/<prefix>/<restOfHash>
     */
    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "content_size")
    private Long contentSize;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}