package com.sm.instagram.platform.unit.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.dto.TokenExchangeResponse;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.auth.service.EmailVerificationService;
import com.sm.instagram.platform.auth.service.TokenExchangeService;
import com.sm.instagram.platform.auth.service.TwoFactorAuthService;
import com.sm.instagram.platform.auth.social.instagram.InstagramService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.legal.ConsentRecordRepository;
import com.sm.instagram.platform.legal.ConsentCookieService;
import com.sm.instagram.platform.legal.ConsentProofPayload;
import com.sm.instagram.platform.legal.LegalConsentService;
import com.sm.instagram.platform.legal.LegalDocumentService;
import com.sm.instagram.platform.common.jwt.JwtTokenProvider;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TokenExchangeService Unit Tests")
class TokenExchangeServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCacheService userCacheService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private TwoFactorAuthService twoFactorAuthService;

    @Mock
    private FirestoreService firestoreService;

    @Mock
    private TotpFirestoreService totpFirestoreService;

    @Mock
    private InstagramService instagramService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private ConsentCookieService consentCookieService;

    @Mock
    private LegalConsentService legalConsentService;

    @Mock
    private LegalDocumentService legalDocumentService;

    @Mock
    private ConsentRecordRepository consentRecordRepository;

    @Mock
    private FirebaseToken firebaseToken;

    @Mock
    private UserRecord userRecord;

    @InjectMocks
    private TokenExchangeService tokenExchangeService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Set up @Value fields via reflection
        ReflectionTestUtils.setField(tokenExchangeService, "cookieHmacSecret", "test-hmac-secret-32-chars-long!!");
        ReflectionTestUtils.setField(tokenExchangeService, "cookieDomain", "localhost");
        ReflectionTestUtils.setField(tokenExchangeService, "secureCookies", false);
        ReflectionTestUtils.setField(tokenExchangeService, "instagramApiValidationEnabled", false);
        ReflectionTestUtils.setField(tokenExchangeService, "validatePrivilegedOnly", false);

        // Stub cookie consent check to pass (return non-null payload)
        when(consentCookieService.readConsentCookie(any(), eq(ConsentCookieService.COOKIE_CONSENT_COOKIE_POLICY), eq(ConsentProofPayload.class)))
                .thenReturn(new ConsentProofPayload());

        // Session duration configuration (seconds-based)
        ReflectionTestUtils.setField(tokenExchangeService, "fullSessionDurationSeconds", 604800);  // 7 days
        ReflectionTestUtils.setField(tokenExchangeService, "adminFullSessionDurationSeconds", 7200); // 2 hours
        ReflectionTestUtils.setField(tokenExchangeService, "partialSessionDurationSeconds", 600);   // 10 minutes

        // Create test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setFirebaseUserId("firebase-uid-123");
        testUser.setEmail("test@example.com");
        testUser.setUserType(UserType.INFLUENCER);
        testUser.setAccountStatus(AccountStatus.ACTIVE);
        testUser.setTokenVersion(1L);
        testUser.setEmailVerified(false);  // Default emailVerified status

        // Mock emailVerificationService - sync should succeed without side effects
        // Updated signature: now accepts User object instead of firebaseUid
        when(emailVerificationService.syncEmailVerificationStatus(any(User.class), anyBoolean()))
                .thenReturn(true);
    }

    private void setupMockRequest() {
        when(request.getCookies()).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 Test");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    }

    @Nested
    @DisplayName("exchangeToken - ID Token Flow")
    class ExchangeTokenIdTokenTests {

        @Test
        @DisplayName("should throw ValidationTranslatableException when idToken is null")
        void shouldThrowWhenIdTokenIsNull() {
            // Given
            setupMockRequest();

            // When/Then
            assertThatThrownBy(() -> tokenExchangeService.exchangeToken(null, 7, request, response))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when idToken is empty")
        void shouldThrowWhenIdTokenIsEmpty() {
            // Given
            setupMockRequest();

            // When/Then
            assertThatThrownBy(() -> tokenExchangeService.exchangeToken("", 7, request, response))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when idToken is whitespace")
        void shouldThrowWhenIdTokenIsWhitespace() {
            // Given
            setupMockRequest();

            // When/Then
            assertThatThrownBy(() -> tokenExchangeService.exchangeToken("   ", 7, request, response))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when expirationDays is less than 1")
        void shouldThrowWhenExpirationDaysLessThanOne() {
            // Given
            setupMockRequest();

            // When/Then
            assertThatThrownBy(() -> tokenExchangeService.exchangeToken("valid-token", 0, request, response))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when expirationDays is greater than 30")
        void shouldThrowWhenExpirationDaysGreaterThan30() {
            // Given
            setupMockRequest();

            // When/Then
            assertThatThrownBy(() -> tokenExchangeService.exchangeToken("valid-token", 31, request, response))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should default to 7 days when expirationDays is null")
        void shouldDefaultTo7DaysWhenNull() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-firebase-id-token";

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("test@example.com");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "INFLUENCER");
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(604800))).thenReturn("jwt-token");

                // When
                TokenExchangeResponse result = tokenExchangeService.exchangeToken(idToken, null, request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                verify(jwtTokenProvider).createTokenWithSecondsExpiry(anyString(), anyMap(), eq(604800));
            }
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when Firebase verification fails")
        void shouldThrowWhenFirebaseVerificationFails() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "invalid-firebase-token";

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenThrow(mock(FirebaseAuthException.class));

                // When/Then
                assertThatThrownBy(() -> tokenExchangeService.exchangeToken(idToken, 7, request, response))
                        .isInstanceOf(AuthenticationTranslatableException.class);
            }
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when user not found in database")
        void shouldThrowWhenUserNotFound() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-firebase-token";

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("test@example.com");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "USER");
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.empty());

                // When/Then - Service wraps ResourceNotFoundException in AuthenticationTranslatableException
                assertThatThrownBy(() -> tokenExchangeService.exchangeToken(idToken, 7, request, response))
                        .isInstanceOf(AuthenticationTranslatableException.class);
            }
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when user is inactive")
        void shouldThrowWhenUserIsInactive() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-firebase-token";

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("test@example.com");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "USER");
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(false);

                // When/Then
                assertThatThrownBy(() -> tokenExchangeService.exchangeToken(idToken, 7, request, response))
                        .isInstanceOf(AuthenticationTranslatableException.class);
            }
        }

        @Test
        @DisplayName("should successfully exchange token for INFLUENCER user")
        void shouldSuccessfullyExchangeTokenForInfluencer() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-firebase-token";

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("test@example.com");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "INFLUENCER");
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(604800))).thenReturn("jwt-token");

                // When
                TokenExchangeResponse result = tokenExchangeService.exchangeToken(idToken, 7, request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getUserId()).isEqualTo(1L);
                assertThat(result.getEmail()).isEqualTo("test@example.com");
                assertThat(result.getRole()).isEqualTo("INFLUENCER");
                assertThat(result.getFirebaseUid()).isEqualTo("firebase-uid-123");
            }
        }

        @Test
        @DisplayName("should successfully exchange token for COMPANY user")
        void shouldSuccessfullyExchangeTokenForCompany() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-firebase-token";
            testUser.setUserType(UserType.COMPANY);

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("company@example.com");
                when(firebaseToken.getClaims()).thenReturn(Map.of(
                        "aud", "test-project",
                        "iat", System.currentTimeMillis() / 1000,
                        "exp", (System.currentTimeMillis() / 1000) + 3600
                ));
                when(firebaseToken.getIssuer()).thenReturn("https://securetoken.google.com/test-project");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "COMPANY");
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(604800))).thenReturn("jwt-token");

                // When
                TokenExchangeResponse result = tokenExchangeService.exchangeToken(idToken, 7, request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getRole()).isEqualTo("COMPANY");
            }
        }
    }

    @Nested
    @DisplayName("exchangeToken - Role Validation")
    class ExchangeTokenRoleValidationTests {

        @Test
        @DisplayName("should default to USER role when no role claim in Firebase")
        void shouldDefaultToUserRole() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-firebase-token";

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("test@example.com");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                // Empty claims - no role set
                Map<String, Object> claims = new HashMap<>();
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(604800))).thenReturn("jwt-token");

                // When
                TokenExchangeResponse result = tokenExchangeService.exchangeToken(idToken, 7, request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getRole()).isEqualTo("USER");
            }
        }

        @Test
        @DisplayName("should handle PENDING_ADMIN role with 2FA setup required")
        void shouldHandlePendingAdminWith2FASetup() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-firebase-token";
            testUser.setUserType(UserType.PENDING_ADMIN);

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("admin@example.com");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "PENDING_ADMIN");
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(totpFirestoreService.is2FAEnabled("firebase-uid-123")).thenReturn(false);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(7200))).thenReturn("jwt-token");

                // When
                TokenExchangeResponse result = tokenExchangeService.exchangeToken(idToken, 7, request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getRole()).isEqualTo("PENDING_ADMIN");
                assertThat(result.getRequires2FASetup()).isTrue();
            }
        }

        @Test
        @DisplayName("should handle PENDING_ADMIN with existing 2FA requiring verification")
        void shouldHandlePendingAdminWithExisting2FA() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-firebase-token";
            testUser.setUserType(UserType.PENDING_ADMIN);

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("admin@example.com");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "PENDING_ADMIN");
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(totpFirestoreService.is2FAEnabled("firebase-uid-123")).thenReturn(true);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(7200))).thenReturn("jwt-token");

                // When
                TokenExchangeResponse result = tokenExchangeService.exchangeToken(idToken, 7, request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getRequires2FA()).isTrue();
                assertThat(result.getRequires2FASetup()).isFalse();
            }
        }

        @Test
        @DisplayName("should create partial session for ADMIN without 2FA verification")
        void shouldCreatePartialSessionForAdminWithout2FA() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-firebase-token";
            testUser.setUserType(UserType.ADMIN);

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("admin@example.com");
                // Token integrity verification mocks
                when(firebaseToken.getClaims()).thenReturn(Map.of(
                        "aud", "test-project",
                        "iat", System.currentTimeMillis() / 1000,
                        "exp", (System.currentTimeMillis() / 1000) + 3600
                ));
                when(firebaseToken.getIssuer()).thenReturn("https://securetoken.google.com/test-project");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "ADMIN");
                // No adminChallengeCompletedAt - 2FA not verified
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(600))).thenReturn("partial-jwt");

                // When
                TokenExchangeResponse result = tokenExchangeService.exchangeToken(idToken, 7, request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getCookieType()).isEqualTo("PARTIAL");
                assertThat(result.getSessionDuration()).isEqualTo("10 minutes");
                assertThat(result.getTwoFactorVerified()).isFalse();
            }
        }

        @Test
        @DisplayName("should create full session for ADMIN with valid 2FA verification")
        void shouldCreateFullSessionForAdminWith2FA() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-firebase-token";
            testUser.setUserType(UserType.ADMIN);

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("admin@example.com");
                when(firebaseToken.getClaims()).thenReturn(Map.of(
                        "aud", "test-project",
                        "iat", System.currentTimeMillis() / 1000,
                        "exp", (System.currentTimeMillis() / 1000) + 3600
                ));
                when(firebaseToken.getIssuer()).thenReturn("https://securetoken.google.com/test-project");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "ADMIN");
                // Valid 2FA challenge completed recently
                claims.put("adminChallengeCompletedAt", System.currentTimeMillis());
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(7200))).thenReturn("full-jwt");

                // When
                TokenExchangeResponse result = tokenExchangeService.exchangeToken(idToken, 7, request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getCookieType()).isEqualTo("FULL");
                assertThat(result.getSessionDuration()).isEqualTo("2 hours");
                assertThat(result.getTwoFactorVerified()).isTrue();
            }
        }

        @Test
        @DisplayName("should treat expired 2FA challenge as not verified")
        void shouldTreatExpired2FAAsNotVerified() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-firebase-token";
            testUser.setUserType(UserType.ADMIN);

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("admin@example.com");
                // Token integrity verification mocks
                when(firebaseToken.getClaims()).thenReturn(Map.of(
                        "aud", "test-project",
                        "iat", System.currentTimeMillis() / 1000,
                        "exp", (System.currentTimeMillis() / 1000) + 3600
                ));
                when(firebaseToken.getIssuer()).thenReturn("https://securetoken.google.com/test-project");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "ADMIN");
                // Expired 2FA challenge (5 minutes ago - past the 2 minute window)
                claims.put("adminChallengeCompletedAt", System.currentTimeMillis() - 5 * 60 * 1000);
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(600))).thenReturn("partial-jwt");

                // When
                TokenExchangeResponse result = tokenExchangeService.exchangeToken(idToken, 7, request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getCookieType()).isEqualTo("PARTIAL");
                assertThat(result.getTwoFactorVerified()).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("exchangeToken - Cookie Handling")
    class ExchangeTokenCookieTests {

        @Test
        @DisplayName("should use FirebaseIdToken from cookie when present and valid")
        void shouldUseFirebaseTokenFromCookie() throws FirebaseAuthException {
            // Given
            String cookieToken = "firebase-token-from-cookie";
            String hmacSignature = generateTestHmac(cookieToken);
            Cookie tokenCookie = new Cookie("FirebaseIdToken", cookieToken);
            Cookie sigCookie = new Cookie("FirebaseIdToken_sig", hmacSignature);
            when(request.getCookies()).thenReturn(new Cookie[]{tokenCookie, sigCookie});
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(cookieToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("test@example.com");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "INFLUENCER");
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(604800))).thenReturn("jwt-token");

                // When - pass null for idToken, should use cookie
                TokenExchangeResponse result = tokenExchangeService.exchangeToken(null, 7, request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                verify(firebaseAuth).verifyIdToken(cookieToken);
            }
        }

        @Test
        @DisplayName("should use OAuth cookie for custom token flow")
        void shouldUseOAuthCookieForCustomTokenFlow() throws FirebaseAuthException {
            // Given
            // Create a mock custom token JWT format (header.payload.signature)
            String customToken = createMockCustomToken("firebase-uid-123");
            String hmacSignature = generateTestHmac(customToken);
            Cookie tokenCookie = new Cookie("oauth_token", customToken);
            Cookie sigCookie = new Cookie("oauth_sig", hmacSignature);
            when(request.getCookies()).thenReturn(new Cookie[]{tokenCookie, sigCookie});
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);
                when(userRecord.getEmail()).thenReturn("oauth@example.com");

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "INFLUENCER");
                when(userRecord.getCustomClaims()).thenReturn(claims);

                // Mock Firestore Instagram data
                Map<String, Object> instagramDoc = new HashMap<>();
                instagramDoc.put("instagramId", "insta-123");
                instagramDoc.put("username", "testuser");
                when(firestoreService.getInstagramUserDataByFirebaseUid("firebase-uid-123")).thenReturn(instagramDoc);

                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(604800))).thenReturn("jwt-token");

                // When - pass null for idToken, should use OAuth cookie
                TokenExchangeResponse result = tokenExchangeService.exchangeToken(null, 7, request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
            }
        }

        @Test
        @DisplayName("should ignore cookies with invalid HMAC signature")
        void shouldIgnoreCookiesWithInvalidHmac() {
            // Given
            String cookieToken = "firebase-token-from-cookie";
            Cookie tokenCookie = new Cookie("FirebaseIdToken", cookieToken);
            Cookie sigCookie = new Cookie("FirebaseIdToken_sig", "invalid-hmac");
            when(request.getCookies()).thenReturn(new Cookie[]{tokenCookie, sigCookie});
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

            // When/Then - should fall through to requiring idToken
            assertThatThrownBy(() -> tokenExchangeService.exchangeToken(null, 7, request, response))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("createOAuthSessionCookies")
    class CreateOAuthSessionCookiesTests {

        @Test
        @DisplayName("should throw when user is inactive")
        void shouldThrowWhenUserIsInactive() {
            // Given
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> tokenExchangeService.createOAuthSessionCookies(
                    testUser, "firebase-uid-123", request, response))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should create session cookies for active user")
        void shouldCreateSessionCookiesForActiveUser() throws FirebaseAuthException {
            // Given
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
            when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(604800))).thenReturn("jwt-token");

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "INFLUENCER");
                when(userRecord.getCustomClaims()).thenReturn(claims);

                // When
                tokenExchangeService.createOAuthSessionCookies(testUser, "firebase-uid-123", request, response);

                // Then
                verify(response, atLeast(2)).addHeader(eq("Set-Cookie"), anyString());
            }
        }

        @Test
        @DisplayName("should mark session as OAuth authenticated")
        void shouldMarkSessionAsOAuthAuthenticated() throws FirebaseAuthException {
            // Given
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
            when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(604800))).thenReturn("jwt-token");

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "INFLUENCER");
                when(userRecord.getCustomClaims()).thenReturn(claims);

                // When
                tokenExchangeService.createOAuthSessionCookies(testUser, "firebase-uid-123", request, response);

                // Then - verify JWT was created with OAuth claims
                verify(jwtTokenProvider).createTokenWithSecondsExpiry(
                        eq("firebase-uid-123"),
                        argThat(map ->
                                Boolean.TRUE.equals(map.get("oauth")) &&
                                "instagram".equals(map.get("provider"))
                        ),
                        eq(604800)
                );
            }
        }
    }

    @Nested
    @DisplayName("createTwoFactorVerifiedSession")
    class CreateTwoFactorVerifiedSessionTests {

        @Test
        @DisplayName("should throw when user is inactive")
        void shouldThrowWhenUserIsInactive() {
            // Given
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> tokenExchangeService.createTwoFactorVerifiedSession(
                    testUser, "firebase-uid-123", request, response))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should create full session with 2FA verified claims")
        void shouldCreateFullSessionWith2FAVerified() throws FirebaseAuthException {
            // Given
            testUser.setUserType(UserType.ADMIN);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
            when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(7200))).thenReturn("jwt-token");

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "ADMIN");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);
                when(userRecord.getCustomClaims()).thenReturn(claims);

                // When
                TokenExchangeResponse result = tokenExchangeService.createTwoFactorVerifiedSession(
                        testUser, "firebase-uid-123", request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getTwoFactorVerified()).isTrue();
                assertThat(result.getCookieType()).isEqualTo("FULL");
                assertThat(result.getSessionDuration()).isEqualTo("2 hours");
            }
        }

        @Test
        @DisplayName("should include correct claims for PENDING_ADMIN after 2FA verification")
        void shouldIncludeCorrectClaimsForPendingAdmin() throws FirebaseAuthException {
            // Given
            testUser.setUserType(UserType.PENDING_ADMIN);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
            when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(7200))).thenReturn("jwt-token");

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "PENDING_ADMIN");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);
                when(userRecord.getCustomClaims()).thenReturn(claims);

                // When
                TokenExchangeResponse result = tokenExchangeService.createTwoFactorVerifiedSession(
                        testUser, "firebase-uid-123", request, response);

                // Then - verify JWT was created with canAccessAdmin = true after 2FA
                verify(jwtTokenProvider).createTokenWithSecondsExpiry(
                        eq("firebase-uid-123"),
                        argThat(map -> Boolean.TRUE.equals(map.get("canAccessAdmin"))),
                        eq(7200)
                );
            }
        }
    }

    @Nested
    @DisplayName("createLimited2FASession")
    class CreateLimited2FASessionTests {

        @Test
        @DisplayName("should create limited session with short expiration")
        void shouldCreateLimitedSessionWithShortExpiration() throws FirebaseAuthException {
            // Given
            testUser.setUserType(UserType.ADMIN);
            when(jwtTokenProvider.createTokenWithClaims(anyString(), anyMap(), anyInt())).thenReturn("limited-jwt");

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "ADMIN");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);
                when(userRecord.getCustomClaims()).thenReturn(claims);

                // When
                TokenExchangeResponse result = tokenExchangeService.createLimited2FASession(
                        testUser, "firebase-uid-123", request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getRequires2FA()).isTrue();
                assertThat(result.getTwoFactorVerified()).isFalse();
            }
        }

        @Test
        @DisplayName("should mark session as limited for 2FA")
        void shouldMarkSessionAsLimitedFor2FA() throws FirebaseAuthException {
            // Given
            testUser.setUserType(UserType.ADMIN);
            when(jwtTokenProvider.createTokenWithClaims(anyString(), anyMap(), anyInt())).thenReturn("limited-jwt");

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "ADMIN");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);
                when(userRecord.getCustomClaims()).thenReturn(claims);

                // When
                tokenExchangeService.createLimited2FASession(testUser, "firebase-uid-123", request, response);

                // Then - verify JWT was created with limitedFor2FA claim
                verify(jwtTokenProvider).createTokenWithClaims(
                        eq("firebase-uid-123"),
                        argThat(map -> Boolean.TRUE.equals(map.get("limitedFor2FA"))),
                        anyInt()
                );
            }
        }
    }

    @Nested
    @DisplayName("clearSessionCookies")
    class ClearSessionCookiesTests {

        @Test
        @DisplayName("should clear all session cookies")
        void shouldClearAllSessionCookies() {
            // When
            tokenExchangeService.clearSessionCookies(response);

            // Then - verify cookies are cleared (at least 6 cookies cleared)
            verify(response, atLeast(6)).addHeader(eq("Set-Cookie"), argThat(cookie ->
                    cookie.contains("Max-Age=0")
            ));
        }

        @Test
        @DisplayName("should clear regular session cookies")
        void shouldClearRegularSessionCookies() {
            // When
            tokenExchangeService.clearSessionCookies(response);

            // Then
            verify(response).addHeader(eq("Set-Cookie"), argThat(cookie ->
                    cookie.startsWith("session=") && cookie.contains("Max-Age=0")
            ));
            verify(response).addHeader(eq("Set-Cookie"), argThat(cookie ->
                    cookie.startsWith("session_sig=") && cookie.contains("Max-Age=0")
            ));
        }

        @Test
        @DisplayName("should clear partial session cookies")
        void shouldClearPartialSessionCookies() {
            // When
            tokenExchangeService.clearSessionCookies(response);

            // Then
            verify(response).addHeader(eq("Set-Cookie"), argThat(cookie ->
                    cookie.startsWith("partialSession=") && cookie.contains("Max-Age=0")
            ));
            verify(response).addHeader(eq("Set-Cookie"), argThat(cookie ->
                    cookie.startsWith("partialSessionSig=") && cookie.contains("Max-Age=0")
            ));
        }
    }

    @Nested
    @DisplayName("clearPartialSessionCookies")
    class ClearPartialSessionCookiesTests {

        @Test
        @DisplayName("should clear only partial session cookies")
        void shouldClearOnlyPartialSessionCookies() {
            // When
            tokenExchangeService.clearPartialSessionCookies(response);

            // Then
            verify(response, times(2)).addHeader(eq("Set-Cookie"), anyString());
            verify(response).addHeader(eq("Set-Cookie"), argThat(cookie ->
                    cookie.startsWith("partialSession=")
            ));
            verify(response).addHeader(eq("Set-Cookie"), argThat(cookie ->
                    cookie.startsWith("partialSessionSig=")
            ));
        }
    }

    @Nested
    @DisplayName("extractFirebaseUserIdFromPartialToken")
    class ExtractFirebaseUserIdFromPartialTokenTests {

        @Test
        @DisplayName("should return null when no cookies")
        void shouldReturnNullWhenNoCookies() {
            // Given
            when(request.getCookies()).thenReturn(null);

            // When
            String result = tokenExchangeService.extractFirebaseUserIdFromPartialToken(request);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should extract UID from session cookie")
        void shouldExtractUidFromSessionCookie() {
            // Given
            Cookie sessionCookie = new Cookie("session", "jwt-token");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});
            when(jwtTokenProvider.getSubject("jwt-token")).thenReturn("firebase-uid-123");

            // When
            String result = tokenExchangeService.extractFirebaseUserIdFromPartialToken(request);

            // Then
            assertThat(result).isEqualTo("firebase-uid-123");
        }

        @Test
        @DisplayName("should extract UID from partial session cookie")
        void shouldExtractUidFromPartialSessionCookie() {
            // Given
            Cookie partialCookie = new Cookie("partialSession", "partial-jwt");
            when(request.getCookies()).thenReturn(new Cookie[]{partialCookie});
            when(jwtTokenProvider.getSubject("partial-jwt")).thenReturn("firebase-uid-123");

            // When
            String result = tokenExchangeService.extractFirebaseUserIdFromPartialToken(request);

            // Then
            assertThat(result).isEqualTo("firebase-uid-123");
        }

        @Test
        @DisplayName("should prioritize full session over partial session")
        void shouldPrioritizeFullSessionOverPartial() {
            // Given
            Cookie sessionCookie = new Cookie("session", "full-jwt");
            Cookie partialCookie = new Cookie("partialSession", "partial-jwt");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, partialCookie});
            when(jwtTokenProvider.getSubject("full-jwt")).thenReturn("full-uid");

            // When
            String result = tokenExchangeService.extractFirebaseUserIdFromPartialToken(request);

            // Then
            assertThat(result).isEqualTo("full-uid");
            verify(jwtTokenProvider, never()).getSubject("partial-jwt");
        }

        @Test
        @DisplayName("should return null when token extraction fails")
        void shouldReturnNullWhenTokenExtractionFails() {
            // Given
            Cookie sessionCookie = new Cookie("session", "invalid-jwt");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});
            when(jwtTokenProvider.getSubject("invalid-jwt")).thenThrow(new RuntimeException("Invalid token"));

            // When
            String result = tokenExchangeService.extractFirebaseUserIdFromPartialToken(request);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("createRefreshedSession")
    class CreateRefreshedSessionTests {

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            // Given
            when(userRepository.findByFirebaseUserId("unknown-uid")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> tokenExchangeService.createRefreshedSession(
                    "unknown-uid", request, response))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when user is inactive")
        void shouldThrowWhenUserIsInactive() {
            // Given
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> tokenExchangeService.createRefreshedSession(
                    "firebase-uid-123", request, response))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        /**
         * BUG-21 (revised 2026-05-31): a BANNED user IS allowed to refresh their session.
         * The BannedUserAuthorizationFilter still blocks every non-whitelisted endpoint, so a
         * refreshed token grants no extra reach — it only lets the banned user load the
         * whitelisted ban page (/users/me) to see why they are banned and contact support.
         * The refreshed JWT carries accountStatus=BANNED so the frontend renders the ban UI.
         * (The original BUG-21 denied refresh entirely, which hard-locked banned users out of
         * the ban page and contradicted the whitelist design + the session-security e2e contract.)
         */
        @Test
        @DisplayName("BUG-21 (revised): should refresh a BANNED user so they can load the whitelisted ban page")
        void shouldRefreshBannedUserSoTheyCanSeeBanPage() throws FirebaseAuthException {
            // Given: a BANNED user — isUserActive returns true (cache semantics keep them able to authenticate)
            testUser.setAccountStatus(AccountStatus.BANNED);
            testUser.setTokenVersion(9L);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
            when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(604800))).thenReturn("refreshed-jwt");

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "INFLUENCER");
                when(userRecord.getCustomClaims()).thenReturn(claims);

                // When: a BANNED user refreshes
                TokenExchangeResponse result = tokenExchangeService.createRefreshedSession(
                        "firebase-uid-123", request, response);

                // Then: refresh succeeds and the JWT carries accountStatus=BANNED for the FE ban page
                assertThat(result.isSuccess()).isTrue();
                verify(jwtTokenProvider).createTokenWithSecondsExpiry(
                        eq("firebase-uid-123"),
                        argThat(map -> "BANNED".equals(map.get("accountStatus"))),
                        eq(604800)
                );
            }
        }

        @Test
        @DisplayName("should create refreshed session with updated token version")
        void shouldCreateRefreshedSessionWithUpdatedTokenVersion() throws FirebaseAuthException {
            // Given
            testUser.setTokenVersion(5L);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
            when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(604800))).thenReturn("refreshed-jwt");

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "INFLUENCER");
                when(userRecord.getCustomClaims()).thenReturn(claims);

                // When
                TokenExchangeResponse result = tokenExchangeService.createRefreshedSession(
                        "firebase-uid-123", request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                verify(jwtTokenProvider).createTokenWithSecondsExpiry(
                        eq("firebase-uid-123"),
                        argThat(map -> Long.valueOf(5L).equals(map.get("tokenVersion"))),
                        eq(604800)
                );
            }
        }

        @Test
        @DisplayName("should refresh session for ADMIN with 2FA enabled in Firestore")
        void shouldRefreshSessionForAdminWith2FAEnabled() throws FirebaseAuthException {
            // Given
            testUser.setUserType(UserType.ADMIN);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
            when(twoFactorAuthService.is2FAEnabled("firebase-uid-123")).thenReturn(true);
            when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), eq(7200))).thenReturn("refreshed-jwt");

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "ADMIN");
                when(userRecord.getCustomClaims()).thenReturn(claims);

                // When
                TokenExchangeResponse result = tokenExchangeService.createRefreshedSession(
                        "firebase-uid-123", request, response);

                // Then
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getTwoFactorVerified()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Token Integrity Verification")
    class TokenIntegrityVerificationTests {

        @Test
        @DisplayName("should verify token integrity for ADMIN role")
        void shouldVerifyTokenIntegrityForAdmin() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-admin-token";
            testUser.setUserType(UserType.ADMIN);

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("admin@example.com");
                when(firebaseToken.getIssuer()).thenReturn("https://securetoken.google.com/test-project");
                when(firebaseToken.getClaims()).thenReturn(Map.of(
                        "aud", "test-project",
                        "iat", System.currentTimeMillis() / 1000,
                        "exp", (System.currentTimeMillis() / 1000) + 3600
                ));
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "ADMIN");
                claims.put("adminChallengeCompletedAt", System.currentTimeMillis());
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), anyInt())).thenReturn("jwt");

                // When
                TokenExchangeResponse result = tokenExchangeService.exchangeToken(idToken, 7, request, response);

                // Then - should succeed if integrity checks pass
                assertThat(result.isSuccess()).isTrue();
            }
        }

        @Test
        @DisplayName("should reject ADMIN token with invalid issuer")
        void shouldRejectAdminTokenWithInvalidIssuer() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "invalid-issuer-token";
            testUser.setUserType(UserType.ADMIN);

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("admin@example.com");
                when(firebaseToken.getIssuer()).thenReturn("https://invalid-issuer.com");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "ADMIN");
                claims.put("adminChallengeCompletedAt", System.currentTimeMillis());
                when(userRecord.getCustomClaims()).thenReturn(claims);

                // When/Then
                assertThatThrownBy(() -> tokenExchangeService.exchangeToken(idToken, 7, request, response))
                        .isInstanceOf(AuthenticationTranslatableException.class);
            }
        }
    }

    @Nested
    @DisplayName("IP Address Extraction")
    class IpAddressExtractionTests {

        @Test
        @DisplayName("should extract IP from X-Forwarded-For header")
        void shouldExtractIpFromXForwardedFor() throws FirebaseAuthException {
            // Given
            when(request.getCookies()).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 192.168.1.1");
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            String idToken = "valid-token";

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("test@example.com");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "USER");
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), anyInt())).thenReturn("jwt");

                // When
                tokenExchangeService.exchangeToken(idToken, 7, request, response);

                // Then - verify JWT contains first IP from X-Forwarded-For
                verify(jwtTokenProvider).createTokenWithSecondsExpiry(
                        anyString(),
                        argThat(map -> "10.0.0.1".equals(map.get("sessionIp"))),
                        anyInt()
                );
            }
        }

        @Test
        @DisplayName("should fallback to remote address when no proxy headers")
        void shouldFallbackToRemoteAddress() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-token";

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("test@example.com");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "USER");
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), anyInt())).thenReturn("jwt");

                // When
                tokenExchangeService.exchangeToken(idToken, 7, request, response);

                // Then - verify JWT contains remote address
                verify(jwtTokenProvider).createTokenWithSecondsExpiry(
                        anyString(),
                        argThat(map -> "127.0.0.1".equals(map.get("sessionIp"))),
                        anyInt()
                );
            }
        }
    }

    @Nested
    @DisplayName("Session Fingerprint")
    class SessionFingerprintTests {

        @Test
        @DisplayName("should include session fingerprint in JWT claims")
        void shouldIncludeSessionFingerprintInClaims() throws FirebaseAuthException {
            // Given
            setupMockRequest();
            String idToken = "valid-token";

            try (MockedStatic<FirebaseAuth> mockedFirebaseAuth = mockStatic(FirebaseAuth.class)) {
                mockedFirebaseAuth.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
                when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
                when(firebaseToken.getUid()).thenReturn("firebase-uid-123");
                when(firebaseToken.getEmail()).thenReturn("test@example.com");
                when(firebaseAuth.getUser("firebase-uid-123")).thenReturn(userRecord);

                Map<String, Object> claims = new HashMap<>();
                claims.put("role", "USER");
                when(userRecord.getCustomClaims()).thenReturn(claims);
                when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(testUser));
                when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
                when(jwtTokenProvider.createTokenWithSecondsExpiry(anyString(), anyMap(), anyInt())).thenReturn("jwt");

                // When
                tokenExchangeService.exchangeToken(idToken, 7, request, response);

                // Then - verify fingerprint is included
                verify(jwtTokenProvider).createTokenWithSecondsExpiry(
                        anyString(),
                        argThat(map ->
                                map.containsKey("fingerprint") &&
                                map.containsKey("sessionIp") &&
                                map.containsKey("sessionUA") &&
                                map.containsKey("sessionStart")
                        ),
                        anyInt()
                );
            }
        }
    }

    // Helper methods

    private String generateTestHmac(String data) {
        // Generate HMAC using the same secret as in setUp
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                    "test-hmac-secret-32-chars-long!!".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test HMAC", e);
        }
    }

    private String createMockCustomToken(String uid) {
        // Create a minimal JWT-like structure for testing
        String header = java.util.Base64.getUrlEncoder().encodeToString(
                "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes()
        );
        String payload = java.util.Base64.getUrlEncoder().encodeToString(
                ("{\"uid\":\"" + uid + "\",\"sub\":\"service@firebase.com\"}").getBytes()
        );
        String signature = java.util.Base64.getUrlEncoder().encodeToString("signature".getBytes());
        return header + "." + payload + "." + signature;
    }
}
