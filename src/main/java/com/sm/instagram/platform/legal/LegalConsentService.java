package com.sm.instagram.platform.legal;

import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.util.RequestContextUtils;
import com.sm.instagram.platform.legal.dto.AnonymousConsentDtoIn;
import com.sm.instagram.platform.legal.dto.ConsentProofDtoIn;
import com.sm.instagram.platform.legal.dto.ConsentRecordDtoIn;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserAccountOrchestrator;
import com.sm.instagram.platform.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalConsentService {

    private final ConsentRecordRepository consentRecordRepository;
    private final LegalDocumentService legalDocumentService;
    private final ConsentCookieService consentCookieService;
    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    private final UserAccountOrchestrator userAccountOrchestrator;

    @Value("${consent.enforcement.grace-period-days:38}")
    private int gracePeriodDays;

    // =========================================================================
    // Pre-registration consent validation
    // =========================================================================

    /**
     * Validates that all required consent cookies are present before registration.
     * Delegates to ConsentCookieService to fail fast (400) before Firebase/DB operations.
     */
    public void validateConsentCookiesPresent(HttpServletRequest request) {
        consentCookieService.validateConsentCookiesPresent(request);
    }

    // =========================================================================
    // Anonymous consent (cookie banner, before registration)
    // =========================================================================

    /**
     * Record anonymous consent from the cookie banner.
     * Creates a consent_record with user_id=NULL.
     * Returns the record ID which is stored in an HMAC-signed cookie.
     */
    @Transactional
    public Long recordAnonymousConsent(AnonymousConsentDtoIn dtoIn, HttpServletRequest request,
                                        HttpServletResponse response) {
        LegalDocument document = legalDocumentService.findByDocumentName(dtoIn.getDocumentName());

        ConsentProofPayload proof = ConsentProofPayload.builder()
                .timestamp(LocalDateTime.now().toString())
                .isTrusted(dtoIn.getIsTrusted())
                .userAgent(dtoIn.getUserAgent() != null ? dtoIn.getUserAgent() : request.getHeader("User-Agent"))
                .language(dtoIn.getLanguage())
                .documentName(dtoIn.getDocumentName())
                .categories(dtoIn.getCategories())
                .build();

        ConsentRecord record = new ConsentRecord();
        record.setUser(null);
        record.setDocument(document);
        record.setUserAgent(request.getHeader("User-Agent"));
        record.setIpAddress(resolveIpAddress(request));
        record.setIsTrusted(dtoIn.getIsTrusted());
        record.setConsentProof(proof);
        record.setSource(ConsentSource.COOKIE_BANNER);

        ConsentRecord saved = consentRecordRepository.save(record);

        // Set HMAC-signed cookie with full proof payload (unified format with ToS/PP cookies)
        proof.setConsentRecordId(saved.getId());
        consentCookieService.setConsentCookie(response,
                ConsentCookieService.COOKIE_CONSENT_COOKIE_POLICY,
                proof);

        log.info("Recorded anonymous consent: recordId={}, documentType={}, ip={}",
                saved.getId(), document.getType(), saved.getIpAddress());

        return saved.getId();
    }

    // =========================================================================
    // Category toggle (cookie banner)
    // =========================================================================

    /**
     * Toggle a cookie category consent on or off.
     * Creates or destroys the corresponding HMAC category cookie.
     */
    public void toggleCategoryConsent(String categoryType, Boolean enabled, Boolean isTrusted,
                                       HttpServletRequest request, HttpServletResponse response) {
        String normalizedType = categoryType.toUpperCase();

        // ESSENTIAL maps to the consent_cookie_policy cookie (not consent_cat_ prefix)
        if ("ESSENTIAL".equals(normalizedType)) {
            if (Boolean.TRUE.equals(enabled)) {
                // Create consent_cookie_policy via the anonymous consent flow (creates DB record + cookie)
                AnonymousConsentDtoIn dtoIn = new AnonymousConsentDtoIn();
                String lang = "pl"; // default, overridden by Accept-Language header
                // Resolve the CURRENT cookie-policy version instead of hard-coding
                // "v2": the constant 404s the moment the version bumps (or in any
                // env seeded to a different version), which silently breaks the
                // ESSENTIAL cookie → /auth/exchange-token then 451s and login
                // fails. findLatest gives us whatever version actually exists.
                LegalDocument cookieDoc = legalDocumentService.findLatest(LegalDocumentType.COOKIE_POLICY, lang);
                dtoIn.setDocumentName("cookie_policy_v" + cookieDoc.getVersion() + "_" + lang + ".pdf");
                dtoIn.setLanguage(lang);
                dtoIn.setIsTrusted(isTrusted);
                dtoIn.setUserAgent(request.getHeader("User-Agent"));
                recordAnonymousConsent(dtoIn, request, response);
                log.debug("Enabled essential cookie via anonymous consent (cookie policy v{})", cookieDoc.getVersion());
            } else {
                consentCookieService.clearConsentCookie(response, ConsentCookieService.COOKIE_CONSENT_COOKIE_POLICY);
                log.debug("Disabled essential cookie");
            }
            return;
        }

        // Regular category cookies (consent_cat_{TYPE})
        String cookieName = ConsentCookieService.categoryBannerCookieName(normalizedType);

        if (Boolean.TRUE.equals(enabled)) {
            CategoryConsentPayload payload = CategoryConsentPayload.builder()
                    .categoryType(normalizedType)
                    .timestamp(LocalDateTime.now().toString())
                    .isTrusted(isTrusted)
                    .userAgent(request.getHeader("User-Agent"))
                    .build();
            consentCookieService.setCategoryCookie(response, cookieName, payload);
            log.debug("Enabled category cookie: {}", normalizedType);
        } else {
            consentCookieService.clearConsentCookie(response, cookieName);
            log.debug("Disabled category cookie: {}", normalizedType);
        }
    }

    // =========================================================================
    // Consent cookie preparation (registration flow)
    // =========================================================================

    /**
     * Prepare an HMAC-signed consent cookie for a specific document type.
     * Called by the FE before registration to set consent proof cookies.
     */
    public void prepareConsentCookie(String documentType, Integer version, String documentHash,
                                     ConsentProofDtoIn proofIn, HttpServletRequest request,
                                     HttpServletResponse response) {
        LegalDocumentType type = parseDocumentType(documentType);

        ConsentProofPayload payload = ConsentProofPayload.builder()
                .timestamp(proofIn.getTimestamp() != null
                        ? java.time.Instant.ofEpochMilli(proofIn.getTimestamp()).toString()
                        : LocalDateTime.now().toString())
                .isTrusted(proofIn.getEventTrusted())
                .documentHash(documentHash)
                .userAgent(request.getHeader("User-Agent"))
                .screenX(proofIn.getScreenX())
                .screenY(proofIn.getScreenY())
                .checkboxId(proofIn.getCheckboxId())
                .build();

        // Preserve consentRecordId from the banner's anonymous consent cookie
        // so the anonymous record gets linked to the user during registration
        if (type == LegalDocumentType.COOKIE_POLICY) {
            ConsentProofPayload existing = consentCookieService.readConsentCookie(
                    request, ConsentCookieService.COOKIE_CONSENT_COOKIE_POLICY, ConsentProofPayload.class);
            if (existing != null && existing.getConsentRecordId() != null) {
                payload.setConsentRecordId(existing.getConsentRecordId());
            }
        }

        String cookieName = ConsentCookieService.cookieNameForType(type);
        consentCookieService.setConsentCookie(response, cookieName, payload);

        log.debug("Prepared consent cookie for {} v{}", documentType, version);
    }

    // =========================================================================
    // Registration consent processing
    // =========================================================================

    /**
     * Process all consent cookies during registration.
     * Reads HMAC cookies, validates them, creates consent_record entries,
     * links the anonymous cookie consent to the new user, and clears cookies.
     */
    @Transactional
    public void processRegistrationConsents(Long userId, HttpServletRequest request,
                                            HttpServletResponse response, ConsentSource source) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ValidationTranslatableException("error.consent.user_not_found"));

        InetAddress clientIp = resolveIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        // 1. Link anonymous cookie consent record to user (if exists from banner)
        processCookieConsent(user, request);

        // 2. Create clickwrap cookie policy record (always — separate consent act from banner)
        processDocumentConsent(user, LegalDocumentType.COOKIE_POLICY, request, clientIp, userAgent, source);

        // 3. Process Terms of Service consent
        processDocumentConsent(user, LegalDocumentType.TERMS_OF_SERVICE, request, clientIp, userAgent, source);

        // 4. Process Privacy Policy consent
        processDocumentConsent(user, LegalDocumentType.PRIVACY_POLICY, request, clientIp, userAgent, source);

        // 5. Set newest_consents_accepted = true
        user.setNewestConsentsAccepted(true);
        userRepository.save(user);

        // 6. Clear all consent cookies
        consentCookieService.clearAllConsentCookies(response);

        log.info("Processed registration consents for userId={}", userId);
    }

    /**
     * Link anonymous cookie consent record to a user during login.
     * Uses REQUIRES_NEW to ensure write persists even if caller is readOnly.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void linkAnonymousCookieConsentToUser(User user, HttpServletRequest request) {
        ConsentProofPayload payload = consentCookieService.readConsentCookie(
                request, ConsentCookieService.COOKIE_CONSENT_COOKIE_POLICY, ConsentProofPayload.class);
        if (payload != null && payload.getConsentRecordId() != null) {
            consentRecordRepository.findById(payload.getConsentRecordId()).ifPresent(record -> {
                if (record.getUser() == null) {
                    // Use managed reference — caller's User may be detached (REQUIRES_NEW = new persistence context)
                    User managedUser = userRepository.getReferenceById(user.getId());
                    record.setUser(managedUser);
                    consentRecordRepository.save(record);
                    log.info("Linked anonymous cookie consent record {} to user {} during login",
                            record.getId(), user.getId());
                }
            });
        }
    }

    /**
     * Attempt to link an anonymous cookie consent record to the user.
     * This links the banner's anonymous DB record (user_id=NULL) to the registering user.
     *
     * <p>BUG-17: fetches a managed reference via {@code userRepository.getReferenceById}
     * before attaching, mirroring the login-path twin {@link #linkAnonymousCookieConsentToUser}.
     * Today the caller-supplied User is managed in the same outer @Transactional, so a direct
     * assignment also worked — but the explicit managed-reference fetch makes the contract
     * survive any future refactor that splits the transaction or passes a detached User.
     */
    private void processCookieConsent(User user, HttpServletRequest request) {
        ConsentProofPayload payload = consentCookieService.readConsentCookie(request,
                ConsentCookieService.COOKIE_CONSENT_COOKIE_POLICY, ConsentProofPayload.class);

        if (payload != null && payload.getConsentRecordId() != null) {
            Long recordId = payload.getConsentRecordId();
            Optional<ConsentRecord> recordOpt = consentRecordRepository.findById(recordId);
            if (recordOpt.isPresent() && recordOpt.get().getUser() == null) {
                ConsentRecord record = recordOpt.get();
                // Defensive: use a managed reference, same as the login-path twin
                User managedUser = userRepository.getReferenceById(user.getId());
                record.setUser(managedUser);
                consentRecordRepository.save(record);
                log.debug("Linked anonymous cookie consent record {} to user {}", recordId, user.getId());
            }
        } else {
            log.debug("No anonymous cookie consent to link for user {}", user.getId());
        }
    }

    private void processDocumentConsent(User user, LegalDocumentType docType, HttpServletRequest request,
                                         InetAddress clientIp, String userAgent, ConsentSource source) {
        String cookieName = ConsentCookieService.cookieNameForType(docType);
        ConsentProofPayload payload = consentCookieService.readConsentCookie(request, cookieName, ConsentProofPayload.class);

        if (payload == null) {
            log.warn("Missing consent cookie for {} during registration of user {}. "
                    + "Continuing without cookie-based proof (may have fallback).", docType, user.getId());
            // Create a record without cookie proof (fallback for Safari ITP edge cases)
            createConsentRecordWithoutProof(user, docType, clientIp, userAgent, source);
            return;
        }

        // Find the latest version of this document type
        LegalDocument document = legalDocumentService.findLatest(docType, "pl"); // language-agnostic acceptance

        ConsentRecord record = new ConsentRecord();
        record.setUser(user);
        record.setDocument(document);
        record.setUserAgent(userAgent);
        record.setIpAddress(clientIp);
        record.setIsTrusted(payload.getIsTrusted());
        record.setConsentProof(payload);
        record.setSource(source);

        consentRecordRepository.save(record);
        log.debug("Created consent record for {} v{} for user {}", docType, document.getVersion(), user.getId());
    }

    private void createConsentRecordWithoutProof(User user, LegalDocumentType docType,
                                                  InetAddress clientIp, String userAgent, ConsentSource source) {
        LegalDocument document = legalDocumentService.findLatest(docType, "pl");

        ConsentRecord record = new ConsentRecord();
        record.setUser(user);
        record.setDocument(document);
        record.setUserAgent(userAgent);
        record.setIpAddress(clientIp);
        record.setIsTrusted(null);
        record.setConsentProof(null);
        record.setSource(source);

        consentRecordRepository.save(record);
        log.warn("Created consent record WITHOUT proof for {} v{} for user {} (cookie fallback)",
                docType, document.getVersion(), user.getId());
    }

    // =========================================================================
    // Authenticated re-consent
    // =========================================================================

    /**
     * Record consent for all required documents atomically (batch re-consent flow).
     * Validates that all 3 required document types are present, creates all records
     * in a single transaction, then checks/updates user status.
     */
    @Transactional
    public void recordAuthenticatedConsentBatch(Long userId, List<ConsentRecordDtoIn> records, HttpServletRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ValidationTranslatableException("error.consent.user_not_found"));

        List<LegalDocumentType> requiredTypes = List.of(
                LegalDocumentType.COOKIE_POLICY,
                LegalDocumentType.TERMS_OF_SERVICE,
                LegalDocumentType.PRIVACY_POLICY
        );

        // Validate all required document types are present
        List<LegalDocumentType> providedTypes = records.stream()
                .map(r -> parseDocumentType(r.getDocumentType()))
                .toList();

        List<String> missingTypes = requiredTypes.stream()
                .filter(t -> !providedTypes.contains(t))
                .map(Enum::name)
                .toList();

        if (!missingTypes.isEmpty()) {
            log.warn("Batch re-consent missing document types: {} for userId={}", missingTypes, userId);
            throw new ValidationTranslatableException("error.consent.missing_consents");
        }

        InetAddress clientIp = resolveIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        // Create all consent records atomically
        for (ConsentRecordDtoIn dtoIn : records) {
            LegalDocumentType type = parseDocumentType(dtoIn.getDocumentType());
            LegalDocument document = legalDocumentService.findLatest(type, "pl");
            ConsentProofPayload proof = buildProofFromDto(dtoIn.getProof(), request);

            ConsentRecord record = new ConsentRecord();
            record.setUser(user);
            record.setDocument(document);
            record.setUserAgent(userAgent);
            record.setIpAddress(clientIp);
            record.setIsTrusted(proof != null ? proof.getIsTrusted() : null);
            record.setConsentProof(proof);
            record.setSource(ConsentSource.LOGIN_PROMPT);

            consentRecordRepository.save(record);
            log.info("Batch re-consent: recorded {} v{} for userId={}", type, document.getVersion(), userId);
        }

        // All records saved — update user status
        user.setNewestConsentsAccepted(true);

        if (user.getAccountStatus() == AccountStatus.BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS) {
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.incrementTokenVersion();
            log.info("Unblocked user {} after batch re-consent — status reverted to ACTIVE", userId);
        }

        userRepository.save(user);
        userCacheService.evict(user.getFirebaseUserId());
    }

    // =========================================================================
    // Consent status checks
    // =========================================================================

    /**
     * Check if a user has accepted the latest version of all required documents.
     * Required documents: COOKIE_POLICY, TERMS_OF_SERVICE, PRIVACY_POLICY.
     */
    public boolean hasAcceptedAllCurrentDocuments(Long userId) {
        List<LegalDocumentType> requiredTypes = List.of(
                LegalDocumentType.COOKIE_POLICY,
                LegalDocumentType.TERMS_OF_SERVICE,
                LegalDocumentType.PRIVACY_POLICY
        );

        for (LegalDocumentType type : requiredTypes) {
            Optional<Integer> latestVersion = legalDocumentService.getLatestVersion(type);
            if (latestVersion.isEmpty()) continue;

            boolean accepted = consentRecordRepository.hasUserAcceptedDocumentVersion(userId, type, latestVersion.get());
            if (!accepted) return false;
        }

        return true;
    }

    /**
     * Compute how many days remain before the user's account will be blocked.
     * Returns null if newest_consents_accepted is true.
     */
    public Integer computeDaysToAcceptNewTerms() {
        Optional<LocalDateTime> latestPublished = legalDocumentService.getLatestPublishedAt();
        if (latestPublished.isEmpty()) return null;

        LocalDateTime deadline = latestPublished.get().plusDays(gracePeriodDays);
        long daysRemaining = ChronoUnit.DAYS.between(LocalDateTime.now(), deadline);
        return Math.max(0, (int) daysRemaining);
    }

    /**
     * Get the document types and versions that need re-consent for a user.
     * Used to build the X-Consent-Required header value.
     */
    public String getRequiredConsentsHeaderValue(Long userId) {
        List<LegalDocumentType> requiredTypes = List.of(
                LegalDocumentType.TERMS_OF_SERVICE,
                LegalDocumentType.PRIVACY_POLICY
        );

        StringBuilder sb = new StringBuilder();
        for (LegalDocumentType type : requiredTypes) {
            Optional<Integer> latestVersion = legalDocumentService.getLatestVersion(type);
            if (latestVersion.isEmpty()) continue;

            boolean accepted = consentRecordRepository.hasUserAcceptedDocumentVersion(userId, type, latestVersion.get());
            if (!accepted) {
                if (!sb.isEmpty()) sb.append(",");
                sb.append(type.name()).append(":").append(latestVersion.get());
            }
        }

        return sb.isEmpty() ? null : sb.toString();
    }

    // =========================================================================
    // Cron job methods
    // =========================================================================

    /**
     * Block users who have not accepted updated terms after the grace period.
     * Called by the daily enforcement cron job.
     */
    @Transactional
    public void blockExpiredUsers() {
        Optional<LocalDateTime> latestPublished = legalDocumentService.getLatestPublishedAt();
        if (latestPublished.isEmpty()) {
            log.debug("No legal documents found — skipping consent enforcement");
            return;
        }

        LocalDateTime deadline = latestPublished.get().plusDays(gracePeriodDays);
        if (LocalDateTime.now().isBefore(deadline)) {
            log.debug("Grace period not yet expired (deadline: {}) — skipping enforcement", deadline);
            return;
        }

        List<User> usersToBlock = userRepository.findByNewestConsentsAcceptedFalseAndAccountStatusIn(
                List.of(AccountStatus.ACTIVE, AccountStatus.IN_VALIDATION));

        int blockedCount = 0;
        for (User user : usersToBlock) {
            user.setAccountStatus(AccountStatus.BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS);
            user.incrementTokenVersion();
            userRepository.save(user);
            userCacheService.evict(user.getFirebaseUserId());
            blockedCount++;
        }

        if (blockedCount > 0) {
            log.info("Consent enforcement: blocked {} users who did not accept terms within {} days",
                    blockedCount, gracePeriodDays);
        }
    }

    /**
     * Archive accounts that have ZERO consent records and were created more than 40 days ago.
     * GDPR Article 6: no consent = no lawful basis for processing.
     * Proactive cleanup demonstrates accountability (Article 5(2)).
     * Called by the weekly no-consent account cleanup cron job.
     */
    @Transactional
    public int archiveUsersWithNoConsents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(gracePeriodDays);
        List<User> usersToArchive = userRepository.findUsersWithNoConsentRecordsBefore(cutoff);

        int archivedCount = 0;
        for (User user : usersToArchive) {
            try {
                log.info("GDPR: Archiving no-consent account — userId={}, email={}, createdTime={}, " +
                                "reason=no_consent_records_after_{}_days, action=TO_BE_DELETED",
                        user.getId(), user.getEmail(), user.getCreatedTime(), gracePeriodDays);
                userAccountOrchestrator.archiveUser(user);
                archivedCount++;
            } catch (Exception e) {
                log.warn("Failed to archive no-consent user {}: {}", user.getId(), e.getMessage());
            }
        }

        if (archivedCount > 0) {
            log.info("GDPR: No-consent account cleanup: archived {} accounts with zero consent records " +
                    "(created more than {} days ago)", archivedCount, gracePeriodDays);
        }

        return archivedCount;
    }

    /**
     * Clean up anonymous consent records older than 1 year.
     * Called by the weekly cleanup cron job.
     */
    @Transactional
    public int cleanupAnonymousRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusYears(1);
        int deleted = consentRecordRepository.deleteAnonymousRecordsBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} anonymous consent records older than 1 year", deleted);
        }
        return deleted;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LegalDocumentType parseDocumentType(String documentType) {
        try {
            return LegalDocumentType.valueOf(documentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationTranslatableException("error.consent.invalid_document_type", documentType);
        }
    }

    private ConsentProofPayload buildProofFromDto(ConsentProofDtoIn proofIn, HttpServletRequest request) {
        if (proofIn == null) return null;

        return ConsentProofPayload.builder()
                .timestamp(proofIn.getTimestamp() != null
                        ? java.time.Instant.ofEpochMilli(proofIn.getTimestamp()).toString()
                        : LocalDateTime.now().toString())
                .isTrusted(proofIn.getEventTrusted())
                .documentHash(proofIn.getDocumentHash())
                .userAgent(request.getHeader("User-Agent"))
                .screenX(proofIn.getScreenX())
                .screenY(proofIn.getScreenY())
                .checkboxId(proofIn.getCheckboxId())
                .build();
    }

    // =========================================================================
    // Subscription consent (inline, not cookie-based)
    // =========================================================================

    /**
     * Record consent for immediate subscription activation (EU Article 16(m)).
     * Called by SubscriptionService during plan purchase/upgrade.
     * No cookies involved — proof comes inline from the checkout UI.
     */
    @Transactional
    public ConsentRecord recordSubscriptionConsent(Long userId, ConsentProofPayload proof,
                                                    HttpServletRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("User not found: " + userId));

        LegalDocument document = legalDocumentService.findLatest(
                LegalDocumentType.SUBSCRIPTION_ACTIVATION_CONSENT,
                request != null ? request.getLocale().getLanguage() : "pl");

        ConsentRecord record = new ConsentRecord();
        record.setUser(user);
        record.setDocument(document);
        record.setSource(ConsentSource.SUBSCRIPTION_PURCHASE);
        record.setConsentProof(proof);
        record.setIsTrusted(proof != null ? proof.getIsTrusted() : null);
        record.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        record.setIpAddress(request != null ? resolveIpAddress(request) : null);

        ConsentRecord saved = consentRecordRepository.save(record);
        log.info("Subscription consent recorded: recordId={}, userId={}, docVersion={}",
                saved.getId(), userId, document.getVersion());
        return saved;
    }

    private InetAddress resolveIpAddress(HttpServletRequest request) {
        String ipStr = RequestContextUtils.getClientIpAddress(request);
        try {
            return InetAddress.getByName(ipStr);
        } catch (UnknownHostException e) {
            log.warn("Could not resolve IP address: {}", ipStr);
            return null;
        }
    }
}
