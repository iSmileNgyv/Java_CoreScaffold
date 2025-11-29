package com.ismile.core.chronovcs.repository;

import com.ismile.core.chronovcs.entity.AuthLogEntity;
import com.ismile.core.chronovcs.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuthLogRepository extends JpaRepository<AuthLogEntity, Long> {

    /**
     * Find logs by user with pagination
     */
    Page<AuthLogEntity> findByUserOrderByCreatedAtDesc(UserEntity user, Pageable pageable);

    /**
     * Find recent logs by user (last N)
     */
    List<AuthLogEntity> findTop10ByUserOrderByCreatedAtDesc(UserEntity user);

    /**
     * Find failed login attempts by email in time window
     */
    @Query("SELECT COUNT(al) FROM AuthLogEntity al " +
            "WHERE al.email = :email " +
            "AND al.action = 'LOGIN_FAILED' " +
            "AND al.createdAt > :since")
    long countFailedLoginAttempts(@Param("email") String email, @Param("since") LocalDateTime since);

    /**
     * Find logs by action type
     */
    List<AuthLogEntity> findByActionOrderByCreatedAtDesc(String action);

    /**
     * Delete old logs (cleanup job - keep only last N days)
     */
    @Query("DELETE FROM AuthLogEntity al WHERE al.createdAt < :cutoffDate")
    void deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find recent failed login attempts from IP
     */
    @Query("SELECT COUNT(al) FROM AuthLogEntity al " +
            "WHERE al.ipAddress = :ipAddress " +
            "AND al.action = 'LOGIN_FAILED' " +
            "AND al.createdAt > :since")
    long countFailedLoginAttemptsFromIp(@Param("ipAddress") String ipAddress, @Param("since") LocalDateTime since);
}