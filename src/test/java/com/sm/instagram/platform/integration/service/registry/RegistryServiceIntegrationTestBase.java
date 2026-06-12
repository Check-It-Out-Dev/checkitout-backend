package com.sm.instagram.platform.integration.service.registry;

import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.registry.*;
import com.sm.instagram.platform.registry.dto.CompanyDataConfirmRequest;
import com.sm.instagram.platform.registry.port.*;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Base class for RegistryLookupService integration tests.
 * Provides mocked external registry ports, helper methods, and shared fixtures.
 *
 * <p>External APIs are mocked because they make network calls to Polish government registries.
 * Everything else is real: Spring context, PostgreSQL (TestContainers), Liquibase, JPA, etc.
 */
public abstract class RegistryServiceIntegrationTestBase extends BaseServiceIntegrationTest {

    @Autowired
    protected RegistryLookupService registryLookupService;

    @Autowired
    protected CompanyDataRepository companyDataRepository;

    @PersistenceContext
    protected EntityManager entityManager;

    // Mock external registry ports — these make real HTTP/SOAP calls to government APIs
    @MockBean
    protected CompanyRegistryPort companyRegistryPort;

    @MockBean
    protected VatRegistryPort vatRegistryPort;

    @MockBean
    protected SoleProprietorRegistryPort soleProprietorRegistryPort;

    protected static final String TEST_NIP = "5261040828";
    protected static final String TEST_NIP_2 = "7740001454";

    @BeforeEach
    void setUpDefaultMockResponses() {
        // Default: GUS returns a valid KRS company
        lenient().when(companyRegistryPort.lookupByNip(anyString()))
                .thenReturn(createMockGusData(TEST_NIP));

        // Default: Biała Lista returns active VAT status
        lenient().when(vatRegistryPort.lookupVatStatus(anyString()))
                .thenReturn(createMockVatData(TEST_NIP));

        // Default: CEIDG returns not found (only used for JDG)
        lenient().when(soleProprietorRegistryPort.lookupByNip(anyString()))
                .thenReturn(SoleProprietorData.builder().found(false).build());
    }

    @AfterEach
    void clearLookupCache() {
        // Clear the in-memory ConcurrentHashMap cache to prevent cross-test leakage
        try {
            Field cacheField = RegistryLookupService.class.getDeclaredField("lookupCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, ?> cache = (ConcurrentHashMap<String, ?>) cacheField.get(registryLookupService);
            cache.clear();
        } catch (Exception e) {
            // Non-critical — cache will be empty on fresh context anyway
        }
    }

    // =========================================================================
    // User Helpers
    // =========================================================================

    /**
     * Creates a COMPANY user with IN_VALIDATION status (the state after registration,
     * before NIP verification).
     */
    protected User createCompanyUserInValidation() {
        return createTestUser(
                "COMPANY-VALIDATE-" + UUID.randomUUID(),
                UserType.COMPANY,
                "validate." + UUID.randomUUID() + "@integration-test.com",
                "Test", "Company",
                AccountStatus.IN_VALIDATION
        );
    }

    /**
     * Creates a COMPANY user with IN_VALIDATION status and email marked as verified.
     */
    protected User createCompanyUserEmailVerified() {
        User user = createCompanyUserInValidation();
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    // =========================================================================
    // Mock Data Builders — KRS Company
    // =========================================================================

    /**
     * Creates mock GUS BIR1 response for a KRS company (sp. z o.o.).
     */
    protected CompanyRegistryData createMockGusData(String nip) {
        Map<String, Object> rawResponse = new LinkedHashMap<>();
        rawResponse.put("searchResult", Map.of("Regon", "012345678", "Nip", nip, "Typ", "P"));
        rawResponse.put("fullReport", Map.of("praw_nazwa", "Test Sp. z o.o."));

        return CompanyRegistryData.builder()
                .found(true)
                .nip(nip)
                .regon("012345678")
                .krs("0000123456")
                .companyName("Testowa Firma Sp. z o.o.")
                .basicLegalFormCode("1")
                .specificLegalFormCode("117")
                .legalFormName("SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ")
                .registryType("REJESTR PRZEDSIĘBIORCÓW")
                .street("Marszałkowska")
                .buildingNumber("1")
                .apartmentNumber("10")
                .city("Warszawa")
                .postalCode("00-001")
                .voivodeship("MAZOWIECKIE")
                .activityStartDate("2020-01-15")
                .pkdMainCode("62.01.Z")
                .pkdMainDescription("Działalność związana z oprogramowaniem")
                .pkdCodes(List.of(
                        Map.of("code", "62.01.Z", "description", "Oprogramowanie", "isPrimary", true),
                        Map.of("code", "62.02.Z", "description", "Doradztwo IT", "isPrimary", false)
                ))
                .rawResponse(rawResponse)
                .build();
    }

    /**
     * Creates mock GUS BIR1 response for a JDG (sole proprietorship).
     */
    protected CompanyRegistryData createMockJdgGusData(String nip) {
        Map<String, Object> rawResponse = new LinkedHashMap<>();
        rawResponse.put("searchResult", Map.of("Regon", "987654321", "Nip", nip, "Typ", "F"));

        return CompanyRegistryData.builder()
                .found(true)
                .nip(nip)
                .regon("987654321")
                .companyName("Jan Kowalski Usługi IT")
                .basicLegalFormCode("9")
                .specificLegalFormCode("099")
                .legalFormName("OSOBA FIZYCZNA PROWADZĄCA DZIAŁALNOŚĆ GOSPODARCZĄ")
                .registryType("CEIDG")
                .street("Kwiatowa")
                .buildingNumber("5")
                .city("Kraków")
                .postalCode("30-001")
                .voivodeship("MAŁOPOLSKIE")
                .activityStartDate("2019-06-01")
                .pkdMainCode("62.01.Z")
                .pkdMainDescription("Oprogramowanie")
                .pkdCodes(List.of(
                        Map.of("code", "62.01.Z", "description", "Oprogramowanie", "isPrimary", true)
                ))
                .rawResponse(rawResponse)
                .build();
    }

    /**
     * Creates mock GUS data for an inactive (liquidated) company.
     */
    protected CompanyRegistryData createMockInactiveGusData(String nip) {
        CompanyRegistryData data = createMockGusData(nip);
        data.setActivityEndDate("2023-12-31");
        return data;
    }

    // =========================================================================
    // Mock Data Builders — Biała Lista & CEIDG
    // =========================================================================

    protected VatStatusData createMockVatData(String nip) {
        return VatStatusData.builder()
                .found(true)
                .statusVat("Czynny")
                .normalizedStatus("ACTIVE")
                .nip(nip)
                .name("Testowa Firma Sp. z o.o.")
                .accountNumbers(List.of("PL61109010140000071219812874", "PL27114020040000300201355387"))
                .build();
    }

    protected SoleProprietorData createMockCeidgData() {
        return SoleProprietorData.builder()
                .found(true)
                .ownerFirstName("Jan")
                .ownerLastName("Kowalski")
                .businessName("Jan Kowalski Usługi IT")
                .status("AKTYWNY")
                .startDate("2019-06-01")
                .build();
    }

    // =========================================================================
    // CompanyData Helpers
    // =========================================================================

    /**
     * Creates and persists a CompanyData entity for the given user/NIP.
     * Useful for setting up "already exists" scenarios.
     */
    protected CompanyData createAndSaveCompanyData(User user, String nip) {
        CompanyData cd = new CompanyData();
        cd.setUser(user);
        cd.setNip(nip);
        cd.setCompanyName("Existing Company Sp. z o.o.");
        cd.setCompanyType(CompanyType.SP_ZOO);
        cd.setDataVerified(true);
        cd.setSourceGus(true);
        cd.setSourceVat(false);
        cd.setSourceCeidg(false);
        cd.setRegisteredAddress(Map.of("city", "Warszawa", "postalCode", "00-001"));
        return companyDataRepository.save(cd);
    }

    /**
     * Creates a confirm request for the given NIP.
     */
    protected CompanyDataConfirmRequest createConfirmRequest(String nip) {
        CompanyDataConfirmRequest request = new CompanyDataConfirmRequest();
        request.setNip(nip);
        return request;
    }

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
