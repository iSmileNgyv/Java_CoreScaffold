package com.ismile.core.auth.util;

import io.grpc.Context;
import io.grpc.Metadata;
import lombok.experimental.UtilityClass;

/**
 * Utility class for extracting user context from gRPC metadata
 */
@UtilityClass
public class GrpcContextUtil {

    private static final Context.Key<String> USER_ID_KEY = Context.key("user-id");
    private static final Context.Key<String> USERNAME_KEY = Context.key("username");

    /**
     * Extract user ID from gRPC context
     */
    public static Integer getUserId() {
        String userId = USER_ID_KEY.get();
        return userId != null ? Integer.parseInt(userId) : null;
    }

    /**
     * Extract username from gRPC context
     */
    public static String getUsername() {
        return USERNAME_KEY.get();
    }

    /**
     * Extract user ID from metadata headers
     */
    public static Integer getUserIdFromMetadata(Metadata headers) {
        String userId = headers.get(Metadata.Key.of("user-id", Metadata.ASCII_STRING_MARSHALLER));
        return userId != null ? Integer.parseInt(userId) : null;
    }

    /**
     * Extract username from metadata headers
     */
    public static String getUsernameFromMetadata(Metadata headers) {
        return headers.get(Metadata.Key.of("username", Metadata.ASCII_STRING_MARSHALLER));
    }

    /**
     * Extract IP address from metadata headers
     */
    public static String getIpAddress(Metadata headers) {
        // Try to get from X-Forwarded-For first (if behind proxy)
        String ip = headers.get(Metadata.Key.of("x-forwarded-for", Metadata.ASCII_STRING_MARSHALLER));

        if (ip == null || ip.isEmpty()) {
            // Fallback to direct IP
            ip = headers.get(Metadata.Key.of("x-real-ip", Metadata.ASCII_STRING_MARSHALLER));
        }

        return ip != null ? ip : "unknown";
    }

    /**
     * Extract User-Agent from metadata headers
     */
    public static String getUserAgent(Metadata headers) {
        String userAgent = headers.get(Metadata.Key.of("user-agent", Metadata.ASCII_STRING_MARSHALLER));
        return userAgent != null ? userAgent : "unknown";
    }
}