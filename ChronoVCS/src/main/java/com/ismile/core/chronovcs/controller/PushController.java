package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.push.PushRequestDto;
import com.ismile.core.chronovcs.dto.push.PushResultDto;
import com.ismile.core.chronovcs.service.auth.AuthService;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.versioning.PushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class PushController {

    private final AuthService authService;
    private final PushService pushService;

    @PostMapping("/{repoKey}/push")
    public ResponseEntity<PushResultDto> push(
            @PathVariable String repoKey,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @RequestBody PushRequestDto request
    ) {
        AuthenticatedUser user = authService
                .authenticate(authHeader)
                .orElseThrow(() -> new RuntimeException("UNAUTHORIZED"));

        PushResultDto result = pushService.push(user, repoKey, request);
        return ResponseEntity.ok(result);
    }
}