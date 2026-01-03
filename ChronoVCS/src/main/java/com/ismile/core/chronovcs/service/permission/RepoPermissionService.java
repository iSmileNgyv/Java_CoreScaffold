package com.ismile.core.chronovcs.service.permission;

import com.ismile.core.chronovcs.dto.permission.RepoPermissionResponseDto;
import com.ismile.core.chronovcs.dto.permission.RepoPermissionUpdateRequestDto;
import com.ismile.core.chronovcs.entity.RepoPermissionEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.repository.RepoPermissionRepository;
import com.ismile.core.chronovcs.repository.UserRepository;
import com.ismile.core.chronovcs.service.repository.RepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RepoPermissionService {

    private final RepositoryService repositoryService;
    private final UserRepository userRepository;
    private final RepoPermissionRepository repoPermissionRepository;

    @Transactional(readOnly = true)
    public List<RepoPermissionResponseDto> listPermissions(String repoKey) {
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);
        Map<Long, RepoPermissionResponseDto> result = new LinkedHashMap<>();

        UserEntity owner = repository.getOwner();
        if (owner != null) {
            result.put(owner.getId(), ownerPermission(repository, owner));
        }

        List<RepoPermissionEntity> permissions = repoPermissionRepository.findAllByRepository(repository);
        for (RepoPermissionEntity permission : permissions) {
            UserEntity user = permission.getUserSettings();
            if (user == null) {
                continue;
            }
            result.put(user.getId(), mapPermission(repository, user, permission, user.equals(owner)));
        }

        return new ArrayList<>(result.values());
    }

    @Transactional
    public RepoPermissionResponseDto upsertPermission(String repoKey, RepoPermissionUpdateRequestDto request) {
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);
        UserEntity user = resolveUser(request);

        RepoPermissionEntity entity = repoPermissionRepository
                .findByUserSettingsAndRepository(user, repository)
                .orElseGet(() -> RepoPermissionEntity.builder()
                        .userSettings(user)
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

        RepoPermissionEntity saved = repoPermissionRepository.save(entity);
        boolean owner = repository.getOwner() != null && repository.getOwner().getId().equals(user.getId());
        return mapPermission(repository, user, saved, owner);
    }

    @Transactional
    public void deletePermission(String repoKey, String userEmail, String userUid) {
        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);
        UserEntity user = resolveUser(userEmail, userUid);

        repoPermissionRepository.findByUserSettingsAndRepository(user, repository)
                .ifPresent(repoPermissionRepository::delete);
    }

    private UserEntity resolveUser(RepoPermissionUpdateRequestDto request) {
        return resolveUser(request.getUserEmail(), request.getUserUid());
    }

    private UserEntity resolveUser(String userEmail, String userUid) {
        if (userEmail != null && !userEmail.isBlank()) {
            return userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
        }
        if (userUid != null && !userUid.isBlank()) {
            return userRepository.findByUserUid(userUid)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
        }
        throw new IllegalArgumentException("userEmail or userUid is required");
    }

    private RepoPermissionResponseDto ownerPermission(RepositoryEntity repository, UserEntity owner) {
        return new RepoPermissionResponseDto(
                repository.getRepoKey(),
                owner.getUserUid(),
                owner.getEmail(),
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true
        );
    }

    private RepoPermissionResponseDto mapPermission(RepositoryEntity repository,
                                                    UserEntity user,
                                                    RepoPermissionEntity permission,
                                                    boolean owner) {
        return new RepoPermissionResponseDto(
                repository.getRepoKey(),
                user.getUserUid(),
                user.getEmail(),
                owner,
                permission.isCanRead(),
                permission.isCanPull(),
                permission.isCanPush(),
                permission.isCanCreateBranch(),
                permission.isCanDeleteBranch(),
                permission.isCanMerge(),
                permission.isCanCreateTag(),
                permission.isCanDeleteTag(),
                permission.isCanManageRepo(),
                permission.isCanBypassTaskPolicy()
        );
    }
}
