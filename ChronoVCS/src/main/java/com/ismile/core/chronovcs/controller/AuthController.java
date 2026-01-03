package com.ismile.core.chronovcs.controller;

import com.ismile.core.chronovcs.dto.auth.LoginRequest;
import com.ismile.core.chronovcs.dto.auth.LoginResponse;
import com.ismile.core.chronovcs.dto.auth.RefreshTokenRequest;
import com.ismile.core.chronovcs.dto.auth.RegisterRequest;
import com.ismile.core.chronovcs.dto.token.CreateTokenRequest;
import com.ismile.core.chronovcs.dto.token.TokenResponse;
import com.ismile.core.chronovcs.dto.token.TokenSummaryDto;
import com.ismile.core.chronovcs.dto.token.TokenPermissionDto;
import com.ismile.core.chronovcs.dto.token.TokenPermissionRequestDto;
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.repository.UserRepository;
import com.ismile.core.chronovcs.service.auth.AuthService;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.auth.TokenPermissionService;
import com.ismile.core.chronovcs.service.auth.TokenService;
import com.ismile.core.chronovcs.web.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final TokenPermissionService tokenPermissionService;
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
     * List all PAT tokens for the current user
     * URL: GET /api/auth/tokens
     * Auth: Bearer Token (JWT)
     */
    @GetMapping("/tokens")
    public ResponseEntity<List<TokenSummaryDto>> listTokens(
            @CurrentUser AuthenticatedUser authenticatedUser
    ) {
        List<TokenSummaryDto> tokens = tokenService.listTokens(authenticatedUser.getUserId());
        return ResponseEntity.ok(tokens);
    }

    /**
     * Revoke a PAT token
     * URL: DELETE /api/auth/tokens/{tokenId}
     * Auth: Bearer Token (JWT)
     */
    @DeleteMapping("/tokens/{tokenId}")
    public ResponseEntity<Void> revokeToken(
            @CurrentUser AuthenticatedUser authenticatedUser,
            @PathVariable Long tokenId
    ) {
        tokenService.revokeToken(authenticatedUser.getUserId(), tokenId);
        return ResponseEntity.noContent().build();
    }

    /**
     * List token permissions (repo-scoped) for a token
     * URL: GET /api/auth/tokens/{tokenId}/permissions
     * Auth: Bearer Token (JWT)
     */
    @GetMapping("/tokens/{tokenId}/permissions")
    public ResponseEntity<List<TokenPermissionDto>> listTokenPermissions(
            @CurrentUser AuthenticatedUser authenticatedUser,
            @PathVariable Long tokenId
    ) {
        List<TokenPermissionDto> permissions =
                tokenPermissionService.listTokenPermissions(authenticatedUser, tokenId);
        return ResponseEntity.ok(permissions);
    }

    /**
     * Create or update token permissions for a repo
     * URL: PUT /api/auth/tokens/{tokenId}/permissions/{repoKey}
     * Auth: Bearer Token (JWT)
     */
    @PutMapping("/tokens/{tokenId}/permissions/{repoKey}")
    public ResponseEntity<TokenPermissionDto> upsertTokenPermissions(
            @CurrentUser AuthenticatedUser authenticatedUser,
            @PathVariable Long tokenId,
            @PathVariable String repoKey,
            @Valid @RequestBody TokenPermissionRequestDto request
    ) {
        TokenPermissionDto response =
                tokenPermissionService.upsertTokenPermission(authenticatedUser, tokenId, repoKey, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Remove token permissions for a repo (fallback to user permissions)
     * URL: DELETE /api/auth/tokens/{tokenId}/permissions/{repoKey}
     * Auth: Bearer Token (JWT)
     */
    @DeleteMapping("/tokens/{tokenId}/permissions/{repoKey}")
    public ResponseEntity<Void> deleteTokenPermissions(
            @CurrentUser AuthenticatedUser authenticatedUser,
            @PathVariable Long tokenId,
            @PathVariable String repoKey
    ) {
        tokenPermissionService.deleteTokenPermission(authenticatedUser, tokenId, repoKey);
        return ResponseEntity.noContent().build();
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
