package com.ismile.core.chronovcs.dto.release;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseTaskDto {
    private String jiraIssueKey;
    private String jiraIssueType;
    private String versionType;
}
