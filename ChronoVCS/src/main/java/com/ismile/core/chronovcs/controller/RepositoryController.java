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
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.clone.CloneService;
import com.ismile.core.chronovcs.service.permission.PermissionService;
import com.ismile.core.chronovcs.service.repository.RepositoryService;
import com.ismile.core.chronovcs.web.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class RepositoryController {

    private final PermissionService permissionService;
    private final RepositoryService repositoryService;
    private final CloneService cloneService;

    @GetMapping("/{repoKey}/info")
    public ResponseEntity<RepositoryInfoDto> getRepoInfo(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey
    ) {
        permissionService.assertCanRead(user, repoKey);
        RepositoryInfoDto info = repositoryService.getRepositoryInfo(repoKey);
        return ResponseEntity.ok(info);
    }

    @GetMapping
    public ResponseEntity<String> listRepositories(
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok("List of repositories for user: " + user.getUserUid());
    }

    @PostMapping
    public ResponseEntity<CreateRepositoryResponseDto> createRepository(
            @CurrentUser AuthenticatedUser user,
            @RequestBody CreateRepositoryRequestDto request
    ) {
        CreateRepositoryResponseDto response = repositoryService.createRepository(user, request);
        return ResponseEntity.status(201).body(response);
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
}