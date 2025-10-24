package com.ismile.core.notification.config;

import com.ismile.core.notification.security.NotificationApiKeyGrpcInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.ServerBuilderCustomizer;

/**
 * Registers security-related gRPC interceptors for the Notification service.
 */
@Configuration
@RequiredArgsConstructor
public class GrpcSecurityConfig {

    private final NotificationApiKeyGrpcInterceptor notificationApiKeyGrpcInterceptor;

    @Bean
    public ServerBuilderCustomizer notificationServerBuilderCustomizer() {
        return serverBuilder -> serverBuilder.intercept(notificationApiKeyGrpcInterceptor);
    }
}
