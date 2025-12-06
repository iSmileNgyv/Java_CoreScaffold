package com.ismile.argusomnicli.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.argusomnicli.extractor.ResponseExtractor;
import com.ismile.argusomnicli.model.RestConfig;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.*;

/**
 * REST/HTTP executor implementation.
 * Follows Open/Closed Principle - extends AbstractExecutor without modifying it.
 * Follows Single Responsibility - only executes REST requests.
 */
@Component
public class RestExecutor extends AbstractExecutor {
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public RestExecutor(VariableResolver variableResolver,
                       ResponseExtractor responseExtractor,
                       ObjectMapper objectMapper) {
        super(variableResolver, responseExtractor);
        this.objectMapper = objectMapper;
        this.webClientBuilder = WebClient.builder();
    }

    @Override
    public boolean supports(TestStep step) {
        return step.getType() == StepType.REST && step.getRest() != null;
    }

    @Override
    protected Object doExecute(TestStep step, ExecutionContext context) throws Exception {
        RestConfig config = step.getRest();

        // Resolve variables in config
        String url = variableResolver.resolve(config.getUrl(), context.getVariableContext());
        String method = config.getMethod().toUpperCase();

        // Parse cookie configuration
        CookieConfig cookieConfig = parseCookieConfig(config, context);

        // Prepare request details for logging
        Map<String, String> requestHeaders = new HashMap<>();
        if (config.getHeaders() != null) {
            config.getHeaders().forEach((key, value) -> {
                String resolvedValue = variableResolver.resolve(value, context.getVariableContext());
                requestHeaders.put(key, resolvedValue);
            });
        }

        // Get cookies that will be sent
        Map<String, String> requestCookies = new HashMap<>();
        if (cookieConfig.isAutoEnabled()) {
            @SuppressWarnings("unchecked")
            Map<String, String> cookieJar = (Map<String, String>) context.getVariable("_cookie_jar");
            if (cookieJar != null) {
                requestCookies.putAll(cookieJar);
            }
        }
        requestCookies.putAll(cookieConfig.getManualCookies());

        // Get request body
        Object requestBody = null;
        if (config.getBody() != null) {
            requestBody = variableResolver.resolveObject(config.getBody(), context.getVariableContext());
        }

        // Store request details in context for later use
        ExecutionResult.RequestDetails requestDetails = ExecutionResult.RequestDetails.builder()
                .url(url)
                .method(method)
                .headers(requestHeaders)
                .cookies(requestCookies)
                .body(requestBody)
                .build();
        context.setVariable("_last_request_details", requestDetails);

        try {
            // Build HTTP client with HTTP/2 support if needed
            HttpClient httpClient = HttpClient.create();
            if (config.isHttp2()) {
                httpClient = httpClient.protocol(reactor.netty.http.HttpProtocol.H2);
            }

            WebClient.Builder clientBuilder = webClientBuilder
                    .clientConnector(new ReactorClientHttpConnector(httpClient));

            // Add cookie filter if auto cookies enabled
            if (cookieConfig.isAutoEnabled()) {
                clientBuilder.filter(createCookieFilter(context));
            }

            WebClient client = clientBuilder.build();

            // Build request
            WebClient.RequestBodyUriSpec requestSpec = client.method(HttpMethod.valueOf(method));
            WebClient.RequestBodySpec request = requestSpec
                    .uri(url)
                    .headers(headers -> {
                        addHeaders(headers, config, context);
                        addManualCookies(headers, cookieConfig);
                    });

            // Add body if present
            WebClient.RequestHeadersSpec<?> finalRequest;
            if (config.getBody() != null) {
                Object resolvedBody = variableResolver.resolveObject(config.getBody(), context.getVariableContext());
                finalRequest = request.contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resolvedBody);
            } else {
                finalRequest = request;
            }

            // Execute request with timeout and capture response entity
            int timeout = config.getTimeout() != null ? config.getTimeout() : 30000;
            var responseEntity = finalRequest.retrieve()
                    .toEntity(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            if (responseEntity == null) {
                context.setVariable("_last_status_code", 0);
                return null;
            }

            // Store status code in context for assertion
            context.setVariable("_last_status_code", responseEntity.getStatusCode().value());

            // Store response headers (for cookie extraction or debugging)
            HttpHeaders responseHeaders = responseEntity.getHeaders();
            context.setVariable("_last_response_headers", responseHeaders);

            // If auto cookies enabled, store Set-Cookie headers
            if (cookieConfig.isAutoEnabled()) {
                storeCookiesFromResponse(responseHeaders, context);
            }

            // Parse JSON response
            String responseBody = responseEntity.getBody();
            if (responseBody != null && !responseBody.isEmpty()) {
                try {
                    return objectMapper.readValue(responseBody, Object.class);
                } catch (Exception e) {
                    return responseBody;
                }
            }

            return null;

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            // HTTP error response (4xx, 5xx)
            context.setVariable("_last_status_code", e.getStatusCode().value());
            context.setVariable("_last_error_type", "HTTP_ERROR");

            // Parse error response body if available
            String errorBody = e.getResponseBodyAsString();
            if (errorBody != null && !errorBody.isEmpty()) {
                try {
                    Object errorResponse = objectMapper.readValue(errorBody, Object.class);
                    context.setVariable("_last_response", errorResponse);
                } catch (Exception parseEx) {
                    // If JSON parsing fails, store as string
                    context.setVariable("_last_response", errorBody);
                }
            }

            throw new Exception(String.format("HTTP %d: %s", e.getStatusCode().value(), e.getStatusText()));

        } catch (Exception e) {
            // Check error type from exception message
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

            if (errorMsg.contains("connection refused") || errorMsg.contains("connect timed out")) {
                context.setVariable("_last_status_code", 0);
                context.setVariable("_last_error_type", "CONNECTION_REFUSED");
            } else if (errorMsg.contains("timeout") || e.getCause() instanceof java.util.concurrent.TimeoutException) {
                context.setVariable("_last_status_code", 0);
                context.setVariable("_last_error_type", "TIMEOUT");
            } else {
                context.setVariable("_last_status_code", 0);
                context.setVariable("_last_error_type", "UNKNOWN");
            }

            throw e;
        }
    }

    private void addHeaders(HttpHeaders headers, RestConfig config, ExecutionContext context) {
        if (config.getHeaders() != null) {
            config.getHeaders().forEach((key, value) -> {
                String resolvedValue = variableResolver.resolve(value, context.getVariableContext());
                headers.add(key, resolvedValue);
            });
        }
    }

    // ==================== Cookie Support Methods ====================

    /**
     * Parse cookie configuration from RestConfig.
     * Supports:
     * - cookies: "auto"
     * - cookies: { "SESSION": "{{sessionId}}" }
     * - cookies: { "auto": true, "CUSTOM": "value" }
     */
    private CookieConfig parseCookieConfig(RestConfig config, ExecutionContext context) {
        Object cookiesObj = config.getCookies();
        if (cookiesObj == null) {
            return new CookieConfig(false, Collections.emptyMap());
        }

        // Case 1: cookies: "auto"
        if (cookiesObj instanceof String && "auto".equalsIgnoreCase((String) cookiesObj)) {
            return new CookieConfig(true, Collections.emptyMap());
        }

        // Case 2: cookies: { ... }
        if (cookiesObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cookieMap = (Map<String, Object>) cookiesObj;

            boolean autoEnabled = false;
            Map<String, String> manualCookies = new HashMap<>();

            for (Map.Entry<String, Object> entry : cookieMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // Check for "auto" key
                if ("auto".equalsIgnoreCase(key)) {
                    autoEnabled = value instanceof Boolean && (Boolean) value;
                } else {
                    // Manual cookie
                    String cookieValue = variableResolver.resolve(value.toString(), context.getVariableContext());
                    manualCookies.put(key, cookieValue);
                }
            }

            return new CookieConfig(autoEnabled, manualCookies);
        }

        return new CookieConfig(false, Collections.emptyMap());
    }

    /**
     * Create exchange filter for automatic cookie handling.
     */
    private ExchangeFilterFunction createCookieFilter(ExecutionContext context) {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            // Get stored cookies from context
            @SuppressWarnings("unchecked")
            Map<String, String> cookieJar = (Map<String, String>) context.getVariable("_cookie_jar");

            if (cookieJar != null && !cookieJar.isEmpty()) {
                // Build Cookie header from stored cookies
                String cookieHeader = cookieJar.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("");

                if (!cookieHeader.isEmpty()) {
                    return reactor.core.publisher.Mono.just(
                        ClientRequest.from(request)
                            .header(HttpHeaders.COOKIE, cookieHeader)
                            .build()
                    );
                }
            }

            return reactor.core.publisher.Mono.just(request);
        });
    }

    /**
     * Add manual cookies to request headers.
     */
    private void addManualCookies(HttpHeaders headers, CookieConfig cookieConfig) {
        if (cookieConfig.getManualCookies().isEmpty()) {
            return;
        }

        // Build Cookie header from manual cookies
        String cookieHeader = cookieConfig.getManualCookies().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "; " + b)
                .orElse("");

        if (!cookieHeader.isEmpty()) {
            headers.add(HttpHeaders.COOKIE, cookieHeader);
        }
    }

    /**
     * Store cookies from response headers to context.
     */
    private void storeCookiesFromResponse(HttpHeaders responseHeaders, ExecutionContext context) {
        List<String> setCookieHeaders = responseHeaders.get(HttpHeaders.SET_COOKIE);
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return;
        }

        // Get or create cookie jar
        @SuppressWarnings("unchecked")
        Map<String, String> cookieJar = (Map<String, String>) context.getVariable("_cookie_jar");
        if (cookieJar == null) {
            cookieJar = new HashMap<>();
        }

        // Parse Set-Cookie headers and store cookies
        for (String setCookie : setCookieHeaders) {
            // Simple parsing: extract name=value (ignores path, domain, etc.)
            String[] parts = setCookie.split(";");
            if (parts.length > 0) {
                String[] nameValue = parts[0].split("=", 2);
                if (nameValue.length == 2) {
                    String name = nameValue[0].trim();
                    String value = nameValue[1].trim();
                    cookieJar.put(name, value);
                }
            }
        }

        context.setVariable("_cookie_jar", cookieJar);
    }

    /**
     * Cookie configuration holder.
     */
    private static class CookieConfig {
        private final boolean autoEnabled;
        private final Map<String, String> manualCookies;

        public CookieConfig(boolean autoEnabled, Map<String, String> manualCookies) {
            this.autoEnabled = autoEnabled;
            this.manualCookies = manualCookies;
        }

        public boolean isAutoEnabled() {
            return autoEnabled;
        }

        public Map<String, String> getManualCookies() {
            return manualCookies;
        }
    }
}
