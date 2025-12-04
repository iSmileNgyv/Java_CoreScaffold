package com.ismile.argusomnicli.model;

import lombok.Data;

import java.util.Map;

/**
 * REST/HTTP step configuration.
 * Encapsulates REST-specific parameters.
 */
@Data
public class RestConfig {
    private String method;
    private String url;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private Object body;
    private boolean http2;
    private Integer timeout;

    // Cookie support
    // Can be:
    // 1. String "auto" -> enable automatic cookie store
    // 2. Map with "auto": true and/or custom cookies
    // Example: cookies: auto
    // Example: cookies: { "JSESSIONID": "{{sessionId}}" }
    // Example: cookies: { "auto": true, "CUSTOM": "value" }
    private Object cookies;
}
