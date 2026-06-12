package com.sm.instagram.platform.auth.exceptions;

/**
 * Exception for 2FA-related errors such as Firestore connectivity issues,
 * TOTP verification failures, or 2FA status check failures.
 *
 * This exception is thrown when infrastructure issues prevent proper 2FA
 * status verification, ensuring that the system doesn't incorrectly treat
 * a Firestore failure as "2FA not configured".
 */
public class TwoFactorAuthException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TwoFactorAuthException(String message) {
        super(message);
    }

    public TwoFactorAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
