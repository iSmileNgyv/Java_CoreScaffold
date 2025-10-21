package com.ismile.core.notification.config;

import com.ismile.core.notification.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Interceptor for WebSocket handshakes to perform JWT-based authentication.
 * It extracts the token from the query parameters of the connection URL.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    /**
     * This method is called before the handshake is processed.
     * It extracts the JWT token from the "token" query parameter, validates it,
     * and extracts the user ID. The user ID is then placed into the WebSocket
     * session attributes for later use.
     * @param request the current request
     * @param response the current response
     * @param wsHandler the target WebSocket handler
     * @param attributes the attributes from the HTTP handshake to associate with the WebSocket session
     * @return true if the handshake should proceed, false otherwise.
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            log.debug("Starting WebSocket handshake. URI: {}", request.getURI());
            String token = UriComponentsBuilder.fromUri(request.getURI())
                    .build()
                    .getQueryParams()
                    .getFirst("token");

            if (token == null || token.trim().isEmpty()) {
                log.warn("Handshake failed: Token is missing from query parameters.");
                return false;
            }

            if (jwtUtil.validateToken(token)) {
                Claims claims = jwtUtil.extractClaims(token);
                String userId = claims.getSubject();
                log.info("Handshake successful for user ID: {}", userId);
                // Put the user ID in the session attributes to identify the user later
                attributes.put("userId", userId);
                return true;
            }
        } catch (Exception e) {
            log.error("Handshake failed due to an exception: {}", e.getMessage());
        }

        log.warn("Handshake failed: Invalid or expired token.");
        return false;
    }

    /**
     * This method is called after the handshake is completed.
     * It's a good place for logging or resource cleanup if needed.
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("Exception after handshake: {}", exception.getMessage());
        } else {
            log.debug("Handshake completed successfully.");
        }
    }
}
