package com.ismile.core.notification.security;

import com.ismile.core.notification.util.JwtUtil;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * gRPC {@link ServerInterceptor} that enforces a shared API key for Notification operations.
 *
 * <p>Requests that present a valid bearer token are also accepted so that end-user clients can
 * authenticate with their JWT without exposing the internal API key.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationApiKeyGrpcInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> API_KEY_HEADER =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER_PREFIX = "Bearer ";

    private final NotificationSecurityProperties securityProperties;
    private final JwtUtil jwtUtil;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String methodName = call.getMethodDescriptor().getFullMethodName();

        if (securityProperties.isPublicEndpoint(methodName)) {
            return next.startCall(call, headers);
        }

        String bearerToken = extractBearerToken(headers.get(AUTHORIZATION_HEADER));
        if (bearerToken != null) {
            if (jwtUtil.validateToken(bearerToken)) {
                return next.startCall(call, headers);
            }
            log.warn("Rejected gRPC call to {} due to invalid bearer token", methodName);
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid credentials"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        String providedKey = headers.get(API_KEY_HEADER);
        if (!securityProperties.isApiKeyAllowed(providedKey)) {
            if (providedKey == null || providedKey.isBlank()) {
                log.warn("Rejected gRPC call to {} due to missing API key header", methodName);
            } else {
                log.warn("Rejected gRPC call to {} due to invalid API key", methodName);
            }
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid credentials"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        return next.startCall(call, headers);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }

        String trimmed = authorizationHeader.trim();
        if (trimmed.length() <= BEARER_PREFIX.length()) {
            return null;
        }

        if (!trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        String token = trimmed.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return null;
        }

        return token;
    }
}
