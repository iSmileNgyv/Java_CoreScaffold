package com.ismile.argusomnicli.report;

import com.ismile.argusomnicli.executor.ExecutionResult;

import java.util.List;

/**
 * Interface for generating test reports.
 * Follows Interface Segregation Principle - small, focused interface.
 * Follows Dependency Inversion Principle - depend on abstraction, not implementation.
 */
public interface ReportGenerator {

    /**
     * Generate a test report from execution results.
     *
     * @param testSuiteName Name of the test suite
     * @param results List of execution results
     * @param outputDir Directory to write the report
     * @return Path to the generated report file
     */
    String generateReport(String testSuiteName, List<ExecutionResult> results, String outputDir);

    /**
     * Get the report format (e.g., "HTML", "PDF", "JSON").
     */
    String getReportFormat();
}
