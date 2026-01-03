package com.ismile.core.chronovcs.service.auth;

import com.ismile.core.chronovcs.dto.token.TokenPermissionDto;
import com.ismile.core.chronovcs.dto.token.TokenPermissionRequestDto;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.entity.TokenPermissionEntity;
import com.ismile.core.chronovcs.entity.UserTokenEntity;
import com.ismile.core.chronovcs.repository.RepositoryRepository;
import com.ismile.core.chronovcs.repository.TokenPermissionRepository;
import com.ismile.core.chronovcs.repository.UserTokenRepository;
import com.ismile.core.chronovcs.service.permission.PermissionService;
import com.ismile.core.chronovcs.entity.RepoPermissionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenPermissionService {

    private final UserTokenRepository userTokenRepository;
    private final TokenPermissionRepository tokenPermissionRepository;
    private final RepositoryRepository repositoryRepository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public List<TokenPermissionDto> listTokenPermissions(AuthenticatedUser user, Long tokenId) {
        UserTokenEntity token = getTokenForUser(user.getUserId(), tokenId);
        List<TokenPermissionEntity> entities = tokenPermissionRepository.findAllByTokenId(token.getId());
        List<TokenPermissionDto> result = new ArrayList<>();
        for (TokenPermissionEntity entity : entities) {
            result.add(toDto(entity));
        }
        return result;
    }

    @Transactional
    public TokenPermissionDto upsertTokenPermission(AuthenticatedUser user,
                                                    Long tokenId,
                                                    String repoKey,
                                                    TokenPermissionRequestDto request) {
        UserTokenEntity token = getTokenForUser(user.getUserId(), tokenId);

        RepoPermissionEntity userPerm = permissionService.resolvePermissionOrThrow(user, repoKey);
        validateSubset(request, userPerm);

        RepositoryEntity repository = repositoryRepository.findByRepoKey(repoKey)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoKey));

        TokenPermissionEntity entity = tokenPermissionRepository
                .findByTokenIdAndRepositoryId(token.getId(), repository.getId())
                .orElseGet(() -> TokenPermissionEntity.builder()
                        .token(token)
                        .repository(repository)
                        .build());

        entity.setCanRead(request.getCanRead());
        entity.setCanPull(request.getCanPull());
        entity.setCanPush(request.getCanPush());
        entity.setCanCreateBranch(request.getCanCreateBranch());
        entity.setCanDeleteBranch(request.getCanDeleteBranch());
        entity.setCanMerge(request.getCanMerge());
        entity.setCanCreateTag(request.getCanCreateTag());
        entity.setCanDeleteTag(request.getCanDeleteTag());
        entity.setCanManageRepo(request.getCanManageRepo());
        entity.setCanBypassTaskPolicy(request.getCanBypassTaskPolicy());

        TokenPermissionEntity saved = tokenPermissionRepository.save(entity);
        return toDto(saved);
    }

    @Transactional
    public void deleteTokenPermission(AuthenticatedUser user, Long tokenId, String repoKey) {
        UserTokenEntity token = getTokenForUser(user.getUserId(), tokenId);
        RepositoryEntity repository = repositoryRepository.findByRepoKey(repoKey)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoKey));

        tokenPermissionRepository.findByTokenIdAndRepositoryId(token.getId(), repository.getId())
                .ifPresent(tokenPermissionRepository::delete);
    }

    private UserTokenEntity getTokenForUser(Long userId, Long tokenId) {
        UserTokenEntity token = userTokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));
        if (!token.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Token not found");
        }
        return token;
    }

    private void validateSubset(TokenPermissionRequestDto request, RepoPermissionEntity userPerm) {
        if (Boolean.TRUE.equals(request.getCanRead()) && !userPerm.isCanRead()) {
            throw new IllegalArgumentException("Cannot grant canRead without permission");
        }
        if (Boolean.TRUE.equals(request.getCanPull()) && !userPerm.isCanPull()) {
            throw new IllegalArgumentException("Cannot grant canPull without permission");
        }
        if (Boolean.TRUE.equals(request.getCanPush()) && !userPerm.isCanPush()) {
            throw new IllegalArgumentException("Cannot grant canPush without permission");
        }
        if (Boolean.TRUE.equals(request.getCanCreateBranch()) && !userPerm.isCanCreateBranch()) {
            throw new IllegalArgumentException("Cannot grant canCreateBranch without permission");
        }
        if (Boolean.TRUE.equals(request.getCanDeleteBranch()) && !userPerm.isCanDeleteBranch()) {
            throw new IllegalArgumentException("Cannot grant canDeleteBranch without permission");
        }
        if (Boolean.TRUE.equals(request.getCanMerge()) && !userPerm.isCanMerge()) {
            throw new IllegalArgumentException("Cannot grant canMerge without permission");
        }
        if (Boolean.TRUE.equals(request.getCanCreateTag()) && !userPerm.isCanCreateTag()) {
            throw new IllegalArgumentException("Cannot grant canCreateTag without permission");
        }
        if (Boolean.TRUE.equals(request.getCanDeleteTag()) && !userPerm.isCanDeleteTag()) {
            throw new IllegalArgumentException("Cannot grant canDeleteTag without permission");
        }
        if (Boolean.TRUE.equals(request.getCanManageRepo()) && !userPerm.isCanManageRepo()) {
            throw new IllegalArgumentException("Cannot grant canManageRepo without permission");
        }
        if (Boolean.TRUE.equals(request.getCanBypassTaskPolicy()) && !userPerm.isCanBypassTaskPolicy()) {
            throw new IllegalArgumentException("Cannot grant canBypassTaskPolicy without permission");
        }
    }

    private TokenPermissionDto toDto(TokenPermissionEntity entity) {
        return new TokenPermissionDto(
                entity.getRepository().getRepoKey(),
                entity.isCanRead(),
                entity.isCanPull(),
                entity.isCanPush(),
                entity.isCanCreateBranch(),
                entity.isCanDeleteBranch(),
                entity.isCanMerge(),
                entity.isCanCreateTag(),
                entity.isCanDeleteTag(),
                entity.isCanManageRepo(),
                entity.isCanBypassTaskPolicy()
        );
    }
}
