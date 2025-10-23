package com.ismile.core.notification.security;

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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationApiKeyGrpcInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> API_KEY_HEADER =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

    private final NotificationSecurityProperties securityProperties;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String methodName = call.getMethodDescriptor().getFullMethodName();

        if (securityProperties.isPublicEndpoint(methodName)) {
            return next.startCall(call, headers);
        }

        String providedKey = headers.get(API_KEY_HEADER);
        if (!securityProperties.isApiKeyAllowed(providedKey)) {
            if (providedKey == null || providedKey.isBlank()) {
                log.warn("Rejected gRPC call to {} due to missing API key header", methodName);
            } else {
                log.warn("Rejected gRPC call to {} due to invalid API key", methodName);
            }
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid API key"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        return next.startCall(call, headers);
    }
}
