package com.sm.instagram.platform.legal;

import com.sm.instagram.platform.auth.service.TokenExchangeService;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.consent.ConsentDefinitionRepository;
import com.sm.instagram.platform.legal.dto.*;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Controller for legal document and consent operations.
 * Provides both public (anonymous) and authenticated endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/legal")
@RequiredArgsConstructor
public class LegalController {

    private final LegalDocumentService legalDocumentService;
    private final LegalConsentService legalConsentService;
    private final ConsentCookieService consentCookieService;
    private final ConsentDefinitionRepository consentDefinitionRepository;
    private final TokenExchangeService tokenExchangeService;
    private final PermissionUtils permissionUtils;
    private final UserRepository userRepository;

    // =========================================================================
    // PUBLIC ENDPOINTS (no authentication required)
    // =========================================================================

    /**
     * Get current versions of all legal documents.
     * Called by the FE LegalDocumentsService on page load.
     */
    @GetMapping("/current")
    public ResponseEntity<List<LegalDocumentDtoOut>> getCurrentDocuments() {
        return ResponseEntity.ok(legalDocumentService.getCurrentDocuments());
    }

    /**
     * Get active cookie consent categories for the banner.
     * Returns category keys ordered by display_order.
     * FE uses i18n keys (COOKIE_BANNER.{consentType}_TITLE) for display text.
     */
    @GetMapping("/cookie-categories")
    public ResponseEntity<List<CookieCategoryDtoOut>> getActiveCookieCategories() {
        List<CookieCategoryDtoOut> categories = consentDefinitionRepository
                .findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(d -> new CookieCategoryDtoOut(d.getConsentType(), d.getDisplayOrder()))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(categories);
    }

    /**
     * Toggle a cookie category on/off — creates or destroys the category HMAC cookie.
     * Called by each banner toggle individually.
     */
    @PostMapping("/consent/category-toggle")
    public ResponseEntity<Void> toggleCookieCategory(
            @RequestBody @Valid CategoryToggleDtoIn dtoIn,
            HttpServletRequest request,
            HttpServletResponse response) {
        legalConsentService.toggleCategoryConsent(
                dtoIn.getCategoryType(), dtoIn.getEnabled(), dtoIn.getIsTrusted(),
                request, response);
        return ResponseEntity.ok().build();
    }

    /**
     * Reject all cookies — clears ALL HttpOnly cookies (session + consent + category).
     * Called by the cookie banner "Reject All" button.
     */
    @PostMapping("/reject-cookies")
    public ResponseEntity<Void> rejectAllCookies(HttpServletResponse response) {
        log.info("GDPR: Operation=reject_all_cookies, Purpose=cookie_rejection");
        tokenExchangeService.clearSessionCookies(response);
        consentCookieService.clearAllConsentCookies(response);
        // Also clear category cookies
        consentDefinitionRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .forEach(d -> consentCookieService.clearConsentCookie(response,
                        ConsentCookieService.categoryBannerCookieName(d.getConsentType())));
        return ResponseEntity.ok().build();
    }

    /**
     * Record anonymous consent from the cookie banner.
     * Creates a consent_record with user_id=NULL and sets an HMAC-signed cookie.
     */
    @PostMapping("/anonymous/consent")
    public ResponseEntity<AnonymousConsentDtoOut> recordAnonymousConsent(
            @RequestBody @Valid AnonymousConsentDtoIn dtoIn,
            HttpServletRequest request,
            HttpServletResponse response) {

        Long recordId = legalConsentService.recordAnonymousConsent(dtoIn, request, response);
        return ResponseEntity.ok(new AnonymousConsentDtoOut(recordId));
    }

    /**
     * Prepare an HMAC-signed consent cookie for a specific document type.
     * Called by the FE before registration (for both standard and OAuth flows).
     * The cookie survives OAuth redirects via SameSite=Lax.
     */
    @PostMapping("/consent/prepare")
    public ResponseEntity<Void> prepareConsentCookie(
            @RequestBody @Valid ConsentPrepareRequest prepareRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        legalConsentService.prepareConsentCookie(
                prepareRequest.getDocumentType(),
                prepareRequest.getVersion(),
                prepareRequest.getDocumentHash(),
                prepareRequest.getProof(),
                request,
                response);

        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // AUTHENTICATED ENDPOINTS
    // =========================================================================

    /**
     * Record consent for all required documents atomically (batch re-consent).
     * All 3 required document types (COOKIE_POLICY, TERMS_OF_SERVICE, PRIVACY_POLICY)
     * must be included. If any is missing, returns 400.
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/consent/record-batch")
    public ResponseEntity<Void> recordConsentBatch(
            @RequestBody @Valid ConsentRecordBatchDtoIn batchDtoIn,
            HttpServletRequest request) {

        Long userId = resolveCurrentUserId();
        legalConsentService.recordAuthenticatedConsentBatch(userId, batchDtoIn.getRecords(), request);
        return ResponseEntity.ok().build();
    }

    /**
     * Get consent status for the current user (for settings page).
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/consent/my")
    public ResponseEntity<ConsentStatusDtoOut> getMyConsentStatus() {
        Long userId = resolveCurrentUserId();
        boolean accepted = legalConsentService.hasAcceptedAllCurrentDocuments(userId);
        Integer daysRemaining = accepted ? null : legalConsentService.computeDaysToAcceptNewTerms();

        ConsentStatusDtoOut status = ConsentStatusDtoOut.builder()
                .newestConsentsAccepted(accepted)
                .daysToAcceptNewTerms(daysRemaining)
                .build();

        return ResponseEntity.ok(status);
    }

    private Long resolveCurrentUserId() {
        String firebaseUid = permissionUtils.getUserId();
        return userRepository.findByFirebaseUserId(firebaseUid)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
