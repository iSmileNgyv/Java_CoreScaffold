package com.ismile.core.chronovcs.dto.integration;

import com.ismile.core.chronovcs.entity.TaskIntegrationType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaskIntegrationResponse {
    private Long id;
    private TaskIntegrationType type;
    private String name;
    private Boolean enabled;
    private LocalDateTime createdAt;

    // JIRA specific fields
    private String jiraUrl;
    private String jiraAuthType;
    private String jiraUsername;

    // GENERIC specific fields
    private String apiUrl;
    private String httpMethod;
    private List<HeaderDto> headers;
    private RequestConfigDto request;
    private ResponseConfigDto response;

    // Version mappings
    private List<VersionMappingDto> versionMappings;
}
