package com.sm.instagram.platform.unit.service;

import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.dto.OAuthCallbackFailure;
import com.sm.instagram.platform.auth.firebase.FirebaseService;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.service.OAuthCallbackService;
import com.sm.instagram.platform.auth.service.TokenExchangeService;
import com.sm.instagram.platform.auth.session.SocialAuthSessionService;
import com.sm.instagram.platform.auth.social.instagram.InstagramService;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.NetworkRetryExhaustedException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformRepository;
import com.sm.instagram.platform.legal.LegalConsentService;
import com.sm.instagram.platform.storage.service.ProfilePictureProxyService;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.usersocialconnection.ConnectionStatus;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OAuthCallbackService.
 * Tests cover OAuth callback processing, user authentication flows, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OAuthCallbackService Unit Tests")
class OAuthCallbackServiceUnitTest {

    @Mock
    private InstagramService instagramService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSocialConnectionRepository socialConnectionRepository;

    @Mock
    private FirebaseService firebaseService;

    @Mock
    private FirestoreService firestoreService;

    @Mock
    private SocialAuthSessionService sessionService;

    @Mock
    private TokenExchangeService tokenExchangeService;

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private ProfilePictureProxyService profilePictureProxyService;

    @Mock
    private LegalConsentService legalConsentService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private UserRecord userRecord;

    private OAuthCallbackService oAuthCallbackService;

    private static final String FRONTEND_URL = "https://localhost:4200";
    private static final String COOKIE_HMAC_SECRET = "test-hmac-secret-32-chars-long!!";

    @BeforeEach
    void setUp() {
        oAuthCallbackService = new OAuthCallbackService(
                instagramService,
                userRepository,
                socialConnectionRepository,
                firebaseService,
                firestoreService,
                sessionService,
                tokenExchangeService,
                platformRepository,
                profilePictureProxyService,
                legalConsentService
        );

        // Set @Value fields via reflection
        ReflectionTestUtils.setField(oAuthCallbackService, "frontendUrl", FRONTEND_URL);
        ReflectionTestUtils.setField(oAuthCallbackService, "cookieHmacSecret", COOKIE_HMAC_SECRET);
        ReflectionTestUtils.setField(oAuthCallbackService, "secureCookies", false);
        ReflectionTestUtils.setField(oAuthCallbackService, "cookieDomain", "localhost");
        ReflectionTestUtils.setField(oAuthCallbackService, "activeProfile", "test");

        // Setup default request mock for web client
        when(request.getHeader("Accept")).thenReturn("text/html");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 Chrome");
    }

    @Test
    @DisplayName("extractFollowersCount coerces Integer/Long/String without throwing (no orphaned Firebase user)")
    void extractFollowersCount_coercesDefensively() {
        // Instagram's followers_count has varied between Integer, Long, and String. A raw (Integer)
        // cast threw a ClassCastException AFTER the Firebase user + Firestore doc were created,
        // orphaning the account. The coercion must accept any numeric/string shape and never throw.
        assertThat(OAuthCallbackService.extractFollowersCount(1234)).isEqualTo(1234);
        assertThat(OAuthCallbackService.extractFollowersCount(1234L)).isEqualTo(1234);
        assertThat(OAuthCallbackService.extractFollowersCount("5678")).isEqualTo(5678);
        assertThat(OAuthCallbackService.extractFollowersCount(null)).isNull();
        assertThat(OAuthCallbackService.extractFollowersCount("not-a-number")).isNull();
        assertThat(OAuthCallbackService.extractFollowersCount(new Object())).isNull();
    }

    private Map<String, Object> createInstagramData() {
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", "12345678");
        data.put("username", "testuser");
        data.put("access_token", "test-access-token");
        data.put("profile_picture_url", "https://instagram.cdninstagram.com/profile.jpg");
        data.put("followers_count", 1000);
        return data;
    }

    private User createUser(Long id, String firebaseUid) {
        User user = new User();
        user.setId(id);
        user.setFirebaseUserId(firebaseUid);
        user.setEmail("test@example.com");
        user.setUserType(UserType.INFLUENCER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setName("Test User");
        return user;
    }

    private UserSocialConnection createSocialConnection(User user) {
        UserSocialConnection connection = new UserSocialConnection();
        connection.setId(1L);
        connection.setUser(user);
        connection.setSocialUserId("12345678");
        connection.setDisplayName("testuser");
        connection.setConnectionStatus(ConnectionStatus.CONNECTED);
        connection.setFollowersCount(1000);
        connection.setIsPrimary(true);
        return connection;
    }

    private Platform createInstagramPlatform() {
        Platform platform = new Platform();
        platform.setId(1L);
        platform.setName("Instagram");
        platform.setActive(true);
        return platform;
    }

    @Nested
    @DisplayName("processInstagramCallback - Existing User Flow")
    class ProcessInstagramCallbackExistingUserTests {

        @Test
        @DisplayName("should redirect to success for existing user with valid Firebase")
        void shouldRedirectToSuccessForExistingUser() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            UserSocialConnection connection = createSocialConnection(existingUser);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(eq("firebase-uid-123"), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            ResponseEntity<?> result = oAuthCallbackService.processInstagramCallback(
                    code, null, request, response);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .isEqualTo(FRONTEND_URL + "/auth/success");
            verify(response, atLeastOnce()).addHeader(eq("Set-Cookie"), anyString());
        }

        @Test
        @DisplayName("should update profile picture when changed and not preservable")
        void shouldUpdateProfilePictureWhenChangedAndNotPreservable() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            instagramData.put("profile_picture_url", "https://instagram.cdninstagram.com/new-profile.jpg");

            User existingUser = createUser(1L, "firebase-uid-123");
            existingUser.setProfilePicture("https://instagram.cdninstagram.com/old-profile.jpg");
            UserSocialConnection connection = createSocialConnection(existingUser);
            connection.setProfilePictureUrl("https://instagram.cdninstagram.com/old-profile.jpg");

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            verify(userRepository).save(argThat(user ->
                    "https://instagram.cdninstagram.com/new-profile.jpg".equals(user.getProfilePicture())
            ));
        }

        @Test
        @DisplayName("should NOT update profile picture when user has preservable photo")
        void shouldNotUpdateProfilePictureWhenPreservable() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            instagramData.put("profile_picture_url", "https://instagram.cdninstagram.com/new-profile.jpg");

            User existingUser = createUser(1L, "firebase-uid-123");
            String permanentUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/profile.jpg";
            existingUser.setProfilePicture(permanentUrl);
            UserSocialConnection connection = createSocialConnection(existingUser);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(permanentUrl)).thenReturn(true);
            when(profilePictureProxyService.isInstagramCdnUrl(permanentUrl)).thenReturn(false);
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then - user profile picture should not be changed
            verify(userRepository, never()).save(argThat(user ->
                    "https://instagram.cdninstagram.com/new-profile.jpg".equals(user.getProfilePicture())
            ));
        }

        @Test
        @DisplayName("should update followers count when changed")
        void shouldUpdateFollowersCountWhenChanged() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            instagramData.put("followers_count", 2000);

            User existingUser = createUser(1L, "firebase-uid-123");
            UserSocialConnection connection = createSocialConnection(existingUser);
            connection.setFollowersCount(1000);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            verify(socialConnectionRepository).save(argThat(conn ->
                    conn.getFollowersCount().equals(2000)
            ));
        }

        @Test
        @DisplayName("should redirect with error when Firebase user not found")
        void shouldRedirectWithErrorWhenFirebaseUserNotFound() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            UserSocialConnection connection = createSocialConnection(existingUser);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firebaseService.getUserById("firebase-uid-123"))
                    .thenThrow(new ExternalServiceException("User not found", "Firebase", "getUserById", null));

            // When
            ResponseEntity<?> result = oAuthCallbackService.processInstagramCallback(
                    code, null, request, response);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .contains("/auth/error");
        }

        @Test
        @DisplayName("should re-proxy profile picture from Instagram CDN to Firebase Storage")
        void shouldReProxyProfilePictureFromInstagramCdn() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            String cdnUrl = "https://instagram.cdninstagram.com/profile.jpg";
            existingUser.setProfilePicture(cdnUrl);
            UserSocialConnection connection = createSocialConnection(existingUser);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(cdnUrl)).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(cdnUrl)).thenReturn(true);
            when(profilePictureProxyService.proxyToFirebaseStorage(cdnUrl, "firebase-uid-123"))
                    .thenReturn("https://firebasestorage.googleapis.com/proxied-profile.jpg");
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            verify(profilePictureProxyService).proxyToFirebaseStorage(cdnUrl, "firebase-uid-123");
            verify(userRepository, atLeastOnce()).save(any(User.class));
        }

        @Test
        @DisplayName("should include role from Firebase claims in custom token")
        void shouldIncludeRoleFromFirebaseClaimsInCustomToken() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            UserSocialConnection connection = createSocialConnection(existingUser);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER", "instagramId", "12345678"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(firebaseService).generateCustomTokenWithClaims(eq("firebase-uid-123"), claimsCaptor.capture());
            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims.get("role")).isEqualTo("INFLUENCER");
        }
    }

    @Nested
    @DisplayName("processInstagramCallback - New User Flow")
    class ProcessInstagramCallbackNewUserTests {

        @Test
        @DisplayName("should create new user and redirect to success")
        void shouldCreateNewUserAndRedirectToSuccess() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            Platform instagramPlatform = createInstagramPlatform();

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createInstagramFirebaseUser("testuser", "12345678"))
                    .thenReturn(Map.of("uid", "new-firebase-uid"));
            when(profilePictureProxyService.proxyToFirebaseStorage(anyString(), eq("new-firebase-uid")))
                    .thenReturn("https://firebasestorage.googleapis.com/profile.jpg");
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagramPlatform));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(firebaseService.generateCustomTokenWithClaims(eq("new-firebase-uid"), anyMap()))
                    .thenReturn("custom-token-new");

            // When
            ResponseEntity<?> result = oAuthCallbackService.processInstagramCallback(
                    code, null, request, response);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .isEqualTo(FRONTEND_URL + "/auth/success");

            // Verify user was created with correct data
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getFirebaseUserId()).isEqualTo("new-firebase-uid");
            assertThat(savedUser.getName()).isEqualTo("testuser");
            assertThat(savedUser.getUserType()).isEqualTo(UserType.INFLUENCER);
            assertThat(savedUser.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
        }

        @Test
        @DisplayName("should create social connection for new user")
        void shouldCreateSocialConnectionForNewUser() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            Platform instagramPlatform = createInstagramPlatform();

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createInstagramFirebaseUser("testuser", "12345678"))
                    .thenReturn(Map.of("uid", "new-firebase-uid"));
            when(profilePictureProxyService.proxyToFirebaseStorage(anyString(), anyString()))
                    .thenReturn("https://firebasestorage.googleapis.com/profile.jpg");
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagramPlatform));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-new");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            ArgumentCaptor<UserSocialConnection> connectionCaptor = ArgumentCaptor.forClass(UserSocialConnection.class);
            verify(socialConnectionRepository).save(connectionCaptor.capture());
            UserSocialConnection savedConnection = connectionCaptor.getValue();
            assertThat(savedConnection.getSocialUserId()).isEqualTo("12345678");
            assertThat(savedConnection.getDisplayName()).isEqualTo("testuser");
            assertThat(savedConnection.getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
            assertThat(savedConnection.getIsPrimary()).isTrue();
        }

        @Test
        @DisplayName("should save Instagram data to Firestore for new user")
        void shouldSaveInstagramDataToFirestoreForNewUser() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            Platform instagramPlatform = createInstagramPlatform();

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createInstagramFirebaseUser("testuser", "12345678"))
                    .thenReturn(Map.of("uid", "new-firebase-uid"));
            when(profilePictureProxyService.proxyToFirebaseStorage(anyString(), anyString())).thenReturn(null);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagramPlatform));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-new");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            verify(firestoreService).storeInstagramUserData(eq(instagramData), eq("new-firebase-uid"));
        }

        @Test
        @DisplayName("should set needsOnboarding flag in claims for new user")
        void shouldSetNeedsOnboardingFlagForNewUser() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            Platform instagramPlatform = createInstagramPlatform();

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createInstagramFirebaseUser("testuser", "12345678"))
                    .thenReturn(Map.of("uid", "new-firebase-uid"));
            when(profilePictureProxyService.proxyToFirebaseStorage(anyString(), anyString())).thenReturn(null);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagramPlatform));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-new");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(firebaseService).generateCustomTokenWithClaims(eq("new-firebase-uid"), claimsCaptor.capture());
            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims.get("needsOnboarding")).isEqualTo(true);
            assertThat(claims.get("needsEmail")).isEqualTo(true);
            assertThat(claims.get("pendingActivation")).isEqualTo(true);
        }

        @Test
        @DisplayName("should redirect with error when Instagram platform not found")
        void shouldRedirectWithErrorWhenInstagramPlatformNotFound() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createInstagramFirebaseUser("testuser", "12345678"))
                    .thenReturn(Map.of("uid", "new-firebase-uid"));
            when(profilePictureProxyService.proxyToFirebaseStorage(anyString(), anyString())).thenReturn(null);
            when(platformRepository.findByName("Instagram"))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });

            // When
            ResponseEntity<?> result = oAuthCallbackService.processInstagramCallback(
                    code, null, request, response);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .contains("/auth/error");
        }

        @Test
        @DisplayName("should use Instagram CDN URL as fallback when proxy fails")
        void shouldUseInstagramCdnUrlAsFallbackWhenProxyFails() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            String cdnUrl = "https://instagram.cdninstagram.com/profile.jpg";
            instagramData.put("profile_picture_url", cdnUrl);
            Platform instagramPlatform = createInstagramPlatform();

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createInstagramFirebaseUser("testuser", "12345678"))
                    .thenReturn(Map.of("uid", "new-firebase-uid"));
            when(profilePictureProxyService.proxyToFirebaseStorage(cdnUrl, "new-firebase-uid"))
                    .thenReturn(null); // Proxy failed
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagramPlatform));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-new");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getProfilePicture()).isEqualTo(cdnUrl);
        }
    }

    @Nested
    @DisplayName("processInstagramCallback - Error Handling")
    class ProcessInstagramCallbackErrorHandlingTests {

        @Test
        @DisplayName("should handle NetworkRetryExhaustedException with user-friendly message")
        void shouldHandleNetworkRetryExhaustedException() throws Exception {
            // Given
            String code = "test-auth-code";
            NetworkRetryExhaustedException networkException = new NetworkRetryExhaustedException(
                    "error.network.retry_exhausted", "Instagram", 3, new RuntimeException("Connection timeout")
            );

            when(instagramService.exchangeAuthCodeForProfile(code)).thenThrow(networkException);

            // When
            ResponseEntity<?> result = oAuthCallbackService.processInstagramCallback(
                    code, null, request, response);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            String location = result.getHeaders().getFirst(HttpHeaders.LOCATION);
            assertThat(location).contains("/auth/error");
            assertThat(location).contains("Instagram+is+temporarily+unavailable");
        }

        @Test
        @DisplayName("should handle ExternalServiceException with detailed logging")
        void shouldHandleExternalServiceException() throws Exception {
            // Given
            String code = "test-auth-code";
            ExternalServiceException serviceException = new ExternalServiceException(
                    "Invalid OAuth code", "Instagram", "exchangeCode", null
            );

            when(instagramService.exchangeAuthCodeForProfile(code)).thenThrow(serviceException);

            // When
            ResponseEntity<?> result = oAuthCallbackService.processInstagramCallback(
                    code, null, request, response);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .contains("/auth/error");
        }

        @Test
        @DisplayName("should handle IllegalArgumentException from validation")
        void shouldHandleIllegalArgumentException() throws Exception {
            // Given
            String code = "test-auth-code";
            when(instagramService.exchangeAuthCodeForProfile(code))
                    .thenThrow(new IllegalArgumentException("Invalid code format"));

            // When
            ResponseEntity<?> result = oAuthCallbackService.processInstagramCallback(
                    code, null, request, response);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .contains("/auth/error")
                    .contains("Invalid+code+format");
        }

        @Test
        @DisplayName("should handle unexpected Exception gracefully")
        void shouldHandleUnexpectedException() throws Exception {
            // Given
            String code = "test-auth-code";
            when(instagramService.exchangeAuthCodeForProfile(code))
                    .thenThrow(new RuntimeException("Unexpected error"));

            // When
            ResponseEntity<?> result = oAuthCallbackService.processInstagramCallback(
                    code, null, request, response);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .contains("/auth/error")
                    .contains("Authentication+failed");
        }

        @Test
        @DisplayName("should extract message from nested ExternalServiceException")
        void shouldExtractMessageFromNestedExternalServiceException() throws Exception {
            // Given
            String code = "test-auth-code";
            ExternalServiceException nestedCause = new ExternalServiceException(
                    "Nested error message", "Instagram", "nested", null
            );
            RuntimeException wrappingException = new RuntimeException("Wrapper", nestedCause);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenThrow(wrappingException);

            // When
            ResponseEntity<?> result = oAuthCallbackService.processInstagramCallback(
                    code, null, request, response);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .contains("/auth/error")
                    .contains("Nested+error+message");
        }
    }

    @Nested
    @DisplayName("handleOAuthError")
    class HandleOAuthErrorTests {

        @Test
        @DisplayName("should redirect with error description when provided")
        void shouldRedirectWithErrorDescriptionWhenProvided() {
            // Given
            String error = "access_denied";
            String errorDescription = "User denied access";

            // When
            ResponseEntity<?> result = oAuthCallbackService.handleOAuthError(
                    error, errorDescription, request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .contains("/auth/error")
                    .contains("User+denied+access");
        }

        @Test
        @DisplayName("should use error code when description is null")
        void shouldUseErrorCodeWhenDescriptionIsNull() {
            // Given
            String error = "invalid_request";

            // When
            ResponseEntity<?> result = oAuthCallbackService.handleOAuthError(
                    error, null, request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .contains("/auth/error")
                    .contains("invalid_request");
        }
    }

    @Nested
    @DisplayName("handleMissingCode")
    class HandleMissingCodeTests {

        @Test
        @DisplayName("should redirect with missing code error message")
        void shouldRedirectWithMissingCodeErrorMessage() {
            // When
            ResponseEntity<?> result = oAuthCallbackService.handleMissingCode(request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .contains("/auth/error")
                    .contains("Authorization+code+missing");
        }
    }

    @Nested
    @DisplayName("handleProcessingError")
    class HandleProcessingErrorTests {

        @Test
        @DisplayName("should redirect with processing error message")
        void shouldRedirectWithProcessingErrorMessage() {
            // Given
            Exception exception = new RuntimeException("Processing failed");

            // When
            ResponseEntity<?> result = oAuthCallbackService.handleProcessingError(exception, request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .contains("/auth/error")
                    .contains("Authentication+processing+failed");
        }
    }

    @Nested
    @DisplayName("API Client Response")
    class ApiClientResponseTests {

        @Test
        @DisplayName("should return JSON error for API client on OAuth error")
        void shouldReturnJsonErrorForApiClient() {
            // Given
            when(request.getHeader("Accept")).thenReturn("application/json");
            when(request.getHeader("User-Agent")).thenReturn("curl/7.79.1");
            String error = "access_denied";
            String errorDescription = "User denied access";

            // When
            ResponseEntity<?> result = oAuthCallbackService.handleOAuthError(
                    error, errorDescription, request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            // A record rather than a map, so the contract can describe it: the endpoint published
            // `object` for a body whose two keys have never changed.
            assertThat(result.getBody()).isInstanceOf(OAuthCallbackFailure.class);
            OAuthCallbackFailure body = (OAuthCallbackFailure) result.getBody();
            assertThat(body.success()).isFalse();
            assertThat(body.error()).isEqualTo("User denied access");
        }

        @Test
        @DisplayName("should return JSON error for API client on missing code")
        void shouldReturnJsonErrorForApiClientOnMissingCode() {
            // Given
            when(request.getHeader("Accept")).thenReturn("application/json");
            when(request.getHeader("User-Agent")).thenReturn("PostmanRuntime/7.29.2");

            // When
            ResponseEntity<?> result = oAuthCallbackService.handleMissingCode(request);

            // Then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(result.getBody()).isInstanceOf(OAuthCallbackFailure.class);
            assertThat(((OAuthCallbackFailure) result.getBody()).error())
                    .isEqualTo("Authorization code missing");
        }
    }

    @Nested
    @DisplayName("Cookie Handling")
    class CookieHandlingTests {

        @Test
        @DisplayName("should set HMAC-signed OAuth cookies for existing user")
        void shouldSetHmacSignedOAuthCookiesForExistingUser() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            UserSocialConnection connection = createSocialConnection(existingUser);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
            verify(response, atLeast(2)).addHeader(eq("Set-Cookie"), cookieCaptor.capture());

            boolean hasOAuthToken = cookieCaptor.getAllValues().stream()
                    .anyMatch(cookie -> cookie.contains("oauth_token="));
            boolean hasOAuthSig = cookieCaptor.getAllValues().stream()
                    .anyMatch(cookie -> cookie.contains("oauth_sig="));

            assertThat(hasOAuthToken).isTrue();
            assertThat(hasOAuthSig).isTrue();
        }

        @Test
        @DisplayName("should set HttpOnly flag on OAuth cookies")
        void shouldSetHttpOnlyFlagOnOAuthCookies() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            UserSocialConnection connection = createSocialConnection(existingUser);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
            verify(response, atLeast(2)).addHeader(eq("Set-Cookie"), cookieCaptor.capture());

            boolean hasHttpOnlyToken = cookieCaptor.getAllValues().stream()
                    .filter(cookie -> cookie.contains("oauth_token="))
                    .anyMatch(cookie -> cookie.contains("HttpOnly"));
            boolean hasHttpOnlySig = cookieCaptor.getAllValues().stream()
                    .filter(cookie -> cookie.contains("oauth_sig="))
                    .anyMatch(cookie -> cookie.contains("HttpOnly"));

            assertThat(hasHttpOnlyToken).isTrue();
            assertThat(hasHttpOnlySig).isTrue();
        }

        @Test
        @DisplayName("should set SameSite=Lax on OAuth cookies")
        void shouldSetSameSiteLaxOnOAuthCookies() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            UserSocialConnection connection = createSocialConnection(existingUser);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
            verify(response, atLeast(2)).addHeader(eq("Set-Cookie"), cookieCaptor.capture());

            boolean hasSameSiteLax = cookieCaptor.getAllValues().stream()
                    .filter(cookie -> cookie.contains("oauth_token="))
                    .anyMatch(cookie -> cookie.contains("SameSite=Lax"));

            assertThat(hasSameSiteLax).isTrue();
        }

        @Test
        @DisplayName("should set metadata cookie for new user indicator")
        void shouldSetMetadataCookieForNewUserIndicator() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            Platform instagramPlatform = createInstagramPlatform();

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createInstagramFirebaseUser("testuser", "12345678"))
                    .thenReturn(Map.of("uid", "new-firebase-uid"));
            when(profilePictureProxyService.proxyToFirebaseStorage(anyString(), anyString())).thenReturn(null);
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagramPlatform));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-new");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then — production uses response.addHeader("Set-Cookie", ...) (not the
            // servlet Cookie API) so Domain + SameSite can be controlled fully.
            // Assert via the same pattern as the SameSite test above (line ~934).
            ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
            verify(response, atLeast(3)).addHeader(eq("Set-Cookie"), headerCaptor.capture());

            boolean hasMetaNewUser = headerCaptor.getAllValues().stream()
                    .anyMatch(header -> header.contains("oauth_meta=new_user"));
            assertThat(hasMetaNewUser).isTrue();
        }
    }

    @Nested
    @DisplayName("Token Refresh for Existing Users")
    class TokenRefreshTests {

        @Test
        @DisplayName("should attempt token refresh when close to expiry")
        void shouldAttemptTokenRefreshWhenCloseToExpiry() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            UserSocialConnection connection = createSocialConnection(existingUser);

            // Firestore data with token close to expiry (10 days remaining)
            long currentTime = System.currentTimeMillis() / 1000;
            long lastUpdated = currentTime - (50 * 24 * 60 * 60); // 50 days ago
            Map<String, Object> firestoreData = new HashMap<>();
            firestoreData.put("lastUpdated", lastUpdated);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firestoreService.getInstagramUserDataByFirebaseUid("firebase-uid-123"))
                    .thenReturn(firestoreData);
            when(firestoreService.getDecryptedAccessToken("firebase-uid-123"))
                    .thenReturn("current-access-token");
            when(instagramService.refreshLongLivedToken("current-access-token"))
                    .thenReturn("new-access-token");
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            verify(instagramService).refreshLongLivedToken("current-access-token");
            verify(firestoreService).updateAccessToken(eq("firebase-uid-123"), eq("new-access-token"), anyLong());
        }

        @Test
        @DisplayName("should continue if token refresh fails")
        void shouldContinueIfTokenRefreshFails() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            UserSocialConnection connection = createSocialConnection(existingUser);

            long currentTime = System.currentTimeMillis() / 1000;
            long lastUpdated = currentTime - (50 * 24 * 60 * 60);
            Map<String, Object> firestoreData = new HashMap<>();
            firestoreData.put("lastUpdated", lastUpdated);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firestoreService.getInstagramUserDataByFirebaseUid("firebase-uid-123"))
                    .thenReturn(firestoreData);
            when(firestoreService.getDecryptedAccessToken("firebase-uid-123"))
                    .thenThrow(new RuntimeException("Firestore error"));
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            ResponseEntity<?> result = oAuthCallbackService.processInstagramCallback(
                    code, null, request, response);

            // Then - should still succeed
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .isEqualTo(FRONTEND_URL + "/auth/success");
        }
    }

    @Nested
    @DisplayName("Firestore Integration")
    class FirestoreIntegrationTests {

        @Test
        @DisplayName("should update Firestore data for existing user")
        void shouldUpdateFirestoreDataForExistingUser() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            UserSocialConnection connection = createSocialConnection(existingUser);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            verify(firestoreService).storeInstagramUserData(eq(instagramData), eq("firebase-uid-123"));
        }

        @Test
        @DisplayName("should continue if Firestore update fails for existing user")
        void shouldContinueIfFirestoreUpdateFailsForExistingUser() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            UserSocialConnection connection = createSocialConnection(existingUser);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            doThrow(new RuntimeException("Firestore error"))
                    .when(firestoreService).storeInstagramUserData(anyMap(), anyString());
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            ResponseEntity<?> result = oAuthCallbackService.processInstagramCallback(
                    code, null, request, response);

            // Then - should still succeed
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .isEqualTo(FRONTEND_URL + "/auth/success");
        }

        @Test
        @DisplayName("should continue if Firestore save fails for new user")
        void shouldContinueIfFirestoreSaveFailsForNewUser() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            Platform instagramPlatform = createInstagramPlatform();

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.empty());
            when(firebaseService.createInstagramFirebaseUser("testuser", "12345678"))
                    .thenReturn(Map.of("uid", "new-firebase-uid"));
            when(profilePictureProxyService.proxyToFirebaseStorage(anyString(), anyString())).thenReturn(null);
            doThrow(new RuntimeException("Firestore error"))
                    .when(firestoreService).storeInstagramUserData(anyMap(), anyString());
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(instagramPlatform));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-new");

            // When
            ResponseEntity<?> result = oAuthCallbackService.processInstagramCallback(
                    code, null, request, response);

            // Then - should still succeed
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(result.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .isEqualTo(FRONTEND_URL + "/auth/success");
        }
    }

    @Nested
    @DisplayName("User Claims Generation")
    class UserClaimsGenerationTests {

        @Test
        @DisplayName("should not include role claim when user has no Firebase role")
        void shouldNotIncludeRoleClaimWhenUserHasNoFirebaseRole() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            UserSocialConnection connection = createSocialConnection(existingUser);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(new HashMap<>()); // No role
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(firebaseService).generateCustomTokenWithClaims(eq("firebase-uid-123"), claimsCaptor.capture());
            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims).doesNotContainKey("role");
        }

        @Test
        @DisplayName("should include userId and accountStatus in claims")
        void shouldIncludeUserIdAndAccountStatusInClaims() throws Exception {
            // Given
            String code = "test-auth-code";
            Map<String, Object> instagramData = createInstagramData();
            User existingUser = createUser(1L, "firebase-uid-123");
            existingUser.setAccountStatus(AccountStatus.ACTIVE);
            UserSocialConnection connection = createSocialConnection(existingUser);

            when(instagramService.exchangeAuthCodeForProfile(code)).thenReturn(instagramData);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", "12345678"))
                    .thenReturn(Optional.of(connection));
            when(profilePictureProxyService.hasPreservableProfilePicture(anyString())).thenReturn(false);
            when(profilePictureProxyService.isInstagramCdnUrl(anyString())).thenReturn(false);
            when(firebaseService.getUserById("firebase-uid-123")).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "INFLUENCER"));
            when(firebaseService.generateCustomTokenWithClaims(anyString(), anyMap()))
                    .thenReturn("custom-token-123");

            // When
            oAuthCallbackService.processInstagramCallback(code, null, request, response);

            // Then
            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(firebaseService).generateCustomTokenWithClaims(eq("firebase-uid-123"), claimsCaptor.capture());
            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims.get("userId")).isEqualTo("1");
            assertThat(claims.get("accountStatus")).isEqualTo("ACTIVE");
            assertThat(claims.get("oauth")).isEqualTo(true);
        }
    }
}
