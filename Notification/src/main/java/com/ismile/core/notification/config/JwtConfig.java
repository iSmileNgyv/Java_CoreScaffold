package com.ismile.core.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for JWT.
 * This class binds properties under the 'jwt' prefix from the application.yml file
 * in a type-safe manner. This is the recommended approach over using @Value for structured properties.
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtConfig {

    /**
     * The secret key used for signing and verifying JWT tokens.
     * It's crucial that this key is kept secure and is long and complex enough
     * for the chosen signing algorithm (e.g., HS512). It must be the same secret
     * used in the Auth service.
     */
    private String secret;
}