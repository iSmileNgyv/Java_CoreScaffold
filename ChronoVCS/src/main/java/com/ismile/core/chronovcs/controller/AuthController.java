package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.auth.LoginRequest;
import com.ismile.core.chronovcs.dto.auth.LoginResponse;
import com.ismile.core.chronovcs.dto.auth.RefreshTokenRequest;
import com.ismile.core.chronovcs.dto.auth.TokenPair;
import com.ismile.core.chronovcs.exception.UnauthorizedException;
import com.ismile.core.chronovcs.service.auth.AuthService;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.auth.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenService jwtTokenService;

    /**
     * CLI və ya web üçün hazırda authenticate olunmuş user.
     * (SecurityContext + ChronoAuthFilter vasitəsilə doldurulur.)
     */
    @GetMapping("/self")
    public ResponseEntity<AuthenticatedUser> self() {
        AuthenticatedUser user = authService.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
        return ResponseEntity.ok(user);
    }

    /**
     * Web / Next.js login:
     * Body: { "email": "...", "token": "..." }
     * Cavab: user + accessToken + refreshToken
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        AuthenticatedUser user = authService
                .authenticateByEmailAndToken(request.getEmail(), request.getToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or token"));

        TokenPair pair = jwtTokenService.generateTokens(user);

        return ResponseEntity.ok(
                new LoginResponse(user, pair.getAccessToken(), pair.getRefreshToken())
        );
    }

    /**
     * Refresh flow:
     * Body: { "refreshToken": "..." }
     * Cavab: yenilənmiş access + refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshTokenRequest request) {
        AuthenticatedUser user = jwtTokenService.parseRefreshToken(request.getRefreshToken());
        TokenPair pair = jwtTokenService.generateTokens(user);
        return ResponseEntity.ok(
                new LoginResponse(user, pair.getAccessToken(), pair.getRefreshToken())
        );
    }
}