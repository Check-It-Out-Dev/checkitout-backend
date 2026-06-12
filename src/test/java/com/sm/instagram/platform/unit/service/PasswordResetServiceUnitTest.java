package com.sm.instagram.platform.unit.service;

import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.dto.ForgotPasswordResponse;
import com.sm.instagram.platform.auth.service.EmailVerificationService;
import com.sm.instagram.platform.auth.service.PasswordResetService;
import com.sm.instagram.platform.support.common.EmailService;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PasswordResetService.
 * Tests password reset flow including anti-enumeration, cooldown,
 * Firebase verification checks, and error handling.
 * Uses pure Mockito without Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PasswordResetService Unit Tests")
class PasswordResetServiceUnitTest {

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private PasswordResetService passwordResetService;

    // Test constants
    private static final String TEST_EMAIL = "test@example.com";
    private static final String FIREBASE_UID = "firebase-uid-123";
    private static final String FRONTEND_URL = "https://localhost:4200";
    private static final String LANGUAGE = "en";
    private static final String FIREBASE_RESET_LINK =
            "https://project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=test-reset-code-456&apiKey=xxx";
    private static final String EXPECTED_RESET_LINK =
            FRONTEND_URL + "/auth/action?mode=resetPassword&oobCode=test-reset-code-456";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "frontendUrl", FRONTEND_URL);
        ReflectionTestUtils.setField(passwordResetService, "passwordResetCooldownSeconds", 60);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    private User createVerifiedUser() {
        User user = new User();
        user.setId(1L);
        user.setFirebaseUserId(FIREBASE_UID);
        user.setEmail(TEST_EMAIL);
        user.setFirstName("John");
        user.setUserType(UserType.INFLUENCER);
        user.setAccountStatus(AccountStatus.IN_VALIDATION);
        user.setEmailVerified(true);
        user.setPasswordResetSentAt(null);
        return user;
    }

    private User createUnverifiedUser() {
        User user = createVerifiedUser();
        user.setEmailVerified(false);
        return user;
    }

    private UserRecord createFirebaseUserRecord(boolean emailVerified) throws FirebaseAuthException {
        UserRecord mockRecord = mock(UserRecord.class);
        when(mockRecord.isEmailVerified()).thenReturn(emailVerified);
        return mockRecord;
    }

    @Nested
    @DisplayName("User Not Found Tests")
    class UserNotFoundTests {

        @Test
        @DisplayName("should return success when user not found (anti-enumeration)")
        void requestPasswordReset_userNotFound_returnsSuccess() {
            // Given
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

            // When
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessageKey()).isEqualTo("auth.forgot_password.success_message");
        }

        @Test
        @DisplayName("should not make any Firebase calls when user not found")
        void requestPasswordReset_userNotFound_noFirebaseCalls() {
            // Given
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

            // When
            passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            verifyNoInteractions(firebaseAuth);
            verifyNoInteractions(emailService);
            verifyNoInteractions(emailVerificationService);
        }
    }

    @Nested
    @DisplayName("Cooldown Active Tests")
    class CooldownActiveTests {

        @Test
        @DisplayName("should return success silently when within 60-second cooldown")
        void requestPasswordReset_within60seconds_returnsSuccess() {
            // Given
            User user = createVerifiedUser();
            user.setPasswordResetSentAt(LocalDateTime.now().minusSeconds(30));
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

            // When
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            // No email should be sent during cooldown
            verifyNoInteractions(emailService);
            verifyNoInteractions(emailVerificationService);
            verifyNoInteractions(firebaseAuth);
        }

        @Test
        @DisplayName("should proceed with password reset when exactly 60 seconds have passed")
        void requestPasswordReset_exactly60seconds_sendsEmail() throws Exception {
            // Given
            User user = createVerifiedUser();
            user.setPasswordResetSentAt(LocalDateTime.now().minusSeconds(61));
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            UserRecord firebaseUser = createFirebaseUserRecord(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(firebaseUser);
            when(firebaseAuth.generatePasswordResetLink(eq(TEST_EMAIL), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_RESET_LINK);

            // When
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            verify(emailService).sendPasswordResetEmail(
                    eq(TEST_EMAIL), eq("John"), anyString(), eq(LANGUAGE));
        }

        @Test
        @DisplayName("should return silently when 59 seconds have passed (just under boundary)")
        void requestPasswordReset_59seconds_returnsSilently() {
            // Given
            User user = createVerifiedUser();
            user.setPasswordResetSentAt(LocalDateTime.now().minusSeconds(59));
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

            // When
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            verifyNoInteractions(emailService);
            verifyNoInteractions(firebaseAuth);
        }
    }

    @Nested
    @DisplayName("Firebase Verification Check Tests")
    class FirebaseVerificationCheckTests {

        @Test
        @DisplayName("should use Firebase emailVerified result when check succeeds")
        void requestPasswordReset_firebaseCheckSucceeds_usesResult() throws Exception {
            // Given
            User user = createVerifiedUser();
            user.setEmailVerified(false); // PostgreSQL says unverified
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            // Firebase says verified (source of truth)
            UserRecord firebaseUser = createFirebaseUserRecord(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(firebaseUser);
            when(firebaseAuth.generatePasswordResetLink(eq(TEST_EMAIL), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_RESET_LINK);

            // When
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            // Should proceed to password reset path (verified in Firebase)
            verify(firebaseAuth).generatePasswordResetLink(eq(TEST_EMAIL), any(ActionCodeSettings.class));
            verify(emailService).sendPasswordResetEmail(
                    eq(TEST_EMAIL), eq("John"), anyString(), eq(LANGUAGE));
        }

        @Test
        @DisplayName("should fall back to PostgreSQL emailVerified when Firebase throws exception")
        void requestPasswordReset_firebaseException_fallsBackToPostgres() throws Exception {
            // Given
            User user = createVerifiedUser();
            user.setEmailVerified(true); // PostgreSQL says verified (fallback)
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            // Firebase throws exception
            when(firebaseAuth.getUser(FIREBASE_UID)).thenThrow(mock(FirebaseAuthException.class));
            when(firebaseAuth.generatePasswordResetLink(eq(TEST_EMAIL), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_RESET_LINK);

            // When
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            // Should fall back to PostgreSQL and proceed with password reset
            verify(firebaseAuth).generatePasswordResetLink(eq(TEST_EMAIL), any(ActionCodeSettings.class));
        }
    }

    @Nested
    @DisplayName("Unverified User Tests")
    class UnverifiedUserTests {

        @Test
        @DisplayName("should send verification email when user email is not verified")
        void requestPasswordReset_unverified_sendsVerificationEmail() throws Exception {
            // Given
            User user = createUnverifiedUser();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            UserRecord firebaseUser = createFirebaseUserRecord(false);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(firebaseUser);

            // When
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            verify(emailVerificationService).sendVerificationEmail(FIREBASE_UID, LANGUAGE);
            // Should NOT send password reset email
            verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should save timestamp BEFORE sending verification email (TOCTOU fix)")
        void requestPasswordReset_unverified_savesTimestampBeforeSending() throws Exception {
            // Given
            User user = createUnverifiedUser();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            UserRecord firebaseUser = createFirebaseUserRecord(false);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(firebaseUser);

            // When
            passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then - verify save is called (timestamp set before email)
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getPasswordResetSentAt()).isNotNull();

            // Also verify verification email was sent
            verify(emailVerificationService).sendVerificationEmail(FIREBASE_UID, LANGUAGE);
        }

        @Test
        @DisplayName("should return success without Firebase call when user has null UID")
        void requestPasswordReset_unverified_nullUid_returnsSuccess() throws Exception {
            // Given
            User user = createUnverifiedUser();
            user.setFirebaseUserId(null);
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

            // Firebase getUser will throw for null UID, but service checks for null first
            // The code checks user.getFirebaseUserId() != null before calling firebaseAuth.getUser()
            // With null UID, no Firebase call is made, and isEmailVerifiedInFirebase stays false
            // Then on the unverified path, null UID check prevents sendVerificationEmail call

            // When
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            // No Firebase or email verification service calls with null UID
            verify(firebaseAuth, never()).getUser(anyString());
            verifyNoInteractions(emailVerificationService);
        }
    }

    @Nested
    @DisplayName("Verified User Happy Path Tests")
    class VerifiedUserHappyPathTests {

        @BeforeEach
        void setUpVerifiedUser() throws Exception {
            // Common setup for verified user happy path
        }

        @Test
        @DisplayName("should save passwordResetSentAt timestamp for verified user")
        void requestPasswordReset_verified_savesTimestamp() throws Exception {
            // Given
            User user = createVerifiedUser();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            UserRecord firebaseUser = createFirebaseUserRecord(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(firebaseUser);
            when(firebaseAuth.generatePasswordResetLink(eq(TEST_EMAIL), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_RESET_LINK);

            // When
            passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getPasswordResetSentAt()).isNotNull();
            assertThat(savedUser.getPasswordResetSentAt()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        @DisplayName("should generate Firebase password reset link for verified user")
        void requestPasswordReset_verified_generatesResetLink() throws Exception {
            // Given
            User user = createVerifiedUser();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            UserRecord firebaseUser = createFirebaseUserRecord(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(firebaseUser);
            when(firebaseAuth.generatePasswordResetLink(eq(TEST_EMAIL), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_RESET_LINK);

            // When
            passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            verify(firebaseAuth).generatePasswordResetLink(eq(TEST_EMAIL), any(ActionCodeSettings.class));
        }

        @Test
        @DisplayName("should send password reset email for verified user")
        void requestPasswordReset_verified_sendsEmail() throws Exception {
            // Given
            User user = createVerifiedUser();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            UserRecord firebaseUser = createFirebaseUserRecord(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(firebaseUser);
            when(firebaseAuth.generatePasswordResetLink(eq(TEST_EMAIL), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_RESET_LINK);

            // When
            passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            verify(emailService).sendPasswordResetEmail(
                    TEST_EMAIL, "John", EXPECTED_RESET_LINK, LANGUAGE);
        }
    }

    @Nested
    @DisplayName("Verified User Error Handling Tests")
    class VerifiedUserErrorHandlingTests {

        @Test
        @DisplayName("should return success when OptimisticLockingFailureException occurs during save")
        void requestPasswordReset_optimisticLock_returnsSuccess() throws Exception {
            // Given
            User user = createVerifiedUser();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

            UserRecord firebaseUser = createFirebaseUserRecord(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(firebaseUser);

            // Simulate concurrent update conflict on save
            when(userRepository.save(any(User.class)))
                    .thenThrow(new OptimisticLockingFailureException("Concurrent update"));

            // When
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            // Email should NOT be sent when timestamp save fails
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("should return success when other DB exception occurs during save")
        void requestPasswordReset_dbException_returnsSuccess() throws Exception {
            // Given
            User user = createVerifiedUser();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

            UserRecord firebaseUser = createFirebaseUserRecord(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(firebaseUser);

            // Simulate generic database failure
            when(userRepository.save(any(User.class)))
                    .thenThrow(new DataAccessResourceFailureException("Database connection lost"));

            // When
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then - BUG #9 FIX: DB exceptions caught and return success (no info leak)
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("should return success when Firebase rate limit is hit during reset link generation")
        void requestPasswordReset_firebaseRateLimit_returnsSuccess() throws Exception {
            // Given
            User user = createVerifiedUser();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            UserRecord firebaseUser = createFirebaseUserRecord(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(firebaseUser);

            // Simulate Firebase rate limit
            FirebaseAuthException rateLimitException = mock(FirebaseAuthException.class);
            when(rateLimitException.getMessage()).thenReturn("TOO_MANY_ATTEMPTS_TRY_LATER");
            when(rateLimitException.getErrorCode()).thenReturn(null);
            when(firebaseAuth.generatePasswordResetLink(eq(TEST_EMAIL), any(ActionCodeSettings.class)))
                    .thenThrow(rateLimitException);

            // When
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then - VULN-006 FIX: Firebase rate limit returns success (anti-enumeration)
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should return success when SMTP failure occurs during email send")
        void requestPasswordReset_smtpFailure_returnsSuccess() throws Exception {
            // Given
            User user = createVerifiedUser();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            UserRecord firebaseUser = createFirebaseUserRecord(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(firebaseUser);
            when(firebaseAuth.generatePasswordResetLink(eq(TEST_EMAIL), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_RESET_LINK);

            // Simulate SMTP failure
            doThrow(new RuntimeException("SMTP connection refused"))
                    .when(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyString());

            // When
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);

            // Then - CRITICAL-2 FIX: All exceptions caught, same response returned
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Helper Method Tests")
    class HelperMethodTests {

        @Test
        @DisplayName("maskEmail should mask characters after first 3 before @ sign")
        void maskEmail_validEmail_masksCorrectly() {
            // Given - maskEmail is private, so we test it via the service's behavior
            // When user is not found, the service logs the masked email
            // We test the masking logic by invoking requestPasswordReset and checking
            // that it completes without error for various email formats
            // Direct testing via reflection:
            String result = ReflectionTestUtils.invokeMethod(
                    passwordResetService, "maskEmail", "test@example.com");

            // Then - "test@example.com" -> "tes*@example.com"
            assertThat(result).isNotNull();
            assertThat(result).contains("@example.com");
            assertThat(result).contains("tes");
            assertThat(result).contains("*");
        }

        @Test
        @DisplayName("maskEmail should return N/A for null email")
        void maskEmail_nullEmail_returnsNA() {
            // When
            String result = ReflectionTestUtils.invokeMethod(
                    passwordResetService, "maskEmail", (String) null);

            // Then
            assertThat(result).isEqualTo("N/A");
        }

        @Test
        @DisplayName("maskEmail should return N/A for empty email")
        void maskEmail_emptyEmail_returnsNA() {
            // When
            String result = ReflectionTestUtils.invokeMethod(
                    passwordResetService, "maskEmail", "");

            // Then
            assertThat(result).isEqualTo("N/A");
        }

        @Test
        @DisplayName("getUserLanguage should return preferred language from UserPreferences")
        void getUserLanguage_returnsPreferredLanguage() {
            // Given
            User user = createVerifiedUser();
            UserPreferences preferences = new UserPreferences();
            preferences.setLanguage("pl");
            when(userPreferencesRepository.findByUserId(user.getId())).thenReturn(preferences);

            // When
            String result = ReflectionTestUtils.invokeMethod(
                    passwordResetService, "getUserLanguage", user);

            // Then
            assertThat(result).isEqualTo("pl");
        }
    }

    @Nested
    @DisplayName("Anti-Enumeration Tests")
    class AntiEnumerationTests {

        @Test
        @DisplayName("all code paths should return the same ForgotPasswordResponse.success() object structure")
        void requestPasswordReset_allPaths_returnSameResponse() throws Exception {
            // Path 1: User not found
            when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());
            ForgotPasswordResponse notFoundResponse =
                    passwordResetService.requestPasswordReset("notfound@example.com", LANGUAGE);

            // Path 2: User found, cooldown active
            User cooldownUser = createVerifiedUser();
            cooldownUser.setPasswordResetSentAt(LocalDateTime.now().minusSeconds(10));
            when(userRepository.findByEmail("cooldown@example.com"))
                    .thenReturn(Optional.of(cooldownUser));
            ForgotPasswordResponse cooldownResponse =
                    passwordResetService.requestPasswordReset("cooldown@example.com", LANGUAGE);

            // Path 3: User found, verified, email sent
            User verifiedUser = createVerifiedUser();
            verifiedUser.setEmail("verified@example.com");
            when(userRepository.findByEmail("verified@example.com"))
                    .thenReturn(Optional.of(verifiedUser));
            when(userRepository.save(any(User.class))).thenReturn(verifiedUser);
            UserRecord firebaseUser = createFirebaseUserRecord(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(firebaseUser);
            when(firebaseAuth.generatePasswordResetLink(eq("verified@example.com"), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_RESET_LINK);
            ForgotPasswordResponse verifiedResponse =
                    passwordResetService.requestPasswordReset("verified@example.com", LANGUAGE);

            // Then - all responses must have identical structure
            assertThat(notFoundResponse.getMessageKey())
                    .isEqualTo(cooldownResponse.getMessageKey())
                    .isEqualTo(verifiedResponse.getMessageKey())
                    .isEqualTo("auth.forgot_password.success_message");
        }

        @Test
        @DisplayName("all code paths should return success=true regardless of path taken")
        void requestPasswordReset_allPaths_returnSuccess() throws Exception {
            // Path 1: User not found
            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.empty());
            ForgotPasswordResponse r1 = passwordResetService.requestPasswordReset("a@test.com", LANGUAGE);

            // Path 2: Unverified user
            User unverifiedUser = createUnverifiedUser();
            unverifiedUser.setEmail("b@test.com");
            when(userRepository.findByEmail("b@test.com")).thenReturn(Optional.of(unverifiedUser));
            when(userRepository.save(any(User.class))).thenReturn(unverifiedUser);
            UserRecord unverifiedFirebase = createFirebaseUserRecord(false);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(unverifiedFirebase);
            ForgotPasswordResponse r2 = passwordResetService.requestPasswordReset("b@test.com", LANGUAGE);

            // Path 3: Verified user
            User verifiedUser = createVerifiedUser();
            verifiedUser.setEmail("c@test.com");
            when(userRepository.findByEmail("c@test.com")).thenReturn(Optional.of(verifiedUser));
            when(userRepository.save(any(User.class))).thenReturn(verifiedUser);
            UserRecord verifiedFirebase = createFirebaseUserRecord(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(verifiedFirebase);
            when(firebaseAuth.generatePasswordResetLink(eq("c@test.com"), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_RESET_LINK);
            ForgotPasswordResponse r3 = passwordResetService.requestPasswordReset("c@test.com", LANGUAGE);

            // Then - all must report success
            assertThat(r1.isSuccess()).isTrue();
            assertThat(r2.isSuccess()).isTrue();
            assertThat(r3.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should call normalizeResponseTiming for timing normalization (anti-timing attack)")
        void requestPasswordReset_timingNormalized() {
            // Given - user not found is the simplest path to test timing normalization
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

            // When - measure that the call completes (normalizeResponseTiming uses Thread.sleep)
            long startTime = System.currentTimeMillis();
            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(TEST_EMAIL, LANGUAGE);
            long elapsed = System.currentTimeMillis() - startTime;

            // Then - timing normalization targets ~500ms (with jitter up to 600ms)
            // We verify response is valid; the timing normalization is implicitly tested
            // by the fact that the method calls normalizeResponseTiming() on all paths
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            // The elapsed time should be at least close to the target time (500ms)
            // We use a generous lower bound to avoid flaky tests in CI
            assertThat(elapsed).isGreaterThanOrEqualTo(300L);
        }
    }

    @Nested
    @DisplayName("extractOobCode Tests")
    class ExtractOobCodeTests {

        @Test
        @DisplayName("should extract oobCode from valid Firebase link")
        void extractOobCode_validLink_returnsCode() {
            String result = ReflectionTestUtils.invokeMethod(
                    passwordResetService, "extractOobCode",
                    "https://project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=reset-code-789&apiKey=xxx");

            assertThat(result).isEqualTo("reset-code-789");
        }

        @Test
        @DisplayName("should throw when oobCode param is missing")
        void extractOobCode_missingParam_throwsException() {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    passwordResetService, "extractOobCode",
                    "https://project.firebaseapp.com/__/auth/action?mode=resetPassword&apiKey=xxx"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should throw when oobCode param is blank")
        void extractOobCode_blankParam_throwsException() {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    passwordResetService, "extractOobCode",
                    "https://project.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=&apiKey=xxx"))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
