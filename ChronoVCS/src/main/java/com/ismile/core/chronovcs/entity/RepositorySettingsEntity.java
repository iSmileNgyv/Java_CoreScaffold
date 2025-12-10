package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chronovcs_repository_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositorySettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", unique = true, nullable = false)
    private RepositoryEntity repository;

    @Column(name = "task_required")
    @Builder.Default
    private Boolean taskRequired = false;

    @Column(name = "auto_increment")
    @Builder.Default
    private Boolean autoIncrement = true;

    @Column(name = "enforce_semantic_versioning")
    @Builder.Default
    private Boolean enforceSemanticVersioning = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
