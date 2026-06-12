package com.sm.instagram.platform.common.email.validation;

/**
 * Thrown when SMTP configuration validation fails.
 */
public class SmtpValidationException extends RuntimeException {

    public SmtpValidationException(String message) {
        super(message);
    }

    public SmtpValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
