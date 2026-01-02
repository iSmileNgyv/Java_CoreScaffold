package com.ismile.core.chronovcs.dto.repository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRepositoryRequestDto {

    /**
     * Repository name (human-readable).
     */
    @NotBlank(message = "Repository name is required")
    @Size(min = 3, max = 100, message = "Repository name must be between 3 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Repository name can only contain letters, numbers, underscores and hyphens")
    private String name;

    /**
     * Optional description.
     */
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /**
     * Versioning mode: "project" or "object".
     */
    @Pattern(regexp = "^(project|object)$", message = "Versioning mode must be 'project' or 'object'")
    private String versioningMode;

    /**
     * Whether the repository is private.
     * Default: true
     */
    private Boolean privateRepo = true;

    /**
     * Default branch name.
     * Default: "main"
     */
    @Size(min = 1, max = 50, message = "Branch name must be between 1 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_/-]+$", message = "Branch name can only contain letters, numbers, underscores, hyphens and slashes")
    private String defaultBranch = "main";

    /**
     * Whether release mode is enabled for this repository.
     * Default: false
     */
    private Boolean releaseEnabled = false;
}
