package com.ismile.core.notification.service.impl;

import com.ismile.core.notification.entity.DeliveryMethod;
import com.ismile.core.notification.factory.NotificationFactory;
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
 * It is fully decoupled from the implementation details of how a notification is sent,
 * using the NotificationFactory to delegate the entire processing of the gRPC request.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class NotificationGrpcServiceImpl extends NotificationGrpcServiceGrpc.NotificationGrpcServiceImplBase {

    private final NotificationFactory notificationFactory;

    /**
     * Handles the SendNotification RPC call.
     * This method finds the correct service using the factory and tells that service
     * to process the request. It no longer needs to know about specific DTOs or logic,
     * adhering to the Open/Closed Principle.
     *
     * @param request          The gRPC request containing notification details.
     * @param responseObserver The observer to send the response back to the client.
     */
    @Override
    @SuppressWarnings({"rawtypes"})
    public void sendNotification(SendNotificationRequest request, StreamObserver<SendNotificationResponse> responseObserver) {
        log.info("Received gRPC notification request for user_id: {} via {}", request.getUserId(), request.getDeliveryMethod());

        try {
            // Step 1: Convert protobuf enum to Java enum
            DeliveryMethod deliveryMethod = DeliveryMethod.valueOf(request.getDeliveryMethod().name());

            // Step 2: Get the appropriate service from the factory
            NotificationService notificationService = notificationFactory.getService(deliveryMethod)
                    .orElseThrow(() -> Status.INVALID_ARGUMENT
                            .withDescription("Unsupported delivery method: " + deliveryMethod.name())
                            .asRuntimeException());

            // Step 3: Delegate the entire request processing to the concrete service.
            // The specific service (e.g., EmailNotificationServiceImpl) will handle
            // creating its own DTO and sending the notification.
            notificationService.processGrpcRequest(request);

            // Step 4: Send a success response to the client.
            SendNotificationResponse response = SendNotificationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Notification request accepted and is being processed via " + deliveryMethod.name())
                    .setRequestId(UUID.randomUUID().toString())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Failed to process notification for user_id {}: {}", request.getUserId(), e.getMessage(), e);

            // Propagate gRPC-specific exceptions directly
            if (e instanceof io.grpc.StatusRuntimeException) {
                responseObserver.onError(e);
            } else {
                // Wrap other exceptions in a standard INTERNAL status
                responseObserver.onError(Status.INTERNAL
                        .withDescription("An internal error occurred: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        }
    }
}

