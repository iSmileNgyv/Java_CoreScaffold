package com.ismile.argusomnicli.model;

import lombok.Data;

/**
 * Configuration for test execution behavior.
 * Controls parallel execution, threading, and dependency resolution.
 */
@Data
public class ExecutionConfig {
    /**
     * Parallel execution configuration.
     */
    private ParallelConfig parallel;

    /**
     * Parallel execution settings.
     */
    @Data
    public static class ParallelConfig {
        /**
         * Enable/disable parallel execution.
         * Default: false (sequential execution)
         */
        private Boolean enabled = false;

        /**
         * Number of concurrent threads for parallel execution.
         * Default: Number of available processors
         */
        private Integer threads;

        /**
         * Maximum time to wait for all tests to complete (milliseconds).
         * Default: No timeout
         */
        private Long timeout;

        /**
         * Stop all tests if one fails.
         * Default: false (continue executing other tests)
         */
        private Boolean failFast = false;
    }
}
