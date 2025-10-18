package com.ismile.core.otp.services;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.grpc.server.service.GrpcService;
import otp.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class OtpServiceImpl extends OtpServiceGrpc.OtpServiceImplBase {

    private final StringRedisTemplate redisTemplate;

    private static final int OTP_LENGTH = 6; // Length of the OTP code
    private static final long OTP_TTL_MINUTES = 5; // Time-to-live for the OTP in minutes

    /**
     * Handles the SendCode RPC call.
     * Generates a numeric OTP, stores it in Redis with a TTL, and returns a success response.
     */
    @Override
    public void sendCode(SendCodeRequest request, StreamObserver<SendCodeResponse> responseObserver) {
        request.getDestination();
        if (request.getDestination().trim().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Destination cannot be empty.").asRuntimeException());
            return;
        }

        if (request.getType() == OtpType.UNKNOWN) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("OTP type cannot be UNKNOWN.").asRuntimeException());
            return;
        }

        try {
            // Generate a random 6-digit numeric code
            String code = RandomStringUtils.randomNumeric(OTP_LENGTH);
            String redisKey = buildRedisKey(request.getDestination(), request.getType());

            // Store the code in Redis with a 5-minute expiration
            redisTemplate.opsForValue().set(redisKey, code, OTP_TTL_MINUTES, TimeUnit.MINUTES);

            // In a real application, you would integrate an SMS/Email gateway here.
            // For now, we log the code for testing purposes.
            log.info("Generated OTP for [{}]. Type: [{}], Code: [{}], Key: [{}]",
                    request.getDestination(), request.getType(), code, redisKey);

            String requestId = UUID.randomUUID().toString();
            SendCodeResponse response = SendCodeResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("OTP code has been sent successfully.")
                    .setRequestId(requestId)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error sending OTP for destination [{}]: {}", request.getDestination(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to send OTP code.").asRuntimeException());
        }
    }

    /**
     * Handles the VerifyCode RPC call.
     * Checks the provided code against the one stored in Redis.
     */
    @Override
    public void verifyCode(VerifyCodeRequest request, StreamObserver<VerifyCodeResponse> responseObserver) {
        request.getDestination();
        if (request.getDestination().trim().isEmpty() || request.getCode().trim().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Destination and code cannot be empty.").asRuntimeException());
            return;
        }

        try {
            String redisKey = buildRedisKey(request.getDestination(), request.getType());
            String storedCode = redisTemplate.opsForValue().get(redisKey);

            VerifyCodeResponse.Builder responseBuilder = VerifyCodeResponse.newBuilder();

            if (storedCode != null && storedCode.equals(request.getCode())) {
                // Successful verification, delete the key to prevent reuse
                redisTemplate.delete(redisKey);
                log.info("OTP verification successful for destination: {}", request.getDestination());
                responseBuilder.setSuccess(true).setMessage("OTP verified successfully.");
            } else {
                // Code is incorrect or expired
                log.warn("OTP verification failed for destination: {}. Provided code: [{}], Stored code: [{}]",
                        request.getDestination(), request.getCode(), storedCode);
                responseBuilder.setSuccess(false).setMessage("Invalid or expired OTP code.");
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error verifying OTP for destination [{}]: {}", request.getDestination(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to verify OTP code.").asRuntimeException());
        }
    }

    /**
     * Helper method to create a consistent Redis key.
     * Format: "otp:{TYPE}:{DESTINATION}"
     * Example: "otp:REGISTRATION:+994501234567"
     */
    private String buildRedisKey(String destination, OtpType type) {
        return String.format("otp:%s:%s", type.name(), destination);
    }
}
