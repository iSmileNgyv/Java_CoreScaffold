package com.ismile.core.otp.services;

import com.ismile.core.otp.entity.DeliveryMethod;
import com.ismile.core.otp.entity.UserSettingsEntity;
import com.ismile.core.otp.repository.UserSettingsRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
    private final UserSettingsRepository userSettingsRepository;

    private static final int OTP_LENGTH = 6; // Length of the OTP code
    private static final long OTP_TTL_MINUTES = 5; // Time-to-live for the OTP in minutes

    /**
     * Handles the SendCode RPC call.
     * It fetches the user's delivery preference, generates a numeric OTP, stores it in Redis,
     * and then prepares the data for the notification service.
     */
    @Override
    public void sendCode(SendCodeRequest request, StreamObserver<SendCodeResponse> responseObserver) {
        if (request.getUserId() <= 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("User ID must be a positive integer.").asRuntimeException());
            return;
        }

        if (request.getType() == OtpType.UNKNOWN) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("OTP type cannot be UNKNOWN.").asRuntimeException());
            return;
        }

        try {
            // Step 1: Fetch user's delivery settings from the database
            UserSettingsEntity settings = userSettingsRepository.findByUserId(request.getUserId())
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("User settings not found. Cannot determine delivery method.")
                            .asRuntimeException());

            DeliveryMethod deliveryMethod = settings.getDeliveryMethod();

            // Step 2: Generate a random 6-digit numeric code
            String code = RandomStringUtils.randomNumeric(OTP_LENGTH);
            String redisKey = buildRedisKey(request.getUserId(), request.getType());

            // Step 3: Store the code in Redis with a 5-minute expiration
            redisTemplate.opsForValue().set(redisKey, code, OTP_TTL_MINUTES, TimeUnit.MINUTES);

            log.info("Generated OTP for User ID [{}]. Type: [{}], Code: [{}], Delivery: [{}], Key: [{}]",
                    request.getUserId(), request.getType(), code, deliveryMethod.name(), redisKey);


            // Step 4: Send a message to Kafka for the Notification service to handle.
            // This decouples the OTP generation from the actual sending (email, sms, etc).
            // The message would contain: userId, code, deliveryMethod, otpType.
            //
            // Example Kafka Producer call (logic to be implemented in a separate Kafka producer class):
            // kafkaProducer.sendOtpNotification(request.getUserId(), code, deliveryMethod, request.getType());
            //
            log.info("Placeholder: A message should be sent to Kafka topic 'otp_notifications' here.");


            String requestId = UUID.randomUUID().toString();
            SendCodeResponse response = SendCodeResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("OTP generation request accepted. Notification will be sent via " + deliveryMethod.name())
                    .setRequestId(requestId)
                    .setDeliveryMethod(deliveryMethod.name())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (StatusRuntimeException e) {
            log.error("gRPC Error sending OTP for User ID [{}]: {}", request.getUserId(), e.getStatus().getDescription());
            responseObserver.onError(e);
        } catch (Exception e) {
            log.error("Internal Error sending OTP for User ID [{}]: {}", request.getUserId(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to process OTP request.").withCause(e).asRuntimeException());
        }
    }

    /**
     * Handles the VerifyCode RPC call.
     * Checks the provided code against the one stored in Redis for the given user ID.
     */
    @Override
    public void verifyCode(VerifyCodeRequest request, StreamObserver<VerifyCodeResponse> responseObserver) {
        if (request.getUserId() <= 0 || request.getCode().trim().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("User ID and code cannot be empty.").asRuntimeException());
            return;
        }

        try {
            String redisKey = buildRedisKey(request.getUserId(), request.getType());
            String storedCode = redisTemplate.opsForValue().get(redisKey);

            VerifyCodeResponse.Builder responseBuilder = VerifyCodeResponse.newBuilder();

            if (storedCode != null && storedCode.equals(request.getCode())) {
                // Successful verification, delete the key to prevent reuse
                redisTemplate.delete(redisKey);
                log.info("OTP verification successful for User ID: {}", request.getUserId());
                responseBuilder.setSuccess(true).setMessage("OTP verified successfully.");
            } else {
                // Code is incorrect or expired
                log.warn("OTP verification failed for User ID: {}. Provided code: [{}], Stored code: [{}]",
                        request.getUserId(), request.getCode(), storedCode);
                //responseBuilder.setSuccess(false).setMessage("Invalid or expired OTP code.");
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid or expired otp code").asRuntimeException());
                return;
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error verifying OTP for User ID [{}]: {}", request.getUserId(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to verify OTP code.").asRuntimeException());
        }
    }

    /**
     * Helper method to create a consistent Redis key.
     * Format: "otp:{TYPE}:{USER_ID}"
     * Example: "otp:LOGIN:123"
     */
    private String buildRedisKey(int userId, OtpType type) {
        return String.format("otp:%s:%d", type.name(), userId);
    }
}

