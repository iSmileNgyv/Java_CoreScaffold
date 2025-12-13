package com.ismile.core.chronovcs.dto.integration;

import com.ismile.core.chronovcs.entity.TaskIntegrationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class CreateTaskIntegrationRequest {

    @NotNull(message = "Integration type is required")
    private TaskIntegrationType type;

    @NotBlank(message = "Integration name is required")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Name can only contain letters, numbers, underscores and hyphens")
    private String name;

    private Boolean enabled;

    // JIRA specific fields
    @Size(max = 255, message = "JIRA URL must not exceed 255 characters")
    @Pattern(regexp = "^(https?://)?[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$",
             message = "JIRA URL must be a valid URL",
             flags = Pattern.Flag.CASE_INSENSITIVE)
    private String jiraUrl;

    @Pattern(regexp = "^(BASIC|BEARER)$", message = "Auth type must be BASIC or BEARER")
    private String jiraAuthType;

    @Size(max = 100, message = "Username must not exceed 100 characters")
    private String jiraUsername;

    @Size(max = 1000, message = "API token must not exceed 1000 characters")
    private String jiraApiToken;

    // GENERIC specific fields
    @Size(max = 500, message = "API URL must not exceed 500 characters")
    @Pattern(regexp = "^(https?://)?[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$",
             message = "API URL must be a valid URL",
             flags = Pattern.Flag.CASE_INSENSITIVE)
    private String apiUrl;

    @Pattern(regexp = "^(GET|POST|PUT|DELETE|PATCH)$", message = "HTTP method must be GET, POST, PUT, DELETE, or PATCH")
    private String httpMethod;

    @Valid
    private List<HeaderDto> headers;

    @Valid
    private RequestConfigDto request;

    @Valid
    private ResponseConfigDto response;

    // Version mappings
    @Valid
    private List<VersionMappingDto> versionMappings;
}
