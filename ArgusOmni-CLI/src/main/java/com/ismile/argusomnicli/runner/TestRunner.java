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
import java.util.concurrent.*;

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
    private final DependencyResolver dependencyResolver;

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
        List<ExecutionResult> results;

        // Check if parallel execution is enabled
        if (isParallelExecutionEnabled(suite)) {
            results = runParallel(suite, context);
        } else {
            results = runSequential(suite, context);
        }

        reporter.reportComplete(results);
        return reporter.getExitCode();
    }

    /**
     * Check if parallel execution is enabled.
     */
    private boolean isParallelExecutionEnabled(TestSuite suite) {
        return suite.getExecution() != null
                && suite.getExecution().getParallel() != null
                && Boolean.TRUE.equals(suite.getExecution().getParallel().getEnabled());
    }

    /**
     * Run tests sequentially (original behavior).
     */
    private List<ExecutionResult> runSequential(TestSuite suite, ExecutionContext context) {
        List<ExecutionResult> results = new ArrayList<>();

        for (TestStep step : suite.getTests()) {
            ExecutionResult result = executeStep(step, context);
            results.add(result);
            reporter.reportStep(result);

            // Stop on failure if not configured to continue
            if (!result.isSuccess() && !step.isContinueOnError()) {
                break;
            }
        }

        return results;
    }

    /**
     * Run tests in parallel with dependency resolution.
     */
    private List<ExecutionResult> runParallel(TestSuite suite, ExecutionContext context) {
        List<ExecutionResult> results = new ArrayList<>();

        try {
            // Resolve dependencies and get execution plan
            DependencyResolver.ExecutionPlan plan = dependencyResolver.resolve(suite.getTests());

            if (context.isVerbose()) {
                reporter.reportInfo("Parallel Execution Plan:");
                reporter.reportInfo(plan.getSummary());
            }

            // Get thread configuration
            int threads = getThreadCount(suite);
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            try {
                // Execute level by level
                for (int level = 0; level < plan.getTotalLevels(); level++) {
                    List<TestStep> levelTests = plan.getLevel(level);

                    if (context.isVerbose()) {
                        reporter.reportInfo(String.format("Executing Level %d (%d tests)...",
                                level, levelTests.size()));
                    }

                    // Execute tests in this level in parallel
                    List<ExecutionResult> levelResults = executeLevelParallel(
                            levelTests, context, executor, suite);

                    results.addAll(levelResults);

                    // Check for fail-fast
                    if (isFailFast(suite) && levelResults.stream().anyMatch(r -> !r.isSuccess())) {
                        reporter.reportInfo("Fail-fast enabled: stopping execution due to failures");
                        break;
                    }
                }
            } finally {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

        } catch (Exception e) {
            reporter.reportInfo("Error in parallel execution: " + e.getMessage());
            // Fallback to sequential
            return runSequential(suite, context);
        }

        return results;
    }

    /**
     * Execute all tests in a level in parallel.
     */
    private List<ExecutionResult> executeLevelParallel(List<TestStep> tests,
                                                       ExecutionContext context,
                                                       ExecutorService executor,
                                                       TestSuite suite) {
        List<Future<ExecutionResult>> futures = new ArrayList<>();

        // Submit all tests for execution
        for (TestStep test : tests) {
            Future<ExecutionResult> future = executor.submit(() -> {
                ExecutionResult result = executeStep(test, context);
                reporter.reportStep(result);
                return result;
            });
            futures.add(future);
        }

        // Collect results
        List<ExecutionResult> results = new ArrayList<>();
        Long timeout = getTimeout(suite);

        for (Future<ExecutionResult> future : futures) {
            try {
                ExecutionResult result;
                if (timeout != null) {
                    result = future.get(timeout, TimeUnit.MILLISECONDS);
                } else {
                    result = future.get();
                }
                results.add(result);
            } catch (TimeoutException e) {
                reporter.reportInfo("Test execution timeout exceeded");
                future.cancel(true);
                results.add(ExecutionResult.builder()
                        .success(false)
                        .errorMessage("Execution timeout")
                        .build());
            } catch (Exception e) {
                results.add(ExecutionResult.builder()
                        .success(false)
                        .errorMessage("Execution failed: " + e.getMessage())
                        .build());
            }
        }

        return results;
    }

    /**
     * Get thread count from configuration.
     */
    private int getThreadCount(TestSuite suite) {
        if (suite.getExecution() != null
                && suite.getExecution().getParallel() != null
                && suite.getExecution().getParallel().getThreads() != null) {
            return suite.getExecution().getParallel().getThreads();
        }
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * Get timeout from configuration.
     */
    private Long getTimeout(TestSuite suite) {
        if (suite.getExecution() != null
                && suite.getExecution().getParallel() != null) {
            return suite.getExecution().getParallel().getTimeout();
        }
        return null;
    }

    /**
     * Check if fail-fast is enabled.
     */
    private boolean isFailFast(TestSuite suite) {
        return suite.getExecution() != null
                && suite.getExecution().getParallel() != null
                && Boolean.TRUE.equals(suite.getExecution().getParallel().getFailFast());
    }

    private ExecutionResult executeStep(TestStep step, ExecutionContext context) {
        // Check if retry is configured
        if (step.getMaxRetries() != null && step.getMaxRetries() > 0) {
            return executeStepWithRetry(step, context);
        }

        return executeSingleAttempt(step, context);
    }

    /**
     * Execute step with retry logic.
     */
    private ExecutionResult executeStepWithRetry(TestStep step, ExecutionContext context) {
        int maxRetries = step.getMaxRetries();
        int retryInterval = step.getRetryInterval() != null ? step.getRetryInterval() : 1000;

        ExecutionResult lastResult = null;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            if (context.isVerbose() && attempt > 1) {
                reporter.reportInfo("🔄 Retry attempt " + (attempt - 1) + "/" + maxRetries + " for: " + step.getName());
            }

            lastResult = executeSingleAttempt(step, context);

            // If successful, return immediately
            if (lastResult.isSuccess()) {
                if (context.isVerbose() && attempt > 1) {
                    reporter.reportInfo("✓ Step succeeded on retry attempt " + (attempt - 1));
                }
                return lastResult;
            }

            // If failed but more retries available, wait and retry
            if (attempt <= maxRetries) {
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // All retries exhausted, return last result
        if (context.isVerbose()) {
            reporter.reportInfo("✗ Step failed after " + maxRetries + " retries");
        }

        return lastResult;
    }

    /**
     * Execute step single attempt (no retry).
     */
    private ExecutionResult executeSingleAttempt(TestStep step, ExecutionContext context) {
        try {
            // Find appropriate executor (Polymorphism - OCP principle)
            TestExecutor executor = findExecutor(step);
            if (executor == null) {
                return ExecutionResult.builder()
                        .success(false)
                        .stepName(step.getName())
                        .errorMessage("No executor found for step type: " + step.getType())
                        .continueOnError(step.isContinueOnError())
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
                            .continueOnError(step.isContinueOnError())
                            .build();
                }
            }

            // Set continueOnError flag on successful results too
            return result.toBuilder()
                    .continueOnError(step.isContinueOnError())
                    .build();

        } catch (Exception e) {
            return ExecutionResult.builder()
                    .success(false)
                    .stepName(step.getName())
                    .errorMessage("Execution failed: " + e.getMessage())
                    .continueOnError(step.isContinueOnError())
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
