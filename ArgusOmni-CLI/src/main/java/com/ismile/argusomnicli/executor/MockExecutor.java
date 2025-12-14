package com.ismile.argusomnicli.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.ismile.argusomnicli.extractor.ResponseExtractor;
import com.ismile.argusomnicli.model.MockConfig;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Mock Server executor using WireMock.
 * Manages mock servers, stubs, and request verification.
 */
@Component
public class MockExecutor extends AbstractExecutor {

    private static final Map<String, WireMockServer> servers = new ConcurrentHashMap<>();
    private static final String DEFAULT_SERVER_ID = "default";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MockExecutor(VariableResolver variableResolver, ResponseExtractor responseExtractor) {
        super(variableResolver, responseExtractor);
    }

    @Override
    public boolean supports(TestStep step) {
        return step.getType() == StepType.MOCK && step.getMock() != null;
    }

    @Override
    protected Object doExecute(TestStep step, ExecutionContext context) throws Exception {
        MockConfig config = step.getMock();
        String action = config.getAction();

        if (action == null || action.isEmpty()) {
            throw new Exception("Mock action is required (start, stop, stub, verify, reset)");
        }

        String serverId = config.getServerId() != null ? config.getServerId() : DEFAULT_SERVER_ID;

        switch (action.toLowerCase()) {
            case "start":
                return startServer(config, serverId, context);
            case "stop":
                return stopServer(serverId, context);
            case "stub":
                return createStub(config, serverId, context);
            case "verify":
                return verifyRequest(config, serverId, context);
            case "reset":
                return resetServer(serverId, context);
            default:
                throw new Exception("Unknown mock action: " + action +
                    " (supported: start, stop, stub, verify, reset)");
        }
    }

    /**
     * Start a WireMock server.
     */
    private Object startServer(MockConfig config, String serverId, ExecutionContext context) throws Exception {
        // Check if server already exists
        if (servers.containsKey(serverId)) {
            WireMockServer existingServer = servers.get(serverId);
            if (existingServer.isRunning()) {
                if (context.isVerbose()) {
                    System.out.println("ℹ️  Mock server '" + serverId + "' already running on port " +
                        existingServer.port());
                }
                storeServerInfo(config, existingServer, context);
                return "Server already running on port " + existingServer.port();
            }
        }

        // Configure server
        WireMockConfiguration wireMockConfig = WireMockConfiguration.options();

        if (config.getPort() != null) {
            wireMockConfig.port(config.getPort());
        } else {
            wireMockConfig.dynamicPort();
        }

        // Create and start server
        WireMockServer server = new WireMockServer(wireMockConfig);
        server.start();
        servers.put(serverId, server);

        if (context.isVerbose()) {
            System.out.println("✅ Mock server '" + serverId + "' started on port " + server.port());
        }

        // Store server info in variables
        storeServerInfo(config, server, context);

        return "Server started on port " + server.port();
    }

    /**
     * Store server port and base URL in variables.
     */
    private void storeServerInfo(MockConfig config, WireMockServer server, ExecutionContext context) {
        if (config.getPortVariable() != null) {
            context.getVariableContext().set(config.getPortVariable(), server.port());
        }
        if (config.getBaseUrlVariable() != null) {
            context.getVariableContext().set(config.getBaseUrlVariable(),
                "http://localhost:" + server.port());
        }
    }

    /**
     * Stop a WireMock server.
     */
    private Object stopServer(String serverId, ExecutionContext context) throws Exception {
        WireMockServer server = servers.get(serverId);
        if (server == null) {
            throw new Exception("Mock server '" + serverId + "' not found");
        }

        server.stop();
        servers.remove(serverId);

        if (context.isVerbose()) {
            System.out.println("🛑 Mock server '" + serverId + "' stopped");
        }

        return "Server stopped";
    }

    /**
     * Create a stub mapping.
     */
    private Object createStub(MockConfig config, String serverId, ExecutionContext context) throws Exception {
        WireMockServer server = getOrStartServer(config, serverId, context);

        if (config.getStub() == null) {
            throw new Exception("Stub configuration is required for action: stub");
        }

        MockConfig.StubConfig stub = config.getStub();
        MockConfig.RequestPattern requestPattern = stub.getRequest();
        MockConfig.ResponseDefinition responseDefinition = stub.getResponse();

        if (requestPattern == null || responseDefinition == null) {
            throw new Exception("Both request and response are required in stub configuration");
        }

        // Build request pattern
        var requestBuilder = buildRequestPattern(requestPattern, context);

        // Build response definition
        var responseBuilder = aResponse()
            .withStatus(responseDefinition.getStatus() != null ? responseDefinition.getStatus() : 200);

        // Add response body
        if (responseDefinition.getBody() != null) {
            String resolvedBody = variableResolver.resolve(responseDefinition.getBody(), context.getVariableContext());
            responseBuilder.withBody(resolvedBody);
        } else if (responseDefinition.getJsonBody() != null) {
            String jsonBody = objectMapper.writeValueAsString(responseDefinition.getJsonBody());
            responseBuilder.withBody(jsonBody)
                .withHeader("Content-Type", "application/json");
        }

        // Add headers
        if (responseDefinition.getHeaders() != null) {
            for (Map.Entry<String, String> header : responseDefinition.getHeaders().entrySet()) {
                String resolvedValue = variableResolver.resolve(header.getValue(), context.getVariableContext());
                responseBuilder.withHeader(header.getKey(), resolvedValue);
            }
        }

        // Add delay
        if (responseDefinition.getFixedDelayMilliseconds() != null) {
            responseBuilder.withFixedDelay(responseDefinition.getFixedDelayMilliseconds());
        }

        // Create stub mapping
        StubMapping stubMapping = server.stubFor(requestBuilder.willReturn(responseBuilder));

        // Set priority if specified
        if (stub.getPriority() != null) {
            stubMapping.setPriority(stub.getPriority());
        }

        // Set scenario state if specified
        if (stub.getScenarioName() != null) {
            stubMapping.setScenarioName(stub.getScenarioName());
            if (stub.getRequiredScenarioState() != null) {
                stubMapping.setRequiredScenarioState(stub.getRequiredScenarioState());
            }
            if (stub.getNewScenarioState() != null) {
                stubMapping.setNewScenarioState(stub.getNewScenarioState());
            }
        }

        if (context.isVerbose()) {
            System.out.println("📍 Stub created: " + requestPattern.getMethod() + " " +
                (requestPattern.getUrl() != null ? requestPattern.getUrl() : requestPattern.getUrlPath()));
        }

        return "Stub created successfully";
    }

    /**
     * Build WireMock request pattern from configuration.
     */
    private com.github.tomakehurst.wiremock.client.MappingBuilder buildRequestPattern(
            MockConfig.RequestPattern pattern, ExecutionContext context) throws Exception {

        String method = pattern.getMethod() != null ? pattern.getMethod().toUpperCase() : "GET";

        com.github.tomakehurst.wiremock.client.MappingBuilder builder;

        // URL matching
        if (pattern.getUrl() != null) {
            String url = variableResolver.resolve(pattern.getUrl(), context.getVariableContext());
            builder = request(method, urlEqualTo(url));
        } else if (pattern.getUrlPath() != null) {
            String urlPath = variableResolver.resolve(pattern.getUrlPath(), context.getVariableContext());
            builder = request(method, urlPathEqualTo(urlPath));
        } else if (pattern.getUrlPathPattern() != null) {
            String urlPattern = variableResolver.resolve(pattern.getUrlPathPattern(), context.getVariableContext());
            builder = request(method, urlPathMatching(urlPattern));
        } else {
            builder = request(method, urlMatching(".*"));
        }

        // Query parameters
        if (pattern.getQueryParameters() != null) {
            for (Map.Entry<String, Object> param : pattern.getQueryParameters().entrySet()) {
                String value = String.valueOf(param.getValue());
                String resolvedValue = variableResolver.resolve(value, context.getVariableContext());
                builder.withQueryParam(param.getKey(), equalTo(resolvedValue));
            }
        }

        // Headers
        if (pattern.getHeaders() != null) {
            for (Map.Entry<String, Object> header : pattern.getHeaders().entrySet()) {
                String value = String.valueOf(header.getValue());
                String resolvedValue = variableResolver.resolve(value, context.getVariableContext());
                builder.withHeader(header.getKey(), equalTo(resolvedValue));
            }
        }

        // Body matching
        if (pattern.getBodyPattern() != null) {
            String bodyPattern = variableResolver.resolve(pattern.getBodyPattern(), context.getVariableContext());
            builder.withRequestBody(matching(bodyPattern));
        } else if (pattern.getBodyJson() != null) {
            String bodyJson = variableResolver.resolve(pattern.getBodyJson(), context.getVariableContext());
            builder.withRequestBody(equalToJson(bodyJson));
        }

        return builder;
    }

    /**
     * Verify request was received.
     */
    private Object verifyRequest(MockConfig config, String serverId, ExecutionContext context) throws Exception {
        WireMockServer server = servers.get(serverId);
        if (server == null) {
            throw new Exception("Mock server '" + serverId + "' not found. Start the server first.");
        }

        if (config.getVerify() == null || config.getVerify().getRequest() == null) {
            throw new Exception("Verify configuration with request pattern is required");
        }

        MockConfig.VerifyConfig verify = config.getVerify();
        MockConfig.RequestPattern requestPattern = verify.getRequest();

        // Build request pattern for verification
        RequestPatternBuilder patternBuilder = buildVerifyPattern(requestPattern, context);

        // Determine count matching
        int expectedCount = verify.getCount() != null ? verify.getCount() : 1;
        String countExpression = verify.getCountExpression() != null ?
            verify.getCountExpression().toLowerCase() : "exactly";

        // Perform verification
        List<LoggedRequest> requests = server.findAll(patternBuilder);
        int actualCount = requests.size();

        boolean verified = false;
        String message = "";

        switch (countExpression) {
            case "exactly":
                verified = actualCount == expectedCount;
                message = String.format("Expected exactly %d requests, found %d", expectedCount, actualCount);
                break;
            case "atleast":
                verified = actualCount >= expectedCount;
                message = String.format("Expected at least %d requests, found %d", expectedCount, actualCount);
                break;
            case "atmost":
                verified = actualCount <= expectedCount;
                message = String.format("Expected at most %d requests, found %d", expectedCount, actualCount);
                break;
            default:
                throw new Exception("Unknown count expression: " + countExpression +
                    " (supported: exactly, atLeast, atMost)");
        }

        if (!verified) {
            throw new Exception("Request verification failed: " + message);
        }

        if (context.isVerbose()) {
            System.out.println("✅ Request verified: " + message);
        }

        return message;
    }

    /**
     * Build request pattern for verification.
     */
    private RequestPatternBuilder buildVerifyPattern(MockConfig.RequestPattern pattern,
                                                     ExecutionContext context) throws Exception {
        String method = pattern.getMethod() != null ? pattern.getMethod().toUpperCase() : "GET";

        RequestPatternBuilder builder;

        // URL matching - create builder with proper URL matcher
        if (pattern.getUrl() != null) {
            String url = variableResolver.resolve(pattern.getUrl(), context.getVariableContext());
            builder = createBuilderForMethod(method, urlEqualTo(url));
        } else if (pattern.getUrlPath() != null) {
            String urlPath = variableResolver.resolve(pattern.getUrlPath(), context.getVariableContext());
            builder = createBuilderForMethod(method, urlPathEqualTo(urlPath));
        } else if (pattern.getUrlPathPattern() != null) {
            String urlPattern = variableResolver.resolve(pattern.getUrlPathPattern(), context.getVariableContext());
            builder = createBuilderForMethod(method, urlPathMatching(urlPattern));
        } else {
            builder = createBuilderForMethod(method, urlMatching(".*"));
        }

        // Headers
        if (pattern.getHeaders() != null) {
            for (Map.Entry<String, Object> header : pattern.getHeaders().entrySet()) {
                String value = String.valueOf(header.getValue());
                String resolvedValue = variableResolver.resolve(value, context.getVariableContext());
                builder.withHeader(header.getKey(), equalTo(resolvedValue));
            }
        }

        return builder;
    }

    /**
     * Create RequestPatternBuilder for specific HTTP method.
     */
    private RequestPatternBuilder createBuilderForMethod(String method,
                                                         com.github.tomakehurst.wiremock.matching.UrlPattern urlPattern) {
        switch (method.toUpperCase()) {
            case "GET":
                return getRequestedFor(urlPattern);
            case "POST":
                return postRequestedFor(urlPattern);
            case "PUT":
                return putRequestedFor(urlPattern);
            case "DELETE":
                return deleteRequestedFor(urlPattern);
            case "PATCH":
                return patchRequestedFor(urlPattern);
            case "HEAD":
                return headRequestedFor(urlPattern);
            case "OPTIONS":
                return optionsRequestedFor(urlPattern);
            default:
                return anyRequestedFor(urlPattern);
        }
    }

    /**
     * Reset server (clear all stubs and request logs).
     */
    private Object resetServer(String serverId, ExecutionContext context) throws Exception {
        WireMockServer server = servers.get(serverId);
        if (server == null) {
            throw new Exception("Mock server '" + serverId + "' not found");
        }

        server.resetAll();

        if (context.isVerbose()) {
            System.out.println("🔄 Mock server '" + serverId + "' reset (stubs and logs cleared)");
        }

        return "Server reset successfully";
    }

    /**
     * Get server or auto-start if configured.
     */
    private WireMockServer getOrStartServer(MockConfig config, String serverId,
                                           ExecutionContext context) throws Exception {
        WireMockServer server = servers.get(serverId);

        if (server == null || !server.isRunning()) {
            boolean autoStart = config.getAutoStart() != null ? config.getAutoStart() : true;

            if (autoStart) {
                if (context.isVerbose()) {
                    System.out.println("ℹ️  Auto-starting mock server '" + serverId + "'");
                }
                startServer(config, serverId, context);
                server = servers.get(serverId);
            } else {
                throw new Exception("Mock server '" + serverId + "' not running. Start it first or enable autoStart.");
            }
        }

        return server;
    }

    /**
     * Shutdown all servers (cleanup).
     */
    public static void shutdownAll() {
        for (Map.Entry<String, WireMockServer> entry : servers.entrySet()) {
            try {
                entry.getValue().stop();
            } catch (Exception e) {
                System.err.println("Error stopping server " + entry.getKey() + ": " + e.getMessage());
            }
        }
        servers.clear();
    }
}
