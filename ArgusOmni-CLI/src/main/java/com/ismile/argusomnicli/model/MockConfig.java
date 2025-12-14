package com.ismile.argusomnicli.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Mock Server configuration.
 * Allows starting WireMock servers, configuring stubs, and verifying requests.
 */
@Data
public class MockConfig {

    /**
     * Action to perform: start, stop, stub, verify, reset
     */
    private String action;

    /**
     * Server port (default: random)
     */
    private Integer port;

    /**
     * Server name/ID for managing multiple mock servers
     */
    private String serverId;

    /**
     * Stub configuration (for action: stub)
     */
    private StubConfig stub;

    /**
     * Verification configuration (for action: verify)
     */
    private VerifyConfig verify;

    /**
     * Auto-start server if not running (default: true)
     */
    private Boolean autoStart;

    /**
     * Store server port in variable
     */
    private String portVariable;

    /**
     * Store server base URL in variable
     */
    private String baseUrlVariable;

    /**
     * Stub configuration
     */
    @Data
    public static class StubConfig {
        private RequestPattern request;
        private ResponseDefinition response;
        private Integer priority;
        private String scenarioName;
        private String requiredScenarioState;
        private String newScenarioState;
    }

    /**
     * Request pattern for stub matching
     */
    @Data
    public static class RequestPattern {
        private String method;
        private String url;
        private String urlPath;
        private String urlPathPattern;
        private Map<String, Object> queryParameters;
        private Map<String, Object> headers;
        private String bodyPattern;
        private String bodyJson;
    }

    /**
     * Response definition
     */
    @Data
    public static class ResponseDefinition {
        private Integer status;
        private String body;
        private Map<String, String> headers;
        private Integer fixedDelayMilliseconds;
        private String bodyFileName;
        private Map<String, Object> jsonBody;
    }

    /**
     * Request verification configuration
     */
    @Data
    public static class VerifyConfig {
        private RequestPattern request;
        private Integer count;
        private String countExpression; // exactly, atLeast, atMost
    }
}
