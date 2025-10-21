package com.ismile.core.notification.service.impl;

import com.ismile.core.notification.dto.websocket.WebSocketNotificationRequestDto;
import com.ismile.core.notification.dto.websocket.WebSocketNotificationResponseDto;
import com.ismile.core.notification.entity.DeliveryMethod;
import com.ismile.core.notification.handler.NotificationWebSocketHandler;
import com.ismile.core.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification.SendNotificationRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * NotificationService implementation for sending notifications via WebSocket.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketNotificationServiceImpl implements NotificationService<WebSocketNotificationRequestDto, WebSocketNotificationResponseDto> {

    private final NotificationWebSocketHandler webSocketHandler;

    /**
     * Sends a notification to a user via WebSocket.
     * The request DTO should contain the user ID in the 'recipient' field.
     * @param request The request containing the message and recipient user ID.
     * @return A response indicating the status and number of sessions the message was delivered to.
     */
    @Override
    public WebSocketNotificationResponseDto send(WebSocketNotificationRequestDto request) {
        log.info("Attempting to send WebSocket notification to user ID: {}", request.getRecipient());
        var response = new WebSocketNotificationResponseDto();
        try {
            int deliveredCount = webSocketHandler.sendMessageToUser(request.getRecipient(), request.getMessage());
            response.setStatus(deliveredCount > 0 ? "DELIVERED" : "NO_ACTIVE_SESSIONS");
            response.setDeliveredSessions(deliveredCount);
        } catch (Exception e) {
            log.error("Failed to send WebSocket message to user ID: {}", request.getRecipient(), e);
            response.setStatus("FAILED");
            response.setDeliveredSessions(0);
        }
        return response;
    }

    /**
     * Returns the delivery method handled by this service.
     * @return DeliveryMethod.WEBSOCKET
     */
    @Override
    public DeliveryMethod getDeliveryMethod() {
        return DeliveryMethod.WEBSOCKET;
    }

    @Override
    public void processGrpcRequest(SendNotificationRequest grpcRequest) {
        log.info("Processing gRPC request for WEBSOCKET to user_id: {}", grpcRequest.getUserId());

        // For WebSocket, the recipient IS the user ID. No database lookup is needed here.
        WebSocketNotificationRequestDto wsRequest = new WebSocketNotificationRequestDto();
        wsRequest.setRecipient(String.valueOf(grpcRequest.getUserId()));
        wsRequest.setMessage(grpcRequest.getMessageBody());
        wsRequest.setRequestId(UUID.randomUUID());

        // Call the main send logic with the prepared DTO
        this.send(wsRequest);
    }
}
