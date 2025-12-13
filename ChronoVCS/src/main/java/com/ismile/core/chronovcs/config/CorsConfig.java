package com.ismile.core.chronovcs.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) Configuration
 *
 * Security features:
 * - Configurable allowed origins (production domains only)
 * - Limited HTTP methods
 * - Credential support for authenticated requests
 * - Preflight request caching
 * - Exposed headers configuration
 */
@Configuration
@Slf4j
public class CorsConfig {

    @Value("${chronovcs.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    @Value("${chronovcs.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String[] allowedMethods;

    @Value("${chronovcs.cors.allowed-headers:*}")
    private String[] allowedHeaders;

    @Value("${chronovcs.cors.exposed-headers:Authorization,Content-Type}")
    private String[] exposedHeaders;

    @Value("${chronovcs.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${chronovcs.cors.max-age:3600}")
    private long maxAge;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origins - IMPORTANT: Configure for production!
        List<String> origins = Arrays.asList(allowedOrigins);
        configuration.setAllowedOrigins(origins);
        log.info("CORS allowed origins: {}", origins);

        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList(allowedMethods));

        // Allowed headers
        if (allowedHeaders.length == 1 && "*".equals(allowedHeaders[0])) {
            configuration.addAllowedHeader("*");
        } else {
            configuration.setAllowedHeaders(Arrays.asList(allowedHeaders));
        }

        // Exposed headers (headers that browser can access)
        configuration.setExposedHeaders(Arrays.asList(exposedHeaders));

        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(allowCredentials);

        // Preflight request cache duration (seconds)
        configuration.setMaxAge(maxAge);

        // Apply CORS configuration to all endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("CORS configuration initialized: methods={}, credentials={}, maxAge={}s",
                Arrays.toString(allowedMethods), allowCredentials, maxAge);

        return source;
    }
}
