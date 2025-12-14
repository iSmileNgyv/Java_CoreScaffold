package com.ismile.argusomnicli.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Loop/iteration configuration for data-driven testing.
 * Supports iterating over arrays, CSV files, JSON files, and ranges.
 */
@Data
public class LoopConfig {
    /**
     * Inline items to iterate over.
     * Example:
     *   items: ["user1", "user2", "user3"]
     */
    private List<Object> items;

    /**
     * Variable name from context to iterate over.
     * Example:
     *   itemsFrom: "userList"
     */
    private String itemsFrom;

    /**
     * Data source configuration for external data (CSV, JSON, etc.)
     * Example:
     *   dataSource:
     *     type: CSV
     *     file: "testdata/users.csv"
     */
    private DataSource dataSource;

    /**
     * Range configuration for numeric iteration.
     * Example:
     *   range:
     *     start: 1
     *     end: 10
     *     step: 1
     */
    private RangeConfig range;

    /**
     * Variable name to store current item.
     * Default: "item"
     * Example: variable: "username"
     */
    private String variable;

    /**
     * Index variable name to store current index.
     * Default: "index"
     * Example: indexVariable: "i"
     */
    private String indexVariable;

    /**
     * Nested steps to execute for each item.
     * Note: This is List<TestStep> to support multiple steps per iteration.
     */
    private List<TestStep> steps;

    /**
     * Continue loop even if a step fails.
     * Default: false
     */
    private Boolean continueOnError;

    /**
     * Maximum iterations (safety limit).
     * Default: 1000
     */
    private Integer maxIterations;

    /**
     * Data source configuration.
     */
    @Data
    public static class DataSource {
        /**
         * Data source type: CSV, JSON, YAML
         */
        private String type;

        /**
         * File path for data source.
         */
        private String file;

        /**
         * For JSON: JSONPath to array
         * Example: path: "$.users"
         */
        private String path;

        /**
         * For CSV: Use first row as headers
         * Default: true
         */
        private Boolean headers;
    }

    /**
     * Range configuration for numeric iteration.
     */
    @Data
    public static class RangeConfig {
        /**
         * Start value (inclusive).
         */
        private Integer start;

        /**
         * End value (inclusive).
         */
        private Integer end;

        /**
         * Step increment.
         * Default: 1
         */
        private Integer step;
    }
}
