package com.sm.instagram.platform.integration.service.activecooperation;

import com.sm.instagram.platform.activecooperations.CoopDto;
import com.sm.instagram.platform.activecooperations.Views;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.MappingJacksonValue;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for ActiveCooperationService view filtering.
 * Tests getCollaborationsInProgress() which returns different views based on user role.
 */
@DisplayName("ActiveCooperationService - View Filtering")
class ActiveCooperationService_ViewFiltering_IntegrationTest extends ActiveCooperationServiceIntegrationTestBase {

    private static final Pageable DEFAULT_PAGEABLE = PageRequest.of(0, 10);

    @Nested
    @DisplayName("getCollaborationsInProgress()")
    class GetCollaborationsInProgress {

        @Test
        @DisplayName("Returns in-progress cooperations")
        void returnsInProgressCooperations() {
            // Given
            createInProgressCooperation(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);
            authenticateAs(testCompany);

            // When
            MappingJacksonValue result = activeCooperationService.getCollaborationsInProgress(DEFAULT_PAGEABLE, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getValue()).isNotNull();
        }

        @Test
        @DisplayName("Returns cooperations with specific status filter")
        void returnsCooperationsWithStatusFilter() {
            // Given
            createInProgressCooperation(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);
            createInProgressCooperation(secondInfluencer, testPartnershipOpportunity, OpportunityStatus.CONTENT_POSTED);
            createTestSocialConnection(secondInfluencer, 5000);
            authenticateAs(testCompany);

            // When - Filter for ACCEPTED_BY_COMPANY only
            List<OpportunityStatus> statuses = List.of(OpportunityStatus.ACCEPTED_BY_COMPANY);
            MappingJacksonValue result = activeCooperationService.getCollaborationsInProgress(DEFAULT_PAGEABLE, statuses);

            // Then
            @SuppressWarnings("unchecked")
            List<CoopDto> content = (List<CoopDto>) result.getValue();
            assertThat(content).allMatch(dto -> dto.getAppliedOpportunityStatus() == OpportunityStatus.ACCEPTED_BY_COMPANY);
        }

        @Test
        @DisplayName("Invalid status throws ValidationException")
        void invalidStatusThrows() {
            // Given
            authenticateAs(testCompany);
            // DONE is not a valid in-progress status
            List<OpportunityStatus> invalidStatuses = List.of(OpportunityStatus.DONE);

            // When/Then
            assertThatThrownBy(() -> activeCooperationService.getCollaborationsInProgress(DEFAULT_PAGEABLE, invalidStatuses))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("APPLIED status is not valid for in-progress")
        void appliedStatusNotValidForInProgress() {
            // Given
            authenticateAs(testCompany);
            List<OpportunityStatus> invalidStatuses = List.of(OpportunityStatus.APPLIED);

            // When/Then
            assertThatThrownBy(() -> activeCooperationService.getCollaborationsInProgress(DEFAULT_PAGEABLE, invalidStatuses))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("Role-Based View Selection")
    class RoleBasedViewSelection {

        @Test
        @DisplayName("Admin gets InProgress_AdminView")
        void adminGetsAdminView() {
            // Given
            createInProgressCooperation(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);
            authenticateAs(testAdmin);

            // When
            MappingJacksonValue result = activeCooperationService.getCollaborationsInProgress(DEFAULT_PAGEABLE, null);

            // Then
            assertThat(result.getSerializationView()).isEqualTo(Views.InProgress_AdminView.class);
        }

        @Test
        @DisplayName("Company gets InProgress_CompanyView")
        void companyGetsCompanyView() {
            // Given
            createInProgressCooperation(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);
            authenticateAs(testCompany);

            // When
            MappingJacksonValue result = activeCooperationService.getCollaborationsInProgress(DEFAULT_PAGEABLE, null);

            // Then
            assertThat(result.getSerializationView()).isEqualTo(Views.InProgress_CompanyView.class);
        }

        @Test
        @DisplayName("Influencer gets InProgress_InfluencerView")
        void influencerGetsInfluencerView() {
            // Given
            createInProgressCooperation(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);
            authenticateAs(testInfluencer);

            // When
            MappingJacksonValue result = activeCooperationService.getCollaborationsInProgress(DEFAULT_PAGEABLE, null);

            // Then
            assertThat(result.getSerializationView()).isEqualTo(Views.InProgress_InfluencerView.class);
        }
    }

    @Nested
    @DisplayName("Influencer Data Scoping")
    class InfluencerDataScoping {

        @Test
        @DisplayName("Influencer only sees own collaborations")
        void influencerOnlySeesOwnCollaborations() {
            // Given - testInfluencer's cooperation
            createInProgressCooperation(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);

            // secondInfluencer's cooperation
            createTestSocialConnection(secondInfluencer, 5000);
            createInProgressCooperation(secondInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);

            authenticateAs(testInfluencer);

            // When
            MappingJacksonValue result = activeCooperationService.getCollaborationsInProgress(DEFAULT_PAGEABLE, null);

            // Then
            @SuppressWarnings("unchecked")
            List<CoopDto> content = (List<CoopDto>) result.getValue();
            assertThat(content).allMatch(dto ->
                    dto.getInfluencerEmail().equals(testInfluencer.getEmail()));
        }
    }

    @Nested
    @DisplayName("Company Data Scoping")
    class CompanyDataScoping {

        @Test
        @DisplayName("Company only sees collaborations for own opportunities")
        void companyOnlySeesOwnOpportunityCollaborations() {
            // Given - testCompany's opportunity
            createInProgressCooperation(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);

            // secondCompany's opportunity
            PartnershipOpportunity otherOpportunity = createAndSavePartnershipOpportunity(secondCompany);
            createTestSocialConnection(secondInfluencer, 5000);
            createInProgressCooperation(secondInfluencer, otherOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);

            authenticateAs(testCompany);

            // When
            MappingJacksonValue result = activeCooperationService.getCollaborationsInProgress(DEFAULT_PAGEABLE, null);

            // Then
            @SuppressWarnings("unchecked")
            List<CoopDto> content = (List<CoopDto>) result.getValue();
            assertThat(content).allMatch(dto ->
                    dto.getPartnershipOpportunityTitle().equals(testPartnershipOpportunity.getTitle()));
        }
    }

    @Nested
    @DisplayName("Multiple Status Filters")
    class MultipleStatusFilters {

        @Test
        @DisplayName("Can filter by multiple valid statuses")
        void canFilterByMultipleStatuses() {
            // Given
            createInProgressCooperation(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);

            PartnershipOpportunity po2 = createAndSavePartnershipOpportunity(testCompany);
            createInProgressCooperation(testInfluencer, po2, OpportunityStatus.CONTENT_APPROVED);

            authenticateAs(testCompany);

            // When
            List<OpportunityStatus> statuses = List.of(
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    OpportunityStatus.CONTENT_APPROVED
            );
            MappingJacksonValue result = activeCooperationService.getCollaborationsInProgress(DEFAULT_PAGEABLE, statuses);

            // Then
            @SuppressWarnings("unchecked")
            List<CoopDto> content = (List<CoopDto>) result.getValue();
            assertThat(content).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Empty result when no matching statuses")
        void emptyResultWhenNoMatchingStatuses() {
            // Given - cooperation at different status
            createInProgressCooperation(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);
            authenticateAs(testCompany);

            // When - filter for status that doesn't exist
            List<OpportunityStatus> statuses = List.of(OpportunityStatus.CONTENT_POSTED);
            MappingJacksonValue result = activeCooperationService.getCollaborationsInProgress(DEFAULT_PAGEABLE, statuses);

            // Then
            @SuppressWarnings("unchecked")
            List<CoopDto> content = (List<CoopDto>) result.getValue();
            assertThat(content).isEmpty();
        }
    }
}
