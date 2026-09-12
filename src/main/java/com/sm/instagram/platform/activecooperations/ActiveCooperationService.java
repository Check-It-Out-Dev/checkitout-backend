package com.sm.instagram.platform.activecooperations;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityRepository;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunitySpecification;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Service for the "active cooperations" feature: pages over influencer ↔
 * company collaborations and applies role-aware filtering and serialization
 * views.
 *
 * <p>Three reading endpoints back the UI:
 * <ul>
 *   <li>{@link #getInfluencersToRate} — collaborations finished
 *       ({@link OpportunityStatus#DONE}) and waiting on a rating.</li>
 *   <li>{@link #getInfluencersToAccept} — fresh applications
 *       ({@link OpportunityStatus#APPLIED}) that the company should triage.</li>
 *   <li>{@link #getCollaborationsInProgress} — everything in flight; the
 *       output is wrapped in a Jackson view ({@link Views.InProgress_*}) so
 *       each role only sees the fields they should.</li>
 * </ul>
 *
 * <p>Two writing endpoints update ratings:
 * {@link #updateCompanyRating(Long, RateStatus)} and
 * {@link #updateInfluencerRating(Long, RateStatus)}. Both share the
 * permission + ownership checks via private helpers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActiveCooperationService {

    private static final Set<OpportunityStatus> IN_PROGRESS_STATUSES = Set.of(
            OpportunityStatus.ACCEPTED_BY_COMPANY,
            OpportunityStatus.ACCEPTED_BY_INFLUENCER,
            OpportunityStatus.CONTENT_POSTED,
            OpportunityStatus.CONTENT_REJECTED,
            OpportunityStatus.CONTENT_APPROVED,
            OpportunityStatus.REJECTED_BY_COMPANY,
            OpportunityStatus.REJECTED_BY_INFLUENCER
    );

    private final AppliedOpportunityRepository appliedOpportunityRepository;
    private final PermissionUtils permissionUtils;
    private final UserRepository userRepository;

    /**
     * Admin-only wrapper around {@link #getInfluencersToRate}. Throws
     * {@link InsufficientPermissionsException} when the caller is not an
     * admin; otherwise delegates verbatim.
     */
    // Carries the delegate's transaction itself: the call below is a self-invocation, which
    // never reaches the proxy, so getInfluencersToRate's readOnly=true was silently dropped.
    @Transactional(readOnly = true)
    public Page<CoopDto> getInfluencersToRateWithPermission(String filterRateStatusStr,
                                                            Pageable pageable) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Service=getInfluencersToRateWithPermission, FirebaseUID={}, FilterStatus={}, Purpose=permission_check",
                firebaseUid, filterRateStatusStr);

        if (!permissionUtils.isAdmin()) {
            log.warn("GDPR: ACCESS_DENIED Service=getInfluencersToRateWithPermission, FirebaseUID={}, Reason=not_admin",
                    firebaseUid);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    firebaseUid,
                    "getInfluencersToRateWithPermission",
                    "ActiveCooperation");
        }
        return getInfluencersToRate(filterRateStatusStr, pageable);
    }

    /**
     * Page through completed cooperations awaiting a rating. The supplied
     * {@code filterRateStatusStr} accepts {@code "ALL"} (or {@code null}) for
     * "no filter", or any {@link RateStatus} name (case-sensitive) to narrow.
     * Companies see only their own opportunities.
     */
    @Transactional(readOnly = true)
    public Page<CoopDto> getInfluencersToRate(String filterRateStatusStr,
                                              Pageable pageable) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Service=getInfluencersToRate, FirebaseUID={}, FilterStatus={}, Purpose=data_retrieval",
                firebaseUid, filterRateStatusStr);

        CoopFilter filter = new CoopFilter();
        filter.setOpportunityStatuses(List.of(OpportunityStatus.DONE));
        filter.setFilterRateStatus(parseRateStatus(filterRateStatusStr));
        narrowToCompanyIfApplicable(filter);

        Page<CoopDto> results = findAll(pageable, filter);
        log.info("GDPR: DataAccessed=influencer_ratings, FirebaseUID={}, RecordsReturned={}, Purpose=rating_management",
                firebaseUid, results.getContent().size());
        return results;
    }

    /**
     * Page through fresh applications. Optional thresholds restrict by
     * follower count and minimum positive ratings; passing {@code null} for
     * any of them skips that dimension. Validates that
     * {@code minFollowers <= maxFollowers}.
     */
    @Transactional(readOnly = true)
    public Page<CoopDto> getInfluencersToAccept(Integer minFollowers,
                                                Integer maxFollowers,
                                                Long minPositiveRates,
                                                Pageable pageable) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Service=getInfluencersToAccept, FirebaseUID={}, MinFollowers={}, MaxFollowers={}, Purpose=acceptance_filtering",
                firebaseUid, minFollowers, maxFollowers);

        if (minFollowers != null && maxFollowers != null && minFollowers > maxFollowers) {
            throw new ValidationTranslatableException(
                    "error.validation.invalid_argument",
                    "minFollowers cannot be greater than maxFollowers");
        }

        CoopFilter filter = new CoopFilter();
        filter.setOpportunityStatuses(List.of(OpportunityStatus.APPLIED));
        filter.setFilterRateStatus(RateStatus.DEFAULT);
        filter.setMinFollowers(minFollowers);
        filter.setMaxFollowers(maxFollowers);
        filter.setMinPositiveRates(minPositiveRates);
        narrowToCompanyIfApplicable(filter);

        Page<CoopDto> results = findAll(pageable, filter);
        log.info("GDPR: DataAccessed=influencer_applications, FirebaseUID={}, RecordsReturned={}, Purpose=acceptance_review",
                firebaseUid, results.getContent().size());
        return results;
    }

    /**
     * Page through in-flight cooperations and wrap the result in a Jackson
     * view scoped to the caller's role. When {@code statuses} is non-null it
     * must be a subset of {@link #IN_PROGRESS_STATUSES} or the request is
     * rejected.
     */
    @Transactional(readOnly = true)
    public MappingJacksonValue getCollaborationsInProgress(Pageable pageable,
                                                           List<OpportunityStatus> statuses) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Service=getCollaborationsInProgress, FirebaseUID={}, Statuses={}, Purpose=collaboration_tracking",
                firebaseUid, statuses);

        CoopFilter filter = new CoopFilter();
        filter.setOpportunityStatuses(resolveInProgressStatuses(statuses));
        filter.setFilterRateStatus(RateStatus.DEFAULT);

        if (permissionUtils.isInfluencer()) {
            filter.setInfluencerId(loadCurrentUser("Influencer user").getId());
        }
        narrowToCompanyIfApplicable(filter);

        Page<CoopDto> page = findAll(pageable, filter);
        log.info("GDPR: DataAccessed=collaborations_in_progress, FirebaseUID={}, RecordsReturned={}, Purpose=status_display",
                firebaseUid, page.getContent().size());

        MappingJacksonValue mapping = new MappingJacksonValue(page.getContent());
        mapping.setSerializationView(pickInProgressView());
        return mapping;
    }

    /**
     * Rate an influencer (set the company-side rating). Requires the caller
     * to be a company that owns the opportunity, or an admin.
     */
    @Transactional
    public CoopDto updateCompanyRating(Long appliedOpportunityId, RateStatus rating) {
        return updateRating(
                "updateCompanyRating",
                appliedOpportunityId,
                rating,
                permissionUtils.isCompany() || permissionUtils.isAdmin(),
                "Company user",
                (opp, company) -> {
                    if (!opp.getPartnershipOpportunity().getCompany().getId().equals(company.getId())) {
                        denyOwnership("updateCompanyRating", appliedOpportunityId);
                    }
                },
                permissionUtils::isCompany,
                AppliedOpportunity::setCompanyRateStatus);
    }

    /**
     * Rate the company (set the influencer-side rating). Requires the caller
     * to be the influencer who owns the opportunity, or an admin.
     */
    @Transactional
    public CoopDto updateInfluencerRating(Long appliedOpportunityId, RateStatus rating) {
        return updateRating(
                "updateInfluencerRating",
                appliedOpportunityId,
                rating,
                permissionUtils.isInfluencer() || permissionUtils.isAdmin(),
                "Influencer user",
                (opp, influencer) -> {
                    if (!opp.getInfluencer().getId().equals(influencer.getId())) {
                        denyOwnership("updateInfluencerRating", appliedOpportunityId);
                    }
                },
                permissionUtils::isInfluencer,
                AppliedOpportunity::setRateStatus);
    }

    /**
     * Run the underlying query: build a {@link AppliedOpportunitySpecification}
     * from the supplied filter, page over it, and map results to
     * {@link CoopDto}.
     */
    @Transactional(readOnly = true)
    public Page<CoopDto> findAll(Pageable pageable, CoopFilter filter) {
        Specification<AppliedOpportunity> spec = new AppliedOpportunitySpecification(filter);
        return appliedOpportunityRepository.findAll(spec, pageable)
                .map(CoopDto::mapToInfluencerCoopDto);
    }

    /**
     * Mutating helper used by {@link #narrowToCompanyIfApplicable(CoopFilter)}
     * — preserved as a public method because external callers (controllers)
     * may invoke it directly to set a company filter before paging.
     */
    public CoopFilter ifUserIsCompanyNarrowDownResults(CoopFilter filter) {
        narrowToCompanyIfApplicable(filter);
        return filter;
    }

    // ----- private helpers -----

    private void narrowToCompanyIfApplicable(CoopFilter filter) {
        if (permissionUtils.isCompany()) {
            filter.setCompanyId(loadCurrentUser("Company user").getId());
        }
    }

    private RateStatus parseRateStatus(String status) {
        if (status == null || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return RateStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ValidationTranslatableException(
                    "error.validation.invalid_argument",
                    "Invalid rate status");
        }
    }

    private List<OpportunityStatus> resolveInProgressStatuses(List<OpportunityStatus> requested) {
        if (requested == null) {
            return List.copyOf(IN_PROGRESS_STATUSES);
        }
        if (!IN_PROGRESS_STATUSES.containsAll(requested)) {
            throw new ValidationTranslatableException(
                    "error.validation.invalid_argument",
                    "Invalid opportunity statuses");
        }
        return requested;
    }

    private Class<?> pickInProgressView() {
        if (permissionUtils.isAdmin()) {
            return Views.InProgress_AdminView.class;
        }
        if (permissionUtils.isInfluencer()) {
            return Views.InProgress_InfluencerView.class;
        }
        if (permissionUtils.isCompany()) {
            return Views.InProgress_CompanyView.class;
        }
        return Views.Basic.class;
    }

    private User loadCurrentUser(String roleLabel) {
        String firebaseUid = permissionUtils.getUserId();
        return userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.business.item_not_found", roleLabel));
    }

    private CoopDto updateRating(String operation,
                                 Long appliedOpportunityId,
                                 RateStatus rating,
                                 boolean roleAllowed,
                                 String userLabel,
                                 BiConsumer<AppliedOpportunity, User> ownershipCheck,
                                 java.util.function.BooleanSupplier ownershipApplies,
                                 BiConsumer<AppliedOpportunity, RateStatus> applyRating) {
        String firebaseUid = permissionUtils.getUserId();
        log.warn("GDPR: UPDATE Service={}, FirebaseUID={}, OpportunityID={}, Rating={}, Purpose=rating_update",
                operation, firebaseUid, appliedOpportunityId, rating);

        if (!roleAllowed) {
            log.warn("GDPR: ACCESS_DENIED Service={}, FirebaseUID={}, Reason=role_not_allowed",
                    operation, firebaseUid);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    firebaseUid, operation,
                    "AppliedOpportunity#" + appliedOpportunityId);
        }

        AppliedOpportunity opportunity = appliedOpportunityRepository.findById(appliedOpportunityId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.business.item_not_found", "Applied opportunity"));

        if (ownershipApplies.getAsBoolean()) {
            User currentUser = loadCurrentUser(userLabel);
            ownershipCheck.accept(opportunity, currentUser);
        }

        applyRating.accept(opportunity, rating);
        AppliedOpportunity saved = appliedOpportunityRepository.save(opportunity);

        log.info("GDPR: UPDATE_COMPLETE Service={}, FirebaseUID={}, OpportunityID={}, Success=true",
                operation, firebaseUid, appliedOpportunityId);
        return CoopDto.mapToInfluencerCoopDto(saved);
    }

    private void denyOwnership(String operation, Long appliedOpportunityId) {
        throw new InsufficientPermissionsException(
                "error.auth.insufficient_permissions",
                permissionUtils.getUserId(),
                operation,
                "AppliedOpportunity#" + appliedOpportunityId);
    }
}
