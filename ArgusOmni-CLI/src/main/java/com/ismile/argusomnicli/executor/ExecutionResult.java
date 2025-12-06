package com.ismile.argusomnicli.executor;

import com.ismile.argusomnicli.model.PerformanceMetrics;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Encapsulates execution result.
 * Follows Encapsulation - immutable result object.
 */
@Data
@Builder(toBuilder = true)
public class ExecutionResult {
    private boolean success;
    private String stepName;
    private Object response;
    private Map<String, Object> extractedVariables;
    private String errorMessage;
    private Integer statusCode;
    private long durationMs;
    private RequestDetails requestDetails;
    private PerformanceMetrics performanceMetrics;
    private boolean continueOnError; // If true, failure is expected and shouldn't count as critical

    @Data
    @Builder
    public static class RequestDetails {
        private String url;
        private String method;
        private Map<String, String> headers;
        private Map<String, String> cookies;
        private Object body;
    }
}
