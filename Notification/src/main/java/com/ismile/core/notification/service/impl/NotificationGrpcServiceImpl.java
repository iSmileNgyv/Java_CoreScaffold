package com.ismile.core.notification.service.impl;

import com.ismile.core.notification.dto.email.EmailNotificationRequestDto;
import com.ismile.core.notification.entity.DeliveryMethod;
import com.ismile.core.notification.entity.UserSettingsEntity;
import com.ismile.core.notification.factory.NotificationFactory;
import com.ismile.core.notification.repository.UserSettingsRepository;
import com.ismile.core.notification.service.NotificationService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification.NotificationGrpcServiceGrpc;
import notification.SendNotificationRequest;
import notification.SendNotificationResponse;
import org.springframework.grpc.server.service.GrpcService;

import java.util.UUID;

/**
 * gRPC service implementation for handling notification requests.
 * This class is the main entry point for incoming gRPC calls to the Notification service.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class NotificationGrpcServiceImpl extends NotificationGrpcServiceGrpc.NotificationGrpcServiceImplBase {

    private final NotificationFactory notificationFactory;
    private final UserSettingsRepository userSettingsRepository;

    /**
     * Handles the SendNotification RPC call.
     * It uses the NotificationFactory to delegate the request to the appropriate service
     * based on the specified delivery method.
     *
     * @param request          The gRPC request containing notification details.
     * @param responseObserver The observer to send the response back to the client.
     */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void sendNotification(SendNotificationRequest request, StreamObserver<SendNotificationResponse> responseObserver) {
        log.info("Received gRPC notification request for user_id: {}", request.getUserId());

        try {
            // Step 1: Convert protobuf enum to Java enum
            DeliveryMethod deliveryMethod = DeliveryMethod.valueOf(request.getDeliveryMethod().name());

            // Step 2: Get the appropriate service from the factory
            NotificationService notificationService = notificationFactory.getService(deliveryMethod)
                    .orElseThrow(() -> Status.INVALID_ARGUMENT
                            .withDescription("Unsupported delivery method: " + deliveryMethod.name())
                            .asRuntimeException());

            // Step 3: Fetch user settings to get the recipient address (e.g., email)
            UserSettingsEntity userSettings = userSettingsRepository.findByUserIdAndDeliveryMethod(request.getUserId(), deliveryMethod)
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("User settings not found for the given user_id and delivery method.")
                            .asRuntimeException());

            // Step 4: Prepare the specific DTO for the concrete service implementation
            // and call the send method. This part uses a condition to create the correct DTO.
            if (deliveryMethod == DeliveryMethod.EMAIL) {
                EmailNotificationRequestDto emailRequest = new EmailNotificationRequestDto();
                emailRequest.setRecipient(userSettings.getRecipient());
                emailRequest.setMessage(request.getMessageBody());
                emailRequest.setSubject(request.getSubject());
                emailRequest.setRequestId(UUID.randomUUID());

                // Call the send method of the specific service (EmailNotificationServiceImpl)
                notificationService.send(emailRequest);
            } else {
                // This block can be extended for other delivery methods like SMS in the future.
                throw Status.UNIMPLEMENTED
                        .withDescription("Delivery method " + deliveryMethod.name() + " is not yet implemented.")
                        .asRuntimeException();
            }

            // Step 5: Send a success response to the client.
            SendNotificationResponse response = SendNotificationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Notification request accepted and is being processed.")
                    .setRequestId(UUID.randomUUID().toString()) // Provide a unique ID for tracking
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Failed to process notification for user_id {}: {}", request.getUserId(), e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("An internal error occurred: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}
