package com.ismile.core.chronovcs.config.security;

import com.ismile.core.chronovcs.service.auth.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChronoAuthFilter extends OncePerRequestFilter {

    // Bu sabitlər ChronoAuthEntryPoint tərəfindən istifadə olunur
    public static final String ATTR_AUTH_ERROR_CODE = "AUTH_ERROR_CODE";
    public static final String ATTR_AUTH_ERROR_MESSAGE = "AUTH_ERROR_MESSAGE";

    private final JwtTokenService jwtTokenService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // 1. Basic Auth və ya Token yoxdursa, icazə ver keçsin.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Bearer Token emalı
        try {
            String jwt = authHeader.substring(7);

            if (jwtTokenService.validateToken(jwt)) {
                UserDetails userDetails = jwtTokenService.getUserFromToken(jwt);

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            log.error("Authentication error: {}", e.getMessage());
            // Xəta detallarını request-ə yazırıq ki, EntryPoint oxuya bilsin
            request.setAttribute(ATTR_AUTH_ERROR_CODE, "INVALID_TOKEN");
            request.setAttribute(ATTR_AUTH_ERROR_MESSAGE, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}