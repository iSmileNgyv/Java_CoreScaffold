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
    private String rawToken;    // Yalnız yaradılan an dolur, sonra null olur
    private String tokenPrefix; // Identifikasiya üçün prefix (Bu sahə çatışmırdı)
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private boolean revoked;
}