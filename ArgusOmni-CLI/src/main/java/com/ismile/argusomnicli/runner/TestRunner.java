package com.ismile.argusomnicli.runner;

import com.ismile.argusomnicli.assertion.AssertionResult;
import com.ismile.argusomnicli.assertion.Asserter;
import com.ismile.argusomnicli.executor.ExecutionResult;
import com.ismile.argusomnicli.executor.TestExecutor;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.model.TestSuite;
import com.ismile.argusomnicli.report.Reporter;
import com.ismile.argusomnicli.variable.VariableContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Main test runner orchestrator.
 * Follows Single Responsibility - only orchestrates test execution.
 * Follows Dependency Inversion - depends on abstractions (interfaces).
 */
@Component
@RequiredArgsConstructor
public class TestRunner {

    private final List<TestExecutor> executors;
    private final Asserter asserter;
    private final Reporter reporter;

    /**
     * Run a test suite.
     * Uses polymorphism - executors selected dynamically based on step type.
     *
     * @param suite Test suite to run
     * @param verbose Verbose mode
     * @return Exit code (0 = success, 1 = failure)
     */
    public int run(TestSuite suite, boolean verbose) {
        reporter.reportStart("Test Suite");

        // Initialize context with environment variables
        VariableContext variableContext = new VariableContext();
        if (suite.getEnv() != null) {
            suite.getEnv().forEach(variableContext::set);
        }
        if (suite.getVariables() != null) {
            suite.getVariables().forEach(variableContext::set);
        }

        ExecutionContext context = new ExecutionContext(variableContext, verbose);
        List<ExecutionResult> results = new ArrayList<>();

        // Execute each test step
        for (TestStep step : suite.getTests()) {
            ExecutionResult result = executeStep(step, context);
            results.add(result);
            reporter.reportStep(result);

            // Stop on failure if not configured to continue
            if (!result.isSuccess() && !step.isContinueOnError()) {
                break;
            }
        }

        reporter.reportComplete(results);
        return reporter.getExitCode();
    }

    private ExecutionResult executeStep(TestStep step, ExecutionContext context) {
        try {
            // Find appropriate executor (Polymorphism - OCP principle)
            TestExecutor executor = findExecutor(step);
            if (executor == null) {
                return ExecutionResult.builder()
                        .success(false)
                        .stepName(step.getName())
                        .errorMessage("No executor found for step type: " + step.getType())
                        .build();
            }

            // Execute step
            ExecutionResult result = executor.execute(step, context);

            // Run assertions if expectations defined
            if (step.getExpect() != null) {
                AssertionResult assertionResult = asserter.assertExpectations(result, step.getExpect(), context);
                if (!assertionResult.isPassed()) {
                    return ExecutionResult.builder()
                            .success(false)
                            .stepName(step.getName())
                            .statusCode(result.getStatusCode())
                            .requestDetails(result.getRequestDetails())
                            .response(result.getResponse())
                            .extractedVariables(result.getExtractedVariables())
                            .errorMessage("Assertions failed: " + assertionResult.getFailures())
                            .durationMs(result.getDurationMs())
                            .performanceMetrics(result.getPerformanceMetrics())
                            .build();
                }
            }

            return result;

        } catch (Exception e) {
            return ExecutionResult.builder()
                    .success(false)
                    .stepName(step.getName())
                    .errorMessage("Execution failed: " + e.getMessage())
                    .build();
        }
    }

    private TestExecutor findExecutor(TestStep step) {
        // Strategy pattern - select executor based on step support
        return executors.stream()
                .filter(executor -> executor.supports(step))
                .findFirst()
                .orElse(null);
    }
}
