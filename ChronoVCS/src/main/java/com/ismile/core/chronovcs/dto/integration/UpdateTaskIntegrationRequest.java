package com.ismile.core.chronovcs.dto.integration;

import lombok.Data;

import java.util.List;

@Data
public class UpdateTaskIntegrationRequest {
    private String name;
    private Boolean enabled;

    // JIRA specific fields
    private String jiraUrl;
    private String jiraAuthType;
    private String jiraUsername;
    private String jiraApiToken;

    // GENERIC specific fields
    private String apiUrl;
    private String httpMethod;
    private List<HeaderDto> headers;
    private RequestConfigDto request;
    private ResponseConfigDto response;
}
