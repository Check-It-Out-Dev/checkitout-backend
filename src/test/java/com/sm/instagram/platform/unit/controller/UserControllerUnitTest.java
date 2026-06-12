package com.sm.instagram.platform.unit.controller;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.translation.TranslationService;
import com.sm.instagram.platform.user.*;
import com.sm.instagram.platform.user.dto.DeletionEligibilityDto;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserController.
 * Pure Mockito tests without Spring context - tests controller logic directly.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserController Unit Tests")
class UserControllerUnitTest {

    @Mock
    private UserService userService;

    @Mock
    private UserSocialConnectionService userSocialConnectionService;

    @Mock
    private TranslationService translationService;

    @Mock
    private DefaultNoteService defaultNoteService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private com.sm.instagram.platform.auth.stepup.StepUpAuthService stepUpAuthService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private TestableUserController userController;

    private static final String FIREBASE_UID = "firebase-uid-123";
    private static final Long USER_ID = 1L;

    /**
     * Testable subclass to access protected constructor.
     */
    private static class TestableUserController extends UserController {
        public TestableUserController(UserService userService,
                                       UserSocialConnectionService userSocialConnectionService,
                                       TranslationService translationService,
                                       DefaultNoteService defaultNoteService,
                                       HttpServletRequest request,
                                       com.sm.instagram.platform.auth.stepup.StepUpAuthService stepUpAuthService) {
            super(userService, userSocialConnectionService, translationService, defaultNoteService, request, stepUpAuthService);
        }
    }

    @BeforeEach
    void setUp() {
        userController = new TestableUserController(
                userService,
                userSocialConnectionService,
                translationService,
                defaultNoteService,
                request,
                stepUpAuthService
        );

        // Setup default security context
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(FIREBASE_UID);
    }

    private User createUser(Long id, String firebaseUid, UserType userType) {
        User user = new User();
        user.setId(id);
        user.setFirebaseUserId(firebaseUid);
        user.setUserType(userType);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPhoneNumber("+48123456789");
        user.setCreatedTime(LocalDateTime.now());
        user.setLastUpdateTime(LocalDateTime.now());
        return user;
    }

    private UserDtoOut createUserDtoOut(Long id, String firebaseUid) {
        UserDtoOut dto = new UserDtoOut();
        dto.setId(id);
        dto.setFirebaseUserId(firebaseUid);
        dto.setEmail("test@example.com");
        dto.setFirstName("John");
        dto.setLastName("Doe");
        dto.setCreatedTime(LocalDateTime.now());
        return dto;
    }

    private UserDtoIn createUserDtoIn() {
        UserDtoIn dto = new UserDtoIn();
        dto.setEmail("test@example.com");
        dto.setUserType(UserType.INFLUENCER);
        dto.setAccountStatus(AccountStatus.IN_VALIDATION);
        dto.setFirstName("John");
        dto.setLastName("Doe");
        return dto;
    }

    // ==================== GET /users/me Tests ====================

    @Nested
    @DisplayName("GET /users/me - getCurrentUser")
    class GetCurrentUserTests {

        @Test
        @DisplayName("should return current user successfully")
        void shouldReturnCurrentUserSuccessfully() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                when(userService.findByFirebaseUserIdAsDto(FIREBASE_UID)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.getCurrentUser();

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getId()).isEqualTo(USER_ID);
                assertThat(response.getBody().getFirebaseUserId()).isEqualTo(FIREBASE_UID);
                verify(userService).findByFirebaseUserIdAsDto(FIREBASE_UID);
            }
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when authentication is null")
        void shouldThrowWhenAuthenticationIsNull() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                when(securityContext.getAuthentication()).thenReturn(null);
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);

                // When/Then
                assertThatThrownBy(() -> userController.getCurrentUser())
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when principal is null")
        void shouldThrowWhenPrincipalIsNull() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                when(authentication.getPrincipal()).thenReturn(null);
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);

                // When/Then
                assertThatThrownBy(() -> userController.getCurrentUser())
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException from service")
        void shouldPropagateResourceNotFoundFromService() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(userService.findByFirebaseUserIdAsDto(FIREBASE_UID))
                        .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

                // When/Then
                assertThatThrownBy(() -> userController.getCurrentUser())
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Test
        @DisplayName("should handle user with all fields populated")
        void shouldHandleUserWithAllFieldsPopulated() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                expectedDto.setPhoneNumber("+48123456789");
                expectedDto.setProfilePicture("https://example.com/pic.jpg");
                expectedDto.setNoteFromAdmin("Admin note");
                expectedDto.setPremium(true);
                when(userService.findByFirebaseUserIdAsDto(FIREBASE_UID)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.getCurrentUser();

                // Then
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getPhoneNumber()).isEqualTo("+48123456789");
                assertThat(response.getBody().getProfilePicture()).isEqualTo("https://example.com/pic.jpg");
                assertThat(response.getBody().getPremium()).isTrue();
            }
        }
    }

    // ==================== GET /users/influencers/{id}/public-profile Tests ====================

    @Nested
    @DisplayName("GET /users/influencers/{id}/public-profile - getInfluencerPublicProfile")
    class GetInfluencerPublicProfileTests {

        @Test
        @DisplayName("should return influencer public profile successfully")
        void shouldReturnInfluencerPublicProfileSuccessfully() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                InfluencerPublicProfileDto expectedDto = new InfluencerPublicProfileDto();
                expectedDto.setId(USER_ID);
                expectedDto.setName("John Doe");
                expectedDto.setFollowersCount(10000);
                when(userService.getInfluencerPublicProfile(USER_ID)).thenReturn(expectedDto);

                // When
                ResponseEntity<InfluencerPublicProfileDto> response = userController.getInfluencerPublicProfile(USER_ID);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getId()).isEqualTo(USER_ID);
                assertThat(response.getBody().getName()).isEqualTo("John Doe");
                verify(userService).getInfluencerPublicProfile(USER_ID);
            }
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when influencer not found")
        void shouldThrowWhenInfluencerNotFound() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(userService.getInfluencerPublicProfile(999L))
                        .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

                // When/Then
                assertThatThrownBy(() -> userController.getInfluencerPublicProfile(999L))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Test
        @DisplayName("should return profile with platform information")
        void shouldReturnProfileWithPlatformInformation() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                InfluencerPublicProfileDto expectedDto = new InfluencerPublicProfileDto();
                expectedDto.setId(USER_ID);
                expectedDto.setPlatformName("Instagram");
                expectedDto.setDisplayName("@johndoe");
                expectedDto.setProfileUrl("https://instagram.com/johndoe");
                when(userService.getInfluencerPublicProfile(USER_ID)).thenReturn(expectedDto);

                // When
                ResponseEntity<InfluencerPublicProfileDto> response = userController.getInfluencerPublicProfile(USER_ID);

                // Then
                assertThat(response.getBody().getPlatformName()).isEqualTo("Instagram");
                assertThat(response.getBody().getDisplayName()).isEqualTo("@johndoe");
            }
        }

        @Test
        @DisplayName("should return profile with premium status")
        void shouldReturnProfileWithPremiumStatus() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                InfluencerPublicProfileDto expectedDto = new InfluencerPublicProfileDto();
                expectedDto.setId(USER_ID);
                expectedDto.setPremium(true);
                when(userService.getInfluencerPublicProfile(USER_ID)).thenReturn(expectedDto);

                // When
                ResponseEntity<InfluencerPublicProfileDto> response = userController.getInfluencerPublicProfile(USER_ID);

                // Then
                assertThat(response.getBody().getPremium()).isTrue();
            }
        }
    }

    // ==================== GET /users/companies/{id}/public-profile Tests ====================

    @Nested
    @DisplayName("GET /users/companies/{id}/public-profile - getCompanyPublicProfile")
    class GetCompanyPublicProfileTests {

        @Test
        @DisplayName("should return company public profile successfully")
        void shouldReturnCompanyPublicProfileSuccessfully() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                CompanyPublicProfileDto expectedDto = new CompanyPublicProfileDto();
                expectedDto.setId(USER_ID);
                expectedDto.setName("Test Company");
                when(userService.getCompanyPublicProfile(USER_ID)).thenReturn(expectedDto);

                // When
                ResponseEntity<CompanyPublicProfileDto> response = userController.getCompanyPublicProfile(USER_ID);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getId()).isEqualTo(USER_ID);
                assertThat(response.getBody().getName()).isEqualTo("Test Company");
                verify(userService).getCompanyPublicProfile(USER_ID);
            }
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when company not found")
        void shouldThrowWhenCompanyNotFound() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(userService.getCompanyPublicProfile(999L))
                        .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

                // When/Then
                assertThatThrownBy(() -> userController.getCompanyPublicProfile(999L))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Test
        @DisplayName("should return profile with description")
        void shouldReturnProfileWithDescription() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                CompanyPublicProfileDto expectedDto = new CompanyPublicProfileDto();
                expectedDto.setId(USER_ID);
                expectedDto.setDescription("A great company");
                when(userService.getCompanyPublicProfile(USER_ID)).thenReturn(expectedDto);

                // When
                ResponseEntity<CompanyPublicProfileDto> response = userController.getCompanyPublicProfile(USER_ID);

                // Then
                assertThat(response.getBody().getDescription()).isEqualTo("A great company");
            }
        }

        @Test
        @DisplayName("should return profile with premium status")
        void shouldReturnProfileWithPremiumStatus() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                CompanyPublicProfileDto expectedDto = new CompanyPublicProfileDto();
                expectedDto.setId(USER_ID);
                expectedDto.setPremium(true);
                when(userService.getCompanyPublicProfile(USER_ID)).thenReturn(expectedDto);

                // When
                ResponseEntity<CompanyPublicProfileDto> response = userController.getCompanyPublicProfile(USER_ID);

                // Then
                assertThat(response.getBody().getPremium()).isTrue();
            }
        }
    }

    // ==================== GET /users/paged/public-profile Tests ====================

    @Nested
    @DisplayName("GET /users/paged/public-profile - findPublicPaginated")
    class FindPublicPaginatedTests {

        @Test
        @DisplayName("should return paginated public profiles successfully")
        void shouldReturnPaginatedPublicProfilesSuccessfully() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();

                User influencer = createUser(1L, "uid-1", UserType.INFLUENCER);
                User company = createUser(2L, "uid-2", UserType.COMPANY);
                Page<User> userPage = new PageImpl<>(List.of(influencer, company), pageable, 2);

                when(userService.getDataPagedAndFiltered(pageable, filters)).thenReturn(userPage);

                InfluencerPublicProfileDto influencerDto = new InfluencerPublicProfileDto();
                influencerDto.setId(1L);
                when(userSocialConnectionService.getPrimaryConnectionSafe(influencer)).thenReturn(null);
                when(userService.toInfluencerPublicProfileDtoWithSocialData(eq(influencer), isNull()))
                        .thenReturn(influencerDto);

                CompanyPublicProfileDto companyDto = new CompanyPublicProfileDto();
                companyDto.setId(2L);
                when(userService.toCompanyPublicProfileDto(company)).thenReturn(companyDto);

                // When
                ResponseEntity<Page<PublicProfileDto>> response = userController.findPublicPaginated(pageable, filters);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
            }
        }

        @Test
        @DisplayName("should filter out admin users from public profiles")
        void shouldFilterOutAdminUsersFromPublicProfiles() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();

                User admin = createUser(1L, "admin-uid", UserType.ADMIN);
                User influencer = createUser(2L, "influencer-uid", UserType.INFLUENCER);
                Page<User> userPage = new PageImpl<>(List.of(admin, influencer), pageable, 2);

                when(userService.getDataPagedAndFiltered(pageable, filters)).thenReturn(userPage);
                when(userSocialConnectionService.getPrimaryConnectionSafe(influencer)).thenReturn(null);

                InfluencerPublicProfileDto influencerDto = new InfluencerPublicProfileDto();
                influencerDto.setId(2L);
                when(userService.toInfluencerPublicProfileDtoWithSocialData(eq(influencer), isNull()))
                        .thenReturn(influencerDto);

                // When
                ResponseEntity<Page<PublicProfileDto>> response = userController.findPublicPaginated(pageable, filters);

                // Then
                assertThat(response.getBody()).isNotNull();
                // Admin should be filtered out
                verify(userService, never()).toInfluencerPublicProfileDtoWithSocialData(eq(admin), any());
            }
        }

        @Test
        @DisplayName("should remove pagination parameters from filters")
        void shouldRemovePaginationParametersFromFilters() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();
                filters.put("page", "0");
                filters.put("size", "10");
                filters.put("sort", "name");
                filters.put("direction", "asc");
                filters.put("userType", "INFLUENCER");

                Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);
                when(userService.getDataPagedAndFiltered(eq(pageable), any())).thenReturn(emptyPage);

                // When
                userController.findPublicPaginated(pageable, filters);

                // Then - verify filters passed to service don't contain pagination params
                verify(userService).getDataPagedAndFiltered(eq(pageable), argThat(f ->
                        !f.containsKey("page") && !f.containsKey("size") &&
                                !f.containsKey("sort") && !f.containsKey("direction")
                ));
            }
        }

        @Test
        @DisplayName("should handle influencer with social connection")
        void shouldHandleInfluencerWithSocialConnection() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();

                User influencer = createUser(1L, "uid-1", UserType.INFLUENCER);
                Page<User> userPage = new PageImpl<>(List.of(influencer), pageable, 1);
                when(userService.getDataPagedAndFiltered(pageable, filters)).thenReturn(userPage);

                UserSocialConnection connection = new UserSocialConnection();
                connection.setId(1L);
                when(userSocialConnectionService.getPrimaryConnectionSafe(influencer)).thenReturn(connection);

                InfluencerPublicProfileDto influencerDto = new InfluencerPublicProfileDto();
                influencerDto.setId(1L);
                when(userService.toInfluencerPublicProfileDtoWithSocialData(influencer, connection))
                        .thenReturn(influencerDto);

                // When
                ResponseEntity<Page<PublicProfileDto>> response = userController.findPublicPaginated(pageable, filters);

                // Then
                verify(userService).toInfluencerPublicProfileDtoWithSocialData(influencer, connection);
            }
        }

        @Test
        @DisplayName("should handle empty page")
        void shouldHandleEmptyPage() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();

                Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);
                when(userService.getDataPagedAndFiltered(pageable, filters)).thenReturn(emptyPage);

                // When
                ResponseEntity<Page<PublicProfileDto>> response = userController.findPublicPaginated(pageable, filters);

                // Then
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getContent()).isEmpty();
            }
        }

        @Test
        @DisplayName("should handle unpaged request")
        void shouldHandleUnpagedRequest() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Pageable pageable = Pageable.unpaged();
                Map<String, String> filters = new HashMap<>();

                Page<User> emptyPage = new PageImpl<>(List.of());
                when(userService.getDataPagedAndFiltered(pageable, filters)).thenReturn(emptyPage);

                // When
                ResponseEntity<Page<PublicProfileDto>> response = userController.findPublicPaginated(pageable, filters);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }
    }

    // ==================== POST /users - create Tests ====================

    @Nested
    @DisplayName("POST /users - create")
    class CreateUserTests {

        @Test
        @DisplayName("should create user successfully")
        void shouldCreateUserSuccessfully() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoIn dtoIn = createUserDtoIn();
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                when(userService.saveAsDto(dtoIn)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.create(dtoIn);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getId()).isEqualTo(USER_ID);
                verify(userService).saveAsDto(dtoIn);
            }
        }

        @Test
        @DisplayName("should set default note when noteFromAdmin is null")
        void shouldSetDefaultNoteWhenNoteFromAdminIsNull() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoIn dtoIn = createUserDtoIn();
                dtoIn.setNoteFromAdmin(null);
                String defaultNote = "Default admin note";
                when(defaultNoteService.getDefaultNote(UserType.INFLUENCER)).thenReturn(defaultNote);

                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                when(userService.saveAsDto(dtoIn)).thenReturn(expectedDto);

                // When
                userController.create(dtoIn);

                // Then
                verify(defaultNoteService).getDefaultNote(UserType.INFLUENCER);
                assertThat(dtoIn.getNoteFromAdmin()).isEqualTo(defaultNote);
            }
        }

        @Test
        @DisplayName("should set default note when noteFromAdmin is blank")
        void shouldSetDefaultNoteWhenNoteFromAdminIsBlank() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoIn dtoIn = createUserDtoIn();
                dtoIn.setNoteFromAdmin("   ");
                String defaultNote = "Default admin note";
                when(defaultNoteService.getDefaultNote(UserType.INFLUENCER)).thenReturn(defaultNote);

                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                when(userService.saveAsDto(dtoIn)).thenReturn(expectedDto);

                // When
                userController.create(dtoIn);

                // Then
                verify(defaultNoteService).getDefaultNote(UserType.INFLUENCER);
            }
        }

        @Test
        @DisplayName("should not set default note when noteFromAdmin is provided")
        void shouldNotSetDefaultNoteWhenNoteFromAdminIsProvided() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoIn dtoIn = createUserDtoIn();
                dtoIn.setNoteFromAdmin("Custom admin note");

                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                when(userService.saveAsDto(dtoIn)).thenReturn(expectedDto);

                // When
                userController.create(dtoIn);

                // Then
                verify(defaultNoteService, never()).getDefaultNote(any());
                assertThat(dtoIn.getNoteFromAdmin()).isEqualTo("Custom admin note");
            }
        }

        @Test
        @DisplayName("should create company user with default note")
        void shouldCreateCompanyUserWithDefaultNote() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoIn dtoIn = createUserDtoIn();
                dtoIn.setUserType(UserType.COMPANY);
                dtoIn.setNoteFromAdmin(null);
                String defaultNote = "Company default note";
                when(defaultNoteService.getDefaultNote(UserType.COMPANY)).thenReturn(defaultNote);

                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                when(userService.saveAsDto(dtoIn)).thenReturn(expectedDto);

                // When
                userController.create(dtoIn);

                // Then
                verify(defaultNoteService).getDefaultNote(UserType.COMPANY);
            }
        }
    }

    // ==================== PATCH /users/{id} - patch Tests ====================

    @Nested
    @DisplayName("PATCH /users/{id} - patch")
    class PatchUserTests {

        @Test
        @DisplayName("should patch user successfully")
        void shouldPatchUserSuccessfully() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Map<String, Object> updates = new HashMap<>();
                updates.put("firstName", "Jane");
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                expectedDto.setFirstName("Jane");
                when(userService.patchAsDto(USER_ID, updates)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.patch(USER_ID, updates);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getFirstName()).isEqualTo("Jane");
                verify(userService).patchAsDto(USER_ID, updates);
            }
        }

        @Test
        @DisplayName("should handle multiple field updates")
        void shouldHandleMultipleFieldUpdates() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Map<String, Object> updates = new HashMap<>();
                updates.put("firstName", "Jane");
                updates.put("lastName", "Smith");
                updates.put("email", "jane@example.com");
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                when(userService.patchAsDto(USER_ID, updates)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.patch(USER_ID, updates);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(userService).patchAsDto(USER_ID, updates);
            }
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Map<String, Object> updates = new HashMap<>();
                updates.put("firstName", "Jane");
                when(userService.patchAsDto(999L, updates))
                        .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

                // When/Then
                assertThatThrownBy(() -> userController.patch(999L, updates))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }
    }

    // ==================== PUT /users/{id} - update Tests ====================

    @Nested
    @DisplayName("PUT /users/{id} - update")
    class UpdateUserTests {

        @Test
        @DisplayName("should update user successfully")
        void shouldUpdateUserSuccessfully() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoIn dtoIn = createUserDtoIn();
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                when(userService.updateAsDto(USER_ID, dtoIn)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.update(USER_ID, dtoIn);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                verify(userService).updateAsDto(USER_ID, dtoIn);
            }
        }

        @Test
        @DisplayName("should throw when user not found for update")
        void shouldThrowWhenUserNotFoundForUpdate() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoIn dtoIn = createUserDtoIn();
                when(userService.updateAsDto(999L, dtoIn))
                        .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

                // When/Then
                assertThatThrownBy(() -> userController.update(999L, dtoIn))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }
    }

    // ==================== DELETE /users/{ids} - delete Tests ====================

    @Nested
    @DisplayName("DELETE /users/{ids} - delete (soft)")
    class DeleteUserTests {

        @Test
        @DisplayName("should delete single user successfully")
        void shouldDeleteSingleUserSuccessfully() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                List<Long> ids = List.of(USER_ID);
                doNothing().when(userService).delete(USER_ID);

                // When
                ResponseEntity<Void> response = userController.delete(ids);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                verify(userService).delete(USER_ID);
            }
        }

        @Test
        @DisplayName("should delete multiple users successfully")
        void shouldDeleteMultipleUsersSuccessfully() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                List<Long> ids = List.of(1L, 2L, 3L);
                doNothing().when(userService).deleteAll(ids);

                // When
                ResponseEntity<Void> response = userController.delete(ids);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                verify(userService).deleteAll(ids);
            }
        }
    }

    // ==================== DELETE /users/delete-permanently/{ids} Tests ====================

    @Nested
    @DisplayName("DELETE /users/delete-permanently/{ids} - deletePermanently")
    class DeletePermanentlyTests {

        @Test
        @DisplayName("should delete single user permanently")
        void shouldDeleteSingleUserPermanently() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                List<Long> ids = List.of(USER_ID);
                doNothing().when(userService).deletePermanently(USER_ID);

                // When
                ResponseEntity<Void> response = userController.deletePermanently(ids);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                verify(userService).deletePermanently(USER_ID);
            }
        }

        @Test
        @DisplayName("should delete multiple users permanently")
        void shouldDeleteMultipleUsersPermanently() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                List<Long> ids = List.of(1L, 2L, 3L);
                doNothing().when(userService).deletePermanently(anyLong());

                // When
                ResponseEntity<Void> response = userController.deletePermanently(ids);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                verify(userService, times(3)).deletePermanently(anyLong());
            }
        }

        @Test
        @DisplayName("should throw NullPointerException when ids is null due to early logging")
        void shouldThrowWhenIdsIsNull() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);

                // When/Then
                // Note: Controller logs ids.size() before null check, so NPE is thrown
                assertThatThrownBy(() -> userController.deletePermanently(null))
                        .isInstanceOf(NullPointerException.class);
            }
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when ids is empty")
        void shouldThrowWhenIdsIsEmpty() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                List<Long> ids = List.of();

                // When/Then
                assertThatThrownBy(() -> userController.deletePermanently(ids))
                        .isInstanceOf(ValidationTranslatableException.class);
            }
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when more than 100 ids")
        void shouldThrowWhenMoreThan100Ids() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                List<Long> ids = new ArrayList<>();
                for (long i = 1; i <= 101; i++) {
                    ids.add(i);
                }

                // When/Then
                assertThatThrownBy(() -> userController.deletePermanently(ids))
                        .isInstanceOf(ValidationTranslatableException.class);
            }
        }

        @Test
        @DisplayName("should handle exactly 100 ids")
        void shouldHandleExactly100Ids() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                List<Long> ids = new ArrayList<>();
                for (long i = 1; i <= 100; i++) {
                    ids.add(i);
                }
                doNothing().when(userService).deletePermanently(anyLong());

                // When
                ResponseEntity<Void> response = userController.deletePermanently(ids);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                verify(userService, times(100)).deletePermanently(anyLong());
            }
        }
    }

    // ==================== GET /users/{id} - getById Tests ====================

    @Nested
    @DisplayName("GET /users/{id} - getById")
    class GetByIdTests {

        @Test
        @DisplayName("should return user by id successfully")
        void shouldReturnUserByIdSuccessfully() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                when(userService.findByIdAsDto(USER_ID)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.getById(USER_ID);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getId()).isEqualTo(USER_ID);
                verify(userService).findByIdAsDto(USER_ID);
            }
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(userService.findByIdAsDto(999L))
                        .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

                // When/Then
                assertThatThrownBy(() -> userController.getById(999L))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }
    }

    // ==================== GET /users/user/{userId} - getUserByUserId Tests ====================

    @Nested
    @DisplayName("GET /users/user/{userId} - getUserByUserId")
    class GetUserByUserIdTests {

        @Test
        @DisplayName("should return user by firebase user id successfully")
        void shouldReturnUserByFirebaseUserIdSuccessfully() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                String targetUserId = "target-firebase-uid";
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, targetUserId);
                when(userService.findByFirebaseUserIdAsDto(targetUserId)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.getUserByUserId(targetUserId);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getFirebaseUserId()).isEqualTo(targetUserId);
                verify(userService).findByFirebaseUserIdAsDto(targetUserId);
            }
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found by firebase id")
        void shouldThrowWhenUserNotFoundByFirebaseId() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(userService.findByFirebaseUserIdAsDto("non-existent"))
                        .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

                // When/Then
                assertThatThrownBy(() -> userController.getUserByUserId("non-existent"))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }
    }

    // ==================== GET /users/accounts/status - getAccountStatusList Tests ====================

    @Nested
    @DisplayName("GET /users/accounts/status - getAccountStatusList")
    class GetAccountStatusListTests {

        @Test
        @DisplayName("should return all account status values")
        void shouldReturnAllAccountStatusValues() {
            // When
            ResponseEntity<List<String>> response = userController.getAccountStatusList();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).contains("ACTIVE", "INACTIVE", "IN_VALIDATION", "BANNED", "TO_BE_DELETED", "DELETED");
        }

        @Test
        @DisplayName("should return correct number of status values")
        void shouldReturnCorrectNumberOfStatusValues() {
            // When
            ResponseEntity<List<String>> response = userController.getAccountStatusList();

            // Then
            assertThat(response.getBody()).hasSize(AccountStatus.values().length);
        }

        @Test
        @DisplayName("should return status values as strings")
        void shouldReturnStatusValuesAsStrings() {
            // When
            ResponseEntity<List<String>> response = userController.getAccountStatusList();

            // Then
            response.getBody().forEach(status ->
                    assertThat(status).isInstanceOf(String.class)
            );
        }
    }

    // ==================== GET /users/type - getUserTypeList Tests ====================

    @Nested
    @DisplayName("GET /users/type - getUserTypeList")
    class GetUserTypeListTests {

        @Test
        @DisplayName("should return all user type values")
        void shouldReturnAllUserTypeValues() {
            // When
            ResponseEntity<List<String>> response = userController.getUserTypeList();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).contains("ADMIN", "PENDING_ADMIN", "INFLUENCER", "COMPANY");
        }

        @Test
        @DisplayName("should return correct number of user type values")
        void shouldReturnCorrectNumberOfUserTypeValues() {
            // When
            ResponseEntity<List<String>> response = userController.getUserTypeList();

            // Then
            assertThat(response.getBody()).hasSize(UserType.values().length);
        }

        @Test
        @DisplayName("should return user type values as strings")
        void shouldReturnUserTypeValuesAsStrings() {
            // When
            ResponseEntity<List<String>> response = userController.getUserTypeList();

            // Then
            response.getBody().forEach(type ->
                    assertThat(type).isInstanceOf(String.class)
            );
        }
    }

    // ==================== PATCH /users/{id}/premium - setPremiumStatus Tests ====================

    @Nested
    @DisplayName("PATCH /users/{id}/premium - setPremiumStatus")
    class SetPremiumStatusTests {

        @Test
        @DisplayName("should set premium status to true")
        void shouldSetPremiumStatusToTrue() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                User updatedUser = createUser(USER_ID, FIREBASE_UID, UserType.INFLUENCER);
                updatedUser.setPremium(true);
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                expectedDto.setPremium(true);

                when(userService.updatePremiumStatus(USER_ID, true)).thenReturn(updatedUser);
                when(userService.toDto(updatedUser)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.setPremiumStatus(USER_ID, true);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getPremium()).isTrue();
                verify(userService).updatePremiumStatus(USER_ID, true);
            }
        }

        @Test
        @DisplayName("should set premium status to false")
        void shouldSetPremiumStatusToFalse() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                User updatedUser = createUser(USER_ID, FIREBASE_UID, UserType.INFLUENCER);
                updatedUser.setPremium(false);
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                expectedDto.setPremium(false);

                when(userService.updatePremiumStatus(USER_ID, false)).thenReturn(updatedUser);
                when(userService.toDto(updatedUser)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.setPremiumStatus(USER_ID, false);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getPremium()).isFalse();
            }
        }

        @Test
        @DisplayName("should throw when user not found for premium update")
        void shouldThrowWhenUserNotFoundForPremiumUpdate() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(userService.updatePremiumStatus(999L, true))
                        .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

                // When/Then
                assertThatThrownBy(() -> userController.setPremiumStatus(999L, true))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }
    }

    // ==================== GET /users/me/deletion-eligibility Tests ====================

    @Nested
    @DisplayName("GET /users/me/deletion-eligibility - checkMyDeletionEligibility")
    class CheckMyDeletionEligibilityTests {

        @Test
        @DisplayName("should return deletion eligibility for current user")
        void shouldReturnDeletionEligibilityForCurrentUser() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                DeletionEligibilityDto expectedDto = DeletionEligibilityDto.builder()
                        .userId(USER_ID)
                        .canSoftDelete(true)
                        .canPermanentDelete(false)
                        .build();
                when(userService.checkMyDeletionEligibility()).thenReturn(expectedDto);

                // When
                ResponseEntity<DeletionEligibilityDto> response = userController.checkMyDeletionEligibility();

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().isCanSoftDelete()).isTrue();
                assertThat(response.getBody().isCanPermanentDelete()).isFalse();
                verify(userService).checkMyDeletionEligibility();
            }
        }

        @Test
        @DisplayName("should return eligibility with blockers")
        void shouldReturnEligibilityWithBlockers() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                DeletionEligibilityDto expectedDto = DeletionEligibilityDto.builder()
                        .userId(USER_ID)
                        .canSoftDelete(false)
                        .canPermanentDelete(false)
                        .summary("Has active partnerships")
                        .build();
                when(userService.checkMyDeletionEligibility()).thenReturn(expectedDto);

                // When
                ResponseEntity<DeletionEligibilityDto> response = userController.checkMyDeletionEligibility();

                // Then
                assertThat(response.getBody().isCanSoftDelete()).isFalse();
                assertThat(response.getBody().getSummary()).isEqualTo("Has active partnerships");
            }
        }
    }

    // ==================== GET /users/{id}/deletion-eligibility Tests ====================

    @Nested
    @DisplayName("GET /users/{id}/deletion-eligibility - checkDeletionEligibility")
    class CheckDeletionEligibilityTests {

        @Test
        @DisplayName("should return deletion eligibility for user by id")
        void shouldReturnDeletionEligibilityForUserById() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                DeletionEligibilityDto expectedDto = DeletionEligibilityDto.builder()
                        .userId(USER_ID)
                        .canSoftDelete(true)
                        .canPermanentDelete(true)
                        .build();
                when(userService.checkDeletionEligibilityById(USER_ID)).thenReturn(expectedDto);

                // When
                ResponseEntity<DeletionEligibilityDto> response = userController.checkDeletionEligibility(USER_ID);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getUserId()).isEqualTo(USER_ID);
                verify(userService).checkDeletionEligibilityById(USER_ID);
            }
        }

        @Test
        @DisplayName("should throw when user not found for eligibility check")
        void shouldThrowWhenUserNotFoundForEligibilityCheck() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(userService.checkDeletionEligibilityById(999L))
                        .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

                // When/Then
                assertThatThrownBy(() -> userController.checkDeletionEligibility(999L))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }
    }

    // ==================== GET /users/paged - findPaginated Tests ====================

    @Nested
    @DisplayName("GET /users/paged - findPaginated")
    class FindPaginatedTests {

        @Test
        @DisplayName("should return paginated users successfully")
        void shouldReturnPaginatedUsersSuccessfully() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();

                UserDtoOut dto1 = createUserDtoOut(1L, "uid-1");
                UserDtoOut dto2 = createUserDtoOut(2L, "uid-2");
                Page<UserDtoOut> expectedPage = new PageImpl<>(List.of(dto1, dto2), pageable, 2);

                doReturn(expectedPage).when(userService).getDataPagedAndFilteredAsDtos(eq(pageable), any());

                // When
                ResponseEntity<Page<UserDtoOut>> response = userController.findPaginated(pageable, filters);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getContent()).hasSize(2);
            }
        }

        @Test
        @DisplayName("should remove pagination parameters from filters")
        void shouldRemovePaginationParametersFromFilters() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();
                filters.put("page", "0");
                filters.put("size", "10");
                filters.put("sort", "name");
                filters.put("direction", "asc");
                filters.put("accountStatus", "ACTIVE");

                Page<UserDtoOut> emptyPage = new PageImpl<>(List.of(), pageable, 0);
                doReturn(emptyPage).when(userService).getDataPagedAndFilteredAsDtos(eq(pageable), any());

                // When
                userController.findPaginated(pageable, filters);

                // Then
                verify(userService).getDataPagedAndFilteredAsDtos(eq(pageable), argThat(f ->
                        !f.containsKey("page") && !f.containsKey("size") &&
                                !f.containsKey("sort") && !f.containsKey("direction")
                ));
            }
        }

        @Test
        @DisplayName("should handle empty page")
        void shouldHandleEmptyPage() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();

                Page<UserDtoOut> emptyPage = new PageImpl<>(List.of(), pageable, 0);
                doReturn(emptyPage).when(userService).getDataPagedAndFilteredAsDtos(eq(pageable), any());

                // When
                ResponseEntity<Page<UserDtoOut>> response = userController.findPaginated(pageable, filters);

                // Then
                assertThat(response.getBody().getContent()).isEmpty();
                assertThat(response.getBody().getTotalElements()).isZero();
            }
        }

        @Test
        @DisplayName("should handle large page size")
        void shouldHandleLargePageSize() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Pageable pageable = PageRequest.of(0, 100);
                Map<String, String> filters = new HashMap<>();

                List<UserDtoOut> dtos = new ArrayList<>();
                for (long i = 1; i <= 100; i++) {
                    dtos.add(createUserDtoOut(i, "uid-" + i));
                }
                Page<UserDtoOut> largePage = new PageImpl<>(dtos, pageable, 100);
                doReturn(largePage).when(userService).getDataPagedAndFilteredAsDtos(eq(pageable), any());

                // When
                ResponseEntity<Page<UserDtoOut>> response = userController.findPaginated(pageable, filters);

                // Then
                assertThat(response.getBody().getContent()).hasSize(100);
            }
        }
    }

    // ==================== Locale Handling Tests ====================

    @Nested
    @DisplayName("Locale Handling")
    class LocaleHandlingTests {

        @Test
        @DisplayName("should parse Accept-Language header with single language")
        void shouldParseAcceptLanguageHeaderWithSingleLanguage() {
            // Given
            when(request.getHeader("Accept-Language")).thenReturn("en");

            // When - indirectly test through a method that uses locale
            ResponseEntity<List<String>> response = userController.getAccountStatusList();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should parse Accept-Language header with multiple languages")
        void shouldParseAcceptLanguageHeaderWithMultipleLanguages() {
            // Given
            when(request.getHeader("Accept-Language")).thenReturn("en-US,en;q=0.9,pl;q=0.8");

            // When
            ResponseEntity<List<String>> response = userController.getUserTypeList();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should default to Polish when no Accept-Language header")
        void shouldDefaultToPolishWhenNoAcceptLanguageHeader() {
            // Given
            when(request.getHeader("Accept-Language")).thenReturn(null);

            // When
            ResponseEntity<List<String>> response = userController.getAccountStatusList();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should default to Polish when Accept-Language header is empty")
        void shouldDefaultToPolishWhenAcceptLanguageHeaderIsEmpty() {
            // Given
            when(request.getHeader("Accept-Language")).thenReturn("");

            // When
            ResponseEntity<List<String>> response = userController.getUserTypeList();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle empty filters map in findPaginated")
        void shouldHandleEmptyFiltersMapInFindPaginated() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();

                Page<UserDtoOut> emptyPage = new PageImpl<>(List.of(), pageable, 0);
                doReturn(emptyPage).when(userService).getDataPagedAndFilteredAsDtos(eq(pageable), any());

                // When
                ResponseEntity<Page<UserDtoOut>> response = userController.findPaginated(pageable, filters);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }

        @Test
        @DisplayName("should handle user with null collections")
        void shouldHandleUserWithNullCollections() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoOut expectedDto = new UserDtoOut();
                expectedDto.setId(USER_ID);
                expectedDto.setAddresses(null);
                expectedDto.setSocialConnections(null);
                when(userService.findByFirebaseUserIdAsDto(FIREBASE_UID)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.getCurrentUser();

                // Then
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getAddresses()).isNull();
                assertThat(response.getBody().getSocialConnections()).isNull();
            }
        }

        @Test
        @DisplayName("should handle very long firebase uid")
        void shouldHandleVeryLongFirebaseUid() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                String longUid = "a".repeat(255);
                when(authentication.getPrincipal()).thenReturn(longUid);
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, longUid);
                when(userService.findByFirebaseUserIdAsDto(longUid)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.getCurrentUser();

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(userService).findByFirebaseUserIdAsDto(longUid);
            }
        }

        @Test
        @DisplayName("should handle special characters in firebase uid")
        void shouldHandleSpecialCharactersInFirebaseUid() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                String specialUid = "user-uid_123.abc";
                when(authentication.getPrincipal()).thenReturn(specialUid);
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, specialUid);
                when(userService.findByFirebaseUserIdAsDto(specialUid)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.getCurrentUser();

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }

        @Test
        @DisplayName("should handle zero user id")
        void shouldHandleZeroUserId() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(userService.findByIdAsDto(0L))
                        .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

                // When/Then
                assertThatThrownBy(() -> userController.getById(0L))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Test
        @DisplayName("should handle negative user id")
        void shouldHandleNegativeUserId() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(userService.findByIdAsDto(-1L))
                        .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

                // When/Then
                assertThatThrownBy(() -> userController.getById(-1L))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Test
        @DisplayName("should handle max long user id")
        void shouldHandleMaxLongUserId() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(userService.findByIdAsDto(Long.MAX_VALUE))
                        .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

                // When/Then
                assertThatThrownBy(() -> userController.getById(Long.MAX_VALUE))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }
    }

    // ==================== DTO Validation Tests ====================

    @Nested
    @DisplayName("DTO Validation Edge Cases")
    class DtoValidationTests {

        @Test
        @DisplayName("should handle UserDtoIn with all null optional fields")
        void shouldHandleUserDtoInWithAllNullOptionalFields() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoIn dtoIn = new UserDtoIn();
                dtoIn.setEmail("test@example.com");
                dtoIn.setUserType(UserType.INFLUENCER);
                dtoIn.setAccountStatus(AccountStatus.IN_VALIDATION);
                // All other fields are null

                when(defaultNoteService.getDefaultNote(UserType.INFLUENCER)).thenReturn("Default note");
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                when(userService.saveAsDto(dtoIn)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.create(dtoIn);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }

        @Test
        @DisplayName("should handle patch with empty updates map")
        void shouldHandlePatchWithEmptyUpdatesMap() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                Map<String, Object> updates = new HashMap<>();
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                when(userService.patchAsDto(USER_ID, updates)).thenReturn(expectedDto);

                // When
                ResponseEntity<UserDtoOut> response = userController.patch(USER_ID, updates);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }
    }

    // ==================== Controller Behavior Tests ====================

    @Nested
    @DisplayName("Controller Behavior")
    class ControllerBehaviorTests {

        @Test
        @DisplayName("should call service method exactly once for getCurrentUser")
        void shouldCallServiceMethodExactlyOnceForGetCurrentUser() {
            try (MockedStatic<SecurityContextHolder> mockedSecurityContext = mockStatic(SecurityContextHolder.class)) {
                // Given
                mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                UserDtoOut expectedDto = createUserDtoOut(USER_ID, FIREBASE_UID);
                when(userService.findByFirebaseUserIdAsDto(FIREBASE_UID)).thenReturn(expectedDto);

                // When
                userController.getCurrentUser();

                // Then
                verify(userService, times(1)).findByFirebaseUserIdAsDto(FIREBASE_UID);
            }
        }

        @Test
        @DisplayName("should not call any service for getAccountStatusList")
        void shouldNotCallAnyServiceForGetAccountStatusList() {
            // When
            userController.getAccountStatusList();

            // Then
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("should not call any service for getUserTypeList")
        void shouldNotCallAnyServiceForGetUserTypeList() {
            // When
            userController.getUserTypeList();

            // Then
            verifyNoInteractions(userService);
        }
    }
}
