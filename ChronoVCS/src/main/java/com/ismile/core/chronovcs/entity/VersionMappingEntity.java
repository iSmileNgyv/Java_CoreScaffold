package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chronovcs_version_mapping",
       uniqueConstraints = @UniqueConstraint(
           name = "unique_mapping",
           columnNames = {"task_integration_id", "field_name", "field_value"}
       ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_integration_id", nullable = false)
    private TaskIntegrationEntity taskIntegration;

    @Column(name = "field_name", nullable = false)
    private String fieldName;  // "issuetype", "priority", "WorkItemType", "type"

    @Column(name = "field_value", nullable = false)
    private String fieldValue; // "Bug", "Story", "Epic"

    @Column(name = "version_type", nullable = false)
    private String versionType; // MAJOR, MINOR, PATCH

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
