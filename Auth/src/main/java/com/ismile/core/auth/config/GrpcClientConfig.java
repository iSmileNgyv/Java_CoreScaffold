package com.ismile.core.auth.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import otp.OtpServiceGrpc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Value("${grpc.client.otp-service.address}")
    private String otpServiceAddress;

    /**
     * Creates a gRPC channel for the OTP service.
     * Channels are expensive to create, so this should be a singleton bean.
     * @return A ManagedChannel instance.
     */
    @Bean(name = "otpServiceChannel")
    public ManagedChannel otpServiceChannel() {
        // usePlaintext is suitable for development/internal networks.
        // For production, you should use TLS for secure communication.
        return ManagedChannelBuilder.forTarget(otpServiceAddress)
                .usePlaintext()
                .build();
    }

    /**
     * Creates a blocking gRPC stub for the OtpService.
     * The stub is the primary way for clients to interact with the gRPC service.
     * @param channel The managed channel to the OTP service.
     * @return A blocking stub for OtpService.
     */
    @Bean
    public OtpServiceGrpc.OtpServiceBlockingStub otpServiceBlockingStub(ManagedChannel channel) {
        return OtpServiceGrpc.newBlockingStub(channel);
    }
}
