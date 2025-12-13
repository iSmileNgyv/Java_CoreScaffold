package com.ismile.core.chronovcs.config;

import com.ismile.core.chronovcs.config.security.ChronoAuthFilter;
import com.ismile.core.chronovcs.security.PatAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Collections;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ChronoAuthFilter chronoAuthFilter;
    private final PatAuthenticationProvider patAuthenticationProvider;
    private final ChronoAuthEntryPoint authEntryPoint;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CORS Configuration - Must be first!
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // Disable CSRF for stateless API
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session management
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Exception handling
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authEntryPoint)
                )

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Allow CORS preflight
                        .anyRequest().authenticated()
                )

                // Custom authentication filter
                .addFilterBefore(chronoAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> {});

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(Collections.singletonList(patAuthenticationProvider));
    }
}