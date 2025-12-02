package com.ismile.core.chronovcs.dto.clone;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefsResponseDto {
    /**
     * Default branch name (e.g., "main")
     */
    private String defaultBranch;

    /**
     * Map of branch name -> HEAD commit hash
     */
    private Map<String, String> branches;
}
