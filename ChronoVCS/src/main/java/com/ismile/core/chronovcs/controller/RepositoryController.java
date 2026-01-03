package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.clone.BatchObjectsRequestDto;
import com.ismile.core.chronovcs.dto.clone.BatchObjectsResponseDto;
import com.ismile.core.chronovcs.dto.clone.CommitHistoryResponseDto;
import com.ismile.core.chronovcs.dto.clone.RefsResponseDto;
import com.ismile.core.chronovcs.dto.handshake.HandshakeResponse;
import com.ismile.core.chronovcs.dto.push.CommitSnapshotDto;
import com.ismile.core.chronovcs.dto.repository.CreateRepositoryRequestDto;
import com.ismile.core.chronovcs.dto.repository.CreateRepositoryResponseDto;
import com.ismile.core.chronovcs.dto.repository.RepositoryInfoDto;
import com.ismile.core.chronovcs.dto.repository.RepositorySettingsResponseDto;
import com.ismile.core.chronovcs.dto.repository.UpdateRepositoryInfoRequestDto;
import com.ismile.core.chronovcs.dto.repository.UpdateRepositorySettingsRequestDto;
import com.ismile.core.chronovcs.dto.tree.TreeResponseDto;
import com.ismile.core.chronovcs.dto.handshake.HandshakePermissionDto;
import com.ismile.core.chronovcs.dto.token.CliTokenResponseDto;
import com.ismile.core.chronovcs.dto.token.CreateTokenRequest;
import com.ismile.core.chronovcs.dto.token.TokenResponse;
import com.ismile.core.chronovcs.dto.permission.EffectivePermissionResponseDto;
import com.ismile.core.chronovcs.dto.permission.RepoPermissionResponseDto;
import com.ismile.core.chronovcs.dto.permission.RepoPermissionUpdateRequestDto;
import com.ismile.core.chronovcs.entity.BlobEntity;
import com.ismile.core.chronovcs.entity.RepoPermissionEntity;
import com.ismile.core.chronovcs.entity.RepositoryEntity;
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.auth.TokenService;
import com.ismile.core.chronovcs.service.clone.CloneService;
import com.ismile.core.chronovcs.service.permission.PermissionService;
import com.ismile.core.chronovcs.service.permission.RepoPermissionService;
import com.ismile.core.chronovcs.service.repository.RepositoryService;
import com.ismile.core.chronovcs.service.repository.RepositorySettingsService;
import com.ismile.core.chronovcs.service.repository.RepositoryDeleteService;
import com.ismile.core.chronovcs.service.storage.BlobStorageService;
import com.ismile.core.chronovcs.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.ismile.core.chronovcs.repository.UserRepository;

import java.util.List;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
@Validated
public class RepositoryController {

    private final PermissionService permissionService;
    private final RepositoryService repositoryService;
    private final CloneService cloneService;
    private final RepositorySettingsService repositorySettingsService;
    private final RepositoryDeleteService repositoryDeleteService;
    private final BlobStorageService blobStorageService;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final RepoPermissionService repoPermissionService;

    @GetMapping("/{repoKey}/info")
    public ResponseEntity<RepositoryInfoDto> getRepoInfo(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey
    ) {
        permissionService.assertCanRead(user, repoKey);
        RepositoryInfoDto info = repositoryService.getRepositoryInfo(repoKey);
        return ResponseEntity.ok(info);
    }

    @GetMapping
    public ResponseEntity<List<RepositoryInfoDto>> listRepositories(
            @CurrentUser AuthenticatedUser user
    ) {
        List<RepositoryInfoDto> repositories = repositoryService.listRepositories(user);
        return ResponseEntity.ok(repositories);
    }

    @PostMapping
    public ResponseEntity<CreateRepositoryResponseDto> createRepository(
            @CurrentUser AuthenticatedUser user,
            @Valid @RequestBody CreateRepositoryRequestDto request
    ) {
        CreateRepositoryResponseDto response = repositoryService.createRepository(user, request);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{repoKey}/settings")
    public ResponseEntity<RepositorySettingsResponseDto> getSettings(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey
    ) {
        permissionService.assertCanManageRepo(user, repoKey);
        RepositorySettingsResponseDto response = repositorySettingsService.getSettings(repoKey);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{repoKey}/settings")
    public ResponseEntity<RepositorySettingsResponseDto> updateSettings(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @RequestBody UpdateRepositorySettingsRequestDto request
    ) {
        permissionService.assertCanManageRepo(user, repoKey);
        RepositorySettingsResponseDto response = repositorySettingsService.updateSettings(repoKey, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{repoKey}")
    public ResponseEntity<RepositoryInfoDto> updateRepositoryInfo(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey,
            @Valid @RequestBody UpdateRepositoryInfoRequestDto request
    ) {
        permissionService.assertCanManageRepo(user, repoKey);
        RepositoryInfoDto response = repositoryService.updateRepositoryInfo(repoKey, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{repoKey}")
    public ResponseEntity<Void> deleteRepository(
            @CurrentUser AuthenticatedUser user,
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid repository key") String repoKey
    ) {
        permissionService.assertCanManageRepo(user, repoKey);
        repositoryDeleteService.deleteRepository(repoKey);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{repoKey}/handshake")
    public ResponseEntity<HandshakeResponse> handshake(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey
    ) {
        HandshakeResponse response = repositoryService.handshake(user, repoKey);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{repoKey}/refs")
    public ResponseEntity<RefsResponseDto> getRefs(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey
    ) {
        permissionService.assertCanRead(user, repoKey);
        RefsResponseDto response = cloneService.getRefs(repoKey);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{repoKey}/tree")
    public ResponseEntity<TreeResponseDto> getTree(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey,
            @RequestParam(required = false) String ref,
            @RequestParam(required = false, defaultValue = "") String path
    ) {
        permissionService.assertCanRead(user, repoKey);
        TreeResponseDto response = cloneService.getTree(repoKey, ref, path);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{repoKey}/commits/{commitHash}")
    public ResponseEntity<CommitSnapshotDto> getCommit(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey,
            @PathVariable String commitHash
    ) {
        permissionService.assertCanRead(user, repoKey);
        CommitSnapshotDto response = cloneService.getCommit(repoKey, commitHash);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{repoKey}/commits")
    public ResponseEntity<CommitHistoryResponseDto> getCommitHistory(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey,
            @RequestParam(required = false, defaultValue = "main") String branch,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String fromCommit
    ) {
        permissionService.assertCanRead(user, repoKey);
        CommitHistoryResponseDto response = cloneService.getCommitHistory(repoKey, branch, limit, fromCommit);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{repoKey}/objects/batch")
    public ResponseEntity<BatchObjectsResponseDto> getBatchObjects(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey,
            @RequestBody BatchObjectsRequestDto request
    ) {
        permissionService.assertCanRead(user, repoKey);
        BatchObjectsResponseDto response = cloneService.getBatchObjects(repoKey, request.getHashes());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{repoKey}/tokens/cli")
    public ResponseEntity<CliTokenResponseDto> createCliToken(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey,
            @Valid @RequestBody CreateTokenRequest request
    ) {
        RepoPermissionEntity permission = permissionService.resolvePermissionOrThrow(user, repoKey);
        UserEntity userEntity = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        TokenResponse token = tokenService.createToken(userEntity, request);

        HandshakePermissionDto permissions = HandshakePermissionDto.builder()
                .canRead(permission.isCanRead())
                .canPull(permission.isCanPull())
                .canPush(permission.isCanPush())
                .canCreateBranch(permission.isCanCreateBranch())
                .canDeleteBranch(permission.isCanDeleteBranch())
                .canMerge(permission.isCanMerge())
                .canCreateTag(permission.isCanCreateTag())
                .canDeleteTag(permission.isCanDeleteTag())
                .canManageRepo(permission.isCanManageRepo())
                .canBypassTaskPolicy(permission.isCanBypassTaskPolicy())
                .build();

        CliTokenResponseDto response = new CliTokenResponseDto(repoKey, token, permissions);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{repoKey}/tokens/permissions")
    public ResponseEntity<HandshakePermissionDto> getTokenPermissions(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey
    ) {
        RepoPermissionEntity permission = permissionService.resolvePermissionOrThrow(user, repoKey);
        HandshakePermissionDto permissions = HandshakePermissionDto.builder()
                .canRead(permission.isCanRead())
                .canPull(permission.isCanPull())
                .canPush(permission.isCanPush())
                .canCreateBranch(permission.isCanCreateBranch())
                .canDeleteBranch(permission.isCanDeleteBranch())
                .canMerge(permission.isCanMerge())
                .canCreateTag(permission.isCanCreateTag())
                .canDeleteTag(permission.isCanDeleteTag())
                .canManageRepo(permission.isCanManageRepo())
                .canBypassTaskPolicy(permission.isCanBypassTaskPolicy())
                .build();
        return ResponseEntity.ok(permissions);
    }

    @GetMapping("/{repoKey}/permissions/effective")
    public ResponseEntity<EffectivePermissionResponseDto> getEffectivePermissions(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey
    ) {
        PermissionService.PermissionResolution resolution =
                permissionService.resolvePermissionWithSource(user, repoKey);

        RepoPermissionEntity permission = resolution.getPermission();
        HandshakePermissionDto permissions = HandshakePermissionDto.builder()
                .canRead(permission.isCanRead())
                .canPull(permission.isCanPull())
                .canPush(permission.isCanPush())
                .canCreateBranch(permission.isCanCreateBranch())
                .canDeleteBranch(permission.isCanDeleteBranch())
                .canMerge(permission.isCanMerge())
                .canCreateTag(permission.isCanCreateTag())
                .canDeleteTag(permission.isCanDeleteTag())
                .canManageRepo(permission.isCanManageRepo())
                .canBypassTaskPolicy(permission.isCanBypassTaskPolicy())
                .build();

        EffectivePermissionResponseDto response = new EffectivePermissionResponseDto(
                resolution.getSource(),
                resolution.getTokenId(),
                permissions
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{repoKey}/permissions")
    public ResponseEntity<List<RepoPermissionResponseDto>> listRepoPermissions(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey
    ) {
        permissionService.assertCanManageRepo(user, repoKey);
        List<RepoPermissionResponseDto> response = repoPermissionService.listPermissions(repoKey);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{repoKey}/permissions")
    public ResponseEntity<RepoPermissionResponseDto> upsertRepoPermission(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey,
            @Valid @RequestBody RepoPermissionUpdateRequestDto request
    ) {
        permissionService.assertCanManageRepo(user, repoKey);
        RepoPermissionResponseDto response = repoPermissionService.upsertPermission(repoKey, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{repoKey}/permissions")
    public ResponseEntity<Void> deleteRepoPermission(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String userUid
    ) {
        permissionService.assertCanManageRepo(user, repoKey);
        repoPermissionService.deletePermission(repoKey, userEmail, userUid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{repoKey}/blobs/{hash}")
    public ResponseEntity<byte[]> getBlob(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey,
            @PathVariable String hash
    ) {
        permissionService.assertCanRead(user, repoKey);

        RepositoryEntity repository = repositoryService.getByKeyOrThrow(repoKey);
        BlobEntity blob = blobStorageService.findByHash(repository, hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blob not found"));

        byte[] content = blobStorageService.loadContent(blob);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (blob.getContentType() != null && !blob.getContentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(blob.getContentType());
            } catch (IllegalArgumentException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().contentType(mediaType);
        if (blob.getContentSize() != null) {
            builder.contentLength(blob.getContentSize());
        }
        return builder.body(content);
    }
}
