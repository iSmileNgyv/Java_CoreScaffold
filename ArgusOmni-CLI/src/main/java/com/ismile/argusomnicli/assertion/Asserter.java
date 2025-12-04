package com.ismile.argusomnicli.assertion;

import com.ismile.argusomnicli.executor.ExecutionResult;
import com.ismile.argusomnicli.model.ExpectConfig;
import com.ismile.argusomnicli.runner.ExecutionContext;

/**
 * Interface Segregation Principle (ISP):
 * Focused interface for assertions.
 *
 * Single Responsibility Principle (SRP):
 * Only responsible for validating expectations.
 */
public interface Asserter {
    /**
     * Validates execution result against expectations.
     *
     * @param result Execution result to validate
     * @param expect Expected outcomes
     * @param context Execution context
     * @return Assertion result
     */
    AssertionResult assertExpectations(ExecutionResult result, ExpectConfig expect, ExecutionContext context);
}
