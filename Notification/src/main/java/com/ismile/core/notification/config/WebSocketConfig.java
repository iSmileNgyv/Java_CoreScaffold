package com.ismile.core.notification.config;

import com.ismile.core.notification.handler.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket Configuration
 * Enables WebSocket and registers the handler with its endpoint and interceptor.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    /**
     * Registers the WebSocket handler to a specific path ("/ws") and adds a handshake interceptor.
     * The interceptor is used for authenticating users via JWT before establishing the connection.
     * `setAllowedOrigins("*")` allows connections from any origin, which is suitable for development.
     * For production, you should restrict this to your frontend's domain.
     * @param registry The WebSocket handler registry.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
