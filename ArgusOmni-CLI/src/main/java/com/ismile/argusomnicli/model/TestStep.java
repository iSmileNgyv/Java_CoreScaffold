package com.ismile.argusomnicli.model;

import lombok.Data;

import java.util.Map;

/**
 * Represents a single test step.
 * Polymorphic model supporting multiple step types.
 * Follows Encapsulation - data access through getters/setters.
 */
@Data
public class TestStep {
    private String name;
    private StepType type;

    // Executor-specific configurations (polymorphic)
    private RestConfig rest;
    private GrpcConfig grpc;
    private FileSystemConfig fs;
    private BashConfig bash;
    private ResolvePathConfig resolvePath;
    private SetConfig set;
    private TransformConfig transform;

    // Common configurations
    private Map<String, String> extract;
    private ExpectConfig expect;
    private boolean continueOnError;
}
