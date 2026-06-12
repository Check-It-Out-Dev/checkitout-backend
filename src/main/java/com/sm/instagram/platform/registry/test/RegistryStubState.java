package com.sm.instagram.platform.registry.test;

import com.sm.instagram.platform.registry.port.CompanyRegistryData;
import com.sm.instagram.platform.registry.port.SoleProprietorData;
import com.sm.instagram.platform.registry.port.VatStatusData;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe state holder for registry port stubs in E2E tests.
 * Configurable via {@link TestRegistryController} REST endpoints.
 *
 * <p>Unconfigured NIPs default to "not found" responses.
 *
 * <p><b>SECURITY:</b> Only active in {@code e2e} profile.
 */
@Component
@Profile("e2e & !prod & !test")
public class RegistryStubState {

    private final ConcurrentHashMap<String, CompanyRegistryData> gusResponses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VatStatusData> vatResponses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SoleProprietorData> ceidgResponses = new ConcurrentHashMap<>();

    public CompanyRegistryData getGusResponse(String nip) {
        return gusResponses.getOrDefault(nip, CompanyRegistryData.builder().found(false).build());
    }

    public VatStatusData getVatResponse(String nip) {
        return vatResponses.getOrDefault(nip, VatStatusData.builder().found(false).build());
    }

    public SoleProprietorData getCeidgResponse(String nip) {
        return ceidgResponses.getOrDefault(nip, SoleProprietorData.builder().found(false).build());
    }

    public void putGus(String nip, CompanyRegistryData data) {
        gusResponses.put(nip, data);
    }

    public void putVat(String nip, VatStatusData data) {
        vatResponses.put(nip, data);
    }

    public void putCeidg(String nip, SoleProprietorData data) {
        ceidgResponses.put(nip, data);
    }

    public void clear() {
        gusResponses.clear();
        vatResponses.clear();
        ceidgResponses.clear();
    }
}
