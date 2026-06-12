package com.sm.instagram.platform.registry.port;

/**
 * Port interface for sole proprietor (JDG) data lookup.
 * Primary implementation: CEIDG REST API v2.
 * Only called when GUS BIR1 identifies the entity as a sole proprietorship.
 */
public interface SoleProprietorRegistryPort {

    /**
     * Looks up sole proprietor data by NIP.
     *
     * @param nip 10-digit Polish NIP
     * @return sole proprietor data including owner name
     */
    SoleProprietorData lookupByNip(String nip);
}
