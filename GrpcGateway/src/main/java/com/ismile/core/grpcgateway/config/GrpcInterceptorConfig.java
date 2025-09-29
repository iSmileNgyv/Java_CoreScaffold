package com.ismile.core.grpcgateway.config;

import com.ismile.core.grpcgateway.interceptors.AuthInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcInterceptorConfig {
    @GrpcGlobalServerInterceptor
    public AuthInterceptor authInterceptor() {
        return new AuthInterceptor();
    }
}
