package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chronovcs_releases",
       uniqueConstraints = @UniqueConstraint(
           name = "unique_repo_version",
           columnNames = {"repository_id", "version"}
       ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryEntity repository;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(name = "snapshot_commit_id", length = 64)
    private String snapshotCommitId;

    @Column(name = "version_type", length = 10)
    private String versionType; // MAJOR, MINOR, PATCH

    @Column(columnDefinition = "TEXT")
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @OneToMany(mappedBy = "release", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReleaseTaskEntity> tasks = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
