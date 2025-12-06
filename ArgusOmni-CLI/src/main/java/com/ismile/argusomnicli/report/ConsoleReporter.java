package com.ismile.argusomnicli.report;

import com.ismile.argusomnicli.executor.ExecutionResult;
import com.ismile.argusomnicli.logger.TestExecutionLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Console reporter implementation with colored output.
 * Follows Single Responsibility - only reports to console.
 * Follows Dependency Inversion - depends on abstractions (ReportGenerator).
 */
@Component
@RequiredArgsConstructor
public class ConsoleReporter implements Reporter {

    private final TestExecutionLogger executionLogger;
    private final HtmlReportGenerator htmlReportGenerator;

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CYAN = "\u001B[36m";

    private int totalTests = 0;
    private int passedTests = 0;
    private int failedTests = 0;
    private int skippedTests = 0; // Tests with continueOnError that failed

    @Override
    public void reportStart(String suiteName) {
        System.out.println(ANSI_CYAN + "╔═══════════════════════════════════════════════════════════╗" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "║  ArgusOmni Test Orchestrator - Universal Test Runner    ║" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "╚═══════════════════════════════════════════════════════════╝" + ANSI_RESET);
        System.out.println();
        System.out.println(ANSI_BLUE + "Running test suite: " + suiteName + ANSI_RESET);
        System.out.println();
    }

    @Override
    public void reportStep(ExecutionResult result) {
        totalTests++;

        if (result.isSuccess()) {
            passedTests++;
            System.out.println(ANSI_GREEN + "✓ " + result.getStepName() +
                    ANSI_RESET + ANSI_YELLOW + " (" + result.getDurationMs() + "ms)" + ANSI_RESET);
        } else if (result.isContinueOnError()) {
            // Failed but expected to continue - don't count as failure
            skippedTests++;
            System.out.println(ANSI_YELLOW + "⚠ " + result.getStepName() + " (expected failure)" +
                    ANSI_RESET + ANSI_YELLOW + " (" + result.getDurationMs() + "ms)" + ANSI_RESET);
            if (result.getErrorMessage() != null) {
                System.out.println(ANSI_YELLOW + "  Warning: " + result.getErrorMessage() + ANSI_RESET);
            }
        } else {
            failedTests++;
            System.out.println(ANSI_RED + "✗ " + result.getStepName() +
                    ANSI_RESET + ANSI_YELLOW + " (" + result.getDurationMs() + "ms)" + ANSI_RESET);
            if (result.getErrorMessage() != null) {
                System.out.println(ANSI_RED + "  Error: " + result.getErrorMessage() + ANSI_RESET);
            }
        }

        // Show extracted variables
        if (result.getExtractedVariables() != null && !result.getExtractedVariables().isEmpty()) {
            System.out.println(ANSI_CYAN + "  Extracted: " + result.getExtractedVariables() + ANSI_RESET);
        }
    }

    @Override
    public void reportComplete(List<ExecutionResult> results) {
        System.out.println();
        System.out.println(ANSI_CYAN + "═══════════════════════════════════════════════════════════" + ANSI_RESET);

        long totalDuration = results.stream()
                .mapToLong(ExecutionResult::getDurationMs)
                .sum();

        System.out.println(ANSI_BLUE + "Total Tests: " + totalTests + ANSI_RESET);
        System.out.println(ANSI_GREEN + "Passed: " + passedTests + ANSI_RESET);
        System.out.println(ANSI_RED + "Failed: " + failedTests + ANSI_RESET);
        if (skippedTests > 0) {
            System.out.println(ANSI_YELLOW + "Skipped (Expected Failures): " + skippedTests + ANSI_RESET);
        }
        System.out.println(ANSI_YELLOW + "Total Duration: " + totalDuration + "ms" + ANSI_RESET);

        System.out.println();
        if (failedTests == 0) {
            System.out.println(ANSI_GREEN + "✓ All tests passed!" + ANSI_RESET);
        } else {
            System.out.println(ANSI_RED + "✗ Some tests failed!" + ANSI_RESET);
        }

        // Write execution log
        System.out.println();
        String logPath = executionLogger.writeLog("Test Suite", results, "./test-logs");
        if (logPath != null) {
            System.out.println(ANSI_CYAN + "📄 Execution log: " + logPath + ANSI_RESET);
        }

        // Generate HTML report
        String htmlReportPath = htmlReportGenerator.generateReport("Test Suite", results, "./test-reports");
        if (htmlReportPath != null) {
            System.out.println(ANSI_CYAN + "📊 HTML Report: " + htmlReportPath + ANSI_RESET);
        }
    }

    @Override
    public int getExitCode() {
        return failedTests > 0 ? 1 : 0;
    }
}
