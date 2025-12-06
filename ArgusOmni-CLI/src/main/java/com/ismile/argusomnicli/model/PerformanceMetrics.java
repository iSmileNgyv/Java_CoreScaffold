package com.ismile.argusomnicli.model;

import lombok.Builder;
import lombok.Data;

/**
 * Performance metrics for test execution.
 * Follows Single Responsibility Principle - only tracks performance data.
 */
@Data
@Builder
public class PerformanceMetrics {
    private long durationMs;
    private Long connectTimeMs;
    private Long firstByteTimeMs;
    private Long downloadTimeMs;
    private Integer requestSize;
    private Integer responseSize;

    /**
     * Check if response time is within acceptable limit.
     */
    public boolean isWithinTimeLimit(long maxDurationMs) {
        return durationMs <= maxDurationMs;
    }

    /**
     * Get performance status indicator.
     */
    public String getPerformanceStatus(long maxDurationMs) {
        if (durationMs <= maxDurationMs * 0.5) {
            return "EXCELLENT";
        } else if (durationMs <= maxDurationMs * 0.8) {
            return "GOOD";
        } else if (durationMs <= maxDurationMs) {
            return "ACCEPTABLE";
        } else {
            return "SLOW";
        }
    }
}
