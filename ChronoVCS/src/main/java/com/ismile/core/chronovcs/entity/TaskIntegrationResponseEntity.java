package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chronovcs_task_integration_response")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskIntegrationResponseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_integration_id", nullable = false, unique = true)
    private TaskIntegrationEntity taskIntegration;

    @Column(name = "task_id_path", nullable = false, length = 200)
    private String taskIdPath;      // JSONPath: "$.id" or "$.tasks[*].key"

    @Column(name = "task_type_path", nullable = false, length = 200)
    private String taskTypePath;    // JSONPath: "$.type" or "$.tasks[*].type"

    @Column(name = "task_title_path", length = 200)
    private String taskTitlePath;   // JSONPath: "$.title" (optional)

    @Column(name = "task_description_path", length = 200)
    private String taskDescriptionPath;   // JSONPath: "$.description" (optional)

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
