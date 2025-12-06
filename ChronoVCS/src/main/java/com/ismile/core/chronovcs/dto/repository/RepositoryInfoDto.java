package com.ismile.core.chronovcs.dto.repository;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RepositoryInfoDto {
    private Long id;
    private String key;
    private String name;
    private String description;
    private boolean privateRepo;
    private String versioningMode;
    private String defaultBranch;
    private String ownerUid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
