package com.sm.instagram.platform.common.exceptions;

import lombok.Getter;

/**
 * Exception thrown when external service calls fail.
 * Used for Firebase, Instagram API, and other third-party service failures.
 */
@Getter
public class ExternalServiceException extends TranslatableException {
    private final String serviceName;
    private final String operation;
    private final int statusCode;

    public ExternalServiceException(String message, String serviceName) {
        super(message);
        this.serviceName = serviceName;
        this.operation = null;
        this.statusCode = -1;
    }

    public ExternalServiceException(String message, String serviceName, String operation) {
        super(message);
        this.serviceName = serviceName;
        this.operation = operation;
        this.statusCode = -1;
    }

    public ExternalServiceException(String message, String serviceName, String operation, int statusCode) {
        super(message);
        this.serviceName = serviceName;
        this.operation = operation;
        this.statusCode = statusCode;
    }

    public ExternalServiceException(String message, String serviceName, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.operation = null;
        this.statusCode = -1;
    }

    public ExternalServiceException(String message, String serviceName, String operation, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.operation = operation;
        this.statusCode = -1;
    }

}