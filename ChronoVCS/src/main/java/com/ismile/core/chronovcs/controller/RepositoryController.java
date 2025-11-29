package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.handshake.HandshakeResponse;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
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

    @GetMapping("/{repoKey}/info")
    public ResponseEntity<String> getRepoInfo(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey
    ) {
        permissionService.assertCanRead(user, repoKey);
        return ResponseEntity.ok(
                "Repo info for: " + repoKey + " (user: " + user.getUserUid() + ")"
        );
    }

    @GetMapping
    public ResponseEntity<String> listRepositories(
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok("List of repositories for user: " + user.getUserUid());
    }

    @PostMapping("/{repoKey}/handshake")
    public ResponseEntity<HandshakeResponse> handshake(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String repoKey
    ) {
        HandshakeResponse response = repositoryService.handshake(user, repoKey);
        return ResponseEntity.ok(response);
    }
}