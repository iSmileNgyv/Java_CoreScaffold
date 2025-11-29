package com.ismile.core.chronovcs.config;

import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import com.ismile.core.chronovcs.service.auth.AuthService;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

@Slf4j
@RequiredArgsConstructor
public class ChronoAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_AUTH_ERROR_CODE = "CHRONO_AUTH_ERROR_CODE";
    public static final String ATTR_AUTH_ERROR_MESSAGE = "CHRONO_AUTH_ERROR_MESSAGE";

    private final AuthService authService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // path-ləri burda whitelist edə bilərsən istəsən
        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || header.isBlank()) {
            markAuthError(request, "UNAUTHORIZED", "Missing Authorization header");
            filterChain.doFilter(request, response);
            return;
        }

        if (header.startsWith("Basic ")) {
            handleBasicAuth(request, header.substring("Basic ".length()));
        } else if (header.startsWith("Bearer ")) {
            // gələcəkdə JWT üçün saxlayırıq
            markAuthError(request, "UNAUTHORIZED", "Bearer auth is not implemented yet");
        } else {
            markAuthError(request, "UNAUTHORIZED", "Unsupported authorization scheme");
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        // Lazım olsa bura daha çox pattern əlavə edərsən
        return path.startsWith("/actuator/health");
    }

    private void handleBasicAuth(HttpServletRequest request, String base64Part) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Part.getBytes(StandardCharsets.UTF_8));
            String raw = new String(decoded, StandardCharsets.UTF_8);

            int idx = raw.indexOf(':');
            if (idx <= 0) {
                markAuthError(request, "UNAUTHORIZED", "Invalid Basic Authorization format");
                return;
            }

            String email = raw.substring(0, idx);
            String token = raw.substring(idx + 1);

            if (email.isBlank() || token.isBlank()) {
                markAuthError(request, "UNAUTHORIZED", "Email or token is blank");
                return;
            }

            AuthenticatedUser user = authService.authenticateBasic(email, token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            Collections.emptyList()
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception ex) {
            log.warn("Basic auth failed: {}", ex.getMessage());
            markAuthError(request, "UNAUTHORIZED", "Invalid credentials");
        }
    }

    private void markAuthError(HttpServletRequest request, String code, String message) {
        request.setAttribute(ATTR_AUTH_ERROR_CODE, code);
        request.setAttribute(ATTR_AUTH_ERROR_MESSAGE, message);
        // SecurityContext-i boş buraxırıq – Spring sonra entrypoint çağıracaq
        SecurityContextHolder.clearContext();
    }
}
