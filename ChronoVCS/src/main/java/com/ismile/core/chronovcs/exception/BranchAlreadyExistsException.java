package com.ismile.core.chronovcs.exception;

public class BranchAlreadyExistsException extends RuntimeException {
    public BranchAlreadyExistsException(String branchName) {
        super("Branch already exists: " + branchName);
    }
}
