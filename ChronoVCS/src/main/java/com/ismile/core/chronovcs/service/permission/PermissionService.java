package com.ismile.core.chronovcs.service.permission;

import com.ismile.core.chronovcs.entity.RepoPermissionEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.entity.TokenPermissionEntity;
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.entity.UserTokenEntity;
import com.ismile.core.chronovcs.exception.PermissionDeniedException;
import com.ismile.core.chronovcs.exception.RepositoryNotFoundException;
import com.ismile.core.chronovcs.exception.TokenPermissionRequiredException;
import com.ismile.core.chronovcs.repository.RepoPermissionRepository;
import com.ismile.core.chronovcs.repository.RepositoryRepository;
import com.ismile.core.chronovcs.repository.TokenPermissionRepository;
import com.ismile.core.chronovcs.repository.UserRepository;
import com.ismile.core.chronovcs.repository.UserTokenRepository;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final RepoPermissionRepository repoPermissionRepository;
    private final UserTokenRepository userTokenRepository;
    private final TokenPermissionRepository tokenPermissionRepository;

    public static class PermissionResolution {
        private final RepoPermissionEntity permission;
        private final String source;
        private final Long tokenId;

        public PermissionResolution(RepoPermissionEntity permission, String source, Long tokenId) {
            this.permission = permission;
            this.source = source;
            this.tokenId = tokenId;
        }

        public RepoPermissionEntity getPermission() {
            return permission;
        }

        public String getSource() {
            return source;
        }

        public Long getTokenId() {
            return tokenId;
        }
    }

    /**
     * Generic helper: load repo, user, permission.
     * Repository owner automatically has full rights.
     */
    public RepoPermissionEntity resolvePermissionOrThrow(AuthenticatedUser authUser, String repoKey) {
        PermissionResolution resolution = resolvePermissionWithSource(authUser, repoKey);
        return resolution.getPermission();
    }

    public PermissionResolution resolvePermissionWithSource(AuthenticatedUser authUser, String repoKey) {
        RepositoryEntity repo = repositoryRepository
                .findByRepoKey(repoKey)
                .orElseThrow(() -> new RepositoryNotFoundException(repoKey));

        UserEntity user = userRepository.findById(authUser.getUserId())
                .orElseThrow(() -> new PermissionDeniedException("User not found"));

        if (authUser.getTokenId() != null) {
            UserTokenEntity token = userTokenRepository.findById(authUser.getTokenId())
                    .filter(t -> t.getUser().getId().equals(user.getId()))
                    .orElseThrow(() -> new PermissionDeniedException("Invalid token"));

            Optional<TokenPermissionEntity> tokenPerm = tokenPermissionRepository
                    .findByTokenIdAndRepositoryId(token.getId(), repo.getId());
            if (tokenPerm.isPresent()) {
                return new PermissionResolution(
                        fromTokenPermission(user, repo, tokenPerm.get()),
                        "TOKEN_OVERRIDE",
                        token.getId()
                );
            }

            if (repo.getOwner() != null && repo.getOwner().getId().equals(user.getId())) {
                return new PermissionResolution(fullAccess(user, repo), "OWNER", token.getId());
            }

            throw new TokenPermissionRequiredException(
                    "Token permissions required for repository: " + repoKey
            );
        }

        if (repo.getOwner() != null && repo.getOwner().getId().equals(user.getId())) {
            return new PermissionResolution(fullAccess(user, repo), "OWNER", authUser.getTokenId());
        }

        RepoPermissionEntity perm = repoPermissionRepository
                .findByUserSettingsAndRepository(user, repo)
                .orElseThrow(() -> new PermissionDeniedException(
                        "No permissions configured for this user on repository: " + repoKey
                ));

        return new PermissionResolution(perm, "USER_REPO", authUser.getTokenId());
    }

    // ---- Assert methods (throw exception if not allowed) ----

    public void assertCanRead(AuthenticatedUser authUser, String repoKey) {
        RepoPermissionEntity perm = resolvePermissionOrThrow(authUser, repoKey);
        if (!perm.isCanRead()) {
            throw new PermissionDeniedException("Read access denied for repository: " + repoKey);
        }
    }

    public void assertCanPull(AuthenticatedUser authUser, String repoKey) {
        RepoPermissionEntity perm = resolvePermissionOrThrow(authUser, repoKey);
        if (!perm.isCanPull()) {
            throw new PermissionDeniedException("Pull access denied for repository: " + repoKey);
        }
    }

    public void assertCanPush(AuthenticatedUser authUser, String repoKey) {
        RepoPermissionEntity perm = resolvePermissionOrThrow(authUser, repoKey);
        if (!perm.isCanPush()) {
            throw new PermissionDeniedException("Push access denied for repository: " + repoKey);
        }
    }

    public void assertCanMerge(AuthenticatedUser authUser, String repoKey) {
        RepoPermissionEntity perm = resolvePermissionOrThrow(authUser, repoKey);
        if (!perm.isCanMerge()) {
            throw new PermissionDeniedException("Merge access denied for repository: " + repoKey);
        }
    }

    public void assertCanManageRepo(AuthenticatedUser authUser, String repoKey) {
        RepoPermissionEntity perm = resolvePermissionOrThrow(authUser, repoKey);
        if (!perm.isCanManageRepo()) {
            throw new PermissionDeniedException("Manage access denied for repository: " + repoKey);
        }
    }

    private RepoPermissionEntity fullAccess(UserEntity user, RepositoryEntity repo) {
        return RepoPermissionEntity.builder()
                .userSettings(user)
                .repository(repo)
                .canRead(true)
                .canPull(true)
                .canPush(true)
                .canCreateBranch(true)
                .canDeleteBranch(true)
                .canMerge(true)
                .canCreateTag(true)
                .canDeleteTag(true)
                .canManageRepo(true)
                .canBypassTaskPolicy(true)
                .build();
    }

    private RepoPermissionEntity fromTokenPermission(UserEntity user,
                                                     RepositoryEntity repo,
                                                     TokenPermissionEntity tokenPermission) {
        return RepoPermissionEntity.builder()
                .userSettings(user)
                .repository(repo)
                .canRead(tokenPermission.isCanRead())
                .canPull(tokenPermission.isCanPull())
                .canPush(tokenPermission.isCanPush())
                .canCreateBranch(tokenPermission.isCanCreateBranch())
                .canDeleteBranch(tokenPermission.isCanDeleteBranch())
                .canMerge(tokenPermission.isCanMerge())
                .canCreateTag(tokenPermission.isCanCreateTag())
                .canDeleteTag(tokenPermission.isCanDeleteTag())
                .canManageRepo(tokenPermission.isCanManageRepo())
                .canBypassTaskPolicy(tokenPermission.isCanBypassTaskPolicy())
                .build();
    }

    // İstəsən əlavə:
    // assertCanCreateBranch, assertCanDeleteBranch, assertCanBypassTaskPolicy və s.
}
