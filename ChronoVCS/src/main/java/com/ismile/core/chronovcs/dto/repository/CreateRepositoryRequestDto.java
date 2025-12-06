package com.ismile.core.chronovcs.dto.repository;

import lombok.Data;

@Data
public class CreateRepositoryRequestDto {

    /**
     * Repository name (human-readable).
     */
    private String name;

    /**
     * Optional description.
     */
    private String description;

    /**
     * Versioning mode: "project" or "object".
     */
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
    private String defaultBranch = "main";
}
