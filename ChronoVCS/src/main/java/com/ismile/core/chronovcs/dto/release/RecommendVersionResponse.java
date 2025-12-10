package com.ismile.core.chronovcs.dto.release;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendVersionResponse {
    private String currentVersion;
    private String recommendedVersion;
    private String versionType;  // MAJOR, MINOR, PATCH
    private String reason;
    private Map<String, Long> breakdown; // {"MAJOR": 1, "MINOR": 2, "PATCH": 3}
}
