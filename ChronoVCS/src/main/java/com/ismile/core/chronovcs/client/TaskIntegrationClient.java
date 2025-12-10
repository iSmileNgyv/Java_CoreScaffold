package com.ismile.core.chronovcs.client;

import com.ismile.core.chronovcs.entity.TaskIntegrationEntity;

import java.util.List;

/**
 * Interface for fetching tasks from external task management systems
 */
public interface TaskIntegrationClient {

    /**
     * Fetch tasks by their external IDs
     *
     * @param integration Task integration configuration
     * @param taskIds List of external task IDs (e.g., JIRA-123, WI-456)
     * @return List of task data objects
     */
    List<TaskData> fetchTasks(TaskIntegrationEntity integration, List<String> taskIds);

    /**
     * Check if this client supports the given integration type
     *
     * @param integration Task integration configuration
     * @return true if this client can handle the integration
     */
    boolean supports(TaskIntegrationEntity integration);

    /**
     * Task data returned from external systems
     */
    record TaskData(
            String externalId,
            String type,
            String title,
            String description
    ) {}
}
