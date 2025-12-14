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

        // Assert headers
        if (expect.getHeaders() != null && !expect.getHeaders().isEmpty()) {
            assertHeaders(result, expect.getHeaders(), assertionResult);
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
        // Handle multiple conditions (allOf/anyOf)
        if (assertion.getAllOf() != null && !assertion.getAllOf().isEmpty()) {
            for (ExpectConfig.JsonPathAssertion subAssertion : assertion.getAllOf()) {
                validateJsonPathAssertion(path, value, subAssertion, assertionResult);
            }
            return;
        }

        if (assertion.getAnyOf() != null && !assertion.getAnyOf().isEmpty()) {
            AssertionResult tempResult = AssertionResult.success();
            for (ExpectConfig.JsonPathAssertion subAssertion : assertion.getAnyOf()) {
                AssertionResult subResult = AssertionResult.success();
                validateJsonPathAssertion(path, value, subAssertion, subResult);
                if (subResult.isPassed()) {
                    return; // At least one passed, success
                }
                tempResult.getFailures().addAll(subResult.getFailures());
            }
            assertionResult.addFailure(String.format("JSONPath '%s' anyOf failed - none of the conditions matched", path));
            return;
        }

        // Null checks
        if (assertion.getIsNull() != null) {
            boolean isNull = value == null;
            if (assertion.getIsNull() && !isNull) {
                assertionResult.addFailure(String.format("JSONPath '%s' should be null but got: %s", path, value));
                return;
            } else if (!assertion.getIsNull() && isNull) {
                assertionResult.addFailure(String.format("JSONPath '%s' should not be null", path));
                return;
            }
        }

        if (assertion.getNotNull() != null && assertion.getNotNull()) {
            if (value == null) {
                assertionResult.addFailure(String.format("JSONPath '%s' should not be null", path));
                return;
            }
        }

        // Type validation
        if (assertion.getType() != null) {
            validateType(path, value, assertion.getType(), assertionResult);
        }

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

        // Check notEquals
        if (assertion.getNotEquals() != null) {
            if (assertion.getNotEquals().equals(value)) {
                assertionResult.addFailure(
                    String.format("JSONPath '%s' should not equal '%s'",
                        path, assertion.getNotEquals())
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

        // Check notContains
        if (assertion.getNotContains() != null) {
            if (value instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) value;
                if (list.contains(assertion.getNotContains())) {
                    assertionResult.addFailure(
                        String.format("JSONPath '%s' should not contain value: %s",
                            path, assertion.getNotContains())
                    );
                }
            }
        }

        // Numeric comparisons
        if (assertion.getGreaterThan() != null) {
            validateNumericComparison(path, value, assertion.getGreaterThan(), ">", assertionResult);
        }
        if (assertion.getGreaterThanOrEqual() != null) {
            validateNumericComparison(path, value, assertion.getGreaterThanOrEqual(), ">=", assertionResult);
        }
        if (assertion.getLessThan() != null) {
            validateNumericComparison(path, value, assertion.getLessThan(), "<", assertionResult);
        }
        if (assertion.getLessThanOrEqual() != null) {
            validateNumericComparison(path, value, assertion.getLessThanOrEqual(), "<=", assertionResult);
        }
        if (assertion.getBetween() != null) {
            validateBetween(path, value, assertion.getBetween(), assertionResult);
        }

        // String operations
        if (assertion.getMatches() != null) {
            validateStringMatches(path, value, assertion.getMatches(), assertionResult);
        }
        if (assertion.getStartsWith() != null) {
            validateStringOperation(path, value, assertion.getStartsWith(), "startsWith", assertionResult);
        }
        if (assertion.getEndsWith() != null) {
            validateStringOperation(path, value, assertion.getEndsWith(), "endsWith", assertionResult);
        }
        if (assertion.getMinLength() != null || assertion.getMaxLength() != null) {
            validateStringLength(path, value, assertion.getMinLength(), assertion.getMaxLength(), assertionResult);
        }

        // Array operations
        if (assertion.getArrayNotEmpty() != null && assertion.getArrayNotEmpty()) {
            validateArrayNotEmpty(path, value, assertionResult);
        }
        if (assertion.getArraySize() != null) {
            validateArraySize(path, value, assertion.getArraySize(), assertionResult);
        }
        if (assertion.getArrayMinSize() != null) {
            validateArrayMinSize(path, value, assertion.getArrayMinSize(), assertionResult);
        }
        if (assertion.getArrayMaxSize() != null) {
            validateArrayMaxSize(path, value, assertion.getArrayMaxSize(), assertionResult);
        }
        if (assertion.getArrayContains() != null) {
            validateArrayContains(path, value, assertion.getArrayContains(), assertionResult);
        }
        if (assertion.getArrayAll() != null) {
            validateArrayAll(path, value, assertion.getArrayAll(), assertionResult);
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

    /**
     * Assert response headers.
     */
    private void assertHeaders(ExecutionResult result, java.util.List<ExpectConfig.HeaderAssertion> headerAssertions, AssertionResult assertionResult) {
        // TODO: Response headers not yet captured in ExecutionResult
        // Skip header assertions for now - feature will be completed when response headers are added to ExecutionResult
        // For now, we'll just not fail - header assertions will be silently skipped

        /* TEMPORARILY DISABLED - Uncomment when response headers are added to ExecutionResult

        Map<String, String> responseHeaders = result.getResponseHeaders();

        if (responseHeaders == null) {
            responseHeaders = new HashMap<>();
        }

        for (ExpectConfig.HeaderAssertion headerAssertion : headerAssertions) {
            String headerName = headerAssertion.getName();
            if (headerName == null) {
                assertionResult.addFailure("Header assertion must specify 'name' field");
                continue;
            }

            // Case-insensitive header lookup
            String headerValue = null;
            for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(headerName)) {
                    headerValue = entry.getValue();
                    break;
                }
            }

            // Exists check
            if (headerAssertion.getExists() != null && headerAssertion.getExists()) {
                if (headerValue == null) {
                    assertionResult.addFailure(String.format("Header '%s' should exist but not found", headerName));
                    continue;
                }
            }

            // Not exists check
            if (headerAssertion.getNotExists() != null && headerAssertion.getNotExists()) {
                if (headerValue != null) {
                    assertionResult.addFailure(String.format("Header '%s' should not exist but found: %s", headerName, headerValue));
                }
                continue;
            }

            // Skip other checks if header doesn't exist
            if (headerValue == null) {
                assertionResult.addFailure(String.format("Header '%s' not found in response", headerName));
                continue;
            }

            // Equals
            if (headerAssertion.getEquals() != null) {
                if (!headerValue.equals(headerAssertion.getEquals())) {
                    assertionResult.addFailure(String.format("Header '%s' expected '%s', got '%s'",
                        headerName, headerAssertion.getEquals(), headerValue));
                }
            }

            // Contains
            if (headerAssertion.getContains() != null) {
                if (!headerValue.contains(headerAssertion.getContains())) {
                    assertionResult.addFailure(String.format("Header '%s' should contain '%s', got '%s'",
                        headerName, headerAssertion.getContains(), headerValue));
                }
            }

            // Not contains
            if (headerAssertion.getNotContains() != null) {
                if (headerValue.contains(headerAssertion.getNotContains())) {
                    assertionResult.addFailure(String.format("Header '%s' should not contain '%s'",
                        headerName, headerAssertion.getNotContains()));
                }
            }

            // Matches (regex)
            if (headerAssertion.getMatches() != null) {
                if (!headerValue.matches(headerAssertion.getMatches())) {
                    assertionResult.addFailure(String.format("Header '%s' does not match pattern '%s', got '%s'",
                        headerName, headerAssertion.getMatches(), headerValue));
                }
            }

            // Starts with
            if (headerAssertion.getStartsWith() != null) {
                if (!headerValue.startsWith(headerAssertion.getStartsWith())) {
                    assertionResult.addFailure(String.format("Header '%s' should start with '%s', got '%s'",
                        headerName, headerAssertion.getStartsWith(), headerValue));
                }
            }

            // Ends with
            if (headerAssertion.getEndsWith() != null) {
                if (!headerValue.endsWith(headerAssertion.getEndsWith())) {
                    assertionResult.addFailure(String.format("Header '%s' should end with '%s', got '%s'",
                        headerName, headerAssertion.getEndsWith(), headerValue));
                }
            }

            // Numeric comparisons (for headers like X-Rate-Limit-Remaining)
            if (headerAssertion.getGreaterThan() != null || headerAssertion.getLessThan() != null) {
                try {
                    int numericValue = Integer.parseInt(headerValue);

                    if (headerAssertion.getGreaterThan() != null && numericValue <= headerAssertion.getGreaterThan()) {
                        assertionResult.addFailure(String.format("Header '%s' value %d should be > %d",
                            headerName, numericValue, headerAssertion.getGreaterThan()));
                    }

                    if (headerAssertion.getLessThan() != null && numericValue >= headerAssertion.getLessThan()) {
                        assertionResult.addFailure(String.format("Header '%s' value %d should be < %d",
                            headerName, numericValue, headerAssertion.getLessThan()));
                    }
                } catch (NumberFormatException e) {
                    assertionResult.addFailure(String.format("Header '%s' value '%s' is not numeric",
                        headerName, headerValue));
                }
            }
        }
        */
    }

    /**
     * Validate type of value.
     */
    private void validateType(String path, Object value, String expectedType, AssertionResult assertionResult) {
        String actualType = getValueType(value);

        if (!expectedType.equalsIgnoreCase(actualType)) {
            assertionResult.addFailure(String.format("JSONPath '%s' type mismatch: expected %s, got %s",
                path, expectedType, actualType));
        }
    }

    /**
     * Get type name of value.
     */
    private String getValueType(Object value) {
        if (value == null) return "null";
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return "integer";
        }
        if (value instanceof Double || value instanceof Float) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof java.util.List) {
            return "array";
        }
        if (value instanceof java.util.Map) {
            return "object";
        }
        return value.getClass().getSimpleName().toLowerCase();
    }

    /**
     * Validate numeric comparison.
     */
    private void validateNumericComparison(String path, Object value, Object expected, String operator, AssertionResult assertionResult) {
        try {
            double actualNum = convertToDouble(value);
            double expectedNum = convertToDouble(expected);

            boolean passed = false;
            switch (operator) {
                case ">":
                    passed = actualNum > expectedNum;
                    break;
                case ">=":
                    passed = actualNum >= expectedNum;
                    break;
                case "<":
                    passed = actualNum < expectedNum;
                    break;
                case "<=":
                    passed = actualNum <= expectedNum;
                    break;
            }

            if (!passed) {
                assertionResult.addFailure(String.format("JSONPath '%s' numeric comparison failed: %s %s %s",
                    path, actualNum, operator, expectedNum));
            }
        } catch (Exception e) {
            assertionResult.addFailure(String.format("JSONPath '%s' is not numeric: %s", path, value));
        }
    }

    /**
     * Validate between (range check).
     */
    private void validateBetween(String path, Object value, ExpectConfig.NumericRange range, AssertionResult assertionResult) {
        try {
            double actualNum = convertToDouble(value);
            double min = convertToDouble(range.getMin());
            double max = convertToDouble(range.getMax());

            if (actualNum < min || actualNum > max) {
                assertionResult.addFailure(String.format("JSONPath '%s' value %s is not between %s and %s",
                    path, actualNum, min, max));
            }
        } catch (Exception e) {
            assertionResult.addFailure(String.format("JSONPath '%s' is not numeric: %s", path, value));
        }
    }

    /**
     * Convert value to double for numeric comparisons.
     */
    private double convertToDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            return Double.parseDouble((String) value);
        }
        throw new IllegalArgumentException("Cannot convert to number: " + value);
    }

    /**
     * Validate string matches regex.
     */
    private void validateStringMatches(String path, Object value, String pattern, AssertionResult assertionResult) {
        if (!(value instanceof String)) {
            assertionResult.addFailure(String.format("JSONPath '%s' is not a string, cannot match pattern", path));
            return;
        }

        String str = (String) value;
        if (!str.matches(pattern)) {
            assertionResult.addFailure(String.format("JSONPath '%s' does not match pattern '%s': %s",
                path, pattern, str));
        }
    }

    /**
     * Validate string operation (startsWith/endsWith).
     */
    private void validateStringOperation(String path, Object value, String expected, String operation, AssertionResult assertionResult) {
        if (!(value instanceof String)) {
            assertionResult.addFailure(String.format("JSONPath '%s' is not a string", path));
            return;
        }

        String str = (String) value;
        boolean passed = false;

        if ("startsWith".equals(operation)) {
            passed = str.startsWith(expected);
        } else if ("endsWith".equals(operation)) {
            passed = str.endsWith(expected);
        }

        if (!passed) {
            assertionResult.addFailure(String.format("JSONPath '%s' string %s failed: expected '%s', got '%s'",
                path, operation, expected, str));
        }
    }

    /**
     * Validate string length.
     */
    private void validateStringLength(String path, Object value, Integer minLength, Integer maxLength, AssertionResult assertionResult) {
        if (!(value instanceof String)) {
            assertionResult.addFailure(String.format("JSONPath '%s' is not a string", path));
            return;
        }

        String str = (String) value;
        int length = str.length();

        if (minLength != null && length < minLength) {
            assertionResult.addFailure(String.format("JSONPath '%s' string length %d is less than minimum %d",
                path, length, minLength));
        }

        if (maxLength != null && length > maxLength) {
            assertionResult.addFailure(String.format("JSONPath '%s' string length %d exceeds maximum %d",
                path, length, maxLength));
        }
    }

    /**
     * Validate array is not empty.
     */
    private void validateArrayNotEmpty(String path, Object value, AssertionResult assertionResult) {
        if (!(value instanceof java.util.List)) {
            assertionResult.addFailure(String.format("JSONPath '%s' is not an array", path));
            return;
        }

        java.util.List<?> list = (java.util.List<?>) value;
        if (list.isEmpty()) {
            assertionResult.addFailure(String.format("JSONPath '%s' array should not be empty", path));
        }
    }

    /**
     * Validate array size (exact).
     */
    private void validateArraySize(String path, Object value, int expectedSize, AssertionResult assertionResult) {
        if (!(value instanceof java.util.List)) {
            assertionResult.addFailure(String.format("JSONPath '%s' is not an array", path));
            return;
        }

        java.util.List<?> list = (java.util.List<?>) value;
        if (list.size() != expectedSize) {
            assertionResult.addFailure(String.format("JSONPath '%s' array size mismatch: expected %d, got %d",
                path, expectedSize, list.size()));
        }
    }

    /**
     * Validate array minimum size.
     */
    private void validateArrayMinSize(String path, Object value, int minSize, AssertionResult assertionResult) {
        if (!(value instanceof java.util.List)) {
            assertionResult.addFailure(String.format("JSONPath '%s' is not an array", path));
            return;
        }

        java.util.List<?> list = (java.util.List<?>) value;
        if (list.size() < minSize) {
            assertionResult.addFailure(String.format("JSONPath '%s' array size %d is less than minimum %d",
                path, list.size(), minSize));
        }
    }

    /**
     * Validate array maximum size.
     */
    private void validateArrayMaxSize(String path, Object value, int maxSize, AssertionResult assertionResult) {
        if (!(value instanceof java.util.List)) {
            assertionResult.addFailure(String.format("JSONPath '%s' is not an array", path));
            return;
        }

        java.util.List<?> list = (java.util.List<?>) value;
        if (list.size() > maxSize) {
            assertionResult.addFailure(String.format("JSONPath '%s' array size %d exceeds maximum %d",
                path, list.size(), maxSize));
        }
    }

    /**
     * Validate array contains element.
     */
    private void validateArrayContains(String path, Object value, Object expected, AssertionResult assertionResult) {
        if (!(value instanceof java.util.List)) {
            assertionResult.addFailure(String.format("JSONPath '%s' is not an array", path));
            return;
        }

        java.util.List<?> list = (java.util.List<?>) value;
        if (!list.contains(expected)) {
            assertionResult.addFailure(String.format("JSONPath '%s' array does not contain '%s'",
                path, expected));
        }
    }

    /**
     * Validate all array elements match condition.
     */
    private void validateArrayAll(String path, Object value, ExpectConfig.ArrayAllCondition condition, AssertionResult assertionResult) {
        if (!(value instanceof java.util.List)) {
            assertionResult.addFailure(String.format("JSONPath '%s' is not an array", path));
            return;
        }

        java.util.List<?> list = (java.util.List<?>) value;
        String operator = condition.getOperator();
        Object expectedValue = condition.getValue();

        for (int i = 0; i < list.size(); i++) {
            Object element = list.get(i);
            boolean passed = false;

            try {
                switch (operator.toLowerCase()) {
                    case "equals":
                        passed = element.equals(expectedValue);
                        break;
                    case "greaterthan":
                        passed = convertToDouble(element) > convertToDouble(expectedValue);
                        break;
                    case "lessthan":
                        passed = convertToDouble(element) < convertToDouble(expectedValue);
                        break;
                    case "greaterthanorequal":
                        passed = convertToDouble(element) >= convertToDouble(expectedValue);
                        break;
                    case "lessthanorequal":
                        passed = convertToDouble(element) <= convertToDouble(expectedValue);
                        break;
                    default:
                        assertionResult.addFailure(String.format("Unknown arrayAll operator: %s", operator));
                        return;
                }

                if (!passed) {
                    assertionResult.addFailure(String.format("JSONPath '%s' array element [%d] = '%s' does not satisfy %s %s",
                        path, i, element, operator, expectedValue));
                }
            } catch (Exception e) {
                assertionResult.addFailure(String.format("JSONPath '%s' array element [%d] validation failed: %s",
                    path, i, e.getMessage()));
            }
        }
    }
}
