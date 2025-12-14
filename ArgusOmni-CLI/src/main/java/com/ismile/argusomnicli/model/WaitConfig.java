package com.ismile.argusomnicli.model;

import lombok.Data;

import java.util.Map;

/**
 * Wait/Polling configuration.
 * Supports simple delays and conditional polling.
 */
@Data
public class WaitConfig {
    /**
     * Simple duration to wait (in milliseconds).
     * Example: duration: 5000  # Wait 5 seconds
     */
    private Integer duration;

    /**
     * Condition to wait for.
     * Polls until condition is true or timeout occurs.
     *
     * Example:
     *   condition:
     *     variable: "jobStatus"
     *     equals: "COMPLETED"
     */
    private WaitCondition condition;

    /**
     * Maximum number of retry attempts.
     * Default: 10
     */
    private Integer maxRetries;

    /**
     * Interval between retries in milliseconds.
     * Default: 1000 (1 second)
     */
    private Integer retryInterval;

    /**
     * Total timeout in milliseconds.
     * If specified, overrides maxRetries.
     * Default: 30000 (30 seconds)
     */
    private Integer timeout;

    /**
     * Message to log while waiting.
     * Example: message: "Waiting for job to complete..."
     */
    private String message;

    /**
     * Wait condition definition.
     */
    @Data
    public static class WaitCondition {
        /**
         * Variable name to check.
         * Example: variable: "status"
         */
        private String variable;

        /**
         * Expected value.
         * Example: equals: "SUCCESS"
         */
        private Object equals;

        /**
         * Check if variable exists (not null).
         * Example: exists: true
         */
        private Boolean exists;

        /**
         * Check if variable is null.
         * Example: isNull: true
         */
        private Boolean isNull;

        /**
         * JSONPath expression to evaluate.
         * Example: jsonPath: "$.status"
         */
        private String jsonPath;

        /**
         * Expected value for JSONPath.
         * Example: jsonPathEquals: "COMPLETED"
         */
        private Object jsonPathEquals;

        /**
         * Check if response contains text.
         * Example: contains: "success"
         */
        private String contains;

        /**
         * Custom condition expression.
         * Example: expression: "{{count}} > 10"
         */
        private String expression;
    }
}
