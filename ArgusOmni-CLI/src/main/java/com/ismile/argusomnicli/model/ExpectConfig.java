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
    private java.util.List<HeaderAssertion> headers;    // Response header validations

    /**
     * JSONPath assertion configuration.
     * Supports complex queries with filters, conditions, etc.
     */
    @Data
    public static class JsonPathAssertion {
        // Existence checks
        private Boolean exists;        // Path should return results
        private Boolean isEmpty;       // Path should return empty results
        private Boolean isNull;        // Value should be null
        private Boolean notNull;       // Value should not be null

        // Count assertions
        private Integer count;         // Exact count of results
        private Integer minCount;      // Minimum count of results
        private Integer maxCount;      // Maximum count of results

        // Equality checks
        private Object equals;         // Result should equal this value
        private Object notEquals;      // Result should not equal this value
        private Object contains;       // Result array should contain this value
        private Object notContains;    // Result array should not contain this value

        // Numeric comparisons
        private Object greaterThan;    // Numeric: value > this
        private Object greaterThanOrEqual; // Numeric: value >= this
        private Object lessThan;       // Numeric: value < this
        private Object lessThanOrEqual;    // Numeric: value <= this
        private NumericRange between;  // Numeric: min <= value <= max

        // String operations
        private String matches;        // Regex pattern match
        private String startsWith;     // String starts with
        private String endsWith;       // String ends with
        private Integer minLength;     // String minimum length
        private Integer maxLength;     // String maximum length

        // Type validation
        private String type;           // Expected type: string, integer, number, boolean, array, object

        // Array operations
        private Boolean arrayNotEmpty; // Array should not be empty
        private Integer arraySize;     // Array exact size
        private Integer arrayMinSize;  // Array minimum size
        private Integer arrayMaxSize;  // Array maximum size
        private Object arrayContains;  // Array contains element
        private ArrayAllCondition arrayAll; // All array elements match condition

        // Multiple conditions
        private java.util.List<JsonPathAssertion> allOf;  // AND: all conditions must pass
        private java.util.List<JsonPathAssertion> anyOf;  // OR: at least one must pass
    }

    /**
     * Numeric range configuration for 'between' operator.
     */
    @Data
    public static class NumericRange {
        private Object min;
        private Object max;
    }

    /**
     * Array 'all' condition - checks all elements match criteria.
     */
    @Data
    public static class ArrayAllCondition {
        private String operator;       // greaterThan, lessThan, equals, etc.
        private Object value;
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

    /**
     * Header assertion configuration.
     * Validates response headers.
     */
    @Data
    public static class HeaderAssertion {
        private String name;           // Header name (required)
        private String equals;         // Exact value match
        private String contains;       // Contains substring
        private String notContains;    // Does not contain substring
        private String matches;        // Regex pattern match
        private String startsWith;     // Starts with prefix
        private String endsWith;       // Ends with suffix
        private Boolean exists;        // Header should exist
        private Boolean notExists;     // Header should not exist
        private Integer greaterThan;   // Numeric: value > this (e.g., rate limits)
        private Integer lessThan;      // Numeric: value < this
    }
}
