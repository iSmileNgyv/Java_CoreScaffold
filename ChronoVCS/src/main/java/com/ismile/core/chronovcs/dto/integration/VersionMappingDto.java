package com.ismile.core.chronovcs.dto.integration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionMappingDto {
    private String fieldName;   // "issuetype", "priority", "WorkItemType"
    private String fieldValue;  // "Bug", "Story", "Epic"
    private String versionType; // "MAJOR", "MINOR", "PATCH"
}
