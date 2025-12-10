package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chronovcs_task_integration_headers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskIntegrationHeaderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_integration_id", nullable = false)
    private TaskIntegrationEntity taskIntegration;

    @Column(name = "header_name", nullable = false, length = 100)
    private String headerName;

    @Column(name = "header_value", nullable = false, columnDefinition = "TEXT")
    private String headerValue;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
