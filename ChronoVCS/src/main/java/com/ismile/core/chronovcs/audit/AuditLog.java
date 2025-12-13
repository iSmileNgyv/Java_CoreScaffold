package com.ismile.core.chronovcs.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Audit Log Entity
 *
 * Stores security and operational audit events for:
 * - Authentication (login, logout, failed attempts)
 * - Authorization (permission denied, role changes)
 * - Data changes (create, update, delete)
 * - Security events (rate limit, suspicious activity)
 * - System events (startup, shutdown, errors)
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_user", columnList = "user_id"),
        @Index(name = "idx_audit_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_severity", columnList = "severity")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Event timestamp
     */
    @Column(nullable = false)
    private Instant timestamp;

    /**
     * Event type/category
     */
    @Column(name = "event_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    /**
     * Event severity level
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Severity severity;

    /**
     * User who performed the action (if applicable)
     */
    @Column(name = "user_id", length = 100)
    private String userId;

    /**
     * User email (for easier searching)
     */
    @Column(name = "user_email", length = 255)
    private String userEmail;

    /**
     * Client IP address
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Action performed
     */
    @Column(nullable = false, length = 100)
    private String action;

    /**
     * Resource affected (repository, file, etc.)
     */
    @Column(length = 255)
    private String resource;

    /**
     * Detailed description
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Additional metadata (JSON format)
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * Success or failure
     */
    @Column(nullable = false)
    private Boolean success;

    /**
     * Error message (if failed)
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Event types
     */
    public enum EventType {
        // Authentication events
        LOGIN,
        LOGOUT,
        LOGIN_FAILED,
        TOKEN_CREATED,
        TOKEN_REVOKED,
        PASSWORD_CHANGED,

        // Authorization events
        PERMISSION_DENIED,
        ROLE_CHANGED,

        // Repository events
        REPOSITORY_CREATED,
        REPOSITORY_DELETED,
        REPOSITORY_CLONED,
        REPOSITORY_PUSHED,
        REPOSITORY_PULLED,

        // Integration events
        INTEGRATION_CREATED,
        INTEGRATION_UPDATED,
        INTEGRATION_DELETED,
        INTEGRATION_EXECUTED,

        // Security events
        RATE_LIMIT_EXCEEDED,
        SUSPICIOUS_ACTIVITY,
        ENCRYPTION_KEY_ROTATED,

        // System events
        SYSTEM_STARTUP,
        SYSTEM_SHUTDOWN,
        SYSTEM_ERROR
    }

    /**
     * Severity levels
     */
    public enum Severity {
        DEBUG,    // Debug information
        INFO,     // Normal operations
        WARN,     // Warning events
        ERROR,    // Error events
        CRITICAL  // Critical security/system events
    }

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
