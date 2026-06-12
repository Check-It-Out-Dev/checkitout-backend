package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.activecooperations.CoopDto;
import com.sm.instagram.platform.activecooperations.CoopFilter;
import com.sm.instagram.platform.activecooperations.Views;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ActiveCooperations DTOs and filters.
 * Tests DTO structure, filter behavior, and JSON view hierarchy.
 */
@DisplayName("ActiveCooperations Unit Tests")
class ActiveCooperationsServiceUnitTest {

    // ==================== CoopDto Tests ====================

    @Nested
    @DisplayName("CoopDto")
    class CoopDtoTests {

        @Test
        @DisplayName("should create DTO with all fields using no-args constructor")
        void shouldCreateDtoWithNoArgsConstructor() {
            // Given
            CoopDto dto = new CoopDto();

            // Then
            assertThat(dto.getId()).isNull();
            assertThat(dto.getInfluencerFirstName()).isNull();
            assertThat(dto.getInfluencerLastName()).isNull();
            assertThat(dto.getCompanyName()).isNull();
        }

        @Test
        @DisplayName("should set and get all fields")
        void shouldSetAndGetAllFields() {
            // Given
            CoopDto dto = new CoopDto();
            dto.setId(1L);
            dto.setInfluencerFirstName("Jan");
            dto.setInfluencerLastName("Kowalski");
            dto.setCompanyName("Test Company");
            dto.setInfluencerEmail("jan@example.com");
            dto.setInfluencerInstagramId("jankowalski");
            dto.setFollowersAmount(10000);
            dto.setInfluencerAvatarUrl("https://instagram.com/avatar.jpg");
            dto.setCompanyAvatarUrl("https://company.com/logo.png");
            dto.setAppliedOpportunityId(100L);
            dto.setAppliedOpportunityStatus(OpportunityStatus.APPLIED);
            dto.setPartnershipOpportunityTitle("Summer Campaign");
            dto.setAppliedOpportunityNote("Looking forward to this collaboration");
            dto.setInfluencerRateStatus(RateStatus.POSITIVE);
            dto.setCompanyRateStatus(RateStatus.POSITIVE);
            dto.setPositiveRatesAmount(10L);
            dto.setNegativeRatesAmount(2L);

            // Then
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getInfluencerFirstName()).isEqualTo("Jan");
            assertThat(dto.getInfluencerLastName()).isEqualTo("Kowalski");
            assertThat(dto.getCompanyName()).isEqualTo("Test Company");
            assertThat(dto.getInfluencerEmail()).isEqualTo("jan@example.com");
            assertThat(dto.getInfluencerInstagramId()).isEqualTo("jankowalski");
            assertThat(dto.getFollowersAmount()).isEqualTo(10000);
            assertThat(dto.getInfluencerAvatarUrl()).isEqualTo("https://instagram.com/avatar.jpg");
            assertThat(dto.getCompanyAvatarUrl()).isEqualTo("https://company.com/logo.png");
            assertThat(dto.getAppliedOpportunityId()).isEqualTo(100L);
            assertThat(dto.getAppliedOpportunityStatus()).isEqualTo(OpportunityStatus.APPLIED);
            assertThat(dto.getPartnershipOpportunityTitle()).isEqualTo("Summer Campaign");
            assertThat(dto.getAppliedOpportunityNote()).isEqualTo("Looking forward to this collaboration");
            assertThat(dto.getInfluencerRateStatus()).isEqualTo(RateStatus.POSITIVE);
            assertThat(dto.getCompanyRateStatus()).isEqualTo(RateStatus.POSITIVE);
            assertThat(dto.getPositiveRatesAmount()).isEqualTo(10L);
            assertThat(dto.getNegativeRatesAmount()).isEqualTo(2L);
        }

        @ParameterizedTest
        @EnumSource(OpportunityStatus.class)
        @DisplayName("should accept all opportunity statuses")
        void shouldAcceptAllOpportunityStatuses(OpportunityStatus status) {
            // Given
            CoopDto dto = new CoopDto();
            dto.setAppliedOpportunityStatus(status);

            // Then
            assertThat(dto.getAppliedOpportunityStatus()).isEqualTo(status);
        }

        @ParameterizedTest
        @EnumSource(RateStatus.class)
        @DisplayName("should accept all rate statuses for influencer")
        void shouldAcceptAllRateStatusesForInfluencer(RateStatus status) {
            // Given
            CoopDto dto = new CoopDto();
            dto.setInfluencerRateStatus(status);

            // Then
            assertThat(dto.getInfluencerRateStatus()).isEqualTo(status);
        }

        @ParameterizedTest
        @EnumSource(RateStatus.class)
        @DisplayName("should accept all rate statuses for company")
        void shouldAcceptAllRateStatusesForCompany(RateStatus status) {
            // Given
            CoopDto dto = new CoopDto();
            dto.setCompanyRateStatus(status);

            // Then
            assertThat(dto.getCompanyRateStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("should handle Polish names")
        void shouldHandlePolishNames() {
            // Given
            CoopDto dto = new CoopDto();
            dto.setInfluencerFirstName("Żółć");
            dto.setInfluencerLastName("Świętokrzyski");
            dto.setCompanyName("Firma Łódzka Sp. z o.o.");

            // Then
            assertThat(dto.getInfluencerFirstName()).isEqualTo("Żółć");
            assertThat(dto.getInfluencerLastName()).isEqualTo("Świętokrzyski");
            assertThat(dto.getCompanyName()).isEqualTo("Firma Łódzka Sp. z o.o.");
        }
    }

    // ==================== CoopFilter Tests ====================

    @Nested
    @DisplayName("CoopFilter")
    class CoopFilterTests {

        @Test
        @DisplayName("should create filter with no-args constructor")
        void shouldCreateFilterWithNoArgsConstructor() {
            // Given
            CoopFilter filter = new CoopFilter();

            // Then
            assertThat(filter.getOpportunityStatuses()).isNull();
            assertThat(filter.getCompanyId()).isNull();
            assertThat(filter.getInfluencerId()).isNull();
            assertThat(filter.getFilterRateStatus()).isNull();
            assertThat(filter.getMinFollowers()).isNull();
            assertThat(filter.getMaxFollowers()).isNull();
            assertThat(filter.getMinPositiveRates()).isNull();
            assertThat(filter.getPartnershipOpportunityId()).isNull();
        }

        @Test
        @DisplayName("should set and get all filter fields")
        void shouldSetAndGetAllFilterFields() {
            // Given
            CoopFilter filter = new CoopFilter();
            List<OpportunityStatus> statuses = Arrays.asList(
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY
            );

            filter.setOpportunityStatuses(statuses);
            filter.setCompanyId(1L);
            filter.setInfluencerId(2L);
            filter.setFilterRateStatus(RateStatus.POSITIVE);
            filter.setMinFollowers(1000);
            filter.setMaxFollowers(100000);
            filter.setMinPositiveRates(5L);
            filter.setPartnershipOpportunityId(3L);

            // Then
            assertThat(filter.getOpportunityStatuses()).containsExactlyInAnyOrder(
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY
            );
            assertThat(filter.getCompanyId()).isEqualTo(1L);
            assertThat(filter.getInfluencerId()).isEqualTo(2L);
            assertThat(filter.getFilterRateStatus()).isEqualTo(RateStatus.POSITIVE);
            assertThat(filter.getMinFollowers()).isEqualTo(1000);
            assertThat(filter.getMaxFollowers()).isEqualTo(100000);
            assertThat(filter.getMinPositiveRates()).isEqualTo(5L);
            assertThat(filter.getPartnershipOpportunityId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("should accept empty opportunity statuses list")
        void shouldAcceptEmptyOpportunityStatusesList() {
            // Given
            CoopFilter filter = new CoopFilter();
            filter.setOpportunityStatuses(List.of());

            // Then
            assertThat(filter.getOpportunityStatuses()).isEmpty();
        }

        @Test
        @DisplayName("should accept all opportunity statuses in filter")
        void shouldAcceptAllOpportunityStatusesInFilter() {
            // Given
            CoopFilter filter = new CoopFilter();
            List<OpportunityStatus> allStatuses = Arrays.asList(OpportunityStatus.values());
            filter.setOpportunityStatuses(allStatuses);

            // Then
            assertThat(filter.getOpportunityStatuses()).hasSize(OpportunityStatus.values().length);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 100, 1000, 10000, 100000, 1000000})
        @DisplayName("should accept various follower counts for min filter")
        void shouldAcceptVariousFollowerCountsForMinFilter(int followers) {
            // Given
            CoopFilter filter = new CoopFilter();
            filter.setMinFollowers(followers);

            // Then
            assertThat(filter.getMinFollowers()).isEqualTo(followers);
        }

        @ParameterizedTest
        @ValueSource(ints = {1000, 5000, 10000, 50000, 100000, 1000000})
        @DisplayName("should accept various follower counts for max filter")
        void shouldAcceptVariousFollowerCountsForMaxFilter(int followers) {
            // Given
            CoopFilter filter = new CoopFilter();
            filter.setMaxFollowers(followers);

            // Then
            assertThat(filter.getMaxFollowers()).isEqualTo(followers);
        }

        @Test
        @DisplayName("should allow min and max followers to define a range")
        void shouldAllowMinAndMaxFollowersToDefineRange() {
            // Given
            CoopFilter filter = new CoopFilter();
            filter.setMinFollowers(1000);
            filter.setMaxFollowers(10000);

            // Then
            assertThat(filter.getMinFollowers()).isLessThan(filter.getMaxFollowers());
        }
    }

    // ==================== Views Tests ====================

    @Nested
    @DisplayName("Views Hierarchy")
    class ViewsTests {

        @Test
        @DisplayName("Views.Basic should be a marker interface")
        void viewsBasicShouldBeMarkerInterface() {
            // Views.Basic is the base interface
            assertThat(Views.Basic.class.isInterface()).isTrue();
        }

        @Test
        @DisplayName("Views.Ratings should extend Basic")
        void viewsRatingsShouldExtendBasic() {
            // Views.Ratings extends Basic
            assertThat(Views.Basic.class.isAssignableFrom(Views.Ratings.class)).isTrue();
        }

        @Test
        @DisplayName("Views.Registration should extend Ratings")
        void viewsRegistrationShouldExtendRatings() {
            // Views.Registration extends Ratings
            assertThat(Views.Ratings.class.isAssignableFrom(Views.Registration.class)).isTrue();
            // And therefore also extends Basic
            assertThat(Views.Basic.class.isAssignableFrom(Views.Registration.class)).isTrue();
        }

        @Test
        @DisplayName("Views.InProgress_InfluencerView should extend Basic")
        void viewsInProgressInfluencerViewShouldExtendBasic() {
            assertThat(Views.Basic.class.isAssignableFrom(Views.InProgress_InfluencerView.class)).isTrue();
        }

        @Test
        @DisplayName("Views.InProgress_CompanyView should extend Basic")
        void viewsInProgressCompanyViewShouldExtendBasic() {
            assertThat(Views.Basic.class.isAssignableFrom(Views.InProgress_CompanyView.class)).isTrue();
        }

        @Test
        @DisplayName("Views.InProgress_AdminView should extend both influencer and company views")
        void viewsInProgressAdminViewShouldExtendBothViews() {
            // InProgress_AdminView extends both InProgress_InfluencerView and InProgress_CompanyView
            assertThat(Views.InProgress_InfluencerView.class.isAssignableFrom(Views.InProgress_AdminView.class)).isTrue();
            assertThat(Views.InProgress_CompanyView.class.isAssignableFrom(Views.InProgress_AdminView.class)).isTrue();
            // And therefore also extends Basic
            assertThat(Views.Basic.class.isAssignableFrom(Views.InProgress_AdminView.class)).isTrue();
        }

        @Test
        @DisplayName("all views should be interfaces")
        void allViewsShouldBeInterfaces() {
            // Check all view classes are interfaces
            assertThat(Views.Basic.class.isInterface()).isTrue();
            assertThat(Views.Ratings.class.isInterface()).isTrue();
            assertThat(Views.Registration.class.isInterface()).isTrue();
            assertThat(Views.InProgress_InfluencerView.class.isInterface()).isTrue();
            assertThat(Views.InProgress_CompanyView.class.isInterface()).isTrue();
            assertThat(Views.InProgress_AdminView.class.isInterface()).isTrue();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle zero followers")
        void shouldHandleZeroFollowers() {
            // Given
            CoopDto dto = new CoopDto();
            dto.setFollowersAmount(0);

            // Then
            assertThat(dto.getFollowersAmount()).isZero();
        }

        @Test
        @DisplayName("should handle very large followers count")
        void shouldHandleVeryLargeFollowersCount() {
            // Given
            CoopDto dto = new CoopDto();
            dto.setFollowersAmount(Integer.MAX_VALUE);

            // Then
            assertThat(dto.getFollowersAmount()).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("should handle zero positive rates")
        void shouldHandleZeroPositiveRates() {
            // Given
            CoopDto dto = new CoopDto();
            dto.setPositiveRatesAmount(0L);
            dto.setNegativeRatesAmount(0L);

            // Then
            assertThat(dto.getPositiveRatesAmount()).isZero();
            assertThat(dto.getNegativeRatesAmount()).isZero();
        }

        @Test
        @DisplayName("should handle null opportunity statuses in filter")
        void shouldHandleNullOpportunityStatusesInFilter() {
            // Given
            CoopFilter filter = new CoopFilter();
            filter.setOpportunityStatuses(null);

            // Then
            assertThat(filter.getOpportunityStatuses()).isNull();
        }

        @Test
        @DisplayName("should handle empty string values")
        void shouldHandleEmptyStringValues() {
            // Given
            CoopDto dto = new CoopDto();
            dto.setInfluencerFirstName("");
            dto.setInfluencerLastName("");
            dto.setCompanyName("");

            // Then
            assertThat(dto.getInfluencerFirstName()).isEmpty();
            assertThat(dto.getInfluencerLastName()).isEmpty();
            assertThat(dto.getCompanyName()).isEmpty();
        }

        @Test
        @DisplayName("should handle long company name")
        void shouldHandleLongCompanyName() {
            // Given
            String longName = "A".repeat(500);
            CoopDto dto = new CoopDto();
            dto.setCompanyName(longName);

            // Then
            assertThat(dto.getCompanyName()).hasSize(500);
        }

        @Test
        @DisplayName("should handle null rate statuses")
        void shouldHandleNullRateStatuses() {
            // Given
            CoopDto dto = new CoopDto();
            dto.setInfluencerRateStatus(null);
            dto.setCompanyRateStatus(null);

            // Then
            assertThat(dto.getInfluencerRateStatus()).isNull();
            assertThat(dto.getCompanyRateStatus()).isNull();
        }
    }

    // ==================== Business Logic Tests ====================

    @Nested
    @DisplayName("Business Logic")
    class BusinessLogicTests {

        @Test
        @DisplayName("filter should support filtering by company ID")
        void filterShouldSupportFilteringByCompanyId() {
            // Given
            CoopFilter filter = new CoopFilter();
            filter.setCompanyId(123L);

            // Then
            assertThat(filter.getCompanyId()).isEqualTo(123L);
            assertThat(filter.getInfluencerId()).isNull();
        }

        @Test
        @DisplayName("filter should support filtering by influencer ID")
        void filterShouldSupportFilteringByInfluencerId() {
            // Given
            CoopFilter filter = new CoopFilter();
            filter.setInfluencerId(456L);

            // Then
            assertThat(filter.getInfluencerId()).isEqualTo(456L);
            assertThat(filter.getCompanyId()).isNull();
        }

        @Test
        @DisplayName("filter should support filtering by partnership opportunity")
        void filterShouldSupportFilteringByPartnershipOpportunity() {
            // Given
            CoopFilter filter = new CoopFilter();
            filter.setPartnershipOpportunityId(789L);

            // Then
            assertThat(filter.getPartnershipOpportunityId()).isEqualTo(789L);
        }

        @Test
        @DisplayName("DTO should track both influencer and company rate statuses separately")
        void dtoShouldTrackBothRateStatusesSeparately() {
            // Given
            CoopDto dto = new CoopDto();
            dto.setInfluencerRateStatus(RateStatus.POSITIVE);
            dto.setCompanyRateStatus(RateStatus.NEGATIVE);

            // Then
            assertThat(dto.getInfluencerRateStatus()).isNotEqualTo(dto.getCompanyRateStatus());
        }
    }
}
