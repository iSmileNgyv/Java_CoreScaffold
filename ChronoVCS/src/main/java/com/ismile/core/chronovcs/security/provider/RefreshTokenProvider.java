package com.ismile.core.chronovcs.security.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Refresh Token Provider
 * JWT refresh token-ları üçün random string yaradır
 *
 * Format: Base64 random string
 * Nümunə: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2
 */
@Component
@Slf4j
public class RefreshTokenProvider {

    private static final int TOKEN_BYTES = 32; // 256 bits
    private final SecureRandom secureRandom;

    public RefreshTokenProvider() {
        this.secureRandom = new SecureRandom();
        log.info("RefreshTokenProvider initialized");
    }

    /**
     * Refresh token yarat
     *
     * @return Random base64 string (~43 chars)
     */
    public String generateToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);

        // URL-safe Base64 (no padding)
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    /**
     * Token format valid mi?
     */
    public boolean isValidFormat(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        // Base64 encoded 32 bytes = ~43 chars
        if (token.length() < 40 || token.length() > 50) {
            return false;
        }

        // Check Base64 URL-safe characters
        return token.matches("^[A-Za-z0-9_-]+$");
    }
}