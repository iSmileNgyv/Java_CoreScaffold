package com.ismile.core.chronovcs.service.repository;

import com.ismile.core.chronovcs.dto.handshake.HandshakePermissionDto;
import com.ismile.core.chronovcs.dto.handshake.HandshakeRepositoryDto;
import com.ismile.core.chronovcs.dto.handshake.HandshakeResponse;
import com.ismile.core.chronovcs.dto.handshake.HandshakeUserDto;
import com.ismile.core.chronovcs.dto.repository.CreateRepositoryRequestDto;
import com.ismile.core.chronovcs.dto.repository.CreateRepositoryResponseDto;
import com.ismile.core.chronovcs.dto.repository.RepositoryInfoDto;
import com.ismile.core.chronovcs.dto.repository.UpdateRepositoryInfoRequestDto;
import com.ismile.core.chronovcs.entity.*;
import com.ismile.core.chronovcs.exception.RepositoryNotFoundException;
import com.ismile.core.chronovcs.repository.BranchHeadRepository;
import com.ismile.core.chronovcs.repository.RepoPermissionRepository;
import com.ismile.core.chronovcs.repository.RepositoryRepository;
import com.ismile.core.chronovcs.repository.RepositorySettingsRepository;
import com.ismile.core.chronovcs.repository.UserRepository;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.permission.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RepositoryService {

    private final PermissionService permissionService;
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final RepositorySettingsRepository settingsRepository;
    private final RepoPermissionRepository repoPermissionRepository;
    private final BranchHeadRepository branchHeadRepository;

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

    public RepositoryInfoDto getRepositoryInfo(String repoKey) {
        RepositoryEntity repository = getByKeyOrThrow(repoKey);
        return mapRepositoryInfo(repository);
    }

    public List<RepositoryInfoDto> listRepositories(AuthenticatedUser user) {
        UserEntity owner = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + user.getUserId()));

        List<RepositoryEntity> ownedRepos = repositoryRepository.findAllByOwnerId(owner.getId());
        List<RepoPermissionEntity> permittedRepos = repoPermissionRepository.findAllByUserSettings(owner);

        Map<Long, RepositoryEntity> uniqueRepos = new LinkedHashMap<>();
        for (RepositoryEntity repository : ownedRepos) {
            uniqueRepos.put(repository.getId(), repository);
        }
        for (RepoPermissionEntity permission : permittedRepos) {
            RepositoryEntity repository = permission.getRepository();
            if (repository != null) {
                uniqueRepos.putIfAbsent(repository.getId(), repository);
            }
        }

        List<RepositoryInfoDto> result = new ArrayList<>();
        for (RepositoryEntity repository : uniqueRepos.values()) {
            boolean releaseEnabled = settingsRepository.findByRepositoryId(repository.getId())
                    .map(RepositorySettingsEntity::getReleaseEnabled)
                    .orElse(false);

            result.add(RepositoryInfoDto.builder()
                    .id(repository.getId())
                    .key(repository.getRepoKey())
                    .name(repository.getName())
                    .description(repository.getDescription())
                    .privateRepo(repository.isPrivateRepo())
                    .versioningMode(repository.getVersioningMode().name())
                    .defaultBranch(repository.getDefaultBranch())
                    .releaseEnabled(releaseEnabled)
                    .ownerUid(repository.getOwner().getUserUid())
                    .createdAt(repository.getCreatedAt())
                    .updatedAt(repository.getUpdatedAt())
                    .build());
        }

        return result;
    }

    @Transactional
    public RepositoryInfoDto updateRepositoryInfo(String repoKey, UpdateRepositoryInfoRequestDto request) {
        RepositoryEntity repository = getByKeyOrThrow(repoKey);

        if (request.getName() != null) {
            repository.setName(request.getName());
        }
        if (request.getDescription() != null) {
            repository.setDescription(request.getDescription());
        }
        if (request.getPrivateRepo() != null) {
            repository.setPrivateRepo(request.getPrivateRepo());
        }
        if (request.getVersioningMode() != null) {
            VersioningMode versioningMode = VersioningMode.valueOf(request.getVersioningMode().toUpperCase());
            repository.setVersioningMode(versioningMode);
        }
        if (request.getDefaultBranch() != null) {
            repository.setDefaultBranch(request.getDefaultBranch());
            branchHeadRepository.findByRepositoryAndBranch(repository, request.getDefaultBranch())
                    .orElseGet(() -> branchHeadRepository.save(BranchHeadEntity.builder()
                            .repository(repository)
                            .branch(request.getDefaultBranch())
                            .headCommitId(null)
                            .build()));
        }

        RepositoryEntity saved = repositoryRepository.save(repository);
        return mapRepositoryInfo(saved);
    }

    private RepositoryInfoDto mapRepositoryInfo(RepositoryEntity repository) {
        boolean releaseEnabled = settingsRepository.findByRepositoryId(repository.getId())
                .map(RepositorySettingsEntity::getReleaseEnabled)
                .orElse(false);
        return RepositoryInfoDto.builder()
                .id(repository.getId())
                .key(repository.getRepoKey())
                .name(repository.getName())
                .description(repository.getDescription())
                .privateRepo(repository.isPrivateRepo())
                .versioningMode(repository.getVersioningMode().name())
                .defaultBranch(repository.getDefaultBranch())
                .releaseEnabled(releaseEnabled)
                .ownerUid(repository.getOwner().getUserUid())
                .createdAt(repository.getCreatedAt())
                .updatedAt(repository.getUpdatedAt())
                .build();
    }

    @Transactional
    public CreateRepositoryResponseDto createRepository(AuthenticatedUser user, CreateRepositoryRequestDto request) {
        // Get the user entity
        UserEntity owner = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + user.getUserId()));

        // Generate repository key from name (slug)
        String repoKey = generateRepoKey(request.getName());

        // Parse versioning mode
        VersioningMode versioningMode = VersioningMode.valueOf(request.getVersioningMode().toUpperCase());

        // Create repository entity
        RepositoryEntity repository = RepositoryEntity.builder()
                .repoKey(repoKey)
                .name(request.getName())
                .description(request.getDescription())
                .privateRepo(request.getPrivateRepo() != null ? request.getPrivateRepo() : true)
                .versioningMode(versioningMode)
                .defaultBranch(request.getDefaultBranch() != null ? request.getDefaultBranch() : "main")
                .owner(owner)
                .storageType(StorageType.LOCAL)
                .build();

        // Save repository
        repository = repositoryRepository.save(repository);

        // Create repository settings
        RepositorySettingsEntity settings = RepositorySettingsEntity.builder()
                .repository(repository)
                .releaseEnabled(request.getReleaseEnabled() != null && request.getReleaseEnabled())
                .build();
        settingsRepository.save(settings);

        // Build response
        return CreateRepositoryResponseDto.builder()
                .id(repository.getId())
                .key(repository.getRepoKey())
                .name(repository.getName())
                .description(repository.getDescription())
                .privateRepo(repository.isPrivateRepo())
                .versioningMode(repository.getVersioningMode().name())
                .defaultBranch(repository.getDefaultBranch())
                .releaseEnabled(settings.getReleaseEnabled())
                .ownerUid(owner.getUserUid())
                .createdAt(repository.getCreatedAt())
                .build();
    }

    /**
     * Generate a repository key (slug) from the repository name.
     * Example: "My Test Repo" -> "my-test-repo"
     */
    private String generateRepoKey(String name) {
        return name.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}