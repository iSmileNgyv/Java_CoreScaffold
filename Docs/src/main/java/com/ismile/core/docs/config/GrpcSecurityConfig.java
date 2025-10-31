package com.ismile.core.docs.config;

import com.ismile.core.docs.security.DocsAuthenticationGrpcInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.ServerBuilderCustomizer;

@Configuration
@RequiredArgsConstructor
public class GrpcSecurityConfig {

    private final DocsAuthenticationGrpcInterceptor interceptor;

    @Bean
    public ServerBuilderCustomizer docsServerBuilderCustomizer() {
        return serverBuilder -> serverBuilder.intercept(interceptor);
    }
}
