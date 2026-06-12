package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.activecooperations.CoopFilter;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA {@link Specification} that materializes a {@link CoopFilter} into a
 * {@code WHERE} clause over {@link AppliedOpportunity}.
 *
 * <p>Each non-null field on the filter contributes one or more
 * {@link Predicate}s, all combined with {@code AND}. Joins are added lazily
 * — we only walk into {@code partnershipOpportunity → company} when a
 * company filter is requested, only into {@code influencer → socialConnections
 * → platform} when a follower-count filter is requested, and so on. The
 * "positive rates" filter issues a correlated sub-select; the others stay in
 * the main query.
 *
 * <p>The Instagram platform is hard-coded by name (matching the value seeded
 * in the {@code platform} table) because follower counts only make sense
 * relative to a specific platform.
 */
@RequiredArgsConstructor
public class AppliedOpportunitySpecification implements Specification<AppliedOpportunity> {

    private static final String INSTAGRAM_PLATFORM_NAME = "Instagram";

    private final CoopFilter filter;

    @Override
    public Predicate toPredicate(@NonNull Root<AppliedOpportunity> root,
                                 @NonNull CriteriaQuery<?> query,
                                 @NonNull CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        addStatusPredicate(root, predicates);
        addRateStatusPredicate(root, cb, predicates);
        addCompanyOwnerPredicate(root, cb, predicates);
        addInfluencerOwnerPredicate(root, cb, predicates);

        Join<AppliedOpportunity, User> influencerJoin =
                addFollowerRangePredicates(root, cb, predicates);
        addMinPositiveRatesPredicate(root, query, cb, predicates, influencerJoin);

        addPartnershipPredicate(root, cb, predicates);

        return cb.and(predicates.toArray(Predicate[]::new));
    }

    // ----- per-filter helpers -----

    private void addStatusPredicate(Root<AppliedOpportunity> root,
                                    List<Predicate> out) {
        List<OpportunityStatus> statuses = filter.getOpportunityStatuses();
        if (statuses != null && !statuses.isEmpty()) {
            out.add(root.get("opportunityStatus").in(statuses));
        }
    }

    private void addRateStatusPredicate(Root<AppliedOpportunity> root,
                                        CriteriaBuilder cb,
                                        List<Predicate> out) {
        RateStatus rateStatus = filter.getFilterRateStatus();
        if (rateStatus != null) {
            out.add(cb.equal(root.get("rateStatus"), rateStatus));
        }
    }

    private void addCompanyOwnerPredicate(Root<AppliedOpportunity> root,
                                          CriteriaBuilder cb,
                                          List<Predicate> out) {
        Long companyId = filter.getCompanyId();
        if (companyId == null) {
            return;
        }
        Join<AppliedOpportunity, PartnershipOpportunity> partnershipJoin =
                root.join("partnershipOpportunity");
        Join<PartnershipOpportunity, User> companyJoin = partnershipJoin.join("company");
        out.add(cb.equal(companyJoin.get("id"), companyId));
    }

    private void addInfluencerOwnerPredicate(Root<AppliedOpportunity> root,
                                             CriteriaBuilder cb,
                                             List<Predicate> out) {
        Long influencerId = filter.getInfluencerId();
        if (influencerId == null) {
            return;
        }
        Join<AppliedOpportunity, User> influencerJoin = root.join("influencer");
        out.add(cb.equal(influencerJoin.get("id"), influencerId));
    }

    private Join<AppliedOpportunity, User> addFollowerRangePredicates(
            Root<AppliedOpportunity> root,
            CriteriaBuilder cb,
            List<Predicate> out) {

        Integer minFollowers = filter.getMinFollowers();
        Integer maxFollowers = filter.getMaxFollowers();
        if (minFollowers == null && maxFollowers == null) {
            return null;
        }

        Join<AppliedOpportunity, User> influencerJoin =
                root.join("influencer", JoinType.INNER);
        Join<User, UserSocialConnection> socialJoin =
                influencerJoin.join("socialConnections", JoinType.INNER);
        Join<UserSocialConnection, Platform> platformJoin =
                socialJoin.join("platform", JoinType.INNER);

        out.add(cb.equal(platformJoin.get("name"), INSTAGRAM_PLATFORM_NAME));
        if (minFollowers != null) {
            out.add(cb.greaterThanOrEqualTo(socialJoin.get("followersCount"), minFollowers));
        }
        if (maxFollowers != null) {
            out.add(cb.lessThanOrEqualTo(socialJoin.get("followersCount"), maxFollowers));
        }
        return influencerJoin;
    }

    private void addMinPositiveRatesPredicate(Root<AppliedOpportunity> root,
                                              CriteriaQuery<?> query,
                                              CriteriaBuilder cb,
                                              List<Predicate> out,
                                              Join<AppliedOpportunity, User> existingInfluencerJoin) {
        Long minPositiveRates = filter.getMinPositiveRates();
        if (minPositiveRates == null) {
            return;
        }
        Join<AppliedOpportunity, User> influencerJoin =
                existingInfluencerJoin != null
                        ? existingInfluencerJoin
                        : root.join("influencer", JoinType.INNER);

        Subquery<Long> positiveRatesSubquery = query.subquery(Long.class);
        Root<AppliedOpportunity> subRoot = positiveRatesSubquery.from(AppliedOpportunity.class);
        Join<AppliedOpportunity, User> subInfluencerJoin = subRoot.join("influencer");

        positiveRatesSubquery.select(cb.count(subRoot))
                .where(cb.and(
                        cb.equal(subInfluencerJoin.get("id"), influencerJoin.get("id")),
                        cb.equal(subRoot.get("rateStatus"), RateStatus.POSITIVE)));

        out.add(cb.greaterThanOrEqualTo(positiveRatesSubquery, minPositiveRates));
    }

    private void addPartnershipPredicate(Root<AppliedOpportunity> root,
                                         CriteriaBuilder cb,
                                         List<Predicate> out) {
        Long partnershipOpportunityId = filter.getPartnershipOpportunityId();
        if (partnershipOpportunityId == null) {
            return;
        }
        Join<AppliedOpportunity, PartnershipOpportunity> partnershipJoin =
                root.join("partnershipOpportunity");
        out.add(cb.equal(partnershipJoin.get("id"), partnershipOpportunityId));
    }
}
