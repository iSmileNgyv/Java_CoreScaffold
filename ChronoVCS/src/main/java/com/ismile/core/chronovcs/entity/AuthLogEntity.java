package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Auth Log Entity
 * Database table: auth_logs
 *
 * Security audit trail - bütün auth event-lərini log edir
 */
@Entity
@Table(name = "auth_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User (nullable - failed login-də user tapılmaya bilər)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    /**
     * Email (failed attempt-lər üçün)
     */
    @Column(name = "email")
    private String email;

    /**
     * Action type
     * Nümunələr:
     * - LOGIN_SUCCESS
     * - LOGIN_FAILED
     * - TOKEN_CREATED
     * - TOKEN_REVOKED
     * - REFRESH_TOKEN_USED
     * - LOGOUT
     */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    /**
     * IP address
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Success flag
     */
    @Column(name = "success", nullable = false)
    private boolean success;

    /**
     * Error message (failed attempts üçün)
     */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Static helper methods

    public static AuthLogEntity loginSuccess(UserEntity user, String ipAddress, String userAgent) {
        return AuthLogEntity.builder()
                .user(user)
                .email(user.getEmail())
                .action("LOGIN_SUCCESS")
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(true)
                .build();
    }

    public static AuthLogEntity loginFailed(String email, String ipAddress, String userAgent, String reason) {
        return AuthLogEntity.builder()
                .email(email)
                .action("LOGIN_FAILED")
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(false)
                .errorMessage(reason)
                .build();
    }

    public static AuthLogEntity tokenCreated(UserEntity user, String tokenName, String ipAddress) {
        return AuthLogEntity.builder()
                .user(user)
                .email(user.getEmail())
                .action("TOKEN_CREATED")
                .ipAddress(ipAddress)
                .success(true)
                .errorMessage("Token: " + tokenName)
                .build();
    }

    public static AuthLogEntity tokenRevoked(UserEntity user, String tokenName) {
        return AuthLogEntity.builder()
                .user(user)
                .email(user.getEmail())
                .action("TOKEN_REVOKED")
                .success(true)
                .errorMessage("Token: " + tokenName)
                .build();
    }
}