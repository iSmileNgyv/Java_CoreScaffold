package com.ismile.core.chronovcs.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Audit Service
 *
 * Provides async audit logging for security and operational events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Log authentication event (async)
     */
    @Async
    public void logAuth(
            AuditLog.EventType eventType,
            String userId,
            String userEmail,
            boolean success,
            String errorMessage
    ) {
        String ipAddress = getCurrentIpAddress();

        AuditLog auditLog = AuditLog.builder()
                .timestamp(Instant.now())
                .eventType(eventType)
                .severity(success ? AuditLog.Severity.INFO : AuditLog.Severity.WARN)
                .userId(userId)
                .userEmail(userEmail)
                .ipAddress(ipAddress)
                .action(eventType.name())
                .success(success)
                .errorMessage(errorMessage)
                .build();

        save(auditLog);
    }

    /**
     * Log repository operation (async)
     */
    @Async
    public void logRepositoryOperation(
            AuditLog.EventType eventType,
            String userId,
            String repositoryKey,
            String action,
            boolean success,
            String errorMessage
    ) {
        AuditLog auditLog = AuditLog.builder()
                .timestamp(Instant.now())
                .eventType(eventType)
                .severity(AuditLog.Severity.INFO)
                .userId(userId)
                .ipAddress(getCurrentIpAddress())
                .action(action)
                .resource(repositoryKey)
                .success(success)
                .errorMessage(errorMessage)
                .build();

        save(auditLog);
    }

    /**
     * Log security event (async)
     */
    @Async
    public void logSecurityEvent(
            AuditLog.EventType eventType,
            String description,
            AuditLog.Severity severity,
            Map<String, Object> metadata
    ) {
        String ipAddress = getCurrentIpAddress();

        AuditLog auditLog = AuditLog.builder()
                .timestamp(Instant.now())
                .eventType(eventType)
                .severity(severity)
                .ipAddress(ipAddress)
                .action(eventType.name())
                .description(description)
                .metadata(toJson(metadata))
                .success(true)
                .build();

        save(auditLog);
    }

    /**
     * Log rate limit exceeded event
     */
    @Async
    public void logRateLimitExceeded(String endpoint) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("endpoint", endpoint);

        logSecurityEvent(
                AuditLog.EventType.RATE_LIMIT_EXCEEDED,
                "Rate limit exceeded for endpoint: " + endpoint,
                AuditLog.Severity.WARN,
                metadata
        );
    }

    /**
     * Log permission denied event
     */
    @Async
    public void logPermissionDenied(String userId, String resource, String action) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("resource", resource);
        metadata.put("action", action);

        AuditLog auditLog = AuditLog.builder()
                .timestamp(Instant.now())
                .eventType(AuditLog.EventType.PERMISSION_DENIED)
                .severity(AuditLog.Severity.WARN)
                .userId(userId)
                .ipAddress(getCurrentIpAddress())
                .action(action)
                .resource(resource)
                .description("Permission denied")
                .metadata(toJson(metadata))
                .success(false)
                .build();

        save(auditLog);
    }

    /**
     * Get recent security events (last 24 hours)
     */
    public List<AuditLog> getRecentSecurityEvents() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        return auditLogRepository.findSecurityEvents(since);
    }

    /**
     * Get recent audit logs
     */
    public List<AuditLog> getRecentLogs(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        return auditLogRepository.findRecentLogs(since);
    }

    /**
     * Check if IP has too many failed login attempts
     */
    public boolean hasExcessiveFailedLogins(String ipAddress) {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        long failedCount = auditLogRepository.countFailedLoginsByIp(ipAddress, since);
        return failedCount >= 10; // 10 failed attempts in 1 hour = suspicious
    }

    /**
     * Save audit log to database
     */
    private void save(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
            log.debug("Audit log saved: {} - {} - {}", auditLog.getEventType(), auditLog.getAction(), auditLog.getSuccess());
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage(), e);
        }
    }

    /**
     * Get current IP address from HTTP request
     */
    private String getCurrentIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return getClientIp(request);
            }
        } catch (Exception e) {
            log.debug("Could not get IP address from request: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Extract client IP from request (handles proxy headers)
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }

    /**
     * Convert map to JSON string
     */
    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("Failed to convert metadata to JSON: {}", e.getMessage());
            return null;
        }
    }
}
