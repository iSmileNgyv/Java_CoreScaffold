package com.ismile.core.notification.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties that drive API key enforcement for the Notification gRPC service.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification.security")
public class NotificationSecurityProperties {

    /**
     * API keys that are allowed to invoke protected gRPC endpoints.
     */
    private List<String> allowedApiKeys = new ArrayList<>();

    /**
     * gRPC method names that are accessible without presenting an API key.
     *
     * Defaults include the standard gRPC health checks so that orchestration systems can probe the service.
     */
    private List<String> publicEndpoints = new ArrayList<>(List.of(
            "grpc.health.v1.Health/Check",
            "grpc.health.v1.Health/Watch"
    ));

    public boolean isApiKeyAllowed(String apiKey) {
        if (apiKey == null) {
            return false;
        }
        String candidate = apiKey.trim();
        if (candidate.isEmpty()) {
            return false;
        }
        return allowedApiKeys.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .anyMatch(candidate::equals);
    }

    public boolean isPublicEndpoint(String methodName) {
        return publicEndpoints.contains(methodName);
    }
}
