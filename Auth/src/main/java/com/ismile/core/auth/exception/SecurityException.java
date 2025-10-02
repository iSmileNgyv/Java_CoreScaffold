package com.ismile.core.auth.exception;

/**
 * Custom security exception for authentication/authorization errors
 */
public class SecurityException extends RuntimeException {

    public SecurityException(String message) {
        super(message);
    }

    public SecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}