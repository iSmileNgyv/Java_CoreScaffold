package com.ismile.core.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT configuration properties
 * Loaded from application.yml
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtConfig {

    // Secret key for signing JWT tokens
    private String secret = "MyDefaultSecretKeyForJWT2024!ChangeThisInProduction";

    // Token expiration time in milliseconds (24 hours)
    private long expiration = 86400000;

    // Refresh token expiration time in milliseconds (7 days)
    private long refreshExpiration = 604800000;

    // Token issuer
    private String issuer = "auth-service";

    // Password security limits (CVE-2025-22228 mitigation)
    private int maxPasswordLength = 72; // BCrypt limit
    private int minPasswordLength = 8;
}