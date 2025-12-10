package com.ismile.core.chronovcs.dto.integration;

import lombok.Data;

import java.util.List;

@Data
public class FetchTasksRequest {
    private List<String> taskIds;
}
