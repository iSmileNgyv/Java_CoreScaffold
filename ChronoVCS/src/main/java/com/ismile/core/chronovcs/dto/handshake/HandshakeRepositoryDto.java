package com.ismile.core.chronovcs.dto.handshake;

import com.ismile.core.chronovcs.entity.VersioningMode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HandshakeRepositoryDto {
    Long id;
    String repoKey;
    String name;
    String description;
    boolean privateRepo;
    VersioningMode versioningMode;
    String defaultBranch;
}
