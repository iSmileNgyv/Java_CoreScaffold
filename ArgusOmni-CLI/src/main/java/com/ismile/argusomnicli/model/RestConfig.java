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

    // Multipart/form-data support
    // Used for file uploads and form submissions
    // Example (simple):
    //   multipart:
    //     profilePhoto: "/path/to/photo.jpg"
    //     name: "John Doe"
    //     age: "30"
    //
    // Example (advanced with file metadata):
    //   multipart:
    //     file:
    //       path: "/path/to/document.pdf"
    //       fieldName: "document"
    //       contentType: "application/pdf"
    //       filename: "custom-name.pdf"
    //     name: "John Doe"
    //     email: "john@example.com"
    //
    // Example (simple array):
    //   multipart:
    //     photos:
    //       - "/path/to/photo1.jpg"
    //       - "/path/to/photo2.jpg"
    //     name: "Gallery Upload"
    //
    // Example (explicit array with format):
    //   multipart:
    //     photos:
    //       type: array
    //       arrayFormat: brackets  # or "indexed", "same"
    //       items:
    //         - "/path/to/photo1.jpg"
    //         - "/path/to/photo2.jpg"
    //     description: "Multiple photos"
    private Map<String, Object> multipart;
}
