package com.ismile.argusomnicli.executor;

import com.ismile.argusomnicli.extractor.ResponseExtractor;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for executors.
 * Follows OOP Inheritance - common behavior in base class.
 * Follows Template Method pattern.
 */
@RequiredArgsConstructor
public abstract class AbstractExecutor implements TestExecutor {
    protected final VariableResolver variableResolver;
    protected final ResponseExtractor responseExtractor;

    @Override
    public ExecutionResult execute(TestStep step, ExecutionContext context) throws Exception {
        long startTime = System.currentTimeMillis();

        try {
            // Template method - delegates to subclasses
            Object response = doExecute(step, context);
            long duration = System.currentTimeMillis() - startTime;

            // Extract variables if configured
            Map<String, Object> extracted = new HashMap<>();
            if (step.getExtract() != null && !step.getExtract().isEmpty()) {
                extracted = responseExtractor.extract(response, step.getExtract());
                extracted.forEach(context::setVariable);
            }

            // Get status code from context if available (set by REST executor)
            Integer statusCode = null;
            Object statusCodeObj = context.getVariable("_last_status_code");
            if (statusCodeObj instanceof Integer) {
                statusCode = (Integer) statusCodeObj;
            }

            return ExecutionResult.builder()
                    .success(true)
                    .stepName(step.getName())
                    .response(response)
                    .extractedVariables(extracted)
                    .statusCode(statusCode)
                    .durationMs(duration)
                    .build();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // Try to get status code from context (set by executor on error)
            Integer statusCode = null;
            Object statusCodeObj = context.getVariable("_last_status_code");
            if (statusCodeObj instanceof Integer) {
                statusCode = (Integer) statusCodeObj;
            }

            return ExecutionResult.builder()
                    .success(false)
                    .stepName(step.getName())
                    .statusCode(statusCode)
                    .errorMessage(e.getMessage())
                    .durationMs(duration)
                    .build();
        }
    }

    /**
     * Template method - subclasses implement actual execution.
     * Follows Abstraction principle.
     */
    protected abstract Object doExecute(TestStep step, ExecutionContext context) throws Exception;
}
