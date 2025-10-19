package com.ismile.core.notification.dto;

import lombok.Data;

import java.util.UUID;
@Data
public class BaseRequestDto {
    private String recipient;
    private String message;
    private UUID requestId;
}