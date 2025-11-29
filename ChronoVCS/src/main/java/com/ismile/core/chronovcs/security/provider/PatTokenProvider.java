package com.ismile.core.chronovcs.security.provider;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Personal Access Token Provider
 * CLI authentication üçün token yaradır
 *
 * Format: cvcs_<random-base64>
 * Nümunə: cvcs_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
 */
@Slf4j
public class PatTokenProvider {

    private static final String TOKEN_PREFIX = "cvcs_";
    private static final int TOKEN_BYTES = 32; // 256 bits
    private static final int PREFIX_LENGTH = 8; // prefix chars for DB index

    private final SecureRandom secureRandom;

    public PatTokenProvider() {
        this.secureRandom = new SecureRandom();
        log.info("PatTokenProvider initialized");
    }

    /**
     * Yeni PAT yarat
     *
     * @return Token və prefix
     */
    public TokenWithPrefix generateToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);

        // URL-safe Base64 (no padding)
        String randomPart = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        String fullToken = TOKEN_PREFIX + randomPart;
        String prefix = extractPrefix(fullToken);

        log.debug("Generated PAT with prefix: {}", prefix);

        return new TokenWithPrefix(fullToken, prefix);
    }

    /**
     * Token-dan prefix çıxart (DB indexing üçün)
     */
    public String extractPrefix(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null");
        }

        if (!token.startsWith(TOKEN_PREFIX)) {
            throw new IllegalArgumentException("Token must start with " + TOKEN_PREFIX);
        }

        int minLength = TOKEN_PREFIX.length() + PREFIX_LENGTH;
        if (token.length() < minLength) {
            throw new IllegalArgumentException("Token too short");
        }

        return token.substring(0, minLength);
    }

    /**
     * Token format valid mi?
     */
    public boolean isValidFormat(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        if (!token.startsWith(TOKEN_PREFIX)) {
            return false;
        }

        // Minimum length: cvcs_ (5) + base64 (40+)
        if (token.length() < 45) {
            return false;
        }

        // Check Base64 URL-safe characters
        String afterPrefix = token.substring(TOKEN_PREFIX.length());
        return afterPrefix.matches("^[A-Za-z0-9_-]+$");
    }

    /**
     * Token və prefix birlikdə
     */
    @Getter
    public static class TokenWithPrefix {
        private final String fullToken;
        private final String prefix;

        public TokenWithPrefix(String fullToken, String prefix) {
            this.fullToken = fullToken;
            this.prefix = prefix;
        }

        @Override
        public String toString() {
            return "TokenWithPrefix{prefix='" + prefix + "'}";
        }
    }
}