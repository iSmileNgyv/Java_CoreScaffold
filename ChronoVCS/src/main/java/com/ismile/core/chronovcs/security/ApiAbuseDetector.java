package com.ismile.core.chronovcs.security;

import com.ismile.core.chronovcs.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API Abuse Detector
 *
 * Detects suspicious patterns that Cloudflare might miss:
 * 1. Rapid sequential requests to same resource
 * 2. Unusual API call patterns
 * 3. Data scraping attempts
 * 4. Automated bot behavior
 * 5. Business logic abuse
 *
 * Works alongside Cloudflare for application-layer protection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiAbuseDetector {

    private final AuditService auditService;

    // Track request patterns per IP
    private final Map<String, RequestPattern> requestPatterns = new ConcurrentHashMap<>();

    // Track failed operations per user
    private final Map<String, FailureTracker> failureTrackers = new ConcurrentHashMap<>();

    /**
     * Track API request for abuse detection
     *
     * @param ipAddress Client IP
     * @param endpoint API endpoint
     * @param userId User ID (if authenticated)
     */
    public void trackRequest(String ipAddress, String endpoint, String userId) {
        RequestPattern pattern = requestPatterns.computeIfAbsent(ipAddress, k -> new RequestPattern());
        pattern.recordRequest(endpoint);

        // Check for suspicious patterns
        if (pattern.isSuspicious()) {
            log.warn("Suspicious API usage detected from IP: {}, pattern: {}", ipAddress, pattern);
            auditService.logSecurityEvent(
                    com.ismile.core.chronovcs.audit.AuditLog.EventType.SUSPICIOUS_ACTIVITY,
                    "Suspicious API usage pattern detected",
                    com.ismile.core.chronovcs.audit.AuditLog.Severity.WARN,
                    Map.of("ip", ipAddress, "requestCount", pattern.getRequestCount())
            );
        }
    }

    /**
     * Track failed operation (login, permission, etc.)
     *
     * @param identifier IP or User ID
     * @param operationType Operation type
     * @return true if threshold exceeded (should block)
     */
    public boolean trackFailure(String identifier, String operationType) {
        FailureTracker tracker = failureTrackers.computeIfAbsent(identifier, k -> new FailureTracker());
        tracker.recordFailure(operationType);

        if (tracker.isThresholdExceeded()) {
            log.error("Failure threshold exceeded for {}: {}", identifier, operationType);
            auditService.logSecurityEvent(
                    com.ismile.core.chronovcs.audit.AuditLog.EventType.SUSPICIOUS_ACTIVITY,
                    "Excessive failures detected: " + operationType,
                    com.ismile.core.chronovcs.audit.AuditLog.Severity.CRITICAL,
                    Map.of("identifier", identifier, "operationType", operationType)
            );
            return true;
        }
        return false;
    }

    /**
     * Check if IP is scraping (too many read requests)
     *
     * @param ipAddress Client IP
     * @return true if scraping detected
     */
    public boolean isScrapingDetected(String ipAddress) {
        RequestPattern pattern = requestPatterns.get(ipAddress);
        if (pattern != null && pattern.getRequestCount() > 1000) { // 1000 requests in time window
            log.warn("Potential scraping detected from IP: {}", ipAddress);
            return true;
        }
        return false;
    }

    /**
     * Cleanup old entries (runs every 10 minutes)
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void cleanup() {
        Instant cutoff = Instant.now().minus(10, ChronoUnit.MINUTES);

        requestPatterns.entrySet().removeIf(entry ->
                entry.getValue().getLastRequestTime().isBefore(cutoff)
        );

        failureTrackers.entrySet().removeIf(entry ->
                entry.getValue().getLastFailureTime().isBefore(cutoff)
        );

        log.debug("API abuse detector cleanup completed. Patterns: {}, Failures: {}",
                requestPatterns.size(), failureTrackers.size());
    }

    /**
     * Request pattern tracker
     */
    private static class RequestPattern {
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private final Map<String, AtomicInteger> endpointCounts = new ConcurrentHashMap<>();
        private Instant lastRequestTime = Instant.now();
        private Instant firstRequestTime = Instant.now();

        public void recordRequest(String endpoint) {
            requestCount.incrementAndGet();
            endpointCounts.computeIfAbsent(endpoint, k -> new AtomicInteger(0)).incrementAndGet();
            lastRequestTime = Instant.now();
        }

        public boolean isSuspicious() {
            // More than 500 requests in 1 minute
            long duration = ChronoUnit.SECONDS.between(firstRequestTime, lastRequestTime);
            if (duration < 60 && requestCount.get() > 500) {
                return true;
            }

            // Same endpoint called >100 times rapidly
            for (AtomicInteger count : endpointCounts.values()) {
                if (count.get() > 100 && duration < 60) {
                    return true;
                }
            }

            return false;
        }

        public int getRequestCount() {
            return requestCount.get();
        }

        public Instant getLastRequestTime() {
            return lastRequestTime;
        }

        @Override
        public String toString() {
            return "RequestPattern{" +
                    "count=" + requestCount.get() +
                    ", endpoints=" + endpointCounts.size() +
                    ", duration=" + ChronoUnit.SECONDS.between(firstRequestTime, lastRequestTime) + "s" +
                    '}';
        }
    }

    /**
     * Failure tracker
     */
    private static class FailureTracker {
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private Instant lastFailureTime = Instant.now();
        private static final int THRESHOLD = 20; // 20 failures = suspicious

        public void recordFailure(String operationType) {
            failureCount.incrementAndGet();
            lastFailureTime = Instant.now();
        }

        public boolean isThresholdExceeded() {
            return failureCount.get() >= THRESHOLD;
        }

        public Instant getLastFailureTime() {
            return lastFailureTime;
        }
    }
}
