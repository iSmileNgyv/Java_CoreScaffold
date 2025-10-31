package com.ismile.core.docs.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "docs.security")
@Getter
@Setter
public class DocsSecurityProperties {

    private List<String> publicEndpoints = new ArrayList<>(List.of(
            "grpc.health.v1.Health/Check",
            "grpc.health.v1.Health/Watch"
    ));

    public boolean isPublicEndpoint(String methodName) {
        return publicEndpoints.contains(methodName);
    }
}
