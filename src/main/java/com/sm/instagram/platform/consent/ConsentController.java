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
 * Controller for user consent operations.
 * Handles GDPR-compliant consent management for regular users.
 */
@Slf4j
@RestController
@RequestMapping("/consent")
@PreAuthorize("isAuthenticated()")
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
public class ConsentController {

    private final ConsentService consentService;

    @Autowired
    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    /**
     * Gets available consent types and current status for the authenticated user.
     *
     * @return List of consent types with current status
     * @throws ResourceNotFoundException if authentication context is missing
     */
    @GetMapping("/my")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserCurrentConsentDtoOut>> getMyConsents() {
        String firebaseUid = getAuthenticatedUserUid();
        log.info("GDPR: Operation=getMyConsents, FirebaseUID={}, Purpose=consent_management_display", firebaseUid);

        List<UserCurrentConsentDtoOut> consents = consentService.getMyAvailableConsents();

        log.info("Retrieved {} consent types for user", consents.size());
        return ResponseEntity.ok(consents);
    }

    /**
     * Records consent for the authenticated user.
     *
     * @param dtoIn The consent data to record
     * @return The recorded consent information
     * @throws ResourceNotFoundException       if authentication context is missing
     * @throws ValidationTranslatableException if consent data is invalid
     */
    @PostMapping("/my")
    @Transactional
    public ResponseEntity<UserConsentDtoOut> recordMyConsent(@RequestBody @Valid UserConsentDtoIn dtoIn) {
        // Additional validation beyond @Valid
        validateConsentData(dtoIn);

        String firebaseUid = getAuthenticatedUserUid();
        log.info("GDPR: Operation=recordConsent, FirebaseUID={}, ConsentType={}, ConsentGiven={}, Purpose=consent_tracking",
                firebaseUid, dtoIn.getConsentType(), dtoIn.getConsentGiven());

        UserConsentDtoOut result = consentService.recordMyConsent(dtoIn);

        log.info("Recorded consent: type={}, given={}", dtoIn.getConsentType(), dtoIn.getConsentGiven());
        return ResponseEntity.ok(result);
    }

    /**
     * Gets consent history for a specific consent type for the authenticated user.
     *
     * @param consentType The type of consent to retrieve history for
     * @return List of consent history records
     * @throws ResourceNotFoundException       if authentication context is missing
     * @throws ValidationTranslatableException if consentType is invalid
     */
    @GetMapping("/my/history/{consentType}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserConsentDtoOut>> getMyConsentHistory(@PathVariable String consentType) {
        // Validate consentType parameter
        if (consentType == null || consentType.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "consentType");
        }

        String firebaseUid = getAuthenticatedUserUid();
        log.info("GDPR: Operation=getConsentHistory, FirebaseUID={}, ConsentType={}, Purpose=consent_audit",
                firebaseUid, consentType);

        List<UserConsentDtoOut> history = consentService.getMyConsentHistory(consentType);

        log.info("Retrieved {} consent history records for type: {}", history.size(), consentType);
        return ResponseEntity.ok(history);
    }

    // ===== HELPER METHODS =====

    /**
     * Gets the authenticated user UID from the security context.
     *
     * @return The Firebase UID of the authenticated user
     * @throws ResourceNotFoundException if authentication context is missing
     */
    private String getAuthenticatedUserUid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            log.error("Authentication not available in security context");
            throw new ResourceNotFoundException("error.auth.not_authenticated");
        }
        return auth.getPrincipal().toString();
    }

    /**
     * Validates consent data beyond what @Valid annotation covers.
     *
     * @param dtoIn The consent data to validate
     * @throws ValidationTranslatableException if consent data is invalid
     */
    private void validateConsentData(UserConsentDtoIn dtoIn) {
        if (dtoIn == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "consent data");
        }

        if (dtoIn.getConsentType() == null || dtoIn.getConsentType().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "consentType");
        }

        if (dtoIn.getConsentGiven() == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "consentGiven");
        }

        // Validate consent type format (alphanumeric with underscores)
        if (!dtoIn.getConsentType().matches("^[A-Z_]+$")) {
            throw new ValidationTranslatableException("error.validation.invalid_parameter",
                    "consentType must contain only uppercase letters and underscores");
        }
    }
}
