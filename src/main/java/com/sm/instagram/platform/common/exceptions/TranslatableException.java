package com.sm.instagram.platform.common.exceptions;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * Base exception for all translatable exceptions in the application.
 * Carries a message key and optional parameters for i18n support.
 * <p>
 * This exception should be used when you need to throw an error that will be
 * translated to the user's locale using MessageSource and properties files.
 *
 * @example throw new TranslatableException("error.auth.invalid_token");
 * @example throw new TranslatableException("error.validation.missing_parameter", "email");
 */
@Getter
public class TranslatableException extends RuntimeException implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * -- GETTER --
     * Gets the i18n message key.
     *
     * @return The message key for translation
     */
    private final String messageKey;
    /**
     * -- GETTER --
     * Gets the message parameters.
     *
     * @return Array of parameters for message formatting
     */
    private final Serializable[] args;

    /**
     * Creates a new translatable exception.
     *
     * @param messageKey The i18n message key (e.g., "error.auth.token_expired")
     * @param args       Optional parameters for message formatting
     */
    public TranslatableException(String messageKey, Serializable... args) {
        super(messageKey); // Fallback to key if translation fails
        this.messageKey = messageKey;
        this.args = args != null ? args : new Serializable[0];
    }
//
//    public TranslatableException(String messageKey, String... args) {
//        super(messageKey); // Fallback to key if translation fails
//        this.messageKey = messageKey;
//        this.args = args != null ? args : new Serializable[0];
//    }

}
