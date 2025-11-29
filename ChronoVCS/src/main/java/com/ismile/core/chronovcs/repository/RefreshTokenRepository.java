package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.RefreshTokenEntity;
import com.ismile.core.chronovcs.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    /**
     * Find refresh token by hash
     */
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Find all active refresh tokens for a user
     */
    @Query("SELECT rt FROM RefreshTokenEntity rt WHERE rt.user = :user " +
            "AND rt.revokedAt IS NULL " +
            "AND rt.expiresAt > :now " +
            "ORDER BY rt.createdAt DESC")
    List<RefreshTokenEntity> findActiveTokensByUser(@Param("user") UserEntity user, @Param("now") LocalDateTime now);

    /**
     * Find all refresh tokens for user (including expired/revoked)
     */
    List<RefreshTokenEntity> findByUserOrderByCreatedAtDesc(UserEntity user);

    /**
     * Revoke all active refresh tokens for a user (logout from all devices)
     */
    @Modifying
    @Query("UPDATE RefreshTokenEntity rt SET rt.revokedAt = :now " +
            "WHERE rt.user = :user AND rt.revokedAt IS NULL")
    void revokeAllByUser(@Param("user") UserEntity user, @Param("now") LocalDateTime now);

    /**
     * Delete expired tokens (cleanup job)
     */
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity rt WHERE rt.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Delete revoked tokens older than N days (cleanup)
     */
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity rt WHERE rt.revokedAt IS NOT NULL " +
            "AND rt.revokedAt < :cutoffDate")
    void deleteOldRevokedTokens(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count active refresh tokens for user
     */
    @Query("SELECT COUNT(rt) FROM RefreshTokenEntity rt WHERE rt.user = :user " +
            "AND rt.revokedAt IS NULL AND rt.expiresAt > :now")
    long countActiveTokensByUser(@Param("user") UserEntity user, @Param("now") LocalDateTime now);
}