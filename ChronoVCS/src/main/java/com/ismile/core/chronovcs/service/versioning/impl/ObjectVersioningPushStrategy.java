package com.ismile.core.chronovcs.service.versioning.impl;

import com.ismile.core.chronovcs.dto.push.PushRequestDto;
import com.ismile.core.chronovcs.dto.push.PushResultDto;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.entity.VersioningMode;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.versioning.VersioningPushStrategy;
import org.springframework.stereotype.Component;

@Component
public class ObjectVersioningPushStrategy implements VersioningPushStrategy {

    @Override
    public VersioningMode getSupportedMode() {
        return VersioningMode.OBJECT;
    }

    @Override
    public PushResultDto handlePush(
            AuthenticatedUser user,
            RepositoryEntity repository,
            PushRequestDto request
    ) {
        // Later: object-level history, task binding, etc.
        // For now we can either:
        //  - throw UnsupportedOperationException
        //  - or reuse PROJECT logic via composition
        throw new UnsupportedOperationException("OBJECT versioning mode is not implemented yet");
    }
}