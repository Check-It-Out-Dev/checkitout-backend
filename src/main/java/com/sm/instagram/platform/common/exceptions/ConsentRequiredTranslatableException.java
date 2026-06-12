package com.sm.instagram.platform.common.exceptions;

import java.io.Serializable;

/**
 * Exception thrown when cookie consent is required but not given.
 * Maps to HTTP 451 (Unavailable For Legal Reasons).
 */
public class ConsentRequiredTranslatableException extends TranslatableException {
    public ConsentRequiredTranslatableException(String messageKey, Serializable... args) {
        super(messageKey, args);
    }
}
