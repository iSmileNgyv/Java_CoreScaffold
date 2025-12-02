package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chronovcs_user_tokens", indexes = {
        @Index(name = "idx_token_prefix", columnList = "token_prefix")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link to the user who owns this token
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    // Friendly name, e.g., "My MacBook Pro" or "CI Server"
    @Column(name = "token_name", nullable = false)
    private String tokenName;

    // We store the BCrypt hash of the token, NOT the raw token
    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    // We store the prefix (e.g., "cvcs_abc123") for fast DB lookups
    @Column(name = "token_prefix", nullable = false, length = 50)
    private String tokenPrefix;

    // Optional expiration date
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "is_revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Helper to check validity
    public boolean isValid() {
        if (revoked) return false;
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) return false;
        return true;
    }
}