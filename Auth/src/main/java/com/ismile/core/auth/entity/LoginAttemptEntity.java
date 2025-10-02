package com.ismile.core.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Login Attempt entity for brute force protection
 * Tracks failed login attempts per user
 */
@Entity
@Table(name = "login_attempts", indexes = {
        @Index(name = "idx_username", columnList = "username"),
        @Index(name = "idx_ip_address", columnList = "ip_address"),
        @Index(name = "idx_attempt_time", columnList = "attempt_time")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "success", nullable = false)
    private boolean success;

    @CreationTimestamp
    @Column(name = "attempt_time", nullable = false, updatable = false)
    private LocalDateTime attemptTime;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;
}