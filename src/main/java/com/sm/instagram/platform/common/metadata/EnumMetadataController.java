package com.sm.instagram.platform.common.metadata;

import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.consent.ConsentAction;
import com.sm.instagram.platform.dictionary.DictionaryService;
import com.sm.instagram.platform.user.AccountStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Slf4j
@RestController
@PreAuthorize("hasAuthority('ADMIN')")
@RequestMapping("/metadata")
@RequiredArgsConstructor
@RateLimit(profile = RateLimitProfile.RELAXED, keyType = RateLimitKeyType.USER_ENDPOINT)  // 120 req/min for metadata endpoints
public class EnumMetadataController {

    private final DictionaryService dictionaryService;

    @GetMapping("/opportunity-statuses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<StatusMetadata>> getOpportunityStatuses(
            @RequestParam(defaultValue = "en") String lang) {

        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=getOpportunityStatuses, FirebaseUID={}, Language={}, Purpose=metadata_retrieval",
                firebaseUid, lang);
        log.info("Retrieving opportunity status metadata for language: {}", lang);
        Locale locale = Locale.forLanguageTag(lang);

        List<StatusMetadata> metadata = Arrays.stream(OpportunityStatus.values())
                .map(status -> StatusMetadata.builder()
                        .value(status.name())
                        .label(status.getLabel(dictionaryService, locale))
                        .description(status.getDescription(dictionaryService, locale))
                        .colorTheme(status.getColorTheme())
                        .icon(status.getIcon())
                        .aliases(status.getAliases())
                        .possibleTransitions(status.getPossibleTransitions().stream()
                                .map(Enum::name)
                                .toList())
                        .isTerminal(status.isTerminalStatus())
                        .isSuccessful(status.isSuccessfulCompletion())
                        .build())
                .toList();

        log.info("GDPR: DataAccessed=opportunity_status_metadata, FirebaseUID={}, RecordCount={}, Purpose=enum_metadata",
                firebaseUid, metadata.size());
        log.debug("Retrieved {} opportunity status metadata entries", metadata.size());
        return ResponseEntity.ok(metadata);
    }

    @GetMapping("/account-statuses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<StatusMetadata>> getAccountStatuses(
            @RequestParam(defaultValue = "en") String lang) {

        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=getAccountStatuses, FirebaseUID={}, Language={}, Purpose=metadata_retrieval",
                firebaseUid, lang);
        log.info("Retrieving account status metadata for language: {}", lang);
        Locale locale = Locale.forLanguageTag(lang);

        List<StatusMetadata> metadata = Arrays.stream(AccountStatus.values())
                .map(status -> StatusMetadata.builder()
                        .value(status.name())
                        .label(status.getLabel(dictionaryService, locale))
                        .description(status.getDescription(dictionaryService, locale))
                        .colorTheme(status.getColorTheme())
                        .icon(status.getIcon())
                        .aliases(status.getAliases())
                        .possibleTransitions(status.getPossibleTransitions().stream()
                                .map(Enum::name)
                                .toList())
                        .isTerminal(status.isTerminal())
                        .canLogin(status.canLogin())
                        .isActive(status.isActive())
                        .build())
                .toList();

        log.info("GDPR: DataAccessed=account_status_metadata, FirebaseUID={}, RecordCount={}, Purpose=enum_metadata",
                firebaseUid, metadata.size());
        log.debug("Retrieved {} account status metadata entries", metadata.size());
        return ResponseEntity.ok(metadata);
    }

    @GetMapping("/consent-actions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<StatusMetadata>> getConsentActions(
            @RequestParam(defaultValue = "en") String lang) {

        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=getConsentActions, FirebaseUID={}, Language={}, Purpose=metadata_retrieval",
                firebaseUid, lang);
        log.info("Retrieving consent action metadata for language: {}", lang);
        Locale locale = Locale.forLanguageTag(lang);

        List<StatusMetadata> metadata = Arrays.stream(ConsentAction.values())
                .map(action -> StatusMetadata.builder()
                        .value(action.name())
                        .label(action.getLabel(dictionaryService, locale))
                        .description(action.getDescription(dictionaryService, locale))
                        .colorTheme(action.getColorTheme())
                        .icon(action.getIcon())
                        .aliases(action.getAliases())
                        .isPositiveAction(action.isPositiveAction())
                        .isNegativeAction(action.isNegativeAction())
                        .isModificationAction(action.isModificationAction())
                        .build())
                .toList();

        log.info("GDPR: DataAccessed=consent_action_metadata, FirebaseUID={}, RecordCount={}, Purpose=enum_metadata",
                firebaseUid, metadata.size());
        log.debug("Retrieved {} consent action metadata entries", metadata.size());
        return ResponseEntity.ok(metadata);
    }

    @GetMapping("/opportunity-statuses/{status}/transitions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getOpportunityStatusTransitions(@PathVariable String status) {
        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=getOpportunityStatusTransitions, FirebaseUID={}, Status={}, Purpose=workflow_metadata",
                firebaseUid, status);
        log.info("Retrieving possible transitions for opportunity status: {}", status);

        try {
            OpportunityStatus currentStatus = OpportunityStatus.valueOf(status.toUpperCase());
            List<String> transitions = currentStatus.getPossibleTransitions()
                    .stream()
                    .map(Enum::name)
                    .toList();

            log.info("GDPR: DataAccessed=status_transitions, FirebaseUID={}, TransitionCount={}, Purpose=workflow_rules",
                    firebaseUid, transitions.size());
            log.debug("Found {} possible transitions for status: {}", transitions.size(), status);
            return ResponseEntity.ok(transitions);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid opportunity status requested: {}", status);
            throw new ValidationTranslatableException("error.validation.invalid_enum_value", status);
        }
    }

    @GetMapping("/account-statuses/{status}/transitions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getAccountStatusTransitions(@PathVariable String status) {
        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=getAccountStatusTransitions, FirebaseUID={}, Status={}, Purpose=workflow_metadata",
                firebaseUid, status);
        log.info("Retrieving possible transitions for account status: {}", status);

        try {
            AccountStatus currentStatus = AccountStatus.valueOf(status.toUpperCase());
            List<String> transitions = currentStatus.getPossibleTransitions()
                    .stream()
                    .map(Enum::name)
                    .toList();

            log.info("GDPR: DataAccessed=status_transitions, FirebaseUID={}, TransitionCount={}, Purpose=workflow_rules",
                    firebaseUid, transitions.size());
            log.debug("Found {} possible transitions for status: {}", transitions.size(), status);
            return ResponseEntity.ok(transitions);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid account status requested: {}", status);
            throw new ValidationTranslatableException("error.validation.invalid_enum_value", status);
        }
    }

    @GetMapping("/opportunity-statuses/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getActiveOpportunityStatuses() {
        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=getActiveOpportunityStatuses, FirebaseUID={}, Purpose=metadata_retrieval", firebaseUid);
        log.info("Retrieving active opportunity statuses");

        List<String> activeStatuses = OpportunityStatus.getActiveStatuses()
                .map(Enum::name)
                .toList();

        log.info("GDPR: DataAccessed=active_opportunity_statuses, FirebaseUID={}, RecordCount={}, Purpose=enum_metadata",
                firebaseUid, activeStatuses.size());
        log.debug("Found {} active opportunity statuses", activeStatuses.size());
        return ResponseEntity.ok(activeStatuses);
    }

    @GetMapping("/opportunity-statuses/completed")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getCompletedOpportunityStatuses() {
        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=getCompletedOpportunityStatuses, FirebaseUID={}, Purpose=metadata_retrieval", firebaseUid);
        log.info("Retrieving completed opportunity statuses");

        List<String> completedStatuses = OpportunityStatus.getCompletedStatuses()
                .map(Enum::name)
                .toList();

        log.info("GDPR: DataAccessed=completed_opportunity_statuses, FirebaseUID={}, RecordCount={}, Purpose=enum_metadata",
                firebaseUid, completedStatuses.size());
        log.debug("Found {} completed opportunity statuses", completedStatuses.size());
        return ResponseEntity.ok(completedStatuses);
    }

    // ===== HELPER METHODS =====

    /**
     * Gets the authenticated admin UID from the security context.
     *
     * @return The Firebase UID of the authenticated admin user
     * @throws ResourceNotFoundException if authentication context is missing
     */
    private String getAuthenticatedAdminUid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            log.error("Authentication not available in security context");
            throw new AuthenticationTranslatableException("error.auth.not_authenticated");
        }
        return auth.getPrincipal().toString();
    }
}
