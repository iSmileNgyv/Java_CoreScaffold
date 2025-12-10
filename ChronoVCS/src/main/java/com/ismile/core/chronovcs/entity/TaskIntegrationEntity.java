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
@Table(name = "chronovcs_task_integration")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskIntegrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TaskIntegrationType type;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    // JIRA-specific fields
    @Column(name = "jira_url")
    private String jiraUrl;

    @Column(name = "jira_auth_type", length = 20)
    private String jiraAuthType;

    @Column(name = "jira_username", length = 100)
    private String jiraUsername;

    @Column(name = "jira_password_encrypted", columnDefinition = "TEXT")
    private String jiraPasswordEncrypted;

    @Column(name = "jira_api_token_encrypted", columnDefinition = "TEXT")
    private String jiraApiTokenEncrypted;

    // Generic integration fields
    @Column(name = "api_url", length = 500)
    private String apiUrl;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @OneToMany(mappedBy = "taskIntegration", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TaskIntegrationHeaderEntity> headers = new ArrayList<>();

    @OneToOne(mappedBy = "taskIntegration", cascade = CascadeType.ALL, orphanRemoval = true)
    private TaskIntegrationRequestEntity request;

    @OneToOne(mappedBy = "taskIntegration", cascade = CascadeType.ALL, orphanRemoval = true)
    private TaskIntegrationResponseEntity response;

    @OneToMany(mappedBy = "taskIntegration", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VersionMappingEntity> versionMappings = new ArrayList<>();

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
