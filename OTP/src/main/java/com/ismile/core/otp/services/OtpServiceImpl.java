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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.kafka.core.KafkaTemplate;
import otp.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class OtpServiceImpl extends OtpServiceGrpc.OtpServiceImplBase {

    private final StringRedisTemplate redisTemplate;
    private final UserSettingsRepository userSettingsRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.otp-prefix}")
    private String otpTopicPrefix;

    private static final int OTP_LENGTH = 6;
    private static final long OTP_TTL_MINUTES = 5;

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
            UserSettingsEntity settings = userSettingsRepository.findByUserId(request.getUserId())
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("User settings not found. Cannot determine delivery method.")
                            .asRuntimeException());

            DeliveryMethod deliveryMethod = settings.getDeliveryMethod();

            String code = RandomStringUtils.randomNumeric(OTP_LENGTH);
            String redisKey = buildRedisKey(request.getUserId(), request.getType());

            redisTemplate.opsForValue().set(redisKey, code, OTP_TTL_MINUTES, TimeUnit.MINUTES);

            log.info("Generated OTP for User ID [{}]. Type: [{}], Code: [{}], Delivery: [{}], Key: [{}]",
                    request.getUserId(), request.getType(), code, deliveryMethod.name(), redisKey);

            // Dynamically create the topic name, e.g., "otp_email"
            String topicName = otpTopicPrefix + "_" + deliveryMethod.name().toLowerCase();

            // Prepare the message body as requested
            String messageBody = String.format("Your verification code for %s is: %s", request.getType().name(), code);

            // Prepare the Kafka message payload
            Map<String, Object> kafkaMessage = new HashMap<>();
            kafkaMessage.put("userId", request.getUserId());
            kafkaMessage.put("messageBody", messageBody);
            // Add subject for emails
            if(deliveryMethod == DeliveryMethod.EMAIL) {
                kafkaMessage.put("subject", "Your One-Time Password");
            }

            // Send the message to the specific topic
            kafkaTemplate.send(topicName, kafkaMessage);
            log.info("Sent OTP notification for user {} to Kafka topic '{}'", request.getUserId(), topicName);

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
            log.error("Internal Error sending OTP for User ID [{}]: {}", request.getUserId(), e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to process OTP request.").withCause(e).asRuntimeException());
        }
    }

    @Override
    public void verifyCode(VerifyCodeRequest request, StreamObserver<VerifyCodeResponse> responseObserver) {
        if (request.getUserId() <= 0 || request.getCode().trim().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("User ID and code cannot be empty.").asRuntimeException());
            return;
        }

        try {
            String redisKey = buildRedisKey(request.getUserId(), request.getType());
            String storedCode = redisTemplate.opsForValue().get(redisKey);

            if (storedCode != null && storedCode.equals(request.getCode())) {
                redisTemplate.delete(redisKey);
                log.info("OTP verification successful for User ID: {}", request.getUserId());
                responseObserver.onNext(VerifyCodeResponse.newBuilder().setSuccess(true).setMessage("OTP verified successfully.").build());
                responseObserver.onCompleted();
            } else {
                log.warn("OTP verification failed for User ID: {}. Provided code: [{}], Stored code: [{}]",
                        request.getUserId(), request.getCode(), storedCode);
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid or expired otp code").asRuntimeException());
            }
        } catch (Exception e) {
            log.error("Error verifying OTP for User ID [{}]: {}", request.getUserId(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to verify OTP code.").asRuntimeException());
        }
    }

    private String buildRedisKey(int userId, OtpType type) {
        return String.format("otp:%s:%d", type.name(), userId);
    }
}
