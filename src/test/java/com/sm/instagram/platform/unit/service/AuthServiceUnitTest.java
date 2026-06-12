package com.sm.instagram.platform.unit.service;

import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressRepository;
import com.sm.instagram.platform.auth.AuthService;
import com.sm.instagram.platform.auth.dto.*;
import com.sm.instagram.platform.auth.firebase.FirebaseService;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.social.SocialPlatformFactory;
import com.sm.instagram.platform.auth.social.SocialPlatformService;
import com.sm.instagram.platform.common.authorization.Permission;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.*;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformRepository;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.usersocialconnection.ConnectionStatus;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 * Tests focus on socialSignIn, registerUser, registerInfluencer, connectSocialPlatform,
 * and refreshSocialConnection methods.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService Unit Tests")
class AuthServiceUnitTest {

    @Mock
    private FirebaseService firebaseService;

    @Mock
    private FirestoreService firestoreService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSocialConnectionRepository socialConnectionRepository;

    @Mock
    private SocialPlatformFactory socialPlatformFactory;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private PermissionUtils permissionUtils;

    @Mock
    private SocialPlatformService socialPlatformService;

    @Mock
    private UserRecord userRecord;

    private AuthService authService;

    private Platform createPlatform(Long id, String name) {
        Platform platform = new Platform();
        platform.setId(id);
        platform.setName(name);
        platform.setActive(true);
        return platform;
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
        return user;
    }

    private UserSocialConnection createSocialConnection(Long id, User user, Platform platform) {
        UserSocialConnection connection = new UserSocialConnection();
        connection.setId(id);
        connection.setUser(user);
        connection.setPlatform(platform);
        connection.setSocialUserId("social123");
        connection.setDisplayName("test_user");
        connection.setFollowersCount(1000);
        connection.setConnectionStatus(ConnectionStatus.CONNECTED);
        connection.setIsPrimary(true);
        return connection;
    }

    private Map<String, Object> createSocialUserData() {
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", "social123");
        data.put("username", "test_user");
        data.put("profile_picture_url", "https://example.com/pic.jpg");
        data.put("followers_count", 1000);
        data.put("access_token", "test_access_token");
        return data;
    }

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                firebaseService,
                firestoreService,
                userRepository,
                socialConnectionRepository,
                socialPlatformFactory,
                addressRepository,
                platformRepository,
                permissionUtils
        );
    }

    // ========================================
    // socialSignIn() Tests
    // ========================================
    @Nested
    @DisplayName("socialSignIn")
    class SocialSignInTests {

        @Test
        @DisplayName("should throw ValidationTranslatableException when request is null")
        void shouldThrowWhenRequestIsNull() {
            assertThatThrownBy(() -> authService.socialSignIn(null))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when platform name is null")
        void shouldThrowWhenPlatformNameIsNull() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName(null);
            request.setAuthCode("test-auth-code");

            assertThatThrownBy(() -> authService.socialSignIn(request))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when platform name is empty")
        void shouldThrowWhenPlatformNameIsEmpty() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("   ");
            request.setAuthCode("test-auth-code");

            assertThatThrownBy(() -> authService.socialSignIn(request))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when auth code is null")
        void shouldThrowWhenAuthCodeIsNull() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode(null);

            assertThatThrownBy(() -> authService.socialSignIn(request))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when auth code is empty")
        void shouldThrowWhenAuthCodeIsEmpty() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("   ");

            assertThatThrownBy(() -> authService.socialSignIn(request))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when social data is null")
        void shouldThrowWhenSocialDataIsNull() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("valid-auth-code");

            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("valid-auth-code")).thenReturn(null);

            assertThatThrownBy(() -> authService.socialSignIn(request))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when social data is empty")
        void shouldThrowWhenSocialDataIsEmpty() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("valid-auth-code");

            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("valid-auth-code")).thenReturn(new HashMap<>());

            assertThatThrownBy(() -> authService.socialSignIn(request))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when user_id is missing")
        void shouldThrowWhenUserIdIsMissing() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("valid-auth-code");

            Map<String, Object> socialData = new HashMap<>();
            socialData.put("username", "test_user");

            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("valid-auth-code")).thenReturn(socialData);

            assertThatThrownBy(() -> authService.socialSignIn(request))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should return response with userExists=false when no existing user found")
        void shouldReturnNewUserResponseWhenNoExistingUser() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("valid-auth-code");

            Map<String, Object> socialData = createSocialUserData();

            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("valid-auth-code")).thenReturn(socialData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId(anyString(), eq("social123")))
                    .thenReturn(Optional.empty());
            when(socialConnectionRepository.findBySocialUserId("social123")).thenReturn(List.of());
            when(platformRepository.findAll()).thenReturn(List.of());

            SocialSignInResponse response = authService.socialSignIn(request);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.isUserExists()).isFalse();
            assertThat(response.getSocialUserData()).isNotNull();
            assertThat(response.getSocialUserData().get("user_id")).isEqualTo("social123");
        }

        @Test
        @DisplayName("should return response with customToken when existing user found")
        void shouldReturnExistingUserResponseWhenUserFound() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("valid-auth-code");

            Map<String, Object> socialData = createSocialUserData();
            Platform platform = createPlatform(1L, "Instagram");
            User existingUser = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);
            UserSocialConnection connection = createSocialConnection(1L, existingUser, platform);

            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("valid-auth-code")).thenReturn(socialData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "social123"))
                    .thenReturn(Optional.of(connection));
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(firebaseService.generateCustomTokenWithClaims(eq("firebase-uid-123"), anyMap()))
                    .thenReturn("custom-token-123");

            SocialSignInResponse response = authService.socialSignIn(request);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.isUserExists()).isTrue();
            assertThat(response.getFirebaseUserId()).isEqualTo("firebase-uid-123");
            assertThat(response.getCustomToken()).isEqualTo("custom-token-123");
            assertThat(response.getUserId()).isEqualTo(1L);

            verify(socialConnectionRepository).save(connection);
        }

        @Test
        @DisplayName("should find connection by normalized platform name")
        void shouldFindConnectionByNormalizedPlatformName() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("INSTAGRAM");
            request.setAuthCode("valid-auth-code");

            Map<String, Object> socialData = createSocialUserData();
            Platform platform = createPlatform(1L, "instagram");
            User existingUser = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);
            UserSocialConnection connection = createSocialConnection(1L, existingUser, platform);

            when(socialPlatformFactory.getSocialService("INSTAGRAM")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("valid-auth-code")).thenReturn(socialData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "social123"))
                    .thenReturn(Optional.empty());
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("instagram", "social123"))
                    .thenReturn(Optional.of(connection));
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(firebaseService.generateCustomTokenWithClaims(eq("firebase-uid-123"), anyMap()))
                    .thenReturn("custom-token-123");

            SocialSignInResponse response = authService.socialSignIn(request);

            assertThat(response.isUserExists()).isTrue();
        }

        @Test
        @DisplayName("should find connection by socialUserId fallback")
        void shouldFindConnectionBySocialUserIdFallback() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("valid-auth-code");

            Map<String, Object> socialData = createSocialUserData();
            Platform platform = createPlatform(1L, "Instagram");
            User existingUser = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);
            UserSocialConnection connection = createSocialConnection(1L, existingUser, platform);

            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("valid-auth-code")).thenReturn(socialData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId(anyString(), eq("social123")))
                    .thenReturn(Optional.empty());
            when(platformRepository.findAll()).thenReturn(List.of(platform));
            when(socialConnectionRepository.findBySocialUserId("social123")).thenReturn(List.of(connection));
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(firebaseService.generateCustomTokenWithClaims(eq("firebase-uid-123"), anyMap()))
                    .thenReturn("custom-token-123");

            SocialSignInResponse response = authService.socialSignIn(request);

            assertThat(response.isUserExists()).isTrue();
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when token generation fails")
        void shouldThrowWhenTokenGenerationFails() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("valid-auth-code");

            Map<String, Object> socialData = createSocialUserData();
            Platform platform = createPlatform(1L, "Instagram");
            User existingUser = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);
            UserSocialConnection connection = createSocialConnection(1L, existingUser, platform);

            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("valid-auth-code")).thenReturn(socialData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "social123"))
                    .thenReturn(Optional.of(connection));
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(firebaseService.generateCustomTokenWithClaims(eq("firebase-uid-123"), anyMap()))
                    .thenThrow(new RuntimeException("Token generation failed"));

            assertThatThrownBy(() -> authService.socialSignIn(request))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when auth code exchange fails")
        void shouldThrowWhenAuthCodeExchangeFails() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("invalid-auth-code");

            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("invalid-auth-code"))
                    .thenThrow(new RuntimeException("Exchange failed"));

            assertThatThrownBy(() -> authService.socialSignIn(request))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }
    }

    // ========================================
    // registerUser() Tests
    // ========================================
    @Nested
    @DisplayName("registerUser")
    class RegisterUserTests {

        @Test
        @DisplayName("should throw ValidationTranslatableException when request is null")
        void shouldThrowWhenRequestIsNull() {
            assertThatThrownBy(() -> authService.registerUser(null))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should register COMPANY user successfully")
        void shouldRegisterCompanyUserSuccessfully() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("company@example.com");
            request.setPassword("Password1!");
            request.setFirstName("John");
            request.setLastName("Doe");
            request.setUserType("COMPANY");
            request.setCompanyName("Test Company");
            request.setPhoneNumber("+48123456789");
            request.setAddressStreet("Main Street 123");
            request.setAddressCity("Warsaw");
            request.setAddressPostalCode("00-001");
            request.setAddressCountry("Poland");

            Map<String, Object> firebaseUser = new HashMap<>();
            firebaseUser.put("uid", "firebase-uid-123");
            firebaseUser.put("email", "company@example.com");

            when(firebaseService.createOrValidateFirebaseUser(
                    eq("company@example.com"),
                    eq("Password1!"),
                    eq("John Doe"),
                    eq(Permission.COMPANY)
            )).thenReturn(firebaseUser);
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(firebaseService.generateCustomTokenWithClaims(eq("firebase-uid-123"), anyMap()))
                    .thenReturn("custom-token-123");

            RegisterUserResponse response = authService.registerUser(request);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getFirebaseUserId()).isEqualTo("firebase-uid-123");
            assertThat(response.getCustomToken()).isEqualTo("custom-token-123");

            verify(addressRepository).save(any(Address.class));
        }

        @Test
        @DisplayName("should register INFLUENCER user successfully without social connection")
        void shouldRegisterInfluencerWithoutSocialConnection() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("influencer@example.com");
            request.setPassword("Password1!");
            request.setFirstName("Jane");
            request.setLastName("Smith");
            request.setUserType("INFLUENCER");

            Map<String, Object> firebaseUser = new HashMap<>();
            firebaseUser.put("uid", "firebase-uid-456");
            firebaseUser.put("email", "influencer@example.com");

            when(firebaseService.createOrValidateFirebaseUser(
                    eq("influencer@example.com"),
                    eq("Password1!"),
                    eq("Jane Smith"),
                    eq(Permission.INFLUENCER)
            )).thenReturn(firebaseUser);
            when(userRepository.findByFirebaseUserId("firebase-uid-456")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(2L);
                return user;
            });
            when(firebaseService.generateCustomTokenWithClaims(eq("firebase-uid-456"), anyMap()))
                    .thenReturn("custom-token-456");

            RegisterUserResponse response = authService.registerUser(request);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getUserId()).isEqualTo(2L);

            verify(addressRepository, never()).save(any(Address.class));
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when user already exists")
        void shouldThrowWhenUserAlreadyExists() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("existing@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");

            Map<String, Object> firebaseUser = new HashMap<>();
            firebaseUser.put("uid", "firebase-uid-existing");

            User existingUser = createUser(1L, "firebase-uid-existing", UserType.INFLUENCER);

            when(firebaseService.createOrValidateFirebaseUser(anyString(), anyString(), anyString(), any()))
                    .thenReturn(firebaseUser);
            when(userRepository.findByFirebaseUserId("firebase-uid-existing"))
                    .thenReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> authService.registerUser(request))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when Firebase returns null uid")
        void shouldThrowWhenFirebaseReturnsNullUid() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password1!");
            request.setUserType("COMPANY");

            Map<String, Object> firebaseUser = new HashMap<>();
            // No uid in response

            when(firebaseService.createOrValidateFirebaseUser(anyString(), anyString(), anyString(), any()))
                    .thenReturn(firebaseUser);

            assertThatThrownBy(() -> authService.registerUser(request))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should use email prefix as display name when names are not provided")
        void shouldUseEmailPrefixAsDisplayNameWhenNamesNotProvided() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("testuser@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");
            request.setFirstName(null);
            request.setLastName(null);

            Map<String, Object> firebaseUser = new HashMap<>();
            firebaseUser.put("uid", "firebase-uid-123");

            when(firebaseService.createOrValidateFirebaseUser(
                    eq("testuser@example.com"),
                    eq("Password1!"),
                    eq("testuser"),
                    eq(Permission.INFLUENCER)
            )).thenReturn(firebaseUser);
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(firebaseService.generateCustomTokenWithClaims(eq("firebase-uid-123"), anyMap()))
                    .thenReturn("token");

            authService.registerUser(request);

            verify(firebaseService).createOrValidateFirebaseUser(
                    eq("testuser@example.com"),
                    eq("Password1!"),
                    eq("testuser"),
                    eq(Permission.INFLUENCER)
            );
        }

        @Test
        @DisplayName("should set company-specific fields for COMPANY user type")
        void shouldSetCompanySpecificFields() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("company@example.com");
            request.setPassword("Password1!");
            request.setUserType("COMPANY");
            request.setCompanyName("Test Company Inc");
            request.setPhoneNumber("+1234567890");
            request.setProfilePictureUrl("https://example.com/logo.png");

            Map<String, Object> firebaseUser = new HashMap<>();
            firebaseUser.put("uid", "firebase-uid-company");

            when(firebaseService.createOrValidateFirebaseUser(anyString(), anyString(), anyString(), any()))
                    .thenReturn(firebaseUser);
            when(userRepository.findByFirebaseUserId("firebase-uid-company")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap())).thenReturn("token");

            authService.registerUser(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getName()).isEqualTo("Test Company Inc");
            assertThat(savedUser.getPhoneNumber()).isEqualTo("+1234567890");
            assertThat(savedUser.getProfilePicture()).isEqualTo("https://example.com/logo.png");
        }

        @Test
        @DisplayName("should register INFLUENCER with social connection")
        void shouldRegisterInfluencerWithSocialConnection() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("influencer@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");
            request.setSocialPlatform("Instagram");
            request.setSocialAuthCode("social-auth-code");

            Map<String, Object> firebaseUser = new HashMap<>();
            firebaseUser.put("uid", "firebase-uid-social");

            Map<String, Object> socialData = createSocialUserData();
            Platform platform = createPlatform(1L, "Instagram");

            // Store saved user reference for subsequent findByFirebaseUserId calls
            final User[] savedUserHolder = new User[1];

            when(firebaseService.createOrValidateFirebaseUser(anyString(), anyString(), anyString(), any()))
                    .thenReturn(firebaseUser);
            // First call during checkUserDoesNotExist returns empty, subsequent calls return the saved user
            when(userRepository.findByFirebaseUserId("firebase-uid-social"))
                    .thenReturn(Optional.empty())
                    .thenAnswer(invocation -> Optional.ofNullable(savedUserHolder[0]));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                savedUserHolder[0] = user;
                return user;
            });
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("social-auth-code")).thenReturn(socialData);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenAnswer(invocation -> {
                UserSocialConnection conn = invocation.getArgument(0);
                conn.setId(1L);
                return conn;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap())).thenReturn("token");

            RegisterUserResponse response = authService.registerUser(request);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getSocialConnection()).isNotNull();
        }
    }

    // ========================================
    // registerInfluencer() Tests
    // ========================================
    @Nested
    @DisplayName("registerInfluencer")
    class RegisterInfluencerTests {

        @Test
        @DisplayName("should throw ValidationTranslatableException when request is null")
        void shouldThrowWhenRequestIsNull() {
            assertThatThrownBy(() -> authService.registerInfluencer(null))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when email is null")
        void shouldThrowWhenEmailIsNull() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail(null);
            request.setPlatformName("Instagram");
            request.setSocialUserData(createSocialUserData());

            assertThatThrownBy(() -> authService.registerInfluencer(request))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when email is empty")
        void shouldThrowWhenEmailIsEmpty() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("   ");
            request.setPlatformName("Instagram");
            request.setSocialUserData(createSocialUserData());

            assertThatThrownBy(() -> authService.registerInfluencer(request))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when platform name is null")
        void shouldThrowWhenPlatformNameIsNull() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName(null);
            request.setSocialUserData(createSocialUserData());

            assertThatThrownBy(() -> authService.registerInfluencer(request))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when social data is null (wrapped validation error)")
        void shouldThrowWhenSocialDataIsNull() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName("Instagram");
            request.setSocialUserData(null);

            // ValidationTranslatableException is caught and re-thrown as BusinessRuleTranslatableException
            assertThatThrownBy(() -> authService.registerInfluencer(request))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when social data is empty (wrapped validation error)")
        void shouldThrowWhenSocialDataIsEmpty() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName("Instagram");
            request.setSocialUserData(new HashMap<>());

            // ValidationTranslatableException is caught and re-thrown as BusinessRuleTranslatableException
            assertThatThrownBy(() -> authService.registerInfluencer(request))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when user_id is missing in social data (wrapped validation error)")
        void shouldThrowWhenUserIdMissingInSocialData() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName("Instagram");

            Map<String, Object> socialData = new HashMap<>();
            socialData.put("username", "test_user");
            request.setSocialUserData(socialData);

            // ValidationTranslatableException is caught and re-thrown as BusinessRuleTranslatableException
            assertThatThrownBy(() -> authService.registerInfluencer(request))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when username is missing (wrapped validation error)")
        void shouldThrowWhenUsernameMissing() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName("Instagram");

            Map<String, Object> socialData = new HashMap<>();
            socialData.put("user_id", "social123");
            socialData.put("username", "");
            request.setSocialUserData(socialData);

            // ValidationTranslatableException is caught and re-thrown as BusinessRuleTranslatableException
            assertThatThrownBy(() -> authService.registerInfluencer(request))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when social account already registered")
        void shouldThrowWhenSocialAccountAlreadyRegistered() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName("Instagram");
            request.setSocialUserData(createSocialUserData());

            Platform platform = createPlatform(1L, "Instagram");
            User existingUser = createUser(1L, "firebase-existing", UserType.INFLUENCER);
            UserSocialConnection existingConnection = createSocialConnection(1L, existingUser, platform);

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "social123"))
                    .thenReturn(Optional.of(existingConnection));

            assertThatThrownBy(() -> authService.registerInfluencer(request))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should register influencer successfully")
        void shouldRegisterInfluencerSuccessfully() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("influencer@example.com");
            request.setPlatformName("Instagram");
            request.setSocialUserData(createSocialUserData());

            Map<String, Object> firebaseUser = new HashMap<>();
            firebaseUser.put("uid", "firebase-uid-new");

            Platform platform = createPlatform(1L, "Instagram");

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "social123"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(
                    eq("influencer@example.com"),
                    eq("test_user"),
                    eq("Instagram"),
                    eq("social123")
            )).thenReturn(firebaseUser);
            when(userRepository.findByFirebaseUserId("firebase-uid-new")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenAnswer(invocation -> {
                UserSocialConnection conn = invocation.getArgument(0);
                conn.setId(1L);
                return conn;
            });
            when(firebaseService.generateCustomTokenWithClaims(eq("firebase-uid-new"), anyMap()))
                    .thenReturn("custom-token-new");

            RegisterUserResponse response = authService.registerInfluencer(request);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getFirebaseUserId()).isEqualTo("firebase-uid-new");
            assertThat(response.getCustomToken()).isEqualTo("custom-token-new");
            assertThat(response.getSocialConnection()).isNotNull();
            assertThat(response.getSocialConnection().getDisplayName()).isEqualTo("test_user");
        }

        @Test
        @DisplayName("should handle existing Firebase user during registration")
        void shouldHandleExistingFirebaseUserDuringRegistration() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("existing@example.com");
            request.setPlatformName("Instagram");
            request.setSocialUserData(createSocialUserData());

            Platform platform = createPlatform(1L, "Instagram");

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "social123"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new ExternalServiceException("An account with this email already exists", "Firebase", "createUser", null));
            when(firebaseService.getUserByEmail("existing@example.com")).thenReturn(userRecord);
            when(userRecord.getUid()).thenReturn("firebase-uid-existing");
            when(userRepository.findByFirebaseUserId("firebase-uid-existing")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenAnswer(invocation -> {
                UserSocialConnection conn = invocation.getArgument(0);
                conn.setId(1L);
                return conn;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap())).thenReturn("token");

            RegisterUserResponse response = authService.registerInfluencer(request);

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should delete Firebase user on registration failure")
        void shouldDeleteFirebaseUserOnRegistrationFailure() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName("Instagram");
            request.setSocialUserData(createSocialUserData());

            Map<String, Object> firebaseUser = new HashMap<>();
            firebaseUser.put("uid", "firebase-uid-to-delete");

            Platform platform = createPlatform(1L, "Instagram");

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "social123"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(firebaseUser);
            when(userRepository.findByFirebaseUserId("firebase-uid-to-delete")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.save(any(UserSocialConnection.class)))
                    .thenThrow(new RuntimeException("Database error"));

            assertThatThrownBy(() -> authService.registerInfluencer(request))
                    .isInstanceOf(BusinessRuleTranslatableException.class);

            verify(firebaseService).deleteUser("firebase-uid-to-delete");
        }

        @Test
        @DisplayName("should extract followers count from social data")
        void shouldExtractFollowersCountFromSocialData() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName("Instagram");

            Map<String, Object> socialData = createSocialUserData();
            socialData.put("followers_count", 5000);
            request.setSocialUserData(socialData);

            Map<String, Object> firebaseUser = new HashMap<>();
            firebaseUser.put("uid", "firebase-uid-123");
            Platform platform = createPlatform(1L, "Instagram");

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(firebaseUser);
            when(userRepository.findByFirebaseUserId(anyString())).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenAnswer(invocation -> {
                UserSocialConnection conn = invocation.getArgument(0);
                conn.setId(1L);
                return conn;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap())).thenReturn("token");

            RegisterUserResponse response = authService.registerInfluencer(request);

            assertThat(response.getSocialConnection().getFollowersCount()).isEqualTo(5000);
        }
    }

    // ========================================
    // connectSocialPlatform() Tests
    // ========================================
    @Nested
    @DisplayName("connectSocialPlatform")
    class ConnectSocialPlatformTests {

        @Test
        @DisplayName("should throw ValidationTranslatableException when request is null")
        void shouldThrowWhenRequestIsNull() {
            assertThatThrownBy(() -> authService.connectSocialPlatform(null))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when platform name is null")
        void shouldThrowWhenPlatformNameIsNull() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName(null);
            request.setAuthCode("auth-code");

            assertThatThrownBy(() -> authService.connectSocialPlatform(request))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when auth code is null")
        void shouldThrowWhenAuthCodeIsNull() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode(null);

            assertThatThrownBy(() -> authService.connectSocialPlatform(request))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user not authenticated")
        void shouldThrowWhenUserNotAuthenticated() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("auth-code");

            when(permissionUtils.getUserId()).thenReturn(null);

            assertThatThrownBy(() -> authService.connectSocialPlatform(request))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user ID is empty")
        void shouldThrowWhenUserIdIsEmpty() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("auth-code");

            when(permissionUtils.getUserId()).thenReturn("");

            assertThatThrownBy(() -> authService.connectSocialPlatform(request))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("auth-code");

            when(permissionUtils.getUserId()).thenReturn("firebase-uid-123");
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.connectSocialPlatform(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when social account connected to another user")
        void shouldThrowWhenSocialAccountConnectedToAnotherUser() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("auth-code");

            User currentUser = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);
            User otherUser = createUser(2L, "firebase-uid-other", UserType.INFLUENCER);
            Platform platform = createPlatform(1L, "Instagram");
            UserSocialConnection otherUserConnection = createSocialConnection(1L, otherUser, platform);

            Map<String, Object> socialData = createSocialUserData();

            when(permissionUtils.getUserId()).thenReturn("firebase-uid-123");
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(currentUser));
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("auth-code")).thenReturn(socialData);
            when(socialConnectionRepository.findByUserIdAndPlatformNameAndSocialUserId(1L, "Instagram", "social123"))
                    .thenReturn(Optional.empty());
            when(socialConnectionRepository.findByPlatformNameAndSocialUserIdAndUserIdNot("Instagram", "social123", 1L))
                    .thenReturn(Optional.of(otherUserConnection));

            assertThatThrownBy(() -> authService.connectSocialPlatform(request))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should create new social connection successfully")
        void shouldCreateNewSocialConnectionSuccessfully() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("auth-code");
            request.setSetPrimary(true);

            User user = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);
            Platform platform = createPlatform(1L, "Instagram");
            Map<String, Object> socialData = createSocialUserData();

            when(permissionUtils.getUserId()).thenReturn("firebase-uid-123");
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(user));
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("auth-code")).thenReturn(socialData);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.findByUserIdAndPlatformNameAndSocialUserId(1L, "Instagram", "social123"))
                    .thenReturn(Optional.empty());
            when(socialConnectionRepository.findByPlatformNameAndSocialUserIdAndUserIdNot("Instagram", "social123", 1L))
                    .thenReturn(Optional.empty());
            when(socialConnectionRepository.findByUserId(1L)).thenReturn(List.of());
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenAnswer(invocation -> {
                UserSocialConnection conn = invocation.getArgument(0);
                conn.setId(1L);
                return conn;
            });

            SocialConnectionResponse response = authService.connectSocialPlatform(request);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getConnectionId()).isEqualTo(1L);
            assertThat(response.getPlatformName()).isEqualTo("Instagram");
            assertThat(response.getSocialUserId()).isEqualTo("social123");
            assertThat(response.getDisplayName()).isEqualTo("test_user");
            assertThat(response.getFollowersCount()).isEqualTo(1000);
        }

        @Test
        @DisplayName("should update existing connection instead of throwing error")
        void shouldUpdateExistingConnection() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("auth-code");

            User user = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);
            Platform platform = createPlatform(1L, "Instagram");
            UserSocialConnection existingConnection = createSocialConnection(1L, user, platform);
            Map<String, Object> socialData = createSocialUserData();

            when(permissionUtils.getUserId()).thenReturn("firebase-uid-123");
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(user));
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("auth-code")).thenReturn(socialData);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.findByUserIdAndPlatformNameAndSocialUserId(1L, "Instagram", "social123"))
                    .thenReturn(Optional.of(existingConnection));
            when(socialConnectionRepository.findByPlatformNameAndSocialUserIdAndUserIdNot("Instagram", "social123", 1L))
                    .thenReturn(Optional.empty());
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenReturn(existingConnection);

            SocialConnectionResponse response = authService.connectSocialPlatform(request);

            assertThat(response.isSuccess()).isTrue();
            verify(socialConnectionRepository).save(existingConnection);
        }

        @Test
        @DisplayName("should store Instagram data in Firestore")
        void shouldStoreInstagramDataInFirestore() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("auth-code");

            User user = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);
            Platform platform = createPlatform(1L, "Instagram");
            Map<String, Object> socialData = createSocialUserData();

            when(permissionUtils.getUserId()).thenReturn("firebase-uid-123");
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(user));
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("auth-code")).thenReturn(socialData);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.findByUserIdAndPlatformNameAndSocialUserId(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(socialConnectionRepository.findByPlatformNameAndSocialUserIdAndUserIdNot(anyString(), anyString(), anyLong()))
                    .thenReturn(Optional.empty());
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenAnswer(invocation -> {
                UserSocialConnection conn = invocation.getArgument(0);
                conn.setId(1L);
                return conn;
            });

            authService.connectSocialPlatform(request);

            verify(firestoreService).storeInstagramUserData(socialData, "firebase-uid-123");
        }

        @Test
        @DisplayName("should unset primary flag on other connections when setting new primary")
        void shouldUnsetPrimaryOnOtherConnections() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("auth-code");
            request.setSetPrimary(true);

            User user = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);
            Platform platform = createPlatform(1L, "Instagram");
            Platform tiktokPlatform = createPlatform(2L, "TikTok");

            UserSocialConnection existingPrimaryConnection = createSocialConnection(2L, user, tiktokPlatform);
            existingPrimaryConnection.setIsPrimary(true);

            Map<String, Object> socialData = createSocialUserData();

            when(permissionUtils.getUserId()).thenReturn("firebase-uid-123");
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(user));
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("auth-code")).thenReturn(socialData);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.findByUserIdAndPlatformNameAndSocialUserId(1L, "Instagram", "social123"))
                    .thenReturn(Optional.empty());
            when(socialConnectionRepository.findByPlatformNameAndSocialUserIdAndUserIdNot("Instagram", "social123", 1L))
                    .thenReturn(Optional.empty());
            when(socialConnectionRepository.findByUserId(1L)).thenReturn(List.of(existingPrimaryConnection));
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenAnswer(invocation -> {
                UserSocialConnection conn = invocation.getArgument(0);
                if (conn.getId() == null) {
                    conn.setId(3L);
                }
                return conn;
            });

            authService.connectSocialPlatform(request);

            assertThat(existingPrimaryConnection.getIsPrimary()).isFalse();
            verify(socialConnectionRepository, times(2)).save(any(UserSocialConnection.class));
        }
    }

    // ========================================
    // refreshSocialConnection() Tests
    // ========================================
    @Nested
    @DisplayName("refreshSocialConnection")
    class RefreshSocialConnectionTests {

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user not authenticated")
        void shouldThrowWhenUserNotAuthenticated() {
            when(permissionUtils.getUserId()).thenReturn(null);

            assertThatThrownBy(() -> authService.refreshSocialConnection(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when connection not found")
        void shouldThrowWhenConnectionNotFound() {
            when(permissionUtils.getUserId()).thenReturn("firebase-uid-123");
            when(socialConnectionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshSocialConnection(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-owner non-admin tries to refresh")
        void shouldThrowWhenNonOwnerNonAdminTriesToRefresh() {
            User owner = createUser(1L, "owner-uid", UserType.INFLUENCER);
            Platform platform = createPlatform(1L, "Instagram");
            UserSocialConnection connection = createSocialConnection(1L, owner, platform);

            when(permissionUtils.getUserId()).thenReturn("other-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(socialConnectionRepository.findById(1L)).thenReturn(Optional.of(connection));

            assertThatThrownBy(() -> authService.refreshSocialConnection(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should refresh connection successfully for owner")
        void shouldRefreshConnectionSuccessfullyForOwner() {
            User owner = createUser(1L, "owner-uid", UserType.INFLUENCER);
            Platform platform = createPlatform(1L, "Instagram");
            UserSocialConnection connection = createSocialConnection(1L, owner, platform);

            Map<String, Object> refreshedData = new HashMap<>();
            refreshedData.put("username", "updated_user");
            refreshedData.put("profile_picture_url", "https://new-pic.com");
            refreshedData.put("followers_count", 2000);

            when(permissionUtils.getUserId()).thenReturn("owner-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(socialConnectionRepository.findById(1L)).thenReturn(Optional.of(connection));
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.refreshSocialData("social123")).thenReturn(refreshedData);
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenReturn(connection);

            SocialConnectionResponse response = authService.refreshSocialConnection(1L);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(connection.getDisplayName()).isEqualTo("updated_user");
            assertThat(connection.getFollowersCount()).isEqualTo(2000);
        }

        @Test
        @DisplayName("should refresh connection successfully for admin")
        void shouldRefreshConnectionSuccessfullyForAdmin() {
            User owner = createUser(1L, "owner-uid", UserType.INFLUENCER);
            Platform platform = createPlatform(1L, "Instagram");
            UserSocialConnection connection = createSocialConnection(1L, owner, platform);

            Map<String, Object> refreshedData = new HashMap<>();
            refreshedData.put("username", "updated_user");

            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(socialConnectionRepository.findById(1L)).thenReturn(Optional.of(connection));
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.refreshSocialData("social123")).thenReturn(refreshedData);
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenReturn(connection);

            SocialConnectionResponse response = authService.refreshSocialConnection(1L);

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when refresh fails")
        void shouldThrowWhenRefreshFails() {
            User owner = createUser(1L, "owner-uid", UserType.INFLUENCER);
            Platform platform = createPlatform(1L, "Instagram");
            UserSocialConnection connection = createSocialConnection(1L, owner, platform);

            when(permissionUtils.getUserId()).thenReturn("owner-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(socialConnectionRepository.findById(1L)).thenReturn(Optional.of(connection));
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.refreshSocialData("social123"))
                    .thenThrow(new RuntimeException("API error"));

            assertThatThrownBy(() -> authService.refreshSocialConnection(1L))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }
    }

    // ========================================
    // connectSocialPlatformForRegistration() Tests
    // ========================================
    @Nested
    @DisplayName("connectSocialPlatformForRegistration")
    class ConnectSocialPlatformForRegistrationTests {

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("auth-code");

            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.connectSocialPlatformForRegistration("firebase-uid-123", request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should create social connection for registration successfully")
        void shouldCreateSocialConnectionForRegistrationSuccessfully() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("auth-code");

            User user = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);
            Platform platform = createPlatform(1L, "Instagram");
            Map<String, Object> socialData = createSocialUserData();

            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(user));
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("auth-code")).thenReturn(socialData);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenAnswer(invocation -> {
                UserSocialConnection conn = invocation.getArgument(0);
                conn.setId(1L);
                return conn;
            });

            SocialConnectionResponse response = authService.connectSocialPlatformForRegistration("firebase-uid-123", request);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getConnectionId()).isEqualTo(1L);

            ArgumentCaptor<UserSocialConnection> connectionCaptor = ArgumentCaptor.forClass(UserSocialConnection.class);
            verify(socialConnectionRepository).save(connectionCaptor.capture());

            UserSocialConnection savedConnection = connectionCaptor.getValue();
            assertThat(savedConnection.getIsPrimary()).isTrue();
        }

        @Test
        @DisplayName("should store Instagram data in Firestore during registration")
        void shouldStoreInstagramDataInFirestoreDuringRegistration() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("auth-code");

            User user = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);
            Platform platform = createPlatform(1L, "Instagram");
            Map<String, Object> socialData = createSocialUserData();

            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(user));
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("auth-code")).thenReturn(socialData);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenAnswer(invocation -> {
                UserSocialConnection conn = invocation.getArgument(0);
                conn.setId(1L);
                return conn;
            });

            authService.connectSocialPlatformForRegistration("firebase-uid-123", request);

            verify(firestoreService).storeInstagramUserData(socialData, "firebase-uid-123");
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when auth code exchange fails")
        void shouldThrowWhenAuthCodeExchangeFailsDuringRegistration() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("invalid-auth-code");

            User user = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);

            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(user));
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("invalid-auth-code"))
                    .thenThrow(new RuntimeException("Exchange failed"));

            assertThatThrownBy(() -> authService.connectSocialPlatformForRegistration("firebase-uid-123", request))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }
    }

    // ========================================
    // Edge Cases and Additional Tests
    // ========================================
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle profile picture URL without https prefix")
        void shouldHandleProfilePictureUrlWithoutHttpsPrefix() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName("Instagram");

            Map<String, Object> socialData = createSocialUserData();
            socialData.put("profile_picture_url", "http://example.com/pic.jpg");
            request.setSocialUserData(socialData);

            Map<String, Object> firebaseUser = new HashMap<>();
            firebaseUser.put("uid", "firebase-uid-123");
            Platform platform = createPlatform(1L, "Instagram");

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(firebaseUser);
            when(userRepository.findByFirebaseUserId(anyString())).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenAnswer(invocation -> {
                UserSocialConnection conn = invocation.getArgument(0);
                conn.setId(1L);
                return conn;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap())).thenReturn("token");

            authService.registerInfluencer(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getProfilePicture()).startsWith("https://");
        }

        @Test
        @DisplayName("should handle followers count from nested user object")
        void shouldHandleFollowersCountFromNestedUserObject() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName("Instagram");

            Map<String, Object> socialData = new HashMap<>();
            socialData.put("user_id", "social123");
            socialData.put("username", "test_user");
            socialData.put("access_token", "token");

            Map<String, Object> nestedUser = new HashMap<>();
            nestedUser.put("followers_count", 3000);
            socialData.put("user", nestedUser);

            request.setSocialUserData(socialData);

            Map<String, Object> firebaseUser = new HashMap<>();
            firebaseUser.put("uid", "firebase-uid-123");
            Platform platform = createPlatform(1L, "Instagram");

            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(firebaseService.createSocialOnlyFirebaseUser(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(firebaseUser);
            when(userRepository.findByFirebaseUserId(anyString())).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.getPlatformEntity()).thenReturn(platform);
            when(socialConnectionRepository.save(any(UserSocialConnection.class))).thenAnswer(invocation -> {
                UserSocialConnection conn = invocation.getArgument(0);
                conn.setId(1L);
                return conn;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap())).thenReturn("token");

            RegisterUserResponse response = authService.registerInfluencer(request);

            assertThat(response.getSocialConnection().getFollowersCount()).isEqualTo(3000);
        }

        @Test
        @DisplayName("should set IN_VALIDATION status for new users")
        void shouldSetInValidationStatusForNewUsers() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");

            Map<String, Object> firebaseUser = new HashMap<>();
            firebaseUser.put("uid", "firebase-uid-123");

            when(firebaseService.createOrValidateFirebaseUser(anyString(), anyString(), anyString(), any()))
                    .thenReturn(firebaseUser);
            when(userRepository.findByFirebaseUserId(anyString())).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap())).thenReturn("token");

            authService.registerUser(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
        }

        @Test
        @DisplayName("should handle Firestore error gracefully during social sign-in")
        void shouldHandleFirestoreErrorGracefullyDuringSocialSignIn() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("valid-auth-code");

            Map<String, Object> socialData = createSocialUserData();
            Platform platform = createPlatform(1L, "Instagram");
            User existingUser = createUser(1L, "firebase-uid-123", UserType.INFLUENCER);
            UserSocialConnection connection = createSocialConnection(1L, existingUser, platform);

            when(socialPlatformFactory.getSocialService("Instagram")).thenReturn(socialPlatformService);
            when(socialPlatformService.exchangeAuthCodeForProfile("valid-auth-code")).thenReturn(socialData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "social123"))
                    .thenReturn(Optional.of(connection));
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(firebaseService.generateCustomTokenWithClaims(eq("firebase-uid-123"), anyMap()))
                    .thenReturn("custom-token-123");
            doThrow(new RuntimeException("Firestore error"))
                    .when(firestoreService).storeInstagramUserData(anyMap(), anyString());

            // Should not throw - Firestore errors are logged but don't fail the operation
            SocialSignInResponse response = authService.socialSignIn(request);

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should pass through IllegalArgumentException from social platform factory")
        void shouldPassThroughIllegalArgumentExceptionFromFactory() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("UnsupportedPlatform");
            request.setAuthCode("auth-code");

            when(socialPlatformFactory.getSocialService("UnsupportedPlatform"))
                    .thenThrow(new IllegalArgumentException("Unsupported platform"));

            assertThatThrownBy(() -> authService.socialSignIn(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported platform");
        }
    }
}
