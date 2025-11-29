package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * External unique identifier (UUID)
     * API response-larda bu göstərilir, internal ID yox
     */
    @Column(name = "user_uid", nullable = false, unique = true)
    private String userUid;

    /**
     * Email (login üçün)
     */
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /**
     * Password hash (BCrypt)
     * CLI-only user-lər üçün null ola bilər
     */
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * Display name (optional)
     */
    @Column(name = "display_name")
    private String displayName;

    /**
     * Active flag
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Email verified flag (gələcək üçün)
     */
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (userUid == null) {
            userUid = UUID.randomUUID().toString();
        }
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
}