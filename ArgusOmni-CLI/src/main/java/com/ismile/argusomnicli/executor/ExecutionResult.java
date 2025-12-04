package com.ismile.argusomnicli.executor;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Encapsulates execution result.
 * Follows Encapsulation - immutable result object.
 */
@Data
@Builder
public class ExecutionResult {
    private boolean success;
    private String stepName;
    private Object response;
    private Map<String, Object> extractedVariables;
    private String errorMessage;
    private Integer statusCode;
    private long durationMs;
}
