package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.service.auth.AuthService;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import lombok.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/self")
    public ResponseEntity<SelfResponse> self(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        AuthenticatedUser user = authService.authenticate(authorizationHeader)
                .orElseThrow(() -> new com.ismile.core.chronovcs.exception.UnauthorizedException(
                        "Invalid or missing credentials"
                ));

        return ResponseEntity.ok(
                new SelfResponse(
                        user.getUserId(),
                        user.getUserUid(),
                        user.getEmail()
                )
        );
    }

    @Value
    public static class SelfResponse {
        Long userId;
        String userUid;
        String email;
    }
}