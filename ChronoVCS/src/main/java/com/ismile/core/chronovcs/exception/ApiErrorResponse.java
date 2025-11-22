package com.ismile.core.chronovcs.exception;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ApiErrorResponse {

    private boolean success;
    private String errorCode;
    private String message;
    private String path;
    private Instant timestamp;
}