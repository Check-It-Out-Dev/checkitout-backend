package com.sm.instagram.platform.common.exceptions;

import java.io.Serializable;

/**
 * Exception for validation errors.
 * <p>
 * Use this exception when input validation fails, required fields are missing,
 * format is incorrect, or constraints are violated.
 *
 * @example throw new ValidationTranslatableException("error.validation.required_field", "email");
 * @example throw new ValidationTranslatableException("error.validation.invalid_format", "phone");
 * @example throw new ValidationTranslatableException("error.validation.max_length_exceeded", "username", 50);
 */
public class ValidationTranslatableException extends TranslatableException {
    /**
     * Creates a new validation exception.
     *
     * @param messageKey The i18n message key (e.g., "error.validation.invalid_email")
     * @param args       Optional parameters for message formatting
     */
    public ValidationTranslatableException(String messageKey, Serializable... args) {
        super(messageKey, args);
    }
}
