package com.ismile.core.chronovcs.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcs.entity.TaskIntegrationEntity;
import com.ismile.core.chronovcs.entity.TaskIntegrationType;
import com.ismile.core.chronovcs.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * JIRA API v3 integration client
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JiraIntegrationClient implements TaskIntegrationClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EncryptionService encryptionService;

    @Override
    public boolean supports(TaskIntegrationEntity integration) {
        return integration.getType() == TaskIntegrationType.JIRA;
    }

    @Override
    public List<TaskData> fetchTasks(TaskIntegrationEntity integration, List<String> taskIds) {
        log.info("Fetching tasks from JIRA: {} for task IDs: {}", integration.getJiraUrl(), taskIds);

        List<TaskData> tasks = new ArrayList<>();

        for (String taskId : taskIds) {
            try {
                TaskData task = fetchSingleTask(integration, taskId);
                tasks.add(task);
            } catch (Exception e) {
                log.error("Failed to fetch JIRA task: {}", taskId, e);
                // Continue with other tasks
            }
        }

        log.info("Successfully fetched {} tasks from JIRA", tasks.size());
        return tasks;
    }

    private TaskData fetchSingleTask(TaskIntegrationEntity integration, String taskId) {
        String url = String.format("%s/rest/api/3/issue/%s", integration.getJiraUrl(), taskId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Add authentication
        if ("BASIC".equals(integration.getJiraAuthType())) {
            // Decrypt token before using
            String decryptedToken = encryptionService.decrypt(integration.getJiraApiTokenEncrypted());
            String auth = integration.getJiraUsername() + ":" + decryptedToken;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.add("Authorization", "Basic " + encodedAuth);
            log.debug("Using BASIC authentication for JIRA");
        } else if ("BEARER".equals(integration.getJiraAuthType())) {
            // Decrypt token before using
            String decryptedToken = encryptionService.decrypt(integration.getJiraApiTokenEncrypted());
            headers.add("Authorization", "Bearer " + decryptedToken);
            log.debug("Using BEARER authentication for JIRA");
        }

        HttpEntity<String> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to fetch JIRA task: " + response.getStatusCode());
        }

        return parseJiraIssue(response.getBody());
    }

    private TaskData parseJiraIssue(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String key = root.path("key").asText();
            String issueType = root.path("fields").path("issuetype").path("name").asText();
            String summary = root.path("fields").path("summary").asText();
            String description = root.path("fields").path("description").asText(null);

            return new TaskData(key, issueType, summary, description);

        } catch (Exception e) {
            log.error("Failed to parse JIRA response", e);
            throw new RuntimeException("Failed to parse JIRA response: " + e.getMessage(), e);
        }
    }
}
