package com.ismile.argusomnicli.model;

/**
 * Enumeration of supported test step types.
 * Follows Single Responsibility - only defines step types.
 */
public enum StepType {
    REST,
    GRPC,
    FS,
    BASH,
    RESOLVE_PATH,
    SET,
    TRANSFORM,
    ASSERT,
    WAIT,
    LOOP,
    IF,
    MOCK
}
