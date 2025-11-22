package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.handshake.HandshakeResponse;
import com.ismile.core.chronovcs.exception.UnauthorizedException;
import com.ismile.core.chronovcs.service.auth.AuthService;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.permission.PermissionService;
import com.ismile.core.chronovcs.service.repository.RepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class RepositoryController {
    private final AuthService authService;
    private final PermissionService permissionService;
    private final RepositoryService repositoryService;

    @GetMapping("/{repoKey}/info")
    public ResponseEntity<String> getRepoInfo(
            @PathVariable String repoKey,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        AuthenticatedUser user = authService.authenticate(authorizationHeader)
                .orElseThrow(() -> new UnauthorizedException("Invalid or missing credentials"));

        permissionService.assertCanRead(user, repoKey);

        return ResponseEntity.ok("Repo info for: " + repoKey + " (user: " + user.getUserUid() + ")");
    }

    @PostMapping("/{repoKey}/handshake")
    public ResponseEntity<HandshakeResponse> handshake(
            @PathVariable String repoKey,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        AuthenticatedUser user = authService.authenticate(authorizationHeader).orElseThrow(
                () -> new UnauthorizedException("Invalid or missing credentials")
        );
        HandshakeResponse response = repositoryService.handshake(user, repoKey);

        return ResponseEntity.ok(response);
    }
}