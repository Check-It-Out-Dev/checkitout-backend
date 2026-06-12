package com.sm.instagram.platform.consent;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin controller for consent management operations.
 * Handles creation and management of consent definitions and versions.
 */
@Slf4j
@RestController
@RequestMapping("/admin/consent")
@PreAuthorize("hasAuthority('ADMIN')")
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
public class ConsentAdminController {

    private final ConsentService consentService;

    @Autowired
    public ConsentAdminController(ConsentService consentService) {
        this.consentService = consentService;
    }

    /**
     * Gets all consent definitions (admin only).
     *
     * @return List of all consent definitions
     * @throws ResourceNotFoundException if authentication context is missing
     */
    @GetMapping("/definitions")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ConsentDefinitionDtoOut>> getAllConsentDefinitions() {
        String adminFirebaseUid = getFirebaseUid();
        log.info("GDPR: Operation=getAllConsentDefinitions, AdminFirebaseUID={}, Purpose=consent_management",
                adminFirebaseUid);

        List<ConsentDefinitionDtoOut> definitions = consentService.getAllConsentDefinitions();

        log.info("Retrieved {} consent definitions", definitions.size());
        return ResponseEntity.ok(definitions);
    }

    /**
     * Creates a new consent definition (admin only).
     *
     * @param dtoIn The consent definition data to create
     * @return The created consent definition
     * @throws ResourceNotFoundException       if authentication context is missing
     * @throws ValidationTranslatableException if input data is invalid
     */
    @PostMapping("/definitions")
    @Transactional
    public ResponseEntity<ConsentDefinitionDtoOut> createConsentDefinition(@RequestBody @Valid ConsentDefinitionDtoIn dtoIn) {
        String adminFirebaseUid = getFirebaseUid();
        log.info("GDPR: Operation=createConsentDefinition, AdminFirebaseUID={}, ConsentType={}, Purpose=consent_type_creation",
                adminFirebaseUid, dtoIn.getConsentType());

        ConsentDefinitionDtoOut result = consentService.createConsentDefinition(dtoIn);

        log.info("Created consent definition: type={}, id={}", result.getConsentType(), result.getId());
        return ResponseEntity.ok(result);
    }

    /**
     * Creates a new consent version (admin only).
     *
     * @param dtoIn The consent version data to create
     * @return The created consent version
     * @throws ResourceNotFoundException       if authentication context is missing
     * @throws ValidationTranslatableException if input data is invalid
     */
    @PostMapping("/versions")
    @Transactional
    public ResponseEntity<ConsentVersionDtoOut> createConsentVersion(@RequestBody @Valid ConsentVersionDtoIn dtoIn) {
        String adminFirebaseUid = getFirebaseUid();
        log.info("GDPR: Operation=createConsentVersion, AdminFirebaseUID={}, DefinitionId={}, Version={}, Purpose=consent_version_creation",
                adminFirebaseUid, dtoIn.getConsentDefinitionId(), dtoIn.getVersion());

        ConsentVersionDtoOut result = consentService.createConsentVersion(dtoIn);

        log.info("Created consent version: id={}, version={}", result.getId(), result.getVersion());
        return ResponseEntity.ok(result);
    }

    /**
     * Gets user's consent information by user ID (admin only).
     *
     * @param userId The ID of the user to retrieve consents for
     * @return List of current consent records for the user
     * @throws ResourceNotFoundException       if authentication context is missing
     * @throws ValidationTranslatableException if userId is invalid
     */
    @GetMapping("/users/{userId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserCurrentConsentDtoOut>> getUserConsents(@PathVariable Long userId) {
        // Validate userId parameter
        if (userId == null || userId <= 0) {
            throw new ValidationTranslatableException("error.validation.invalid_id", "userId");
        }

        String adminFirebaseUid = getFirebaseUid();
        log.info("GDPR: Operation=getUserConsents, AdminFirebaseUID={}, TargetUserId={}, Purpose=consent_audit",
                adminFirebaseUid, userId);

        List<UserCurrentConsentDtoOut> consents = consentService.getUserCurrentConsentsForUser(userId);

        log.info("Retrieved {} consent records for user {}", consents.size(), userId);
        return ResponseEntity.ok(consents);
    }

    /**
     * Gets user's consent history for a specific consent type (admin only).
     *
     * @param userId      The ID of the user to retrieve consent history for
     * @param consentType The type of consent to retrieve history for
     * @return List of consent history records for the user and consent type
     * @throws ResourceNotFoundException       if authentication context is missing
     * @throws ValidationTranslatableException if parameters are invalid
     */
    @GetMapping("/users/{userId}/history/{consentType}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserConsentDtoOut>> getUserConsentHistory(@PathVariable Long userId,
                                                                         @PathVariable String consentType) {
        // Validate userId parameter
        if (userId == null || userId <= 0) {
            throw new ValidationTranslatableException("error.validation.invalid_id", "userId");
        }

        // Validate consentType parameter
        if (consentType == null || consentType.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "consentType");
        }

        String adminFirebaseUid = getFirebaseUid();
        log.info("GDPR: Operation=getUserConsentHistory, AdminFirebaseUID={}, TargetUserId={}, ConsentType={}, Purpose=consent_audit",
                adminFirebaseUid, userId, consentType);

        List<UserConsentDtoOut> history = consentService.getUserConsentHistoryForUser(userId, consentType);

        log.info("Retrieved {} consent history records for user {} and type {}", history.size(), userId, consentType);
        return ResponseEntity.ok(history);
    }

    // ===== HELPER METHODS =====

    /**
     * Gets the Firebase UID from the current security context.
     *
     * @return The Firebase UID of the authenticated admin user
     * @throws ResourceNotFoundException if authentication context is missing or invalid
     */
    private String getFirebaseUid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            log.error("Authentication not available in security context");
            throw new ResourceNotFoundException("error.auth.not_authenticated");
        }
        return auth.getPrincipal().toString();
    }
}
