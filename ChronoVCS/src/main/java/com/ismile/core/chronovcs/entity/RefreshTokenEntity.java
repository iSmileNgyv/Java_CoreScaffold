package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Refresh Token Entity
 * Database table: refresh_tokens
 *
 * JWT refresh token-ları üçün (Next.js)
 */
@Entity
@Table(name = "chronovcs_refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Token owner
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * Token hash (SHA-256)
     * Actual token client-də saxlanır
     */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    /**
     * Device info (user-agent-dən çıxarılır)
     * Nümunə: "Chrome on macOS"
     */
    @Column(name = "device_info")
    private String deviceInfo;

    /**
     * IP address (token yaradılan zaman)
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent string (full)
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Token expiration
     * Məsələn: 7 gün, 30 gün
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Revoked timestamp (logout)
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * Last used timestamp
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Token valid mi?
     */
    @Transient
    public boolean isValid() {
        if (revokedAt != null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * Revoke token
     */
    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }
}