package com.ismile.core.chronovcs.dto.integration;

import com.ismile.core.chronovcs.entity.TaskIntegrationType;
import lombok.Data;

import java.util.List;

@Data
public class CreateTaskIntegrationRequest {
    private TaskIntegrationType type;
    private String name;
    private Boolean enabled;

    // JIRA specific fields
    private String jiraUrl;
    private String jiraAuthType;  // BASIC, BEARER
    private String jiraUsername;
    private String jiraApiToken;

    // GENERIC specific fields
    private String apiUrl;
    private String httpMethod;  // GET, POST, PUT
    private List<HeaderDto> headers;
    private RequestConfigDto request;
    private ResponseConfigDto response;

    // Version mappings
    private List<VersionMappingDto> versionMappings;
}
