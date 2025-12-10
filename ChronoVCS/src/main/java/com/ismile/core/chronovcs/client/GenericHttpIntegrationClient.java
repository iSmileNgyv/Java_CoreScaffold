package com.ismile.core.chronovcs.client;

import com.ismile.core.chronovcs.entity.TaskIntegrationEntity;
import com.ismile.core.chronovcs.entity.TaskIntegrationHeaderEntity;
import com.ismile.core.chronovcs.entity.TaskIntegrationRequestEntity;
import com.ismile.core.chronovcs.entity.TaskIntegrationResponseEntity;
import com.ismile.core.chronovcs.entity.TaskIntegrationType;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generic HTTP client for custom task integrations using JSONPath
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenericHttpIntegrationClient implements TaskIntegrationClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public boolean supports(TaskIntegrationEntity integration) {
        return integration.getType() == TaskIntegrationType.GENERIC;
    }

    @Override
    public List<TaskData> fetchTasks(TaskIntegrationEntity integration, List<String> taskIds) {
        log.info("Fetching tasks from generic API: {} for task IDs: {}", integration.getApiUrl(), taskIds);

        try {
            // Prepare request
            String url = integration.getApiUrl();
            HttpMethod method = HttpMethod.valueOf(integration.getHttpMethod());
            HttpHeaders headers = buildHeaders(integration);
            String requestBody = buildRequestBody(integration, taskIds);

            // Execute request
            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, method, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to fetch tasks: HTTP {}", response.getStatusCode());
                throw new RuntimeException("Failed to fetch tasks: " + response.getStatusCode());
            }

            // Parse response using JSONPath
            return parseResponse(response.getBody(), integration.getResponse());

        } catch (Exception e) {
            log.error("Error fetching tasks from generic API", e);
            throw new RuntimeException("Failed to fetch tasks: " + e.getMessage(), e);
        }
    }

    private HttpHeaders buildHeaders(TaskIntegrationEntity integration) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (integration.getHeaders() != null) {
            for (TaskIntegrationHeaderEntity header : integration.getHeaders()) {
                headers.add(header.getHeaderName(), header.getHeaderValue());
            }
        }

        return headers;
    }

    private String buildRequestBody(TaskIntegrationEntity integration, List<String> taskIds) {
        TaskIntegrationRequestEntity requestConfig = integration.getRequest();

        if (requestConfig == null || requestConfig.getBodyTemplate() == null) {
            return null;
        }

        // Replace {taskIds} placeholder with actual task IDs
        String template = requestConfig.getBodyTemplate();

        // Convert task IDs to JSON array format
        String taskIdsJson = taskIds.stream()
                .map(id -> "\"" + id + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        return template.replace("{taskIds}", taskIdsJson);
    }

    private List<TaskData> parseResponse(String responseBody, TaskIntegrationResponseEntity responseConfig) {
        List<TaskData> tasks = new ArrayList<>();

        if (responseConfig == null) {
            log.warn("No response configuration found, cannot parse response");
            return tasks;
        }

        try {
            Object document = JsonPath.parse(responseBody).json();

            // Check if response is an array or single object
            List<Map<String, Object>> items;

            if (responseConfig.getTaskIdPath().contains("[*]")) {
                // Array response
                items = JsonPath.read(document, responseConfig.getTaskIdPath().replace("[*]", ""));
            } else {
                // Single object response - wrap in list
                Map<String, Object> singleItem = JsonPath.read(document, "$");
                items = List.of(singleItem);
            }

            // Parse each item
            for (Map<String, Object> item : items) {
                try {
                    String taskId = extractField(item, responseConfig.getTaskIdPath());
                    String taskType = extractField(item, responseConfig.getTaskTypePath());
                    String taskTitle = extractField(item, responseConfig.getTaskTitlePath());
                    String taskDescription = responseConfig.getTaskDescriptionPath() != null ?
                            extractField(item, responseConfig.getTaskDescriptionPath()) : null;

                    tasks.add(new TaskData(taskId, taskType, taskTitle, taskDescription));
                } catch (Exception e) {
                    log.warn("Failed to parse task item: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse response with JSONPath", e);
            throw new RuntimeException("Failed to parse API response: " + e.getMessage(), e);
        }

        log.info("Successfully parsed {} tasks from response", tasks.size());
        return tasks;
    }

    private String extractField(Map<String, Object> item, String jsonPath) {
        if (jsonPath == null) {
            return null;
        }

        // Remove array notation for individual item extraction
        String simplePath = jsonPath.replaceAll("\\[\\*\\]", "").replace("$.", "");

        Object value = item.get(simplePath);
        return value != null ? value.toString() : null;
    }
}
