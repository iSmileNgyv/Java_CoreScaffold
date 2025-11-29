package com.ismile.core.chronovcs.dto.token;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    private Long id;
    private String tokenName;
    private String token; // Only returned when creating, null otherwise
    private String tokenPrefix; // Visible prefix for identification
    private String[] scopes;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private String lastUsedIp;
    private boolean revoked;
    private LocalDateTime createdAt;
}