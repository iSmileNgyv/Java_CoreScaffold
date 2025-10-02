package com.ismile.core.auth.security;

import com.ismile.core.auth.entity.AuditLogEntity;
import com.ismile.core.auth.repository.AuditLogRepository;
import io.grpc.*;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * gRPC Interceptor for JWT token validation
 * Intercepts all gRPC calls and validates authorization header
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GrpcAuthInterceptor implements ServerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogRepository auditLogRepository;

    // Context key for metadata
    private static final Context.Key<Metadata> METADATA_KEY = Context.key("metadata");

    // Public endpoints that don't require authentication
    private static final List<String> PUBLIC_ENDPOINTS = Arrays.asList(
            "auth.AuthService/Login",
            "auth.AuthService/Register",
            "auth.AuthService/RefreshToken",
            "envoy.service.auth.v3.Authorization/Check"
    );

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();

        // Store metadata in context for later use
        Context context = Context.current().withValue(METADATA_KEY, headers);

        // Skip authentication for public endpoints
        if (PUBLIC_ENDPOINTS.contains(methodName)) {
            log.debug("Public endpoint accessed: {}", methodName);
            return Contexts.interceptCall(context, call, headers, next);
        }

        // Extract authorization header
        String authHeader = headers.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid authorization header for: {}", methodName);

            // Log unauthorized attempt
            logUnauthorizedAccess(methodName, "Missing authorization header");

            call.close(
                    Status.UNAUTHENTICATED.withDescription("Missing or invalid authorization header"),
                    new Metadata()
            );
            return new ServerCall.Listener<ReqT>() {};
        }

        // Extract token
        String token = authHeader.substring(7);

        try {
            // Validate token
            Claims claims = jwtTokenProvider.validateToken(token);

            // Extract user info from token
            int userId = Integer.parseInt(claims.getSubject());
            String username = claims.get("username", String.class);
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);

            log.debug("Authenticated user: {} (ID: {}) with roles: {}", username, userId, roles);

            // Add user context to metadata for downstream use
            Metadata newHeaders = new Metadata();
            newHeaders.merge(headers);
            newHeaders.put(
                    Metadata.Key.of("user-id", Metadata.ASCII_STRING_MARSHALLER),
                    String.valueOf(userId)
            );
            newHeaders.put(
                    Metadata.Key.of("username", Metadata.ASCII_STRING_MARSHALLER),
                    username
            );

            // Create context with both metadata and user info
            Context newContext = context
                    .withValue(METADATA_KEY, newHeaders);

            return Contexts.interceptCall(newContext, call, newHeaders, next);

        } catch (com.ismile.core.auth.exception.SecurityException e) {
            log.warn("Token validation failed for {}: {}", methodName, e.getMessage());

            // Log failed validation
            logUnauthorizedAccess(methodName, e.getMessage());

            call.close(
                    Status.UNAUTHENTICATED.withDescription("Invalid or expired token: " + e.getMessage()),
                    new Metadata()
            );
            return new ServerCall.Listener<ReqT>() {};
        } catch (Exception e) {
            log.error("Unexpected error during authentication for {}: {}", methodName, e.getMessage());

            call.close(
                    Status.INTERNAL.withDescription("Authentication error"),
                    new Metadata()
            );
            return new ServerCall.Listener<ReqT>() {};
        }
    }

    /**
     * Log unauthorized access attempt
     */
    private void logUnauthorizedAccess(String endpoint, String reason) {
        try {
            AuditLogEntity auditLog = AuditLogEntity.builder()
                    .eventType(AuditLogEntity.AuditEventType.UNAUTHORIZED_ACCESS)
                    .details("Endpoint: " + endpoint + ", Reason: " + reason)
                    .success(false)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log unauthorized access: {}", e.getMessage());
        }
    }
}