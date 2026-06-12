package com.sm.instagram.platform.auth.exceptions;

import com.sm.instagram.platform.common.exceptions.BaseException;

/**
 * Exception class for authentication-related errors.
 * Extends the application's BaseException for consistent error handling.
 */
public class AuthException extends BaseException {
    private static final long serialVersionUID = 1L;
    public AuthException(String message) {
        super(message);
    }
}