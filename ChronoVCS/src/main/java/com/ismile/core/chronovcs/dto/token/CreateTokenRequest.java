package com.ismile.core.chronovcs.dto.token;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTokenRequest {
    private String tokenName;
    private Integer expiresInDays; // null = never expires
    private String[] scopes; // optional, for future
}