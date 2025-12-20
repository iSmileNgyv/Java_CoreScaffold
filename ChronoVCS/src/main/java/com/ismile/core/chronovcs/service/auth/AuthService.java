package com.ismile.core.chronovcs.service.auth;

import com.ismile.core.chronovcs.config.security.ChronoUserPrincipal;
import com.ismile.core.chronovcs.dto.auth.LoginRequest;
import com.ismile.core.chronovcs.dto.auth.LoginResponse;
import com.ismile.core.chronovcs.dto.auth.RegisterRequest;
import com.ismile.core.chronovcs.entity.RefreshTokenEntity;
import com.ismile.core.chronovcs.entity.UserEntity;
import com.ismile.core.chronovcs.repository.RefreshTokenRepository;
import com.ismile.core.chronovcs.repository.UserRepository;
import com.ismile.core.chronovcs.security.provider.RefreshTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository; // Yeni
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenProvider refreshTokenProvider; // Yeni

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        // 1. Email artıq mövcuddursa error
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadCredentialsException("Email already exists");
        }

        // 2. Yeni user yarat
        UserEntity user = UserEntity.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .active(true)
                .emailVerified(false)
                .build();

        userRepository.save(user);

        // 3. Access Token yarat (JWT)
        AuthenticatedUser authUser = AuthenticatedUser.fromEntity(user);
        String accessToken = jwtTokenService.generateAccessToken(new ChronoUserPrincipal(authUser));

        // 4. Refresh Token yarat (Random String)
        String rawRefreshToken = refreshTokenProvider.generateToken();

        // 5. Refresh Token-i Hash-lə və Bazada saxla
        String tokenHash = hashToken(rawRefreshToken);

        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusDays(7)) // 7 gün vaxt
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        // 6. Cavabı qaytar
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .email(user.getEmail())
                .userUid(user.getUserUid())
                .build();
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. Useri tap
        UserEntity user = userRepository.findByEmailAndActiveTrue(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // 2. Parolu yoxla
        if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid password");
        }

        // 3. Access Token yarat (JWT)
        AuthenticatedUser authUser = AuthenticatedUser.fromEntity(user);
        String accessToken = jwtTokenService.generateAccessToken(new ChronoUserPrincipal(authUser));

        // 4. Refresh Token yarat (Random String)
        String rawRefreshToken = refreshTokenProvider.generateToken();

        // 5. Refresh Token-i Hash-lə və Bazada saxla
        // Qeyd: Refresh Tokenləri BCrypt ilə yox, sürətli olsun deyə SHA-256 ilə hash-ləyirik
        String tokenHash = hashToken(rawRefreshToken);

        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusDays(7)) // 7 gün vaxt
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        // 6. Cavabı qaytar
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken) // Userə originalı veririk
                .email(user.getEmail())
                .userUid(user.getUserUid())
                .build();
    }

    // SHA-256 Hashing Helper
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing refresh token", e);
        }
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        // 1. Gələn tokeni hash-lə
        String tokenHash = hashToken(rawRefreshToken);

        // 2. Bazadan tap
        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        // 3. Validasiya (Vaxtı keçibmi? Revoke olubmu?)
        if (!tokenEntity.isValid()) {
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        UserEntity user = tokenEntity.getUser();

        // 4. Token Rotation (Köhnəni revoke et, yenisini yarat)
        // Köhnəni "işlənmiş" kimi işarələyirik
        tokenEntity.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(tokenEntity);

        // Yeni Refresh Token yarad
        String newRawRefreshToken = refreshTokenProvider.generateToken();
        String newTokenHash = hashToken(newRawRefreshToken);

        RefreshTokenEntity newRefreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(newTokenHash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        refreshTokenRepository.save(newRefreshTokenEntity);

        // 5. Yeni Access Token yarad
        AuthenticatedUser authUser = AuthenticatedUser.fromEntity(user);
        String newAccessToken = jwtTokenService.generateAccessToken(new ChronoUserPrincipal(authUser));

        // 6. Cavabı qaytar
        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .email(user.getEmail())
                .userUid(user.getUserUid())
                .build();
    }
}