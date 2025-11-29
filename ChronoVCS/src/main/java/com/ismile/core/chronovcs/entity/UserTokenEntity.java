package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * User Token Entity (Personal Access Token)
 * Database table: user_tokens
 *
 * CLI authentication üçün token-lar
 */
@Entity
@Table(name = "user_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTokenEntity {

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
     * Token adı (user-friendly)
     * Nümunə: "my-laptop", "ci-server", "office-desktop"
     */
    @Column(name = "token_name", nullable = false, length = 100)
    private String tokenName;

    /**
     * Token hash (BCrypt)
     * Actual token heç vaxt DB-də saxlanmır
     */
    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    /**
     * Token prefix (fast lookup üçün)
     * Nümunə: "cvcs_abc1"
     * DB index var bu sütunda
     */
    @Column(name = "token_prefix", nullable = false, length = 20)
    private String tokenPrefix;

    /**
     * Token expiration (null = never expires)
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Last used timestamp
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /**
     * IP address from last use
     */
    @Column(name = "last_used_ip", length = 45)
    private String lastUsedIp;

    /**
     * Revoked flag
     */
    @Column(name = "is_revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

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
     * Token valid mi? (expired və ya revoked deyil)
     */
    @Transient
    public boolean isValid() {
        if (revoked) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }
}