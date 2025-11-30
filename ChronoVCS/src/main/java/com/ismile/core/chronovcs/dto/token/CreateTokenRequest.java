package com.ismile.core.chronovcs.dto.token;

import lombok.Data;

@Data
public class CreateTokenRequest {
    private String tokenName;      // Məs: "My Laptop"
    private Integer expiresInDays; // null = limitsiz
}