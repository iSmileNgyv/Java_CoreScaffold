package com.ismile.core.chronovcs.exception;

public class RepositoryNotFoundException extends RuntimeException {
    public RepositoryNotFoundException(String message) {
        super("Repository not found: " + message);
    }
}
