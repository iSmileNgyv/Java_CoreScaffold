package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chronovcs_tasks",
       uniqueConstraints = @UniqueConstraint(
           name = "unique_external_task",
           columnNames = {"external_id", "task_integration_id"}
       ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;     // PROJ-123, WI-456, etc.

    @Column(name = "external_type", length = 100)
    private String externalType;   // Bug, Story, Feature, Epic

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "version_type", length = 10)
    private String versionType;    // MAJOR, MINOR, PATCH (calculated)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_integration_id")
    private TaskIntegrationEntity taskIntegration;

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
