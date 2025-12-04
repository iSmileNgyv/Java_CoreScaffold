package com.ismile.argusomnicli.executor;

import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;

/**
 * Interface Segregation Principle (ISP):
 * Single focused interface for test step execution.
 *
 * Open/Closed Principle (OCP):
 * New executors can be added without modifying existing code.
 *
 * Dependency Inversion Principle (DIP):
 * High-level modules depend on this abstraction, not concrete implementations.
 */
public interface TestExecutor {
    /**
     * Executes a test step within given context.
     *
     * @param step Test step to execute
     * @param context Execution context containing variables and state
     * @return Execution result
     */
    ExecutionResult execute(TestStep step, ExecutionContext context) throws Exception;

    /**
     * Checks if this executor supports the given step type.
     *
     * @param step Test step to check
     * @return true if supported
     */
    boolean supports(TestStep step);
}
