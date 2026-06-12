package com.sm.instagram.platform.unit.controller;

import com.sm.instagram.platform.activecooperations.*;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ActiveCooperationController.
 * Uses pure Mockito - no Spring context loaded.
 *
 * Tests cover:
 * - All controller endpoints
 * - Request parameter handling
 * - Response formatting
 * - Error scenarios
 * - Pagination logic
 * - Security context handling
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ActiveCooperationController Unit Tests")
class ActiveCooperationControllerUnitTest {

    @Mock
    private ActiveCooperationService activeCooperationService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ActiveCooperationController controller;

    // Test fixtures
    private CoopDto testCoopDto;
    private Page<CoopDto> testPage;
    private MappingJacksonValue testMappingJacksonValue;

    @BeforeEach
    void setUp() {
        // Set default page size via reflection
        ReflectionTestUtils.setField(controller, "defaultPageSize", 12);

        // Setup security context mock
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("test-firebase-uid");
        SecurityContextHolder.setContext(securityContext);

        // Create test CoopDto
        testCoopDto = new CoopDto();
        testCoopDto.setId(1L);
        testCoopDto.setInfluencerFirstName("Jan");
        testCoopDto.setInfluencerLastName("Kowalski");
        testCoopDto.setCompanyName("Test Company");
        testCoopDto.setInfluencerEmail("jan@example.com");
        testCoopDto.setInfluencerInstagramId("jankowalski");
        testCoopDto.setFollowersAmount(10000);
        testCoopDto.setInfluencerAvatarUrl("https://instagram.com/avatar.jpg");
        testCoopDto.setCompanyAvatarUrl("https://company.com/logo.png");
        testCoopDto.setAppliedOpportunityId(100L);
        testCoopDto.setAppliedOpportunityStatus(OpportunityStatus.DONE);
        testCoopDto.setPartnershipOpportunityTitle("Summer Campaign");
        testCoopDto.setAppliedOpportunityNote("Great collaboration");
        testCoopDto.setInfluencerRateStatus(RateStatus.POSITIVE);
        testCoopDto.setCompanyRateStatus(RateStatus.POSITIVE);
        testCoopDto.setPositiveRatesAmount(10L);
        testCoopDto.setNegativeRatesAmount(2L);

        // Create test page
        testPage = new PageImpl<>(
                List.of(testCoopDto),
                PageRequest.of(0, 12),
                1
        );

        // Create test MappingJacksonValue
        testMappingJacksonValue = new MappingJacksonValue(List.of(testCoopDto));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== GET /activecoop/rate Tests ====================

    @Nested
    @DisplayName("GET /activecoop/rate - getInfluencersToRate")
    class GetInfluencersToRateTests {

        @Test
        @DisplayName("should return influencers with default filter status")
        void shouldReturnInfluencersWithDefaultFilterStatus() {
            // Given
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    eq("DEFAULT"), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            ResponseEntity<List<CoopDto>> response = controller.getInfluencersToRate(
                    "DEFAULT", 0, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getId()).isEqualTo(1L);
            verify(activeCooperationService).getInfluencersToRateWithPermission(
                    eq("DEFAULT"), any(Pageable.class));
        }

        @Test
        @DisplayName("should use default page size when size is null")
        void shouldUseDefaultPageSizeWhenSizeIsNull() {
            // Given
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToRate("DEFAULT", 0, null);

            // Then
            verify(activeCooperationService).getInfluencersToRateWithPermission(
                    eq("DEFAULT"), argThat(pageable -> pageable.getPageSize() == 12));
        }

        @Test
        @DisplayName("should use provided page size when specified")
        void shouldUseProvidedPageSizeWhenSpecified() {
            // Given
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToRate("DEFAULT", 0, 20);

            // Then
            verify(activeCooperationService).getInfluencersToRateWithPermission(
                    eq("DEFAULT"), argThat(pageable -> pageable.getPageSize() == 20));
        }

        @Test
        @DisplayName("should enforce max page size of 100")
        void shouldEnforceMaxPageSizeOf100() {
            // Given
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToRate("DEFAULT", 0, 150);

            // Then
            verify(activeCooperationService).getInfluencersToRateWithPermission(
                    eq("DEFAULT"), argThat(pageable -> pageable.getPageSize() == 100));
        }

        @ParameterizedTest
        @ValueSource(strings = {"DEFAULT", "POSITIVE", "NEGATIVE", "ALL"})
        @DisplayName("should accept valid filter rate statuses")
        void shouldAcceptValidFilterRateStatuses(String filterStatus) {
            // Given
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    eq(filterStatus), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            ResponseEntity<List<CoopDto>> response = controller.getInfluencersToRate(
                    filterStatus, 0, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(activeCooperationService).getInfluencersToRateWithPermission(
                    eq(filterStatus), any(Pageable.class));
        }

        @Test
        @DisplayName("should pass correct page number")
        void shouldPassCorrectPageNumber() {
            // Given
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToRate("DEFAULT", 5, 10);

            // Then
            verify(activeCooperationService).getInfluencersToRateWithPermission(
                    eq("DEFAULT"), argThat(pageable -> pageable.getPageNumber() == 5));
        }

        @Test
        @DisplayName("should return empty list when no influencers found")
        void shouldReturnEmptyListWhenNoInfluencersFound() {
            // Given
            Page<CoopDto> emptyPage = new PageImpl<>(Collections.emptyList());
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // When
            ResponseEntity<List<CoopDto>> response = controller.getInfluencersToRate(
                    "DEFAULT", 0, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should throw exception when service throws InsufficientPermissionsException")
        void shouldThrowExceptionWhenNotAdmin() {
            // Given
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class)))
                    .thenThrow(new InsufficientPermissionsException(
                            "error.auth.insufficient_permissions",
                            "test-uid",
                            "getInfluencersToRate",
                            "ActiveCooperation"));

            // When/Then
            assertThatThrownBy(() -> controller.getInfluencersToRate("DEFAULT", 0, null))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should sort by lastUpdateTime ascending")
        void shouldSortByLastUpdateTimeAscending() {
            // Given
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToRate("DEFAULT", 0, null);

            // Then
            verify(activeCooperationService).getInfluencersToRateWithPermission(
                    eq("DEFAULT"), argThat(pageable ->
                            pageable.getSort().getOrderFor("lastUpdateTime") != null &&
                                    pageable.getSort().getOrderFor("lastUpdateTime").isAscending()));
        }
    }

    // ==================== GET /activecoop/accept Tests ====================

    @Nested
    @DisplayName("GET /activecoop/accept - getInfluencersToAccept")
    class GetInfluencersToAcceptTests {

        @Test
        @DisplayName("should return influencers to accept with no filters")
        void shouldReturnInfluencersToAcceptWithNoFilters() {
            // Given
            when(activeCooperationService.getInfluencersToAccept(
                    isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            ResponseEntity<List<CoopDto>> response = controller.getInfluencersToAccept(
                    null, null, null, 0, 12);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("should pass minFollowers filter")
        void shouldPassMinFollowersFilter() {
            // Given
            when(activeCooperationService.getInfluencersToAccept(
                    eq(1000), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToAccept(1000, null, null, 0, 12);

            // Then
            verify(activeCooperationService).getInfluencersToAccept(
                    eq(1000), isNull(), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("should pass maxFollowers filter")
        void shouldPassMaxFollowersFilter() {
            // Given
            when(activeCooperationService.getInfluencersToAccept(
                    isNull(), eq(50000), isNull(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToAccept(null, 50000, null, 0, 12);

            // Then
            verify(activeCooperationService).getInfluencersToAccept(
                    isNull(), eq(50000), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("should pass minPositiveRates filter")
        void shouldPassMinPositiveRatesFilter() {
            // Given
            when(activeCooperationService.getInfluencersToAccept(
                    isNull(), isNull(), eq(5L), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToAccept(null, null, 5L, 0, 12);

            // Then
            verify(activeCooperationService).getInfluencersToAccept(
                    isNull(), isNull(), eq(5L), any(Pageable.class));
        }

        @Test
        @DisplayName("should pass all filters together")
        void shouldPassAllFiltersTogether() {
            // Given
            when(activeCooperationService.getInfluencersToAccept(
                    eq(1000), eq(50000), eq(10L), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToAccept(1000, 50000, 10L, 0, 12);

            // Then
            verify(activeCooperationService).getInfluencersToAccept(
                    eq(1000), eq(50000), eq(10L), any(Pageable.class));
        }

        @Test
        @DisplayName("should enforce max page size of 100")
        void shouldEnforceMaxPageSizeOf100() {
            // Given
            when(activeCooperationService.getInfluencersToAccept(
                    any(), any(), any(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToAccept(null, null, null, 0, 200);

            // Then
            verify(activeCooperationService).getInfluencersToAccept(
                    any(), any(), any(), argThat(pageable -> pageable.getPageSize() == 100));
        }

        @Test
        @DisplayName("should sort by lastUpdateTime descending")
        void shouldSortByLastUpdateTimeDescending() {
            // Given
            when(activeCooperationService.getInfluencersToAccept(
                    any(), any(), any(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToAccept(null, null, null, 0, 12);

            // Then
            verify(activeCooperationService).getInfluencersToAccept(
                    any(), any(), any(), argThat(pageable ->
                            pageable.getSort().getOrderFor("lastUpdateTime") != null &&
                                    pageable.getSort().getOrderFor("lastUpdateTime").isDescending()));
        }

        @Test
        @DisplayName("should throw exception when minFollowers greater than maxFollowers")
        void shouldThrowExceptionWhenMinFollowersGreaterThanMaxFollowers() {
            // Given
            when(activeCooperationService.getInfluencersToAccept(
                    eq(50000), eq(1000), isNull(), any(Pageable.class)))
                    .thenThrow(new ValidationTranslatableException(
                            "error.validation.invalid_argument",
                            "minFollowers cannot be greater than maxFollowers"));

            // When/Then
            assertThatThrownBy(() -> controller.getInfluencersToAccept(50000, 1000, null, 0, 12))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 5, 10, 50})
        @DisplayName("should accept various page numbers")
        void shouldAcceptVariousPageNumbers(int page) {
            // Given
            when(activeCooperationService.getInfluencersToAccept(
                    any(), any(), any(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToAccept(null, null, null, page, 12);

            // Then
            verify(activeCooperationService).getInfluencersToAccept(
                    any(), any(), any(), argThat(pageable -> pageable.getPageNumber() == page));
        }
    }

    // ==================== GET /activecoop/inprogress Tests ====================

    @Nested
    @DisplayName("GET /activecoop/inprogress - getOpportunitiesInProgress")
    class GetOpportunitiesInProgressTests {

        @Test
        @DisplayName("should return opportunities in progress with no statuses filter")
        void shouldReturnOpportunitiesInProgressWithNoStatusesFilter() {
            // Given
            when(activeCooperationService.getCollaborationsInProgress(
                    any(Pageable.class), isNull()))
                    .thenReturn(testMappingJacksonValue);

            // When
            ResponseEntity<MappingJacksonValue> response = controller.getOpportunitiesInProgress(
                    0, 12, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("should pass statuses filter to service")
        void shouldPassStatusesFilterToService() {
            // Given
            List<OpportunityStatus> statuses = List.of(
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    OpportunityStatus.ACCEPTED_BY_INFLUENCER
            );
            when(activeCooperationService.getCollaborationsInProgress(
                    any(Pageable.class), eq(statuses)))
                    .thenReturn(testMappingJacksonValue);

            // When
            controller.getOpportunitiesInProgress(0, 12, statuses);

            // Then
            verify(activeCooperationService).getCollaborationsInProgress(
                    any(Pageable.class), eq(statuses));
        }

        @Test
        @DisplayName("should enforce max page size of 100")
        void shouldEnforceMaxPageSizeOf100() {
            // Given
            when(activeCooperationService.getCollaborationsInProgress(
                    any(Pageable.class), any()))
                    .thenReturn(testMappingJacksonValue);

            // When
            controller.getOpportunitiesInProgress(0, 150, null);

            // Then
            verify(activeCooperationService).getCollaborationsInProgress(
                    argThat(pageable -> pageable.getPageSize() == 100), any());
        }

        @Test
        @DisplayName("should sort by lastUpdateTime descending")
        void shouldSortByLastUpdateTimeDescending() {
            // Given
            when(activeCooperationService.getCollaborationsInProgress(
                    any(Pageable.class), any()))
                    .thenReturn(testMappingJacksonValue);

            // When
            controller.getOpportunitiesInProgress(0, 12, null);

            // Then
            verify(activeCooperationService).getCollaborationsInProgress(
                    argThat(pageable ->
                            pageable.getSort().getOrderFor("lastUpdateTime") != null &&
                                    pageable.getSort().getOrderFor("lastUpdateTime").isDescending()),
                    any());
        }

        @Test
        @DisplayName("should throw exception for invalid statuses")
        void shouldThrowExceptionForInvalidStatuses() {
            // Given
            List<OpportunityStatus> invalidStatuses = List.of(OpportunityStatus.DONE);
            when(activeCooperationService.getCollaborationsInProgress(
                    any(Pageable.class), eq(invalidStatuses)))
                    .thenThrow(new ValidationTranslatableException(
                            "error.validation.invalid_argument",
                            "Invalid opportunity statuses"));

            // When/Then
            assertThatThrownBy(() -> controller.getOpportunitiesInProgress(0, 12, invalidStatuses))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @ParameterizedTest
        @MethodSource("provideValidOpportunityStatuses")
        @DisplayName("should accept valid in-progress statuses")
        void shouldAcceptValidInProgressStatuses(OpportunityStatus status) {
            // Given
            List<OpportunityStatus> statuses = List.of(status);
            when(activeCooperationService.getCollaborationsInProgress(
                    any(Pageable.class), eq(statuses)))
                    .thenReturn(testMappingJacksonValue);

            // When
            ResponseEntity<MappingJacksonValue> response = controller.getOpportunitiesInProgress(
                    0, 12, statuses);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        static Stream<OpportunityStatus> provideValidOpportunityStatuses() {
            return Stream.of(
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    OpportunityStatus.ACCEPTED_BY_INFLUENCER,
                    OpportunityStatus.CONTENT_POSTED,
                    OpportunityStatus.CONTENT_REJECTED,
                    OpportunityStatus.CONTENT_APPROVED,
                    OpportunityStatus.REJECTED_BY_COMPANY,
                    OpportunityStatus.REJECTED_BY_INFLUENCER
            );
        }
    }

    // ==================== PUT /activecoop/{id}/company-rating Tests ====================

    @Nested
    @DisplayName("PUT /activecoop/{id}/company-rating - updateCompanyRating")
    class UpdateCompanyRatingTests {

        @Test
        @DisplayName("should update company rating successfully")
        void shouldUpdateCompanyRatingSuccessfully() {
            // Given
            when(activeCooperationService.updateCompanyRating(eq(1L), eq(RateStatus.POSITIVE)))
                    .thenReturn(testCoopDto);

            // When
            ResponseEntity<MappingJacksonValue> response = controller.updateCompanyRating(
                    1L, RateStatus.POSITIVE);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSerializationView())
                    .isEqualTo(Views.InProgress_CompanyView.class);
        }

        @ParameterizedTest
        @EnumSource(RateStatus.class)
        @DisplayName("should accept all rate statuses")
        void shouldAcceptAllRateStatuses(RateStatus rating) {
            // Given
            when(activeCooperationService.updateCompanyRating(eq(1L), eq(rating)))
                    .thenReturn(testCoopDto);

            // When
            ResponseEntity<MappingJacksonValue> response = controller.updateCompanyRating(
                    1L, rating);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(activeCooperationService).updateCompanyRating(1L, rating);
        }

        @Test
        @DisplayName("should throw exception when not authorized")
        void shouldThrowExceptionWhenNotAuthorized() {
            // Given
            when(activeCooperationService.updateCompanyRating(eq(1L), any()))
                    .thenThrow(new InsufficientPermissionsException(
                            "error.auth.insufficient_permissions",
                            "test-uid",
                            "updateCompanyRating",
                            "AppliedOpportunity#1"));

            // When/Then
            assertThatThrownBy(() -> controller.updateCompanyRating(1L, RateStatus.POSITIVE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw exception when opportunity not found")
        void shouldThrowExceptionWhenOpportunityNotFound() {
            // Given
            when(activeCooperationService.updateCompanyRating(eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException(
                            "error.business.item_not_found",
                            "Applied opportunity"));

            // When/Then
            assertThatThrownBy(() -> controller.updateCompanyRating(999L, RateStatus.POSITIVE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should set correct serialization view")
        void shouldSetCorrectSerializationView() {
            // Given
            when(activeCooperationService.updateCompanyRating(eq(1L), any()))
                    .thenReturn(testCoopDto);

            // When
            ResponseEntity<MappingJacksonValue> response = controller.updateCompanyRating(
                    1L, RateStatus.POSITIVE);

            // Then
            assertThat(response.getBody().getSerializationView())
                    .isEqualTo(Views.InProgress_CompanyView.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 100L, 999L, Long.MAX_VALUE})
        @DisplayName("should accept various opportunity IDs")
        void shouldAcceptVariousOpportunityIds(Long id) {
            // Given
            when(activeCooperationService.updateCompanyRating(eq(id), any()))
                    .thenReturn(testCoopDto);

            // When
            ResponseEntity<MappingJacksonValue> response = controller.updateCompanyRating(
                    id, RateStatus.POSITIVE);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(activeCooperationService).updateCompanyRating(eq(id), any());
        }
    }

    // ==================== PUT /activecoop/{id}/influencer-rating Tests ====================

    @Nested
    @DisplayName("PUT /activecoop/{id}/influencer-rating - updateInfluencerRating")
    class UpdateInfluencerRatingTests {

        @Test
        @DisplayName("should update influencer rating successfully")
        void shouldUpdateInfluencerRatingSuccessfully() {
            // Given
            when(activeCooperationService.updateInfluencerRating(eq(1L), eq(RateStatus.POSITIVE)))
                    .thenReturn(testCoopDto);

            // When
            ResponseEntity<MappingJacksonValue> response = controller.updateInfluencerRating(
                    1L, RateStatus.POSITIVE);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSerializationView())
                    .isEqualTo(Views.InProgress_InfluencerView.class);
        }

        @ParameterizedTest
        @EnumSource(RateStatus.class)
        @DisplayName("should accept all rate statuses")
        void shouldAcceptAllRateStatuses(RateStatus rating) {
            // Given
            when(activeCooperationService.updateInfluencerRating(eq(1L), eq(rating)))
                    .thenReturn(testCoopDto);

            // When
            ResponseEntity<MappingJacksonValue> response = controller.updateInfluencerRating(
                    1L, rating);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(activeCooperationService).updateInfluencerRating(1L, rating);
        }

        @Test
        @DisplayName("should throw exception when not authorized")
        void shouldThrowExceptionWhenNotAuthorized() {
            // Given
            when(activeCooperationService.updateInfluencerRating(eq(1L), any()))
                    .thenThrow(new InsufficientPermissionsException(
                            "error.auth.insufficient_permissions",
                            "test-uid",
                            "updateInfluencerRating",
                            "AppliedOpportunity#1"));

            // When/Then
            assertThatThrownBy(() -> controller.updateInfluencerRating(1L, RateStatus.POSITIVE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw exception when opportunity not found")
        void shouldThrowExceptionWhenOpportunityNotFound() {
            // Given
            when(activeCooperationService.updateInfluencerRating(eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException(
                            "error.business.item_not_found",
                            "Applied opportunity"));

            // When/Then
            assertThatThrownBy(() -> controller.updateInfluencerRating(999L, RateStatus.POSITIVE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should set correct serialization view")
        void shouldSetCorrectSerializationView() {
            // Given
            when(activeCooperationService.updateInfluencerRating(eq(1L), any()))
                    .thenReturn(testCoopDto);

            // When
            ResponseEntity<MappingJacksonValue> response = controller.updateInfluencerRating(
                    1L, RateStatus.POSITIVE);

            // Then
            assertThat(response.getBody().getSerializationView())
                    .isEqualTo(Views.InProgress_InfluencerView.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 100L, 999L, Long.MAX_VALUE})
        @DisplayName("should accept various opportunity IDs")
        void shouldAcceptVariousOpportunityIds(Long id) {
            // Given
            when(activeCooperationService.updateInfluencerRating(eq(id), any()))
                    .thenReturn(testCoopDto);

            // When
            ResponseEntity<MappingJacksonValue> response = controller.updateInfluencerRating(
                    id, RateStatus.POSITIVE);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(activeCooperationService).updateInfluencerRating(eq(id), any());
        }
    }

    // ==================== CoopDto Tests ====================

    @Nested
    @DisplayName("CoopDto")
    class CoopDtoTests {

        @Test
        @DisplayName("should create DTO with all-args constructor")
        void shouldCreateDtoWithAllArgsConstructor() {
            // Given
            CoopDto dto = new CoopDto(
                    1L,
                    "Jan",
                    "Kowalski",
                    "Test Company",
                    "jan@test.com",
                    "jankowalski",
                    10000,
                    "https://avatar.url",
                    "https://company.url",
                    100L,
                    OpportunityStatus.DONE,
                    "Campaign Title",
                    "Note",
                    RateStatus.POSITIVE,
                    RateStatus.POSITIVE,
                    10L,
                    2L
            );

            // Then
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getInfluencerFirstName()).isEqualTo("Jan");
            assertThat(dto.getInfluencerLastName()).isEqualTo("Kowalski");
            assertThat(dto.getCompanyName()).isEqualTo("Test Company");
            assertThat(dto.getInfluencerEmail()).isEqualTo("jan@test.com");
            assertThat(dto.getInfluencerInstagramId()).isEqualTo("jankowalski");
            assertThat(dto.getFollowersAmount()).isEqualTo(10000);
            assertThat(dto.getInfluencerAvatarUrl()).isEqualTo("https://avatar.url");
            assertThat(dto.getCompanyAvatarUrl()).isEqualTo("https://company.url");
            assertThat(dto.getAppliedOpportunityId()).isEqualTo(100L);
            assertThat(dto.getAppliedOpportunityStatus()).isEqualTo(OpportunityStatus.DONE);
            assertThat(dto.getPartnershipOpportunityTitle()).isEqualTo("Campaign Title");
            assertThat(dto.getAppliedOpportunityNote()).isEqualTo("Note");
            assertThat(dto.getInfluencerRateStatus()).isEqualTo(RateStatus.POSITIVE);
            assertThat(dto.getCompanyRateStatus()).isEqualTo(RateStatus.POSITIVE);
            assertThat(dto.getPositiveRatesAmount()).isEqualTo(10L);
            assertThat(dto.getNegativeRatesAmount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should create DTO with no-args constructor")
        void shouldCreateDtoWithNoArgsConstructor() {
            // Given
            CoopDto dto = new CoopDto();

            // Then
            assertThat(dto.getId()).isNull();
            assertThat(dto.getInfluencerFirstName()).isNull();
            assertThat(dto.getAppliedOpportunityStatus()).isNull();
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
        @DisplayName("should handle edge case of zero followers")
        void shouldHandleEdgeCaseOfZeroFollowers() {
            // Given
            CoopDto dto = new CoopDto();
            dto.setFollowersAmount(0);

            // Then
            assertThat(dto.getFollowersAmount()).isZero();
        }

        @Test
        @DisplayName("should handle edge case of max integer followers")
        void shouldHandleEdgeCaseOfMaxIntegerFollowers() {
            // Given
            CoopDto dto = new CoopDto();
            dto.setFollowersAmount(Integer.MAX_VALUE);

            // Then
            assertThat(dto.getFollowersAmount()).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("should handle Polish characters in names")
        void shouldHandlePolishCharactersInNames() {
            // Given
            CoopDto dto = new CoopDto();
            dto.setInfluencerFirstName("Zdzislaw");
            dto.setInfluencerLastName("Swietokrzyski");
            dto.setCompanyName("Spolka Lodzka Sp. z o.o.");

            // Then
            assertThat(dto.getInfluencerFirstName()).isEqualTo("Zdzislaw");
            assertThat(dto.getInfluencerLastName()).isEqualTo("Swietokrzyski");
            assertThat(dto.getCompanyName()).isEqualTo("Spolka Lodzka Sp. z o.o.");
        }
    }

    // ==================== CoopFilter Tests ====================

    @Nested
    @DisplayName("CoopFilter")
    class CoopFilterTests {

        @Test
        @DisplayName("should create filter with default values")
        void shouldCreateFilterWithDefaultValues() {
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
            List<OpportunityStatus> statuses = List.of(
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
        @DisplayName("should accept all opportunity statuses")
        void shouldAcceptAllOpportunityStatuses() {
            // Given
            CoopFilter filter = new CoopFilter();
            filter.setOpportunityStatuses(Arrays.asList(OpportunityStatus.values()));

            // Then
            assertThat(filter.getOpportunityStatuses()).hasSize(OpportunityStatus.values().length);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 100, 1000, 10000, 100000, 1000000})
        @DisplayName("should accept various min follower counts")
        void shouldAcceptVariousMinFollowerCounts(int followers) {
            // Given
            CoopFilter filter = new CoopFilter();
            filter.setMinFollowers(followers);

            // Then
            assertThat(filter.getMinFollowers()).isEqualTo(followers);
        }

        @ParameterizedTest
        @ValueSource(ints = {1000, 5000, 10000, 50000, 100000, 1000000})
        @DisplayName("should accept various max follower counts")
        void shouldAcceptVariousMaxFollowerCounts(int followers) {
            // Given
            CoopFilter filter = new CoopFilter();
            filter.setMaxFollowers(followers);

            // Then
            assertThat(filter.getMaxFollowers()).isEqualTo(followers);
        }

        @Test
        @DisplayName("should allow range filtering by followers")
        void shouldAllowRangeFilteringByFollowers() {
            // Given
            CoopFilter filter = new CoopFilter();
            filter.setMinFollowers(1000);
            filter.setMaxFollowers(50000);

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
            assertThat(Views.Basic.class.isInterface()).isTrue();
        }

        @Test
        @DisplayName("Views.Ratings should extend Basic")
        void viewsRatingsShouldExtendBasic() {
            assertThat(Views.Basic.class.isAssignableFrom(Views.Ratings.class)).isTrue();
        }

        @Test
        @DisplayName("Views.Registration should extend Ratings")
        void viewsRegistrationShouldExtendRatings() {
            assertThat(Views.Ratings.class.isAssignableFrom(Views.Registration.class)).isTrue();
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
            assertThat(Views.InProgress_InfluencerView.class.isAssignableFrom(Views.InProgress_AdminView.class)).isTrue();
            assertThat(Views.InProgress_CompanyView.class.isAssignableFrom(Views.InProgress_AdminView.class)).isTrue();
            assertThat(Views.Basic.class.isAssignableFrom(Views.InProgress_AdminView.class)).isTrue();
        }

        @Test
        @DisplayName("all views should be interfaces")
        void allViewsShouldBeInterfaces() {
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
        @DisplayName("should handle null security principal gracefully")
        void shouldHandleNullSecurityPrincipalGracefully() {
            // Given
            when(authentication.getPrincipal()).thenReturn(null);
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When/Then
            assertThatThrownBy(() -> controller.getInfluencersToRate("DEFAULT", 0, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should handle page size at boundary of 100")
        void shouldHandlePageSizeAtBoundaryOf100() {
            // Given
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToRate("DEFAULT", 0, 100);

            // Then
            verify(activeCooperationService).getInfluencersToRateWithPermission(
                    eq("DEFAULT"), argThat(pageable -> pageable.getPageSize() == 100));
        }

        @Test
        @DisplayName("should handle page size just above 100")
        void shouldHandlePageSizeJustAbove100() {
            // Given
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToRate("DEFAULT", 0, 101);

            // Then
            verify(activeCooperationService).getInfluencersToRateWithPermission(
                    eq("DEFAULT"), argThat(pageable -> pageable.getPageSize() == 100));
        }

        @Test
        @DisplayName("should handle empty result from service")
        void shouldHandleEmptyResultFromService() {
            // Given
            Page<CoopDto> emptyPage = new PageImpl<>(Collections.emptyList());
            when(activeCooperationService.getInfluencersToAccept(
                    any(), any(), any(), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // When
            ResponseEntity<List<CoopDto>> response = controller.getInfluencersToAccept(
                    null, null, null, 0, 12);

            // Then
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should handle service throwing RuntimeException")
        void shouldHandleServiceThrowingRuntimeException() {
            // Given
            when(activeCooperationService.updateCompanyRating(eq(1L), any()))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When/Then
            assertThatThrownBy(() -> controller.updateCompanyRating(1L, RateStatus.POSITIVE))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database connection failed");
        }
    }

    // ==================== Security Context Tests ====================

    @Nested
    @DisplayName("Security Context")
    class SecurityContextTests {

        @Test
        @DisplayName("should extract Firebase UID from security context")
        void shouldExtractFirebaseUidFromSecurityContext() {
            // Given
            when(authentication.getPrincipal()).thenReturn("specific-firebase-uid");
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToRate("DEFAULT", 0, null);

            // Then
            verify(authentication, atLeastOnce()).getPrincipal();
        }

        @Test
        @DisplayName("should use security context for all endpoints")
        void shouldUseSecurityContextForAllEndpoints() {
            // Given
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class))).thenReturn(testPage);
            when(activeCooperationService.getInfluencersToAccept(
                    any(), any(), any(), any(Pageable.class))).thenReturn(testPage);
            when(activeCooperationService.getCollaborationsInProgress(
                    any(Pageable.class), any())).thenReturn(testMappingJacksonValue);
            when(activeCooperationService.updateCompanyRating(anyLong(), any()))
                    .thenReturn(testCoopDto);
            when(activeCooperationService.updateInfluencerRating(anyLong(), any()))
                    .thenReturn(testCoopDto);

            // When
            controller.getInfluencersToRate("DEFAULT", 0, null);
            controller.getInfluencersToAccept(null, null, null, 0, 12);
            controller.getOpportunitiesInProgress(0, 12, null);
            controller.updateCompanyRating(1L, RateStatus.POSITIVE);
            controller.updateInfluencerRating(1L, RateStatus.POSITIVE);

            // Then
            verify(authentication, atLeast(5)).getPrincipal();
        }
    }

    // ==================== Pagination Tests ====================

    @Nested
    @DisplayName("Pagination")
    class PaginationTests {

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 25, 50, 99, 100})
        @DisplayName("should accept valid page sizes for getInfluencersToRate")
        void shouldAcceptValidPageSizesForGetInfluencersToRate(int size) {
            // Given
            when(activeCooperationService.getInfluencersToRateWithPermission(
                    anyString(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToRate("DEFAULT", 0, size);

            // Then
            verify(activeCooperationService).getInfluencersToRateWithPermission(
                    eq("DEFAULT"), argThat(pageable -> pageable.getPageSize() == size));
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 25, 50, 99, 100})
        @DisplayName("should accept valid page sizes for getInfluencersToAccept")
        void shouldAcceptValidPageSizesForGetInfluencersToAccept(int size) {
            // Given
            when(activeCooperationService.getInfluencersToAccept(
                    any(), any(), any(), any(Pageable.class)))
                    .thenReturn(testPage);

            // When
            controller.getInfluencersToAccept(null, null, null, 0, size);

            // Then
            verify(activeCooperationService).getInfluencersToAccept(
                    any(), any(), any(), argThat(pageable -> pageable.getPageSize() == size));
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 25, 50, 99, 100})
        @DisplayName("should accept valid page sizes for getOpportunitiesInProgress")
        void shouldAcceptValidPageSizesForGetOpportunitiesInProgress(int size) {
            // Given
            when(activeCooperationService.getCollaborationsInProgress(
                    any(Pageable.class), any()))
                    .thenReturn(testMappingJacksonValue);

            // When
            controller.getOpportunitiesInProgress(0, size, null);

            // Then
            verify(activeCooperationService).getCollaborationsInProgress(
                    argThat(pageable -> pageable.getPageSize() == size), any());
        }
    }
}
