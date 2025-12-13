package com.ismile.core.chronovcs.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find audit logs for specific user
     */
    List<AuditLog> findByUserIdOrderByTimestampDesc(String userId);

    /**
     * Find audit logs by event type
     */
    List<AuditLog> findByEventTypeOrderByTimestampDesc(AuditLog.EventType eventType);

    /**
     * Find failed events for security monitoring
     */
    List<AuditLog> findBySuccessFalseAndSeverityOrderByTimestampDesc(AuditLog.Severity severity);

    /**
     * Find recent audit logs
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentLogs(@Param("since") Instant since);

    /**
     * Find security events (login failures, permission denied, etc.)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.eventType IN " +
           "('LOGIN_FAILED', 'PERMISSION_DENIED', 'RATE_LIMIT_EXCEEDED', 'SUSPICIOUS_ACTIVITY') " +
           "AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findSecurityEvents(@Param("since") Instant since);

    /**
     * Count failed login attempts for specific IP
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.eventType = 'LOGIN_FAILED' " +
           "AND a.ipAddress = :ip AND a.timestamp >= :since")
    long countFailedLoginsByIp(@Param("ip") String ipAddress, @Param("since") Instant since);
}
