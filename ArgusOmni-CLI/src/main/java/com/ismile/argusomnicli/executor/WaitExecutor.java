package com.ismile.argusomnicli.executor;

import com.ismile.argusomnicli.extractor.ResponseExtractor;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.model.WaitConfig;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import com.jayway.jsonpath.JsonPath;
import org.springframework.stereotype.Component;

/**
 * Wait/Polling executor implementation.
 * Supports simple delays and conditional polling.
 */
@Component
public class WaitExecutor extends AbstractExecutor {

    public WaitExecutor(VariableResolver variableResolver, ResponseExtractor responseExtractor) {
        super(variableResolver, responseExtractor);
    }

    @Override
    public boolean supports(TestStep step) {
        return step.getType() == StepType.WAIT && step.getWait() != null;
    }

    @Override
    protected Object doExecute(TestStep step, ExecutionContext context) throws Exception {
        WaitConfig config = step.getWait();

        // Simple duration wait
        if (config.getDuration() != null && config.getCondition() == null) {
            return simpleWait(config, context);
        }

        // Conditional wait with polling
        if (config.getCondition() != null) {
            return conditionalWait(config, context);
        }

        throw new Exception("Wait configuration must have either 'duration' or 'condition'");
    }

    /**
     * Simple wait for specified duration.
     */
    private Object simpleWait(WaitConfig config, ExecutionContext context) throws InterruptedException {
        int duration = config.getDuration();

        if (context.isVerbose()) {
            String message = config.getMessage() != null
                    ? config.getMessage()
                    : "Waiting for " + duration + "ms...";
            System.out.println("⏳ " + message);
        }

        Thread.sleep(duration);

        return "Waited for " + duration + "ms";
    }

    /**
     * Conditional wait with polling.
     * Polls until condition is met or timeout occurs.
     */
    private Object conditionalWait(WaitConfig config, ExecutionContext context) throws Exception {
        WaitConfig.WaitCondition condition = config.getCondition();

        // Get configuration with defaults
        int maxRetries = config.getMaxRetries() != null ? config.getMaxRetries() : 10;
        int retryInterval = config.getRetryInterval() != null ? config.getRetryInterval() : 1000;
        Integer timeout = config.getTimeout();

        long startTime = System.currentTimeMillis();
        int attempt = 0;

        while (true) {
            attempt++;

            if (context.isVerbose()) {
                String message = config.getMessage() != null
                        ? config.getMessage()
                        : "Polling condition (attempt " + attempt + "/" + maxRetries + ")...";
                System.out.println("🔄 " + message);
            }

            // Check condition
            boolean conditionMet = evaluateCondition(condition, context);

            if (conditionMet) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (context.isVerbose()) {
                    System.out.println("✓ Condition met after " + attempt + " attempts (" + elapsed + "ms)");
                }
                return "Condition met after " + attempt + " attempts (" + elapsed + "ms)";
            }

            // Check timeout
            if (timeout != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= timeout) {
                    throw new Exception("Wait timeout after " + elapsed + "ms (max: " + timeout + "ms)");
                }
            }

            // Check max retries
            if (attempt >= maxRetries) {
                long elapsed = System.currentTimeMillis() - startTime;
                throw new Exception("Wait condition not met after " + maxRetries + " attempts (" + elapsed + "ms)");
            }

            // Wait before next retry
            Thread.sleep(retryInterval);
        }
    }

    /**
     * Evaluate wait condition.
     */
    private boolean evaluateCondition(WaitConfig.WaitCondition condition, ExecutionContext context) {
        try {
            // Variable equals check
            if (condition.getVariable() != null && condition.getEquals() != null) {
                Object value = context.getVariable(condition.getVariable());
                String resolvedExpected = variableResolver.resolve(
                        String.valueOf(condition.getEquals()),
                        context.getVariableContext()
                );
                return resolvedExpected.equals(String.valueOf(value));
            }

            // Variable exists check
            if (condition.getVariable() != null && condition.getExists() != null) {
                Object value = context.getVariable(condition.getVariable());
                boolean exists = value != null;
                return condition.getExists() == exists;
            }

            // Variable isNull check
            if (condition.getVariable() != null && condition.getIsNull() != null) {
                Object value = context.getVariable(condition.getVariable());
                boolean isNull = value == null;
                return condition.getIsNull() == isNull;
            }

            // JSONPath check on last response
            if (condition.getJsonPath() != null) {
                Object lastResponse = context.getVariable("_last_response");
                if (lastResponse == null) {
                    return false;
                }

                String jsonResponse = convertToJson(lastResponse);
                Object pathValue = JsonPath.read(jsonResponse, condition.getJsonPath());

                if (condition.getJsonPathEquals() != null) {
                    return condition.getJsonPathEquals().equals(pathValue);
                } else {
                    // JSONPath exists check
                    return pathValue != null;
                }
            }

            // Contains check on last response
            if (condition.getContains() != null) {
                Object lastResponse = context.getVariable("_last_response");
                if (lastResponse == null) {
                    return false;
                }
                return String.valueOf(lastResponse).contains(condition.getContains());
            }

            // Expression check (simple variable comparison)
            if (condition.getExpression() != null) {
                String expression = variableResolver.resolve(
                        condition.getExpression(),
                        context.getVariableContext()
                );
                return evaluateSimpleExpression(expression);
            }

            return false;

        } catch (Exception e) {
            // Condition evaluation failed, return false to continue polling
            return false;
        }
    }

    /**
     * Evaluate simple expressions like "10 > 5" or "true == true"
     */
    private boolean evaluateSimpleExpression(String expression) {
        try {
            // Support simple comparisons: ==, !=, >, <, >=, <=
            if (expression.contains("==")) {
                String[] parts = expression.split("==");
                return parts[0].trim().equals(parts[1].trim());
            } else if (expression.contains("!=")) {
                String[] parts = expression.split("!=");
                return !parts[0].trim().equals(parts[1].trim());
            } else if (expression.contains(">=")) {
                String[] parts = expression.split(">=");
                double left = Double.parseDouble(parts[0].trim());
                double right = Double.parseDouble(parts[1].trim());
                return left >= right;
            } else if (expression.contains("<=")) {
                String[] parts = expression.split("<=");
                double left = Double.parseDouble(parts[0].trim());
                double right = Double.parseDouble(parts[1].trim());
                return left <= right;
            } else if (expression.contains(">")) {
                String[] parts = expression.split(">");
                double left = Double.parseDouble(parts[0].trim());
                double right = Double.parseDouble(parts[1].trim());
                return left > right;
            } else if (expression.contains("<")) {
                String[] parts = expression.split("<");
                double left = Double.parseDouble(parts[0].trim());
                double right = Double.parseDouble(parts[1].trim());
                return left < right;
            }
            // Boolean value
            return Boolean.parseBoolean(expression.trim());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Convert response object to JSON string.
     */
    private String convertToJson(Object response) throws Exception {
        if (response instanceof String) {
            return (String) response;
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.writeValueAsString(response);
    }
}
