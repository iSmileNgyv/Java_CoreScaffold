package com.ismile.argusomnicli.report;

import com.ismile.argusomnicli.executor.ExecutionResult;

import java.util.List;

/**
 * Interface Segregation Principle (ISP):
 * Focused interface for reporting test results.
 *
 * Single Responsibility Principle (SRP):
 * Only responsible for reporting.
 */
public interface Reporter {
    /**
     * Reports test execution start.
     */
    void reportStart(String suiteName);

    /**
     * Reports single step result.
     */
    void reportStep(ExecutionResult result);

    /**
     * Reports test suite completion.
     */
    void reportComplete(List<ExecutionResult> results);

    /**
     * Reports informational message (e.g., retry attempts).
     */
    void reportInfo(String message);

    /**
     * Returns final exit code.
     */
    int getExitCode();
}
