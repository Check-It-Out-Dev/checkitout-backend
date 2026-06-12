package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.activecooperations.CoopFilter;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunitySpecification;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AppliedOpportunitySpecification.
 * Tests JPA Specification/Criteria API predicate building logic.
 * No Spring context needed - testing pure Java logic with mocked JPA interfaces.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppliedOpportunitySpecification Unit Tests")
class AppliedOpportunitySpecificationUnitTest {

    @Mock
    private Root<AppliedOpportunity> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder criteriaBuilder;

    @Mock
    private Predicate predicate;

    @Mock
    private Predicate andPredicate;

    @Mock
    private Path<Object> path;

    @Mock
    private CriteriaBuilder.In<Object> inPredicate;

    @Mock
    private Join<AppliedOpportunity, PartnershipOpportunity> partnershipJoin;

    @Mock
    private Join<PartnershipOpportunity, User> companyJoin;

    @Mock
    private Join<AppliedOpportunity, User> influencerJoin;

    @Mock
    private Join<User, UserSocialConnection> socialJoin;

    @Mock
    private Join<UserSocialConnection, Platform> platformJoin;

    @Mock
    private Subquery<Long> subquery;

    @Mock
    private Root<AppliedOpportunity> subRoot;

    @Mock
    private Join<AppliedOpportunity, User> subInfluencerJoin;

    private CoopFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CoopFilter();

        // Setup common mock behavior with lenient stubbing for flexibility
        lenient().when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(andPredicate);
        lenient().when(criteriaBuilder.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        lenient().when(criteriaBuilder.equal(any(Path.class), any())).thenReturn(predicate);
        lenient().when(root.get(anyString())).thenReturn(path);
    }

    @Nested
    @DisplayName("toPredicate()")
    class ToPredicateTests {

        @Test
        @DisplayName("should build empty predicate when filter has no criteria")
        void shouldBuildEmptyPredicateWhenNoFilters() {
            // Given
            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isEqualTo(andPredicate);
            verify(criteriaBuilder).and(any(Predicate[].class));
        }

        @Test
        @DisplayName("should build predicate with opportunity statuses filter")
        void shouldBuildPredicateWithStatusFilter() {
            // Given
            filter.setOpportunityStatuses(List.of(OpportunityStatus.APPLIED, OpportunityStatus.ACCEPTED_BY_COMPANY));
            when(path.in(any(List.class))).thenReturn(predicate);

            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isNotNull();
            verify(root).get("opportunityStatus");
            verify(path).in(filter.getOpportunityStatuses());
        }

        @Test
        @DisplayName("should build predicate with rate status filter")
        void shouldBuildPredicateWithRateStatusFilter() {
            // Given
            filter.setFilterRateStatus(RateStatus.POSITIVE);

            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isNotNull();
            verify(root).get("rateStatus");
            verify(criteriaBuilder).equal(path, RateStatus.POSITIVE);
        }

        @Test
        @DisplayName("should build predicate with company ID filter")
        void shouldBuildPredicateWithCompanyIdFilter() {
            // Given
            filter.setCompanyId(100L);
            doReturn(partnershipJoin).when(root).join("partnershipOpportunity");
            doReturn(companyJoin).when(partnershipJoin).join("company");
            doReturn(path).when(companyJoin).get("id");

            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isNotNull();
            verify(root).join("partnershipOpportunity");
            verify(partnershipJoin).join("company");
            verify(criteriaBuilder).equal(path, 100L);
        }

        @Test
        @DisplayName("should build predicate with influencer ID filter")
        void shouldBuildPredicateWithInfluencerIdFilter() {
            // Given
            filter.setInfluencerId(200L);
            doReturn(influencerJoin).when(root).join("influencer");
            doReturn(path).when(influencerJoin).get("id");

            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isNotNull();
            verify(root).join("influencer");
            verify(criteriaBuilder).equal(path, 200L);
        }

        @Test
        @DisplayName("should build predicate with partnership opportunity ID filter")
        void shouldBuildPredicateWithPartnershipOpportunityIdFilter() {
            // Given
            filter.setPartnershipOpportunityId(300L);
            doReturn(partnershipJoin).when(root).join("partnershipOpportunity");
            doReturn(path).when(partnershipJoin).get("id");

            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isNotNull();
            verify(root).join("partnershipOpportunity");
            verify(criteriaBuilder).equal(path, 300L);
        }

        @Test
        @DisplayName("should build predicate with followers filter including Instagram platform")
        void shouldBuildPredicateWithFollowersFilter() {
            // Given
            filter.setMinFollowers(1000);
            filter.setMaxFollowers(50000);

            doReturn(influencerJoin).when(root).join("influencer", JoinType.INNER);
            doReturn(socialJoin).when(influencerJoin).join("socialConnections", JoinType.INNER);
            doReturn(platformJoin).when(socialJoin).join("platform", JoinType.INNER);
            doReturn(path).when(platformJoin).get("name");
            doReturn(path).when(socialJoin).get("followersCount");
            lenient().when(criteriaBuilder.greaterThanOrEqualTo(any(), eq(1000))).thenReturn(predicate);
            lenient().when(criteriaBuilder.lessThanOrEqualTo(any(), eq(50000))).thenReturn(predicate);

            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isNotNull();
            verify(root).join("influencer", JoinType.INNER);
            verify(influencerJoin).join("socialConnections", JoinType.INNER);
            verify(socialJoin).join("platform", JoinType.INNER);
            verify(criteriaBuilder).equal(path, "Instagram");
        }

        @Test
        @DisplayName("should build predicate with minimum positive rates filter using subquery")
        void shouldBuildPredicateWithMinPositiveRatesFilter() {
            // Given
            filter.setMinPositiveRates(5L);

            doReturn(influencerJoin).when(root).join("influencer", JoinType.INNER);
            doReturn(path).when(influencerJoin).get("id");
            when(query.subquery(Long.class)).thenReturn(subquery);
            when(subquery.from(AppliedOpportunity.class)).thenReturn(subRoot);
            doReturn(subInfluencerJoin).when(subRoot).join("influencer");
            doReturn(path).when(subInfluencerJoin).get("id");
            doReturn(path).when(subRoot).get("rateStatus");
            when(criteriaBuilder.count(subRoot)).thenReturn(null);
            when(subquery.select(any())).thenReturn(subquery);
            lenient().when(subquery.where(any(Predicate.class))).thenReturn(subquery);
            lenient().when(subquery.where((Predicate) null)).thenReturn(subquery);
            lenient().when(criteriaBuilder.greaterThanOrEqualTo(any(Subquery.class), eq(5L))).thenReturn(predicate);

            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isNotNull();
            verify(query).subquery(Long.class);
            verify(subquery).from(AppliedOpportunity.class);
        }
    }

    @Nested
    @DisplayName("Combined Filters")
    class CombinedFiltersTests {

        @Test
        @DisplayName("should build predicate with all filters populated")
        void shouldBuildPredicateWithAllFiltersPopulated() {
            // Given
            filter.setOpportunityStatuses(List.of(OpportunityStatus.APPLIED));
            filter.setFilterRateStatus(RateStatus.POSITIVE);
            filter.setCompanyId(100L);
            filter.setInfluencerId(200L);

            when(path.in(any(List.class))).thenReturn(predicate);
            doReturn(partnershipJoin).when(root).join(eq("partnershipOpportunity"));
            doReturn(companyJoin).when(partnershipJoin).join("company");
            doReturn(path).when(companyJoin).get("id");
            doReturn(influencerJoin).when(root).join(eq("influencer"));
            doReturn(path).when(influencerJoin).get("id");

            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isNotNull();
            verify(root).get("opportunityStatus");
            verify(root).get("rateStatus");
            verify(root).join("partnershipOpportunity");
            verify(root).join("influencer");
        }

        @Test
        @DisplayName("should not add predicate for empty opportunity statuses list")
        void shouldNotAddPredicateForEmptyStatusList() {
            // Given
            filter.setOpportunityStatuses(List.of());

            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isNotNull();
            verify(root, never()).get("opportunityStatus");
        }

        @Test
        @DisplayName("should not add predicate for null opportunity statuses")
        void shouldNotAddPredicateForNullStatusList() {
            // Given
            filter.setOpportunityStatuses(null);

            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isNotNull();
            verify(root, never()).get("opportunityStatus");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle filter with only min followers set")
        void shouldHandleFilterWithOnlyMinFollowers() {
            // Given
            filter.setMinFollowers(5000);

            doReturn(influencerJoin).when(root).join("influencer", JoinType.INNER);
            doReturn(socialJoin).when(influencerJoin).join("socialConnections", JoinType.INNER);
            doReturn(platformJoin).when(socialJoin).join("platform", JoinType.INNER);
            doReturn(path).when(platformJoin).get("name");
            doReturn(path).when(socialJoin).get("followersCount");
            lenient().when(criteriaBuilder.greaterThanOrEqualTo(any(), eq(5000))).thenReturn(predicate);

            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isNotNull();
            verify(criteriaBuilder).greaterThanOrEqualTo(any(), eq(5000));
            verify(criteriaBuilder, never()).lessThanOrEqualTo(any(), any(Integer.class));
        }

        @Test
        @DisplayName("should handle filter with only max followers set")
        void shouldHandleFilterWithOnlyMaxFollowers() {
            // Given
            filter.setMaxFollowers(100000);

            doReturn(influencerJoin).when(root).join("influencer", JoinType.INNER);
            doReturn(socialJoin).when(influencerJoin).join("socialConnections", JoinType.INNER);
            doReturn(platformJoin).when(socialJoin).join("platform", JoinType.INNER);
            doReturn(path).when(platformJoin).get("name");
            doReturn(path).when(socialJoin).get("followersCount");
            lenient().when(criteriaBuilder.lessThanOrEqualTo(any(), eq(100000))).thenReturn(predicate);

            AppliedOpportunitySpecification spec = new AppliedOpportunitySpecification(filter);

            // When
            Predicate result = spec.toPredicate(root, query, criteriaBuilder);

            // Then
            assertThat(result).isNotNull();
            verify(criteriaBuilder).lessThanOrEqualTo(any(), eq(100000));
            verify(criteriaBuilder, never()).greaterThanOrEqualTo(any(), any(Integer.class));
        }
    }
}
