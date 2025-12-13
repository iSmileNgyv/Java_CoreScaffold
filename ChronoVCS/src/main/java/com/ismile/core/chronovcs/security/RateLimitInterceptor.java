package com.ismile.core.chronovcs.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcs.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

/**
 * HTTP Interceptor for rate limiting authentication endpoints
 *
 * Intercepts requests to:
 * - /api/auth/login
 * - /api/auth/register
 * - /api/auth/refresh
 *
 * Returns 429 Too Many Requests if rate limit exceeded
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String ipAddress = getClientIp(request);

        boolean allowed = true;

        // Check rate limits for specific endpoints
        if (path.endsWith("/api/auth/login")) {
            allowed = rateLimitService.allowLogin(ipAddress);
        } else if (path.endsWith("/api/auth/register")) {
            allowed = rateLimitService.allowRegister(ipAddress);
        } else if (path.endsWith("/api/auth/refresh")) {
            allowed = rateLimitService.allowTokenRefresh(ipAddress);
        }

        if (!allowed) {
            sendRateLimitError(response, path);
            return false;
        }

        return true;
    }

    /**
     * Send rate limit exceeded error response
     */
    private void sendRateLimitError(HttpServletResponse response, String path) throws Exception {
        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .success(false)
                .errorCode("RATE_LIMIT_EXCEEDED")
                .message("Too many requests. Please try again later.")
                .path(path)
                .timestamp(Instant.now())
                .build();

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    /**
     * Extract client IP address from request
     * Handles proxy headers (X-Forwarded-For, X-Real-IP)
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // If X-Forwarded-For contains multiple IPs, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }
}
