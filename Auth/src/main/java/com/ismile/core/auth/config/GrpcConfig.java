package com.ismile.core.auth.config;

import com.ismile.core.auth.security.GrpcAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.ServerBuilderCustomizer;

/**
 * gRPC Server Configuration
 * Registers interceptors for authentication
 */
@Configuration
@RequiredArgsConstructor
public class GrpcConfig {

    private final GrpcAuthInterceptor authInterceptor;

    /**
     * Add auth interceptor to gRPC server
     */
    @Bean
    public ServerBuilderCustomizer grpcServerCustomizer() {
        return serverBuilder -> {
            serverBuilder.intercept(authInterceptor);
        };
    }
}