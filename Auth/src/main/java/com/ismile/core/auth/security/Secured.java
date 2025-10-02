package com.ismile.core.auth.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark gRPC methods that require authentication
 * Future use: Can be extended to support role-based access control
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Secured {

    /**
     * Required roles to access this endpoint
     */
    String[] roles() default {};

    /**
     * Allow access even if account is locked (for unlock operations)
     */
    boolean allowLocked() default false;
}