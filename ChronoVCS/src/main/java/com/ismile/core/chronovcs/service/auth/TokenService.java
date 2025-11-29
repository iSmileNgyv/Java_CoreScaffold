package com.ismile.core.chronovcs.service.auth;

import com.ismile.core.chronovcs.dto.token.CreateTokenRequest;
import com.ismile.core.chronovcs.dto.token.TokenResponse;
import com.ismile.core.chronovcs.entity.AuthLogEntity;
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.entity.UserTokenEntity;
import com.ismile.core.chronovcs.repository.AuthLogRepository;
import com.ismile.core.chronovcs.repository.UserTokenRepository;
import com.ismile.core.chronovcs.security.provider.PatTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final UserTokenRepository userTokenRepository;
    private final AuthLogRepository authLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final PatTokenProvider patTokenProvider;

    /**
     * Create new Personal Access Token
     * Bu method JWT-authenticated user tərəfindən çağrılır (Next.js-dən)
     */
    @Transactional
    public TokenResponse createToken(UserEntity user, CreateTokenRequest request, String ipAddress) {
        // Validate token name unique for user
        if (userTokenRepository.existsByUserAndTokenName(user, request.getTokenName())) {
            throw new IllegalArgumentException("Token name already exists: " + request.getTokenName());
        }

        // Generate token
        PatTokenProvider.TokenWithPrefix tokenData = patTokenProvider.generateToken();

        // Calculate expiry
        LocalDateTime expiresAt = null;
        if (request.getExpiresInDays() != null && request.getExpiresInDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(request.getExpiresInDays());
        }

        // Hash token before storing
        String tokenHash = passwordEncoder.encode(tokenData.getFullToken());

        // Create entity
        UserTokenEntity tokenEntity = UserTokenEntity.builder()
                .user(user)
                .tokenName(request.getTokenName())
                .tokenHash(tokenHash)
                .tokenPrefix(tokenData.getPrefix())
                .scopes(request.getScopes())
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        tokenEntity = userTokenRepository.save(tokenEntity);

        // Log creation
        authLogRepository.save(
                AuthLogEntity.tokenCreated(user, request.getTokenName(), ipAddress)
        );

        log.info("Personal access token created: {} for user: {}", request.getTokenName(), user.getEmail());

        // Return response with FULL token (only time user will see it)
        return TokenResponse.builder()
                .id(tokenEntity.getId())
                .tokenName(tokenEntity.getTokenName())
                .token(tokenData.getFullToken()) // ⚠️ CRITICAL: Only returned here!
                .tokenPrefix(tokenEntity.getTokenPrefix())
                .scopes(tokenEntity.getScopes())
                .expiresAt(tokenEntity.getExpiresAt())
                .lastUsedAt(tokenEntity.getLastUsedAt())
                .lastUsedIp(tokenEntity.getLastUsedIp())
                .revoked(tokenEntity.isRevoked())
                .createdAt(tokenEntity.getCreatedAt())
                .build();
    }

    /**
     * List user's tokens (without revealing actual token)
     */
    @Transactional(readOnly = true)
    public List<TokenResponse> listTokens(UserEntity user, boolean activeOnly) {
        List<UserTokenEntity> tokens;

        if (activeOnly) {
            tokens = userTokenRepository.findActiveTokensByUser(user, LocalDateTime.now());
        } else {
            tokens = userTokenRepository.findByUserOrderByCreatedAtDesc(user);
        }

        return tokens.stream()
                .map(this::toTokenResponse)
                .collect(Collectors.toList());
    }

    /**
     * Revoke token
     */
    @Transactional
    public void revokeToken(UserEntity user, Long tokenId) {
        UserTokenEntity token = userTokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));

        // Validate ownership
        if (!token.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized to revoke this token");
        }

        token.setRevoked(true);
        userTokenRepository.save(token);

        log.info("Token revoked: {} by user: {}", token.getTokenName(), user.getEmail());
    }

    /**
     * Update token name
     */
    @Transactional
    public TokenResponse updateTokenName(UserEntity user, Long tokenId, String newName) {
        UserTokenEntity token = userTokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));

        // Validate ownership
        if (!token.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized to update this token");
        }

        // Check if new name already exists
        if (userTokenRepository.existsByUserAndTokenName(user, newName)) {
            throw new IllegalArgumentException("Token name already exists: " + newName);
        }

        token.setTokenName(newName);
        token = userTokenRepository.save(token);

        log.info("Token renamed to: {} by user: {}", newName, user.getEmail());

        return toTokenResponse(token);
    }

    /**
     * Authenticate using Personal Access Token (for CLI)
     * Bu method Basic Auth filter-də çağrılır
     */
    @Transactional
    public UserEntity authenticateWithToken(String email, String token, String ipAddress) {
        // Validate token format
        if (!patTokenProvider.isValidFormat(token)) {
            throw new IllegalArgumentException("Invalid token format");
        }

        // Extract token prefix for fast lookup
        String tokenPrefix = patTokenProvider.extractPrefix(token);

        // Find candidate tokens by prefix (not revoked)
        List<UserTokenEntity> candidateTokens = userTokenRepository
                .findByTokenPrefixAndRevokedFalse(tokenPrefix);

        // Try to match token with any user having this email
        for (UserTokenEntity tokenEntity : candidateTokens) {
            UserEntity user = tokenEntity.getUser();

            // Check email match
            if (!user.getEmail().equalsIgnoreCase(email)) {
                continue;
            }

            // Check if user is active
            if (!user.isActive()) {
                continue;
            }

            // Check if token is valid (not expired)
            if (!tokenEntity.isValid()) {
                continue;
            }

            // Verify token hash
            if (passwordEncoder.matches(token, tokenEntity.getTokenHash())) {
                // Update last used info
                tokenEntity.setLastUsedAt(LocalDateTime.now());
                tokenEntity.setLastUsedIp(ipAddress);
                userTokenRepository.save(tokenEntity);

                log.info("CLI authenticated with token: {} for user: {}",
                        tokenEntity.getTokenName(), user.getEmail());
                return user;
            }
        }

        // No match found
        log.warn("Failed token authentication attempt for email: {}", email);
        throw new IllegalArgumentException("Invalid credentials");
    }

    /**
     * Convert entity to DTO (without revealing token)
     */
    private TokenResponse toTokenResponse(UserTokenEntity entity) {
        return TokenResponse.builder()
                .id(entity.getId())
                .tokenName(entity.getTokenName())
                .token(null) // Never return actual token after creation
                .tokenPrefix(entity.getTokenPrefix())
                .scopes(entity.getScopes())
                .expiresAt(entity.getExpiresAt())
                .lastUsedAt(entity.getLastUsedAt())
                .lastUsedIp(entity.getLastUsedIp())
                .revoked(entity.isRevoked())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}