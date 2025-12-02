package com.ismile.core.chronovcs.service.versioning;

import com.ismile.core.chronovcs.dto.push.PushRequestDto;
import com.ismile.core.chronovcs.dto.push.PushResultDto;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.permission.PermissionService;
import com.ismile.core.chronovcs.service.repository.RepositoryService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PushService {

    private final RepositoryService repositoryService;
    private final PermissionService permissionService;
    private final VersioningPushStrategyRegistry strategyRegistry;

    @Transactional
    public PushResultDto push(
            AuthenticatedUser user,
            String repoKey,
            PushRequestDto request
    ) {
        // 1) Resolve repo
        RepositoryEntity repo = repositoryService
                .getByKeyOrThrow(repoKey);

        // 2) Permission check
        permissionService.assertCanPush(user, repo.getRepoKey());

        // 3) Resolve strategy by versioning_mode
        VersioningPushStrategy strategy =
                strategyRegistry.getStrategy(repo.getVersioningMode());

        // 4) Delegate to strategy
        return strategy.handlePush(user, repo, request);
    }
}