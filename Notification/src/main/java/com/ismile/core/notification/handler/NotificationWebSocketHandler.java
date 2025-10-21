package com.ismile.core.notification.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles WebSocket connections and messages.
 * Manages active sessions to allow broadcasting messages to specific users.
 */
@Component
@Slf4j
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    // A thread-safe map to store sessions. Key is userId (String), Value is a list of sessions for that user.
    // A list is used because a user might be connected from multiple devices/tabs.
    private final Map<String, List<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /**
     * Called after a WebSocket connection is established.
     * It retrieves the userId set by the JwtHandshakeInterceptor and stores the session.
     * @param session The new WebSocket session.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessions.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(session);
            log.info("WebSocket connection established for user ID: {}. Total sessions for user: {}", userId, sessions.get(userId).size());
        } else {
            log.warn("Connection established but no user ID found in session attributes. Closing session.");
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("User ID not found"));
            } catch (IOException e) {
                log.error("Error closing session without user ID.", e);
            }
        }
    }

    /**
     * Called after a WebSocket connection is closed.
     * Removes the session from the stored map.
     * @param session The closed WebSocket session.
     * @param status The close status.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            List<WebSocketSession> userSessions = sessions.get(userId);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) {
                    sessions.remove(userId);
                }
                log.info("WebSocket connection closed for user ID: {}. Reason: {}. Remaining sessions for user: {}", userId, status, userSessions.size());
            }
        }
    }

    /**
     * Handles incoming text messages from clients.
     * For a notification service, this is typically used for things like "ping/pong" or acknowledgements.
     * Here, we just log the incoming message.
     * @param session The session that sent the message.
     * @param message The received message.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = (String) session.getAttributes().get("userId");
        log.info("Received message from user ID {}: {}", userId, message.getPayload());
        // Here you could implement logic to handle client messages, e.g., ACKs.
    }

    /**
     * Sends a message to all active sessions for a specific user.
     * @param userId The ID of the target user.
     * @param message The message to send.
     * @return The number of sessions the message was sent to.
     */
    public int sendMessageToUser(String userId, String message) {
        List<WebSocketSession> userSessions = sessions.get(userId);
        int sessionCount = 0;
        if (userSessions != null && !userSessions.isEmpty()) {
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : userSessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                        sessionCount++;
                    }
                } catch (IOException e) {
                    log.error("Failed to send message to user ID {}: session ID {}", userId, session.getId(), e);
                }
            }
        }
        log.info("Sent message to {} session(s) for user ID: {}", sessionCount, userId);
        return sessionCount;
    }
}
