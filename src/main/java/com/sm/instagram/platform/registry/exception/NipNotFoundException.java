package com.sm.instagram.platform.registry.exception;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;

import java.io.Serializable;

/**
 * Thrown when a NIP is not found in any Polish public registry.
 * Handled as HTTP 404 by the existing exception handlers.
 */
public class NipNotFoundException extends ResourceNotFoundException {

    public NipNotFoundException(String nip) {
        super("error.registry.nip_not_found", nip);
    }

    public NipNotFoundException(String messageKey, Serializable... args) {
        super(messageKey, args);
    }
}
