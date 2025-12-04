package com.ismile.argusomnicli.model;

import lombok.Data;

import java.util.Map;

/**
 * gRPC step configuration.
 * Encapsulates gRPC-specific parameters.
 */
@Data
public class GrpcConfig {
    private String proto;
    private String descriptorSet;
    private String host;
    private String service;
    private String method;
    private Map<String, Object> request;
    private Map<String, String> metadata;
    private Integer timeout;
}
