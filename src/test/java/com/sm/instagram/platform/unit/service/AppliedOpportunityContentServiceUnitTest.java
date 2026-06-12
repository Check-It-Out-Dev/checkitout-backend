package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.appliedopportunities.*;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.contenttype.ContentType;
import com.sm.instagram.platform.contenttype.ContentTypeRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import com.sm.instagram.platform.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AppliedOpportunityContentService.
 * Tests content submission, approval/rejection workflows, and permission checks.
 * Uses pure Mockito - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppliedOpportunityContentService Unit Tests")
class AppliedOpportunityContentServiceUnitTest {

    @Mock
    private AppliedOpportunityContentRepository contentRepository;

    @Mock
    private AppliedOpportunityRepository appliedOpportunityRepository;

    @Mock
    private ContentTypeRepository contentTypeRepository;

    @Mock
    private PermissionUtils permissionUtils;

    @Mock
    private AppliedOpportunityContentSpecificationBuilder specificationBuilder;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private RepositoryResolver repositoryResolver;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserRepository userRepository;

    private AppliedOpportunityContentService service;

    // Test fixtures
    private User testInfluencer;
    private User testCompany;
    private AppliedOpportunity testAppliedOpportunity;
    private AppliedOpportunityContent testContent;
    private ContentType testContentType;
    private PartnershipOpportunity testPartnershipOpportunity;
    private AppliedOpportunityContentDtoIn testDtoIn;

    @BeforeEach
    void setUp() {
        // Create the service with constructor injection
        service = new AppliedOpportunityContentService(
                specificationBuilder,
                contentRepository,
                modelMapper,
                repositoryResolver,
                appliedOpportunityRepository,
                contentTypeRepository,
                permissionUtils,
                applicationContext,
                eventPublisher,
                userRepository
        );

        // Create a spy and set up applicationContext to return the spy for getSelf() mechanism
        service = spy(service);

        // Mock applicationContext.getBean to return the spy (for getSelf())
        when(applicationContext.getBean(AppliedOpportunityContentService.class)).thenReturn(service);

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

        // Mock userRepository to return a test user for any firebaseUserId lookup
        // This is needed for event publishing in status transition methods
        when(userRepository.findByFirebaseUserId(anyString())).thenReturn(Optional.of(testInfluencer));

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

        // Create test content
        testContent = new AppliedOpportunityContent();
        testContent.setId(1L);
        testContent.setAppliedOpportunity(testAppliedOpportunity);
        testContent.setContentType(testContentType);
        testContent.setApprovalStatus(ContentApprovalStatus.PENDING);
        testContent.setDescription("Test content description");
        testContent.setUrls(List.of("https://example.com/content1"));
        testContent.setContentCount(1);
        testContent.setTags("test,content");

        // Create test DTO
        testDtoIn = new AppliedOpportunityContentDtoIn();
        testDtoIn.setAppliedOpportunityId(10L);
        testDtoIn.setContentTypeId(1L);
        testDtoIn.setDescription("New content description");
        testDtoIn.setUrls(List.of("https://example.com/content"));
        testDtoIn.setContentCount(1);
        testDtoIn.setTags("new,tags");
    }

    // ==================== createContentSubmission Tests ====================

    @Nested
    @DisplayName("createContentSubmission")
    class CreateContentSubmission {

        @Test
        @DisplayName("should create content when user is admin")
        void shouldCreateContentWhenUserIsAdmin() {
            // Given
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> {
                        AppliedOpportunityContent content = inv.getArgument(0);
                        content.setId(1L);
                        return content;
                    });
            doNothing().when(service).moveToContentSendToAccept(any(), anyString());

            // When
            AppliedOpportunityContent result = service.createContentSubmission(testDtoIn, "admin-uid");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getApprovalStatus()).isEqualTo(ContentApprovalStatus.PENDING);
            assertThat(result.getDescription()).isEqualTo("New content description");
            verify(contentRepository).save(any(AppliedOpportunityContent.class));
        }

        @Test
        @DisplayName("should create content when user is opportunity owner")
        void shouldCreateContentWhenUserIsOpportunityOwner() {
            // Given
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(testAppliedOpportunity)).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> {
                        AppliedOpportunityContent content = inv.getArgument(0);
                        content.setId(1L);
                        return content;
                    });
            doNothing().when(service).moveToContentSendToAccept(any(), anyString());

            // When
            AppliedOpportunityContent result = service.createContentSubmission(testDtoIn, "influencer-firebase-uid");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getApprovalStatus()).isEqualTo(ContentApprovalStatus.PENDING);
            verify(contentRepository).save(any(AppliedOpportunityContent.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when applied opportunity not found")
        void shouldThrowWhenAppliedOpportunityNotFound() {
            // Given
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.createContentSubmission(testDtoIn, "influencer-uid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user has no permission")
        void shouldThrowWhenUserHasNoPermission() {
            // Given
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(testAppliedOpportunity)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.createContentSubmission(testDtoIn, "unauthorized-uid"))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when content type not found")
        void shouldThrowWhenContentTypeNotFound() {
            // Given
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.createContentSubmission(testDtoIn, "admin-uid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should set submission date from DTO when provided")
        void shouldSetSubmissionDateFromDtoWhenProvided() {
            // Given
            LocalDateTime submissionDate = LocalDateTime.now().minusDays(1);
            testDtoIn.setSubmissionDate(submissionDate);

            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(service).moveToContentSendToAccept(any(), anyString());

            // When
            AppliedOpportunityContent result = service.createContentSubmission(testDtoIn, "admin-uid");

            // Then
            assertThat(result.getSubmissionDate()).isEqualTo(submissionDate);
        }

        @Test
        @DisplayName("should set submission date to now when not provided in DTO")
        void shouldSetSubmissionDateToNowWhenNotProvided() {
            // Given
            testDtoIn.setSubmissionDate(null);

            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(service).moveToContentSendToAccept(any(), anyString());

            // When
            AppliedOpportunityContent result = service.createContentSubmission(testDtoIn, "admin-uid");

            // Then
            assertThat(result.getSubmissionDate()).isNotNull();
            assertThat(result.getSubmissionDate()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        @DisplayName("should set all fields from DTO")
        void shouldSetAllFieldsFromDto() {
            // Given
            testDtoIn.setSocialMediaLink("https://instagram.com/p/abc");
            testDtoIn.setContentCreationDate(LocalDateTime.now().minusDays(2));

            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(service).moveToContentSendToAccept(any(), anyString());

            // When
            AppliedOpportunityContent result = service.createContentSubmission(testDtoIn, "admin-uid");

            // Then
            assertThat(result.getDescription()).isEqualTo(testDtoIn.getDescription());
            assertThat(result.getTags()).isEqualTo(testDtoIn.getTags());
            assertThat(result.getUrls()).isEqualTo(testDtoIn.getUrls());
            assertThat(result.getContentCount()).isEqualTo(testDtoIn.getContentCount());
            assertThat(result.getSocialMediaLink()).isEqualTo(testDtoIn.getSocialMediaLink());
            assertThat(result.getContentCreationDate()).isEqualTo(testDtoIn.getContentCreationDate());
        }
    }

    // ==================== updateContentSubmission Tests ====================

    @Nested
    @DisplayName("updateContentSubmission")
    class UpdateContentSubmission {

        @Test
        @DisplayName("should update content when user is admin")
        void shouldUpdateContentWhenUserIsAdmin() {
            // Given
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            AppliedOpportunityContent result = service.updateContentSubmission(1L, testDtoIn, "admin-uid");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDescription()).isEqualTo("New content description");
            verify(contentRepository).save(any(AppliedOpportunityContent.class));
        }

        @Test
        @DisplayName("should update content when user is owner")
        void shouldUpdateContentWhenUserIsOwner() {
            // Given
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(testAppliedOpportunity)).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            AppliedOpportunityContent result = service.updateContentSubmission(1L, testDtoIn, "influencer-uid");

            // Then
            assertThat(result).isNotNull();
            verify(contentRepository).save(any(AppliedOpportunityContent.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when content not found")
        void shouldThrowWhenContentNotFound() {
            // Given
            when(contentRepository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.updateContentSubmission(999L, testDtoIn, "admin-uid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user has no permission")
        void shouldThrowWhenUserHasNoPermissionToUpdate() {
            // Given
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(testAppliedOpportunity)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.updateContentSubmission(1L, testDtoIn, "unauthorized-uid"))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when content type not found during update")
        void shouldThrowWhenContentTypeNotFoundDuringUpdate() {
            // Given
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.updateContentSubmission(1L, testDtoIn, "admin-uid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should transition to CONTENT_POSTED when social media link added and can transition")
        void shouldTransitionToContentPostedWhenSocialMediaLinkAdded() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.CONTENT_APPROVED);
            testContent.setSocialMediaLink(null);
            testDtoIn.setSocialMediaLink("https://instagram.com/p/abc123");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(service).moveToContentPosted(any(), anyString());

            // When
            service.updateContentSubmission(1L, testDtoIn, "admin-uid");

            // Then
            verify(service).moveToContentPosted(any(), anyString());
        }

        @Test
        @DisplayName("should set submission date when publishing and submission date is null")
        void shouldSetSubmissionDateWhenPublishingAndSubmissionDateIsNull() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.CONTENT_APPROVED);
            testContent.setSocialMediaLink(null);
            testContent.setSubmissionDate(null);
            testDtoIn.setSocialMediaLink("https://instagram.com/p/abc123");
            testDtoIn.setSubmissionDate(null);

            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(service).moveToContentPosted(any(), anyString());

            // When
            AppliedOpportunityContent result = service.updateContentSubmission(1L, testDtoIn, "admin-uid");

            // Then
            assertThat(result.getSubmissionDate()).isNotNull();
        }
    }

    // ==================== moveToContentPosted Tests ====================

    @Nested
    @DisplayName("moveToContentPosted")
    class MoveToContentPosted {

        @Test
        @DisplayName("should transition to CONTENT_POSTED when status allows")
        void shouldTransitionToContentPostedWhenStatusAllows() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.CONTENT_APPROVED);
            when(appliedOpportunityRepository.save(any(AppliedOpportunity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.moveToContentPosted(testAppliedOpportunity, "admin-uid");

            // Then
            assertThat(testAppliedOpportunity.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_POSTED);
            verify(appliedOpportunityRepository).save(testAppliedOpportunity);
        }

        @Test
        @DisplayName("should not transition when status does not allow")
        void shouldNotTransitionWhenStatusDoesNotAllow() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.APPLIED);

            // When
            service.moveToContentPosted(testAppliedOpportunity, "admin-uid");

            // Then
            assertThat(testAppliedOpportunity.getOpportunityStatus()).isEqualTo(OpportunityStatus.APPLIED);
            verify(appliedOpportunityRepository, never()).save(any());
        }

        @Test
        @DisplayName("should set updater ID when transitioning")
        void shouldSetUpdaterIdWhenTransitioning() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.CONTENT_APPROVED);
            when(appliedOpportunityRepository.save(any(AppliedOpportunity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.moveToContentPosted(testAppliedOpportunity, "test-updater-id");

            // Then
            assertThat(testAppliedOpportunity.getUpdaterId()).isEqualTo("test-updater-id");
        }
    }

    // ==================== moveToContentSendToAccept Tests ====================

    @Nested
    @DisplayName("moveToContentSendToAccept")
    class MoveToContentSendToAccept {

        @Test
        @DisplayName("should transition to CONTENT_SEND_TO_ACCEPT when status allows")
        void shouldTransitionToContentSendToAcceptWhenStatusAllows() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.ACCEPTED_BY_INFLUENCER);
            when(appliedOpportunityRepository.save(any(AppliedOpportunity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.moveToContentSendToAccept(testAppliedOpportunity, "influencer-uid");

            // Then
            assertThat(testAppliedOpportunity.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            verify(appliedOpportunityRepository).save(testAppliedOpportunity);
        }

        @Test
        @DisplayName("should not transition when status does not allow")
        void shouldNotTransitionWhenStatusDoesNotAllowForSendToAccept() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.APPLIED);

            // When
            service.moveToContentSendToAccept(testAppliedOpportunity, "influencer-uid");

            // Then
            assertThat(testAppliedOpportunity.getOpportunityStatus()).isEqualTo(OpportunityStatus.APPLIED);
            verify(appliedOpportunityRepository, never()).save(any());
        }
    }

    // ==================== approveContent Tests ====================

    @Nested
    @DisplayName("approveContent")
    class ApproveContent {

        @Test
        @DisplayName("should approve content when user is admin")
        void shouldApproveContentWhenUserIsAdmin() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            when(contentRepository.findByIdWithRelationships(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(appliedOpportunityRepository.save(any(AppliedOpportunity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.approveContent(1L, "Great content!", "admin-uid");

            // Then
            assertThat(testContent.getApprovalStatus()).isEqualTo(ContentApprovalStatus.APPROVED);
            assertThat(testContent.getApprovalNotes()).isEqualTo("Great content!");
            verify(contentRepository).save(testContent);
        }

        @Test
        @DisplayName("should approve content when user is company owner")
        void shouldApproveContentWhenUserIsCompanyOwner() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            when(contentRepository.findByIdWithRelationships(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompanyOwnerOfAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(appliedOpportunityRepository.save(any(AppliedOpportunity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.approveContent(1L, "Approved!", "company-uid");

            // Then
            assertThat(testContent.getApprovalStatus()).isEqualTo(ContentApprovalStatus.APPROVED);
            verify(contentRepository).save(testContent);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when content not found for approval")
        void shouldThrowWhenContentNotFoundForApproval() {
            // Given
            when(contentRepository.findByIdWithRelationships(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.approveContent(999L, "Notes", "admin-uid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user cannot approve")
        void shouldThrowWhenUserCannotApprove() {
            // Given
            when(contentRepository.findByIdWithRelationships(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompanyOwnerOfAppliedOpportunity(testAppliedOpportunity)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.approveContent(1L, "Notes", "unauthorized-uid"))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should transition opportunity to CONTENT_APPROVED when at CONTENT_SEND_TO_ACCEPT")
        void shouldTransitionOpportunityToContentApproved() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            when(contentRepository.findByIdWithRelationships(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(appliedOpportunityRepository.save(any(AppliedOpportunity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.approveContent(1L, "Great!", "admin-uid");

            // Then
            assertThat(testAppliedOpportunity.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_APPROVED);
            verify(appliedOpportunityRepository).save(testAppliedOpportunity);
        }

        @Test
        @DisplayName("should not transition opportunity status when not at CONTENT_SEND_TO_ACCEPT")
        void shouldNotTransitionOpportunityStatusWhenNotAtContentSendToAccept() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.CONTENT_APPROVED);
            when(contentRepository.findByIdWithRelationships(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.approveContent(1L, "Great!", "admin-uid");

            // Then
            assertThat(testAppliedOpportunity.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_APPROVED);
            verify(appliedOpportunityRepository, never()).save(any());
        }
    }

    // ==================== rejectContent Tests ====================

    @Nested
    @DisplayName("rejectContent")
    class RejectContent {

        @Test
        @DisplayName("should reject content when user is admin")
        void shouldRejectContentWhenUserIsAdmin() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            when(contentRepository.findByIdWithRelationships(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(appliedOpportunityRepository.save(any(AppliedOpportunity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.rejectContent(1L, "Needs more work", "admin-uid");

            // Then
            assertThat(testContent.getApprovalStatus()).isEqualTo(ContentApprovalStatus.REJECTED);
            assertThat(testContent.getApprovalNotes()).isEqualTo("Needs more work");
        }

        @Test
        @DisplayName("should reject content when user is company owner")
        void shouldRejectContentWhenUserIsCompanyOwner() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            when(contentRepository.findByIdWithRelationships(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompanyOwnerOfAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(appliedOpportunityRepository.save(any(AppliedOpportunity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.rejectContent(1L, "Rejected!", "company-uid");

            // Then
            assertThat(testContent.getApprovalStatus()).isEqualTo(ContentApprovalStatus.REJECTED);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when content not found for rejection")
        void shouldThrowWhenContentNotFoundForRejection() {
            // Given
            when(contentRepository.findByIdWithRelationships(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.rejectContent(999L, "Notes", "admin-uid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user cannot reject")
        void shouldThrowWhenUserCannotReject() {
            // Given
            when(contentRepository.findByIdWithRelationships(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompanyOwnerOfAppliedOpportunity(testAppliedOpportunity)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.rejectContent(1L, "Notes", "unauthorized-uid"))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should transition to CONTENT_REJECTED when at CONTENT_SEND_TO_ACCEPT")
        void shouldTransitionToContentRejectedWhenAtContentSendToAccept() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            when(contentRepository.findByIdWithRelationships(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(appliedOpportunityRepository.save(any(AppliedOpportunity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.rejectContent(1L, "Rejected", "admin-uid");

            // Then
            assertThat(testAppliedOpportunity.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_REJECTED);
        }

        @Test
        @DisplayName("should transition to CONTENT_POSTED_REJECTED when at CONTENT_POSTED")
        void shouldTransitionToContentPostedRejectedWhenAtContentPosted() {
            // Given
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.CONTENT_POSTED);
            when(contentRepository.findByIdWithRelationships(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(appliedOpportunityRepository.save(any(AppliedOpportunity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.rejectContent(1L, "Rejected after posting", "admin-uid");

            // Then
            assertThat(testAppliedOpportunity.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_POSTED_REJECTED);
        }
    }

    // ==================== getContentByAppliedOpportunity Tests ====================

    @Nested
    @DisplayName("getContentByAppliedOpportunity")
    class GetContentByAppliedOpportunity {

        @Test
        @DisplayName("should return content list when user can view opportunity")
        void shouldReturnContentListWhenUserCanView() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("influencer-uid");
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(contentRepository.findByAppliedOpportunityId(10L)).thenReturn(List.of(testContent));

            // When
            List<AppliedOpportunityContent> result = service.getContentByAppliedOpportunity(10L);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(testContent);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when opportunity not found")
        void shouldThrowWhenOpportunityNotFoundForRetrieval() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("user-uid");
            when(appliedOpportunityRepository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.getContentByAppliedOpportunity(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user cannot view")
        void shouldThrowWhenUserCannotViewOpportunity() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("unauthorized-uid");
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.getContentByAppliedOpportunity(10L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should return empty list when no content exists")
        void shouldReturnEmptyListWhenNoContentExists() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("influencer-uid");
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(contentRepository.findByAppliedOpportunityId(10L)).thenReturn(Collections.emptyList());

            // When
            List<AppliedOpportunityContent> result = service.getContentByAppliedOpportunity(10L);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ==================== getContentByAppliedOpportunityAndStatus Tests ====================

    @Nested
    @DisplayName("getContentByAppliedOpportunityAndStatus")
    class GetContentByAppliedOpportunityAndStatus {

        @Test
        @DisplayName("should return filtered content by status")
        void shouldReturnFilteredContentByStatus() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("influencer-uid");
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(contentRepository.findByAppliedOpportunityIdAndApprovalStatus(10L, ContentApprovalStatus.PENDING))
                    .thenReturn(List.of(testContent));

            // When
            List<AppliedOpportunityContent> result = service.getContentByAppliedOpportunityAndStatus(10L, ContentApprovalStatus.PENDING);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getApprovalStatus()).isEqualTo(ContentApprovalStatus.PENDING);
        }

        @ParameterizedTest
        @EnumSource(ContentApprovalStatus.class)
        @DisplayName("should filter by any content approval status")
        void shouldFilterByAnyContentApprovalStatus(ContentApprovalStatus status) {
            // Given
            when(permissionUtils.getUserId()).thenReturn("influencer-uid");
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(contentRepository.findByAppliedOpportunityIdAndApprovalStatus(10L, status))
                    .thenReturn(Collections.emptyList());

            // When
            List<AppliedOpportunityContent> result = service.getContentByAppliedOpportunityAndStatus(10L, status);

            // Then
            assertThat(result).isEmpty();
            verify(contentRepository).findByAppliedOpportunityIdAndApprovalStatus(10L, status);
        }
    }

    // ==================== getContentByApprovalStatus Tests ====================

    @Nested
    @DisplayName("getContentByApprovalStatus")
    class GetContentByApprovalStatus {

        @Test
        @DisplayName("admin should get all content by status")
        void adminShouldGetAllContentByStatus() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentRepository.findByApprovalStatus(ContentApprovalStatus.PENDING))
                    .thenReturn(List.of(testContent));

            // When
            List<AppliedOpportunityContent> result = service.getContentByApprovalStatus(ContentApprovalStatus.PENDING);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("company should only get content for their opportunities")
        void companyShouldOnlyGetContentForTheirOpportunities() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("company-firebase-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompany()).thenReturn(true);
            when(contentRepository.findByApprovalStatus(ContentApprovalStatus.PENDING))
                    .thenReturn(List.of(testContent));

            // When
            List<AppliedOpportunityContent> result = service.getContentByApprovalStatus(ContentApprovalStatus.PENDING);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("influencer should only get their own content")
        void influencerShouldOnlyGetTheirOwnContent() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("influencer-firebase-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompany()).thenReturn(false);
            when(permissionUtils.isInfluencer()).thenReturn(true);
            when(contentRepository.findByApprovalStatus(ContentApprovalStatus.PENDING))
                    .thenReturn(List.of(testContent));

            // When
            List<AppliedOpportunityContent> result = service.getContentByApprovalStatus(ContentApprovalStatus.PENDING);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("unknown role should get empty list")
        void unknownRoleShouldGetEmptyList() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("unknown-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompany()).thenReturn(false);
            when(permissionUtils.isInfluencer()).thenReturn(false);
            when(contentRepository.findByApprovalStatus(ContentApprovalStatus.PENDING))
                    .thenReturn(List.of(testContent));

            // When
            List<AppliedOpportunityContent> result = service.getContentByApprovalStatus(ContentApprovalStatus.PENDING);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ==================== updateEngagementMetrics Tests ====================

    @Nested
    @DisplayName("updateEngagementMetrics")
    class UpdateEngagementMetrics {

        @Test
        @DisplayName("should update all engagement metrics when provided")
        void shouldUpdateAllEngagementMetricsWhenProvided() {
            // Given
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.updateEngagementMetrics(1L, 1000L, 50L, 5000L, 100L, "admin-uid");

            // Then
            assertThat(testContent.getLikesCount()).isEqualTo(1000L);
            assertThat(testContent.getCommentsCount()).isEqualTo(50L);
            assertThat(testContent.getViewsCount()).isEqualTo(5000L);
            assertThat(testContent.getSharesCount()).isEqualTo(100L);
            verify(contentRepository).save(testContent);
        }

        @Test
        @DisplayName("should only update non-null metrics")
        void shouldOnlyUpdateNonNullMetrics() {
            // Given
            testContent.setLikesCount(100L);
            testContent.setCommentsCount(10L);
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When - only update likes
            service.updateEngagementMetrics(1L, 200L, null, null, null, "admin-uid");

            // Then
            assertThat(testContent.getLikesCount()).isEqualTo(200L);
            assertThat(testContent.getCommentsCount()).isEqualTo(10L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when content not found")
        void shouldThrowWhenContentNotFoundForMetricsUpdate() {
            // Given
            when(contentRepository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.updateEngagementMetrics(999L, 100L, 10L, 1000L, 50L, "admin-uid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user cannot update metrics")
        void shouldThrowWhenUserCannotUpdateMetrics() {
            // Given
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(testAppliedOpportunity)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.updateEngagementMetrics(1L, 100L, 10L, 1000L, 50L, "unauthorized-uid"))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("owner can update engagement metrics")
        void ownerCanUpdateEngagementMetrics() {
            // Given
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(testAppliedOpportunity)).thenReturn(true);
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            service.updateEngagementMetrics(1L, 500L, 25L, 2500L, 50L, "influencer-uid");

            // Then
            assertThat(testContent.getLikesCount()).isEqualTo(500L);
            verify(contentRepository).save(testContent);
        }
    }

    // ==================== getContentById Tests ====================

    @Nested
    @DisplayName("getContentById")
    class GetContentById {

        @Test
        @DisplayName("should return content when user can view")
        void shouldReturnContentWhenUserCanView() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("influencer-uid");
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);

            // When
            AppliedOpportunityContent result = service.getContentById(1L);

            // Then
            assertThat(result).isEqualTo(testContent);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when content not found")
        void shouldThrowWhenContentNotFoundById() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("user-uid");
            when(contentRepository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.getContentById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user cannot view content")
        void shouldThrowWhenUserCannotViewContent() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("unauthorized-uid");
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.getContentById(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // ==================== deleteContent Tests ====================

    @Nested
    @DisplayName("deleteContent")
    class DeleteContent {

        @Test
        @DisplayName("should delete content when user is admin")
        void shouldDeleteContentWhenUserIsAdmin() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(true);
            doNothing().when(contentRepository).delete(testContent);

            // When
            service.deleteContent(1L);

            // Then
            verify(contentRepository).delete(testContent);
        }

        @Test
        @DisplayName("should delete content when user is owner")
        void shouldDeleteContentWhenUserIsOwner() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("influencer-uid");
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(testAppliedOpportunity)).thenReturn(true);
            doNothing().when(contentRepository).delete(testContent);

            // When
            service.deleteContent(1L);

            // Then
            verify(contentRepository).delete(testContent);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when content not found for deletion")
        void shouldThrowWhenContentNotFoundForDeletion() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(contentRepository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.deleteContent(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user cannot delete")
        void shouldThrowWhenUserCannotDelete() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("unauthorized-uid");
            when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(testAppliedOpportunity)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.deleteContent(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // ==================== toDto Tests ====================

    @Nested
    @DisplayName("toDto")
    class ToDto {

        @Test
        @DisplayName("should return null when entity is null")
        void shouldReturnNullWhenEntityIsNull() {
            // When
            Object result = service.toDto(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should convert entity to DTO")
        void shouldConvertEntityToDto() {
            // Given
            AppliedOpportunityContentDtoOut expectedDto = new AppliedOpportunityContentDtoOut();
            expectedDto.setId(1L);
            when(modelMapper.map(testContent, AppliedOpportunityContentDtoOut.class)).thenReturn(expectedDto);

            // When
            Object result = service.toDto(testContent);

            // Then
            assertThat(result).isInstanceOf(AppliedOpportunityContentDtoOut.class);
            assertThat(((AppliedOpportunityContentDtoOut) result).getId()).isEqualTo(1L);
        }
    }

    // ==================== Content Status Workflow Tests ====================

    @Nested
    @DisplayName("Content Status Workflow")
    class ContentStatusWorkflow {

        @ParameterizedTest
        @EnumSource(ContentApprovalStatus.class)
        @DisplayName("all content approval statuses should be settable")
        void allContentApprovalStatusesShouldBeSettable(ContentApprovalStatus status) {
            // When
            testContent.setApprovalStatus(status);

            // Then
            assertThat(testContent.getApprovalStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("content workflow: PENDING to APPROVED")
        void contentWorkflowPendingToApproved() {
            // Given
            testContent.setApprovalStatus(ContentApprovalStatus.PENDING);

            // When
            testContent.setApprovalStatus(ContentApprovalStatus.APPROVED);

            // Then
            assertThat(testContent.getApprovalStatus()).isEqualTo(ContentApprovalStatus.APPROVED);
        }

        @Test
        @DisplayName("content workflow: PENDING to REJECTED")
        void contentWorkflowPendingToRejected() {
            // Given
            testContent.setApprovalStatus(ContentApprovalStatus.PENDING);

            // When
            testContent.setApprovalStatus(ContentApprovalStatus.REJECTED);

            // Then
            assertThat(testContent.getApprovalStatus()).isEqualTo(ContentApprovalStatus.REJECTED);
        }
    }

    // ==================== Opportunity Status Transition Tests ====================

    @Nested
    @DisplayName("Opportunity Status Transitions")
    class OpportunityStatusTransitions {

        @Test
        @DisplayName("CONTENT_APPROVED can transition to CONTENT_POSTED")
        void contentApprovedCanTransitionToContentPosted() {
            assertThat(OpportunityStatus.CONTENT_APPROVED.canTransitionTo(OpportunityStatus.CONTENT_POSTED)).isTrue();
        }

        @Test
        @DisplayName("CONTENT_SEND_TO_ACCEPT can transition to CONTENT_APPROVED")
        void contentSendToAcceptCanTransitionToContentApproved() {
            assertThat(OpportunityStatus.CONTENT_SEND_TO_ACCEPT.canTransitionTo(OpportunityStatus.CONTENT_APPROVED)).isTrue();
        }

        @Test
        @DisplayName("CONTENT_SEND_TO_ACCEPT can transition to CONTENT_REJECTED")
        void contentSendToAcceptCanTransitionToContentRejected() {
            assertThat(OpportunityStatus.CONTENT_SEND_TO_ACCEPT.canTransitionTo(OpportunityStatus.CONTENT_REJECTED)).isTrue();
        }

        @Test
        @DisplayName("CONTENT_POSTED can transition to CONTENT_POSTED_REJECTED")
        void contentPostedCanTransitionToContentPostedRejected() {
            assertThat(OpportunityStatus.CONTENT_POSTED.canTransitionTo(OpportunityStatus.CONTENT_POSTED_REJECTED)).isTrue();
        }

        @Test
        @DisplayName("ACCEPTED_BY_INFLUENCER can transition to CONTENT_SEND_TO_ACCEPT")
        void acceptedByInfluencerCanTransitionToContentSendToAccept() {
            assertThat(OpportunityStatus.ACCEPTED_BY_INFLUENCER.canTransitionTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT)).isTrue();
        }

        @Test
        @DisplayName("APPLIED cannot transition directly to CONTENT_POSTED")
        void appliedCannotTransitionDirectlyToContentPosted() {
            assertThat(OpportunityStatus.APPLIED.canTransitionTo(OpportunityStatus.CONTENT_POSTED)).isFalse();
        }
    }

    // ==================== getContentByAppliedOpportunityPaged Tests ====================

    @Nested
    @DisplayName("getContentByAppliedOpportunityPaged")
    class GetContentByAppliedOpportunityPaged {

        @Test
        @DisplayName("should return paged content when user can view")
        void shouldReturnPagedContentWhenUserCanView() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Map<String, String> filters = new HashMap<>();
            Page<AppliedOpportunityContent> expectedPage = new PageImpl<>(List.of(testContent));

            when(permissionUtils.getUserId()).thenReturn("influencer-uid");
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            doReturn(expectedPage).when(service).getDataPagedAndFiltered(eq(pageable), any());

            // When
            Page<AppliedOpportunityContent> result = service.getContentByAppliedOpportunityPaged(10L, pageable, filters);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user cannot view paged content")
        void shouldThrowWhenUserCannotViewPagedContent() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Map<String, String> filters = new HashMap<>();

            when(permissionUtils.getUserId()).thenReturn("unauthorized-uid");
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.getContentByAppliedOpportunityPaged(10L, pageable, filters))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // ==================== Edge Case Tests ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty URLs list")
        void shouldHandleEmptyUrlsList() {
            // Given
            testDtoIn.setUrls(Collections.emptyList());

            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(service).moveToContentSendToAccept(any(), anyString());

            // When
            AppliedOpportunityContent result = service.createContentSubmission(testDtoIn, "admin-uid");

            // Then
            assertThat(result.getUrls()).isEmpty();
        }

        @Test
        @DisplayName("should handle null description")
        void shouldHandleNullDescription() {
            // Given
            testDtoIn.setDescription(null);

            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(service).moveToContentSendToAccept(any(), anyString());

            // When
            AppliedOpportunityContent result = service.createContentSubmission(testDtoIn, "admin-uid");

            // Then
            assertThat(result.getDescription()).isNull();
        }

        @Test
        @DisplayName("should handle null tags")
        void shouldHandleNullTags() {
            // Given
            testDtoIn.setTags(null);

            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(service).moveToContentSendToAccept(any(), anyString());

            // When
            AppliedOpportunityContent result = service.createContentSubmission(testDtoIn, "admin-uid");

            // Then
            assertThat(result.getTags()).isNull();
        }

        @Test
        @DisplayName("should handle null content count")
        void shouldHandleNullContentCount() {
            // Given
            testDtoIn.setContentCount(null);

            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(contentTypeRepository.findById(1L)).thenReturn(Optional.of(testContentType));
            when(contentRepository.save(any(AppliedOpportunityContent.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(service).moveToContentSendToAccept(any(), anyString());

            // When
            AppliedOpportunityContent result = service.createContentSubmission(testDtoIn, "admin-uid");

            // Then
            assertThat(result.getContentCount()).isNull();
        }
    }
}
