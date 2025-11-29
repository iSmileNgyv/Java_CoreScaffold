package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chronovcs_users")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_uid", nullable = false, unique = true)
    private String userUid;
    @Column(name = "username", nullable = false, unique = true)
    private String username;
    @Column(length = 100)
    private String email;
    @Column(name = "token_hash", length = 500)
    private String tokenHash;
    @Column(name = "is_active", nullable = false)
    private boolean isActive;
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 32)
    private UserType userType;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @PrePersist
    public void prePersist() {
        if(createdAt == null)
            createdAt = LocalDateTime.now();
    }
}
