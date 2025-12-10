package com.ismile.core.chronovcs.dto.release;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReleaseRequest {
    private String version;        // e.g., "1.0.0" or null for auto-calculate
    private String versionType;    // MAJOR, MINOR, PATCH (for auto-increment)
    private String message;
    private List<String> jiraIssueKeys; // e.g., ["PROJ-123", "PROJ-456"]
}
