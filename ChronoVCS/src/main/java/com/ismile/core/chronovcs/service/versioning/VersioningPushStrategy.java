package com.ismile.core.chronovcs.service.versioning;

import com.ismile.core.chronovcs.dto.push.PushRequestDto;
import com.ismile.core.chronovcs.dto.push.PushResultDto;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.entity.VersioningMode;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;

public interface VersioningPushStrategy {
    VersioningMode getSupportedMode();
    PushResultDto handlePush(
            AuthenticatedUser user,
            RepositoryEntity repository,
            PushRequestDto request
    );
}
