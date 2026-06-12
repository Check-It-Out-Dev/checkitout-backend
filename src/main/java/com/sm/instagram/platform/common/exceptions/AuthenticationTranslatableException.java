package com.sm.instagram.platform.common.exceptions;

import java.io.Serializable;

/**
 * Exception for authentication and authorization errors.
 * <p>
 * Use this exception when authentication fails, tokens are invalid,
 * credentials are incorrect, or authorization checks fail.
 *
 * @example throw new AuthenticationTranslatableException("error.auth.invalid_credentials");
 * @example throw new AuthenticationTranslatableException("error.auth.token_expired");
 * @example throw new AuthenticationTranslatableException("error.auth.insufficient_permissions", "admin");
 */
public class AuthenticationTranslatableException extends TranslatableException {
    /**
     * Creates a new authentication exception.
     *
     * @param messageKey The i18n message key (e.g., "error.auth.invalid_token")
     * @param args       Optional parameters for message formatting
     */
    public AuthenticationTranslatableException(String messageKey, Serializable... args) {
        super(messageKey, args);
    }
}
