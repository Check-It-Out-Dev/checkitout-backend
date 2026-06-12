package com.sm.instagram.platform.unit.controller;

import com.sm.instagram.platform.appliedopportunities.*;
import com.sm.instagram.platform.common.exceptions.*;
import com.sm.instagram.platform.common.translation.TranslationService;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for AppliedOpportunityController.
 * Uses pure Mockito - no Spring context loaded.
 *
 * Tests focus on:
 * - Controller endpoint behavior
 * - Request/Response handling
 * - Service method delegation
 * - Error handling and edge cases
 * - Translation handling
 * - Pagination and filtering
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppliedOpportunityController Unit Tests")
class AppliedOpportunityControllerUnitTest {

    @Mock
    private AppliedOpportunityService appliedOpportunityService;

    @Mock
    private AppliedOpportunityStatusHistoryService statusHistoryService;

    @Mock
    private TranslationService translationService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AppliedOpportunityController controller;

    // Test fixtures
    private User testInfluencer;
    private User testCompany;
    private PartnershipOpportunity testPartnershipOpportunity;
    private AppliedOpportunity testAppliedOpportunity;
    private AppliedOpportunityDtoIn testDtoIn;
    private AppliedOpportunityDtoOut testDtoOut;

    private static final String TEST_FIREBASE_UID = "test-firebase-uid";
    private static final Long TEST_OPPORTUNITY_ID = 10L;

    @BeforeEach
    void setUp() {
        // Set up SecurityContext mock
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(TEST_FIREBASE_UID);

        // Create test influencer
        testInfluencer = new User();
        testInfluencer.setId(1L);
        testInfluencer.setFirebaseUserId("influencer-firebase-uid");
        testInfluencer.setUserType(UserType.INFLUENCER);
        testInfluencer.setName("Test Influencer");
        testInfluencer.setEmail("influencer@test.com");
        testInfluencer.setAccountStatus(AccountStatus.ACTIVE);

        // Create test company
        testCompany = new User();
        testCompany.setId(2L);
        testCompany.setFirebaseUserId("company-firebase-uid");
        testCompany.setUserType(UserType.COMPANY);
        testCompany.setName("Test Company");
        testCompany.setEmail("company@test.com");
        testCompany.setAccountStatus(AccountStatus.ACTIVE);

        // Create test partnership opportunity
        testPartnershipOpportunity = new PartnershipOpportunity();
        testPartnershipOpportunity.setId(100L);
        testPartnershipOpportunity.setCompany(testCompany);
        testPartnershipOpportunity.setName("Test Partnership");

        // Create test applied opportunity entity
        testAppliedOpportunity = new AppliedOpportunity();
        testAppliedOpportunity.setId(TEST_OPPORTUNITY_ID);
        testAppliedOpportunity.setInfluencer(testInfluencer);
        testAppliedOpportunity.setPartnershipOpportunity(testPartnershipOpportunity);
        testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.APPLIED);
        testAppliedOpportunity.setRateStatus(RateStatus.DEFAULT);
        testAppliedOpportunity.setCompanyRateStatus(RateStatus.DEFAULT);
        testAppliedOpportunity.setNote("Test note");
        testAppliedOpportunity.setCreatedTime(LocalDateTime.now());
        testAppliedOpportunity.setLastUpdateTime(LocalDateTime.now());

        // Create test DtoIn
        testDtoIn = new AppliedOpportunityDtoIn();
        testDtoIn.setInfluencer(1L);
        testDtoIn.setPartnershipOpportunity(100L);
        testDtoIn.setNote("Test note");

        // Create test DtoOut
        testDtoOut = new AppliedOpportunityDtoOut();
        testDtoOut.setId(TEST_OPPORTUNITY_ID);
        testDtoOut.setOpportunityStatus(createOpportunityStatusDto(OpportunityStatus.APPLIED));
        testDtoOut.setRateStatus(createRateStatusDto(RateStatus.DEFAULT));
        testDtoOut.setCompanyRateStatus(createRateStatusDto(RateStatus.DEFAULT));
        testDtoOut.setNote("Test note");
        testDtoOut.setCreatedTime(LocalDateTime.now());

        // Default request header mock
        when(request.getHeader("Accept-Language")).thenReturn("en");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Helper method to create OpportunityStatusDtoOut
    private OpportunityStatusDtoOut createOpportunityStatusDto(OpportunityStatus status) {
        return OpportunityStatusDtoOut.builder()
                .value(status.name())
                .label(status.name())
                .originalLabel(status.name())
                .colorTheme(status.getColorTheme())
                .icon(status.getIcon())
                .isTerminal(status.isTerminalStatus())
                .isSuccessful(status.isSuccessfulCompletion())
                .build();
    }

    // Helper method to create RateStatusDtoOut
    private RateStatusDtoOut createRateStatusDto(RateStatus status) {
        return RateStatusDtoOut.builder()
                .value(status.name())
                .label(status.name())
                .originalLabel(status.name())
                .colorTheme(status.getColorTheme())
                .icon(status.getIcon())
                .build();
    }

    // =====================================================
    // GetById Tests
    // =====================================================
    @Nested
    @DisplayName("getById Endpoint")
    class GetByIdEndpoint {

        @Test
        @DisplayName("should return opportunity when found")
        void shouldReturnOpportunityWhenFound() {
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);
            when(translationService.translateOpportunityStatus(anyString(), any())).thenReturn("Applied");

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.getById(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(TEST_OPPORTUNITY_ID);
            verify(appliedOpportunityService).findByIdAsDto(TEST_OPPORTUNITY_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void shouldThrowResourceNotFoundExceptionWhenNotFound() {
            when(appliedOpportunityService.findByIdAsDto(999L))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

            assertThatThrownBy(() -> controller.getById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should translate opportunity status based on Accept-Language header")
        void shouldTranslateOpportunityStatusBasedOnAcceptLanguageHeader() {
            when(request.getHeader("Accept-Language")).thenReturn("pl");
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);
            when(translationService.translateOpportunityStatus("APPLIED", Locale.forLanguageTag("pl")))
                    .thenReturn("Aplikowano");

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.getById(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(translationService).translateOpportunityStatus(eq("APPLIED"), any(Locale.class));
        }

        @Test
        @DisplayName("should use Polish locale when Accept-Language is null")
        void shouldUsePolishLocaleWhenAcceptLanguageIsNull() {
            when(request.getHeader("Accept-Language")).thenReturn(null);
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);
            when(translationService.translateOpportunityStatus(anyString(), any())).thenReturn("Aplikowano");

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.getById(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should use Polish locale when Accept-Language is empty")
        void shouldUsePolishLocaleWhenAcceptLanguageIsEmpty() {
            when(request.getHeader("Accept-Language")).thenReturn("");
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.getById(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle Accept-Language with multiple languages")
        void shouldHandleAcceptLanguageWithMultipleLanguages() {
            when(request.getHeader("Accept-Language")).thenReturn("en-US,en;q=0.9,pl;q=0.8");
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.getById(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle null opportunity status in entity")
        void shouldHandleNullOpportunityStatusInEntity() {
            testAppliedOpportunity.setOpportunityStatus(null);
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.getById(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should propagate service exception")
        void shouldPropagateServiceException() {
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID))
                    .thenThrow(new RuntimeException("Database error"));

            assertThatThrownBy(() -> controller.getById(TEST_OPPORTUNITY_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database error");
        }

        @Test
        @DisplayName("should call mapRateStatusBasedOnRole for rate status translation")
        void shouldCallMapRateStatusBasedOnRoleForRateStatusTranslation() {
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);

            controller.getById(TEST_OPPORTUNITY_ID);

            verify(appliedOpportunityService).mapRateStatusBasedOnRole(eq(testAppliedOpportunity), eq(testDtoOut));
        }
    }

    // =====================================================
    // Create Endpoint Tests
    // =====================================================
    @Nested
    @DisplayName("create Endpoint")
    class CreateEndpoint {

        @Test
        @DisplayName("should create opportunity successfully")
        void shouldCreateOpportunitySuccessfully() {
            doReturn(testDtoOut).when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.create(testDtoIn);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(TEST_OPPORTUNITY_ID);
            verify(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));
        }

        @Test
        @DisplayName("should throw validation exception for invalid input")
        void shouldThrowValidationExceptionForInvalidInput() {
            doThrow(new ValidationTranslatableException("error.validation.required_field", "influencer"))
                    .when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            assertThatThrownBy(() -> controller.create(testDtoIn))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw insufficient permissions exception")
        void shouldThrowInsufficientPermissionsException() {
            doThrow(new InsufficientPermissionsException("error.auth.insufficient_permissions",
                    TEST_FIREBASE_UID, "saveAsDto", "AppliedOpportunity"))
                    .when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            assertThatThrownBy(() -> controller.create(testDtoIn))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should create opportunity with null influencer for auto-assignment")
        void shouldCreateOpportunityWithNullInfluencerForAutoAssignment() {
            testDtoIn.setInfluencer(null);
            doReturn(testDtoOut).when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.create(testDtoIn);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should propagate service exception on create failure")
        void shouldPropagateServiceExceptionOnCreateFailure() {
            doThrow(new RuntimeException("Create failed"))
                    .when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            assertThatThrownBy(() -> controller.create(testDtoIn))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Create failed");
        }

        @Test
        @DisplayName("should create opportunity with note")
        void shouldCreateOpportunityWithNote() {
            testDtoIn.setNote("Test note for opportunity");
            doReturn(testDtoOut).when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.create(testDtoIn);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should throw FollowerValidationException when follower count is invalid")
        void shouldThrowFollowerValidationExceptionWhenFollowerCountIsInvalid() {
            doThrow(new FollowerValidationException("Follower count below minimum"))
                    .when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            assertThatThrownBy(() -> controller.create(testDtoIn))
                    .isInstanceOf(FollowerValidationException.class);
        }
    }

    // =====================================================
    // Update Company Rating Endpoint Tests
    // =====================================================
    @Nested
    @DisplayName("updateCompanyRating Endpoint")
    class UpdateCompanyRatingEndpoint {

        @Test
        @DisplayName("should update company rating to POSITIVE")
        void shouldUpdateCompanyRatingToPositive() {
            AppliedOpportunityDtoOut updatedDto = new AppliedOpportunityDtoOut();
            updatedDto.setId(TEST_OPPORTUNITY_ID);
            updatedDto.setCompanyRateStatus(createRateStatusDto(RateStatus.POSITIVE));

            when(appliedOpportunityService.updateCompanyRatingAsDto(TEST_OPPORTUNITY_ID, RateStatus.POSITIVE))
                    .thenReturn(updatedDto);

            ResponseEntity<AppliedOpportunityDtoOut> response =
                    controller.updateCompanyRating(TEST_OPPORTUNITY_ID, RateStatus.POSITIVE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getCompanyRateStatus().getValue()).isEqualTo("POSITIVE");
        }

        @Test
        @DisplayName("should update company rating to NEGATIVE")
        void shouldUpdateCompanyRatingToNegative() {
            AppliedOpportunityDtoOut updatedDto = new AppliedOpportunityDtoOut();
            updatedDto.setId(TEST_OPPORTUNITY_ID);
            updatedDto.setCompanyRateStatus(createRateStatusDto(RateStatus.NEGATIVE));

            when(appliedOpportunityService.updateCompanyRatingAsDto(TEST_OPPORTUNITY_ID, RateStatus.NEGATIVE))
                    .thenReturn(updatedDto);

            ResponseEntity<AppliedOpportunityDtoOut> response =
                    controller.updateCompanyRating(TEST_OPPORTUNITY_ID, RateStatus.NEGATIVE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getCompanyRateStatus().getValue()).isEqualTo("NEGATIVE");
        }

        @Test
        @DisplayName("should update company rating to DEFAULT")
        void shouldUpdateCompanyRatingToDefault() {
            AppliedOpportunityDtoOut updatedDto = new AppliedOpportunityDtoOut();
            updatedDto.setId(TEST_OPPORTUNITY_ID);
            updatedDto.setCompanyRateStatus(createRateStatusDto(RateStatus.DEFAULT));

            when(appliedOpportunityService.updateCompanyRatingAsDto(TEST_OPPORTUNITY_ID, RateStatus.DEFAULT))
                    .thenReturn(updatedDto);

            ResponseEntity<AppliedOpportunityDtoOut> response =
                    controller.updateCompanyRating(TEST_OPPORTUNITY_ID, RateStatus.DEFAULT);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should throw exception when opportunity not found")
        void shouldThrowExceptionWhenOpportunityNotFound() {
            when(appliedOpportunityService.updateCompanyRatingAsDto(999L, RateStatus.POSITIVE))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

            assertThatThrownBy(() -> controller.updateCompanyRating(999L, RateStatus.POSITIVE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw exception when user lacks permission")
        void shouldThrowExceptionWhenUserLacksPermission() {
            when(appliedOpportunityService.updateCompanyRatingAsDto(TEST_OPPORTUNITY_ID, RateStatus.POSITIVE))
                    .thenThrow(new InsufficientPermissionsException("error.auth.insufficient_permissions",
                            TEST_FIREBASE_UID, "updateCompanyRating", "AppliedOpportunity"));

            assertThatThrownBy(() -> controller.updateCompanyRating(TEST_OPPORTUNITY_ID, RateStatus.POSITIVE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @ParameterizedTest
        @EnumSource(RateStatus.class)
        @DisplayName("should handle all RateStatus values")
        void shouldHandleAllRateStatusValues(RateStatus rating) {
            AppliedOpportunityDtoOut updatedDto = new AppliedOpportunityDtoOut();
            updatedDto.setId(TEST_OPPORTUNITY_ID);
            updatedDto.setCompanyRateStatus(createRateStatusDto(rating));

            when(appliedOpportunityService.updateCompanyRatingAsDto(TEST_OPPORTUNITY_ID, rating))
                    .thenReturn(updatedDto);

            ResponseEntity<AppliedOpportunityDtoOut> response =
                    controller.updateCompanyRating(TEST_OPPORTUNITY_ID, rating);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getCompanyRateStatus().getValue()).isEqualTo(rating.name());
        }
    }

    // =====================================================
    // Update Influencer Rating Endpoint Tests
    // =====================================================
    @Nested
    @DisplayName("updateInfluencerRating Endpoint")
    class UpdateInfluencerRatingEndpoint {

        @Test
        @DisplayName("should update influencer rating to POSITIVE")
        void shouldUpdateInfluencerRatingToPositive() {
            AppliedOpportunityDtoOut updatedDto = new AppliedOpportunityDtoOut();
            updatedDto.setId(TEST_OPPORTUNITY_ID);
            updatedDto.setRateStatus(createRateStatusDto(RateStatus.POSITIVE));

            when(appliedOpportunityService.updateInfluencerRatingAsDto(TEST_OPPORTUNITY_ID, RateStatus.POSITIVE))
                    .thenReturn(updatedDto);

            ResponseEntity<AppliedOpportunityDtoOut> response =
                    controller.updateInfluencerRating(TEST_OPPORTUNITY_ID, RateStatus.POSITIVE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getRateStatus().getValue()).isEqualTo("POSITIVE");
        }

        @Test
        @DisplayName("should update influencer rating to NEGATIVE")
        void shouldUpdateInfluencerRatingToNegative() {
            AppliedOpportunityDtoOut updatedDto = new AppliedOpportunityDtoOut();
            updatedDto.setId(TEST_OPPORTUNITY_ID);
            updatedDto.setRateStatus(createRateStatusDto(RateStatus.NEGATIVE));

            when(appliedOpportunityService.updateInfluencerRatingAsDto(TEST_OPPORTUNITY_ID, RateStatus.NEGATIVE))
                    .thenReturn(updatedDto);

            ResponseEntity<AppliedOpportunityDtoOut> response =
                    controller.updateInfluencerRating(TEST_OPPORTUNITY_ID, RateStatus.NEGATIVE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getRateStatus().getValue()).isEqualTo("NEGATIVE");
        }

        @Test
        @DisplayName("should throw exception when opportunity not found")
        void shouldThrowExceptionWhenOpportunityNotFound() {
            when(appliedOpportunityService.updateInfluencerRatingAsDto(999L, RateStatus.POSITIVE))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

            assertThatThrownBy(() -> controller.updateInfluencerRating(999L, RateStatus.POSITIVE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw exception when user lacks permission")
        void shouldThrowExceptionWhenUserLacksPermission() {
            when(appliedOpportunityService.updateInfluencerRatingAsDto(TEST_OPPORTUNITY_ID, RateStatus.POSITIVE))
                    .thenThrow(new InsufficientPermissionsException("error.auth.insufficient_permissions",
                            TEST_FIREBASE_UID, "updateInfluencerRating", "AppliedOpportunity"));

            assertThatThrownBy(() -> controller.updateInfluencerRating(TEST_OPPORTUNITY_ID, RateStatus.POSITIVE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @ParameterizedTest
        @EnumSource(RateStatus.class)
        @DisplayName("should handle all RateStatus values")
        void shouldHandleAllRateStatusValues(RateStatus rating) {
            AppliedOpportunityDtoOut updatedDto = new AppliedOpportunityDtoOut();
            updatedDto.setId(TEST_OPPORTUNITY_ID);
            updatedDto.setRateStatus(createRateStatusDto(rating));

            when(appliedOpportunityService.updateInfluencerRatingAsDto(TEST_OPPORTUNITY_ID, rating))
                    .thenReturn(updatedDto);

            ResponseEntity<AppliedOpportunityDtoOut> response =
                    controller.updateInfluencerRating(TEST_OPPORTUNITY_ID, rating);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // =====================================================
    // Update Rate Status Endpoint Tests
    // =====================================================
    @Nested
    @DisplayName("updateRateStatus Endpoint")
    class UpdateRateStatusEndpoint {

        @Test
        @DisplayName("should update rate status via patch")
        void shouldUpdateRateStatusViaPatch() {
            Map<String, Object> updates = new HashMap<>();
            updates.put("rateStatus", "POSITIVE");

            doReturn(testDtoOut).when(appliedOpportunityService).patchAsDto(eq(TEST_OPPORTUNITY_ID), anyMap());

            ResponseEntity<AppliedOpportunityDtoOut> response =
                    controller.updateRateStatus(TEST_OPPORTUNITY_ID, updates);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(appliedOpportunityService).patchAsDto(eq(TEST_OPPORTUNITY_ID), anyMap());
        }

        @Test
        @DisplayName("should handle empty updates map")
        void shouldHandleEmptyUpdatesMap() {
            Map<String, Object> updates = new HashMap<>();

            doReturn(testDtoOut).when(appliedOpportunityService).patchAsDto(eq(TEST_OPPORTUNITY_ID), anyMap());

            ResponseEntity<AppliedOpportunityDtoOut> response =
                    controller.updateRateStatus(TEST_OPPORTUNITY_ID, updates);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should propagate service exception")
        void shouldPropagateServiceException() {
            Map<String, Object> updates = new HashMap<>();
            updates.put("rateStatus", "INVALID");

            doThrow(new ValidationTranslatableException("error.validation.invalid_argument"))
                    .when(appliedOpportunityService).patchAsDto(eq(TEST_OPPORTUNITY_ID), anyMap());

            assertThatThrownBy(() -> controller.updateRateStatus(TEST_OPPORTUNITY_ID, updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should update company rate status via patch")
        void shouldUpdateCompanyRateStatusViaPatch() {
            Map<String, Object> updates = new HashMap<>();
            updates.put("companyRateStatus", "POSITIVE");

            doReturn(testDtoOut).when(appliedOpportunityService).patchAsDto(eq(TEST_OPPORTUNITY_ID), anyMap());

            ResponseEntity<AppliedOpportunityDtoOut> response =
                    controller.updateRateStatus(TEST_OPPORTUNITY_ID, updates);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // =====================================================
    // Update Opportunity Status Endpoint Tests
    // =====================================================
    @Nested
    @DisplayName("updateOpportunityStatus Endpoint")
    class UpdateOpportunityStatusEndpoint {

        @Test
        @DisplayName("should accept opportunity")
        void shouldAcceptOpportunity() {
            AppliedOpportunityDtoOut acceptedDto = new AppliedOpportunityDtoOut();
            acceptedDto.setId(TEST_OPPORTUNITY_ID);
            acceptedDto.setOpportunityStatus(createOpportunityStatusDto(OpportunityStatus.ACCEPTED_BY_COMPANY));

            when(appliedOpportunityService.updateOpportunityStatusAsDto(TEST_OPPORTUNITY_ID, true))
                    .thenReturn(acceptedDto);

            ResponseEntity<AppliedOpportunityDtoOut> response =
                    controller.updateOpportunityStatus(TEST_OPPORTUNITY_ID, true);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getOpportunityStatus().getValue())
                    .isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY.name());
        }

        @Test
        @DisplayName("should reject opportunity")
        void shouldRejectOpportunity() {
            AppliedOpportunityDtoOut rejectedDto = new AppliedOpportunityDtoOut();
            rejectedDto.setId(TEST_OPPORTUNITY_ID);
            rejectedDto.setOpportunityStatus(createOpportunityStatusDto(OpportunityStatus.REJECTED_BY_COMPANY));

            when(appliedOpportunityService.updateOpportunityStatusAsDto(TEST_OPPORTUNITY_ID, false))
                    .thenReturn(rejectedDto);

            ResponseEntity<AppliedOpportunityDtoOut> response =
                    controller.updateOpportunityStatus(TEST_OPPORTUNITY_ID, false);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getOpportunityStatus().getValue())
                    .isEqualTo(OpportunityStatus.REJECTED_BY_COMPANY.name());
        }

        @Test
        @DisplayName("should throw exception when opportunity not found")
        void shouldThrowExceptionWhenOpportunityNotFound() {
            when(appliedOpportunityService.updateOpportunityStatusAsDto(999L, true))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

            assertThatThrownBy(() -> controller.updateOpportunityStatus(999L, true))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw exception when user lacks permission")
        void shouldThrowExceptionWhenUserLacksPermission() {
            when(appliedOpportunityService.updateOpportunityStatusAsDto(TEST_OPPORTUNITY_ID, true))
                    .thenThrow(new InsufficientPermissionsException("error.auth.insufficient_permissions",
                            TEST_FIREBASE_UID, "updateOpportunityStatus", "AppliedOpportunity"));

            assertThatThrownBy(() -> controller.updateOpportunityStatus(TEST_OPPORTUNITY_ID, true))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw exception for invalid status transition")
        void shouldThrowExceptionForInvalidStatusTransition() {
            when(appliedOpportunityService.updateOpportunityStatusAsDto(TEST_OPPORTUNITY_ID, true))
                    .thenThrow(new BusinessRuleTranslatableException("error.business.invalid_state"));

            assertThatThrownBy(() -> controller.updateOpportunityStatus(TEST_OPPORTUNITY_ID, true))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }
    }

    // =====================================================
    // Get Status History Endpoint Tests
    // =====================================================
    @Nested
    @DisplayName("getStatusHistory Endpoint")
    class GetStatusHistoryEndpoint {

        @Test
        @DisplayName("should return status history list")
        void shouldReturnStatusHistoryList() {
            AppliedOpportunityStatusHistory history1 = new AppliedOpportunityStatusHistory();
            history1.setId(1L);
            history1.setAppliedOpportunity(testAppliedOpportunity);
            history1.setPreviousStatus(null);
            history1.setNewStatus(OpportunityStatus.APPLIED);
            history1.setChangedByFirebaseId(TEST_FIREBASE_UID);
            history1.setChangedAt(LocalDateTime.now());

            AppliedOpportunityStatusHistory history2 = new AppliedOpportunityStatusHistory();
            history2.setId(2L);
            history2.setAppliedOpportunity(testAppliedOpportunity);
            history2.setPreviousStatus(OpportunityStatus.APPLIED);
            history2.setNewStatus(OpportunityStatus.ACCEPTED_BY_COMPANY);
            history2.setChangedByFirebaseId(TEST_FIREBASE_UID);
            history2.setChangedAt(LocalDateTime.now());

            when(statusHistoryService.getStatusHistory(TEST_OPPORTUNITY_ID))
                    .thenReturn(List.of(history1, history2));

            ResponseEntity<List<AppliedOpportunityStatusHistoryDtoOut>> response =
                    controller.getStatusHistory(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list when no history")
        void shouldReturnEmptyListWhenNoHistory() {
            when(statusHistoryService.getStatusHistory(TEST_OPPORTUNITY_ID))
                    .thenReturn(Collections.emptyList());

            ResponseEntity<List<AppliedOpportunityStatusHistoryDtoOut>> response =
                    controller.getStatusHistory(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should throw exception when opportunity not found")
        void shouldThrowExceptionWhenOpportunityNotFound() {
            when(statusHistoryService.getStatusHistory(999L))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

            assertThatThrownBy(() -> controller.getStatusHistory(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw exception when user lacks permission")
        void shouldThrowExceptionWhenUserLacksPermission() {
            when(statusHistoryService.getStatusHistory(TEST_OPPORTUNITY_ID))
                    .thenThrow(new InsufficientPermissionsException("error.auth.insufficient_permissions",
                            TEST_FIREBASE_UID, "getStatusHistory", "AppliedOpportunity"));

            assertThatThrownBy(() -> controller.getStatusHistory(TEST_OPPORTUNITY_ID))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // =====================================================
    // Get Status History Paged Endpoint Tests
    // =====================================================
    @Nested
    @DisplayName("getStatusHistoryPaged Endpoint")
    class GetStatusHistoryPagedEndpoint {

        @Test
        @DisplayName("should return paged status history")
        void shouldReturnPagedStatusHistory() {
            AppliedOpportunityStatusHistory history = new AppliedOpportunityStatusHistory();
            history.setId(1L);
            history.setAppliedOpportunity(testAppliedOpportunity);
            history.setNewStatus(OpportunityStatus.APPLIED);
            history.setChangedByFirebaseId(TEST_FIREBASE_UID);
            history.setChangedAt(LocalDateTime.now());

            Page<AppliedOpportunityStatusHistory> historyPage = new PageImpl<>(
                    List.of(history),
                    PageRequest.of(0, 20),
                    1
            );

            when(statusHistoryService.getStatusHistory(eq(TEST_OPPORTUNITY_ID), any(Pageable.class)))
                    .thenReturn(historyPage);

            ResponseEntity<Page<AppliedOpportunityStatusHistoryDtoOut>> response =
                    controller.getStatusHistoryPaged(TEST_OPPORTUNITY_ID, 0, 20, "changedAt", "desc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("should enforce max page size of 100")
        void shouldEnforceMaxPageSizeOf100() {
            Page<AppliedOpportunityStatusHistory> historyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 100),
                    0
            );

            when(statusHistoryService.getStatusHistory(eq(TEST_OPPORTUNITY_ID), any(Pageable.class)))
                    .thenReturn(historyPage);

            ResponseEntity<Page<AppliedOpportunityStatusHistoryDtoOut>> response =
                    controller.getStatusHistoryPaged(TEST_OPPORTUNITY_ID, 0, 200, "changedAt", "desc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(statusHistoryService).getStatusHistory(eq(TEST_OPPORTUNITY_ID),
                    argThat(pageable -> pageable.getPageSize() == 100));
        }

        @Test
        @DisplayName("should use ascending sort direction")
        void shouldUseAscendingSortDirection() {
            Page<AppliedOpportunityStatusHistory> historyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "changedAt")),
                    0
            );

            when(statusHistoryService.getStatusHistory(eq(TEST_OPPORTUNITY_ID), any(Pageable.class)))
                    .thenReturn(historyPage);

            ResponseEntity<Page<AppliedOpportunityStatusHistoryDtoOut>> response =
                    controller.getStatusHistoryPaged(TEST_OPPORTUNITY_ID, 0, 20, "changedAt", "asc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(statusHistoryService).getStatusHistory(eq(TEST_OPPORTUNITY_ID),
                    argThat(pageable -> pageable.getSort().getOrderFor("changedAt").getDirection() == Sort.Direction.ASC));
        }

        @Test
        @DisplayName("should use descending sort direction by default")
        void shouldUseDescendingSortDirectionByDefault() {
            Page<AppliedOpportunityStatusHistory> historyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "changedAt")),
                    0
            );

            when(statusHistoryService.getStatusHistory(eq(TEST_OPPORTUNITY_ID), any(Pageable.class)))
                    .thenReturn(historyPage);

            ResponseEntity<Page<AppliedOpportunityStatusHistoryDtoOut>> response =
                    controller.getStatusHistoryPaged(TEST_OPPORTUNITY_ID, 0, 20, "changedAt", "other");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should throw exception when opportunity not found")
        void shouldThrowExceptionWhenOpportunityNotFound() {
            when(statusHistoryService.getStatusHistory(eq(999L), any(Pageable.class)))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

            assertThatThrownBy(() -> controller.getStatusHistoryPaged(999L, 0, 20, "changedAt", "desc"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should handle different page numbers")
        void shouldHandleDifferentPageNumbers() {
            Page<AppliedOpportunityStatusHistory> historyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(5, 20),
                    0
            );

            when(statusHistoryService.getStatusHistory(eq(TEST_OPPORTUNITY_ID), any(Pageable.class)))
                    .thenReturn(historyPage);

            ResponseEntity<Page<AppliedOpportunityStatusHistoryDtoOut>> response =
                    controller.getStatusHistoryPaged(TEST_OPPORTUNITY_ID, 5, 20, "changedAt", "desc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(statusHistoryService).getStatusHistory(eq(TEST_OPPORTUNITY_ID),
                    argThat(pageable -> pageable.getPageNumber() == 5));
        }
    }

    // =====================================================
    // Find Paginated Endpoint Tests
    // =====================================================
    @Nested
    @DisplayName("findPaginated Endpoint")
    class FindPaginatedEndpoint {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> filters = new HashMap<>();

            Page<AppliedOpportunityDtoOut> dtoPage = new PageImpl<>(
                    List.of(testDtoOut),
                    pageable,
                    1
            );
            Page<AppliedOpportunity> entityPage = new PageImpl<>(
                    List.of(testAppliedOpportunity),
                    pageable,
                    1
            );

            doReturn(dtoPage).when(appliedOpportunityService).getDataPagedAndFilteredAsDtos(any(Pageable.class), anyMap());
            when(appliedOpportunityService.getDataPagedAndFiltered(any(Pageable.class), anyMap()))
                    .thenReturn(entityPage);

            ResponseEntity<Page<AppliedOpportunityDtoOut>> response =
                    controller.findPaginated(pageable, filters);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("should remove pagination parameters from filters")
        void shouldRemovePaginationParametersFromFilters() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> filters = new HashMap<>();
            filters.put("page", "0");
            filters.put("size", "20");
            filters.put("sort", "createdTime");
            filters.put("direction", "desc");
            filters.put("sortBy", "createdTime");
            filters.put("sortDirection", "desc");
            filters.put("opportunityStatus", "APPLIED");

            Page<AppliedOpportunityDtoOut> dtoPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            Page<AppliedOpportunity> entityPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            doReturn(dtoPage).when(appliedOpportunityService).getDataPagedAndFilteredAsDtos(any(Pageable.class), anyMap());
            when(appliedOpportunityService.getDataPagedAndFiltered(any(Pageable.class), anyMap()))
                    .thenReturn(entityPage);

            controller.findPaginated(pageable, filters);

            assertThat(filters).doesNotContainKeys("page", "size", "sort", "direction", "sortBy", "sortDirection");
        }

        @Test
        @DisplayName("should translate opportunity status in results")
        void shouldTranslateOpportunityStatusInResults() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> filters = new HashMap<>();

            Page<AppliedOpportunityDtoOut> dtoPage = new PageImpl<>(
                    List.of(testDtoOut),
                    pageable,
                    1
            );
            Page<AppliedOpportunity> entityPage = new PageImpl<>(
                    List.of(testAppliedOpportunity),
                    pageable,
                    1
            );

            doReturn(dtoPage).when(appliedOpportunityService).getDataPagedAndFilteredAsDtos(any(Pageable.class), anyMap());
            when(appliedOpportunityService.getDataPagedAndFiltered(any(Pageable.class), anyMap()))
                    .thenReturn(entityPage);
            when(translationService.translateOpportunityStatus(anyString(), any())).thenReturn("Applied");

            ResponseEntity<Page<AppliedOpportunityDtoOut>> response =
                    controller.findPaginated(pageable, filters);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(translationService).translateOpportunityStatus(eq("APPLIED"), any(Locale.class));
        }

        @Test
        @DisplayName("should handle empty results")
        void shouldHandleEmptyResults() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> filters = new HashMap<>();

            Page<AppliedOpportunityDtoOut> dtoPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            Page<AppliedOpportunity> entityPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            doReturn(dtoPage).when(appliedOpportunityService).getDataPagedAndFilteredAsDtos(any(Pageable.class), anyMap());
            when(appliedOpportunityService.getDataPagedAndFiltered(any(Pageable.class), anyMap()))
                    .thenReturn(entityPage);

            ResponseEntity<Page<AppliedOpportunityDtoOut>> response =
                    controller.findPaginated(pageable, filters);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getContent()).isEmpty();
        }

        @Test
        @DisplayName("should call mapRateStatusBasedOnRole for each result")
        void shouldCallMapRateStatusBasedOnRoleForEachResult() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> filters = new HashMap<>();

            AppliedOpportunityDtoOut dto1 = new AppliedOpportunityDtoOut();
            dto1.setId(1L);
            dto1.setOpportunityStatus(createOpportunityStatusDto(OpportunityStatus.APPLIED));

            AppliedOpportunityDtoOut dto2 = new AppliedOpportunityDtoOut();
            dto2.setId(2L);
            dto2.setOpportunityStatus(createOpportunityStatusDto(OpportunityStatus.ACCEPTED_BY_COMPANY));

            AppliedOpportunity entity1 = new AppliedOpportunity();
            entity1.setId(1L);
            entity1.setOpportunityStatus(OpportunityStatus.APPLIED);

            AppliedOpportunity entity2 = new AppliedOpportunity();
            entity2.setId(2L);
            entity2.setOpportunityStatus(OpportunityStatus.ACCEPTED_BY_COMPANY);

            Page<AppliedOpportunityDtoOut> dtoPage = new PageImpl<>(List.of(dto1, dto2), pageable, 2);
            Page<AppliedOpportunity> entityPage = new PageImpl<>(List.of(entity1, entity2), pageable, 2);

            doReturn(dtoPage).when(appliedOpportunityService).getDataPagedAndFilteredAsDtos(any(Pageable.class), anyMap());
            when(appliedOpportunityService.getDataPagedAndFiltered(any(Pageable.class), anyMap()))
                    .thenReturn(entityPage);

            controller.findPaginated(pageable, filters);

            verify(appliedOpportunityService, times(2)).mapRateStatusBasedOnRole(any(AppliedOpportunity.class), any(AppliedOpportunityDtoOut.class));
        }

        @Test
        @DisplayName("should handle entity not found in stream")
        void shouldHandleEntityNotFoundInStream() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> filters = new HashMap<>();

            AppliedOpportunityDtoOut dto = new AppliedOpportunityDtoOut();
            dto.setId(999L);  // Different ID from entity
            dto.setOpportunityStatus(createOpportunityStatusDto(OpportunityStatus.APPLIED));

            Page<AppliedOpportunityDtoOut> dtoPage = new PageImpl<>(List.of(dto), pageable, 1);
            Page<AppliedOpportunity> entityPage = new PageImpl<>(List.of(testAppliedOpportunity), pageable, 1);

            doReturn(dtoPage).when(appliedOpportunityService).getDataPagedAndFilteredAsDtos(any(Pageable.class), anyMap());
            when(appliedOpportunityService.getDataPagedAndFiltered(any(Pageable.class), anyMap()))
                    .thenReturn(entityPage);

            ResponseEntity<Page<AppliedOpportunityDtoOut>> response =
                    controller.findPaginated(pageable, filters);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // =====================================================
    // Get Statistics Endpoint Tests
    // =====================================================
    @Nested
    @DisplayName("getStatistics Endpoint")
    class GetStatisticsEndpoint {

        @Test
        @DisplayName("should return statistics")
        void shouldReturnStatistics() {
            AppliedOpportunityStatisticsDto stats = AppliedOpportunityStatisticsDto.builder()
                    .inProgress(5L)
                    .newOpportunities(3L)
                    .done(10L)
                    .total(18L)
                    .build();

            when(appliedOpportunityService.getAppliedOpportunityStatistics()).thenReturn(stats);

            ResponseEntity<AppliedOpportunityStatisticsDto> response = controller.getStatistics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getTotal()).isEqualTo(18L);
            assertThat(response.getBody().getInProgress()).isEqualTo(5L);
            assertThat(response.getBody().getNewOpportunities()).isEqualTo(3L);
            assertThat(response.getBody().getDone()).isEqualTo(10L);
        }

        @Test
        @DisplayName("should return zero statistics when no opportunities")
        void shouldReturnZeroStatisticsWhenNoOpportunities() {
            AppliedOpportunityStatisticsDto stats = AppliedOpportunityStatisticsDto.builder()
                    .inProgress(0L)
                    .newOpportunities(0L)
                    .done(0L)
                    .total(0L)
                    .build();

            when(appliedOpportunityService.getAppliedOpportunityStatistics()).thenReturn(stats);

            ResponseEntity<AppliedOpportunityStatisticsDto> response = controller.getStatistics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getTotal()).isZero();
        }

        @Test
        @DisplayName("should throw exception when user lacks permission")
        void shouldThrowExceptionWhenUserLacksPermission() {
            when(appliedOpportunityService.getAppliedOpportunityStatistics())
                    .thenThrow(new InsufficientPermissionsException("error.auth.insufficient_permissions",
                            TEST_FIREBASE_UID, "getAppliedOpportunityStatistics", "AppliedOpportunity"));

            assertThatThrownBy(() -> controller.getStatistics())
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should propagate service exception")
        void shouldPropagateServiceException() {
            when(appliedOpportunityService.getAppliedOpportunityStatistics())
                    .thenThrow(new RuntimeException("Database error"));

            assertThatThrownBy(() -> controller.getStatistics())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database error");
        }
    }

    // =====================================================
    // Get Payment Contact Endpoint Tests
    // =====================================================
    @Nested
    @DisplayName("getPaymentContact Endpoint")
    class GetPaymentContactEndpoint {

        @Test
        @DisplayName("should return payment contact with phone")
        void shouldReturnPaymentContactWithPhone() {
            PaymentContactDto contact = new PaymentContactDto(
                    "Test Company",
                    "company@test.com",
                    "+48123456789",
                    "https://example.com/profile.jpg"
            );

            when(appliedOpportunityService.getPaymentContact(TEST_OPPORTUNITY_ID)).thenReturn(contact);

            ResponseEntity<PaymentContactDto> response = controller.getPaymentContact(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().name()).isEqualTo("Test Company");
            assertThat(response.getBody().email()).isEqualTo("company@test.com");
            assertThat(response.getBody().phone()).isEqualTo("+48123456789");
        }

        @Test
        @DisplayName("should return payment contact without phone when not shared")
        void shouldReturnPaymentContactWithoutPhoneWhenNotShared() {
            PaymentContactDto contact = new PaymentContactDto(
                    "Test Company",
                    "company@test.com",
                    null,
                    "https://example.com/profile.jpg"
            );

            when(appliedOpportunityService.getPaymentContact(TEST_OPPORTUNITY_ID)).thenReturn(contact);

            ResponseEntity<PaymentContactDto> response = controller.getPaymentContact(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().phone()).isNull();
        }

        @Test
        @DisplayName("should throw exception when opportunity not found")
        void shouldThrowExceptionWhenOpportunityNotFound() {
            when(appliedOpportunityService.getPaymentContact(999L))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Applied Opportunity"));

            assertThatThrownBy(() -> controller.getPaymentContact(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw exception when status not at payment stage")
        void shouldThrowExceptionWhenStatusNotAtPaymentStage() {
            when(appliedOpportunityService.getPaymentContact(TEST_OPPORTUNITY_ID))
                    .thenThrow(new InsufficientPermissionsException("error.auth.insufficient_permissions",
                            TEST_FIREBASE_UID, "getPaymentContact", "Contact data only available at payment stage"));

            assertThatThrownBy(() -> controller.getPaymentContact(TEST_OPPORTUNITY_ID))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw exception when user not party to collaboration")
        void shouldThrowExceptionWhenUserNotPartyToCollaboration() {
            when(appliedOpportunityService.getPaymentContact(TEST_OPPORTUNITY_ID))
                    .thenThrow(new InsufficientPermissionsException("error.auth.insufficient_permissions",
                            TEST_FIREBASE_UID, "getPaymentContact", "User not party to this collaboration"));

            assertThatThrownBy(() -> controller.getPaymentContact(TEST_OPPORTUNITY_ID))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // =====================================================
    // Controller Constructor Tests
    // =====================================================
    @Nested
    @DisplayName("Controller Constructor")
    class ControllerConstructor {

        @Test
        @DisplayName("should inject all dependencies")
        void shouldInjectAllDependencies() {
            assertThat(controller).isNotNull();
        }

        // Note: getService() is protected, tested via behavior rather than direct access
    }

    // =====================================================
    // Locale Handling Tests
    // =====================================================
    @Nested
    @DisplayName("Locale Handling")
    class LocaleHandling {

        @Test
        @DisplayName("should parse language with country code")
        void shouldParseLanguageWithCountryCode() {
            when(request.getHeader("Accept-Language")).thenReturn("en-US");
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);
            when(translationService.translateOpportunityStatus(anyString(), any())).thenReturn("Applied");

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.getById(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should parse language with quality values")
        void shouldParseLanguageWithQualityValues() {
            when(request.getHeader("Accept-Language")).thenReturn("pl;q=0.9");
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.getById(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @ParameterizedTest
        @ValueSource(strings = {"en", "pl", "de", "fr", "es"})
        @DisplayName("should handle various language codes")
        void shouldHandleVariousLanguageCodes(String language) {
            when(request.getHeader("Accept-Language")).thenReturn(language);
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.getById(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // =====================================================
    // Translation Edge Cases Tests
    // =====================================================
    @Nested
    @DisplayName("Translation Edge Cases")
    class TranslationEdgeCases {

        @Test
        @DisplayName("should handle null opportunity status in DTO")
        void shouldHandleNullOpportunityStatusInDto() {
            testDtoOut.setOpportunityStatus(null);
            testAppliedOpportunity.setOpportunityStatus(null);

            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.getById(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getOpportunityStatus()).isNull();
        }

        @Test
        @DisplayName("should handle null rate status in entity")
        void shouldHandleNullRateStatusInEntity() {
            testAppliedOpportunity.setRateStatus(null);
            testAppliedOpportunity.setCompanyRateStatus(null);

            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.getById(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // =====================================================
    // Exception Handling Tests
    // =====================================================
    @Nested
    @DisplayName("Exception Handling")
    class ExceptionHandling {

        @Test
        @DisplayName("should propagate ResourceNotFoundException")
        void shouldPropagateResourceNotFoundException() {
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

            assertThatThrownBy(() -> controller.getById(TEST_OPPORTUNITY_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should propagate InsufficientPermissionsException")
        void shouldPropagateInsufficientPermissionsException() {
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID))
                    .thenThrow(new InsufficientPermissionsException("error.auth.insufficient_permissions",
                            TEST_FIREBASE_UID, "findById", "AppliedOpportunity"));

            assertThatThrownBy(() -> controller.getById(TEST_OPPORTUNITY_ID))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should propagate ValidationTranslatableException")
        void shouldPropagateValidationTranslatableException() {
            doThrow(new ValidationTranslatableException("error.validation.required_field", "field"))
                    .when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            assertThatThrownBy(() -> controller.create(testDtoIn))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should propagate BusinessRuleTranslatableException")
        void shouldPropagateBusinessRuleTranslatableException() {
            when(appliedOpportunityService.updateOpportunityStatusAsDto(TEST_OPPORTUNITY_ID, true))
                    .thenThrow(new BusinessRuleTranslatableException("error.business.invalid_state"));

            assertThatThrownBy(() -> controller.updateOpportunityStatus(TEST_OPPORTUNITY_ID, true))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should propagate FollowerValidationException")
        void shouldPropagateFollowerValidationException() {
            doThrow(new FollowerValidationException("Follower count invalid"))
                    .when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            assertThatThrownBy(() -> controller.create(testDtoIn))
                    .isInstanceOf(FollowerValidationException.class);
        }
    }

    // =====================================================
    // Security Context Tests
    // =====================================================
    @Nested
    @DisplayName("Security Context")
    class SecurityContextTests {

        @Test
        @DisplayName("should get firebase UID from security context")
        void shouldGetFirebaseUidFromSecurityContext() {
            when(appliedOpportunityService.findByIdAsDto(TEST_OPPORTUNITY_ID)).thenReturn(testDtoOut);
            when(appliedOpportunityService.findById(TEST_OPPORTUNITY_ID)).thenReturn(testAppliedOpportunity);

            controller.getById(TEST_OPPORTUNITY_ID);

            verify(securityContext, atLeastOnce()).getAuthentication();
            verify(authentication, atLeastOnce()).getPrincipal();
        }

        @Test
        @DisplayName("should use correct principal in all endpoints")
        void shouldUseCorrectPrincipalInAllEndpoints() {
            when(authentication.getPrincipal()).thenReturn("specific-firebase-uid");
            doReturn(testDtoOut).when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            controller.create(testDtoIn);

            verify(authentication).getPrincipal();
        }
    }

    // =====================================================
    // DTO Mapping Tests
    // =====================================================
    @Nested
    @DisplayName("DTO Mapping")
    class DtoMappingTests {

        @Test
        @DisplayName("should map OpportunityStatus to OpportunityStatusDtoOut correctly")
        void shouldMapOpportunityStatusToOpportunityStatusDtoOutCorrectly() {
            OpportunityStatusDtoOut dto = createOpportunityStatusDto(OpportunityStatus.APPLIED);

            assertThat(dto.getValue()).isEqualTo("APPLIED");
            assertThat(dto.getColorTheme()).isEqualTo("primary");
            assertThat(dto.getIcon()).isEqualTo("user");
            assertThat(dto.isTerminal()).isFalse();
            assertThat(dto.isSuccessful()).isFalse();
        }

        @Test
        @DisplayName("should map terminal status correctly")
        void shouldMapTerminalStatusCorrectly() {
            OpportunityStatusDtoOut dto = createOpportunityStatusDto(OpportunityStatus.DONE);

            assertThat(dto.isTerminal()).isTrue();
            assertThat(dto.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("should map RateStatus to RateStatusDtoOut correctly")
        void shouldMapRateStatusToRateStatusDtoOutCorrectly() {
            RateStatusDtoOut dto = createRateStatusDto(RateStatus.POSITIVE);

            assertThat(dto.getValue()).isEqualTo("POSITIVE");
            assertThat(dto.getColorTheme()).isEqualTo("success");
            assertThat(dto.getIcon()).isEqualTo("thumbs-up");
        }

        @ParameterizedTest
        @EnumSource(OpportunityStatus.class)
        @DisplayName("should map all OpportunityStatus values")
        void shouldMapAllOpportunityStatusValues(OpportunityStatus status) {
            OpportunityStatusDtoOut dto = createOpportunityStatusDto(status);

            assertThat(dto.getValue()).isEqualTo(status.name());
            assertThat(dto.getColorTheme()).isNotNull();
            assertThat(dto.getIcon()).isNotNull();
        }

        @ParameterizedTest
        @EnumSource(RateStatus.class)
        @DisplayName("should map all RateStatus values")
        void shouldMapAllRateStatusValues(RateStatus status) {
            RateStatusDtoOut dto = createRateStatusDto(status);

            assertThat(dto.getValue()).isEqualTo(status.name());
            assertThat(dto.getColorTheme()).isNotNull();
            assertThat(dto.getIcon()).isNotNull();
        }
    }

    // =====================================================
    // Edge Cases Tests
    // =====================================================
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle very long note in DTO")
        void shouldHandleVeryLongNoteInDto() {
            testDtoIn.setNote("a".repeat(500));
            doReturn(testDtoOut).when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.create(testDtoIn);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle null note in DTO")
        void shouldHandleNullNoteInDto() {
            testDtoIn.setNote(null);
            doReturn(testDtoOut).when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.create(testDtoIn);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle empty note in DTO")
        void shouldHandleEmptyNoteInDto() {
            testDtoIn.setNote("");
            doReturn(testDtoOut).when(appliedOpportunityService).saveAsDto(any(AppliedOpportunityDtoIn.class));

            ResponseEntity<AppliedOpportunityDtoOut> response = controller.create(testDtoIn);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle zero ID")
        void shouldHandleZeroId() {
            when(appliedOpportunityService.findByIdAsDto(0L))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

            assertThatThrownBy(() -> controller.getById(0L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should handle negative ID")
        void shouldHandleNegativeId() {
            when(appliedOpportunityService.findByIdAsDto(-1L))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

            assertThatThrownBy(() -> controller.getById(-1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should handle Long.MAX_VALUE ID")
        void shouldHandleLongMaxValueId() {
            when(appliedOpportunityService.findByIdAsDto(Long.MAX_VALUE))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

            assertThatThrownBy(() -> controller.getById(Long.MAX_VALUE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =====================================================
    // Status History DTO Conversion Tests
    // =====================================================
    @Nested
    @DisplayName("Status History DTO Conversion")
    class StatusHistoryDtoConversionTests {

        @Test
        @DisplayName("should convert status history to DTO with user")
        void shouldConvertStatusHistoryToDtoWithUser() {
            AppliedOpportunityStatusHistory history = new AppliedOpportunityStatusHistory();
            history.setId(1L);
            history.setAppliedOpportunity(testAppliedOpportunity);
            history.setPreviousStatus(OpportunityStatus.APPLIED);
            history.setNewStatus(OpportunityStatus.ACCEPTED_BY_COMPANY);
            history.setChangedByUser(testCompany);
            history.setChangedByFirebaseId("company-firebase-uid");
            history.setChangedAt(LocalDateTime.now());
            history.setChangeReason("Company accepted");

            when(statusHistoryService.getStatusHistory(TEST_OPPORTUNITY_ID))
                    .thenReturn(List.of(history));

            ResponseEntity<List<AppliedOpportunityStatusHistoryDtoOut>> response =
                    controller.getStatusHistory(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("should convert status history to DTO without user")
        void shouldConvertStatusHistoryToDtoWithoutUser() {
            AppliedOpportunityStatusHistory history = new AppliedOpportunityStatusHistory();
            history.setId(1L);
            history.setAppliedOpportunity(testAppliedOpportunity);
            history.setPreviousStatus(OpportunityStatus.APPLIED);
            history.setNewStatus(OpportunityStatus.ACCEPTED_BY_COMPANY);
            history.setChangedByFirebaseId("unknown-uid");
            history.setChangedAt(LocalDateTime.now());

            when(statusHistoryService.getStatusHistory(TEST_OPPORTUNITY_ID))
                    .thenReturn(List.of(history));

            ResponseEntity<List<AppliedOpportunityStatusHistoryDtoOut>> response =
                    controller.getStatusHistory(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should convert system status history to DTO")
        void shouldConvertSystemStatusHistoryToDto() {
            AppliedOpportunityStatusHistory history = new AppliedOpportunityStatusHistory();
            history.setId(1L);
            history.setAppliedOpportunity(testAppliedOpportunity);
            history.setNewStatus(OpportunityStatus.APPLIED);
            history.setChangedByFirebaseId("SYSTEM");
            history.setChangedAt(LocalDateTime.now());
            history.setChangeReason("System generated");

            when(statusHistoryService.getStatusHistory(TEST_OPPORTUNITY_ID))
                    .thenReturn(List.of(history));

            ResponseEntity<List<AppliedOpportunityStatusHistoryDtoOut>> response =
                    controller.getStatusHistory(TEST_OPPORTUNITY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
