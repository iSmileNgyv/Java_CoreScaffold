package com.ismile.core.chronovcs.config.security;

import com.ismile.core.chronovcs.exception.UnauthorizedException;
import com.ismile.core.chronovcs.service.auth.AuthService;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.auth.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@RequiredArgsConstructor
public class ChronoAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_AUTH_ERROR_CODE = "AUTH_ERROR_CODE";
    public static final String ATTR_AUTH_ERROR_MESSAGE = "AUTH_ERROR_MESSAGE";

    private final AuthService authService;
    private final JwtTokenService jwtTokenService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);

            if (header == null || header.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }

            if (header.startsWith("Basic ")) {
                handleBasic(header.substring(6).trim());
            } else if (header.startsWith("Bearer ")) {
                handleBearer(header.substring(7).trim());
            }

        } catch (UnauthorizedException ex) {
            SecurityContextHolder.clearContext();
            request.setAttribute(ATTR_AUTH_ERROR_CODE, "UNAUTHORIZED");
            request.setAttribute(ATTR_AUTH_ERROR_MESSAGE, ex.getMessage());
        } catch (Exception ex) {
            log.error("Authentication error", ex);
            SecurityContextHolder.clearContext();
            request.setAttribute(ATTR_AUTH_ERROR_CODE, "AUTH_ERROR");
            request.setAttribute(ATTR_AUTH_ERROR_MESSAGE, "Authentication failed");
        }

        filterChain.doFilter(request, response);
    }

    private void handleBasic(String basicToken) {
        authService.authenticateBasic(basicToken)
                .ifPresent(this::setAuthenticatedUser);
    }

    private void handleBearer(String jwtToken) {
        AuthenticatedUser user = jwtTokenService.parseAccessToken(jwtToken);
        setAuthenticatedUser(user);
    }

    private void setAuthenticatedUser(AuthenticatedUser user) {
        ChronoUserPrincipal principal = new ChronoUserPrincipal(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        Collections.emptyList()
                );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}