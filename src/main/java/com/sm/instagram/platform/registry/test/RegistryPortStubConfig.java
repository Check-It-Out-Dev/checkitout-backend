package com.sm.instagram.platform.registry.test;

import com.sm.instagram.platform.registry.port.CompanyRegistryPort;
import com.sm.instagram.platform.registry.port.SoleProprietorRegistryPort;
import com.sm.instagram.platform.registry.port.VatRegistryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Provides {@code @Primary} stub implementations of registry ports for E2E tests.
 * Each bean delegates to {@link RegistryStubState} which is configured via
 * {@link TestRegistryController} REST endpoints.
 *
 * <p><b>SECURITY:</b> Only active in the {@code e2e} and {@code dev-lite}
 * (credential-less simulator) profiles. In production, the real adapters
 * (GUS SOAP, Biała Lista REST, CEIDG REST) are used.
 */
@Configuration
@Profile("(e2e | dev-lite) & !prod & !test")
public class RegistryPortStubConfig {

    @Bean
    @Primary
    public CompanyRegistryPort companyRegistryPort(RegistryStubState state) {
        return state::getGusResponse;
    }

    @Bean
    @Primary
    public VatRegistryPort vatRegistryPort(RegistryStubState state) {
        return state::getVatResponse;
    }

    @Bean
    @Primary
    public SoleProprietorRegistryPort soleProprietorRegistryPort(RegistryStubState state) {
        return state::getCeidgResponse;
    }
}
