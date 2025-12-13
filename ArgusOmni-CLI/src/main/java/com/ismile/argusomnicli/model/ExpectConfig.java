package com.ismile.argusomnicli.model;

import lombok.Data;

import java.util.Map;

/**
 * Expectation/Assertion configuration.
 * Defines what outcomes to validate.
 */
@Data
public class ExpectConfig {
    private Integer status;
    private Map<String, Object> json;
    private Map<String, Object> jsonContains;
    private Map<String, Object> jsonNotContains;
    private Map<String, JsonPathAssertion> jsonPath;
    private String fsExists;
    private String fsSize;
    private Map<String, Object> equals;
    private PerformanceExpectation performance;
    private String jsonSchema;                          // Path to JSON Schema file
    private Map<String, Object> dateFormats;            // Custom date format validations

    /**
     * JSONPath assertion configuration.
     * Supports complex queries with filters, conditions, etc.
     */
    @Data
    public static class JsonPathAssertion {
        private Boolean exists;        // Path should return results
        private Boolean isEmpty;       // Path should return empty results
        private Integer count;         // Exact count of results
        private Integer minCount;      // Minimum count of results
        private Integer maxCount;      // Maximum count of results
        private Object equals;         // Result should equal this value
        private Object contains;       // Result array should contain this value
    }

    /**
     * Performance assertion configuration.
     * Validates response time against SLA requirements.
     */
    @Data
    public static class PerformanceExpectation {
        private Long maxDuration;      // Maximum response time in milliseconds
        private Long minDuration;      // Minimum response time in milliseconds (detect suspiciously fast responses)
    }

    /**
     * Date format configuration for custom date validation.
     * Supports both simple pattern strings and complex locale-based formats.
     */
    @Data
    public static class DateFormatConfig {
        private String pattern;        // Date pattern (e.g., "dd-MM-yyyy", "dd MMMM yyyy")
        private String locale;         // Locale for month/day names (e.g., "az-AZ", "en-US")
        private String min;            // Minimum date value
        private String max;            // Maximum date value
    }
}
