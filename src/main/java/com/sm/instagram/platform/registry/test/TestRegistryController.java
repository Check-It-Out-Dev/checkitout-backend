package com.sm.instagram.platform.registry.test;

import com.sm.instagram.platform.registry.CompanyData;
import com.sm.instagram.platform.registry.CompanyDataRepository;
import com.sm.instagram.platform.registry.CompanyType;
import com.sm.instagram.platform.registry.RegistryLookupService;
import com.sm.instagram.platform.registry.port.CompanyRegistryData;
import com.sm.instagram.platform.registry.port.SoleProprietorData;
import com.sm.instagram.platform.registry.port.VatStatusData;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only controller for configuring registry port stubs and manipulating
 * user/DB state in E2E tests. Follows the TestAuthController/TestLegalController pattern.
 *
 * <p><b>SECURITY:</b> This bean is guarded by {@code @Profile("(e2e | dev-lite) & !prod & !test")}.
 * In production and standard test profiles, this controller is not registered
 * and its endpoints return 404.
 */
@Slf4j
@RestController
@Profile("(e2e | dev-lite) & !prod & !test")
@RequestMapping("/test/registry")
@RequiredArgsConstructor
public class TestRegistryController {

    private final RegistryStubState stubState;
    private final UserRepository userRepository;
    private final CompanyDataRepository companyDataRepository;
    private final RegistryLookupService registryLookupService;
    private final com.sm.instagram.platform.auth.cache.UserCacheService userCacheService;

    // =========================================================================
    // Stub Configuration Endpoints
    // =========================================================================

    /**
     * Configure GUS + VAT stubs to return a KRS company for the given NIP.
     */
    @PostMapping("/configure-krs-company")
    public ResponseEntity<Map<String, Object>> configureKrsCompany(@RequestBody Map<String, Object> request) {
        String nip = (String) request.get("nip");
        String companyName = (String) request.getOrDefault("companyName", "E2E Testowa Firma Sp. z o.o.");

        log.info("[E2E] Configuring KRS company stub for NIP: {}", nip);

        Map<String, Object> rawResponse = new LinkedHashMap<>();
        rawResponse.put("searchResult", Map.of("Regon", "012345678", "Nip", nip, "Typ", "P"));
        rawResponse.put("fullReport", Map.of("praw_nazwa", companyName));

        CompanyRegistryData gusData = CompanyRegistryData.builder()
                .found(true)
                .nip(nip)
                .regon("012345678")
                .krs("0000123456")
                .companyName(companyName)
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

        VatStatusData vatData = VatStatusData.builder()
                .found(true)
                .statusVat("Czynny")
                .normalizedStatus("ACTIVE")
                .nip(nip)
                .name(companyName)
                .accountNumbers(List.of("PL61109010140000071219812874", "PL27114020040000300201355387"))
                .build();

        stubState.putGus(nip, gusData);
        stubState.putVat(nip, vatData);

        return ResponseEntity.ok(Map.of("configured", true, "nip", nip, "type", "KRS"));
    }

    /**
     * Configure GUS + CEIDG + VAT stubs to return a JDG for the given NIP.
     */
    @PostMapping("/configure-jdg-company")
    public ResponseEntity<Map<String, Object>> configureJdgCompany(@RequestBody Map<String, Object> request) {
        String nip = (String) request.get("nip");
        String ownerFirstName = (String) request.getOrDefault("ownerFirstName", "Jan");
        String ownerLastName = (String) request.getOrDefault("ownerLastName", "Kowalski");
        String businessName = (String) request.getOrDefault("businessName", ownerFirstName + " " + ownerLastName + " Usługi IT");

        log.info("[E2E] Configuring JDG company stub for NIP: {}", nip);

        Map<String, Object> rawResponse = new LinkedHashMap<>();
        rawResponse.put("searchResult", Map.of("Regon", "987654321", "Nip", nip, "Typ", "F"));

        CompanyRegistryData gusData = CompanyRegistryData.builder()
                .found(true)
                .nip(nip)
                .regon("987654321")
                .companyName(businessName)
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

        VatStatusData vatData = VatStatusData.builder()
                .found(true)
                .statusVat("Czynny")
                .normalizedStatus("ACTIVE")
                .nip(nip)
                .name(businessName)
                .accountNumbers(List.of("PL61109010140000071219812874"))
                .build();

        SoleProprietorData ceidgData = SoleProprietorData.builder()
                .found(true)
                .ownerFirstName(ownerFirstName)
                .ownerLastName(ownerLastName)
                .businessName(businessName)
                .nip(nip)
                .status("AKTYWNY")
                .startDate("2019-06-01")
                .build();

        stubState.putGus(nip, gusData);
        stubState.putVat(nip, vatData);
        stubState.putCeidg(nip, ceidgData);

        return ResponseEntity.ok(Map.of("configured", true, "nip", nip, "type", "JDG"));
    }

    /**
     * Configure GUS to return found=false for the given NIP.
     */
    @PostMapping("/configure-gus-not-found")
    public ResponseEntity<Map<String, Object>> configureGusNotFound(@RequestBody Map<String, String> request) {
        String nip = request.get("nip");
        log.info("[E2E] Configuring GUS not-found for NIP: {}", nip);

        stubState.putGus(nip, CompanyRegistryData.builder().found(false).build());
        return ResponseEntity.ok(Map.of("configured", true, "nip", nip, "gusFound", false));
    }

    /**
     * Configure GUS to return a company with activityEndDate (inactive/liquidated).
     */
    @PostMapping("/configure-inactive-company")
    public ResponseEntity<Map<String, Object>> configureInactiveCompany(@RequestBody Map<String, Object> request) {
        String nip = (String) request.get("nip");
        String endDate = (String) request.getOrDefault("activityEndDate", "2023-12-31");

        log.info("[E2E] Configuring inactive company stub for NIP: {}", nip);

        Map<String, Object> rawResponse = new LinkedHashMap<>();
        rawResponse.put("searchResult", Map.of("Regon", "012345678", "Nip", nip, "Typ", "P"));

        CompanyRegistryData gusData = CompanyRegistryData.builder()
                .found(true)
                .nip(nip)
                .regon("012345678")
                .krs("0000123456")
                .companyName("Zlikwidowana Firma Sp. z o.o.")
                .basicLegalFormCode("1")
                .specificLegalFormCode("117")
                .legalFormName("SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ")
                .registryType("REJESTR PRZEDSIĘBIORCÓW")
                .street("Marszałkowska")
                .buildingNumber("1")
                .city("Warszawa")
                .postalCode("00-001")
                .voivodeship("MAZOWIECKIE")
                .activityStartDate("2015-01-01")
                .activityEndDate(endDate)
                .pkdMainCode("62.01.Z")
                .pkdMainDescription("Oprogramowanie")
                .rawResponse(rawResponse)
                .build();

        stubState.putGus(nip, gusData);

        return ResponseEntity.ok(Map.of("configured", true, "nip", nip, "inactive", true));
    }

    /**
     * Clear all stub state and company data from DB. All NIPs default to not-found.
     */
    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<Map<String, Object>> reset() {
        log.info("[E2E] Resetting all registry stub state and DB company data");
        stubState.clear();

        // Clean up company data from DB to avoid NIP uniqueness conflicts between scenarios
        List<CompanyData> allCompanyData = companyDataRepository.findAll();
        for (CompanyData cd : allCompanyData) {
            User user = cd.getUser();
            if (user != null) {
                user.setNip(null);
                userRepository.save(user);
            }
        }
        companyDataRepository.deleteAll();
        log.info("[E2E] Deleted {} company data records", allCompanyData.size());

        return ResponseEntity.ok(Map.of("reset", true, "companyDataDeleted", allCompanyData.size()));
    }

    // =========================================================================
    // User/DB Manipulation Endpoints
    // =========================================================================

    /**
     * Set user.emailVerified by Firebase UID.
     */
    @PostMapping("/set-email-verified")
    @Transactional
    public ResponseEntity<Map<String, Object>> setEmailVerified(@RequestBody Map<String, Object> request) {
        String firebaseUid = (String) request.get("firebaseUid");
        boolean verified = (Boolean) request.getOrDefault("verified", true);

        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + firebaseUid));
        user.setEmailVerified(verified);
        if (verified) {
            user.setLastVerifiedEmail(user.getEmail());
        } else {
            user.setLastVerifiedEmail(null);
        }

        // Clear verification email cooldown when setting to unverified (allows re-sending)
        if (!verified) {
            user.setEmailVerificationSentAt(null);
        }

        // Also set accountStatus if specified, or default to IN_VALIDATION when emailVerified=false
        String accountStatus = (String) request.get("accountStatus");
        if (accountStatus != null) {
            user.setAccountStatus(AccountStatus.valueOf(accountStatus));
        } else if (!verified) {
            user.setAccountStatus(AccountStatus.IN_VALIDATION);
        }

        userRepository.save(user);

        // Evict Redis user cache so the confirm flow reads the updated emailVerified
        userCacheService.evict(firebaseUid);

        log.info("[E2E] Set emailVerified={}, accountStatus={} for user: {} (cache evicted)",
                verified, user.getAccountStatus(), firebaseUid);
        return ResponseEntity.ok(Map.of("firebaseUid", firebaseUid, "emailVerified", verified,
                "accountStatus", user.getAccountStatus().name()));
    }

    /**
     * Set user.initialAccountSetupCompleted by Firebase UID.
     */
    @PostMapping("/set-initial-setup")
    @Transactional
    public ResponseEntity<Map<String, Object>> setInitialSetup(@RequestBody Map<String, Object> request) {
        String firebaseUid = (String) request.get("firebaseUid");
        boolean completed = (Boolean) request.getOrDefault("completed", false);

        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + firebaseUid));
        user.setInitialAccountSetupCompleted(completed);
        userRepository.save(user);

        userCacheService.evict(firebaseUid);

        log.info("[E2E] Set initialAccountSetupCompleted={} for user: {} (cache evicted)", completed, firebaseUid);
        return ResponseEntity.ok(Map.of("firebaseUid", firebaseUid, "initialAccountSetupCompleted", completed));
    }

    /**
     * Directly persist a CompanyData entity for a user (for duplicate NIP tests).
     */
    @PostMapping("/create-company-data")
    @Transactional
    public ResponseEntity<Map<String, Object>> createCompanyData(@RequestBody Map<String, Object> request) {
        String firebaseUid = (String) request.get("firebaseUid");
        String nip = (String) request.get("nip");
        String companyName = (String) request.getOrDefault("companyName", "Existing Company Sp. z o.o.");

        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + firebaseUid));

        CompanyData cd = new CompanyData();
        cd.setUser(user);
        cd.setNip(nip);
        cd.setCompanyName(companyName);
        cd.setCompanyType(CompanyType.SP_ZOO);
        cd.setDataVerified(true);
        cd.setSourceGus(true);
        cd.setSourceVat(false);
        cd.setSourceCeidg(false);
        cd.setRegisteredAddress(Map.of("city", "Warszawa", "postalCode", "00-001"));
        cd = companyDataRepository.save(cd);

        user.setNip(nip);
        userRepository.save(user);

        log.info("[E2E] Created CompanyData: id={}, nip={}, user={}", cd.getId(), nip, firebaseUid);
        return ResponseEntity.ok(Map.of("companyDataId", cd.getId(), "nip", nip));
    }

    /**
     * Delete CompanyData by NIP.
     */
    @DeleteMapping("/delete-company-data")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteCompanyData(@RequestParam String nip) {
        Optional<CompanyData> cd = companyDataRepository.findByNip(nip);
        if (cd.isPresent()) {
            // Clear denormalized NIP on user
            User user = cd.get().getUser();
            user.setNip(null);
            userRepository.save(user);

            companyDataRepository.delete(cd.get());
            log.info("[E2E] Deleted CompanyData for NIP: {}", nip);
            return ResponseEntity.ok(Map.of("deleted", true, "nip", nip));
        }
        return ResponseEntity.ok(Map.of("deleted", false, "nip", nip, "reason", "not found"));
    }

    /**
     * Clear RegistryLookupService in-memory lookup cache.
     */
    @PostMapping("/clear-cache")
    public ResponseEntity<Map<String, Object>> clearCache() {
        try {
            Field cacheField = RegistryLookupService.class.getDeclaredField("lookupCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, ?> cache = (ConcurrentHashMap<String, ?>) cacheField.get(registryLookupService);
            int size = cache.size();
            cache.clear();
            log.info("[E2E] Cleared registry lookup cache ({} entries)", size);
            return ResponseEntity.ok(Map.of("cleared", true, "entriesRemoved", size));
        } catch (Exception e) {
            log.warn("[E2E] Failed to clear cache: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("cleared", false, "error", e.getMessage()));
        }
    }

    /**
     * Clear the passwordResetSentAt cooldown for a user.
     * Allows E2E tests to request password reset emails without waiting 60 seconds.
     */
    @PostMapping("/clear-password-reset-cooldown")
    @Transactional
    public ResponseEntity<Map<String, Object>> clearPasswordResetCooldown(@RequestBody Map<String, String> request) {
        String firebaseUid = request.get("firebaseUid");

        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + firebaseUid));
        user.setPasswordResetSentAt(null);
        userRepository.save(user);

        userCacheService.evict(firebaseUid);

        log.info("[E2E] Cleared passwordResetSentAt for user: {} (cache evicted)", firebaseUid);
        return ResponseEntity.ok(Map.of("firebaseUid", firebaseUid, "cooldownCleared", true));
    }

    /**
     * Get user's accountStatus, emailVerified, and nip for E2E assertions.
     */
    @GetMapping("/user-status")
    public ResponseEntity<Map<String, Object>> getUserStatus(@RequestParam String firebaseUid) {
        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + firebaseUid));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("firebaseUid", user.getFirebaseUserId());
        result.put("userId", user.getId());
        result.put("email", user.getEmail());
        result.put("accountStatus", user.getAccountStatus().name());
        result.put("emailVerified", user.getEmailVerified());
        result.put("nip", user.getNip());
        result.put("userType", user.getUserType().name());

        return ResponseEntity.ok(result);
    }
}
