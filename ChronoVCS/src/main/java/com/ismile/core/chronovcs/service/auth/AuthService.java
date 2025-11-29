package com.ismile.core.chronovcs.service.auth;

import com.ismile.core.chronovcs.dto.auth.*;
import com.ismile.core.chronovcs.entity.AuthLogEntity;
import com.ismile.core.chronovcs.entity.RefreshTokenEntity;
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.repository.AuthLogRepository;
import com.ismile.core.chronovcs.repository.RefreshTokenRepository;
import com.ismile.core.chronovcs.repository.UserRepository;
import com.ismile.core.chronovcs.security.provider.JwtTokenProvider;
import com.ismile.core.chronovcs.security.provider.RefreshTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthLogRepository authLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenProvider refreshTokenProvider;

    @Value("${chronovcs.jwt.refresh-token-validity-days:7}")
    private int refreshTokenValidityDays;

    @Value("${chronovcs.jwt.access-token-validity-minutes:15}")
    private long accessTokenValidityMinutes;

    /**
     * Register new user
     */
    @Transactional
    public LoginResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        // Validate email not exists
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Create user
        UserEntity user = UserEntity.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .active(true)
                .emailVerified(false) // Email verification can be added later
                .build();

        user = userRepository.save(user);

        log.info("User registered: {} ({})", user.getEmail(), user.getUserUid());

        // Auto-login after registration
        return generateLoginResponse(user, ipAddress, userAgent);
    }

    /**
     * Login with email and password
     */
    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        // Find user
        UserEntity user = userRepository.findByEmailIgnoreCaseAndActiveTrue(request.getEmail())
                .orElse(null);

        // Validate password
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Log failed attempt
            authLogRepository.save(
                    AuthLogEntity.loginFailed(request.getEmail(), ipAddress, userAgent, "Invalid credentials")
            );
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Log successful login
        authLogRepository.save(
                AuthLogEntity.loginSuccess(user, ipAddress, userAgent)
        );

        log.info("User logged in: {} from IP: {}", user.getEmail(), ipAddress);

        return generateLoginResponse(user, ipAddress, userAgent);
    }

    /**
     * Refresh access token using refresh token
     */
    @Transactional
    public LoginResponse refresh(RefreshRequest request, String ipAddress, String userAgent) {
        // Hash the incoming refresh token
        String tokenHash = hashSha256(request.getRefreshToken());

        // Find refresh token
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        // Validate token
        if (!refreshToken.isValid()) {
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }

        UserEntity user = refreshToken.getUser();

        // Update last used
        refreshToken.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        // Generate new tokens (rotation strategy - old refresh token gets revoked)
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        log.info("Token refreshed for user: {}", user.getEmail());

        return generateLoginResponse(user, ipAddress, userAgent);
    }

    /**
     * Logout (revoke refresh token)
     */
    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = hashSha256(refreshToken);

        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    token.revoke();
                    refreshTokenRepository.save(token);
                    log.info("User logged out: {}", token.getUser().getEmail());
                });
    }

    /**
     * Logout from all devices (revoke all refresh tokens)
     */
    @Transactional
    public void logoutAll(UserEntity user) {
        refreshTokenRepository.revokeAllByUser(user, LocalDateTime.now());
        log.info("User logged out from all devices: {}", user.getEmail());
    }

    /**
     * Generate login response with access token and refresh token
     */
    private LoginResponse generateLoginResponse(UserEntity user, String ipAddress, String userAgent) {
        // Generate access token (JWT)
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUserUid(),
                user.getEmail()
        );

        // Generate refresh token
        String refreshToken = refreshTokenProvider.generateRefreshToken();
        String refreshTokenHash = hashSha256(refreshToken);

        // Save refresh token to DB
        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .deviceInfo(extractDeviceInfo(userAgent))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenValidityDays))
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        // Build user DTO
        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .userUid(user.getUserUid())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .active(user.isActive())
                .emailVerified(user.isEmailVerified())
                .build();

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenValidityMinutes * 60) // Convert to seconds
                .user(userDto)
                .build();
    }

    /**
     * Hash string with SHA-256
     */
    private String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Extract device info from user agent (simple version)
     */
    private String extractDeviceInfo(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }

        // Simple extraction - can be improved with user-agent parser library
        if (userAgent.contains("Chrome")) {
            return "Chrome Browser";
        } else if (userAgent.contains("Firefox")) {
            return "Firefox Browser";
        } else if (userAgent.contains("Safari")) {
            return "Safari Browser";
        } else if (userAgent.contains("Edge")) {
            return "Edge Browser";
        }

        return "Unknown Browser";
    }
}