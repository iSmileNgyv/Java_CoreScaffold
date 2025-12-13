package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.integration.*;
import com.ismile.core.chronovcs.entity.TaskEntity;
import com.ismile.core.chronovcs.service.integration.TaskIntegrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing task integrations
 */
@RestController
@RequestMapping("/api/v1/task-integrations")
@RequiredArgsConstructor
@Slf4j
@Validated
public class TaskIntegrationController {

    private final TaskIntegrationService taskIntegrationService;

    /**
     * Create a new task integration
     * POST /api/v1/task-integrations
     */
    @PostMapping
    public ResponseEntity<TaskIntegrationResponse> createIntegration(
            @Valid @RequestBody CreateTaskIntegrationRequest request) {
        log.info("Creating task integration: {}", request.getName());
        TaskIntegrationResponse response = taskIntegrationService.createIntegration(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all task integrations
     * GET /api/v1/task-integrations
     */
    @GetMapping
    public ResponseEntity<List<TaskIntegrationResponse>> getAllIntegrations() {
        log.info("Getting all task integrations");
        List<TaskIntegrationResponse> integrations = taskIntegrationService.getAllIntegrations();
        return ResponseEntity.ok(integrations);
    }

    /**
     * Get a specific task integration
     * GET /api/v1/task-integrations/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskIntegrationResponse> getIntegration(@PathVariable Long id) {
        log.info("Getting task integration: {}", id);
        TaskIntegrationResponse integration = taskIntegrationService.getIntegration(id);
        return ResponseEntity.ok(integration);
    }

    /**
     * Update a task integration
     * PUT /api/v1/task-integrations/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<TaskIntegrationResponse> updateIntegration(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody UpdateTaskIntegrationRequest request) {
        log.info("Updating task integration: {}", id);
        TaskIntegrationResponse response = taskIntegrationService.updateIntegration(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a task integration
     * DELETE /api/v1/task-integrations/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIntegration(@PathVariable Long id) {
        log.info("Deleting task integration: {}", id);
        taskIntegrationService.deleteIntegration(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Fetch tasks from external system and store them
     * POST /api/v1/task-integrations/{id}/fetch-tasks
     */
    @PostMapping("/{id}/fetch-tasks")
    public ResponseEntity<List<TaskEntity>> fetchTasks(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody FetchTasksRequest request) {
        log.info("Fetching tasks from integration {}: {}", id, request.getTaskIds());
        List<TaskEntity> tasks = taskIntegrationService.fetchAndStoreTasks(id, request.getTaskIds());
        return ResponseEntity.ok(tasks);
    }
}
