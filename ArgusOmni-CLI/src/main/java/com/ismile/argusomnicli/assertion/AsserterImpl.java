package com.ismile.argusomnicli.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.argusomnicli.executor.ExecutionResult;
import com.ismile.argusomnicli.model.ExpectConfig;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.jayway.jsonpath.JsonPath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Assertion engine implementation.
 * Follows Single Responsibility - only validates expectations.
 */
@Component
public class AsserterImpl implements Asserter {

    private final ObjectMapper objectMapper;

    public AsserterImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AssertionResult assertExpectations(ExecutionResult result, ExpectConfig expect, ExecutionContext context) {
        if (expect == null) {
            return AssertionResult.success();
        }

        AssertionResult assertionResult = AssertionResult.success();

        // Assert status code
        if (expect.getStatus() != null) {
            assertStatus(result, expect.getStatus(), assertionResult);
        }

        // Skip JSON assertions if response is null and status code is error (4xx, 5xx)
        boolean skipJsonAssertions = shouldSkipJsonAssertions(result);

        // Assert JSON equality
        if (expect.getJson() != null) {
            if (skipJsonAssertions) {
                assertionResult.addFailure(
                    String.format("Cannot assert JSON: No response body%s",
                        result.getStatusCode() != null ? " (status: " + result.getStatusCode() + ")" : "")
                );
            } else {
                assertJsonEquals(result, expect.getJson(), assertionResult);
            }
        }

        // Assert JSON contains
        if (expect.getJsonContains() != null) {
            if (skipJsonAssertions) {
                assertionResult.addFailure(
                    String.format("Cannot assert JSON contains: No response body%s",
                        result.getStatusCode() != null ? " (status: " + result.getStatusCode() + ")" : "")
                );
            } else {
                assertJsonContains(result, expect.getJsonContains(), assertionResult);
            }
        }

        // Assert JSON not contains
        if (expect.getJsonNotContains() != null) {
            if (!skipJsonAssertions) {
                assertJsonNotContains(result, expect.getJsonNotContains(), assertionResult);
            }
            // Skip silently for not-contains when no response
        }

        // Assert file system exists
        if (expect.getFsExists() != null) {
            assertFileExists(expect.getFsExists(), assertionResult, context);
        }

        // Assert file size
        if (expect.getFsSize() != null) {
            assertFileSize(expect.getFsSize(), assertionResult, context);
        }

        // Assert variable equality
        if (expect.getEquals() != null) {
            assertVariableEquals(expect.getEquals(), assertionResult, context);
        }

        // Assert JSONPath expressions
        if (expect.getJsonPath() != null) {
            if (skipJsonAssertions) {
                assertionResult.addFailure(
                    String.format("Cannot assert JSONPath: No response body%s",
                        result.getStatusCode() != null ? " (status: " + result.getStatusCode() + ")" : "")
                );
            } else {
                assertJsonPath(result, expect.getJsonPath(), assertionResult);
            }
        }

        // Assert performance (response time)
        if (expect.getPerformance() != null) {
            assertPerformance(result, expect.getPerformance(), assertionResult, context);
        }

        // Assert JSON Schema
        if (expect.getJsonSchema() != null) {
            if (skipJsonAssertions) {
                assertionResult.addFailure(
                    String.format("Cannot assert JSON Schema: No response body%s",
                        result.getStatusCode() != null ? " (status: " + result.getStatusCode() + ")" : "")
                );
            } else {
                assertJsonSchema(result, expect.getJsonSchema(), assertionResult, context);
            }
        }

        // Assert custom date formats
        if (expect.getDateFormats() != null && !expect.getDateFormats().isEmpty()) {
            if (skipJsonAssertions) {
                assertionResult.addFailure(
                    String.format("Cannot assert date formats: No response body%s",
                        result.getStatusCode() != null ? " (status: " + result.getStatusCode() + ")" : "")
                );
            } else {
                assertDateFormats(result, expect.getDateFormats(), assertionResult, context);
            }
        }

        return assertionResult;
    }

    private void assertStatus(ExecutionResult result, Integer expectedStatus, AssertionResult assertionResult) {
        if (result.getStatusCode() == null) {
            assertionResult.addFailure("No status code in response");
            return;
        }

        if (!result.getStatusCode().equals(expectedStatus)) {
            assertionResult.addFailure(
                    String.format("Status mismatch: expected %d, got %d",
                            expectedStatus, result.getStatusCode())
            );
        }
    }

    private void assertJsonEquals(ExecutionResult result, Map<String, Object> expected, AssertionResult assertionResult) {
        try {
            String jsonResponse = convertToJson(result.getResponse());

            for (Map.Entry<String, Object> entry : expected.entrySet()) {
                String path = "$." + entry.getKey();
                Object actualValue = JsonPath.read(jsonResponse, path);

                if (!entry.getValue().equals(actualValue)) {
                    assertionResult.addFailure(
                            String.format("JSON field '%s': expected '%s', got '%s'",
                                    entry.getKey(), entry.getValue(), actualValue)
                    );
                }
            }
        } catch (Exception e) {
            assertionResult.addFailure("JSON assertion failed: " + e.getMessage());
        }
    }

    private void assertJsonContains(ExecutionResult result, Map<String, Object> expected, AssertionResult assertionResult) {
        try {
            String jsonResponse = convertToJson(result.getResponse());

            for (Map.Entry<String, Object> entry : expected.entrySet()) {
                String path = "$." + entry.getKey();
                try {
                    JsonPath.read(jsonResponse, path);
                } catch (Exception e) {
                    assertionResult.addFailure(
                            String.format("JSON does not contain field: %s", entry.getKey())
                    );
                }
            }
        } catch (Exception e) {
            assertionResult.addFailure("JSON contains assertion failed: " + e.getMessage());
        }
    }

    private void assertJsonNotContains(ExecutionResult result, Map<String, Object> expected, AssertionResult assertionResult) {
        try {
            String jsonResponse = convertToJson(result.getResponse());

            for (Map.Entry<String, Object> entry : expected.entrySet()) {
                String path = "$." + entry.getKey();
                try {
                    JsonPath.read(jsonResponse, path);
                    assertionResult.addFailure(
                            String.format("JSON should not contain field: %s", entry.getKey())
                    );
                } catch (Exception e) {
                    // Field not found - this is expected
                }
            }
        } catch (Exception e) {
            assertionResult.addFailure("JSON not-contains assertion failed: " + e.getMessage());
        }
    }

    private void assertFileExists(String path, AssertionResult assertionResult, ExecutionContext context) {
        File file = new File(path);
        if (!file.exists()) {
            assertionResult.addFailure("File does not exist: " + path);
        }
    }

    private void assertFileSize(String sizeExpression, AssertionResult assertionResult, ExecutionContext context) {
        // Parse size expression: "path:size"
        String[] parts = sizeExpression.split(":");
        if (parts.length != 2) {
            assertionResult.addFailure("Invalid size expression: " + sizeExpression);
            return;
        }

        String path = parts[0];
        long expectedSize = Long.parseLong(parts[1]);

        File file = new File(path);
        if (!file.exists()) {
            assertionResult.addFailure("File does not exist: " + path);
            return;
        }

        long actualSize = file.length();
        if (actualSize != expectedSize) {
            assertionResult.addFailure(
                    String.format("File size mismatch for %s: expected %d, got %d",
                            path, expectedSize, actualSize)
            );
        }
    }

    private void assertVariableEquals(Map<String, Object> expected, AssertionResult assertionResult, ExecutionContext context) {
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            String varName = entry.getKey();
            Object expectedValue = entry.getValue();
            Object actualValue = context.getVariable(varName);

            if (!expectedValue.equals(actualValue)) {
                assertionResult.addFailure(
                        String.format("Variable '%s': expected '%s', got '%s'",
                                varName, expectedValue, actualValue)
                );
            }
        }
    }

    private String convertToJson(Object response) throws Exception {
        if (response instanceof String) {
            return (String) response;
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.writeValueAsString(response);
    }

    private boolean shouldSkipJsonAssertions(ExecutionResult result) {
        // Skip JSON assertions if:
        // 1. Response is null
        // 2. AND status code indicates error (4xx, 5xx for REST) or no status code (for gRPC/other)
        if (result.getResponse() == null) {
            Integer statusCode = result.getStatusCode();
            if (statusCode == null || statusCode == 0) {
                return true; // Connection error or gRPC error (no HTTP status code)
            }
            if (statusCode >= 400) {
                return true; // REST API error (4xx, 5xx)
            }
        }
        return false;
    }

    /**
     * Assert JSONPath expressions.
     * Supports complex queries with filters, conditions, etc.
     */
    private void assertJsonPath(ExecutionResult result, Map<String, ExpectConfig.JsonPathAssertion> jsonPathAssertions, AssertionResult assertionResult) {
        try {
            String jsonResponse = convertToJson(result.getResponse());

            for (Map.Entry<String, ExpectConfig.JsonPathAssertion> entry : jsonPathAssertions.entrySet()) {
                String path = entry.getKey();
                ExpectConfig.JsonPathAssertion assertion = entry.getValue();

                try {
                    Object value = JsonPath.read(jsonResponse, path);
                    validateJsonPathAssertion(path, value, assertion, assertionResult);
                } catch (Exception e) {
                    // Path evaluation failed
                    if (assertion.getExists() != null && assertion.getExists()) {
                        assertionResult.addFailure(
                            String.format("JSONPath '%s' should exist but failed: %s", path, e.getMessage())
                        );
                    } else if (assertion.getIsEmpty() != null && !assertion.getIsEmpty()) {
                        assertionResult.addFailure(
                            String.format("JSONPath '%s' should not be empty but failed: %s", path, e.getMessage())
                        );
                    }
                    // For other assertions, path not found might be acceptable
                }
            }
        } catch (Exception e) {
            assertionResult.addFailure("JSONPath assertion failed: " + e.getMessage());
        }
    }

    /**
     * Validate a single JSONPath assertion result.
     */
    private void validateJsonPathAssertion(String path, Object value, ExpectConfig.JsonPathAssertion assertion, AssertionResult assertionResult) {
        // Check exists
        if (assertion.getExists() != null) {
            boolean exists = value != null && (!isEmptyCollection(value));
            if (assertion.getExists() && !exists) {
                assertionResult.addFailure(
                    String.format("JSONPath '%s' should return results but got: %s", path, value)
                );
            } else if (!assertion.getExists() && exists) {
                assertionResult.addFailure(
                    String.format("JSONPath '%s' should not return results but got: %s", path, value)
                );
            }
        }

        // Check isEmpty
        if (assertion.getIsEmpty() != null) {
            boolean isEmpty = value == null || isEmptyCollection(value);
            if (assertion.getIsEmpty() && !isEmpty) {
                assertionResult.addFailure(
                    String.format("JSONPath '%s' should be empty but got: %s", path, value)
                );
            } else if (!assertion.getIsEmpty() && isEmpty) {
                assertionResult.addFailure(
                    String.format("JSONPath '%s' should not be empty but is empty", path)
                );
            }
        }

        // Check count (exact)
        if (assertion.getCount() != null) {
            int actualCount = getCollectionSize(value);
            if (actualCount != assertion.getCount()) {
                assertionResult.addFailure(
                    String.format("JSONPath '%s' count mismatch: expected %d, got %d",
                        path, assertion.getCount(), actualCount)
                );
            }
        }

        // Check minCount
        if (assertion.getMinCount() != null) {
            int actualCount = getCollectionSize(value);
            if (actualCount < assertion.getMinCount()) {
                assertionResult.addFailure(
                    String.format("JSONPath '%s' count %d is less than minimum %d",
                        path, actualCount, assertion.getMinCount())
                );
            }
        }

        // Check maxCount
        if (assertion.getMaxCount() != null) {
            int actualCount = getCollectionSize(value);
            if (actualCount > assertion.getMaxCount()) {
                assertionResult.addFailure(
                    String.format("JSONPath '%s' count %d is greater than maximum %d",
                        path, actualCount, assertion.getMaxCount())
                );
            }
        }

        // Check equals
        if (assertion.getEquals() != null) {
            if (!assertion.getEquals().equals(value)) {
                assertionResult.addFailure(
                    String.format("JSONPath '%s' value mismatch: expected '%s', got '%s'",
                        path, assertion.getEquals(), value)
                );
            }
        }

        // Check contains (for array results)
        if (assertion.getContains() != null) {
            if (value instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) value;
                if (!list.contains(assertion.getContains())) {
                    assertionResult.addFailure(
                        String.format("JSONPath '%s' does not contain value: %s",
                            path, assertion.getContains())
                    );
                }
            } else {
                assertionResult.addFailure(
                    String.format("JSONPath '%s' is not a list, cannot check contains", path)
                );
            }
        }
    }

    /**
     * Check if value is an empty collection.
     */
    private boolean isEmptyCollection(Object value) {
        if (value instanceof java.util.Collection) {
            return ((java.util.Collection<?>) value).isEmpty();
        }
        if (value instanceof java.util.Map) {
            return ((java.util.Map<?, ?>) value).isEmpty();
        }
        if (value instanceof Object[]) {
            return ((Object[]) value).length == 0;
        }
        return false;
    }

    /**
     * Get size of collection or 1 for non-collection values.
     */
    private int getCollectionSize(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof java.util.Collection) {
            return ((java.util.Collection<?>) value).size();
        }
        if (value instanceof java.util.Map) {
            return ((java.util.Map<?, ?>) value).size();
        }
        if (value instanceof Object[]) {
            return ((Object[]) value).length;
        }
        return 1; // Single value
    }

    /**
     * Assert performance expectations (response time).
     * Validates that response time meets SLA requirements.
     */
    private void assertPerformance(ExecutionResult result, ExpectConfig.PerformanceExpectation performance, AssertionResult assertionResult, ExecutionContext context) {
        // Get response time from context
        Long responseTime = (Long) context.getVariable("_last_response_time");

        if (responseTime == null) {
            assertionResult.addFailure("Response time not available");
            return;
        }

        // Check maximum duration
        if (performance.getMaxDuration() != null) {
            if (responseTime > performance.getMaxDuration()) {
                assertionResult.addFailure(String.format(
                    "Response time %dms exceeded maximum %dms",
                    responseTime, performance.getMaxDuration()
                ));
            }
        }

        // Check minimum duration (detect suspiciously fast responses)
        if (performance.getMinDuration() != null) {
            if (responseTime < performance.getMinDuration()) {
                assertionResult.addFailure(String.format(
                    "Response time %dms is below minimum %dms (suspiciously fast)",
                    responseTime, performance.getMinDuration()
                ));
            }
        }
    }

    /**
     * Assert JSON Schema validation.
     * Validates that response matches the JSON Schema specification.
     */
    private void assertJsonSchema(ExecutionResult result, String schemaPath, AssertionResult assertionResult, ExecutionContext context) {
        try {
            // Load JSON Schema from file
            File schemaFile = new File(schemaPath);
            if (!schemaFile.exists()) {
                assertionResult.addFailure("JSON Schema file not found: " + schemaPath);
                return;
            }

            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            JsonSchema schema = factory.getSchema(new FileInputStream(schemaFile));

            // Convert response to JsonNode
            String responseJson = convertToJson(result.getResponse());
            JsonNode jsonNode = objectMapper.readTree(responseJson);

            // Validate
            Set<ValidationMessage> errors = schema.validate(jsonNode);

            if (!errors.isEmpty()) {
                StringBuilder errorMsg = new StringBuilder("JSON Schema validation failed:\n");
                for (ValidationMessage error : errors) {
                    errorMsg.append("  - ").append(error.getMessage()).append("\n");
                }
                assertionResult.addFailure(errorMsg.toString().trim());
            }

        } catch (Exception e) {
            assertionResult.addFailure("JSON Schema validation error: " + e.getMessage());
        }
    }

    /**
     * Assert custom date formats.
     * Validates date fields match specified patterns and locales.
     */
    private void assertDateFormats(ExecutionResult result, Map<String, Object> dateFormats, AssertionResult assertionResult, ExecutionContext context) {
        try {
            // Convert response to JSON for field extraction
            String responseJson = convertToJson(result.getResponse());
            JsonNode jsonNode = objectMapper.readTree(responseJson);

            for (Map.Entry<String, Object> entry : dateFormats.entrySet()) {
                String fieldPath = entry.getKey();
                Object formatConfig = entry.getValue();

                // Extract field value using JSONPath
                Object fieldValue;
                try {
                    fieldValue = JsonPath.read(responseJson, "$." + fieldPath);
                } catch (Exception e) {
                    assertionResult.addFailure("Date field not found: " + fieldPath);
                    continue;
                }

                if (fieldValue == null) {
                    assertionResult.addFailure("Date field is null: " + fieldPath);
                    continue;
                }

                String dateValue = fieldValue.toString();

                // Parse format configuration
                if (formatConfig instanceof String) {
                    // Simple pattern string
                    validateDateFormat(dateValue, (String) formatConfig, null, fieldPath, assertionResult);
                } else if (formatConfig instanceof Map) {
                    // Complex configuration with pattern, locale, min, max
                    @SuppressWarnings("unchecked")
                    Map<String, Object> config = (Map<String, Object>) formatConfig;

                    String pattern = (String) config.get("pattern");
                    String locale = (String) config.get("locale");
                    String min = (String) config.get("min");
                    String max = (String) config.get("max");

                    if (pattern == null) {
                        assertionResult.addFailure("Date format pattern not specified for field: " + fieldPath);
                        continue;
                    }

                    validateDateFormat(dateValue, pattern, locale, fieldPath, assertionResult);

                    // Validate min/max if specified
                    if (min != null || max != null) {
                        validateDateRange(dateValue, pattern, locale, min, max, fieldPath, assertionResult);
                    }
                }
            }

        } catch (Exception e) {
            assertionResult.addFailure("Date format validation error: " + e.getMessage());
        }
    }

    /**
     * Validate a date string against a pattern and locale.
     */
    private void validateDateFormat(String dateValue, String pattern, String locale, String fieldPath, AssertionResult assertionResult) {
        try {
            Locale loc = locale != null ? Locale.forLanguageTag(locale) : Locale.getDefault();
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, loc);
            sdf.setLenient(false);  // Strict parsing

            Date parsedDate = sdf.parse(dateValue);

            // Reverse check - format back and compare
            String formatted = sdf.format(parsedDate);
            if (!formatted.equals(dateValue)) {
                assertionResult.addFailure(String.format(
                    "Date format mismatch for field '%s': '%s' does not match pattern '%s'%s",
                    fieldPath, dateValue, pattern,
                    locale != null ? " (locale: " + locale + ")" : ""
                ));
            }

        } catch (ParseException e) {
            assertionResult.addFailure(String.format(
                "Invalid date format for field '%s': '%s' does not match pattern '%s'%s",
                fieldPath, dateValue, pattern,
                locale != null ? " (locale: " + locale + ")" : ""
            ));
        }
    }

    /**
     * Validate date is within min/max range.
     */
    private void validateDateRange(String dateValue, String pattern, String locale, String min, String max, String fieldPath, AssertionResult assertionResult) {
        try {
            Locale loc = locale != null ? Locale.forLanguageTag(locale) : Locale.getDefault();
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, loc);
            sdf.setLenient(false);

            Date date = sdf.parse(dateValue);

            if (min != null) {
                Date minDate = sdf.parse(min);
                if (date.before(minDate)) {
                    assertionResult.addFailure(String.format(
                        "Date '%s' in field '%s' is before minimum allowed date '%s'",
                        dateValue, fieldPath, min
                    ));
                }
            }

            if (max != null) {
                Date maxDate = sdf.parse(max);
                if (date.after(maxDate)) {
                    assertionResult.addFailure(String.format(
                        "Date '%s' in field '%s' is after maximum allowed date '%s'",
                        dateValue, fieldPath, max
                    ));
                }
            }

        } catch (ParseException e) {
            assertionResult.addFailure("Date range validation error for field '" + fieldPath + "': " + e.getMessage());
        }
    }
}
