package com.ismile.core.auth.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
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
        // --- FIX STARTS HERE ---
        // Instead of using forTarget with a "static://" scheme, we parse the address manually
        // and use forAddress(host, port). This is more robust and avoids NameResolverProvider issues.
        try {
            String[] parts = otpServiceAddress.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid gRPC address format in application.yml. Expected 'host:port', but got: " + otpServiceAddress);
            }
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            // usePlaintext is suitable for development/internal networks.
            // For production, you should use TLS for secure communication.
            return ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port in gRPC address: " + otpServiceAddress, e);
        }
        // --- FIX ENDS HERE ---
    }

    /**
     * Creates a blocking gRPC stub for the OtpService.
     * The stub is the primary way for clients to interact with the gRPC service.
     * @param channel The managed channel to the OTP service. @Qualifier ensures the correct channel is injected.
     * @return A blocking stub for OtpService.
     */
    @Bean
    public OtpServiceGrpc.OtpServiceBlockingStub otpServiceBlockingStub(@Qualifier("otpServiceChannel") ManagedChannel channel) {
        return OtpServiceGrpc.newBlockingStub(channel);
    }
}

