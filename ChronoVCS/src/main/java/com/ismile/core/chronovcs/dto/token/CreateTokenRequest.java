package com.ismile.core.chronovcs.dto.token;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTokenRequest {

    @NotBlank(message = "Token name is required")
    @Size(min = 3, max = 100, message = "Token name must be between 3 and 100 characters")
    private String tokenName;

    @Min(value = 1, message = "Expiration must be at least 1 day")
    @Max(value = 3650, message = "Expiration cannot exceed 3650 days (10 years)")
    private Integer expiresInDays; // null = unlimited
}