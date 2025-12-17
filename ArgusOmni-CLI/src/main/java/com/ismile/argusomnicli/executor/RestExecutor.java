package com.ismile.argusomnicli.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.argusomnicli.extractor.ResponseExtractor;
import com.ismile.argusomnicli.model.RestConfig;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.netty.http.client.HttpClient;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /**
     * Build URL with query parameters.
     * Supports variable resolution in both URL and query parameter values.
     * URL encoding is handled automatically by UriComponentsBuilder.
     */
    private String buildUrlWithQueryParams(RestConfig config, ExecutionContext context) {
        String url = variableResolver.resolve(config.getUrl(), context.getVariableContext());

        if (config.getQueryParams() != null && !config.getQueryParams().isEmpty()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

            config.getQueryParams().forEach((key, value) -> {
                String resolvedValue = variableResolver.resolve(value, context.getVariableContext());
                builder.queryParam(key, resolvedValue);
            });

            url = builder.build().toUriString();
        }

        return url;
    }

    @Override
    protected Object doExecute(TestStep step, ExecutionContext context) throws Exception {
        RestConfig config = step.getRest();

        // Resolve variables in config and build URL with query parameters
        String url = buildUrlWithQueryParams(config, context);
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
        if (config.getMultipart() != null && !config.getMultipart().isEmpty()) {
            // For multipart, show field names and file info instead of full content
            Map<String, Object> multipartInfo = new HashMap<>();
            for (Map.Entry<String, Object> entry : config.getMultipart().entrySet()) {
                Object value = variableResolver.resolveObject(entry.getValue(), context.getVariableContext());

                // Check if value is a List/Array
                if (value instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> items = (java.util.List<Object>) value;
                    java.util.List<String> arrayInfo = new java.util.ArrayList<>();
                    for (Object item : items) {
                        if (item instanceof String && isFilePath((String) item)) {
                            arrayInfo.add("[FILE: " + item + "]");
                        } else {
                            arrayInfo.add(String.valueOf(item));
                        }
                    }
                    multipartInfo.put(entry.getKey(), "[ARRAY: " + arrayInfo + "]");
                }
                // Check if value is explicit array config
                else if (value instanceof Map && ((Map<?, ?>) value).containsKey("type")
                         && "array".equals(((Map<?, ?>) value).get("type"))) {
                    Map<?, ?> arrayConfig = (Map<?, ?>) value;
                    String arrayFormat = arrayConfig.containsKey("arrayFormat")
                        ? String.valueOf(arrayConfig.get("arrayFormat"))
                        : "brackets";
                    multipartInfo.put(entry.getKey(), "[ARRAY format=" + arrayFormat + ", items=" + arrayConfig.get("items") + "]");
                }
                // Check if value is file config
                else if (value instanceof Map && ((Map<?, ?>) value).containsKey("path")) {
                    Map<?, ?> fileConfig = (Map<?, ?>) value;
                    multipartInfo.put(entry.getKey(), "[FILE: " + fileConfig.get("path") + "]");
                }
                // Check if value is simple file path
                else if (value instanceof String && isFilePath((String) value)) {
                    multipartInfo.put(entry.getKey(), "[FILE: " + value + "]");
                }
                // Regular form field
                else {
                    multipartInfo.put(entry.getKey(), value);
                }
            }
            requestBody = multipartInfo;
        } else if (config.getBody() != null) {
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

        // Start timing for performance assertion
        long startTime = System.currentTimeMillis();

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
            if (config.getMultipart() != null && !config.getMultipart().isEmpty()) {
                // Handle multipart/form-data request
                MultipartBodyBuilder multipartBuilder = buildMultipartBody(config, context);
                finalRequest = request.contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(BodyInserters.fromMultipartData(multipartBuilder.build()));
            } else if (config.getBody() != null) {
                // Handle regular JSON body
                Object resolvedBody = variableResolver.resolveObject(config.getBody(), context.getVariableContext());

                if (context.isVerbose()) {
                    System.out.println("  📤 Resolved body type: " + resolvedBody.getClass().getName());
                    System.out.println("  📤 Resolved body: " + resolvedBody);
                }

                // Serialize to JSON string to ensure proper JSON encoding
                String jsonBody = objectMapper.writeValueAsString(resolvedBody);

                if (context.isVerbose()) {
                    System.out.println("  📤 JSON body: " + jsonBody);
                }

                finalRequest = request.contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(jsonBody);
            } else {
                finalRequest = request;
            }

            // Execute request with timeout and capture response entity
            int timeout = config.getTimeout() != null ? config.getTimeout() : 30000;

            var responseEntity = finalRequest.retrieve()
                    .toEntity(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            // Calculate response time
            long responseTime = System.currentTimeMillis() - startTime;
            context.setVariable("_last_response_time", responseTime);

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
                    Object parsedResponse = objectMapper.readValue(responseBody, Object.class);
                    if (context.isVerbose()) {
                        System.out.println("  📥 Response: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedResponse));
                    }
                    return parsedResponse;
                } catch (Exception e) {
                    if (context.isVerbose()) {
                        System.out.println("  📥 Response (non-JSON): " + responseBody);
                    }
                    return responseBody;
                }
            }

            return null;

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            // HTTP error response (4xx, 5xx)
            // Calculate response time even for error responses
            long responseTime = System.currentTimeMillis() - startTime;
            context.setVariable("_last_response_time", responseTime);

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
            // Calculate response time even for errors
            long responseTime = System.currentTimeMillis() - startTime;
            context.setVariable("_last_response_time", responseTime);

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

    // ==================== Multipart/Form-Data Support Methods ====================

    /**
     * Build multipart body from configuration.
     * Supports both simple file paths and advanced file metadata.
     *
     * Examples:
     * 1. Simple file: { "photo": "/path/to/image.jpg" }
     * 2. Advanced file: { "file": { "path": "/path/to/doc.pdf", "fieldName": "document", "contentType": "application/pdf" } }
     * 3. Form field: { "name": "John Doe" }
     * 4. Mixed: { "photo": "/path/to/image.jpg", "name": "John", "age": "30" }
     * 5. Simple array: { "photos": ["/path/1.jpg", "/path/2.jpg"] }
     * 6. Explicit array: { "photos": { "type": "array", "arrayFormat": "brackets", "items": [...] } }
     */
    private MultipartBodyBuilder buildMultipartBody(RestConfig config, ExecutionContext context) throws Exception {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        for (Map.Entry<String, Object> entry : config.getMultipart().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Resolve variables in values
            Object resolvedValue = variableResolver.resolveObject(value, context.getVariableContext());

            // Check if value is a List/Array (simple array support)
            if (resolvedValue instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> items = (java.util.List<Object>) resolvedValue;
                // Default array format: brackets
                addArrayParts(builder, key, items, "brackets", context);
            }
            // Check if value is explicit array configuration
            else if (resolvedValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapValue = (Map<String, Object>) resolvedValue;

                if (mapValue.containsKey("type") && "array".equals(mapValue.get("type"))) {
                    // Explicit array configuration
                    addExplicitArrayParts(builder, key, mapValue, context);
                } else if (mapValue.containsKey("path")) {
                    // Advanced file configuration
                    addFilePartAdvanced(builder, key, mapValue, context);
                } else {
                    // Regular form field that happens to be a map
                    builder.part(key, resolvedValue);
                }
            } else if (resolvedValue instanceof String) {
                String strValue = (String) resolvedValue;

                // Check if it's a file path
                if (isFilePath(strValue)) {
                    // Simple file upload
                    addFilePartSimple(builder, key, strValue);
                } else {
                    // Regular form field
                    builder.part(key, strValue);
                }
            } else {
                // Other types (numbers, booleans, etc.)
                builder.part(key, resolvedValue);
            }
        }

        return builder;
    }

    /**
     * Add array items as multiple parts.
     * Supports different array naming formats.
     */
    private void addArrayParts(MultipartBodyBuilder builder, String fieldName, java.util.List<Object> items,
                               String arrayFormat, ExecutionContext context) throws Exception {
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            Object resolvedItem = variableResolver.resolveObject(item, context.getVariableContext());

            String partName = formatArrayFieldName(fieldName, i, arrayFormat);

            // Check if item is a file path
            if (resolvedItem instanceof String && isFilePath((String) resolvedItem)) {
                addFilePartSimple(builder, partName, (String) resolvedItem);
            } else if (resolvedItem instanceof Map && ((Map<?, ?>) resolvedItem).containsKey("path")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fileConfig = (Map<String, Object>) resolvedItem;
                addFilePartAdvanced(builder, partName, fileConfig, context);
            } else {
                // Regular form field
                builder.part(partName, resolvedItem);
            }
        }
    }

    /**
     * Add explicit array configuration parts.
     * Format: { type: "array", arrayFormat: "brackets", items: [...] }
     */
    private void addExplicitArrayParts(MultipartBodyBuilder builder, String fieldName,
                                      Map<String, Object> config, ExecutionContext context) throws Exception {
        Object itemsObj = config.get("items");
        if (!(itemsObj instanceof java.util.List)) {
            throw new Exception("Array configuration must have 'items' as a list");
        }

        @SuppressWarnings("unchecked")
        java.util.List<Object> items = (java.util.List<Object>) itemsObj;

        // Get array format (default: brackets)
        String arrayFormat = config.containsKey("arrayFormat")
            ? config.get("arrayFormat").toString()
            : "brackets";

        addArrayParts(builder, fieldName, items, arrayFormat, context);
    }

    /**
     * Format array field name based on arrayFormat.
     * Supports: brackets (photos[]), indexed (photos[0]), same (photos)
     */
    private String formatArrayFieldName(String fieldName, int index, String arrayFormat) {
        return switch (arrayFormat.toLowerCase()) {
            case "brackets" -> fieldName + "[]";
            case "indexed" -> fieldName + "[" + index + "]";
            case "same" -> fieldName;
            default -> fieldName + "[]";  // Default to brackets
        };
    }

    /**
     * Check if a string looks like a file path.
     * Heuristic: contains file extension or starts with common path indicators.
     */
    private boolean isFilePath(String value) {
        // Check for file extensions
        if (value.matches(".*\\.(jpg|jpeg|png|gif|pdf|doc|docx|xls|xlsx|txt|csv|json|xml|zip|tar|gz)$")) {
            return true;
        }

        // Check if file exists
        Path path = Paths.get(value);
        return Files.exists(path) && Files.isRegularFile(path);
    }

    /**
     * Add file part with simple configuration (just file path).
     * Field name defaults to the key name.
     */
    private void addFilePartSimple(MultipartBodyBuilder builder, String fieldName, String filePath) throws Exception {
        File file = new File(filePath);

        if (!file.exists()) {
            throw new Exception("File not found: " + filePath);
        }

        if (!file.isFile()) {
            throw new Exception("Path is not a file: " + filePath);
        }

        FileSystemResource fileResource = new FileSystemResource(file);

        // Auto-detect content type from file extension
        String contentType = detectContentType(filePath);

        builder.part(fieldName, fileResource)
               .contentType(MediaType.parseMediaType(contentType))
               .filename(file.getName());
    }

    /**
     * Add file part with advanced configuration.
     * Supports custom field name, content type, and filename.
     */
    private void addFilePartAdvanced(MultipartBodyBuilder builder, String key, Map<String, Object> fileConfig, ExecutionContext context) throws Exception {
        String filePath = variableResolver.resolve(fileConfig.get("path").toString(), context.getVariableContext());
        String fieldName = fileConfig.containsKey("fieldName")
                ? variableResolver.resolve(fileConfig.get("fieldName").toString(), context.getVariableContext())
                : key;
        String contentType = fileConfig.containsKey("contentType")
                ? variableResolver.resolve(fileConfig.get("contentType").toString(), context.getVariableContext())
                : detectContentType(filePath);
        String filename = fileConfig.containsKey("filename")
                ? variableResolver.resolve(fileConfig.get("filename").toString(), context.getVariableContext())
                : new File(filePath).getName();

        File file = new File(filePath);

        if (!file.exists()) {
            throw new Exception("File not found: " + filePath);
        }

        if (!file.isFile()) {
            throw new Exception("Path is not a file: " + filePath);
        }

        FileSystemResource fileResource = new FileSystemResource(file);

        builder.part(fieldName, fileResource)
               .contentType(MediaType.parseMediaType(contentType))
               .filename(filename);
    }

    /**
     * Detect content type from file extension.
     */
    private String detectContentType(String filePath) {
        String extension = "";
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0) {
            extension = filePath.substring(lastDot + 1).toLowerCase();
        }

        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "txt" -> "text/plain";
            case "csv" -> "text/csv";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "zip" -> "application/zip";
            case "tar" -> "application/x-tar";
            case "gz" -> "application/gzip";
            default -> "application/octet-stream";
        };
    }
}
