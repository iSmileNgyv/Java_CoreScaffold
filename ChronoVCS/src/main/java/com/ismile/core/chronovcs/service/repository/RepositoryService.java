package com.ismile.core.chronovcs.service.repository;

import com.ismile.core.chronovcs.dto.handshake.HandshakePermissionDto;
import com.ismile.core.chronovcs.dto.handshake.HandshakeRepositoryDto;
import com.ismile.core.chronovcs.dto.handshake.HandshakeResponse;
import com.ismile.core.chronovcs.dto.handshake.HandshakeUserDto;
import com.ismile.core.chronovcs.entity.RepoPermissionEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.exception.RepositoryNotFoundException;
import com.ismile.core.chronovcs.repository.RepositoryRepository;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.permission.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RepositoryService {

    private final PermissionService permissionService;
    private final RepositoryRepository repositoryRepository;

    public HandshakeResponse handshake(AuthenticatedUser user, String repoKey) {

        // This will throw RepositoryNotFoundException or PermissionDeniedException
        RepoPermissionEntity perm = permissionService.resolvePermissionOrThrow(user, repoKey);
        RepositoryEntity repo = perm.getRepository();

        HandshakeUserDto userDto = HandshakeUserDto.builder()
                .id(user.getUserId())
                .userUid(user.getUserUid())
                .email(user.getEmail())
                .build();

        HandshakeRepositoryDto repoDto = HandshakeRepositoryDto.builder()
                .id(repo.getId())
                .repoKey(repo.getRepoKey())
                .name(repo.getName())
                .description(repo.getDescription())
                .privateRepo(repo.isPrivateRepo())
                .versioningMode(repo.getVersioningMode())
                .defaultBranch(repo.getDefaultBranch())
                .build();

        HandshakePermissionDto permDto = HandshakePermissionDto.builder()
                .canRead(perm.isCanRead())
                .canPull(perm.isCanPull())
                .canPush(perm.isCanPush())
                .canCreateBranch(perm.isCanCreateBranch())
                .canDeleteBranch(perm.isCanDeleteBranch())
                .canMerge(perm.isCanMerge())
                .canCreateTag(perm.isCanCreateTag())
                .canDeleteTag(perm.isCanDeleteTag())
                .canManageRepo(perm.isCanManageRepo())
                .canBypassTaskPolicy(perm.isCanBypassTaskPolicy())
                .build();

        return HandshakeResponse.builder()
                .success(true)
                .user(userDto)
                .repository(repoDto)
                .permissions(permDto)
                .build();
    }

    public RepositoryEntity getByKeyOrThrow(String repoKey) {
        return repositoryRepository.findByRepoKeyIgnoreCase(repoKey)
                .orElseThrow(() -> new RepositoryNotFoundException(repoKey));
    }
}