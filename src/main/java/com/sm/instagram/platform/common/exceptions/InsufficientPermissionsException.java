package com.sm.instagram.platform.common.exceptions;

import lombok.Getter;

/**
 * Exception thrown when a user lacks sufficient permissions for an operation.
 * Used for domain-specific authorization failures beyond Spring Security's AccessDeniedException.
 */
@Getter
public class InsufficientPermissionsException extends TranslatableException {
    private final String userId;
    private final String operation;
    private final String resource;
    private final String requiredPermission;

    public InsufficientPermissionsException(String message) {
        super(message);
        this.userId = null;
        this.operation = null;
        this.resource = null;
        this.requiredPermission = null;
    }

    public InsufficientPermissionsException(String message, String userId, String operation) {
        super(message);
        this.userId = userId;
        this.operation = operation;
        this.resource = null;
        this.requiredPermission = null;
    }

    public InsufficientPermissionsException(String message, String userId, String operation, String resource) {
        super(message);
        this.userId = userId;
        this.operation = operation;
        this.resource = resource;
        this.requiredPermission = null;
    }

    public InsufficientPermissionsException(String message, String userId, String operation, String resource, String requiredPermission) {
        super(message);
        this.userId = userId;
        this.operation = operation;
        this.resource = resource;
        this.requiredPermission = requiredPermission;
    }

}