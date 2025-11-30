package com.ismile.core.chronovcs.security.provider;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PatTokenProvider {

    private static final String PREFIX = "cvcs_";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a random PAT.
     * Format: cvcs_<base64_string>
     */
    public TokenPair generate() {
        byte[] randomBytes = new byte[32]; // 256-bit random
        RANDOM.nextBytes(randomBytes);

        // URL-safe base64 to avoid special chars
        String randomStr = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String fullToken = PREFIX + randomStr;

        // We use the first 10 chars as a "prefix" for DB indexing.
        // This lets us find candidate tokens quickly without scanning the whole table.
        String prefix = fullToken.substring(0, 10);

        return new TokenPair(fullToken, prefix);
    }

    // Helper class to return both full token and its prefix
    @Getter
    public static class TokenPair {
        private final String fullToken;
        private final String prefix;

        public TokenPair(String fullToken, String prefix) {
            this.fullToken = fullToken;
            this.prefix = prefix;
        }
    }

    // Helper to check if a string looks like our token
    public boolean isPatToken(String token) {
        return token != null && token.startsWith(PREFIX);
    }

    public String extractPrefix(String token) {
        if (token.length() < 10) return token;
        return token.substring(0, 10);
    }
}