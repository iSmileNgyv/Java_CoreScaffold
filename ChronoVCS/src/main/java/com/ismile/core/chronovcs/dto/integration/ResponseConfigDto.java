package com.ismile.core.chronovcs.dto.integration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseConfigDto {
    private String taskIdPath;
    private String taskTypePath;
    private String taskTitlePath;
    private String taskDescriptionPath;
}
