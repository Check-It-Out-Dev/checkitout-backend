package com.sm.instagram.platform.legal;

import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test-only legal controller for E2E consent lifecycle tests.
 * Provides endpoints to manipulate legal documents and trigger enforcement
 * without waiting for cron schedules or ShedLock.
 *
 * <p><b>SECURITY:</b> This bean is guarded by {@code @Profile("e2e & !prod & !test")}.
 * In production and standard test profiles, this controller is not registered
 * and its endpoints return 404.
 */
@Slf4j
@RestController
@Profile("e2e & !prod & !test")
@RequestMapping("/test/legal")
@RequiredArgsConstructor
public class TestLegalController {

    private final LegalDocumentRepository legalDocumentRepository;
    private final LegalConsentService legalConsentService;
    private final LegalDocumentService legalDocumentService;
    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    private final ConsentRecordRepository consentRecordRepository;

    @Value("${consent.enforcement.grace-period-days:38}")
    private int gracePeriodDays;

    /**
     * Publish a new legal document version with controlled publishedAt.
     */
    @PostMapping("/publish-document-version")
    @Transactional
    public ResponseEntity<Map<String, Object>> publishDocumentVersion(
            @RequestBody Map<String, Object> request) {

        String typeStr = (String) request.get("type");
        String language = (String) request.get("language");
        int version = ((Number) request.get("version")).intValue();
        String publishedAtStr = (String) request.get("publishedAt");

        LegalDocumentType type = LegalDocumentType.valueOf(typeStr);
        LocalDateTime publishedAt = LocalDateTime.parse(publishedAtStr);

        // Upsert: update existing document if found, otherwise create new
        Optional<LegalDocument> existing = legalDocumentRepository
                .findByTypeAndLanguageAndVersion(type, language, version);

        LegalDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
            doc.setContentHash("e2e-test-hash-" + typeStr + "-v" + version);
            doc.setDocumentUrl("https://e2e-test-docs/" + typeStr.toLowerCase() + "_v" + version + "_" + language + ".pdf");
            doc.setPublishedAt(publishedAt);
            log.info("[E2E] Updated existing document version: type={}, language={}, version={}, publishedAt={}",
                    type, language, version, publishedAt);
        } else {
            doc = new LegalDocument();
            doc.setType(type);
            doc.setLanguage(language);
            doc.setVersion(version);
            doc.setContentHash("e2e-test-hash-" + typeStr + "-v" + version);
            doc.setDocumentUrl("https://e2e-test-docs/" + typeStr.toLowerCase() + "_v" + version + "_" + language + ".pdf");
            doc.setPublishedAt(publishedAt);
            log.info("[E2E] Created new document version: type={}, language={}, version={}, publishedAt={}",
                    type, language, version, publishedAt);
        }

        LegalDocument saved = legalDocumentRepository.save(doc);

        return ResponseEntity.ok(Map.of(
                "documentId", saved.getId(),
                "type", saved.getType().name(),
                "version", saved.getVersion(),
                "publishedAt", saved.getPublishedAt().toString()
        ));
    }

    /**
     * Update published_at on ALL existing legal documents.
     * Needed because Liquibase seed data uses CURRENT_TIMESTAMP at migration time.
     */
    @PostMapping("/set-published-at")
    @Transactional
    public ResponseEntity<Map<String, Object>> setPublishedAt(
            @RequestBody Map<String, String> request) {

        String publishedAtStr = request.get("publishedAt");
        LocalDateTime publishedAt = LocalDateTime.parse(publishedAtStr);

        List<LegalDocument> allDocs = legalDocumentRepository.findAll();
        for (LegalDocument doc : allDocs) {
            doc.setPublishedAt(publishedAt);
            legalDocumentRepository.save(doc);
        }

        log.info("[E2E] Updated published_at for {} documents to {}", allDocs.size(), publishedAt);

        return ResponseEntity.ok(Map.of("documentsUpdated", allDocs.size()));
    }

    /**
     * Trigger consent enforcement directly (bypasses cron schedule + ShedLock).
     */
    @PostMapping("/trigger-enforcement")
    public ResponseEntity<Map<String, Object>> triggerEnforcement() {
        log.info("[E2E] Triggering consent enforcement");
        try {
            legalConsentService.blockExpiredUsers();
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            String rootCause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.error("[E2E] trigger-enforcement failed: {} / rootCause: {}", e.getClass().getSimpleName(), rootCause, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getClass().getSimpleName(),
                    "message", rootCause != null ? rootCause : "unknown"
            ));
        }
    }

    /**
     * Reset newestConsentsAccepted for all or a specific user.
     */
    @PostMapping("/reset-consents")
    @Transactional
    public ResponseEntity<Map<String, Object>> resetConsents(
            @RequestBody(required = false) Map<String, String> request) {

        String email = request != null ? request.get("email") : null;

        if (email != null && !email.isBlank()) {
            User user = userRepository.findByEmail(email).orElseThrow(
                    () -> new IllegalArgumentException("User not found: " + email));
            user.setNewestConsentsAccepted(false);
            userRepository.save(user);
            int deletedRecords = consentRecordRepository.deleteByUserId(user.getId());
            userCacheService.evict(user.getFirebaseUserId());
            log.info("[E2E] Reset consents for user: {} (deleted {} consent records)", email, deletedRecords);
            return ResponseEntity.ok(Map.of("usersAffected", 1));
        }

        int affected = userRepository.resetAllConsentsAccepted();
        log.info("[E2E] Reset consents for {} users", affected);
        userCacheService.evictAll();
        return ResponseEntity.ok(Map.of("usersAffected", affected));
    }

    /**
     * Get current grace period information.
     */
    @GetMapping("/grace-period-info")
    public ResponseEntity<Map<String, Object>> gracePeriodInfo() {
        Optional<LocalDateTime> latestPublished = legalDocumentService.getLatestPublishedAt();

        if (latestPublished.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "latestPublishedAt", "none",
                    "gracePeriodDays", gracePeriodDays,
                    "expired", false
            ));
        }

        LocalDateTime published = latestPublished.get();
        LocalDateTime deadline = published.plusDays(gracePeriodDays);
        long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), deadline));
        boolean expired = LocalDateTime.now().isAfter(deadline);

        return ResponseEntity.ok(Map.of(
                "latestPublishedAt", published.toString(),
                "gracePeriodDays", gracePeriodDays,
                "deadline", deadline.toString(),
                "daysRemaining", daysRemaining,
                "expired", expired
        ));
    }

    /**
     * Delete all legal documents with version above the specified maximum.
     * Used by E2E test hooks to reset to v1-only state before consent scenarios.
     */
    @DeleteMapping("/delete-documents-above-version")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteDocumentsAboveVersion(
            @RequestParam int maxVersion) {
        List<LegalDocument> toDelete = legalDocumentRepository.findAll().stream()
                .filter(d -> d.getVersion() > maxVersion)
                .toList();
        legalDocumentRepository.deleteAll(toDelete);
        log.info("[E2E] Deleted {} documents with version > {}", toDelete.size(), maxVersion);
        return ResponseEntity.ok(Map.of("deleted", toDelete.size(), "maxVersion", maxVersion));
    }

    /**
     * Seed a COOKIE_POLICY consent record for a user (idempotent).
     * Used by E2E login helpers so exchange-token doesn't return 451.
     */
    @PostMapping("/seed-cookie-consent")
    @Transactional
    public ResponseEntity<Map<String, Object>> seedCookieConsent(
            @RequestBody Map<String, String> request) {
        String email = request.get("email");
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new IllegalArgumentException("User not found: " + email));

        Optional<Integer> latestVersion = legalDocumentRepository.findLatestVersionByType(LegalDocumentType.COOKIE_POLICY);
        if (latestVersion.isEmpty()) {
            return ResponseEntity.ok(Map.of("seeded", false, "reason", "no COOKIE_POLICY document exists"));
        }

        if (consentRecordRepository.hasUserAcceptedDocumentVersion(user.getId(), LegalDocumentType.COOKIE_POLICY, latestVersion.get())) {
            return ResponseEntity.ok(Map.of("seeded", false, "reason", "already has consent"));
        }

        LegalDocument doc = legalDocumentRepository.findByTypeAndLanguageAndVersion(
                        LegalDocumentType.COOKIE_POLICY, "pl", latestVersion.get())
                .or(() -> legalDocumentRepository.findByTypeAndLanguageAndVersion(
                        LegalDocumentType.COOKIE_POLICY, "en", latestVersion.get()))
                .orElseThrow(() -> new IllegalStateException("COOKIE_POLICY v" + latestVersion.get() + " not found"));

        ConsentRecord record = new ConsentRecord();
        record.setUser(user);
        record.setDocument(doc);
        record.setSource(ConsentSource.COOKIE_BANNER);
        consentRecordRepository.save(record);

        log.info("[E2E] Seeded cookie consent for user {} (docId={}, v{})", email, doc.getId(), latestVersion.get());
        return ResponseEntity.ok(Map.of("seeded", true, "userId", user.getId(), "documentVersion", latestVersion.get()));
    }

    /**
     * Get user consent status by email (no session needed).
     */
    @GetMapping("/user-consent-status")
    public ResponseEntity<Map<String, Object>> userConsentStatus(@RequestParam String email) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new IllegalArgumentException("User not found: " + email));

        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "accountStatus", user.getAccountStatus().name(),
                "newestConsentsAccepted", user.getNewestConsentsAccepted() != null && user.getNewestConsentsAccepted(),
                "userId", user.getId()
        ));
    }
}
