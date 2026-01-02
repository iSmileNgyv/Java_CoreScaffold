package com.ismile.core.chronovcs.service.permission;

import com.ismile.core.chronovcs.entity.RepoPermissionEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.exception.PermissionDeniedException;
import com.ismile.core.chronovcs.exception.RepositoryNotFoundException;
import com.ismile.core.chronovcs.repository.RepoPermissionRepository;
import com.ismile.core.chronovcs.repository.RepositoryRepository;
import com.ismile.core.chronovcs.repository.UserRepository;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final RepoPermissionRepository repoPermissionRepository;

    /**
     * Generic helper: load repo, user, permission.
     * Repository owner automatically has full rights.
     */
    public RepoPermissionEntity resolvePermissionOrThrow(AuthenticatedUser authUser, String repoKey) {
        RepositoryEntity repo = repositoryRepository
                .findByRepoKey(repoKey)
                .orElseThrow(() -> new RepositoryNotFoundException(repoKey));

        UserEntity user = userRepository.findById(authUser.getUserId())
                .orElseThrow(() -> new PermissionDeniedException("User not found"));

        // Owner: full access shortcut
        if (repo.getOwner() != null && repo.getOwner().getId().equals(user.getId())) {
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

        return repoPermissionRepository
                .findByUserSettingsAndRepository(user, repo)
                .orElseThrow(() -> new PermissionDeniedException(
                        "No permissions configured for this user on repository: " + repoKey
                ));
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

    // İstəsən əlavə:
    // assertCanCreateBranch, assertCanDeleteBranch, assertCanBypassTaskPolicy və s.
}
