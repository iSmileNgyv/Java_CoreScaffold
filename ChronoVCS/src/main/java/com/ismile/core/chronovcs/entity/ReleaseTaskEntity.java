package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chronovcs_release_tasks",
       uniqueConstraints = @UniqueConstraint(
           name = "unique_release_task",
           columnNames = {"release_id", "jira_issue_key"}
       ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "release_id", nullable = false)
    private ReleaseEntity release;

    @Column(name = "jira_issue_key", nullable = false, length = 50)
    private String jiraIssueKey; // e.g., "PROJ-123"

    @Column(name = "jira_issue_type", length = 50)
    private String jiraIssueType; // e.g., "Bug", "Story"

    @Column(name = "version_type", length = 10)
    private String versionType; // MAJOR, MINOR, PATCH

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
