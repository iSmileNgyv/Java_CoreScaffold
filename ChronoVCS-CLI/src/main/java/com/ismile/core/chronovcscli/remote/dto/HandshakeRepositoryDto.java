package com.ismile.core.chronovcscli.remote.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HandshakeRepositoryDto {

    private Long id;
    private String repoKey;
    private String name;
    private String description;
    private boolean privateRepo;
    private VersioningModeDto versioningMode;
    private String defaultBranch;
}