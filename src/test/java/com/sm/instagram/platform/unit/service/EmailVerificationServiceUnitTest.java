package com.sm.instagram.platform.unit.service;

import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.service.EmailVerificationService;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.RateLimitTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.support.common.EmailService;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.address.Address;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.MessagingException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmailVerificationService Unit Tests")
class EmailVerificationServiceUnitTest {

    @Mock
    private FirebaseAuth firebaseAuth;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPreferencesRepository userPreferencesRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private PermissionUtils permissionUtils;
    @Mock
    private com.sm.instagram.platform.registry.CompanyDataRepository companyDataRepository;
    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @Mock
    private org.springframework.data.redis.core.ValueOperations<String, String> valueOperations;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private com.sm.instagram.platform.auth.cache.UserCacheService userCacheService;

    @InjectMocks
    private EmailVerificationService service;

    private static final String FIREBASE_VERIFICATION_LINK =
            "https://project.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=test-oob-code-123&apiKey=xxx";
    private static final String EXPECTED_VERIFICATION_LINK =
            "https://frontend.test/auth/action?mode=verifyEmail&oobCode=test-oob-code-123&ut=I&iac=0";
    private static final String EXPECTED_VERIFICATION_LINK_IAC_1 =
            "https://frontend.test/auth/action?mode=verifyEmail&oobCode=test-oob-code-123&ut=I&iac=1";

    private User baseUser() {
        User u = new User();
        u.setId(1L);
        u.setFirebaseUserId("uid-123");
        u.setUserType(UserType.INFLUENCER);
        u.setEmail("user@example.com");
        u.setEmailVerified(false);
        return u;
    }

    /** Influencer with a fully complete profile (passes isProfileComplete). */
    private User completeInfluencer() {
        User u = baseUser();
        u.setFirstName("Jane");
        u.setLastName("Doe");
        u.setPhoneNumber("+48123456789");
        Address a = new Address();
        a.setPrimary(true);
        a.setStreet("Main 1");
        a.setCity("Warsaw");
        a.setPostalCode("00-001");
        a.setCountry("PL");
        a.setState("Mazowieckie");
        u.setAddresses(new java.util.ArrayList<>(java.util.List.of(a)));
        return u;
    }

    @BeforeEach
    void setup() throws Exception {
        // Set default frontend.url field via reflection since it's injected by @Value
        var field = EmailVerificationService.class.getDeclaredField("frontendUrl");
        field.setAccessible(true);
        field.set(service, "https://frontend.test");

        // Set cooldown duration (defaults to 0 in @InjectMocks, tests need production value)
        var cooldownField = EmailVerificationService.class.getDeclaredField("verificationCooldownSeconds");
        cooldownField.setAccessible(true);
        cooldownField.set(service, 60);

        // Stub Redis for oobCode storage
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("sendVerificationEmail")
    class SendVerificationEmailTests {

        @Test
        @DisplayName("should send email and persist timestamp when eligible")
        void shouldSendEmailAndPersistTimestamp() throws Exception {
            User user = baseUser();
            when(userRepository.findByFirebaseUserId("uid-123")).thenReturn(Optional.of(user));

            // Firebase user not yet verified
            UserRecord firebaseUser = mock(UserRecord.class);
            when(firebaseAuth.getUser("uid-123")).thenReturn(firebaseUser);
            when(firebaseUser.isEmailVerified()).thenReturn(false);

            when(firebaseAuth.generateEmailVerificationLink(eq("user@example.com"), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_VERIFICATION_LINK);

            // No explicit language provided -> fallback to preferences
            UserPreferences prefs = new UserPreferences();
            prefs.setLanguage("pl");
            when(userPreferencesRepository.findByUserId(1L)).thenReturn(prefs);

            // emailService succeeds
            doNothing().when(emailService).sendVerificationEmail(eq("user@example.com"), anyString(), eq(EXPECTED_VERIFICATION_LINK), eq("pl"));

            // save after email sent
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.sendVerificationEmail("uid-123", null);

            // Then
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, atLeastOnce()).save(captor.capture());
            assertThat(captor.getValue().getEmailVerificationSentAt()).isNotNull();
            verify(emailService).sendVerificationEmail(eq("user@example.com"), anyString(), eq(EXPECTED_VERIFICATION_LINK), eq("pl"));
        }

        @Test
        @DisplayName("should throw when user already verified in DB")
        void shouldThrowWhenAlreadyVerifiedInDb() {
            User user = baseUser();
            user.setEmailVerified(true);
            when(userRepository.findByFirebaseUserId("uid-123")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.sendVerificationEmail("uid-123", "en"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("email_already_verified");
        }

        @Test
        @DisplayName("should sync from Firebase and throw when Firebase already verified")
        void shouldSyncFromFirebaseAndThrow() throws Exception {
            User user = baseUser();
            when(userRepository.findByFirebaseUserId("uid-123")).thenReturn(Optional.of(user));

            UserRecord firebaseUser = mock(UserRecord.class);
            when(firebaseAuth.getUser("uid-123")).thenReturn(firebaseUser);
            when(firebaseUser.isEmailVerified()).thenReturn(true);

            assertThatThrownBy(() -> service.sendVerificationEmail("uid-123", "en"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("email_already_verified");

            // DB synced
            assertThat(user.getEmailVerified()).isTrue();
            assertThat(user.getEmailVerifiedAt()).isNotNull();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("should ignore OptimisticLockingFailureException when saving timestamp")
        void shouldIgnoreOptimisticLockingFailureOnTimestampUpdate() throws Exception {
            User user = baseUser();
            when(userRepository.findByFirebaseUserId("uid-123")).thenReturn(Optional.of(user));

            UserRecord firebaseUser = mock(UserRecord.class);
            when(firebaseAuth.getUser("uid-123")).thenReturn(firebaseUser);
            when(firebaseUser.isEmailVerified()).thenReturn(false);

            when(firebaseAuth.generateEmailVerificationLink(anyString(), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_VERIFICATION_LINK);

            doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), anyString(), anyString());

            // First save (from Firebase sync path not taken), then timestamp save fails
            doThrow(new OptimisticLockingFailureException("concurrent"))
                    .when(userRepository).save(user);

            // When/Then - should not throw
            assertThatCode(() -> service.sendVerificationEmail("uid-123", "en")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should throw ExternalServiceException for SMTP failure")
        void shouldThrowExternalForSmtpFailure() throws Exception {
            User user = baseUser();
            when(userRepository.findByFirebaseUserId("uid-123")).thenReturn(Optional.of(user));

            UserRecord firebaseUser = mock(UserRecord.class);
            when(firebaseAuth.getUser("uid-123")).thenReturn(firebaseUser);
            when(firebaseUser.isEmailVerified()).thenReturn(false);

            when(firebaseAuth.generateEmailVerificationLink(anyString(), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_VERIFICATION_LINK);

            doThrow(new MessagingException("SMTP down")).when(emailService)
                    .sendVerificationEmail(anyString(), anyString(), anyString(), anyString());

            assertThatThrownBy(() -> service.sendVerificationEmail("uid-123", "en"))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to send verification email");
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByFirebaseUserId("missing")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.sendVerificationEmail("missing", "en"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when email missing")
        void shouldThrowWhenEmailMissing() {
            User user = baseUser();
            user.setEmail(null);
            when(userRepository.findByFirebaseUserId("uid-123")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.sendVerificationEmail("uid-123", "en"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("email_required");
        }

        @Test
        @DisplayName("should include iac=1 when initialAccountSetupCompleted is true")
        void shouldIncludeIacOneWhenInitialAccountSetupCompleted() throws Exception {
            User user = baseUser();
            user.setInitialAccountSetupCompleted(true);
            when(userRepository.findByFirebaseUserId("uid-123")).thenReturn(Optional.of(user));

            UserRecord firebaseUser = mock(UserRecord.class);
            when(firebaseAuth.getUser("uid-123")).thenReturn(firebaseUser);
            when(firebaseUser.isEmailVerified()).thenReturn(false);

            when(firebaseAuth.generateEmailVerificationLink(eq("user@example.com"), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_VERIFICATION_LINK);

            UserPreferences prefs = new UserPreferences();
            prefs.setLanguage("en");
            when(userPreferencesRepository.findByUserId(1L)).thenReturn(prefs);

            doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), eq(EXPECTED_VERIFICATION_LINK_IAC_1), anyString());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.sendVerificationEmail("uid-123", null);

            verify(emailService).sendVerificationEmail(eq("user@example.com"), anyString(), eq(EXPECTED_VERIFICATION_LINK_IAC_1), eq("en"));
        }

        @Test
        @DisplayName("should include iac=0 when initialAccountSetupCompleted is false")
        void shouldIncludeIacZeroWhenInitialAccountSetupNotCompleted() throws Exception {
            User user = baseUser();
            user.setInitialAccountSetupCompleted(false);
            when(userRepository.findByFirebaseUserId("uid-123")).thenReturn(Optional.of(user));

            UserRecord firebaseUser = mock(UserRecord.class);
            when(firebaseAuth.getUser("uid-123")).thenReturn(firebaseUser);
            when(firebaseUser.isEmailVerified()).thenReturn(false);

            when(firebaseAuth.generateEmailVerificationLink(eq("user@example.com"), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_VERIFICATION_LINK);

            UserPreferences prefs = new UserPreferences();
            prefs.setLanguage("en");
            when(userPreferencesRepository.findByUserId(1L)).thenReturn(prefs);

            doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), eq(EXPECTED_VERIFICATION_LINK), anyString());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.sendVerificationEmail("uid-123", null);

            verify(emailService).sendVerificationEmail(eq("user@example.com"), anyString(), eq(EXPECTED_VERIFICATION_LINK), eq("en"));
        }
    }

    @Nested
    @DisplayName("syncEmailVerificationStatus")
    class SyncEmailVerificationStatusTests {

        /**
         * BUG-14: tests in this nest stub findById to return the same user instance.
         * Production code refetches a managed reference inside REQUIRES_NEW (so the
         * caller's detached entity isn't mutated/saved across the tx boundary); for
         * unit tests the same instance is fine because Mockito doesn't simulate
         * persistence-context detach.
         */

        @Test
        @DisplayName("should update status and set ACTIVE for influencer when verified")
        void shouldUpdateStatusAndActivateInfluencer() {
            User user = completeInfluencer();
            user.setEmailVerified(false);
            user.setEmailVerifiedAt(null);
            user.setInitialAccountSetupCompleted(true);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            boolean updated = service.syncEmailVerificationStatus(user, true);

            assertThat(updated).isTrue();
            assertThat(user.getEmailVerified()).isTrue();
            assertThat(user.getEmailVerifiedAt()).isNotNull();
            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            verify(permissionUtils).changeUserRole(eq("uid-123"), eq(AccountStatus.ACTIVE), eq(UserType.INFLUENCER));
            verify(eventPublisher).publishEvent(any(com.sm.instagram.platform.notification.event.AccountActivatedEvent.class));
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("influencer verified + INCOMPLETE profile -> stays IN_VALIDATION")
        void influencerIncompleteProfile_staysInValidation() {
            User user = baseUser();                 // no name/phone/address -> incomplete
            user.setInitialAccountSetupCompleted(true);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            boolean updated = service.syncEmailVerificationStatus(user, true);

            assertThat(updated).isTrue();
            assertThat(user.getEmailVerified()).isTrue();
            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
            verify(permissionUtils, never()).changeUserRole(any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any(
                    com.sm.instagram.platform.notification.event.AccountActivatedEvent.class));
            // emailVerified flipped false->true (user still IN_VALIDATION) — cache MUST be evicted
            // so EmailVerificationEnforcementFilter sees the fresh value, not a stale cached one.
            verify(userCacheService).evict("uid-123");
        }

        @Test
        @DisplayName("should not update when no change")
        void shouldNotUpdateWhenNoChange() {
            User user = baseUser();
            user.setEmailVerified(false);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            boolean updated = service.syncEmailVerificationStatus(user, false);
            assertThat(updated).isFalse();
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should not set verified when email is blank")
        void shouldNotSetVerifiedWhenEmailBlank() {
            User user = baseUser();
            user.setEmail("");
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            boolean updated = service.syncEmailVerificationStatus(user, true);
            assertThat(updated).isFalse();
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("influencer verified + COMPLETE profile -> ACTIVE")
        void influencerCompleteProfile_activates() {
            User user = completeInfluencer();
            user.setInitialAccountSetupCompleted(true);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            boolean updated = service.syncEmailVerificationStatus(user, true);

            assertThat(updated).isTrue();
            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            verify(permissionUtils).changeUserRole(eq("uid-123"), eq(AccountStatus.ACTIVE), eq(UserType.INFLUENCER));
            verify(eventPublisher).publishEvent(any(
                    com.sm.instagram.platform.notification.event.AccountActivatedEvent.class));
        }

        @Test
        @DisplayName("evicts cache when emailVerified flips true->false (no activation) — keeps enforcement filter coherent")
        void evictsCacheOnVerifiedToUnverifiedFlip() {
            User user = completeInfluencer();
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setInitialAccountSetupCompleted(true);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            boolean updated = service.syncEmailVerificationStatus(user, false);

            assertThat(updated).isTrue();
            assertThat(user.getEmailVerified()).isFalse();
            // emailVerified is read from cache by EmailVerificationEnforcementFilter; a stale 'true'
            // would let an unverified user POST campaigns/applications until the 5-min cache TTL.
            verify(userCacheService).evict("uid-123");
        }

        @Test
        @DisplayName("does not write Firebase role when the PG save fails (no Firebase/PG split-brain)")
        void doesNotWriteFirebaseRoleWhenSaveFails() {
            User user = completeInfluencer();
            user.setAccountStatus(AccountStatus.IN_VALIDATION);
            user.setEmailVerified(false);
            user.setInitialAccountSetupCompleted(true);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenThrow(new RuntimeException("PG save failed"));

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> service.syncEmailVerificationStatus(user, true))
                    .isInstanceOf(RuntimeException.class);

            // The Firebase role write must be DEFERRED until after a successful PG save. On save
            // failure it must never run -- otherwise Firebase shows ACTIVE while PG rolls back to
            // IN_VALIDATION (no tokenVersion bump, no 419), a split-brain that can't self-heal.
            verify(permissionUtils, never()).changeUserRole(any(), any(), any());
        }

        /**
         * BUG-14: pins the persistence-context boundary fix. The caller's User instance
         * comes from an outer readOnly tx and is detached inside this REQUIRES_NEW.
         * The sync must refetch a managed reference, mutate + save the FRESH instance,
         * and mirror the auth-relevant fields back to the caller's reference so
         * downstream session-minting sees the post-sync state without touching the
         * outer tx's managed copy.
         */
        @Test
        @DisplayName("BUG-14: refetches managed reference inside REQUIRES_NEW and mirrors fields back to caller's input")
        void shouldRefetchManagedReferenceAndMirrorBack() {
            // Caller's reference — detached in production from the outer readOnly tx
            User callerRef = completeInfluencer();
            callerRef.setEmailVerified(false);
            callerRef.setEmailVerifiedAt(null);
            callerRef.setAccountStatus(AccountStatus.IN_VALIDATION);
            callerRef.setTokenVersion(7L);
            callerRef.setInitialAccountSetupCompleted(true);

            // Managed reference — what findById returns inside REQUIRES_NEW (different instance)
            User managedRef = completeInfluencer();
            managedRef.setEmailVerified(false);
            managedRef.setEmailVerifiedAt(null);
            managedRef.setAccountStatus(AccountStatus.IN_VALIDATION);
            managedRef.setTokenVersion(7L);
            managedRef.setInitialAccountSetupCompleted(true);

            when(userRepository.findById(callerRef.getId())).thenReturn(Optional.of(managedRef));

            boolean updated = service.syncEmailVerificationStatus(callerRef, true);

            assertThat(updated).isTrue();
            // The managed instance is what was saved
            verify(userRepository).save(managedRef);
            // Auth-relevant fields are mirrored back to the caller's reference so claims build correctly
            assertThat(callerRef.getEmailVerified()).isTrue();
            assertThat(callerRef.getEmailVerifiedAt()).isNotNull();
            assertThat(callerRef.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(callerRef.getTokenVersion()).isEqualTo(managedRef.getTokenVersion());
            // ...and the caller's instance is NOT the saved instance (proves the fix is doing real work)
            verify(userRepository, never()).save(callerRef);
        }
    }

    @Nested
    @DisplayName("evaluateAutoActivation")
    class EvaluateAutoActivationTests {

        @Test
        @DisplayName("profile completed while already emailVerified -> ACTIVE")
        void profileCompletedAfterVerify_activates() {
            User user = completeInfluencer();
            user.setEmailVerified(true);

            boolean activated = service.evaluateAutoActivation(user);

            assertThat(activated).isTrue();
            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            verify(permissionUtils).changeUserRole(eq("uid-123"), eq(AccountStatus.ACTIVE), eq(UserType.INFLUENCER));
        }

        @Test
        @DisplayName("does not reactivate a BANNED user")
        void doesNotReactivateBanned() {
            User user = completeInfluencer();
            user.setEmailVerified(true);
            user.setAccountStatus(AccountStatus.BANNED);

            assertThat(service.evaluateAutoActivation(user)).isFalse();
            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.BANNED);
            verify(permissionUtils, never()).changeUserRole(any(), any(), any());
        }

        @Test
        @DisplayName("does not activate when profile incomplete")
        void doesNotActivateIncompleteProfile() {
            User user = baseUser();
            user.setEmailVerified(true);

            assertThat(service.evaluateAutoActivation(user)).isFalse();
            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
        }
    }

    @Nested
    @DisplayName("extractOobCode")
    class ExtractOobCodeTests {

        @Test
        @DisplayName("should extract oobCode from valid Firebase link")
        void extractOobCode_validLink_returnsCode() {
            String result = ReflectionTestUtils.invokeMethod(
                    service, "extractOobCode",
                    "https://project.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=abc-123&apiKey=xxx");

            assertThat(result).isEqualTo("abc-123");
        }

        @Test
        @DisplayName("should throw ExternalServiceException when oobCode param is missing")
        void extractOobCode_missingParam_throwsException() {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    service, "extractOobCode",
                    "https://project.firebaseapp.com/__/auth/action?mode=verifyEmail&apiKey=xxx"))
                    .isInstanceOf(ExternalServiceException.class);
        }

        @Test
        @DisplayName("should throw ExternalServiceException when oobCode param is blank")
        void extractOobCode_blankParam_throwsException() {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    service, "extractOobCode",
                    "https://project.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=&apiKey=xxx"))
                    .isInstanceOf(ExternalServiceException.class);
        }

        @Test
        @DisplayName("sendVerificationEmail should rewrite link to frontend URL format")
        void sendVerificationEmail_rewritesLink() throws Exception {
            User user = baseUser();
            when(userRepository.findByFirebaseUserId("uid-123")).thenReturn(Optional.of(user));

            UserRecord firebaseUser = mock(UserRecord.class);
            when(firebaseAuth.getUser("uid-123")).thenReturn(firebaseUser);
            when(firebaseUser.isEmailVerified()).thenReturn(false);

            when(firebaseAuth.generateEmailVerificationLink(anyString(), any(ActionCodeSettings.class)))
                    .thenReturn(FIREBASE_VERIFICATION_LINK);

            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), anyString(), anyString());

            service.sendVerificationEmail("uid-123", "en");

            ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendVerificationEmail(anyString(), anyString(), linkCaptor.capture(), anyString());
            assertThat(linkCaptor.getValue()).isEqualTo(EXPECTED_VERIFICATION_LINK);
        }
    }
}
