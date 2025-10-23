package com.ismile.core.otp.config;

import com.ismile.core.otp.security.OtpApiKeyGrpcInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.ServerBuilderCustomizer;

/**
 * Registers security-related gRPC interceptors for the OTP service.
 */
@Configuration
@RequiredArgsConstructor
public class GrpcSecurityConfig {

    private final OtpApiKeyGrpcInterceptor otpApiKeyGrpcInterceptor;

    @Bean
    public ServerBuilderCustomizer otpServerBuilderCustomizer() {
        return serverBuilder -> serverBuilder.intercept(otpApiKeyGrpcInterceptor);
    }
}
