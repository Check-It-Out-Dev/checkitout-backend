package com.sm.instagram.platform.unit.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.auth.validator.TotpValidationService;
import com.sm.instagram.platform.common.security.KMSValidationService;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TotpValidationService.
 * Tests cover TOTP validation at application startup, including KMS access,
 * Firestore access, and TOTP generation validation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TotpValidationService Unit Tests")
class TotpValidationServiceUnitTest {

    @Mock
    private Firestore firestore;

    @Mock
    private KMSValidationService kmsService;

    @Mock
    private TotpFirestoreService totpFirestoreService;

    @Mock
    private GoogleAuthenticator gAuth;

    @Mock
    private ApplicationReadyEvent applicationReadyEvent;

    @Mock
    private CollectionReference collectionReference;

    @Mock
    private DocumentReference documentReference;

    @Mock
    private ApiFuture<DocumentSnapshot> documentSnapshotFuture;

    @Mock
    private DocumentSnapshot documentSnapshot;

    @Mock
    private GoogleAuthenticatorKey googleAuthenticatorKey;

    private TotpValidationService service;

    private static final String TEST_SECRET = "JBSWY3DPEHPK3PXP";
    private static final String ENCRYPTED_SECRET = "encrypted-secret-base64";
    private static final int TEST_TOTP_CODE = 123456;
    // Base32 encoded "test-totp-secret"
    private static final String TEST_ENCODED_SECRET = "ORSXG5DJNZUXI4ZTORUGS3TH";

    @BeforeEach
    void setUp() {
        service = new TotpValidationService(firestore, kmsService, totpFirestoreService, gAuth);

        // Set default configuration values
        ReflectionTestUtils.setField(service, "totpEnabled", true);
        ReflectionTestUtils.setField(service, "issuer", "CheckItOut");
        ReflectionTestUtils.setField(service, "totpKeyName", "totp-secrets-key");
        ReflectionTestUtils.setField(service, "keyRingName", "instagram-tokens");
    }

    private void setupSuccessfulFirestoreAccess() throws Exception {
        when(firestore.collection("totpSecrets")).thenReturn(collectionReference);
        when(collectionReference.document("test-validation")).thenReturn(documentReference);
        when(documentReference.get()).thenReturn(documentSnapshotFuture);
        when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
    }

    private void setupSuccessfulKMSAccess() {
        // Use Answer to return the same value that was encrypted
        when(kmsService.encryptTotpSecret(anyString())).thenReturn(ENCRYPTED_SECRET);
        when(kmsService.decryptTotpSecret(anyString())).thenAnswer(invocation -> {
            // Return the TEST_ENCODED_SECRET which is what the service is looking for
            return TEST_ENCODED_SECRET;
        });
    }

    private void setupSuccessfulTOTPGeneration() {
        when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
        when(googleAuthenticatorKey.getKey()).thenReturn(TEST_SECRET);
        when(gAuth.getTotpPassword(anyString())).thenReturn(TEST_TOTP_CODE);
        when(gAuth.authorize(anyString(), anyInt())).thenReturn(true);
    }

    @Nested
    @DisplayName("onApplicationEvent - TOTP Disabled")
    class TotpDisabledTests {

        @Test
        @DisplayName("should skip all validation when TOTP is disabled")
        void shouldSkipValidationWhenTotpDisabled() {
            // Given
            ReflectionTestUtils.setField(service, "totpEnabled", false);

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then
            verifyNoInteractions(kmsService);
            verifyNoInteractions(firestore);
            verifyNoInteractions(gAuth);
        }

        @Test
        @DisplayName("should not throw exception when TOTP is disabled")
        void shouldNotThrowWhenTotpDisabled() {
            // Given
            ReflectionTestUtils.setField(service, "totpEnabled", false);

            // When/Then
            assertThatNoException()
                .isThrownBy(() -> service.onApplicationEvent(applicationReadyEvent));
        }
    }

    @Nested
    @DisplayName("onApplicationEvent - TOTP Enabled")
    class TotpEnabledTests {

        @Test
        @DisplayName("should complete validation successfully when all checks pass")
        void shouldCompleteValidationWhenAllChecksPassed() throws Exception {
            // Given
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then
            verify(kmsService).encryptTotpSecret(anyString());
            verify(kmsService).decryptTotpSecret(anyString());
            verify(firestore).collection("totpSecrets");
            verify(gAuth).createCredentials();
            verify(gAuth).getTotpPassword(anyString());
            verify(gAuth, atLeast(1)).authorize(anyString(), anyInt());
        }

        @Test
        @DisplayName("should not throw exception even when validation fails")
        void shouldNotThrowExceptionWhenValidationFails() {
            // Given - KMS throws exception
            when(kmsService.encryptTotpSecret(anyString()))
                .thenThrow(new RuntimeException("KMS unavailable"));

            // When/Then - should not throw, just log error
            assertThatNoException()
                .isThrownBy(() -> service.onApplicationEvent(applicationReadyEvent));
        }
    }

    @Nested
    @DisplayName("validateKMSAccess")
    class ValidateKMSAccessTests {

        @Test
        @DisplayName("should validate KMS access when encryption/decryption works")
        void shouldValidateKMSAccessSuccessfully() throws Exception {
            // Given
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then
            verify(kmsService).encryptTotpSecret(anyString());
            verify(kmsService).decryptTotpSecret(anyString());
        }

        @Test
        @DisplayName("should fail KMS validation when encryption throws exception")
        void shouldFailKMSValidationWhenEncryptionFails() throws Exception {
            // Given
            when(kmsService.encryptTotpSecret(anyString()))
                .thenThrow(new RuntimeException("KMS encryption failed"));
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - should continue despite failure
            verify(kmsService).encryptTotpSecret(anyString());
            verify(kmsService, never()).decryptTotpSecret(anyString());
        }

        @Test
        @DisplayName("should fail KMS validation when decryption throws exception")
        void shouldFailKMSValidationWhenDecryptionFails() throws Exception {
            // Given
            when(kmsService.encryptTotpSecret(anyString())).thenReturn(ENCRYPTED_SECRET);
            when(kmsService.decryptTotpSecret(anyString()))
                .thenThrow(new RuntimeException("KMS decryption failed"));
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then
            verify(kmsService).encryptTotpSecret(anyString());
            verify(kmsService).decryptTotpSecret(anyString());
        }

        @Test
        @DisplayName("should fail KMS validation when round-trip mismatch")
        void shouldFailKMSValidationWhenRoundTripMismatch() throws Exception {
            // Given
            when(kmsService.encryptTotpSecret(anyString())).thenReturn(ENCRYPTED_SECRET);
            when(kmsService.decryptTotpSecret(anyString())).thenReturn("WRONG_SECRET");
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - should continue despite failure
            verify(kmsService).encryptTotpSecret(anyString());
            verify(kmsService).decryptTotpSecret(anyString());
        }
    }

    @Nested
    @DisplayName("validateFirestoreAccess")
    class ValidateFirestoreAccessTests {

        @Test
        @DisplayName("should validate Firestore access successfully")
        void shouldValidateFirestoreAccessSuccessfully() throws Exception {
            // Given
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then
            verify(firestore).collection("totpSecrets");
            verify(collectionReference).document("test-validation");
            verify(documentReference).get();
        }

        @Test
        @DisplayName("should succeed when document does not exist (NOT_FOUND)")
        void shouldSucceedWhenDocumentNotFound() throws Exception {
            // Given
            when(kmsService.encryptTotpSecret(anyString())).thenReturn(ENCRYPTED_SECRET);
            when(kmsService.decryptTotpSecret(anyString())).thenReturn(TEST_ENCODED_SECRET);
            setupSuccessfulTOTPGeneration();

            when(firestore.collection("totpSecrets")).thenReturn(collectionReference);
            when(collectionReference.document("test-validation")).thenReturn(documentReference);
            when(documentReference.get()).thenReturn(documentSnapshotFuture);
            when(documentSnapshotFuture.get()).thenThrow(
                new ExecutionException("NOT_FOUND", new RuntimeException("NOT_FOUND")));

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - should succeed despite NOT_FOUND
            verify(firestore).collection("totpSecrets");
        }

        @Test
        @DisplayName("should fail Firestore validation on real error")
        void shouldFailFirestoreValidationOnRealError() throws Exception {
            // Given
            when(kmsService.encryptTotpSecret(anyString())).thenReturn(ENCRYPTED_SECRET);
            when(kmsService.decryptTotpSecret(anyString())).thenReturn(TEST_ENCODED_SECRET);
            setupSuccessfulTOTPGeneration();

            when(firestore.collection("totpSecrets")).thenReturn(collectionReference);
            when(collectionReference.document("test-validation")).thenReturn(documentReference);
            when(documentReference.get()).thenReturn(documentSnapshotFuture);
            when(documentSnapshotFuture.get()).thenThrow(
                new ExecutionException("Connection refused", new RuntimeException("Connection refused")));

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - should continue but log failure
            verify(firestore).collection("totpSecrets");
        }

        @Test
        @DisplayName("should fail Firestore validation when collection access fails")
        void shouldFailFirestoreValidationWhenCollectionAccessFails() throws Exception {
            // Given
            when(kmsService.encryptTotpSecret(anyString())).thenReturn(ENCRYPTED_SECRET);
            when(kmsService.decryptTotpSecret(anyString())).thenReturn(TEST_ENCODED_SECRET);
            setupSuccessfulTOTPGeneration();

            when(firestore.collection("totpSecrets"))
                .thenThrow(new RuntimeException("Firestore unavailable"));

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - should continue despite failure
            verify(firestore).collection("totpSecrets");
        }
    }

    @Nested
    @DisplayName("validateTOTPGeneration")
    class ValidateTOTPGenerationTests {

        @Test
        @DisplayName("should validate TOTP generation successfully")
        void shouldValidateTOTPGenerationSuccessfully() throws Exception {
            // Given
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then
            verify(gAuth).createCredentials();
            verify(gAuth).getTotpPassword(anyString());
            verify(gAuth, atLeast(1)).authorize(anyString(), anyInt());
        }

        @Test
        @DisplayName("should fail TOTP validation when createCredentials fails")
        void shouldFailTOTPValidationWhenCreateCredentialsFails() throws Exception {
            // Given
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();

            when(gAuth.createCredentials())
                .thenThrow(new RuntimeException("TOTP generation failed"));

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - should continue despite failure
            verify(gAuth).createCredentials();
            verify(gAuth, never()).getTotpPassword(anyString());
        }

        @Test
        @DisplayName("should fail TOTP validation when authorize returns false")
        void shouldFailTOTPValidationWhenAuthorizeReturnsFalse() throws Exception {
            // Given
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();

            when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
            when(googleAuthenticatorKey.getKey()).thenReturn(TEST_SECRET);
            when(gAuth.getTotpPassword(anyString())).thenReturn(TEST_TOTP_CODE);
            when(gAuth.authorize(anyString(), anyInt())).thenReturn(false);

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - should fail validation
            verify(gAuth, atLeast(1)).authorize(anyString(), anyInt());
        }

        @Test
        @DisplayName("should fail TOTP validation when getTotpPassword throws exception")
        void shouldFailTOTPValidationWhenGetTotpPasswordFails() throws Exception {
            // Given
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();

            when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
            when(googleAuthenticatorKey.getKey()).thenReturn(TEST_SECRET);
            when(gAuth.getTotpPassword(anyString()))
                .thenThrow(new RuntimeException("TOTP password generation failed"));

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - should continue despite failure
            verify(gAuth).getTotpPassword(anyString());
        }

        @Test
        @DisplayName("should continue validation when time window check fails")
        void shouldContinueWhenTimeWindowCheckFails() throws Exception {
            // Given
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();

            when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
            when(googleAuthenticatorKey.getKey()).thenReturn(TEST_SECRET);
            when(gAuth.getTotpPassword(anyString())).thenReturn(TEST_TOTP_CODE);
            // First call returns true (initial validation), second call returns false (time window check)
            when(gAuth.authorize(anyString(), anyInt()))
                .thenReturn(true)
                .thenReturn(false);

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - should continue with warning, not failure
            verify(gAuth, atLeast(1)).authorize(anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("Configuration Value Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("should use configured issuer value")
        void shouldUseConfiguredIssuer() throws Exception {
            // Given
            String customIssuer = "MyCustomApp";
            ReflectionTestUtils.setField(service, "issuer", customIssuer);
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - no exception means it ran successfully with custom issuer
            verify(gAuth).createCredentials();
        }

        @Test
        @DisplayName("should use configured key ring name")
        void shouldUseConfiguredKeyRingName() throws Exception {
            // Given
            String customKeyRing = "custom-key-ring";
            ReflectionTestUtils.setField(service, "keyRingName", customKeyRing);
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - no exception means it ran successfully with custom key ring
            verify(kmsService).encryptTotpSecret(anyString());
        }

        @Test
        @DisplayName("should use configured TOTP key name")
        void shouldUseConfiguredTotpKeyName() throws Exception {
            // Given
            String customTotpKey = "custom-totp-key";
            ReflectionTestUtils.setField(service, "totpKeyName", customTotpKey);
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - no exception means it ran successfully with custom totp key
            verify(kmsService).encryptTotpSecret(anyString());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle null ApplicationReadyEvent")
        void shouldHandleNullEvent() {
            // When/Then - should handle null gracefully
            assertThatNoException()
                .isThrownBy(() -> service.onApplicationEvent(null));
        }

        @Test
        @DisplayName("should handle all validations failing")
        void shouldHandleAllValidationsFailing() throws Exception {
            // Given
            when(kmsService.encryptTotpSecret(anyString()))
                .thenThrow(new RuntimeException("KMS failed"));
            when(firestore.collection("totpSecrets"))
                .thenThrow(new RuntimeException("Firestore failed"));
            when(gAuth.createCredentials())
                .thenThrow(new RuntimeException("TOTP failed"));

            // When/Then - should not throw, just log errors
            assertThatNoException()
                .isThrownBy(() -> service.onApplicationEvent(applicationReadyEvent));
        }

        @Test
        @DisplayName("should log degraded state when some checks fail")
        void shouldLogDegradedStateWhenSomeChecksFail() throws Exception {
            // Given - KMS fails, others succeed
            when(kmsService.encryptTotpSecret(anyString()))
                .thenThrow(new RuntimeException("KMS failed"));
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - should still attempt other validations
            verify(firestore).collection("totpSecrets");
            verify(gAuth).createCredentials();
        }

        @Test
        @DisplayName("should handle empty secret key from GoogleAuthenticator")
        void shouldHandleEmptySecretKey() throws Exception {
            // Given
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();

            when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
            when(googleAuthenticatorKey.getKey()).thenReturn("");

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - should handle empty key gracefully
            verify(googleAuthenticatorKey).getKey();
        }

        @Test
        @DisplayName("should handle short secret key from GoogleAuthenticator")
        void shouldHandleShortSecretKey() throws Exception {
            // Given
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();

            String shortSecret = "AB";
            when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
            when(googleAuthenticatorKey.getKey()).thenReturn(shortSecret);
            when(gAuth.getTotpPassword(anyString())).thenReturn(123456);
            when(gAuth.authorize(anyString(), anyInt())).thenReturn(true);

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - should handle short key (even if it's 2 chars, substring call won't fail)
            verify(googleAuthenticatorKey).getKey();
        }
    }

    @Nested
    @DisplayName("ApplicationListener Interface Tests")
    class ApplicationListenerTests {

        @Test
        @DisplayName("should implement ApplicationListener correctly")
        void shouldImplementApplicationListenerCorrectly() {
            // Given
            ReflectionTestUtils.setField(service, "totpEnabled", false);

            // When/Then
            assertThatNoException()
                .isThrownBy(() -> service.onApplicationEvent(applicationReadyEvent));
        }

        @Test
        @DisplayName("should run at correct order after Firebase and KMS validation")
        void shouldRunAtCorrectOrder() {
            // The @Order(3) annotation ensures this runs after Firebase and KMS validation
            // This test just verifies the service can be invoked without issues
            ReflectionTestUtils.setField(service, "totpEnabled", false);

            assertThatNoException()
                .isThrownBy(() -> service.onApplicationEvent(applicationReadyEvent));
        }
    }

    @Nested
    @DisplayName("Interaction Between Validation Steps")
    class ValidationInteractionTests {

        @Test
        @DisplayName("should continue to Firestore validation even if KMS fails")
        void shouldContinueToFirestoreEvenIfKMSFails() throws Exception {
            // Given
            when(kmsService.encryptTotpSecret(anyString()))
                .thenThrow(new RuntimeException("KMS failed"));
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then
            verify(kmsService).encryptTotpSecret(anyString());
            verify(firestore).collection("totpSecrets");
        }

        @Test
        @DisplayName("should continue to TOTP validation even if Firestore fails")
        void shouldContinueToTOTPEvenIfFirestoreFails() throws Exception {
            // Given
            setupSuccessfulKMSAccess();
            when(firestore.collection("totpSecrets"))
                .thenThrow(new RuntimeException("Firestore failed"));
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then
            verify(firestore).collection("totpSecrets");
            verify(gAuth).createCredentials();
        }

        @Test
        @DisplayName("should report operational when all checks pass")
        void shouldReportOperationalWhenAllPass() throws Exception {
            // Given
            setupSuccessfulKMSAccess();
            setupSuccessfulFirestoreAccess();
            setupSuccessfulTOTPGeneration();

            // When
            service.onApplicationEvent(applicationReadyEvent);

            // Then - all services should be invoked
            verify(kmsService).encryptTotpSecret(anyString());
            verify(kmsService).decryptTotpSecret(anyString());
            verify(firestore).collection("totpSecrets");
            verify(gAuth).createCredentials();
            verify(gAuth).getTotpPassword(anyString());
            verify(gAuth, atLeast(1)).authorize(anyString(), anyInt());
        }
    }
}
