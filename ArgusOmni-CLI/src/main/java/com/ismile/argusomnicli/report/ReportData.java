package com.ismile.argusomnicli.report;

import com.ismile.argusomnicli.executor.ExecutionResult;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for test report.
 * Follows Single Responsibility Principle - only holds report data.
 */
@Data
@Builder
public class ReportData {
    private String testSuiteName;
    private LocalDateTime executionTime;
    private int totalTests;
    private int passedTests;
    private int failedTests;
    private long totalDurationMs;
    private long minDurationMs;
    private long maxDurationMs;
    private double avgDurationMs;
    private List<ExecutionResult> results;

    /**
     * Get pass rate as percentage.
     */
    public double getPassRate() {
        if (totalTests == 0) return 0.0;
        return (passedTests * 100.0) / totalTests;
    }

    /**
     * Get fail rate as percentage.
     */
    public double getFailRate() {
        if (totalTests == 0) return 0.0;
        return (failedTests * 100.0) / totalTests;
    }
}
