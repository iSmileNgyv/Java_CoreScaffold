package com.ismile.argusomnicli.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Represents a single test step.
 * Polymorphic model supporting multiple step types.
 * Follows Encapsulation - data access through getters/setters.
 */
@Data
public class TestStep {
    private String id;          // Unique identifier for dependency tracking
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
    private WaitConfig wait;
    private LoopConfig loop;
    private IfConfig ifConfig;
    private MockConfig mock;

    // Common configurations
    private Map<String, String> extract;
    private ExpectConfig expect;
    private boolean continueOnError;

    // Retry configuration
    private Integer maxRetries;      // Maximum number of retry attempts
    private Integer retryInterval;   // Wait time between retries (ms)

    // Dependency configuration for parallel execution
    private List<String> dependsOn;  // List of test IDs this test depends on
}
