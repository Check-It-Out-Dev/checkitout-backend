package com.sm.instagram.platform.auth.exceptions;

import com.sm.instagram.platform.common.exceptions.BaseException;

/**
 * Exception thrown when social media connection operations fail.
 * Used for platform-specific errors, token validation issues, and API failures.
 */
public class SocialConnectionException extends BaseException {
    private static final long serialVersionUID = 1L;
    
    private final String platformName;
    private final String connectionId;

    public SocialConnectionException(String message) {
        super(message);
        this.platformName = null;
        this.connectionId = null;
    }

    public SocialConnectionException(String message, String platformName) {
        super(message);
        this.platformName = platformName;
        this.connectionId = null;
    }

    public SocialConnectionException(String message, String platformName, String connectionId) {
        super(message);
        this.platformName = platformName;
        this.connectionId = connectionId;
    }

    public SocialConnectionException(String message, Throwable cause) {
        super(message, cause);
        this.platformName = null;
        this.connectionId = null;
    }

    public SocialConnectionException(String message, String platformName, Throwable cause) {
        super(message, cause);
        this.platformName = platformName;
        this.connectionId = null;
    }

    public String getPlatformName() {
        return platformName;
    }

    public String getConnectionId() {
        return connectionId;
    }
}