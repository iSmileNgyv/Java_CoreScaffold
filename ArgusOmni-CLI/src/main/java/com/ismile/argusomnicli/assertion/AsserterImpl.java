package com.ismile.argusomnicli.assertion;

import com.ismile.argusomnicli.executor.ExecutionResult;
import com.ismile.argusomnicli.model.ExpectConfig;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.jayway.jsonpath.JsonPath;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;

/**
 * Assertion engine implementation.
 * Follows Single Responsibility - only validates expectations.
 */
@Component
public class AsserterImpl implements Asserter {

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
}
