package com.ismile.core.chronovcs.service.integration;

import com.ismile.core.chronovcs.client.TaskIntegrationClient;
import com.ismile.core.chronovcs.dto.integration.*;
import com.ismile.core.chronovcs.entity.*;
import com.ismile.core.chronovcs.repository.*;
import com.ismile.core.chronovcs.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskIntegrationService {

    private final TaskIntegrationRepository integrationRepository;
    private final TaskRepository taskRepository;
    private final VersionMappingRepository versionMappingRepository;
    private final List<TaskIntegrationClient> integrationClients;
    private final EncryptionService encryptionService;

    /**
     * Create a new task integration
     */
    @Transactional
    public TaskIntegrationResponse createIntegration(CreateTaskIntegrationRequest request) {
        log.info("Creating task integration: {} of type {}", request.getName(), request.getType());

        // Validate unique name
        if (integrationRepository.findByName(request.getName()).isPresent()) {
            throw new RuntimeException("Task integration with name already exists: " + request.getName());
        }

        // Build entity
        TaskIntegrationEntity integration = TaskIntegrationEntity.builder()
                .type(request.getType())
                .name(request.getName())
                .enabled(request.getEnabled() != null ? request.getEnabled() : false)
                .build();

        // Set type-specific fields
        if (request.getType() == TaskIntegrationType.JIRA) {
            integration.setJiraUrl(request.getJiraUrl());
            integration.setJiraAuthType(request.getJiraAuthType());
            integration.setJiraUsername(request.getJiraUsername());
            // Encrypt token before storing
            if (request.getJiraApiToken() != null && !request.getJiraApiToken().isEmpty()) {
                String encrypted = encryptionService.encrypt(request.getJiraApiToken());
                integration.setJiraApiTokenEncrypted(encrypted);
                log.debug("JIRA API token encrypted successfully");
            }
        } else if (request.getType() == TaskIntegrationType.GENERIC) {
            integration.setApiUrl(request.getApiUrl());
            integration.setHttpMethod(request.getHttpMethod());
        }

        // Save first to generate ID
        integration = integrationRepository.save(integration);

        // Add related entities for GENERIC type
        if (request.getType() == TaskIntegrationType.GENERIC) {
            // Add headers
            if (request.getHeaders() != null) {
                final TaskIntegrationEntity finalIntegration = integration;
                List<TaskIntegrationHeaderEntity> headers = request.getHeaders().stream()
                        .map(h -> TaskIntegrationHeaderEntity.builder()
                                .taskIntegration(finalIntegration)
                                .headerName(h.getKey())
                                .headerValue(h.getValue())
                                .build())
                        .collect(Collectors.toList());
                integration.setHeaders(headers);
            }

            // Add request config
            if (request.getRequest() != null) {
                TaskIntegrationRequestEntity requestEntity = TaskIntegrationRequestEntity.builder()
                        .taskIntegration(integration)
                        .bodyTemplate(request.getRequest().getBodyTemplate())
                        .build();
                integration.setRequest(requestEntity);
            }

            // Add response config
            if (request.getResponse() != null) {
                TaskIntegrationResponseEntity responseEntity = TaskIntegrationResponseEntity.builder()
                        .taskIntegration(integration)
                        .taskIdPath(request.getResponse().getTaskIdPath())
                        .taskTypePath(request.getResponse().getTaskTypePath())
                        .taskTitlePath(request.getResponse().getTaskTitlePath())
                        .taskDescriptionPath(request.getResponse().getTaskDescriptionPath())
                        .build();
                integration.setResponse(responseEntity);
            }

            // Save again with related entities
            integration = integrationRepository.save(integration);
        }

        // Add version mappings
        if (request.getVersionMappings() != null) {
            for (VersionMappingDto mapping : request.getVersionMappings()) {
                VersionMappingEntity entity = VersionMappingEntity.builder()
                        .taskIntegration(integration)
                        .fieldName(mapping.getFieldName())
                        .fieldValue(mapping.getFieldValue())
                        .versionType(mapping.getVersionType())
                        .build();
                versionMappingRepository.save(entity);
            }
        }

        log.info("Created task integration with ID: {}", integration.getId());
        return toResponse(integration);
    }

    /**
     * Update an existing integration
     */
    @Transactional
    public TaskIntegrationResponse updateIntegration(Long id, UpdateTaskIntegrationRequest request) {
        log.info("Updating task integration: {}", id);

        TaskIntegrationEntity integration = integrationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task integration not found: " + id));

        // Update basic fields
        if (request.getName() != null) {
            integration.setName(request.getName());
        }
        if (request.getEnabled() != null) {
            integration.setEnabled(request.getEnabled());
        }

        // Update type-specific fields
        if (integration.getType() == TaskIntegrationType.JIRA) {
            if (request.getJiraUrl() != null) integration.setJiraUrl(request.getJiraUrl());
            if (request.getJiraAuthType() != null) integration.setJiraAuthType(request.getJiraAuthType());
            if (request.getJiraUsername() != null) integration.setJiraUsername(request.getJiraUsername());
            // Encrypt token before storing
            if (request.getJiraApiToken() != null && !request.getJiraApiToken().isEmpty()) {
                String encrypted = encryptionService.encrypt(request.getJiraApiToken());
                integration.setJiraApiTokenEncrypted(encrypted);
                log.debug("JIRA API token encrypted successfully");
            }
        } else if (integration.getType() == TaskIntegrationType.GENERIC) {
            if (request.getApiUrl() != null) integration.setApiUrl(request.getApiUrl());
            if (request.getHttpMethod() != null) integration.setHttpMethod(request.getHttpMethod());

            // Update headers (replace all)
            if (request.getHeaders() != null) {
                integration.getHeaders().clear();
                for (HeaderDto h : request.getHeaders()) {
                    integration.getHeaders().add(TaskIntegrationHeaderEntity.builder()
                            .taskIntegration(integration)
                            .headerName(h.getKey())
                            .headerValue(h.getValue())
                            .build());
                }
            }

            // Update request config
            if (request.getRequest() != null && integration.getRequest() != null) {
                integration.getRequest().setBodyTemplate(request.getRequest().getBodyTemplate());
            }

            // Update response config
            if (request.getResponse() != null && integration.getResponse() != null) {
                TaskIntegrationResponseEntity response = integration.getResponse();
                if (request.getResponse().getTaskIdPath() != null)
                    response.setTaskIdPath(request.getResponse().getTaskIdPath());
                if (request.getResponse().getTaskTypePath() != null)
                    response.setTaskTypePath(request.getResponse().getTaskTypePath());
                if (request.getResponse().getTaskTitlePath() != null)
                    response.setTaskTitlePath(request.getResponse().getTaskTitlePath());
                if (request.getResponse().getTaskDescriptionPath() != null)
                    response.setTaskDescriptionPath(request.getResponse().getTaskDescriptionPath());
            }
        }

        integration = integrationRepository.save(integration);
        log.info("Updated task integration: {}", id);
        return toResponse(integration);
    }

    /**
     * Get integration by ID
     */
    public TaskIntegrationResponse getIntegration(Long id) {
        TaskIntegrationEntity integration = integrationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Task integration not found: " + id));
        return toResponse(integration);
    }

    /**
     * Get all integrations
     */
    public List<TaskIntegrationResponse> getAllIntegrations() {
        return integrationRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Delete integration
     */
    @Transactional
    public void deleteIntegration(Long id) {
        log.info("Deleting task integration: {}", id);

        if (!integrationRepository.existsById(id)) {
            throw new RuntimeException("Task integration not found: " + id);
        }

        // Delete version mappings
        versionMappingRepository.deleteByTaskIntegrationId(id);

        // Delete integration
        integrationRepository.deleteById(id);

        log.info("Deleted task integration: {}", id);
    }

    /**
     * Fetch tasks from external system
     */
    @Transactional
    public List<TaskEntity> fetchAndStoreTasks(Long integrationId, List<String> taskIds) {
        log.info("Fetching tasks from integration {} for IDs: {}", integrationId, taskIds);

        TaskIntegrationEntity integration = integrationRepository.findByIdWithDetails(integrationId)
                .orElseThrow(() -> new RuntimeException("Task integration not found: " + integrationId));

        if (!integration.getEnabled()) {
            throw new RuntimeException("Task integration is disabled: " + integration.getName());
        }

        // Find appropriate client
        TaskIntegrationClient client = integrationClients.stream()
                .filter(c -> c.supports(integration))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No client found for integration type: " + integration.getType()));

        // Fetch tasks
        List<TaskIntegrationClient.TaskData> taskDataList = client.fetchTasks(integration, taskIds);

        // Store in database
        List<TaskEntity> tasks = new ArrayList<>();
        for (TaskIntegrationClient.TaskData taskData : taskDataList) {
            // Check if task already exists
            TaskEntity task = taskRepository.findByExternalIdAndTaskIntegrationId(taskData.externalId(), integration.getId())
                    .orElse(TaskEntity.builder()
                            .externalId(taskData.externalId())
                            .taskIntegration(integration)
                            .build());

            // Update fields
            task.setExternalType(taskData.type());
            task.setTitle(taskData.title());
            task.setDescription(taskData.description());

            // Determine version type from mappings
            String versionType = determineVersionType(integration, taskData.type());
            task.setVersionType(versionType);

            task = taskRepository.save(task);
            tasks.add(task);
        }

        log.info("Fetched and stored {} tasks", tasks.size());
        return tasks;
    }

    /**
     * Determine version type based on task type and version mappings
     */
    private String determineVersionType(TaskIntegrationEntity integration, String taskType) {
        if (taskType == null) {
            return "MINOR"; // Default
        }

        // Look for exact match in version mappings
        List<VersionMappingEntity> mappings = versionMappingRepository.findByTaskIntegrationId(integration.getId());

        for (VersionMappingEntity mapping : mappings) {
            if (taskType.equalsIgnoreCase(mapping.getFieldValue())) {
                return mapping.getVersionType();
            }
        }

        // Default to MINOR if no mapping found
        return "MINOR";
    }

    private TaskIntegrationResponse toResponse(TaskIntegrationEntity entity) {
        TaskIntegrationResponse response = new TaskIntegrationResponse();
        response.setId(entity.getId());
        response.setType(entity.getType());
        response.setName(entity.getName());
        response.setEnabled(entity.getEnabled());
        response.setCreatedAt(entity.getCreatedAt());

        if (entity.getType() == TaskIntegrationType.JIRA) {
            response.setJiraUrl(entity.getJiraUrl());
            response.setJiraAuthType(entity.getJiraAuthType());
            response.setJiraUsername(entity.getJiraUsername());
            // Don't expose API token in response
        } else if (entity.getType() == TaskIntegrationType.GENERIC) {
            response.setApiUrl(entity.getApiUrl());
            response.setHttpMethod(entity.getHttpMethod());

            if (entity.getHeaders() != null) {
                response.setHeaders(entity.getHeaders().stream()
                        .map(h -> new HeaderDto(h.getHeaderName(), h.getHeaderValue()))
                        .collect(Collectors.toList()));
            }

            if (entity.getRequest() != null) {
                response.setRequest(new RequestConfigDto(entity.getRequest().getBodyTemplate()));
            }

            if (entity.getResponse() != null) {
                TaskIntegrationResponseEntity r = entity.getResponse();
                response.setResponse(new ResponseConfigDto(
                        r.getTaskIdPath(),
                        r.getTaskTypePath(),
                        r.getTaskTitlePath(),
                        r.getTaskDescriptionPath()
                ));
            }
        }

        // Add version mappings
        List<VersionMappingEntity> mappings = versionMappingRepository.findByTaskIntegrationId(entity.getId());
        response.setVersionMappings(mappings.stream()
                .map(m -> new VersionMappingDto(m.getFieldName(), m.getFieldValue(), m.getVersionType()))
                .collect(Collectors.toList()));

        return response;
    }
}
