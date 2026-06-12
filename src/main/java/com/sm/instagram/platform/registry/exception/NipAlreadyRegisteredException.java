package com.sm.instagram.platform.registry.exception;

import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;

/**
 * Thrown when a NIP is already claimed by another company in the platform.
 * Handled as HTTP 409 by the existing exception handlers.
 */
public class NipAlreadyRegisteredException extends BusinessRuleTranslatableException {

    public NipAlreadyRegisteredException(String nip) {
        super("error.registry.nip_already_registered", nip);
    }
}
