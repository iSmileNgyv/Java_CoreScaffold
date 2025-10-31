package com.ismile.core.docs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtConfig {

    /**
     * Shared signing secret that must match the Auth service configuration so
     * that JWT tokens issued by Auth can be validated locally.
     */
    private String secret;
}
