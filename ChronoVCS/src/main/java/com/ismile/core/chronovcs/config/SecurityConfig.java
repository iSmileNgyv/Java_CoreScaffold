package com.ismile.core.chronovcs.config.security;

import com.ismile.core.chronovcs.config.ChronoAuthEntryPoint;
import com.ismile.core.chronovcs.config.ChronoAuthFilter;
import com.ismile.core.chronovcs.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ChronoAuthEntryPoint authEntryPoint;
    private final AuthService authService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ChronoAuthFilter chronoAuthFilter() {
        return new ChronoAuthFilter(authService);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS
                ))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                )
                .authorizeHttpRequests(auth -> auth
                        // public endpoint-lər (istəsən artırarsan)
                        .requestMatchers("/actuator/health").permitAll()
                        // gələcəkdə registration, public info və s. əlavə edə bilərsən
                        // login yoxdu, çünki CLI ilk dəfə də Basic ilə gəlir
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults()); // sadəcə disable eləmirik, amma öz filter-miz işləyəcək

        // Öz filter-imizi chain-ə əlavə edirik
        http.addFilterBefore(
                chronoAuthFilter(),
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
}