package com.sm.instagram.platform.registry.port;

/**
 * Port interface for VAT taxpayer status verification.
 * Primary implementation: Ministry of Finance Biała Lista (White List) REST API.
 */
public interface VatRegistryPort {

    /**
     * Looks up VAT status for a given NIP.
     *
     * @param nip 10-digit Polish NIP
     * @return VAT status data including registered bank accounts
     */
    VatStatusData lookupVatStatus(String nip);
}
