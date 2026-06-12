package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.common.base.BaseController;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.common.translation.TranslationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * REST controller for the {@link AppliedOpportunity} aggregate — influencer
 * applications to partnership campaigns.
 *
 * <p>The controller stays a thin wrapper around {@link AppliedOpportunityService}
 * and the inherited {@link BaseController} surface; it is responsible for
 * extracting the authenticated principal, parsing {@code Accept-Language}
 * for localized projections, and emitting GDPR audit log lines on every
 * state-changing operation.
 *
 * <p>Rate limits are tagged at three different points: the class-level
 * {@code @RateLimit} provides the default {@link RateLimitProfile#STANDARD}
 * envelope, while specific endpoints override with stricter (status mutation
 * is 30/min) or laxer ({@link RateLimitProfile#HIGH} for read-only history)
 * profiles.
 */
@Slf4j
@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("applied-opportunity")
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
public class AppliedOpportunityController
        extends BaseController<AppliedOpportunity, Long, AppliedOpportunityDtoIn, AppliedOpportunityDtoOut> {

    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("pl");

    private final AppliedOpportunityService appliedOpportunityService;
    private final AppliedOpportunityStatusHistoryService statusHistoryService;
    private final TranslationService translationService;
    private final HttpServletRequest request;

    public AppliedOpportunityController(AppliedOpportunityService appliedOpportunityService,
                                        AppliedOpportunityStatusHistoryService statusHistoryService,
                                        TranslationService translationService,
                                        HttpServletRequest request) {
        super(AppliedOpportunity.class);
        this.appliedOpportunityService = appliedOpportunityService;
        this.statusHistoryService = statusHistoryService;
        this.translationService = translationService;
        this.request = request;
    }

    /**
     * Resolve the caller's preferred locale from the {@code Accept-Language}
     * header, falling back to Polish ({@code pl}) when the header is absent
     * or unparseable.
     */
    private Locale getLocaleFromRequest() {
        String header = request.getHeader("Accept-Language");
        if (header == null || header.isEmpty()) {
            return DEFAULT_LOCALE;
        }
        String firstTag = header.split(",")[0].split(";")[0].trim();
        if (firstTag.isEmpty()) {
            return DEFAULT_LOCALE;
        }
        return Locale.forLanguageTag(firstTag);
    }

    /**
     * Translate OpportunityStatus enum to DTO
     */
    private OpportunityStatusDtoOut translateOpportunityStatus(OpportunityStatus opportunityStatus, Locale locale) {
        if (opportunityStatus == null) {
            return null;
        }
        
        String translatedLabel = translationService.translateOpportunityStatus(opportunityStatus.name(), locale);
        
        return OpportunityStatusDtoOut.builder()
                .value(opportunityStatus.name())
                .label(translatedLabel)
                .originalLabel(opportunityStatus.name())
                .colorTheme(opportunityStatus.getColorTheme())
                .icon(opportunityStatus.getIcon())
                .isTerminal(opportunityStatus.isTerminalStatus())
                .isSuccessful(opportunityStatus.isSuccessfulCompletion())
                .build();
    }

    /**
     * Translate RateStatus enum to DTO
     */
    private RateStatusDtoOut translateRateStatus(RateStatus rateStatus, Locale locale) {
        if (rateStatus == null) {
            return null;
        }

        String translatedLabel = translationService.translateRateStatus(rateStatus.name(), locale);
        
        return RateStatusDtoOut.builder()
                .value(rateStatus.name())
                .label(translatedLabel)
                .originalLabel(rateStatus.name())
                .colorTheme(rateStatus.getColorTheme())
                .icon(rateStatus.getIcon())
                .build();
    }

    @Override
    protected BaseService<AppliedOpportunity, Long, AppliedOpportunityDtoIn> getService() {
        return appliedOpportunityService;
    }

    // Override getById to use service DTO method
    @Override
    @GetMapping("/{id}")
    public ResponseEntity<AppliedOpportunityDtoOut> getById(@PathVariable Long id) {
        String firebaseUid = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        
        log.info("GDPR: Operation=getAppliedOpportunityById, FirebaseUID={}, OpportunityID={}, Purpose=opportunity_retrieval", 
            firebaseUid, id);
        log.info("AppliedOpportunityController: Retrieving AppliedOpportunity with ID: {}", id);
        long startTime = System.currentTimeMillis();

        try {
            Locale locale = getLocaleFromRequest();
            AppliedOpportunityDtoOut result = appliedOpportunityService.findByIdAsDto(id);
            
            log.info("GDPR: DataAccessed=applied_opportunity.all_fields, FirebaseUID={}, OpportunityID={}, Purpose=display", 
                firebaseUid, id);

            // Translate enum fields in the DTO
            AppliedOpportunity entity = appliedOpportunityService.findById(id);
            if (entity.getOpportunityStatus() != null) {
                OpportunityStatusDtoOut translatedOpportunityStatus = translateOpportunityStatus(entity.getOpportunityStatus(), locale);
                result.setOpportunityStatus(translatedOpportunityStatus);
            }
            
            // Translate rate status enums
            appliedOpportunityService.mapRateStatusBasedOnRole(entity,result);

            long duration = System.currentTimeMillis() - startTime;
            log.info("AppliedOpportunityController: Successfully retrieved AppliedOpportunity with ID: {} in {}ms", id, duration);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("GDPR: Operation=getAppliedOpportunityById_failed, FirebaseUID={}, OpportunityID={}, Error={}", 
                firebaseUid, id, e.getMessage());
            log.error("AppliedOpportunityController: Failed to retrieve AppliedOpportunity with ID: {} after {}ms - {}", id, duration, e.getMessage());
            throw e;
        }
    }

    @Override
    @PostMapping
    @RateLimit(value = 20, duration = 3600)  // 20 applications per hour to prevent spam
    public ResponseEntity<AppliedOpportunityDtoOut> create(@Valid @RequestBody AppliedOpportunityDtoIn dto) {
        String firebaseUid = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        
        log.info("GDPR: Operation=createAppliedOpportunity, FirebaseUID={}, Purpose=opportunity_application", firebaseUid);
        log.info("Creating new applied opportunity");
        log.debug("Creating applied opportunity with data: {}", dto);
        long startTime = System.currentTimeMillis();
        try {
            AppliedOpportunityDtoOut result = appliedOpportunityService.saveAsDto(dto);
            
            log.info("GDPR: DataCreated=applied_opportunity, FirebaseUID={}, OpportunityID={}, Purpose=application_submitted", 
                firebaseUid, result.getId());

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully created applied opportunity in {}ms", duration);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("GDPR: Operation=createAppliedOpportunity_failed, FirebaseUID={}, Error={}", 
                firebaseUid, e.getMessage());
            log.error("Failed to create applied opportunity after {}ms - {}", duration, e.getMessage());
            throw e;
        }
    }

    /**
     * Updates company rating status for an applied opportunity
     */
    @PutMapping("/{id}/company-rating")
    @RateLimit(value = 30, duration = 60)  // 30 rating updates per minute
    public ResponseEntity<AppliedOpportunityDtoOut> updateCompanyRating(
            @PathVariable Long id,
            @RequestParam RateStatus rating) {
        String firebaseUid = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
            
        log.info("GDPR: Operation=updateCompanyRating, FirebaseUID={}, OpportunityID={}, NewRating={}, Purpose=rating_update", 
            firebaseUid, id, rating);
        log.info("Updating company rating for applied opportunity ID: {} to: {}", id, rating);

        try {
            AppliedOpportunityDtoOut result = appliedOpportunityService.updateCompanyRatingAsDto(id, rating);
            
            log.info("GDPR: DataModified=company_rating, FirebaseUID={}, OpportunityID={}, Purpose=rating_saved", 
                firebaseUid, id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("GDPR: Operation=updateCompanyRating_failed, FirebaseUID={}, OpportunityID={}, Error={}", 
                firebaseUid, id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Updates influencer rating status for an applied opportunity
     */
    @PutMapping("/{id}/influencer-rating")
    @RateLimit(value = 30, duration = 60)  // 30 rating updates per minute
    public ResponseEntity<AppliedOpportunityDtoOut> updateInfluencerRating(
            @PathVariable Long id,
            @RequestParam RateStatus rating) {
        String firebaseUid = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
            
        log.info("GDPR: Operation=updateInfluencerRating, FirebaseUID={}, OpportunityID={}, NewRating={}, Purpose=rating_update", 
            firebaseUid, id, rating);
        log.info("Updating influencer rating for applied opportunity ID: {} to: {}", id, rating);

        try {
            AppliedOpportunityDtoOut result = appliedOpportunityService.updateInfluencerRatingAsDto(id, rating);
            
            log.info("GDPR: DataModified=influencer_rating, FirebaseUID={}, OpportunityID={}, Purpose=rating_saved", 
                firebaseUid, id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("GDPR: Operation=updateInfluencerRating_failed, FirebaseUID={}, OpportunityID={}, Error={}", 
                firebaseUid, id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Patch a subset of fields on an applied opportunity. Currently only
     * rating-related fields flow through this endpoint, but the underlying
     * {@code patchAsDto} contract is generic — any allow-listed scalar can
     * be updated. Exception path logs error + duration before re-throwing.
     */
    @PatchMapping("/rate/update/{appliedOpportunityId}")
    public ResponseEntity<AppliedOpportunityDtoOut> updateRateStatus(
            @PathVariable Long appliedOpportunityId,
            @RequestBody Map<String, Object> updates) {

        log.info("Updating rate status for applied opportunity ID: {} with fields: {}",
                appliedOpportunityId, updates.keySet());
        log.debug("Rate status update data: {}", updates);

        long startedAt = System.currentTimeMillis();
        try {
            @SuppressWarnings("unchecked")
            AppliedOpportunityDtoOut result = (AppliedOpportunityDtoOut)
                    getService().patchAsDto(appliedOpportunityId, updates);

            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.info("Successfully updated rate status for applied opportunity ID: {} in {}ms",
                    appliedOpportunityId, elapsedMs);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.error("Failed to update rate status for applied opportunity ID: {} after {}ms - {}",
                    appliedOpportunityId, elapsedMs, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Advance an applied opportunity along its state machine. {@code accept}
     * drives whether the transition is the "yes" or "no" branch as defined
     * by {@link com.sm.instagram.platform.appliedopportunities.OpportunityStatus#getNextStatus(com.sm.instagram.platform.appliedopportunities.OpportunityStatus, boolean)}.
     * Capped at 30 calls / minute since each call mutates a state machine
     * and triggers downstream side-effects (notifications, audit history).
     */
    @PatchMapping("/status/update/{appliedOpportunityId}")
    @RateLimit(value = 30, duration = 60)
    public ResponseEntity<AppliedOpportunityDtoOut> updateOpportunityStatus(
            @PathVariable Long appliedOpportunityId,
            @RequestParam boolean accept) {

        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal()
                .toString();
        String decisionLabel = accept ? "ACCEPTED" : "REJECTED";

        log.info("GDPR: Operation=updateOpportunityStatus, FirebaseUID={}, OpportunityID={}, Accept={}, Purpose=status_update",
                firebaseUid, appliedOpportunityId, accept);
        log.info("Updating opportunity status for applied opportunity ID: {} - accept: {}",
                appliedOpportunityId, accept);

        long startedAt = System.currentTimeMillis();
        try {
            AppliedOpportunityDtoOut result =
                    appliedOpportunityService.updateOpportunityStatusAsDto(appliedOpportunityId, accept);

            log.info("GDPR: DataModified=opportunity_status, FirebaseUID={}, OpportunityID={}, NewStatus={}, Purpose=status_changed",
                    firebaseUid, appliedOpportunityId, decisionLabel);

            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.info("Successfully updated opportunity status for applied opportunity ID: {} to {} in {}ms",
                    appliedOpportunityId, decisionLabel, elapsedMs);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.error("GDPR: Operation=updateOpportunityStatus_failed, FirebaseUID={}, OpportunityID={}, Error={}",
                    firebaseUid, appliedOpportunityId, ex.getMessage());
            log.error("Failed to update opportunity status for applied opportunity ID: {} after {}ms - {}",
                    appliedOpportunityId, elapsedMs, ex.getMessage());
            throw ex;
        }
    }

    @GetMapping("/{appliedOpportunityId}/status-history")
    @RateLimit(profile = RateLimitProfile.HIGH)  // 300 req/min for read-only history
    public ResponseEntity<List<AppliedOpportunityStatusHistoryDtoOut>> getStatusHistory(
            @PathVariable Long appliedOpportunityId) {
        log.info("Retrieving status history for applied opportunity ID: {}", appliedOpportunityId);
        long startTime = System.currentTimeMillis();

        try {
            // Let the service handle permission validation
            List<AppliedOpportunityStatusHistory> history = statusHistoryService.getStatusHistory(appliedOpportunityId);
            List<AppliedOpportunityStatusHistoryDtoOut> historyDtos = history.stream()
                    .map(AppliedOpportunityStatusHistoryDtoOut::fromEntity)
                    .toList();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully retrieved {} status history entries for applied opportunity ID: {} in {}ms",
                    historyDtos.size(), appliedOpportunityId, duration);

            return ResponseEntity.ok(historyDtos);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to retrieve status history for applied opportunity ID: {} after {}ms - {}",
                    appliedOpportunityId, duration, e.getMessage());
            throw e;
        }
    }

    @GetMapping("/{appliedOpportunityId}/status-history/paged")
    @RateLimit(profile = RateLimitProfile.HIGH)  // 300 req/min for read-only history
    public ResponseEntity<Page<AppliedOpportunityStatusHistoryDtoOut>> getStatusHistoryPaged(
            @PathVariable Long appliedOpportunityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "changedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {
        log.info("Retrieving paged status history for applied opportunity ID: {} (page: {}, size: {})",
                appliedOpportunityId, page, size);
        long startTime = System.currentTimeMillis();

        try {
            // Enforce max page size limit
            if (size > 100) {
                size = 100;
                log.warn("Page size {} exceeds maximum allowed (100), limiting to 100", size);
            }

            Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

            // Let the service handle permission validation
            Page<AppliedOpportunityStatusHistory> historyPage = statusHistoryService.getStatusHistory(appliedOpportunityId, pageable);
            Page<AppliedOpportunityStatusHistoryDtoOut> historyDtoPage = historyPage.map(AppliedOpportunityStatusHistoryDtoOut::fromEntity);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully retrieved paged status history for applied opportunity ID: {} in {}ms ({} total elements)",
                    appliedOpportunityId, duration, historyDtoPage.getTotalElements());

            return ResponseEntity.ok(historyDtoPage);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to retrieve paged status history for applied opportunity ID: {} after {}ms - {}",
                    appliedOpportunityId, duration, e.getMessage());
            throw e;
        }
    }

    @Override
    @GetMapping("/paged")
    public ResponseEntity<Page<AppliedOpportunityDtoOut>> findPaginated(Pageable pageable, @RequestParam Map<String, String> filters) {
        String firebaseUid = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
            
        log.info("GDPR: Operation=getAppliedOpportunitiesPaged, FirebaseUID={}, Page={}, Size={}, Purpose=list_retrieval", 
            firebaseUid, pageable.getPageNumber(), pageable.getPageSize());
        log.info("Retrieving paginated applied opportunities - page: {}, size: {}, filters: {}",
                pageable.getPageNumber(), pageable.getPageSize(), filters.keySet());
        long startTime = System.currentTimeMillis();

        Locale locale = getLocaleFromRequest();

        // Remove pagination and sorting parameters from filters
        filters.remove("page");
        filters.remove("size");
        filters.remove("sort");
        filters.remove("direction");
        filters.remove("sortBy");
        filters.remove("sortDirection");

        // Use the new method that returns DTOs directly with all conversions done inside transaction
        Page<AppliedOpportunityDtoOut> dtoPage = appliedOpportunityService.getDataPagedAndFilteredAsDtos(pageable, filters);

        // Translate enum fields for each DTO in the page
        Page<AppliedOpportunity> entityPage = appliedOpportunityService.getDataPagedAndFiltered(pageable, filters);
        Page<AppliedOpportunityDtoOut> translatedDtoPage = dtoPage.map(dto -> {
            // Find the corresponding entity to get enum values
            AppliedOpportunity entity = entityPage.getContent().stream()
                    .filter(e -> e.getId().equals(dto.getId()))
                    .findFirst()
                    .orElse(null);
            
            if (entity != null) {
                if (entity.getOpportunityStatus() != null) {
                    OpportunityStatusDtoOut translatedOpportunityStatus = translateOpportunityStatus(entity.getOpportunityStatus(), locale);
                    dto.setOpportunityStatus(translatedOpportunityStatus);
                }

                appliedOpportunityService.mapRateStatusBasedOnRole(entity,dto);
            }
            
            return dto;
        });

        long duration = System.currentTimeMillis() - startTime;
        log.info("GDPR: DataAccessed=applied_opportunities[], FirebaseUID={}, RecordCount={}, Purpose=paginated_display", 
            firebaseUid, dtoPage.getNumberOfElements());
        log.info("Successfully retrieved {} applied opportunities (total: {}) in {}ms",
                translatedDtoPage.getNumberOfElements(), translatedDtoPage.getTotalElements(), duration);

        return ResponseEntity.ok(translatedDtoPage);
    }

    /**
     * Gets statistics for applied opportunities grouped by status categories
     */
    @GetMapping("/statistics")
    @RateLimit(profile = RateLimitProfile.HIGH)  // 300 req/min for read-only statistics
    public ResponseEntity<AppliedOpportunityStatisticsDto> getStatistics() {
        String firebaseUid = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
            
        log.info("GDPR: Operation=getAppliedOpportunityStatistics, FirebaseUID={}, Purpose=statistics_retrieval", firebaseUid);
        log.info("Retrieving applied opportunity statistics for current user");
        long startTime = System.currentTimeMillis();

        try {
            AppliedOpportunityStatisticsDto statistics = appliedOpportunityService.getAppliedOpportunityStatistics();
            
            log.info("GDPR: DataAccessed=opportunity_statistics, FirebaseUID={}, DataPoints=[inProgress,new,done,total], Purpose=dashboard_display", 
                firebaseUid);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully retrieved applied opportunity statistics in {}ms - inProgress: {}, new: {}, done: {}, total: {}",
                    duration, statistics.getInProgress(), statistics.getNewOpportunities(), 
                    statistics.getDone(), statistics.getTotal());

            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("GDPR: Operation=getAppliedOpportunityStatistics_failed, FirebaseUID={}, Error={}",
                firebaseUid, e.getMessage());
            log.error("Failed to retrieve applied opportunity statistics after {}ms - {}", duration, e.getMessage());
            throw e;
        }
    }

    /**
     * CIO-373: Get payment contact information for the other party in a collaboration.
     * Only available at TO_BE_PAID or DONE status.
     * Email is always returned; phone is conditional on the other party's sharePhoneForPayments preference.
     */
    @GetMapping("/{id}/payment-contact")
    @RateLimit(profile = RateLimitProfile.STANDARD)
    @Operation(summary = "Get contact data for payment coordination")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contact data retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "User is not a party to this collaboration or status is not at payment stage"),
            @ApiResponse(responseCode = "404", description = "Applied opportunity not found")
    })
    public ResponseEntity<PaymentContactDto> getPaymentContact(@PathVariable Long id) {
        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        log.info("GDPR: Operation=getPaymentContact, FirebaseUID={}, OpportunityID={}, Purpose=payment_coordination",
                firebaseUid, id);

        try {
            PaymentContactDto contact = appliedOpportunityService.getPaymentContact(id);

            log.info("GDPR: DataAccessed=payment_contact, FirebaseUID={}, OpportunityID={}, Purpose=payment_coordination",
                    firebaseUid, id);

            return ResponseEntity.ok(contact);
        } catch (Exception e) {
            log.error("GDPR: Operation=getPaymentContact_failed, FirebaseUID={}, OpportunityID={}, Error={}",
                    firebaseUid, id, e.getMessage());
            throw e;
        }
    }
}
