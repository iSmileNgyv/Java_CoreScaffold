package com.ismile.core.auth.config;

import com.ismile.core.auth.security.PasswordValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security configuration for Auth service
 * Provides password encoding and validation
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtConfig jwtConfig;

    /**
     * BCrypt password encoder bean
     * Strength: 12 (2^12 iterations)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Password validator bean with length limits
     * Mitigates CVE-2025-22228
     */
    @Bean
    public PasswordValidator passwordValidator() {
        return new PasswordValidator(
                jwtConfig.getMinPasswordLength(),
                jwtConfig.getMaxPasswordLength()
        );
    }
}