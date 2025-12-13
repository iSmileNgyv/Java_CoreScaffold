package com.ismile.core.chronovcs.dto.integration;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FetchTasksRequest {

    @NotEmpty(message = "Task IDs list cannot be empty")
    @Size(max = 100, message = "Cannot fetch more than 100 tasks at once")
    private List<String> taskIds;
}
