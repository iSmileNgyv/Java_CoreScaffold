package com.ismile.core.chronovcs.exception;

public class PullRequestNotFoundException extends RuntimeException {
    public PullRequestNotFoundException(Long id) {
        super("Pull request not found: " + id);
    }
}
