package com.ismile.core.chronovcs.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "chronovcs.security.jwt")
public class JwtProperties {

    /**
     * Base64 encoded secret (minimum 256 bit for HS256).
     */
    private String secret;

    /**
     * Access token lifetime in seconds (e.g. 900 = 15 minutes).
     */
    private long accessTtlSeconds = 900;

    /**
     * Refresh token lifetime in seconds (e.g. 30 days).
     */
    private long refreshTtlSeconds = 30L * 24 * 60 * 60;
}