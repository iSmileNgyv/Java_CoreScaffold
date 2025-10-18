package com.ismile.core.otp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity to store user-specific settings.
 * Initially, it will store the preferred delivery method for OTPs.
 */
@Entity
@Table(name = "user_settings", indexes = {
        @Index(name = "idx_user_id_unique", columnList = "user_id", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private int userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false)
    private DeliveryMethod deliveryMethod;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
