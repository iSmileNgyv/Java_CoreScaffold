package com.ismile.core.chronovcscli.common;

import lombok.Data;

@Data
public class ApiErrorResponse {
    private boolean success;
    private String errorCode;
    private String message;
}
