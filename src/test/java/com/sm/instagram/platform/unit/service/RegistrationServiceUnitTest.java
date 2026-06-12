package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.dto.RegisterUserRequest;
import com.sm.instagram.platform.auth.dto.RegistrationResponse;
import com.sm.instagram.platform.auth.firebase.FirebaseService;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.service.RegistrationService;
import com.sm.instagram.platform.auth.session.SessionData;
import com.sm.instagram.platform.auth.session.SocialAuthSessionService;
import com.sm.instagram.platform.common.authorization.Permission;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformRepository;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.sm.instagram.platform.legal.LegalConsentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RegistrationService.
 * Tests both email/password registration and social OAuth registration flows.
 * Uses pure Mockito without Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RegistrationService Unit Tests")
class RegistrationServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FirebaseService firebaseService;

    @Mock
    private FirestoreService firestoreService;

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private UserSocialConnectionRepository socialConnectionRepository;

    @Mock
    private SocialAuthSessionService sessionService;

    @Mock
    private com.sm.instagram.platform.auth.service.EmailVerificationService emailVerificationService;

    @Mock
    private com.sm.instagram.platform.userpreferences.UserPreferencesService userPreferencesService;

    @Mock
    private LegalConsentService legalConsentService;

    @Mock
    private HttpServletRequest mockHttpRequest;

    @Mock
    private HttpServletResponse mockHttpResponse;

    @Mock
    private com.sm.instagram.platform.support.common.EmailService emailService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RegistrationService registrationService;

    private RegisterUserRequest createValidInfluencerRequest() {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setEmail("test@example.com");
        request.setPassword("Password1!");
        request.setUserType("INFLUENCER");
        request.setFirstName("John");
        request.setLastName("Doe");
        return request;
    }

    private RegisterUserRequest createValidCompanyRequest() {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setEmail("company@example.com");
        request.setPassword("Password1!");
        request.setUserType("COMPANY");
        request.setFirstName("Jane");
        request.setLastName("Smith");
        request.setCompanyName("Test Company");
        request.setPhoneNumber("+48123456789");
        request.setAddressStreet("Main Street 123");
        request.setAddressCity("Warsaw");
        request.setAddressPostalCode("00-001");
        request.setAddressCountry("Poland");
        request.setAddressState("Mazowieckie");
        return request;
    }

    private Map<String, Object> createFirebaseUserResponse(String uid, String email, boolean existed) {
        Map<String, Object> response = new HashMap<>();
        response.put("uid", uid);
        response.put("email", email);
        response.put("displayName", "Test User");
        response.put("existed", existed);
        return response;
    }

    private User createSavedUser(Long id, String firebaseUid, UserType userType) {
        User user = new User();
        user.setId(id);
        user.setFirebaseUserId(firebaseUid);
        user.setUserType(userType);
        user.setEmail("test@example.com");
        user.setAccountStatus(AccountStatus.IN_VALIDATION);
        return user;
    }

    private SessionData createValidSessionData(String platform) {
        Map<String, Object> socialData = new HashMap<>();
        socialData.put("user_id", "12345678");
        socialData.put("username", "testuser");
        socialData.put("profile_picture_url", "https://example.com/pic.jpg");
        socialData.put("followers_count", 1000);

        return SessionData.builder()
                .socialData(socialData)
                .platform(platform)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    private Platform createPlatform(Long id, String name) {
        Platform platform = new Platform();
        platform.setId(id);
        platform.setName(name);
        platform.setActive(true);
        return platform;
    }

    @Nested
    @DisplayName("registerUser - Email/Password Registration")
    class RegisterUserTests {

        @BeforeEach
        void setUp() {
            // Reset mocks before each test
            reset(userRepository, firebaseService, firestoreService);
        }

        @Test
        @DisplayName("should successfully register influencer user")
        void shouldSuccessfullyRegisterInfluencerUser() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("firebase-uid-123", request.getEmail(), false);
            User savedUser = createSavedUser(1L, "firebase-uid-123", UserType.INFLUENCER);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.INFLUENCER)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // When
            RegistrationResponse response = registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getFirebaseUid()).isEqualTo("firebase-uid-123");
            assertThat(response.getUserType()).isEqualTo("INFLUENCER");
            verify(userRepository).save(any(User.class));
            verify(emailService).sendNewUserAdminNotification(anyString(), eq("test@example.com"), eq("INFLUENCER"));
        }

        @Test
        @DisplayName("should successfully register company user with address")
        void shouldSuccessfullyRegisterCompanyUserWithAddress() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("firebase-uid-456", request.getEmail(), false);
            User savedUser = createSavedUser(2L, "firebase-uid-456", UserType.COMPANY);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.COMPANY)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("firebase-uid-456")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // When
            RegistrationResponse response = registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getUserType()).isEqualTo("COMPANY");

            // Verify user was saved with address
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User capturedUser = userCaptor.getValue();
            assertThat(capturedUser.getAddresses()).isNotEmpty();
            assertThat(capturedUser.getName()).isEqualTo("Test Company");
            verify(emailService).sendNewUserAdminNotification(anyString(), anyString(), eq("COMPANY"));
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when user already exists in database")
        void shouldThrowWhenUserAlreadyExistsInDatabase() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("existing-uid", request.getEmail(), true);
            User existingUser = createSavedUser(99L, "existing-uid", UserType.INFLUENCER);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.INFLUENCER)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("existing-uid")).thenReturn(Optional.of(existingUser));

            // When/Then
            // Note: The service catches BusinessRuleTranslatableException in generic catch block
            // and rethrows as "error.business.invalid_state"
            assertThatThrownBy(() -> registrationService.registerUser(request, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(BusinessRuleTranslatableException.class)
                    .hasMessageContaining("error.business.invalid_state");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("should rethrow DataIntegrityViolationException for duplicate email")
        void shouldRethrowDataIntegrityViolationException() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("firebase-uid-123", request.getEmail(), false);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.INFLUENCER)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("Duplicate email"));

            // When/Then
            assertThatThrownBy(() -> registrationService.registerUser(request, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException for Firebase service errors")
        void shouldThrowBusinessRuleExceptionForFirebaseErrors() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.INFLUENCER)
            )).thenThrow(new ExternalServiceException("Firebase error", "Firebase", "createUser", new RuntimeException()));

            // When/Then
            assertThatThrownBy(() -> registrationService.registerUser(request, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(BusinessRuleTranslatableException.class)
                    .hasMessageContaining("error.business.invalid_state");
        }

        @Test
        @DisplayName("should set user status to IN_VALIDATION on registration")
        void shouldSetUserStatusToInValidation() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("firebase-uid-123", request.getEmail(), false);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.INFLUENCER)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
        }

        @Test
        @DisplayName("should set profile picture URL when provided")
        void shouldSetProfilePictureUrlWhenProvided() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setProfilePictureUrl("https://example.com/profile.jpg");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("firebase-uid-123", request.getEmail(), false);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.INFLUENCER)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getProfilePicture()).isEqualTo("https://example.com/profile.jpg");
        }
    }

    @Nested
    @DisplayName("registerUser - Validation Tests")
    class RegisterUserValidationTests {

        @Test
        @DisplayName("should throw NullPointerException when request is null")
        void shouldThrowWhenRequestIsNull() {
            // When/Then
            // Note: The service accesses request.getEmail() in GDPR logging before validation,
            // causing NullPointerException rather than ValidationTranslatableException
            assertThatThrownBy(() -> registrationService.registerUser(null, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("should throw ValidationTranslatableException when email is invalid")
        void shouldThrowWhenEmailIsInvalid(String email) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setEmail(email);

            // When/Then
            assertThatThrownBy(() -> registrationService.registerUser(request, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.invalid_email");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"12345", "abc"})
        @DisplayName("should throw ValidationTranslatableException when password is too short")
        void shouldThrowWhenPasswordIsTooShort(String password) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setPassword(password);

            // When/Then
            assertThatThrownBy(() -> registrationService.registerUser(request, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.weak_password");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when userType is null")
        void shouldThrowWhenUserTypeIsNull() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setUserType(null);

            // When/Then
            assertThatThrownBy(() -> registrationService.registerUser(request, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.required_field");
        }

        @ParameterizedTest
        @ValueSource(strings = {"INVALID", "admin", "SuperUser", "MANAGER"})
        @DisplayName("should throw ValidationTranslatableException when userType is invalid")
        void shouldThrowWhenUserTypeIsInvalid(String userType) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setUserType(userType);

            // When/Then
            assertThatThrownBy(() -> registrationService.registerUser(request, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.type_mismatch");
        }
    }

    @Nested
    @DisplayName("registerUser - Display Name Building")
    class DisplayNameBuildingTests {

        @Test
        @DisplayName("should build display name from first and last name")
        void shouldBuildDisplayNameFromFirstAndLastName() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setFirstName("John");
            request.setLastName("Doe");

            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid", request.getEmail(), false);
            when(firebaseService.createOrValidateFirebaseUser(
                    anyString(), anyString(), eq("John Doe"), any()
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            verify(firebaseService).createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    eq("John Doe"),
                    eq(Permission.INFLUENCER)
            );
        }

        @Test
        @DisplayName("should build display name from first name only when last name is null")
        void shouldBuildDisplayNameFromFirstNameOnlyWhenLastNameIsNull() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setFirstName("John");
            request.setLastName(null);

            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid", request.getEmail(), false);
            when(firebaseService.createOrValidateFirebaseUser(
                    anyString(), anyString(), eq("John"), any()
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            verify(firebaseService).createOrValidateFirebaseUser(
                    anyString(), anyString(), eq("John"), any()
            );
        }

        @Test
        @DisplayName("should use company name for display name when first name is empty")
        void shouldUseCompanyNameWhenFirstNameIsEmpty() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setFirstName(null);
            request.setCompanyName("Acme Corp");

            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid", request.getEmail(), false);
            when(firebaseService.createOrValidateFirebaseUser(
                    anyString(), anyString(), eq("Acme Corp"), any()
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            verify(firebaseService).createOrValidateFirebaseUser(
                    anyString(), anyString(), eq("Acme Corp"), any()
            );
        }

        @Test
        @DisplayName("should use email prefix for display name when no name fields provided")
        void shouldUseEmailPrefixWhenNoNameFieldsProvided() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setFirstName(null);
            request.setLastName(null);
            request.setCompanyName(null);
            request.setEmail("john.doe@example.com");

            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid", request.getEmail(), false);
            when(firebaseService.createOrValidateFirebaseUser(
                    anyString(), anyString(), eq("john.doe"), any()
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            verify(firebaseService).createOrValidateFirebaseUser(
                    anyString(), anyString(), eq("john.doe"), any()
            );
        }

        @Test
        @DisplayName("should throw 400 when consent cookies are missing before Firebase call")
        void should_throw_400_when_consent_cookies_missing() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            doThrow(new ValidationTranslatableException("error.consent.missing_consents"))
                    .when(legalConsentService).validateConsentCookiesPresent(any(HttpServletRequest.class));

            // When / Then
            assertThatThrownBy(() -> registrationService.registerUser(request, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(ValidationTranslatableException.class);

            // Verify Firebase was NEVER called
            verifyNoInteractions(firebaseService);
        }
    }

    @Nested
    @DisplayName("completeSocialRegistration - OAuth Registration")
    class CompleteSocialRegistrationTests {

        @Test
        @DisplayName("should successfully complete Instagram social registration")
        void shouldSuccessfullyCompleteInstagramSocialRegistration() {
            // Given
            String sessionId = "session-123";
            String email = "social@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            Platform instagram = createPlatform(1L, "Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("firebase-social-uid", email, false);
            User savedUser = createSavedUser(10L, "firebase-social-uid", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(
                    eq(email), eq("testuser"), eq("Instagram"), eq("12345678")
            )).thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagram));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            RegistrationResponse response = registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getUserId()).isEqualTo(10L);
            assertThat(response.getFirebaseUid()).isEqualTo("firebase-social-uid");
            assertThat(response.getUserType()).isEqualTo("INFLUENCER");

            verify(firestoreService).storeInstagramUserData(eq(sessionData.getSocialData()), eq("firebase-social-uid"));
            verify(socialConnectionRepository).save(any(UserSocialConnection.class));
            verify(emailService).sendNewUserAdminNotification(anyString(), eq("social@example.com"), eq("INFLUENCER"));
        }

        @Test
        @DisplayName("should successfully complete non-Instagram social registration without Firestore")
        void shouldCompleteNonInstagramSocialRegistrationWithoutFirestore() {
            // Given
            String sessionId = "session-456";
            String email = "tiktok@example.com";
            SessionData sessionData = createValidSessionData("TikTok");
            Platform tiktok = createPlatform(2L, "TikTok");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("firebase-tiktok-uid", email, false);
            User savedUser = createSavedUser(11L, "firebase-tiktok-uid", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("TikTok", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(
                    eq(email), eq("testuser"), eq("TikTok"), eq("12345678")
            )).thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("TikTok")).thenReturn(Optional.of(tiktok));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            RegistrationResponse response = registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();

            // Verify Firestore NOT called for non-Instagram
            verify(firestoreService, never()).storeInstagramUserData(any(), any());
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when social account already registered")
        void shouldThrowWhenSocialAccountAlreadyRegistered() {
            // Given
            String sessionId = "session-789";
            String email = "existing@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            UserSocialConnection existingConnection = new UserSocialConnection();

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(existingConnection));

            // When/Then
            // Note: The service catches BusinessRuleTranslatableException in generic catch block
            // and rethrows as "error.business.invalid_state"
            assertThatThrownBy(() -> registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(BusinessRuleTranslatableException.class)
                    .hasMessageContaining("error.business.invalid_state");

            verify(firebaseService, never()).createSocialOnlyFirebaseUser(any(), any(), any(), any());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("should throw ValidationTranslatableException when email is invalid")
        void shouldThrowWhenEmailIsInvalidForSocialRegistration(String email) {
            // Given
            String sessionId = "session-invalid";
            SessionData sessionData = createValidSessionData("Instagram");

            // When/Then
            assertThatThrownBy(() -> registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.invalid_email");
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when platform not found")
        void shouldThrowWhenPlatformNotFound() {
            // Given
            String sessionId = "session-no-platform";
            String email = "test@example.com";
            SessionData sessionData = createValidSessionData("UnknownPlatform");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("firebase-uid", email, false);
            User savedUser = createSavedUser(12L, "firebase-uid", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("UnknownPlatform", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("UnknownPlatform")).thenReturn(Optional.empty());

            // When/Then
            // Note: ResourceNotFoundException is caught by generic catch block and rethrown
            // as BusinessRuleTranslatableException with "error.business.invalid_state"
            assertThatThrownBy(() -> registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(BusinessRuleTranslatableException.class)
                    .hasMessageContaining("error.business.invalid_state");
        }

        @Test
        @DisplayName("should rethrow DataIntegrityViolationException for duplicate social email")
        void shouldRethrowDataIntegrityViolationForSocialRegistration() {
            // Given
            String sessionId = "session-duplicate";
            String email = "duplicate@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("firebase-uid", email, false);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("Duplicate email"));

            // When/Then
            assertThatThrownBy(() -> registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("should continue registration even if Firestore fails")
        void shouldContinueRegistrationEvenIfFirestoreFails() {
            // Given
            String sessionId = "session-firestore-fail";
            String email = "firestore.fail@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            Platform instagram = createPlatform(1L, "Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("firebase-uid-ff", email, false);
            User savedUser = createSavedUser(13L, "firebase-uid-ff", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagram));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("Firestore error"))
                    .when(firestoreService).storeInstagramUserData(any(), any());

            // When
            RegistrationResponse response = registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then - Registration should still succeed despite Firestore failure
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException for Firebase social user creation errors")
        void shouldThrowBusinessRuleExceptionForFirebaseSocialErrors() {
            // Given
            String sessionId = "session-firebase-error";
            String email = "firebase.error@example.com";
            SessionData sessionData = createValidSessionData("Instagram");

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenThrow(new ExternalServiceException("Firebase error", "Firebase", "createSocialUser", new RuntimeException()));

            // When/Then
            assertThatThrownBy(() -> registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(BusinessRuleTranslatableException.class)
                    .hasMessageContaining("error.business.invalid_state");
        }
    }

    @Nested
    @DisplayName("completeSocialRegistration - Social Data Extraction")
    class SocialDataExtractionTests {

        @Test
        @DisplayName("should extract followers count as integer")
        void shouldExtractFollowersCountAsInteger() {
            // Given
            String sessionId = "session-int-followers";
            String email = "followers@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            sessionData.getSocialData().put("followers_count", 5000);
            Platform instagram = createPlatform(1L, "Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid", email, false);
            User savedUser = createSavedUser(14L, "uid", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagram));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<UserSocialConnection> connectionCaptor = ArgumentCaptor.forClass(UserSocialConnection.class);
            verify(socialConnectionRepository).save(connectionCaptor.capture());
            assertThat(connectionCaptor.getValue().getFollowersCount()).isEqualTo(5000);
        }

        @Test
        @DisplayName("should extract followers count as string")
        void shouldExtractFollowersCountAsString() {
            // Given
            String sessionId = "session-str-followers";
            String email = "followers.str@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            sessionData.getSocialData().put("followers_count", "2500");
            Platform instagram = createPlatform(1L, "Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-str", email, false);
            User savedUser = createSavedUser(15L, "uid-str", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagram));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<UserSocialConnection> connectionCaptor = ArgumentCaptor.forClass(UserSocialConnection.class);
            verify(socialConnectionRepository).save(connectionCaptor.capture());
            assertThat(connectionCaptor.getValue().getFollowersCount()).isEqualTo(2500);
        }

        @Test
        @DisplayName("should default followers count to zero for invalid string")
        void shouldDefaultFollowersCountToZeroForInvalidString() {
            // Given
            String sessionId = "session-invalid-followers";
            String email = "invalid.followers@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            sessionData.getSocialData().put("followers_count", "not-a-number");
            Platform instagram = createPlatform(1L, "Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-invalid", email, false);
            User savedUser = createSavedUser(16L, "uid-invalid", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagram));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<UserSocialConnection> connectionCaptor = ArgumentCaptor.forClass(UserSocialConnection.class);
            verify(socialConnectionRepository).save(connectionCaptor.capture());
            assertThat(connectionCaptor.getValue().getFollowersCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should default followers count to zero when not provided")
        void shouldDefaultFollowersCountToZeroWhenNotProvided() {
            // Given
            String sessionId = "session-no-followers";
            String email = "no.followers@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            sessionData.getSocialData().remove("followers_count");
            Platform instagram = createPlatform(1L, "Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-no", email, false);
            User savedUser = createSavedUser(17L, "uid-no", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagram));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<UserSocialConnection> connectionCaptor = ArgumentCaptor.forClass(UserSocialConnection.class);
            verify(socialConnectionRepository).save(connectionCaptor.capture());
            assertThat(connectionCaptor.getValue().getFollowersCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should set profile picture URL from social data")
        void shouldSetProfilePictureUrlFromSocialData() {
            // Given
            String sessionId = "session-profile-pic";
            String email = "profile.pic@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            sessionData.getSocialData().put("profile_picture_url", "https://cdn.instagram.com/pic.jpg");
            Platform instagram = createPlatform(1L, "Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-pic", email, false);
            User savedUser = createSavedUser(18L, "uid-pic", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagram));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, atLeast(1)).save(userCaptor.capture());
            assertThat(userCaptor.getAllValues().get(0).getProfilePicture()).isEqualTo("https://cdn.instagram.com/pic.jpg");

            ArgumentCaptor<UserSocialConnection> connectionCaptor = ArgumentCaptor.forClass(UserSocialConnection.class);
            verify(socialConnectionRepository).save(connectionCaptor.capture());
            assertThat(connectionCaptor.getValue().getProfilePictureUrl()).isEqualTo("https://cdn.instagram.com/pic.jpg");
        }
    }

    @Nested
    @DisplayName("Parameterized UserType Tests")
    class ParameterizedUserTypeTests {

        private static Stream<Arguments> validUserTypes() {
            return Stream.of(
                    Arguments.of("INFLUENCER", UserType.INFLUENCER, Permission.INFLUENCER),
                    Arguments.of("COMPANY", UserType.COMPANY, Permission.COMPANY)
            );
        }

        @ParameterizedTest
        @MethodSource("validUserTypes")
        @DisplayName("should handle valid user type registration")
        void shouldHandleValidUserTypeRegistration(String userTypeStr, UserType expectedUserType, Permission expectedPermission) {
            // Given
            RegisterUserRequest request = "COMPANY".equals(userTypeStr)
                    ? createValidCompanyRequest()
                    : createValidInfluencerRequest();
            request.setUserType(userTypeStr);

            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-" + userTypeStr, request.getEmail(), false);
            User savedUser = createSavedUser(100L, "uid-" + userTypeStr, expectedUserType);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(expectedPermission)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid-" + userTypeStr)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // When
            RegistrationResponse response = registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getUserType()).isEqualTo(expectedUserType.toString());

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getUserType()).isEqualTo(expectedUserType);
        }
    }

    @Nested
    @DisplayName("Address Creation Tests")
    class AddressCreationTests {

        @Test
        @DisplayName("should create address for company user with all fields")
        void shouldCreateAddressForCompanyUserWithAllFields() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setAddressInfo("Floor 5, Office 501");

            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-company", request.getEmail(), false);
            User savedUser = createSavedUser(200L, "uid-company", UserType.COMPANY);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.COMPANY)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid-company")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User capturedUser = userCaptor.getValue();

            assertThat(capturedUser.getAddresses()).hasSize(1);
            var address = capturedUser.getAddresses().get(0);
            assertThat(address.getStreet()).isEqualTo("Main Street 123");
            assertThat(address.getCity()).isEqualTo("Warsaw");
            assertThat(address.getPostalCode()).isEqualTo("00-001");
            assertThat(address.getCountry()).isEqualTo("Poland");
            assertThat(address.getState()).isEqualTo("Mazowieckie");
            assertThat(address.getAdditionalInfo()).isEqualTo("Floor 5, Office 501");
            assertThat(address.getAddressType()).isEqualTo("MAIN");
            assertThat(address.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("should not create address when required fields are missing")
        void shouldNotCreateAddressWhenRequiredFieldsAreMissing() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setAddressStreet(null); // Missing required field

            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-company-noaddr", request.getEmail(), false);
            User savedUser = createSavedUser(201L, "uid-company-noaddr", UserType.COMPANY);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.COMPANY)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid-company-noaddr")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getAddresses()).isEmpty();
        }

        @Test
        @DisplayName("should not create address for influencer user")
        void shouldNotCreateAddressForInfluencerUser() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setAddressStreet("Some Street");
            request.setAddressCity("Some City");

            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-influencer", request.getEmail(), false);
            User savedUser = createSavedUser(202L, "uid-influencer", UserType.INFLUENCER);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.INFLUENCER)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid-influencer")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            // Influencer should not have address even if address fields are provided
            assertThat(userCaptor.getValue().getAddresses()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Social Connection Creation Tests")
    class SocialConnectionCreationTests {

        @Test
        @DisplayName("should create social connection with correct attributes")
        void shouldCreateSocialConnectionWithCorrectAttributes() {
            // Given
            String sessionId = "session-connection";
            String email = "connection@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            Platform instagram = createPlatform(1L, "Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-conn", email, false);
            User savedUser = createSavedUser(300L, "uid-conn", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagram));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<UserSocialConnection> connectionCaptor = ArgumentCaptor.forClass(UserSocialConnection.class);
            verify(socialConnectionRepository).save(connectionCaptor.capture());
            UserSocialConnection connection = connectionCaptor.getValue();

            assertThat(connection.getUser()).isEqualTo(savedUser);
            assertThat(connection.getPlatform()).isEqualTo(instagram);
            assertThat(connection.getSocialUserId()).isEqualTo("12345678");
            assertThat(connection.getDisplayName()).isEqualTo("testuser");
            assertThat(connection.getIsPrimary()).isTrue();
            assertThat(connection.getLastSyncTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle whitespace-only first name")
        void shouldHandleWhitespaceOnlyFirstName() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setFirstName("   ");
            request.setLastName("Doe");

            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-ws", request.getEmail(), false);
            // When firstName is whitespace, should fall through to companyName or email
            when(firebaseService.createOrValidateFirebaseUser(
                    anyString(), anyString(), eq("test"), any()
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid-ws")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then - display name should be derived from email prefix (since whitespace firstName is treated as empty)
            verify(firebaseService).createOrValidateFirebaseUser(
                    anyString(), anyString(), eq("test"), any()
            );
        }

        @Test
        @DisplayName("should handle empty profile picture URL")
        void shouldHandleEmptyProfilePictureUrl() {
            // Given
            String sessionId = "session-empty-pic";
            String email = "empty.pic@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            sessionData.getSocialData().put("profile_picture_url", "");
            Platform instagram = createPlatform(1L, "Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-empty-pic", email, false);
            User savedUser = createSavedUser(400L, "uid-empty-pic", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagram));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, atLeast(1)).save(userCaptor.capture());
            assertThat(userCaptor.getAllValues().get(0).getProfilePicture()).isNull();
        }

        @Test
        @DisplayName("should handle null profile picture URL in social data")
        void shouldHandleNullProfilePictureUrlInSocialData() {
            // Given
            String sessionId = "session-null-pic";
            String email = "null.pic@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            sessionData.getSocialData().remove("profile_picture_url");
            Platform instagram = createPlatform(1L, "Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-null-pic", email, false);
            User savedUser = createSavedUser(401L, "uid-null-pic", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagram));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, atLeast(1)).save(userCaptor.capture());
            assertThat(userCaptor.getAllValues().get(0).getProfilePicture()).isNull();

            ArgumentCaptor<UserSocialConnection> connectionCaptor = ArgumentCaptor.forClass(UserSocialConnection.class);
            verify(socialConnectionRepository).save(connectionCaptor.capture());
            assertThat(connectionCaptor.getValue().getProfilePictureUrl()).isNull();
        }

        @Test
        @DisplayName("should rethrow IllegalArgumentException from validation")
        void shouldRethrowIllegalArgumentExceptionFromValidation() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-iae", request.getEmail(), false);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.INFLUENCER)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid-iae")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenThrow(new IllegalArgumentException("Invalid user data"));

            // When/Then
            assertThatThrownBy(() -> registrationService.registerUser(request, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid user data");
        }

        @Test
        @DisplayName("should rethrow IllegalArgumentException from social registration")
        void shouldRethrowIllegalArgumentExceptionFromSocialRegistration() {
            // Given
            String sessionId = "session-iae";
            String email = "iae@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-iae-social", email, false);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenThrow(new IllegalArgumentException("Invalid social data"));

            // When/Then
            assertThatThrownBy(() -> registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid social data");
        }
    }

    @Nested
    @DisplayName("Company-Specific Registration Tests")
    class CompanySpecificTests {

        @Test
        @DisplayName("should set company name for company user")
        void shouldSetCompanyNameForCompanyUser() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setCompanyName("Awesome Corp Ltd.");

            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-company-name", request.getEmail(), false);
            User savedUser = createSavedUser(500L, "uid-company-name", UserType.COMPANY);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.COMPANY)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid-company-name")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getName()).isEqualTo("Awesome Corp Ltd.");
        }

        @Test
        @DisplayName("should set phone number for company user")
        void shouldSetPhoneNumberForCompanyUser() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setPhoneNumber("+48987654321");

            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-company-phone", request.getEmail(), false);
            User savedUser = createSavedUser(501L, "uid-company-phone", UserType.COMPANY);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    anyString(),
                    eq(Permission.COMPANY)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid-company-phone")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPhoneNumber()).isEqualTo("+48987654321");
        }
    }

    @Nested
    @DisplayName("User Entity Creation Verification")
    class UserEntityCreationTests {

        @Test
        @DisplayName("should set all user fields correctly during registration")
        void shouldSetAllUserFieldsCorrectlyDuringRegistration() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setEmail("full.user@example.com");
            request.setFirstName("Alice");
            request.setLastName("Wonder");
            request.setProfilePictureUrl("https://example.com/alice.jpg");

            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-full", request.getEmail(), false);
            User savedUser = createSavedUser(600L, "uid-full", UserType.INFLUENCER);

            when(firebaseService.createOrValidateFirebaseUser(
                    eq(request.getEmail()),
                    eq(request.getPassword()),
                    eq("Alice Wonder"),
                    eq(Permission.INFLUENCER)
            )).thenReturn(firebaseResponse);
            when(userRepository.findByFirebaseUserId("uid-full")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // When
            registrationService.registerUser(request, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User capturedUser = userCaptor.getValue();

            assertThat(capturedUser.getFirebaseUserId()).isEqualTo("uid-full");
            assertThat(capturedUser.getEmail()).isEqualTo("full.user@example.com");
            assertThat(capturedUser.getFirstName()).isEqualTo("Alice");
            assertThat(capturedUser.getLastName()).isEqualTo("Wonder");
            assertThat(capturedUser.getUserType()).isEqualTo(UserType.INFLUENCER);
            assertThat(capturedUser.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
            assertThat(capturedUser.getProfilePicture()).isEqualTo("https://example.com/alice.jpg");
            assertThat(capturedUser.getCreatedTime()).isNotNull();
            assertThat(capturedUser.getLastUpdateTime()).isNotNull();
        }

        @Test
        @DisplayName("should create influencer user from social data correctly")
        void shouldCreateInfluencerUserFromSocialDataCorrectly() {
            // Given
            String sessionId = "session-influencer-create";
            String email = "social.influencer@example.com";
            SessionData sessionData = createValidSessionData("Instagram");
            sessionData.getSocialData().put("username", "social_star");
            sessionData.getSocialData().put("profile_picture_url", "https://instagram.com/pic.jpg");
            Platform instagram = createPlatform(1L, "Instagram");
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-social-inf", email, false);
            User savedUser = createSavedUser(601L, "uid-social-inf", UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagram));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, atLeast(1)).save(userCaptor.capture());
            User capturedUser = userCaptor.getAllValues().get(0);

            assertThat(capturedUser.getFirebaseUserId()).isEqualTo("uid-social-inf");
            assertThat(capturedUser.getEmail()).isEqualTo("social.influencer@example.com");
            assertThat(capturedUser.getName()).isEqualTo("social_star");
            assertThat(capturedUser.getUserType()).isEqualTo(UserType.INFLUENCER);
            assertThat(capturedUser.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
            assertThat(capturedUser.getProfilePicture()).isEqualTo("https://instagram.com/pic.jpg");
        }
    }

    @Nested
    @DisplayName("Platform Case Sensitivity Tests")
    class PlatformCaseSensitivityTests {

        @ParameterizedTest
        @ValueSource(strings = {"instagram", "INSTAGRAM", "Instagram", "InStAgRaM"})
        @DisplayName("should handle different Instagram casing for Firestore storage")
        void shouldHandleDifferentInstagramCasingForFirestoreStorage(String platformName) {
            // Given
            String sessionId = "session-case-" + platformName;
            String email = "case@example.com";
            SessionData sessionData = SessionData.builder()
                    .socialData(createValidSessionData("Instagram").getSocialData())
                    .platform(platformName)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .build();
            Platform instagram = createPlatform(1L, platformName);
            Map<String, Object> firebaseResponse = createFirebaseUserResponse("uid-case-" + platformName, email, false);
            User savedUser = createSavedUser(700L, "uid-case-" + platformName, UserType.INFLUENCER);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId(platformName, "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(any(), any(), any(), any()))
                    .thenReturn(firebaseResponse);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(platformRepository.findByName(platformName)).thenReturn(Optional.of(instagram));
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            registrationService.completeSocialRegistration(sessionId, email, sessionData, mockHttpRequest, mockHttpResponse);

            // Then - Firestore should be called regardless of case (equalsIgnoreCase is used)
            verify(firestoreService).storeInstagramUserData(any(), any());
        }
    }
}
