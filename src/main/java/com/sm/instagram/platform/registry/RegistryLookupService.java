package com.sm.instagram.platform.registry;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.notification.event.AccountActivatedEvent;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.registry.config.RegistryProperties;
import com.sm.instagram.platform.registry.dto.*;
import com.sm.instagram.platform.registry.exception.NipAlreadyRegisteredException;
import com.sm.instagram.platform.registry.exception.NipNotFoundException;
import com.sm.instagram.platform.registry.port.*;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator service for company registry lookups and data confirmation.
 * Coordinates all 3 registry ports (GUS BIR1, Biała Lista, CEIDG),
 * merges results, and classifies company type.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryLookupService {

    private final CompanyRegistryPort companyRegistryPort;
    private final VatRegistryPort vatRegistryPort;
    private final SoleProprietorRegistryPort soleProprietorRegistryPort;
    private final CompanyDataRepository companyDataRepository;
    private final UserRepository userRepository;
    private final NipValidator nipValidator;
    private final CompanyTypeClassifier companyTypeClassifier;
    private final RegistryProperties registryProperties;
    private final PermissionUtils permissionUtils;
    private final ApplicationEventPublisher eventPublisher;
    private final UserCacheService userCacheService;

    /** In-memory cache of lookup results for the confirm step. Keyed by NIP. */
    private final ConcurrentHashMap<String, CachedLookup> lookupCache = new ConcurrentHashMap<>();

    // =========================================================================
    // NIP Lookup
    // =========================================================================

    /**
     * Looks up company data by NIP from all available registries.
     * Results are cached in-memory for the confirm step.
     */
    public NipLookupResponse lookupByNip(String rawNip, String firebaseUid) {
        String nip = nipValidator.normalize(rawNip);

        // Validate NIP format + checksum
        if (!nipValidator.isValid(nip)) {
            throw new ValidationTranslatableException("error.registry.invalid_nip");
        }

        // Check if NIP is already claimed
        if (companyDataRepository.existsByNip(nip)) {
            throw new NipAlreadyRegisteredException(nip);
        }

        // Verify calling user is a COMPANY type
        User user = findUserByFirebaseUid(firebaseUid);
        if (user.getUserType() != UserType.COMPANY) {
            throw new BusinessRuleTranslatableException("error.registry.invalid_user_type");
        }

        log.info("Starting registry lookup for NIP: {} by user: {}", maskNip(nip), user.getId());

        // Step 1: GUS BIR1 (mandatory)
        CompanyRegistryData gusData = companyRegistryPort.lookupByNip(nip);
        if (!gusData.isFound()) {
            throw new NipNotFoundException(nip);
        }

        // Classify company type
        CompanyType companyType = companyTypeClassifier.classify(gusData);
        boolean isActive = companyTypeClassifier.isActive(gusData);
        boolean isSuspended = companyTypeClassifier.isSuspended(gusData);

        if (!isActive && !isSuspended) {
            throw new BusinessRuleTranslatableException("error.registry.company_inactive");
        }

        // Step 2: Biała Lista (non-blocking)
        VatStatusData vatData = VatStatusData.builder().found(false).build();
        try {
            vatData = vatRegistryPort.lookupVatStatus(nip);
        } catch (Exception e) {
            log.warn("Biała Lista lookup failed (non-blocking): {}", e.getMessage());
        }

        // Step 3: CEIDG (only for JDG)
        SoleProprietorData ceidgData = SoleProprietorData.builder().found(false).build();
        if (companyType == CompanyType.JDG) {
            try {
                ceidgData = soleProprietorRegistryPort.lookupByNip(nip);
            } catch (Exception e) {
                log.warn("CEIDG lookup failed (non-blocking for lookup phase): {}", e.getMessage());
            }
        }

        // Build response
        NipLookupResponse response = buildLookupResponse(gusData, vatData, ceidgData, companyType, isActive, isSuspended);

        // Cache the result for the confirm step
        lookupCache.put(nip, new CachedLookup(gusData, vatData, ceidgData, companyType, LocalDateTime.now()));

        // Evict expired cache entries
        evictExpiredCacheEntries();

        log.info("Registry lookup complete for NIP: {}. Type: {}, Active: {}", maskNip(nip), companyType, isActive);
        return response;
    }

    // =========================================================================
    // Company Data Confirmation
    // =========================================================================

    /**
     * Confirms and persists company data after user review.
     * Checks auto-activation conditions (email verified + data verified).
     */
    @Transactional
    public CompanyDataConfirmResponse confirmCompanyData(
            String firebaseUid,
            CompanyDataConfirmRequest request) {

        String nip = nipValidator.normalize(request.getNip());

        // Validate NIP
        if (!nipValidator.isValid(nip)) {
            throw new ValidationTranslatableException("error.registry.invalid_nip");
        }

        // Load and validate user
        User user = findUserByFirebaseUid(firebaseUid);
        if (user.getUserType() != UserType.COMPANY) {
            throw new BusinessRuleTranslatableException("error.registry.invalid_user_type");
        }

        // Check if user already has company data
        if (companyDataRepository.existsByUserId(user.getId())) {
            throw new BusinessRuleTranslatableException("error.registry.company_data_exists");
        }

        // Check NIP uniqueness (race condition protection)
        if (companyDataRepository.existsByNip(nip)) {
            throw new NipAlreadyRegisteredException(nip);
        }

        // Retrieve cached lookup result or re-fetch
        CachedLookup cached = lookupCache.get(nip);
        CompanyRegistryData gusData;
        VatStatusData vatData;
        SoleProprietorData ceidgData;
        CompanyType companyType;

        if (cached != null && !cached.isExpired(registryProperties.getCache().getTtlMinutes())) {
            gusData = cached.gusData;
            vatData = cached.vatData;
            ceidgData = cached.ceidgData;
            companyType = cached.companyType;
        } else {
            // Cache expired or missing — re-fetch
            log.info("Cache miss for NIP: {}, re-fetching from registries", maskNip(nip));
            gusData = companyRegistryPort.lookupByNip(nip);
            if (!gusData.isFound()) throw new NipNotFoundException(nip);
            companyType = companyTypeClassifier.classify(gusData);
            vatData = safeVatLookup(nip);
            ceidgData = companyType == CompanyType.JDG ? safeCeidgLookup(nip) : SoleProprietorData.builder().found(false).build();
        }

        // Create and save CompanyData entity
        CompanyData companyData = buildCompanyDataEntity(user, nip, gusData, vatData, ceidgData, companyType, request);
        try {
            companyData = companyDataRepository.save(companyData);
        } catch (DataIntegrityViolationException e) {
            // Concurrent confirm race condition — DB unique constraint on NIP catches it
            throw new NipAlreadyRegisteredException(nip);
        }

        // Denormalize NIP to user table
        user.setNip(nip);

        // BUG-15 (sibling of BUG-13): defer the Firebase role write + activation event
        // until AFTER the PG save commits. If userRepository.save fails after the Firebase
        // write succeeded, Firebase would show ACTIVE while PG stayed IN_VALIDATION with
        // no tokenVersion bump (no 419) — a split-brain that cannot self-heal.
        List<Runnable> deferredFirebaseActions = new ArrayList<>();
        boolean activated = user.getAccountStatus() == AccountStatus.ACTIVE;
        if (!activated && user.getAccountStatus() == AccountStatus.IN_VALIDATION
                && Boolean.TRUE.equals(user.getEmailVerified()) && companyData.getDataVerified()) {
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.incrementTokenVersion();
            final String firebaseUidLocal = user.getFirebaseUserId();
            deferredFirebaseActions.add(() ->
                    permissionUtils.changeUserRole(firebaseUidLocal, AccountStatus.ACTIVE, UserType.COMPANY));
            deferredFirebaseActions.add(() -> eventPublisher.publishEvent(
                    new AccountActivatedEvent(this, user, AccountStatus.IN_VALIDATION, "COMPANY_DATA_CONFIRMATION")));
            activated = true;
            log.info("COMPANY user {} auto-activated: email verified + company data verified", user.getId());
        }

        userRepository.save(user);
        // PG is now consistent (within the outer @Transactional). Run Firebase + event AFTER PG save.
        deferredFirebaseActions.forEach(Runnable::run);

        if (activated) {
            userCacheService.evict(user.getFirebaseUserId());
        }

        // Clear cache entry
        lookupCache.remove(nip);

        return CompanyDataConfirmResponse.builder()
                .companyDataId(companyData.getId())
                .nip(nip)
                .companyName(companyData.getCompanyName())
                .companyType(companyType)
                .activated(activated)
                .accountStatus(user.getAccountStatus().name())
                .message(activated ? "Company verified and activated" : "Company verified, awaiting email confirmation")
                .build();
    }

    // =========================================================================
    // Reset Company Data (change NIP)
    // =========================================================================

    /**
     * Deletes the user's company data so they can enter a different NIP.
     * Account status stays unchanged — this is a data swap, not deactivation.
     */
    @Transactional
    public void resetCompanyData(String firebaseUid) {
        User user = findUserByFirebaseUid(firebaseUid);
        if (user.getUserType() != UserType.COMPANY) {
            throw new BusinessRuleTranslatableException("error.registry.invalid_user_type");
        }

        Optional<CompanyData> existing = companyDataRepository.findByUserId(user.getId());
        if (existing.isEmpty()) {
            throw new BusinessRuleTranslatableException("error.registry.company_data_not_found");
        }

        log.info("Resetting company data for user {} (old NIP: {})", user.getId(), maskNip(existing.get().getNip()));

        companyDataRepository.delete(existing.get());
        user.setNip(null);
        userRepository.save(user);
    }

    // =========================================================================
    // Refresh Company Data
    // =========================================================================

    /**
     * Re-fetches company data from registries for a user who already has confirmed data.
     * Updates the existing CompanyData entity with fresh registry information.
     */
    @Transactional
    public NipLookupResponse refreshCompanyData(String firebaseUid) {
        User user = findUserByFirebaseUid(firebaseUid);
        if (user.getUserType() != UserType.COMPANY) {
            throw new BusinessRuleTranslatableException("error.registry.invalid_user_type");
        }

        CompanyData existing = companyDataRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessRuleTranslatableException("error.registry.company_data_not_found"));

        String nip = existing.getNip();
        log.info("Refreshing registry data for NIP: {} by user: {}", maskNip(nip), user.getId());

        // Re-fetch from all registries
        CompanyRegistryData gusData = companyRegistryPort.lookupByNip(nip);
        if (!gusData.isFound()) {
            throw new NipNotFoundException(nip);
        }

        CompanyType companyType = companyTypeClassifier.classify(gusData);
        boolean isActive = companyTypeClassifier.isActive(gusData);
        boolean isSuspended = companyTypeClassifier.isSuspended(gusData);

        VatStatusData vatData = safeVatLookup(nip);
        SoleProprietorData ceidgData = companyType == CompanyType.JDG
                ? safeCeidgLookup(nip)
                : SoleProprietorData.builder().found(false).build();

        // Update existing entity with fresh data
        existing.setRegon(gusData.getRegon());
        existing.setKrs(gusData.getKrs());
        existing.setCompanyName(gusData.getCompanyName());
        existing.setCompanyType(companyType);
        existing.setLegalFormCode(gusData.getBasicLegalFormCode());
        existing.setLegalFormName(gusData.getLegalFormName());
        existing.setPkdCodes(gusData.getPkdCodes());
        existing.setRegistryDataFetchedAt(LocalDateTime.now());
        existing.setSourceGus(gusData.isFound());
        existing.setSourceVat(vatData.isFound());
        existing.setSourceCeidg(ceidgData.isFound());
        existing.setRawGusResponse(gusData.getRawResponse());

        if (vatData.isFound()) {
            existing.setVatStatus(vatData.getNormalizedStatus());
            existing.setBankAccounts(vatData.getAccountNumbers());
        }
        if (ceidgData.isFound()) {
            existing.setOwnerName(ceidgData.getOwnerFullName());
        }

        companyDataRepository.save(existing);
        log.info("Registry data refreshed for NIP: {}", maskNip(nip));

        return buildLookupResponse(gusData, vatData, ceidgData, companyType, isActive, isSuspended);
    }

    // =========================================================================
    // Read Company Data
    // =========================================================================

    public CompanyDataDtoOut getCompanyDataForUser(String firebaseUid) {
        User user = findUserByFirebaseUid(firebaseUid);
        CompanyData companyData = companyDataRepository.findByUserId(user.getId())
                .orElse(null);
        return CompanyDataDtoOut.fromEntity(companyData);
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private CompanyData buildCompanyDataEntity(
            User user, String nip,
            CompanyRegistryData gusData, VatStatusData vatData,
            SoleProprietorData ceidgData, CompanyType companyType,
            CompanyDataConfirmRequest request) {

        CompanyData entity = new CompanyData();
        entity.setUser(user);
        entity.setNip(nip);
        entity.setRegon(gusData.getRegon());
        entity.setKrs(gusData.getKrs());
        entity.setCompanyName(gusData.getCompanyName());
        entity.setCompanyType(companyType);
        entity.setLegalFormCode(gusData.getBasicLegalFormCode());
        entity.setLegalFormName(gusData.getLegalFormName());

        // Registered address from GUS
        Map<String, String> address = new LinkedHashMap<>();
        if (gusData.getStreet() != null) address.put("street", gusData.getStreet());
        if (gusData.getBuildingNumber() != null) address.put("building", gusData.getBuildingNumber());
        if (gusData.getApartmentNumber() != null) address.put("apartment", gusData.getApartmentNumber());
        if (gusData.getCity() != null) address.put("city", gusData.getCity());
        if (gusData.getPostalCode() != null) address.put("postalCode", gusData.getPostalCode());
        if (gusData.getVoivodeship() != null) address.put("voivodeship", gusData.getVoivodeship());
        entity.setRegisteredAddress(address);

        // Correspondence address from user input
        if (request.getCorrespondenceAddress() != null && !request.getCorrespondenceAddress().isEmpty()) {
            entity.setCorrespondenceAddress(request.getCorrespondenceAddress());
        }

        // PKD codes from GUS
        entity.setPkdCodes(gusData.getPkdCodes());

        // VAT status from Biała Lista
        if (vatData.isFound()) {
            entity.setVatStatus(vatData.getNormalizedStatus());
            entity.setBankAccounts(vatData.getAccountNumbers());
        }

        // Owner name from CEIDG (JDG only)
        if (ceidgData.isFound()) {
            entity.setOwnerName(ceidgData.getOwnerFullName());
        }

        entity.setRegistryDataFetchedAt(LocalDateTime.now());
        entity.setDataVerified(true);
        entity.setSourceGus(gusData.isFound());
        entity.setSourceVat(vatData.isFound());
        entity.setSourceCeidg(ceidgData.isFound());
        entity.setRawGusResponse(gusData.getRawResponse());

        return entity;
    }

    private NipLookupResponse buildLookupResponse(
            CompanyRegistryData gusData, VatStatusData vatData,
            SoleProprietorData ceidgData, CompanyType companyType,
            boolean isActive, boolean isSuspended) {

        return NipLookupResponse.builder()
                .nip(gusData.getNip())
                .regon(gusData.getRegon())
                .krs(gusData.getKrs())
                .companyName(gusData.getCompanyName())
                .companyType(companyType)
                .legalFormName(gusData.getLegalFormName())
                .street(gusData.getStreet())
                .buildingNumber(gusData.getBuildingNumber())
                .apartmentNumber(gusData.getApartmentNumber())
                .city(gusData.getCity())
                .postalCode(gusData.getPostalCode())
                .voivodeship(gusData.getVoivodeship())
                .pkdMainCode(gusData.getPkdMainCode())
                .pkdMainDescription(gusData.getPkdMainDescription())
                .pkdCodes(gusData.getPkdCodes())
                .vatStatus(vatData.isFound() ? vatData.getNormalizedStatus() : null)
                .bankAccounts(vatData.isFound() ? vatData.getAccountNumbers() : null)
                .ownerName(ceidgData.isFound() ? ceidgData.getOwnerFullName() : null)
                .companyActive(isActive)
                .companySuspended(isSuspended)
                .sourceGus(gusData.isFound())
                .sourceVat(vatData.isFound())
                .sourceCeidg(ceidgData.isFound())
                .build();
    }

    private VatStatusData safeVatLookup(String nip) {
        try {
            return vatRegistryPort.lookupVatStatus(nip);
        } catch (Exception e) {
            log.warn("Biała Lista lookup failed (non-blocking): {}", e.getMessage());
            return VatStatusData.builder().found(false).build();
        }
    }

    private SoleProprietorData safeCeidgLookup(String nip) {
        try {
            return soleProprietorRegistryPort.lookupByNip(nip);
        } catch (Exception e) {
            log.warn("CEIDG lookup failed (non-blocking): {}", e.getMessage());
            return SoleProprietorData.builder().found(false).build();
        }
    }

    private User findUserByFirebaseUid(String firebaseUid) {
        return userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new BusinessRuleTranslatableException("error.resource.user_not_found"));
    }

    private void evictExpiredCacheEntries() {
        int ttl = registryProperties.getCache().getTtlMinutes();
        lookupCache.entrySet().removeIf(entry -> entry.getValue().isExpired(ttl));
    }

    private String maskNip(String nip) {
        if (nip == null || nip.length() < 4) return "***";
        return nip.substring(0, 3) + "*******";
    }

    // =========================================================================
    // Cache entry
    // =========================================================================

    private record CachedLookup(
            CompanyRegistryData gusData,
            VatStatusData vatData,
            SoleProprietorData ceidgData,
            CompanyType companyType,
            LocalDateTime timestamp) {

        boolean isExpired(int ttlMinutes) {
            return timestamp.plusMinutes(ttlMinutes).isBefore(LocalDateTime.now());
        }
    }
}
