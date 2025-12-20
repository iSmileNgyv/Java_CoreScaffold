package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.auth.LoginRequest;
import com.ismile.core.chronovcs.dto.auth.LoginResponse;
import com.ismile.core.chronovcs.dto.auth.RefreshTokenRequest;
import com.ismile.core.chronovcs.dto.auth.RegisterRequest;
import com.ismile.core.chronovcs.dto.token.CreateTokenRequest;
import com.ismile.core.chronovcs.dto.token.TokenResponse;
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.repository.UserRepository;
import com.ismile.core.chronovcs.service.auth.AuthService;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.auth.TokenService;
import com.ismile.core.chronovcs.web.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    /**
     * Register Endpoint
     * URL: POST /api/auth/register
     * Body: { "email": "...", "password": "...", "displayName": "..." }
     */
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /**
     * WEB Login Endpoint
     * URL: POST /api/auth/login
     * Body: { "email": "...", "password": "..." }
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * CLI & Web User Info Endpoint
     * URL: GET /api/auth/self
     * Auth: Basic Auth (CLI) və ya Bearer Token (Web)
     */
    @GetMapping("/self")
    public ResponseEntity<AuthenticatedUser> self(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(user);
    }

    /**
     * PAT Creation Endpoint (Web Only)
     * URL: POST /api/auth/tokens
     * Auth: Bearer Token (JWT)
     * Body: { "tokenName": "MacBook", "expiresInDays": 30 }
     */
    @PostMapping("/tokens")
    public ResponseEntity<TokenResponse> createToken(
            @CurrentUser AuthenticatedUser authenticatedUser,
            @RequestBody CreateTokenRequest request) {

        // Token yaratmaq üçün UserEntity lazımdır (əlaqə qurmaq üçün).
        // AuthenticatedUser-dəki ID ilə birbaşa axtarış edirik (daha sürətli və dəqiqdir).
        UserEntity user = userRepository.findById(authenticatedUser.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        TokenResponse response = tokenService.createToken(user, request);

        // Bu response-da "rawToken" olacaq. Frontend onu userə göstərməlidir.
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh Token Flow
     * URL: POST /api/auth/refresh
     * Body: { "refreshToken": "..." }
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshTokenRequest request) {
        // Artıq birbaşa AuthService-in refresh metodunu çağırırıq
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }
}