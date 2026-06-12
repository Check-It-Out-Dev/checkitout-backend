package com.sm.instagram.platform.unit.controller;

import com.sm.instagram.platform.appliedopportunities.*;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.contenttype.ContentType;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 * Unit tests for AppliedOpportunityContentController, AppliedOpportunityContentMapping,
 * and AppliedOpportunityContentSpecificationBuilder.
 * Uses pure Mockito - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppliedOpportunityContent Controller, Mapping, and SpecificationBuilder Unit Tests")
class AppliedOpportunityContentControllerUnitTest {

    @Mock
    private AppliedOpportunityContentService contentService;

    @Mock
    private AppliedOpportunityContentMapping contentMapping;

    @Mock
    private PermissionUtils permissionUtils;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AppliedOpportunityContentController controller;

    // Test fixtures
    private User testInfluencer;
    private User testCompany;
    private AppliedOpportunity testAppliedOpportunity;
    private AppliedOpportunityContent testContent;
    private ContentType testContentType;
    private PartnershipOpportunity testPartnershipOpportunity;
    private AppliedOpportunityContentDtoIn testDtoIn;
    private AppliedOpportunityContentDtoOut testDtoOut;
    private AppliedOpportunityContentSimpleDtoOut testSimpleDtoOut;

    @BeforeEach
    void setUp() {
        // Set up security context
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("test-firebase-uid");
        when(authentication.getName()).thenReturn("test-firebase-uid");

        // Create test influencer
        testInfluencer = new User();
        testInfluencer.setId(1L);
        testInfluencer.setFirebaseUserId("influencer-firebase-uid");
        testInfluencer.setUserType(UserType.INFLUENCER);

        // Create test company
        testCompany = new User();
        testCompany.setId(2L);
        testCompany.setFirebaseUserId("company-firebase-uid");
        testCompany.setUserType(UserType.COMPANY);

        // Create test partnership opportunity
        testPartnershipOpportunity = new PartnershipOpportunity();
        testPartnershipOpportunity.setId(100L);
        testPartnershipOpportunity.setCompany(testCompany);

        // Create test applied opportunity
        testAppliedOpportunity = new AppliedOpportunity();
        testAppliedOpportunity.setId(10L);
        testAppliedOpportunity.setInfluencer(testInfluencer);
        testAppliedOpportunity.setPartnershipOpportunity(testPartnershipOpportunity);
        testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.ACCEPTED_BY_INFLUENCER);

        // Create test content type
        testContentType = new ContentType();
        testContentType.setId(1L);
        testContentType.setName("Instagram Reel");

        // Create test content entity
        testContent = new AppliedOpportunityContent();
        testContent.setId(1L);
        testContent.setAppliedOpportunity(testAppliedOpportunity);
        testContent.setContentType(testContentType);
        testContent.setApprovalStatus(ContentApprovalStatus.PENDING);
        testContent.setDescription("Test content description");
        testContent.setUrls(List.of("https://example.com/content1"));
        testContent.setContentCount(1);
        testContent.setTags("test,content");
        testContent.setSocialMediaLink("https://instagram.com/p/abc123");
        testContent.setLikesCount(100L);
        testContent.setCommentsCount(10L);
        testContent.setViewsCount(1000L);
        testContent.setSharesCount(5L);
        testContent.setCreatedTime(LocalDateTime.now().minusDays(1));
        testContent.setLastUpdateTime(LocalDateTime.now());
        testContent.setUpdaterId("test-updater");

        // Create test DTO input
        testDtoIn = new AppliedOpportunityContentDtoIn();
        testDtoIn.setAppliedOpportunityId(10L);
        testDtoIn.setContentTypeId(1L);
        testDtoIn.setDescription("New content description");
        testDtoIn.setUrls(List.of("https://example.com/content"));
        testDtoIn.setContentCount(1);
        testDtoIn.setTags("new,tags");
        testDtoIn.setSocialMediaLink("https://instagram.com/p/xyz789");

        // Create test DTO output
        testDtoOut = new AppliedOpportunityContentDtoOut();
        testDtoOut.setId(1L);
        testDtoOut.setAppliedOpportunityId(10L);
        testDtoOut.setContentTypeId(1L);
        testDtoOut.setContentTypeName("Instagram Reel");
        testDtoOut.setContentCount(1);
        testDtoOut.setUrls(List.of("https://example.com/content1"));
        testDtoOut.setDescription("Test content description");
        testDtoOut.setTags("test,content");
        testDtoOut.setApprovalStatus(ContentApprovalStatus.PENDING);
        testDtoOut.setLikesCount(100L);
        testDtoOut.setCommentsCount(10L);
        testDtoOut.setViewsCount(1000L);
        testDtoOut.setSharesCount(5L);
        testDtoOut.setSocialMediaLink("https://instagram.com/p/abc123");
        testDtoOut.setCreatedTime(LocalDateTime.now().minusDays(1));
        testDtoOut.setLastUpdateTime(LocalDateTime.now());

        // Create test simple DTO output
        testSimpleDtoOut = new AppliedOpportunityContentSimpleDtoOut();
        testSimpleDtoOut.setId(1L);
        testSimpleDtoOut.setContentTypeName("Instagram Reel");
        testSimpleDtoOut.setContentCount(1);
        testSimpleDtoOut.setUrls(List.of("https://example.com/content1"));
        testSimpleDtoOut.setApprovalStatus(ContentApprovalStatus.PENDING);
        testSimpleDtoOut.setLikesCount(100L);
        testSimpleDtoOut.setViewsCount(1000L);
    }

    // ==================== CONTROLLER TESTS ====================

    @Nested
    @DisplayName("POST /applied-opportunity/content - submitContent")
    class SubmitContent {

        @Test
        @DisplayName("should submit content successfully and return 201 Created")
        void shouldSubmitContentSuccessfully() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("test-firebase-uid");
            when(contentService.createContentSubmission(any(AppliedOpportunityContentDtoIn.class), anyString()))
                    .thenReturn(testContent);
            when(contentMapping.toDto(testContent)).thenReturn(testDtoOut);

            // When
            ResponseEntity<AppliedOpportunityContentDtoOut> response = controller.submitContent(testDtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(1L);
            verify(contentService).createContentSubmission(eq(testDtoIn), eq("test-firebase-uid"));
            verify(contentMapping).toDto(testContent);
        }

        @Test
        @DisplayName("should call service with correct updaterId from permissionUtils")
        void shouldCallServiceWithCorrectUpdaterId() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("specific-user-uid");
            when(contentService.createContentSubmission(any(), anyString())).thenReturn(testContent);
            when(contentMapping.toDto(any())).thenReturn(testDtoOut);

            // When
            controller.submitContent(testDtoIn);

            // Then
            verify(contentService).createContentSubmission(any(), eq("specific-user-uid"));
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException when applied opportunity not found")
        void shouldPropagateResourceNotFoundExceptionForOpportunity() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("test-uid");
            when(contentService.createContentSubmission(any(), anyString()))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

            // When/Then
            assertThatThrownBy(() -> controller.submitContent(testDtoIn))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should propagate InsufficientPermissionsException when user cannot submit")
        void shouldPropagateInsufficientPermissionsException() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("unauthorized-uid");
            when(contentService.createContentSubmission(any(), anyString()))
                    .thenThrow(new InsufficientPermissionsException(
                            "error.auth.insufficient_permissions",
                            "unauthorized-uid",
                            "createContentSubmission",
                            "AppliedOpportunityContent"));

            // When/Then
            assertThatThrownBy(() -> controller.submitContent(testDtoIn))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should correctly pass DTO to service")
        void shouldCorrectlyPassDtoToService() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("test-uid");
            when(contentService.createContentSubmission(any(), anyString())).thenReturn(testContent);
            when(contentMapping.toDto(any())).thenReturn(testDtoOut);

            // When
            controller.submitContent(testDtoIn);

            // Then
            verify(contentService).createContentSubmission(argThat(dto ->
                dto.getAppliedOpportunityId().equals(10L) &&
                dto.getContentTypeId().equals(1L) &&
                dto.getDescription().equals("New content description")
            ), anyString());
        }
    }

    @Nested
    @DisplayName("PUT /applied-opportunity/content/{contentId} - updateContent")
    class UpdateContent {

        @Test
        @DisplayName("should update content successfully and return 200 OK")
        void shouldUpdateContentSuccessfully() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("test-firebase-uid");
            when(contentService.updateContentSubmission(eq(1L), any(AppliedOpportunityContentDtoIn.class), anyString()))
                    .thenReturn(testContent);
            when(contentMapping.toDto(testContent)).thenReturn(testDtoOut);

            // When
            ResponseEntity<AppliedOpportunityContentDtoOut> response = controller.updateContent(1L, testDtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(1L);
            verify(contentService).updateContentSubmission(eq(1L), eq(testDtoIn), eq("test-firebase-uid"));
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException when content not found")
        void shouldPropagateResourceNotFoundExceptionForContent() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("test-uid");
            when(contentService.updateContentSubmission(eq(999L), any(), anyString()))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", 999L));

            // When/Then
            assertThatThrownBy(() -> controller.updateContent(999L, testDtoIn))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should propagate InsufficientPermissionsException when user cannot update")
        void shouldPropagateInsufficientPermissionsExceptionForUpdate() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("unauthorized-uid");
            when(contentService.updateContentSubmission(eq(1L), any(), anyString()))
                    .thenThrow(new InsufficientPermissionsException(
                            "error.auth.insufficient_permissions",
                            "unauthorized-uid",
                            "updateContentSubmission",
                            "AppliedOpportunityContent#1"));

            // When/Then
            assertThatThrownBy(() -> controller.updateContent(1L, testDtoIn))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 5L, 100L, 9999L})
        @DisplayName("should correctly pass contentId to service")
        void shouldCorrectlyPassContentIdToService(Long contentId) {
            // Given
            when(permissionUtils.getUserId()).thenReturn("test-uid");
            when(contentService.updateContentSubmission(eq(contentId), any(), anyString())).thenReturn(testContent);
            when(contentMapping.toDto(any())).thenReturn(testDtoOut);

            // When
            controller.updateContent(contentId, testDtoIn);

            // Then
            verify(contentService).updateContentSubmission(eq(contentId), any(), anyString());
        }
    }

    @Nested
    @DisplayName("GET /applied-opportunity/content/{contentId} - getContent")
    class GetContent {

        @Test
        @DisplayName("should return content successfully with 200 OK")
        void shouldReturnContentSuccessfully() {
            // Given
            when(contentService.getContentById(1L)).thenReturn(testContent);
            when(contentMapping.toDto(testContent)).thenReturn(testDtoOut);

            // When
            ResponseEntity<AppliedOpportunityContentDtoOut> response = controller.getContent(1L);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(1L);
            assertThat(response.getBody().getDescription()).isEqualTo("Test content description");
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException when content not found")
        void shouldPropagateResourceNotFoundException() {
            // Given
            when(contentService.getContentById(999L))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", 999L));

            // When/Then
            assertThatThrownBy(() -> controller.getContent(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should propagate InsufficientPermissionsException when user cannot view")
        void shouldPropagateInsufficientPermissionsException() {
            // Given
            when(contentService.getContentById(1L))
                    .thenThrow(new InsufficientPermissionsException(
                            "error.auth.insufficient_permissions",
                            "user-uid",
                            "getContentById",
                            "AppliedOpportunityContent#1"));

            // When/Then
            assertThatThrownBy(() -> controller.getContent(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 10L, 100L, 500L})
        @DisplayName("should correctly pass contentId to service for various IDs")
        void shouldCorrectlyPassContentIdToService(Long contentId) {
            // Given
            testContent.setId(contentId);
            testDtoOut.setId(contentId);
            when(contentService.getContentById(contentId)).thenReturn(testContent);
            when(contentMapping.toDto(testContent)).thenReturn(testDtoOut);

            // When
            ResponseEntity<AppliedOpportunityContentDtoOut> response = controller.getContent(contentId);

            // Then
            assertThat(response.getBody().getId()).isEqualTo(contentId);
            verify(contentService).getContentById(contentId);
        }
    }

    @Nested
    @DisplayName("GET /applied-opportunity/content/applied-opportunity/{appliedOpportunityId} - getContentByAppliedOpportunity")
    class GetContentByAppliedOpportunity {

        @Test
        @DisplayName("should return content list without status filter")
        void shouldReturnContentListWithoutStatusFilter() {
            // Given
            List<AppliedOpportunityContent> contentList = List.of(testContent);
            when(contentService.getContentByAppliedOpportunity(10L)).thenReturn(contentList);
            when(contentMapping.toDto(testContent)).thenReturn(testDtoOut);

            // When
            ResponseEntity<List<AppliedOpportunityContentDtoOut>> response =
                    controller.getContentByAppliedOpportunity(10L, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getId()).isEqualTo(1L);
            verify(contentService).getContentByAppliedOpportunity(10L);
            verify(contentService, never()).getContentByAppliedOpportunityAndStatus(anyLong(), any());
        }

        @Test
        @DisplayName("should return content list with status filter")
        void shouldReturnContentListWithStatusFilter() {
            // Given
            List<AppliedOpportunityContent> contentList = List.of(testContent);
            when(contentService.getContentByAppliedOpportunityAndStatus(10L, ContentApprovalStatus.PENDING))
                    .thenReturn(contentList);
            when(contentMapping.toDto(testContent)).thenReturn(testDtoOut);

            // When
            ResponseEntity<List<AppliedOpportunityContentDtoOut>> response =
                    controller.getContentByAppliedOpportunity(10L, ContentApprovalStatus.PENDING);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            verify(contentService).getContentByAppliedOpportunityAndStatus(10L, ContentApprovalStatus.PENDING);
            verify(contentService, never()).getContentByAppliedOpportunity(anyLong());
        }

        @ParameterizedTest
        @EnumSource(ContentApprovalStatus.class)
        @DisplayName("should correctly filter by any content approval status")
        void shouldCorrectlyFilterByAnyContentApprovalStatus(ContentApprovalStatus status) {
            // Given
            when(contentService.getContentByAppliedOpportunityAndStatus(10L, status))
                    .thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<AppliedOpportunityContentDtoOut>> response =
                    controller.getContentByAppliedOpportunity(10L, status);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(contentService).getContentByAppliedOpportunityAndStatus(10L, status);
        }

        @Test
        @DisplayName("should return empty list when no content exists")
        void shouldReturnEmptyListWhenNoContentExists() {
            // Given
            when(contentService.getContentByAppliedOpportunity(10L)).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<AppliedOpportunityContentDtoOut>> response =
                    controller.getContentByAppliedOpportunity(10L, null);

            // Then
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException when opportunity not found")
        void shouldPropagateResourceNotFoundException() {
            // Given
            when(contentService.getContentByAppliedOpportunity(999L))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "opportunity"));

            // When/Then
            assertThatThrownBy(() -> controller.getContentByAppliedOpportunity(999L, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("GET /applied-opportunity/content/applied-opportunity/{appliedOpportunityId}/paged - getContentByAppliedOpportunityPaged")
    class GetContentByAppliedOpportunityPaged {

        @Test
        @DisplayName("should return paginated content successfully")
        void shouldReturnPaginatedContentSuccessfully() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Map<String, String> filters = new HashMap<>();
            Page<AppliedOpportunityContent> contentPage = new PageImpl<>(List.of(testContent), pageable, 1);

            when(contentService.getContentByAppliedOpportunityPaged(10L, pageable, filters))
                    .thenReturn(contentPage);
            when(contentMapping.toDto(testContent)).thenReturn(testDtoOut);

            // When
            ResponseEntity<Page<AppliedOpportunityContentDtoOut>> response =
                    controller.getContentByAppliedOpportunityPaged(10L, pageable, filters);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).hasSize(1);
            assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return empty page when no content exists")
        void shouldReturnEmptyPageWhenNoContentExists() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Map<String, String> filters = new HashMap<>();
            Page<AppliedOpportunityContent> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            when(contentService.getContentByAppliedOpportunityPaged(10L, pageable, filters))
                    .thenReturn(emptyPage);

            // When
            ResponseEntity<Page<AppliedOpportunityContentDtoOut>> response =
                    controller.getContentByAppliedOpportunityPaged(10L, pageable, filters);

            // Then
            assertThat(response.getBody().getContent()).isEmpty();
            assertThat(response.getBody().getTotalElements()).isZero();
        }

        @Test
        @DisplayName("should propagate InsufficientPermissionsException when user cannot view")
        void shouldPropagateInsufficientPermissionsException() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Map<String, String> filters = new HashMap<>();

            when(contentService.getContentByAppliedOpportunityPaged(10L, pageable, filters))
                    .thenThrow(new InsufficientPermissionsException(
                            "error.auth.insufficient_permissions",
                            "user-uid",
                            "getContentByAppliedOpportunityPaged",
                            "AppliedOpportunity#10"));

            // When/Then
            assertThatThrownBy(() -> controller.getContentByAppliedOpportunityPaged(10L, pageable, filters))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should correctly pass filters to service")
        void shouldCorrectlyPassFiltersToService() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Map<String, String> filters = new HashMap<>();
            filters.put("approvalStatus", "PENDING");
            filters.put("contentType", "Instagram Reel");

            Page<AppliedOpportunityContent> contentPage = new PageImpl<>(List.of(testContent), pageable, 1);
            when(contentService.getContentByAppliedOpportunityPaged(eq(10L), eq(pageable), eq(filters)))
                    .thenReturn(contentPage);
            when(contentMapping.toDto(any())).thenReturn(testDtoOut);

            // When
            controller.getContentByAppliedOpportunityPaged(10L, pageable, filters);

            // Then
            verify(contentService).getContentByAppliedOpportunityPaged(10L, pageable, filters);
        }
    }

    @Nested
    @DisplayName("GET /applied-opportunity/content/pending-approval - getPendingContent")
    class GetPendingContent {

        @Test
        @DisplayName("should return pending content for admin/company")
        void shouldReturnPendingContent() {
            // Given
            List<AppliedOpportunityContent> contentList = List.of(testContent);
            when(contentService.getContentByApprovalStatus(ContentApprovalStatus.PENDING))
                    .thenReturn(contentList);
            when(contentMapping.toDto(testContent)).thenReturn(testDtoOut);

            // When
            ResponseEntity<List<AppliedOpportunityContentDtoOut>> response = controller.getPendingContent();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            verify(contentService).getContentByApprovalStatus(ContentApprovalStatus.PENDING);
        }

        @Test
        @DisplayName("should return empty list when no pending content")
        void shouldReturnEmptyListWhenNoPendingContent() {
            // Given
            when(contentService.getContentByApprovalStatus(ContentApprovalStatus.PENDING))
                    .thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<AppliedOpportunityContentDtoOut>> response = controller.getPendingContent();

            // Then
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /applied-opportunity/content/status/{status} - getContentByStatus")
    class GetContentByStatus {

        @ParameterizedTest
        @EnumSource(ContentApprovalStatus.class)
        @DisplayName("should return content for any status")
        void shouldReturnContentForAnyStatus(ContentApprovalStatus status) {
            // Given
            testContent.setApprovalStatus(status);
            when(contentService.getContentByApprovalStatus(status)).thenReturn(List.of(testContent));
            when(contentMapping.toDto(testContent)).thenReturn(testDtoOut);

            // When
            ResponseEntity<List<AppliedOpportunityContentDtoOut>> response = controller.getContentByStatus(status);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(contentService).getContentByApprovalStatus(status);
        }

        @Test
        @DisplayName("should return empty list for status with no content")
        void shouldReturnEmptyListForStatusWithNoContent() {
            // Given
            when(contentService.getContentByApprovalStatus(ContentApprovalStatus.APPROVED))
                    .thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<AppliedOpportunityContentDtoOut>> response =
                    controller.getContentByStatus(ContentApprovalStatus.APPROVED);

            // Then
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("PATCH /applied-opportunity/content/{contentId}/approve - approveContent")
    class ApproveContent {

        @Test
        @DisplayName("should approve content successfully with approval notes")
        void shouldApproveContentSuccessfullyWithNotes() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            doNothing().when(contentService).approveContent(eq(1L), eq("Great content!"), eq("admin-uid"));

            // When
            ResponseEntity<Void> response = controller.approveContent(1L, "Great content!");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(contentService).approveContent(1L, "Great content!", "admin-uid");
        }

        @Test
        @DisplayName("should approve content successfully without approval notes")
        void shouldApproveContentSuccessfullyWithoutNotes() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("company-uid");
            doNothing().when(contentService).approveContent(eq(1L), isNull(), eq("company-uid"));

            // When
            ResponseEntity<Void> response = controller.approveContent(1L, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(contentService).approveContent(1L, null, "company-uid");
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException when content not found")
        void shouldPropagateResourceNotFoundException() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            doThrow(new ResourceNotFoundException("error.business.item_not_found", 999L))
                    .when(contentService).approveContent(eq(999L), anyString(), anyString());

            // When/Then
            assertThatThrownBy(() -> controller.approveContent(999L, "Notes"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should propagate InsufficientPermissionsException when user cannot approve")
        void shouldPropagateInsufficientPermissionsException() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("unauthorized-uid");
            doThrow(new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    "unauthorized-uid",
                    "approveContent",
                    "AppliedOpportunityContent#1"))
                    .when(contentService).approveContent(eq(1L), anyString(), anyString());

            // When/Then
            assertThatThrownBy(() -> controller.approveContent(1L, "Notes"))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("PATCH /applied-opportunity/content/{contentId}/reject - rejectContent")
    class RejectContent {

        @Test
        @DisplayName("should reject content successfully with approval notes")
        void shouldRejectContentSuccessfullyWithNotes() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            doNothing().when(contentService).rejectContent(eq(1L), eq("Needs improvement"), eq("admin-uid"));

            // When
            ResponseEntity<Void> response = controller.rejectContent(1L, "Needs improvement");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(contentService).rejectContent(1L, "Needs improvement", "admin-uid");
        }

        @Test
        @DisplayName("should reject content successfully without approval notes")
        void shouldRejectContentSuccessfullyWithoutNotes() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("company-uid");
            doNothing().when(contentService).rejectContent(eq(1L), isNull(), eq("company-uid"));

            // When
            ResponseEntity<Void> response = controller.rejectContent(1L, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(contentService).rejectContent(1L, null, "company-uid");
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException when content not found")
        void shouldPropagateResourceNotFoundException() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            doThrow(new ResourceNotFoundException("error.business.item_not_found", 999L))
                    .when(contentService).rejectContent(eq(999L), anyString(), anyString());

            // When/Then
            assertThatThrownBy(() -> controller.rejectContent(999L, "Notes"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should propagate InsufficientPermissionsException when user cannot reject")
        void shouldPropagateInsufficientPermissionsException() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("unauthorized-uid");
            doThrow(new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    "unauthorized-uid",
                    "rejectContent",
                    "AppliedOpportunityContent#1"))
                    .when(contentService).rejectContent(eq(1L), anyString(), anyString());

            // When/Then
            assertThatThrownBy(() -> controller.rejectContent(1L, "Notes"))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("PATCH /applied-opportunity/content/{contentId}/engagement - updateEngagementMetrics")
    class UpdateEngagementMetrics {

        @Test
        @DisplayName("should update all engagement metrics successfully")
        void shouldUpdateAllEngagementMetricsSuccessfully() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("influencer-uid");
            doNothing().when(contentService).updateEngagementMetrics(
                    eq(1L), eq(1000L), eq(50L), eq(5000L), eq(100L), eq("influencer-uid"));

            // When
            ResponseEntity<Void> response = controller.updateEngagementMetrics(1L, 1000L, 50L, 5000L, 100L);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(contentService).updateEngagementMetrics(1L, 1000L, 50L, 5000L, 100L, "influencer-uid");
        }

        @Test
        @DisplayName("should update partial engagement metrics when some are null")
        void shouldUpdatePartialEngagementMetrics() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("influencer-uid");
            doNothing().when(contentService).updateEngagementMetrics(
                    eq(1L), eq(500L), isNull(), isNull(), isNull(), eq("influencer-uid"));

            // When
            ResponseEntity<Void> response = controller.updateEngagementMetrics(1L, 500L, null, null, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(contentService).updateEngagementMetrics(1L, 500L, null, null, null, "influencer-uid");
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException when content not found")
        void shouldPropagateResourceNotFoundException() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("uid");
            doThrow(new ResourceNotFoundException("error.business.item_not_found", 999L))
                    .when(contentService).updateEngagementMetrics(eq(999L), any(), any(), any(), any(), anyString());

            // When/Then
            assertThatThrownBy(() -> controller.updateEngagementMetrics(999L, 100L, 10L, 1000L, 50L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should propagate InsufficientPermissionsException when user cannot update metrics")
        void shouldPropagateInsufficientPermissionsException() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("unauthorized-uid");
            doThrow(new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    "unauthorized-uid",
                    "updateEngagementMetrics",
                    "AppliedOpportunityContent#1"))
                    .when(contentService).updateEngagementMetrics(eq(1L), any(), any(), any(), any(), anyString());

            // When/Then
            assertThatThrownBy(() -> controller.updateEngagementMetrics(1L, 100L, 10L, 1000L, 50L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, 1L, 100L, 999999L})
        @DisplayName("should accept various metric values")
        void shouldAcceptVariousMetricValues(Long metricValue) {
            // Given
            when(permissionUtils.getUserId()).thenReturn("uid");
            doNothing().when(contentService).updateEngagementMetrics(any(), any(), any(), any(), any(), any());

            // When
            ResponseEntity<Void> response = controller.updateEngagementMetrics(
                    1L, metricValue, metricValue, metricValue, metricValue);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("DELETE /applied-opportunity/content/{contentId} - deleteContent")
    class DeleteContent {

        @Test
        @DisplayName("should delete content successfully and return 204 No Content")
        void shouldDeleteContentSuccessfully() {
            // Given
            doNothing().when(contentService).deleteContent(1L);

            // When
            ResponseEntity<Void> response = controller.deleteContent(1L);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(contentService).deleteContent(1L);
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException when content not found")
        void shouldPropagateResourceNotFoundException() {
            // Given
            doThrow(new ResourceNotFoundException("error.business.item_not_found", 999L))
                    .when(contentService).deleteContent(999L);

            // When/Then
            assertThatThrownBy(() -> controller.deleteContent(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should propagate InsufficientPermissionsException when user cannot delete")
        void shouldPropagateInsufficientPermissionsException() {
            // Given
            doThrow(new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    "unauthorized-uid",
                    "deleteContent",
                    "AppliedOpportunityContent#1"))
                    .when(contentService).deleteContent(1L);

            // When/Then
            assertThatThrownBy(() -> controller.deleteContent(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 10L, 100L, 9999L})
        @DisplayName("should correctly pass contentId to service for deletion")
        void shouldCorrectlyPassContentIdToServiceForDeletion(Long contentId) {
            // Given
            doNothing().when(contentService).deleteContent(contentId);

            // When
            controller.deleteContent(contentId);

            // Then
            verify(contentService).deleteContent(contentId);
        }
    }

    // ==================== MAPPING TESTS ====================

    @Nested
    @DisplayName("AppliedOpportunityContentMapping - toDto")
    class MappingToDto {

        private AppliedOpportunityContentMapping mappingInstance;

        @BeforeEach
        void setUpMapping() {
            mappingInstance = new AppliedOpportunityContentMapping();
        }

        @Test
        @DisplayName("should return null when content is null")
        void shouldReturnNullWhenContentIsNull() {
            // When
            AppliedOpportunityContentDtoOut result = mappingInstance.toDto(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should map all fields correctly")
        void shouldMapAllFieldsCorrectly() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            testContent.setCreatedTime(now.minusDays(1));
            testContent.setLastUpdateTime(now);
            testContent.setSubmissionDate(now.minusHours(2));
            testContent.setContentCreationDate(now.minusDays(3));
            testContent.setApprovalNotes("Looks good");
            testContent.setUpdaterId("updater-123");

            // When
            AppliedOpportunityContentDtoOut result = mappingInstance.toDto(testContent);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getAppliedOpportunityId()).isEqualTo(10L);
            assertThat(result.getContentTypeId()).isEqualTo(1L);
            assertThat(result.getContentTypeName()).isEqualTo("Instagram Reel");
            assertThat(result.getContentCount()).isEqualTo(1);
            assertThat(result.getUrls()).containsExactly("https://example.com/content1");
            assertThat(result.getDescription()).isEqualTo("Test content description");
            assertThat(result.getTags()).isEqualTo("test,content");
            assertThat(result.getSocialMediaLink()).isEqualTo("https://instagram.com/p/abc123");
            assertThat(result.getApprovalStatus()).isEqualTo(ContentApprovalStatus.PENDING);
            assertThat(result.getApprovalNotes()).isEqualTo("Looks good");
            assertThat(result.getLikesCount()).isEqualTo(100L);
            assertThat(result.getCommentsCount()).isEqualTo(10L);
            assertThat(result.getViewsCount()).isEqualTo(1000L);
            assertThat(result.getSharesCount()).isEqualTo(5L);
            assertThat(result.getCreatedTime()).isEqualTo(now.minusDays(1));
            assertThat(result.getLastUpdateTime()).isEqualTo(now);
            assertThat(result.getSubmissionDate()).isEqualTo(now.minusHours(2));
            assertThat(result.getContentCreationDate()).isEqualTo(now.minusDays(3));
            assertThat(result.getUpdaterId()).isEqualTo("updater-123");
        }

        @ParameterizedTest
        @EnumSource(ContentApprovalStatus.class)
        @DisplayName("should correctly map any approval status")
        void shouldCorrectlyMapAnyApprovalStatus(ContentApprovalStatus status) {
            // Given
            testContent.setApprovalStatus(status);

            // When
            AppliedOpportunityContentDtoOut result = mappingInstance.toDto(testContent);

            // Then
            assertThat(result.getApprovalStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("should handle null engagement metrics")
        void shouldHandleNullEngagementMetrics() {
            // Given
            testContent.setLikesCount(null);
            testContent.setCommentsCount(null);
            testContent.setViewsCount(null);
            testContent.setSharesCount(null);

            // When
            AppliedOpportunityContentDtoOut result = mappingInstance.toDto(testContent);

            // Then
            assertThat(result.getLikesCount()).isNull();
            assertThat(result.getCommentsCount()).isNull();
            assertThat(result.getViewsCount()).isNull();
            assertThat(result.getSharesCount()).isNull();
        }

        @Test
        @DisplayName("should handle null optional fields")
        void shouldHandleNullOptionalFields() {
            // Given
            testContent.setDescription(null);
            testContent.setTags(null);
            testContent.setSocialMediaLink(null);
            testContent.setApprovalNotes(null);
            testContent.setUrls(null);

            // When
            AppliedOpportunityContentDtoOut result = mappingInstance.toDto(testContent);

            // Then
            assertThat(result.getDescription()).isNull();
            assertThat(result.getTags()).isNull();
            assertThat(result.getSocialMediaLink()).isNull();
            assertThat(result.getApprovalNotes()).isNull();
            assertThat(result.getUrls()).isNull();
        }

        @Test
        @DisplayName("should handle empty URL list")
        void shouldHandleEmptyUrlList() {
            // Given
            testContent.setUrls(Collections.emptyList());

            // When
            AppliedOpportunityContentDtoOut result = mappingInstance.toDto(testContent);

            // Then
            assertThat(result.getUrls()).isEmpty();
        }

        @Test
        @DisplayName("should handle multiple URLs")
        void shouldHandleMultipleUrls() {
            // Given
            testContent.setUrls(List.of(
                    "https://example.com/1",
                    "https://example.com/2",
                    "https://example.com/3"
            ));

            // When
            AppliedOpportunityContentDtoOut result = mappingInstance.toDto(testContent);

            // Then
            assertThat(result.getUrls()).hasSize(3);
            assertThat(result.getUrls()).containsExactly(
                    "https://example.com/1",
                    "https://example.com/2",
                    "https://example.com/3"
            );
        }
    }

    @Nested
    @DisplayName("AppliedOpportunityContentMapping - toSimpleDto")
    class MappingToSimpleDto {

        private AppliedOpportunityContentMapping mappingInstance;

        @BeforeEach
        void setUpMapping() {
            mappingInstance = new AppliedOpportunityContentMapping();
        }

        @Test
        @DisplayName("should return null when content is null")
        void shouldReturnNullWhenContentIsNull() {
            // When
            AppliedOpportunityContentSimpleDtoOut result = mappingInstance.toSimpleDto(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should map essential fields correctly")
        void shouldMapEssentialFieldsCorrectly() {
            // Given
            LocalDateTime submissionDate = LocalDateTime.now().minusHours(2);
            testContent.setSubmissionDate(submissionDate);

            // When
            AppliedOpportunityContentSimpleDtoOut result = mappingInstance.toSimpleDto(testContent);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getContentTypeName()).isEqualTo("Instagram Reel");
            assertThat(result.getContentCount()).isEqualTo(1);
            assertThat(result.getUrls()).containsExactly("https://example.com/content1");
            assertThat(result.getApprovalStatus()).isEqualTo(ContentApprovalStatus.PENDING);
            assertThat(result.getSocialMediaLink()).isEqualTo("https://instagram.com/p/abc123");
            assertThat(result.getSubmissionDate()).isEqualTo(submissionDate);
            assertThat(result.getLikesCount()).isEqualTo(100L);
            assertThat(result.getViewsCount()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("should handle null content type gracefully")
        void shouldHandleNullContentTypeGracefully() {
            // Given
            testContent.setContentType(null);

            // When
            AppliedOpportunityContentSimpleDtoOut result = mappingInstance.toSimpleDto(testContent);

            // Then
            assertThat(result.getContentTypeName()).isNull();
        }

        @ParameterizedTest
        @EnumSource(ContentApprovalStatus.class)
        @DisplayName("should correctly map any approval status in simple dto")
        void shouldCorrectlyMapAnyApprovalStatusInSimpleDto(ContentApprovalStatus status) {
            // Given
            testContent.setApprovalStatus(status);

            // When
            AppliedOpportunityContentSimpleDtoOut result = mappingInstance.toSimpleDto(testContent);

            // Then
            assertThat(result.getApprovalStatus()).isEqualTo(status);
        }
    }

    @Nested
    @DisplayName("AppliedOpportunityContentMapping - fromDto")
    class MappingFromDto {

        private AppliedOpportunityContentMapping mappingInstance;

        @BeforeEach
        void setUpMapping() {
            mappingInstance = new AppliedOpportunityContentMapping();
        }

        @Test
        @DisplayName("should return null when dto is null")
        void shouldReturnNullWhenDtoIsNull() {
            // When
            AppliedOpportunityContent result = mappingInstance.fromDto(null, testAppliedOpportunity, testContentType);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should create entity with all fields from dto")
        void shouldCreateEntityWithAllFieldsFromDto() {
            // Given
            LocalDateTime creationDate = LocalDateTime.now().minusDays(2);
            LocalDateTime submissionDate = LocalDateTime.now().minusHours(1);
            testDtoIn.setContentCreationDate(creationDate);
            testDtoIn.setSubmissionDate(submissionDate);

            // When
            AppliedOpportunityContent result = mappingInstance.fromDto(testDtoIn, testAppliedOpportunity, testContentType);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAppliedOpportunity()).isEqualTo(testAppliedOpportunity);
            assertThat(result.getContentType()).isEqualTo(testContentType);
            assertThat(result.getContentCount()).isEqualTo(1);
            assertThat(result.getUrls()).containsExactly("https://example.com/content");
            assertThat(result.getDescription()).isEqualTo("New content description");
            assertThat(result.getTags()).isEqualTo("new,tags");
            assertThat(result.getSocialMediaLink()).isEqualTo("https://instagram.com/p/xyz789");
            assertThat(result.getContentCreationDate()).isEqualTo(creationDate);
            assertThat(result.getSubmissionDate()).isEqualTo(submissionDate);
            assertThat(result.getApprovalStatus()).isEqualTo(ContentApprovalStatus.PENDING);
        }

        @Test
        @DisplayName("should set default submission date when not provided")
        void shouldSetDefaultSubmissionDateWhenNotProvided() {
            // Given
            testDtoIn.setSubmissionDate(null);
            LocalDateTime before = LocalDateTime.now();

            // When
            AppliedOpportunityContent result = mappingInstance.fromDto(testDtoIn, testAppliedOpportunity, testContentType);
            LocalDateTime after = LocalDateTime.now();

            // Then
            assertThat(result.getSubmissionDate()).isNotNull();
            assertThat(result.getSubmissionDate()).isBetween(before, after.plusSeconds(1));
        }

        @Test
        @DisplayName("should always set approval status to PENDING")
        void shouldAlwaysSetApprovalStatusToPending() {
            // When
            AppliedOpportunityContent result = mappingInstance.fromDto(testDtoIn, testAppliedOpportunity, testContentType);

            // Then
            assertThat(result.getApprovalStatus()).isEqualTo(ContentApprovalStatus.PENDING);
        }

        @Test
        @DisplayName("should handle null optional fields in dto")
        void shouldHandleNullOptionalFieldsInDto() {
            // Given
            testDtoIn.setDescription(null);
            testDtoIn.setTags(null);
            testDtoIn.setSocialMediaLink(null);
            testDtoIn.setUrls(null);
            testDtoIn.setContentCount(null);

            // When
            AppliedOpportunityContent result = mappingInstance.fromDto(testDtoIn, testAppliedOpportunity, testContentType);

            // Then
            assertThat(result.getDescription()).isNull();
            assertThat(result.getTags()).isNull();
            assertThat(result.getSocialMediaLink()).isNull();
            assertThat(result.getUrls()).isNull();
            assertThat(result.getContentCount()).isNull();
        }
    }

    @Nested
    @DisplayName("AppliedOpportunityContentMapping - updateFromDto")
    class MappingUpdateFromDto {

        private AppliedOpportunityContentMapping mappingInstance;

        @BeforeEach
        void setUpMapping() {
            mappingInstance = new AppliedOpportunityContentMapping();
        }

        @Test
        @DisplayName("should do nothing when content is null")
        void shouldDoNothingWhenContentIsNull() {
            // When/Then - no exception should be thrown
            mappingInstance.updateFromDto(null, testDtoIn, testContentType);
        }

        @Test
        @DisplayName("should do nothing when dto is null")
        void shouldDoNothingWhenDtoIsNull() {
            // Given
            String originalDescription = testContent.getDescription();

            // When
            mappingInstance.updateFromDto(testContent, null, testContentType);

            // Then
            assertThat(testContent.getDescription()).isEqualTo(originalDescription);
        }

        @Test
        @DisplayName("should update all fields from dto")
        void shouldUpdateAllFieldsFromDto() {
            // Given
            LocalDateTime newCreationDate = LocalDateTime.now().minusDays(5);
            LocalDateTime newSubmissionDate = LocalDateTime.now().minusDays(1);
            ContentType newContentType = new ContentType();
            newContentType.setId(2L);
            newContentType.setName("Instagram Story");

            testDtoIn.setDescription("Updated description");
            testDtoIn.setTags("updated,tags");
            testDtoIn.setUrls(List.of("https://new-url.com"));
            testDtoIn.setContentCount(5);
            testDtoIn.setSocialMediaLink("https://new-link.com");
            testDtoIn.setContentCreationDate(newCreationDate);
            testDtoIn.setSubmissionDate(newSubmissionDate);

            // When
            mappingInstance.updateFromDto(testContent, testDtoIn, newContentType);

            // Then
            assertThat(testContent.getContentType()).isEqualTo(newContentType);
            assertThat(testContent.getDescription()).isEqualTo("Updated description");
            assertThat(testContent.getTags()).isEqualTo("updated,tags");
            assertThat(testContent.getUrls()).containsExactly("https://new-url.com");
            assertThat(testContent.getContentCount()).isEqualTo(5);
            assertThat(testContent.getSocialMediaLink()).isEqualTo("https://new-link.com");
            assertThat(testContent.getContentCreationDate()).isEqualTo(newCreationDate);
            assertThat(testContent.getSubmissionDate()).isEqualTo(newSubmissionDate);
        }

        @Test
        @DisplayName("should not update submission date when null in dto")
        void shouldNotUpdateSubmissionDateWhenNullInDto() {
            // Given
            LocalDateTime originalSubmissionDate = LocalDateTime.now().minusDays(2);
            testContent.setSubmissionDate(originalSubmissionDate);
            testDtoIn.setSubmissionDate(null);

            // When
            mappingInstance.updateFromDto(testContent, testDtoIn, testContentType);

            // Then
            assertThat(testContent.getSubmissionDate()).isEqualTo(originalSubmissionDate);
        }

        @Test
        @DisplayName("should update submission date when provided in dto")
        void shouldUpdateSubmissionDateWhenProvidedInDto() {
            // Given
            LocalDateTime newSubmissionDate = LocalDateTime.now().plusDays(1);
            testDtoIn.setSubmissionDate(newSubmissionDate);

            // When
            mappingInstance.updateFromDto(testContent, testDtoIn, testContentType);

            // Then
            assertThat(testContent.getSubmissionDate()).isEqualTo(newSubmissionDate);
        }
    }

    // ==================== SPECIFICATION BUILDER TESTS ====================

    @Nested
    @DisplayName("AppliedOpportunityContentSpecificationBuilder")
    class SpecificationBuilderTests {

        private AppliedOpportunityContentSpecificationBuilder specBuilder;

        @BeforeEach
        void setUp() {
            specBuilder = new AppliedOpportunityContentSpecificationBuilder();
        }

        @Test
        @DisplayName("should create empty specification when no filters provided")
        void shouldCreateEmptySpecificationWhenNoFiltersProvided() {
            // Given
            Map<String, String> filters = new HashMap<>();

            // When
            var specification = specBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
        }

        @Test
        @DisplayName("should create specification with single filter")
        void shouldCreateSpecificationWithSingleFilter() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("approvalStatus", "PENDING");

            // When
            var specification = specBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
        }

        @Test
        @DisplayName("should create specification with multiple filters")
        void shouldCreateSpecificationWithMultipleFilters() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("approvalStatus", "APPROVED");
            filters.put("description", "test");

            // When
            var specification = specBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
        }

        @Test
        @DisplayName("should handle null filter values gracefully")
        void shouldHandleNullFilterValuesGracefully() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("description", null);

            // When
            var specification = specBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
        }

        @Test
        @DisplayName("should handle empty filter values")
        void shouldHandleEmptyFilterValues() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("description", "");

            // When
            var specification = specBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
        }

        @Test
        @DisplayName("specification builder extends base SpecificationBuilder")
        void specificationBuilderExtendsBaseSpecificationBuilder() {
            // Then
            assertThat(specBuilder).isInstanceOf(
                    com.sm.instagram.platform.common.util.filtering.SpecificationBuilder.class);
        }
    }

    // ==================== DTO TESTS ====================

    @Nested
    @DisplayName("AppliedOpportunityContentDtoIn")
    class DtoInTests {

        @Test
        @DisplayName("should create DTO with all fields")
        void shouldCreateDtoWithAllFields() {
            // Given
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            LocalDateTime creationDate = LocalDateTime.now().minusDays(1);
            LocalDateTime submissionDate = LocalDateTime.now();

            // When
            dto.setAppliedOpportunityId(100L);
            dto.setContentTypeId(5L);
            dto.setContentCount(3);
            dto.setUrls(List.of("url1", "url2"));
            dto.setDescription("Test description");
            dto.setTags("tag1,tag2");
            dto.setSocialMediaLink("https://instagram.com/test");
            dto.setContentCreationDate(creationDate);
            dto.setSubmissionDate(submissionDate);

            // Then
            assertThat(dto.getAppliedOpportunityId()).isEqualTo(100L);
            assertThat(dto.getContentTypeId()).isEqualTo(5L);
            assertThat(dto.getContentCount()).isEqualTo(3);
            assertThat(dto.getUrls()).containsExactly("url1", "url2");
            assertThat(dto.getDescription()).isEqualTo("Test description");
            assertThat(dto.getTags()).isEqualTo("tag1,tag2");
            assertThat(dto.getSocialMediaLink()).isEqualTo("https://instagram.com/test");
            assertThat(dto.getContentCreationDate()).isEqualTo(creationDate);
            assertThat(dto.getSubmissionDate()).isEqualTo(submissionDate);
        }

        @Test
        @DisplayName("should handle null values")
        void shouldHandleNullValues() {
            // Given
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();

            // Then
            assertThat(dto.getAppliedOpportunityId()).isNull();
            assertThat(dto.getContentTypeId()).isNull();
            assertThat(dto.getContentCount()).isNull();
            assertThat(dto.getUrls()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getTags()).isNull();
            assertThat(dto.getSocialMediaLink()).isNull();
            assertThat(dto.getContentCreationDate()).isNull();
            assertThat(dto.getSubmissionDate()).isNull();
        }
    }

    @Nested
    @DisplayName("AppliedOpportunityContentDtoOut")
    class DtoOutTests {

        @Test
        @DisplayName("should create DTO with all fields")
        void shouldCreateDtoWithAllFields() {
            // Given
            AppliedOpportunityContentDtoOut dto = new AppliedOpportunityContentDtoOut();
            LocalDateTime creationDate = LocalDateTime.now().minusDays(2);
            LocalDateTime submissionDate = LocalDateTime.now().minusDays(1);
            LocalDateTime createdTime = LocalDateTime.now().minusDays(3);
            LocalDateTime lastUpdateTime = LocalDateTime.now();

            // When
            dto.setId(1L);
            dto.setAppliedOpportunityId(10L);
            dto.setContentTypeId(1L);
            dto.setContentTypeName("Instagram Reel");
            dto.setContentCount(2);
            dto.setUrls(List.of("url1", "url2"));
            dto.setDescription("Description");
            dto.setTags("tags");
            dto.setContentCreationDate(creationDate);
            dto.setSubmissionDate(submissionDate);
            dto.setApprovalStatus(ContentApprovalStatus.APPROVED);
            dto.setApprovalNotes("Great!");
            dto.setLikesCount(100L);
            dto.setCommentsCount(20L);
            dto.setViewsCount(500L);
            dto.setSharesCount(10L);
            dto.setSocialMediaLink("https://link.com");
            dto.setCreatedTime(createdTime);
            dto.setLastUpdateTime(lastUpdateTime);
            dto.setUpdaterId("updater");

            // Then
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getAppliedOpportunityId()).isEqualTo(10L);
            assertThat(dto.getContentTypeId()).isEqualTo(1L);
            assertThat(dto.getContentTypeName()).isEqualTo("Instagram Reel");
            assertThat(dto.getContentCount()).isEqualTo(2);
            assertThat(dto.getUrls()).containsExactly("url1", "url2");
            assertThat(dto.getDescription()).isEqualTo("Description");
            assertThat(dto.getTags()).isEqualTo("tags");
            assertThat(dto.getContentCreationDate()).isEqualTo(creationDate);
            assertThat(dto.getSubmissionDate()).isEqualTo(submissionDate);
            assertThat(dto.getApprovalStatus()).isEqualTo(ContentApprovalStatus.APPROVED);
            assertThat(dto.getApprovalNotes()).isEqualTo("Great!");
            assertThat(dto.getLikesCount()).isEqualTo(100L);
            assertThat(dto.getCommentsCount()).isEqualTo(20L);
            assertThat(dto.getViewsCount()).isEqualTo(500L);
            assertThat(dto.getSharesCount()).isEqualTo(10L);
            assertThat(dto.getSocialMediaLink()).isEqualTo("https://link.com");
            assertThat(dto.getCreatedTime()).isEqualTo(createdTime);
            assertThat(dto.getLastUpdateTime()).isEqualTo(lastUpdateTime);
            assertThat(dto.getUpdaterId()).isEqualTo("updater");
        }
    }

    @Nested
    @DisplayName("AppliedOpportunityContentSimpleDtoOut")
    class SimpleDtoOutTests {

        @Test
        @DisplayName("should create simple DTO with essential fields")
        void shouldCreateSimpleDtoWithEssentialFields() {
            // Given
            AppliedOpportunityContentSimpleDtoOut dto = new AppliedOpportunityContentSimpleDtoOut();
            LocalDateTime submissionDate = LocalDateTime.now();

            // When
            dto.setId(1L);
            dto.setContentTypeName("Story");
            dto.setContentCount(1);
            dto.setUrls(List.of("url"));
            dto.setApprovalStatus(ContentApprovalStatus.PENDING);
            dto.setSocialMediaLink("link");
            dto.setSubmissionDate(submissionDate);
            dto.setLikesCount(50L);
            dto.setViewsCount(200L);

            // Then
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getContentTypeName()).isEqualTo("Story");
            assertThat(dto.getContentCount()).isEqualTo(1);
            assertThat(dto.getUrls()).containsExactly("url");
            assertThat(dto.getApprovalStatus()).isEqualTo(ContentApprovalStatus.PENDING);
            assertThat(dto.getSocialMediaLink()).isEqualTo("link");
            assertThat(dto.getSubmissionDate()).isEqualTo(submissionDate);
            assertThat(dto.getLikesCount()).isEqualTo(50L);
            assertThat(dto.getViewsCount()).isEqualTo(200L);
        }
    }

    // ==================== CONTENT APPROVAL STATUS TESTS ====================

    @Nested
    @DisplayName("ContentApprovalStatus Enum")
    class ContentApprovalStatusTests {

        @Test
        @DisplayName("should have correct values")
        void shouldHaveCorrectValues() {
            assertThat(ContentApprovalStatus.PENDING.getValue()).isEqualTo("PENDING");
            assertThat(ContentApprovalStatus.APPROVED.getValue()).isEqualTo("APPROVED");
            assertThat(ContentApprovalStatus.REJECTED.getValue()).isEqualTo("REJECTED");
            assertThat(ContentApprovalStatus.NEEDS_REVISION.getValue()).isEqualTo("NEEDS_REVISION");
            assertThat(ContentApprovalStatus.SUBMITTED.getValue()).isEqualTo("SUBMITTED");
        }

        @Test
        @DisplayName("should have correct toString representation")
        void shouldHaveCorrectToStringRepresentation() {
            assertThat(ContentApprovalStatus.PENDING.toString()).isEqualTo("pending");
            assertThat(ContentApprovalStatus.APPROVED.toString()).isEqualTo("approved");
            assertThat(ContentApprovalStatus.REJECTED.toString()).isEqualTo("rejected");
            assertThat(ContentApprovalStatus.NEEDS_REVISION.toString()).isEqualTo("needs_revision");
            assertThat(ContentApprovalStatus.SUBMITTED.toString()).isEqualTo("submitted");
        }

        @Test
        @DisplayName("should have all expected statuses")
        void shouldHaveAllExpectedStatuses() {
            assertThat(ContentApprovalStatus.values()).hasSize(5);
            assertThat(ContentApprovalStatus.values()).containsExactly(
                    ContentApprovalStatus.PENDING,
                    ContentApprovalStatus.APPROVED,
                    ContentApprovalStatus.REJECTED,
                    ContentApprovalStatus.NEEDS_REVISION,
                    ContentApprovalStatus.SUBMITTED
            );
        }

        @ParameterizedTest
        @EnumSource(ContentApprovalStatus.class)
        @DisplayName("each status should have non-empty value")
        void eachStatusShouldHaveNonEmptyValue(ContentApprovalStatus status) {
            assertThat(status.getValue()).isNotNull();
            assertThat(status.getValue()).isNotEmpty();
        }
    }

    // ==================== EDGE CASE TESTS ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("controller should handle null DTO gracefully in mapping")
        void controllerShouldHandleNullDtoGracefully() {
            // Given
            when(contentService.getContentById(1L)).thenReturn(testContent);
            when(contentMapping.toDto(testContent)).thenReturn(null);

            // When
            ResponseEntity<AppliedOpportunityContentDtoOut> response = controller.getContent(1L);

            // Then
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("should handle very long description in DTO")
        void shouldHandleVeryLongDescriptionInDto() {
            // Given
            String longDescription = "A".repeat(1000);
            testDtoIn.setDescription(longDescription);
            when(permissionUtils.getUserId()).thenReturn("uid");
            when(contentService.createContentSubmission(any(), anyString())).thenReturn(testContent);
            when(contentMapping.toDto(any())).thenReturn(testDtoOut);

            // When
            ResponseEntity<AppliedOpportunityContentDtoOut> response = controller.submitContent(testDtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should handle special characters in tags")
        void shouldHandleSpecialCharactersInTags() {
            // Given
            testDtoIn.setTags("tag-with-dash,tag_with_underscore,tag.with.dots");
            when(permissionUtils.getUserId()).thenReturn("uid");
            when(contentService.createContentSubmission(any(), anyString())).thenReturn(testContent);
            when(contentMapping.toDto(any())).thenReturn(testDtoOut);

            // When
            ResponseEntity<AppliedOpportunityContentDtoOut> response = controller.submitContent(testDtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should handle URLs with special characters")
        void shouldHandleUrlsWithSpecialCharacters() {
            // Given
            testDtoIn.setUrls(List.of(
                    "https://example.com/path?param=value&other=123",
                    "https://example.com/path#section",
                    "https://example.com/path%20with%20spaces"
            ));
            when(permissionUtils.getUserId()).thenReturn("uid");
            when(contentService.createContentSubmission(any(), anyString())).thenReturn(testContent);
            when(contentMapping.toDto(any())).thenReturn(testDtoOut);

            // When
            ResponseEntity<AppliedOpportunityContentDtoOut> response = controller.submitContent(testDtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should handle zero engagement metrics")
        void shouldHandleZeroEngagementMetrics() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("uid");
            doNothing().when(contentService).updateEngagementMetrics(
                    eq(1L), eq(0L), eq(0L), eq(0L), eq(0L), anyString());

            // When
            ResponseEntity<Void> response = controller.updateEngagementMetrics(1L, 0L, 0L, 0L, 0L);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(contentService).updateEngagementMetrics(1L, 0L, 0L, 0L, 0L, "uid");
        }

        @Test
        @DisplayName("should handle large engagement metrics")
        void shouldHandleLargeEngagementMetrics() {
            // Given
            Long largeValue = Long.MAX_VALUE;
            when(permissionUtils.getUserId()).thenReturn("uid");
            doNothing().when(contentService).updateEngagementMetrics(
                    eq(1L), eq(largeValue), eq(largeValue), eq(largeValue), eq(largeValue), anyString());

            // When
            ResponseEntity<Void> response = controller.updateEngagementMetrics(
                    1L, largeValue, largeValue, largeValue, largeValue);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ==================== SECURITY CONTEXT TESTS ====================

    @Nested
    @DisplayName("Security Context Handling")
    class SecurityContextHandling {

        @Test
        @DisplayName("should use security context principal for logging")
        void shouldUseSecurityContextPrincipalForLogging() {
            // Given
            when(authentication.getPrincipal()).thenReturn("specific-firebase-uid");
            when(permissionUtils.getUserId()).thenReturn("specific-firebase-uid");
            when(contentService.createContentSubmission(any(), anyString())).thenReturn(testContent);
            when(contentMapping.toDto(any())).thenReturn(testDtoOut);

            // When
            controller.submitContent(testDtoIn);

            // Then
            verify(authentication).getPrincipal();
        }

        @Test
        @DisplayName("should handle different principal types")
        void shouldHandleDifferentPrincipalTypes() {
            // Given
            when(authentication.getPrincipal()).thenReturn(new Object() {
                @Override
                public String toString() {
                    return "custom-principal";
                }
            });
            when(permissionUtils.getUserId()).thenReturn("uid");
            when(contentService.createContentSubmission(any(), anyString())).thenReturn(testContent);
            when(contentMapping.toDto(any())).thenReturn(testDtoOut);

            // When
            ResponseEntity<AppliedOpportunityContentDtoOut> response = controller.submitContent(testDtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }
}
