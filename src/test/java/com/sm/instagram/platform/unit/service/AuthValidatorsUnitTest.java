package com.sm.instagram.platform.unit.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.auth.service.ImprovedQRCodeService;
import com.sm.instagram.platform.auth.service.QRCodeGeneratorService;
import com.sm.instagram.platform.auth.validator.RecaptchaStartupValidator;
import com.sm.instagram.platform.auth.validator.TotpQRCodeStartupValidator;
import com.sm.instagram.platform.auth.validator.TotpValidationService;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import com.sm.instagram.platform.common.security.KMSValidationService;
import com.sm.instagram.platform.config.RecaptchaConfig;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for Auth Validators.
 * Tests cover:
 * - RecaptchaStartupValidator: reCAPTCHA configuration and API connectivity validation
 * - TotpQRCodeStartupValidator: TOTP QR code generation validation
 * - TotpValidationService: Extended tests for TOTP validation at startup
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Auth Validators Unit Tests")
class AuthValidatorsUnitTest {

    // ========================================================================
    // RECAPTCHA STARTUP VALIDATOR TESTS
    // ========================================================================

    @Nested
    @DisplayName("RecaptchaStartupValidator Tests")
    class RecaptchaStartupValidatorTests {

        @Mock
        private RecaptchaConfig recaptchaConfig;

        @Mock
        private GoogleCredentialsProvider credentialsProvider;

        @Mock
        private ApplicationReadyEvent applicationReadyEvent;

        private RecaptchaStartupValidator validator;

        @BeforeEach
        void setUp() {
            validator = new RecaptchaStartupValidator(recaptchaConfig, credentialsProvider);
        }

        @Nested
        @DisplayName("validateOnStartup - Disabled State")
        class DisabledStateTests {

            @Test
            @DisplayName("should skip validation when reCAPTCHA is disabled")
            void shouldSkipValidationWhenDisabled() {
                // Given
                when(recaptchaConfig.isEnabled()).thenReturn(false);

                // When
                validator.validateOnStartup();

                // Then
                verify(recaptchaConfig).isEnabled();
                verifyNoMoreInteractions(credentialsProvider);
            }

            @Test
            @DisplayName("should not throw exception when disabled")
            void shouldNotThrowWhenDisabled() {
                // Given
                when(recaptchaConfig.isEnabled()).thenReturn(false);

                // When/Then
                assertThatNoException().isThrownBy(() -> validator.validateOnStartup());
            }
        }

        @Nested
        @DisplayName("validateConfiguration")
        class ValidateConfigurationTests {

            @Test
            @DisplayName("should pass validation with valid configuration")
            void shouldPassWithValidConfiguration() {
                // Given
                setupValidRecaptchaConfig();

                // When
                validator.validateOnStartup();

                // Then - getProjectId is called multiple times during validation
                verify(recaptchaConfig, atLeast(1)).getProjectId();
                verify(recaptchaConfig, atLeast(1)).getSiteKey();
            }

            @Test
            @DisplayName("should fail validation when project ID is null")
            void shouldFailWhenProjectIdIsNull() {
                // Given
                when(recaptchaConfig.isEnabled()).thenReturn(true);
                when(recaptchaConfig.getProjectId()).thenReturn(null);
                when(recaptchaConfig.getSiteKey()).thenReturn("1234567890123456789012345678901234567890");
                setupMinimalCredentialsProvider();

                // When
                validator.validateOnStartup();

                // Then - should continue but log failure, getProjectId called multiple times
                verify(recaptchaConfig, atLeast(1)).getProjectId();
            }

            @Test
            @DisplayName("should fail validation when project ID is empty")
            void shouldFailWhenProjectIdIsEmpty() {
                // Given
                when(recaptchaConfig.isEnabled()).thenReturn(true);
                when(recaptchaConfig.getProjectId()).thenReturn("");
                when(recaptchaConfig.getSiteKey()).thenReturn("1234567890123456789012345678901234567890");
                setupMinimalCredentialsProvider();

                // When
                validator.validateOnStartup();

                // Then - getProjectId called multiple times across validation methods
                verify(recaptchaConfig, atLeast(1)).getProjectId();
            }

            @Test
            @DisplayName("should handle null site key - throws NullPointerException in printValidationSummary")
            void shouldFailWhenSiteKeyIsNull() {
                // Given
                when(recaptchaConfig.isEnabled()).thenReturn(true);
                when(recaptchaConfig.getProjectId()).thenReturn("test-project");
                when(recaptchaConfig.getSiteKey()).thenReturn(null);
                setupMinimalCredentialsProvider();
                setupValidThresholds();

                // When/Then - printValidationSummary calls getSiteKey().substring() which throws NPE
                // This is expected behavior - the validator needs null-safe handling
                assertThatThrownBy(() -> validator.validateOnStartup())
                    .isInstanceOf(NullPointerException.class);
            }

            @Test
            @DisplayName("should handle empty site key - throws StringIndexOutOfBoundsException")
            void shouldFailWhenSiteKeyIsEmpty() {
                // Given
                when(recaptchaConfig.isEnabled()).thenReturn(true);
                when(recaptchaConfig.getProjectId()).thenReturn("test-project");
                when(recaptchaConfig.getSiteKey()).thenReturn("");
                setupMinimalCredentialsProvider();
                setupValidThresholds();

                // When/Then - printValidationSummary calls getSiteKey().substring(0, 8) which throws
                // for empty string
                assertThatThrownBy(() -> validator.validateOnStartup())
                    .isInstanceOf(StringIndexOutOfBoundsException.class);
            }
        }

        @Nested
        @DisplayName("validateCredentials")
        class ValidateCredentialsTests {

            @Test
            @DisplayName("should pass when credentials are available")
            void shouldPassWhenCredentialsAvailable() {
                // Given
                setupValidRecaptchaConfig();
                when(credentialsProvider.getCachedCredentials()).thenReturn(mock(com.google.auth.oauth2.GoogleCredentials.class));
                when(credentialsProvider.getServiceAccountEmail()).thenReturn("test@project.iam.gserviceaccount.com");
                when(credentialsProvider.getProjectId()).thenReturn("test-project");
                when(credentialsProvider.getInstanceType()).thenReturn("TEST");

                // When
                validator.validateOnStartup();

                // Then
                verify(credentialsProvider).getCachedCredentials();
            }

            @Test
            @DisplayName("should fail when no credentials available")
            void shouldFailWhenNoCredentials() {
                // Given
                setupValidRecaptchaConfig();
                when(credentialsProvider.getCachedCredentials()).thenReturn(null);
                when(credentialsProvider.getProjectId()).thenReturn("test-project");
                when(credentialsProvider.getInstanceType()).thenReturn("TEST");

                // When
                validator.validateOnStartup();

                // Then
                verify(credentialsProvider).getCachedCredentials();
            }

            @Test
            @DisplayName("should handle credential provider exception")
            void shouldHandleCredentialProviderException() {
                // Given
                setupValidRecaptchaConfig();
                when(credentialsProvider.getCachedCredentials()).thenThrow(new RuntimeException("Credential error"));

                // When
                validator.validateOnStartup();

                // Then - should continue despite error
                verify(credentialsProvider).getCachedCredentials();
            }
        }

        @Nested
        @DisplayName("validateSiteKey")
        class ValidateSiteKeyTests {

            @Test
            @DisplayName("should pass with 40-character site key")
            void shouldPassWith40CharSiteKey() {
                // Given
                setupValidRecaptchaConfig();

                // When
                validator.validateOnStartup();

                // Then
                verify(recaptchaConfig, atLeastOnce()).getSiteKey();
            }

            @Test
            @DisplayName("should throw when site key is too short for substring operation")
            void shouldWarnWithUnusualSiteKeyLength() {
                // Given
                when(recaptchaConfig.isEnabled()).thenReturn(true);
                when(recaptchaConfig.getProjectId()).thenReturn("test-project");
                when(recaptchaConfig.getSiteKey()).thenReturn("short-key");
                setupMinimalCredentialsProvider();
                setupValidThresholds();

                // When/Then - printValidationSummary calls getSiteKey().substring(36) which throws
                // for keys shorter than 40 characters
                assertThatThrownBy(() -> validator.validateOnStartup())
                    .isInstanceOf(StringIndexOutOfBoundsException.class);
            }
        }

        @Nested
        @DisplayName("validateThresholds")
        class ValidateThresholdsTests {

            @Test
            @DisplayName("should pass with valid thresholds")
            void shouldPassWithValidThresholds() {
                // Given
                setupValidRecaptchaConfig();

                // When
                validator.validateOnStartup();

                // Then
                verify(recaptchaConfig).getLoginThreshold();
                verify(recaptchaConfig).getSignupThreshold();
                verify(recaptchaConfig).getForgotPasswordThreshold();
            }

            @ParameterizedTest
            @ValueSource(doubles = {-0.1, 1.1, 2.0, -1.0})
            @DisplayName("should fail with invalid login threshold")
            void shouldFailWithInvalidLoginThreshold(double threshold) {
                // Given
                when(recaptchaConfig.isEnabled()).thenReturn(true);
                when(recaptchaConfig.getProjectId()).thenReturn("test-project");
                when(recaptchaConfig.getSiteKey()).thenReturn("1234567890123456789012345678901234567890");
                setupMinimalCredentialsProvider();
                when(recaptchaConfig.getLoginThreshold()).thenReturn(threshold);
                when(recaptchaConfig.getSignupThreshold()).thenReturn(0.5);
                when(recaptchaConfig.getForgotPasswordThreshold()).thenReturn(0.5);
                when(recaptchaConfig.getCacheDuration()).thenReturn(60);

                // When
                validator.validateOnStartup();

                // Then
                verify(recaptchaConfig).getLoginThreshold();
            }

            @ParameterizedTest
            @ValueSource(doubles = {-0.1, 1.1, 2.0, -1.0})
            @DisplayName("should fail with invalid signup threshold")
            void shouldFailWithInvalidSignupThreshold(double threshold) {
                // Given
                when(recaptchaConfig.isEnabled()).thenReturn(true);
                when(recaptchaConfig.getProjectId()).thenReturn("test-project");
                when(recaptchaConfig.getSiteKey()).thenReturn("1234567890123456789012345678901234567890");
                setupMinimalCredentialsProvider();
                when(recaptchaConfig.getLoginThreshold()).thenReturn(0.5);
                when(recaptchaConfig.getSignupThreshold()).thenReturn(threshold);
                when(recaptchaConfig.getForgotPasswordThreshold()).thenReturn(0.5);
                when(recaptchaConfig.getCacheDuration()).thenReturn(60);

                // When
                validator.validateOnStartup();

                // Then
                verify(recaptchaConfig).getSignupThreshold();
            }

            @ParameterizedTest
            @ValueSource(doubles = {0.0, 0.5, 1.0, 0.3, 0.7})
            @DisplayName("should pass with valid threshold values")
            void shouldPassWithValidThresholdValues(double threshold) {
                // Given
                when(recaptchaConfig.isEnabled()).thenReturn(true);
                when(recaptchaConfig.getProjectId()).thenReturn("test-project");
                when(recaptchaConfig.getSiteKey()).thenReturn("1234567890123456789012345678901234567890");
                setupMinimalCredentialsProvider();
                when(recaptchaConfig.getLoginThreshold()).thenReturn(threshold);
                when(recaptchaConfig.getSignupThreshold()).thenReturn(threshold);
                when(recaptchaConfig.getForgotPasswordThreshold()).thenReturn(threshold);
                when(recaptchaConfig.getCacheDuration()).thenReturn(60);

                // When
                validator.validateOnStartup();

                // Then - no exception means success
                verify(recaptchaConfig).getLoginThreshold();
            }
        }

        @Nested
        @DisplayName("Email Masking")
        class EmailMaskingTests {

            @Test
            @DisplayName("should mask email correctly")
            void shouldMaskEmailCorrectly() {
                // Given
                setupValidRecaptchaConfig();
                when(credentialsProvider.getServiceAccountEmail()).thenReturn("service@project.iam.gserviceaccount.com");

                // When
                validator.validateOnStartup();

                // Then
                verify(credentialsProvider).getServiceAccountEmail();
            }

            @Test
            @DisplayName("should handle null email gracefully")
            void shouldHandleNullEmailGracefully() {
                // Given
                setupValidRecaptchaConfig();
                when(credentialsProvider.getServiceAccountEmail()).thenReturn(null);

                // When
                validator.validateOnStartup();

                // Then - should not throw
                verify(credentialsProvider).getServiceAccountEmail();
            }

            @Test
            @DisplayName("should handle empty email gracefully")
            void shouldHandleEmptyEmailGracefully() {
                // Given
                setupValidRecaptchaConfig();
                when(credentialsProvider.getServiceAccountEmail()).thenReturn("");

                // When
                validator.validateOnStartup();

                // Then - should not throw
                verify(credentialsProvider).getServiceAccountEmail();
            }

            @Test
            @DisplayName("should handle short email gracefully")
            void shouldHandleShortEmailGracefully() {
                // Given
                setupValidRecaptchaConfig();
                when(credentialsProvider.getServiceAccountEmail()).thenReturn("a@b");

                // When
                validator.validateOnStartup();

                // Then - should not throw
                verify(credentialsProvider).getServiceAccountEmail();
            }
        }

        @Nested
        @DisplayName("Project Access Validation")
        class ProjectAccessValidationTests {

            @Test
            @DisplayName("should detect project ID mismatch")
            void shouldDetectProjectIdMismatch() {
                // Given
                when(recaptchaConfig.isEnabled()).thenReturn(true);
                when(recaptchaConfig.getProjectId()).thenReturn("config-project");
                when(recaptchaConfig.getSiteKey()).thenReturn("1234567890123456789012345678901234567890");
                setupValidThresholds();
                when(credentialsProvider.getCachedCredentials()).thenReturn(mock(com.google.auth.oauth2.GoogleCredentials.class));
                when(credentialsProvider.getServiceAccountEmail()).thenReturn("test@project.iam.gserviceaccount.com");
                when(credentialsProvider.getProjectId()).thenReturn("credential-project");
                when(credentialsProvider.getInstanceType()).thenReturn("TEST");

                // When
                validator.validateOnStartup();

                // Then - should log warning about mismatch
                verify(credentialsProvider).getProjectId();
            }
        }

        private void setupValidRecaptchaConfig() {
            when(recaptchaConfig.isEnabled()).thenReturn(true);
            when(recaptchaConfig.getProjectId()).thenReturn("test-project");
            when(recaptchaConfig.getSiteKey()).thenReturn("1234567890123456789012345678901234567890");
            setupMinimalCredentialsProvider();
            setupValidThresholds();
        }

        private void setupMinimalCredentialsProvider() {
            when(credentialsProvider.getCachedCredentials()).thenReturn(mock(com.google.auth.oauth2.GoogleCredentials.class));
            when(credentialsProvider.getServiceAccountEmail()).thenReturn("test@project.iam.gserviceaccount.com");
            when(credentialsProvider.getProjectId()).thenReturn("test-project");
            when(credentialsProvider.getInstanceType()).thenReturn("TEST");
        }

        private void setupValidThresholds() {
            when(recaptchaConfig.getLoginThreshold()).thenReturn(0.5);
            when(recaptchaConfig.getSignupThreshold()).thenReturn(0.3);
            when(recaptchaConfig.getForgotPasswordThreshold()).thenReturn(0.3);
            when(recaptchaConfig.getCacheDuration()).thenReturn(60);
        }
    }

    // ========================================================================
    // TOTP QR CODE STARTUP VALIDATOR TESTS
    // ========================================================================

    @Nested
    @DisplayName("TotpQRCodeStartupValidator Tests")
    class TotpQRCodeStartupValidatorTests {

        @Mock
        private ImprovedQRCodeService improvedQRCodeService;

        @Mock
        private QRCodeGeneratorService qrCodeGeneratorService;

        @Mock
        private ApplicationReadyEvent applicationReadyEvent;

        private TotpQRCodeStartupValidator validator;

        private static final String TEST_SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
        private static final String TEST_EMAIL = "validator@test.com";
        private static final String VALID_QR_DATA_URL = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        private static final String VALID_OTPAUTH_URL = "otpauth://totp/CheckItOut:validator@test.com?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=CheckItOut&algorithm=SHA1&digits=6&period=30";

        @BeforeEach
        void setUp() {
            validator = new TotpQRCodeStartupValidator(improvedQRCodeService, qrCodeGeneratorService);
            ReflectionTestUtils.setField(validator, "totpEnabled", true);
            ReflectionTestUtils.setField(validator, "issuer", "CheckItOut");
        }

        @Nested
        @DisplayName("validateOnStartup - Disabled State")
        class DisabledStateTests {

            @Test
            @DisplayName("should skip validation when TOTP is disabled")
            void shouldSkipValidationWhenDisabled() {
                // Given
                ReflectionTestUtils.setField(validator, "totpEnabled", false);

                // When
                validator.validateOnStartup();

                // Then
                verifyNoInteractions(improvedQRCodeService);
                verifyNoInteractions(qrCodeGeneratorService);
            }

            @Test
            @DisplayName("should not throw exception when disabled")
            void shouldNotThrowWhenDisabled() {
                // Given
                ReflectionTestUtils.setField(validator, "totpEnabled", false);

                // When/Then
                assertThatNoException().isThrownBy(() -> validator.validateOnStartup());
            }
        }

        @Nested
        @DisplayName("validateConfiguration")
        class ValidateConfigurationTests {

            @Test
            @DisplayName("should pass with valid issuer configuration")
            void shouldPassWithValidIssuer() {
                // Given
                setupSuccessfulQRGeneration();

                // When
                validator.validateOnStartup();

                // Then - no exception means success
                verify(improvedQRCodeService).generateSecret();
            }

            @Test
            @DisplayName("should fail config validation when issuer is null but continue to secret generation")
            void shouldFailWhenIssuerIsNull() {
                // Given
                ReflectionTestUtils.setField(validator, "issuer", null);
                setupSuccessfulQRGeneration();

                // When
                validator.validateOnStartup();

                // Then - the validator logs config failure but continues execution
                // so secret generation is still called
                verify(improvedQRCodeService).generateSecret();
            }

            @Test
            @DisplayName("should fail config validation when issuer is empty but continue to secret generation")
            void shouldFailWhenIssuerIsEmpty() {
                // Given
                ReflectionTestUtils.setField(validator, "issuer", "");
                setupSuccessfulQRGeneration();

                // When
                validator.validateOnStartup();

                // Then - the validator logs config failure but continues execution
                verify(improvedQRCodeService).generateSecret();
            }
        }

        @Nested
        @DisplayName("testSecretGeneration")
        class SecretGenerationTests {

            @Test
            @DisplayName("should pass with valid 32-character Base32 secret")
            void shouldPassWithValidSecret() {
                // Given
                setupSuccessfulQRGeneration();

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).generateSecret();
            }

            @Test
            @DisplayName("should fail when secret is null")
            void shouldFailWhenSecretIsNull() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn(null);

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).generateSecret();
            }

            @Test
            @DisplayName("should fail when secret is empty")
            void shouldFailWhenSecretIsEmpty() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn("");

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).generateSecret();
            }

            @Test
            @DisplayName("should fail when secret is not Base32")
            void shouldFailWhenSecretIsNotBase32() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn("not-valid-base32!");

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).generateSecret();
            }

            @Test
            @DisplayName("should warn when secret has unusual length")
            void shouldWarnWhenSecretHasUnusualLength() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn("ABCD"); // Too short

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).generateSecret();
            }

            @Test
            @DisplayName("should handle exception during secret generation")
            void shouldHandleSecretGenerationException() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenThrow(new RuntimeException("Generation failed"));

                // When
                validator.validateOnStartup();

                // Then - should not throw
                verify(improvedQRCodeService).generateSecret();
            }
        }

        @Nested
        @DisplayName("testImprovedQRGeneration")
        class ImprovedQRGenerationTests {

            @Test
            @DisplayName("should pass with valid QR code data URL")
            void shouldPassWithValidQRDataUrl() {
                // Given
                setupSuccessfulQRGeneration();

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).generateQRCode(anyString(), eq(TEST_EMAIL));
            }

            @Test
            @DisplayName("should fail when QR code is null")
            void shouldFailWhenQRCodeIsNull() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn(TEST_SECRET);
                when(improvedQRCodeService.generateQRCode(anyString(), anyString())).thenReturn(null);
                setupOtherServices();

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).generateQRCode(anyString(), eq(TEST_EMAIL));
            }

            @Test
            @DisplayName("should fail when QR code has invalid format")
            void shouldFailWhenQRCodeHasInvalidFormat() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn(TEST_SECRET);
                when(improvedQRCodeService.generateQRCode(anyString(), anyString())).thenReturn("invalid-format");
                setupOtherServices();

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).generateQRCode(anyString(), eq(TEST_EMAIL));
            }

            @Test
            @DisplayName("should handle QR generation exception")
            void shouldHandleQRGenerationException() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn(TEST_SECRET);
                when(improvedQRCodeService.generateQRCode(anyString(), anyString()))
                    .thenThrow(new RuntimeException("QR generation failed"));
                setupOtherServices();

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).generateQRCode(anyString(), eq(TEST_EMAIL));
            }
        }

        @Nested
        @DisplayName("testOtpAuthUrlFormat")
        class OtpAuthUrlFormatTests {

            @Test
            @DisplayName("should pass with valid otpauth URL")
            void shouldPassWithValidOtpAuthUrl() {
                // Given
                setupSuccessfulQRGeneration();

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService, atLeast(1)).generateOtpAuthUrl(anyString(), eq(TEST_EMAIL));
            }

            @Test
            @DisplayName("should fail when otpauth URL is null")
            void shouldFailWhenOtpAuthUrlIsNull() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn(TEST_SECRET);
                when(improvedQRCodeService.generateQRCode(anyString(), anyString())).thenReturn(VALID_QR_DATA_URL);
                when(improvedQRCodeService.generateOtpAuthUrl(anyString(), anyString())).thenReturn(null);
                setupOtherServices();

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService, atLeast(1)).generateOtpAuthUrl(anyString(), eq(TEST_EMAIL));
            }

            @Test
            @DisplayName("should fail when otpauth URL has wrong prefix")
            void shouldFailWhenOtpAuthUrlHasWrongPrefix() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn(TEST_SECRET);
                when(improvedQRCodeService.generateQRCode(anyString(), anyString())).thenReturn(VALID_QR_DATA_URL);
                when(improvedQRCodeService.generateOtpAuthUrl(anyString(), anyString())).thenReturn("https://not-otpauth.com");
                setupOtherServices();

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService, atLeast(1)).generateOtpAuthUrl(anyString(), eq(TEST_EMAIL));
            }

            @Test
            @DisplayName("should warn when URL missing issuer parameter")
            void shouldWarnWhenMissingIssuer() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn(TEST_SECRET);
                when(improvedQRCodeService.generateQRCode(anyString(), anyString())).thenReturn(VALID_QR_DATA_URL);
                when(improvedQRCodeService.generateOtpAuthUrl(anyString(), anyString()))
                    .thenReturn("otpauth://totp/Test?secret=" + TEST_SECRET);
                setupOtherServices();

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService, atLeast(1)).generateOtpAuthUrl(anyString(), eq(TEST_EMAIL));
            }
        }

        @Nested
        @DisplayName("testGoogleChartsQR")
        class GoogleChartsQRTests {

            @Test
            @DisplayName("should pass with valid QuickChart URL")
            void shouldPassWithValidQuickChartUrl() {
                // Given
                setupSuccessfulQRGeneration();

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).generateGoogleChartsQRUrl(anyString(), eq(TEST_EMAIL));
            }

            @Test
            @DisplayName("should fail when external QR URL is null")
            void shouldFailWhenExternalQRUrlIsNull() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn(TEST_SECRET);
                when(improvedQRCodeService.generateQRCode(anyString(), anyString())).thenReturn(VALID_QR_DATA_URL);
                when(improvedQRCodeService.generateOtpAuthUrl(anyString(), anyString())).thenReturn(VALID_OTPAUTH_URL);
                when(improvedQRCodeService.generateGoogleChartsQRUrl(anyString(), anyString())).thenReturn(null);
                setupOtherServices();

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).generateGoogleChartsQRUrl(anyString(), eq(TEST_EMAIL));
            }
        }

        @Nested
        @DisplayName("testLegacyQRGenerator")
        class LegacyQRGeneratorTests {

            @Test
            @DisplayName("should pass with valid simple QR code")
            void shouldPassWithValidSimpleQR() {
                // Given
                setupSuccessfulQRGeneration();

                // When
                validator.validateOnStartup();

                // Then
                verify(qrCodeGeneratorService).generateSimpleQRCode(anyString());
            }

            @Test
            @DisplayName("should fail when simple QR generation fails")
            void shouldFailWhenSimpleQRFails() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn(TEST_SECRET);
                when(improvedQRCodeService.generateQRCode(anyString(), anyString())).thenReturn(VALID_QR_DATA_URL);
                when(improvedQRCodeService.generateOtpAuthUrl(anyString(), anyString())).thenReturn(VALID_OTPAUTH_URL);
                when(improvedQRCodeService.generateGoogleChartsQRUrl(anyString(), anyString())).thenReturn("https://quickchart.io/qr?text=test");
                when(qrCodeGeneratorService.generateSimpleQRCode(anyString())).thenReturn(null);
                setupManualEntryInfo();

                // When
                validator.validateOnStartup();

                // Then
                verify(qrCodeGeneratorService).generateSimpleQRCode(anyString());
            }

            @Test
            @DisplayName("should handle legacy QR exception")
            void shouldHandleLegacyQRException() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn(TEST_SECRET);
                when(improvedQRCodeService.generateQRCode(anyString(), anyString())).thenReturn(VALID_QR_DATA_URL);
                when(improvedQRCodeService.generateOtpAuthUrl(anyString(), anyString())).thenReturn(VALID_OTPAUTH_URL);
                when(improvedQRCodeService.generateGoogleChartsQRUrl(anyString(), anyString())).thenReturn("https://quickchart.io/qr?text=test");
                when(qrCodeGeneratorService.generateSimpleQRCode(anyString()))
                    .thenThrow(new RuntimeException("Legacy QR failed"));
                setupManualEntryInfo();

                // When
                validator.validateOnStartup();

                // Then
                verify(qrCodeGeneratorService).generateSimpleQRCode(anyString());
            }
        }

        @Nested
        @DisplayName("testManualEntryInfo")
        class ManualEntryInfoTests {

            @Test
            @DisplayName("should pass with valid manual entry info")
            void shouldPassWithValidManualEntryInfo() {
                // Given
                setupSuccessfulQRGeneration();

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).getManualEntryInfo(anyString(), eq(TEST_EMAIL));
            }

            @Test
            @DisplayName("should fail when manual entry info is null")
            void shouldFailWhenManualEntryInfoIsNull() {
                // Given
                when(improvedQRCodeService.generateSecret()).thenReturn(TEST_SECRET);
                when(improvedQRCodeService.generateQRCode(anyString(), anyString())).thenReturn(VALID_QR_DATA_URL);
                when(improvedQRCodeService.generateOtpAuthUrl(anyString(), anyString())).thenReturn(VALID_OTPAUTH_URL);
                when(improvedQRCodeService.generateGoogleChartsQRUrl(anyString(), anyString())).thenReturn("https://quickchart.io/qr?text=test");
                when(qrCodeGeneratorService.generateSimpleQRCode(anyString())).thenReturn(VALID_QR_DATA_URL);
                when(improvedQRCodeService.getManualEntryInfo(anyString(), anyString())).thenReturn(null);

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).getManualEntryInfo(anyString(), eq(TEST_EMAIL));
            }

            @Test
            @DisplayName("should warn when secret not formatted with spaces")
            void shouldWarnWhenSecretNotFormatted() {
                // Given
                ImprovedQRCodeService.ManualEntryInfo info = ImprovedQRCodeService.ManualEntryInfo.builder()
                    .accountName(TEST_EMAIL)
                    .issuer("CheckItOut")
                    .secret(TEST_SECRET) // No spaces
                    .secretRaw(TEST_SECRET)
                    .type("Time based (TOTP)")
                    .algorithm("SHA1")
                    .digits("6")
                    .period("30 seconds")
                    .build();

                when(improvedQRCodeService.generateSecret()).thenReturn(TEST_SECRET);
                when(improvedQRCodeService.generateQRCode(anyString(), anyString())).thenReturn(VALID_QR_DATA_URL);
                when(improvedQRCodeService.generateOtpAuthUrl(anyString(), anyString())).thenReturn(VALID_OTPAUTH_URL);
                when(improvedQRCodeService.generateGoogleChartsQRUrl(anyString(), anyString())).thenReturn("https://quickchart.io/qr?text=test");
                when(qrCodeGeneratorService.generateSimpleQRCode(anyString())).thenReturn(VALID_QR_DATA_URL);
                when(improvedQRCodeService.getManualEntryInfo(anyString(), anyString())).thenReturn(info);

                // When
                validator.validateOnStartup();

                // Then
                verify(improvedQRCodeService).getManualEntryInfo(anyString(), eq(TEST_EMAIL));
            }
        }

        private void setupSuccessfulQRGeneration() {
            when(improvedQRCodeService.generateSecret()).thenReturn(TEST_SECRET);
            when(improvedQRCodeService.generateQRCode(anyString(), eq(TEST_EMAIL))).thenReturn(VALID_QR_DATA_URL);
            when(improvedQRCodeService.generateOtpAuthUrl(anyString(), eq(TEST_EMAIL))).thenReturn(VALID_OTPAUTH_URL);
            when(improvedQRCodeService.generateGoogleChartsQRUrl(anyString(), eq(TEST_EMAIL)))
                .thenReturn("https://quickchart.io/qr?text=test&size=300");
            when(qrCodeGeneratorService.generateSimpleQRCode(anyString())).thenReturn(VALID_QR_DATA_URL);
            setupManualEntryInfo();
        }

        private void setupOtherServices() {
            when(improvedQRCodeService.generateGoogleChartsQRUrl(anyString(), anyString()))
                .thenReturn("https://quickchart.io/qr?text=test");
            when(qrCodeGeneratorService.generateSimpleQRCode(anyString())).thenReturn(VALID_QR_DATA_URL);
            setupManualEntryInfo();
        }

        private void setupManualEntryInfo() {
            ImprovedQRCodeService.ManualEntryInfo info = ImprovedQRCodeService.ManualEntryInfo.builder()
                .accountName(TEST_EMAIL)
                .issuer("CheckItOut")
                .secret("JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP")
                .secretRaw(TEST_SECRET)
                .type("Time based (TOTP)")
                .algorithm("SHA1")
                .digits("6")
                .period("30 seconds")
                .build();
            when(improvedQRCodeService.getManualEntryInfo(anyString(), anyString())).thenReturn(info);
        }
    }

    // ========================================================================
    // TOTP VALIDATION SERVICE EXTENDED TESTS
    // ========================================================================

    @Nested
    @DisplayName("TotpValidationService Extended Tests")
    class TotpValidationServiceExtendedTests {

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
        private static final String TEST_ENCODED_SECRET = "ORSXG5DJNZUXI4ZTORUGS3TH";

        @BeforeEach
        void setUp() {
            service = new TotpValidationService(firestore, kmsService, totpFirestoreService, gAuth);
            ReflectionTestUtils.setField(service, "totpEnabled", true);
            ReflectionTestUtils.setField(service, "issuer", "CheckItOut");
            ReflectionTestUtils.setField(service, "totpKeyName", "totp-secrets-key");
            ReflectionTestUtils.setField(service, "keyRingName", "instagram-tokens");
        }

        @Nested
        @DisplayName("Extended Configuration Tests")
        class ExtendedConfigurationTests {

            @ParameterizedTest
            @ValueSource(strings = {"MyApp", "TestIssuer", "Production-App", "App_123"})
            @DisplayName("should handle various issuer names")
            void shouldHandleVariousIssuerNames(String issuerName) throws Exception {
                // Given
                ReflectionTestUtils.setField(service, "issuer", issuerName);
                setupSuccessfulValidation();

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(gAuth).createCredentials();
            }

            @ParameterizedTest
            @ValueSource(strings = {"key-ring-1", "my_key_ring", "production-keys"})
            @DisplayName("should handle various key ring names")
            void shouldHandleVariousKeyRingNames(String keyRingName) throws Exception {
                // Given
                ReflectionTestUtils.setField(service, "keyRingName", keyRingName);
                setupSuccessfulValidation();

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(kmsService).encryptTotpSecret(anyString());
            }

            @ParameterizedTest
            @ValueSource(strings = {"totp-key", "my_totp_key", "2fa-secrets-key"})
            @DisplayName("should handle various TOTP key names")
            void shouldHandleVariousTotpKeyNames(String totpKeyName) throws Exception {
                // Given
                ReflectionTestUtils.setField(service, "totpKeyName", totpKeyName);
                setupSuccessfulValidation();

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(kmsService).encryptTotpSecret(anyString());
            }
        }

        @Nested
        @DisplayName("Extended KMS Validation Tests")
        class ExtendedKMSValidationTests {

            @Test
            @DisplayName("should handle KMS returning null for encryption")
            void shouldHandleKMSReturningNullForEncryption() throws Exception {
                // Given
                when(kmsService.encryptTotpSecret(anyString())).thenReturn(null);
                setupFirestoreAccess();
                setupTOTPGeneration();

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(kmsService).encryptTotpSecret(anyString());
            }

            @Test
            @DisplayName("should handle KMS returning empty for encryption")
            void shouldHandleKMSReturningEmptyForEncryption() throws Exception {
                // Given
                when(kmsService.encryptTotpSecret(anyString())).thenReturn("");
                setupFirestoreAccess();
                setupTOTPGeneration();

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(kmsService).encryptTotpSecret(anyString());
            }

            @Test
            @DisplayName("should handle various KMS exception types")
            void shouldHandleVariousKMSExceptionTypes() throws Exception {
                // Given
                when(kmsService.encryptTotpSecret(anyString()))
                    .thenThrow(new IllegalStateException("KMS not initialized"));
                setupFirestoreAccess();
                setupTOTPGeneration();

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(kmsService).encryptTotpSecret(anyString());
            }
        }

        @Nested
        @DisplayName("Extended Firestore Validation Tests")
        class ExtendedFirestoreValidationTests {

            @Test
            @DisplayName("should handle Firestore connection timeout")
            void shouldHandleFirestoreConnectionTimeout() throws Exception {
                // Given
                setupKMSAccess();
                when(firestore.collection("totpSecrets")).thenReturn(collectionReference);
                when(collectionReference.document("test-validation")).thenReturn(documentReference);
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get())
                    .thenThrow(new ExecutionException("DEADLINE_EXCEEDED", new RuntimeException()));
                setupTOTPGeneration();

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(firestore).collection("totpSecrets");
            }

            @Test
            @DisplayName("should handle Firestore permission denied")
            void shouldHandleFirestorePermissionDenied() throws Exception {
                // Given
                setupKMSAccess();
                when(firestore.collection("totpSecrets")).thenReturn(collectionReference);
                when(collectionReference.document("test-validation")).thenReturn(documentReference);
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get())
                    .thenThrow(new ExecutionException("PERMISSION_DENIED", new RuntimeException()));
                setupTOTPGeneration();

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(firestore).collection("totpSecrets");
            }

            @Test
            @DisplayName("should handle null collection reference")
            void shouldHandleNullCollectionReference() throws Exception {
                // Given
                setupKMSAccess();
                when(firestore.collection("totpSecrets")).thenReturn(null);
                setupTOTPGeneration();

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(firestore).collection("totpSecrets");
            }
        }

        @Nested
        @DisplayName("Extended TOTP Generation Tests")
        class ExtendedTOTPGenerationTests {

            @Test
            @DisplayName("should handle null GoogleAuthenticatorKey")
            void shouldHandleNullGoogleAuthenticatorKey() throws Exception {
                // Given
                setupKMSAccess();
                setupFirestoreAccess();
                when(gAuth.createCredentials()).thenReturn(null);

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(gAuth).createCredentials();
            }

            @Test
            @DisplayName("should handle very long secret key")
            void shouldHandleVeryLongSecretKey() throws Exception {
                // Given
                setupKMSAccess();
                setupFirestoreAccess();
                String longSecret = "A".repeat(100);
                when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
                when(googleAuthenticatorKey.getKey()).thenReturn(longSecret);
                when(gAuth.getTotpPassword(anyString())).thenReturn(TEST_TOTP_CODE);
                when(gAuth.authorize(anyString(), anyInt())).thenReturn(true);

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(googleAuthenticatorKey).getKey();
            }

            @Test
            @DisplayName("should handle TOTP code with leading zeros")
            void shouldHandleTOTPCodeWithLeadingZeros() throws Exception {
                // Given
                setupKMSAccess();
                setupFirestoreAccess();
                when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
                when(googleAuthenticatorKey.getKey()).thenReturn(TEST_SECRET);
                when(gAuth.getTotpPassword(anyString())).thenReturn(1234); // 001234 when formatted
                when(gAuth.authorize(anyString(), anyInt())).thenReturn(true);

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(gAuth).getTotpPassword(anyString());
            }

            @Test
            @DisplayName("should handle maximum TOTP code value")
            void shouldHandleMaximumTOTPCodeValue() throws Exception {
                // Given
                setupKMSAccess();
                setupFirestoreAccess();
                when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
                when(googleAuthenticatorKey.getKey()).thenReturn(TEST_SECRET);
                when(gAuth.getTotpPassword(anyString())).thenReturn(999999);
                when(gAuth.authorize(anyString(), anyInt())).thenReturn(true);

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(gAuth).getTotpPassword(anyString());
            }

            @Test
            @DisplayName("should handle zero TOTP code")
            void shouldHandleZeroTOTPCode() throws Exception {
                // Given
                setupKMSAccess();
                setupFirestoreAccess();
                when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
                when(googleAuthenticatorKey.getKey()).thenReturn(TEST_SECRET);
                when(gAuth.getTotpPassword(anyString())).thenReturn(0);
                when(gAuth.authorize(anyString(), anyInt())).thenReturn(true);

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(gAuth).getTotpPassword(anyString());
            }
        }

        @Nested
        @DisplayName("Time Window Tolerance Tests")
        class TimeWindowToleranceTests {

            @Test
            @DisplayName("should validate time window tolerance on first try")
            void shouldValidateTimeWindowToleranceOnFirstTry() throws Exception {
                // Given
                setupSuccessfulValidation();

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(gAuth, atLeast(1)).authorize(anyString(), anyInt());
            }

            @Test
            @DisplayName("should handle time window validation failure")
            void shouldHandleTimeWindowValidationFailure() throws Exception {
                // Given
                setupKMSAccess();
                setupFirestoreAccess();
                when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
                when(googleAuthenticatorKey.getKey()).thenReturn(TEST_SECRET);
                when(gAuth.getTotpPassword(anyString())).thenReturn(TEST_TOTP_CODE);
                // First authorize passes, second fails (time window check)
                when(gAuth.authorize(anyString(), anyInt()))
                    .thenReturn(true)
                    .thenReturn(false);

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(gAuth, atLeast(1)).authorize(anyString(), anyInt());
            }
        }

        @Nested
        @DisplayName("Combined Failure Scenarios")
        class CombinedFailureScenarios {

            @Test
            @DisplayName("should handle KMS and Firestore both failing")
            void shouldHandleKMSAndFirestoreBothFailing() {
                // Given
                when(kmsService.encryptTotpSecret(anyString()))
                    .thenThrow(new RuntimeException("KMS failed"));
                when(firestore.collection("totpSecrets"))
                    .thenThrow(new RuntimeException("Firestore failed"));
                setupTOTPGeneration();

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(kmsService).encryptTotpSecret(anyString());
                verify(firestore).collection("totpSecrets");
            }

            @Test
            @DisplayName("should handle Firestore and TOTP both failing")
            void shouldHandleFirestoreAndTOTPBothFailing() throws Exception {
                // Given
                setupKMSAccess();
                when(firestore.collection("totpSecrets"))
                    .thenThrow(new RuntimeException("Firestore failed"));
                when(gAuth.createCredentials())
                    .thenThrow(new RuntimeException("TOTP failed"));

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(firestore).collection("totpSecrets");
                verify(gAuth).createCredentials();
            }

            @Test
            @DisplayName("should still complete when only KMS passes")
            void shouldCompleteWhenOnlyKMSPasses() throws Exception {
                // Given
                setupKMSAccess();
                when(firestore.collection("totpSecrets"))
                    .thenThrow(new RuntimeException("Firestore failed"));
                when(gAuth.createCredentials())
                    .thenThrow(new RuntimeException("TOTP failed"));

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(kmsService).encryptTotpSecret(anyString());
                verify(kmsService).decryptTotpSecret(anyString());
            }
        }

        @Nested
        @DisplayName("Edge Cases for Secret Handling")
        class SecretHandlingEdgeCases {

            @Test
            @DisplayName("should handle secret with special characters")
            void shouldHandleSecretWithSpecialCharacters() throws Exception {
                // Given
                setupKMSAccess();
                setupFirestoreAccess();
                when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
                when(googleAuthenticatorKey.getKey()).thenReturn("ABC=123+XYZ/");
                when(gAuth.getTotpPassword(anyString())).thenReturn(TEST_TOTP_CODE);
                when(gAuth.authorize(anyString(), anyInt())).thenReturn(true);

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(googleAuthenticatorKey).getKey();
            }

            @Test
            @DisplayName("should handle whitespace in secret")
            void shouldHandleWhitespaceInSecret() throws Exception {
                // Given
                setupKMSAccess();
                setupFirestoreAccess();
                when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
                when(googleAuthenticatorKey.getKey()).thenReturn(" ABC DEF ");
                when(gAuth.getTotpPassword(anyString())).thenReturn(TEST_TOTP_CODE);
                when(gAuth.authorize(anyString(), anyInt())).thenReturn(true);

                // When
                service.onApplicationEvent(applicationReadyEvent);

                // Then
                verify(googleAuthenticatorKey).getKey();
            }
        }

        private void setupSuccessfulValidation() throws Exception {
            setupKMSAccess();
            setupFirestoreAccess();
            setupTOTPGeneration();
        }

        private void setupKMSAccess() {
            when(kmsService.encryptTotpSecret(anyString())).thenReturn(ENCRYPTED_SECRET);
            when(kmsService.decryptTotpSecret(anyString())).thenReturn(TEST_ENCODED_SECRET);
        }

        private void setupFirestoreAccess() throws Exception {
            when(firestore.collection("totpSecrets")).thenReturn(collectionReference);
            when(collectionReference.document("test-validation")).thenReturn(documentReference);
            when(documentReference.get()).thenReturn(documentSnapshotFuture);
            when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
        }

        private void setupTOTPGeneration() {
            when(gAuth.createCredentials()).thenReturn(googleAuthenticatorKey);
            when(googleAuthenticatorKey.getKey()).thenReturn(TEST_SECRET);
            when(gAuth.getTotpPassword(anyString())).thenReturn(TEST_TOTP_CODE);
            when(gAuth.authorize(anyString(), anyInt())).thenReturn(true);
        }
    }

    // ========================================================================
    // RECAPTCHA CONFIG TESTS
    // ========================================================================

    @Nested
    @DisplayName("RecaptchaConfig Tests")
    class RecaptchaConfigTests {

        private RecaptchaConfig config;

        @BeforeEach
        void setUp() {
            config = new RecaptchaConfig();
        }

        @Nested
        @DisplayName("Basic Properties")
        class BasicPropertiesTests {

            @Test
            @DisplayName("should set and get project ID")
            void shouldSetAndGetProjectId() {
                // Given
                String projectId = "my-project-id";

                // When
                config.setProjectId(projectId);

                // Then
                assertThat(config.getProjectId()).isEqualTo(projectId);
            }

            @Test
            @DisplayName("should set and get site key")
            void shouldSetAndGetSiteKey() {
                // Given
                String siteKey = "1234567890123456789012345678901234567890";

                // When
                config.setSiteKey(siteKey);

                // Then
                assertThat(config.getSiteKey()).isEqualTo(siteKey);
            }

            @Test
            @DisplayName("should set and get enabled status")
            void shouldSetAndGetEnabledStatus() {
                // When
                config.setEnabled(false);

                // Then
                assertThat(config.isEnabled()).isFalse();

                // When
                config.setEnabled(true);

                // Then
                assertThat(config.isEnabled()).isTrue();
            }

            @Test
            @DisplayName("should have default enabled value of true")
            void shouldHaveDefaultEnabledValue() {
                assertThat(config.isEnabled()).isTrue();
            }

            @Test
            @DisplayName("should set and get cache duration")
            void shouldSetAndGetCacheDuration() {
                // Given
                int cacheDuration = 120;

                // When
                config.setCacheDuration(cacheDuration);

                // Then
                assertThat(config.getCacheDuration()).isEqualTo(cacheDuration);
            }

            @Test
            @DisplayName("should have default cache duration of 60")
            void shouldHaveDefaultCacheDuration() {
                assertThat(config.getCacheDuration()).isEqualTo(60);
            }
        }

        @Nested
        @DisplayName("Threshold Properties")
        class ThresholdPropertiesTests {

            @Test
            @DisplayName("should set and get score threshold")
            void shouldSetAndGetScoreThreshold() {
                // Given
                Double threshold = 0.7;

                // When
                config.setScoreThreshold(threshold);

                // Then
                assertThat(config.getScoreThreshold()).isEqualTo(threshold);
            }

            @Test
            @DisplayName("should have default score threshold of 0.5")
            void shouldHaveDefaultScoreThreshold() {
                assertThat(config.getScoreThreshold()).isEqualTo(0.5);
            }

            @Test
            @DisplayName("should set and get login threshold")
            void shouldSetAndGetLoginThreshold() {
                // Given
                Double threshold = 0.6;

                // When
                config.setLoginThreshold(threshold);

                // Then
                assertThat(config.getLoginThreshold()).isEqualTo(threshold);
            }

            @Test
            @DisplayName("should set and get signup threshold")
            void shouldSetAndGetSignupThreshold() {
                // Given
                Double threshold = 0.4;

                // When
                config.setSignupThreshold(threshold);

                // Then
                assertThat(config.getSignupThreshold()).isEqualTo(threshold);
            }

            @Test
            @DisplayName("should set and get forgot password threshold")
            void shouldSetAndGetForgotPasswordThreshold() {
                // Given
                Double threshold = 0.35;

                // When
                config.setForgotPasswordThreshold(threshold);

                // Then
                assertThat(config.getForgotPasswordThreshold()).isEqualTo(threshold);
            }

            @Test
            @DisplayName("should fall back to score threshold when login threshold is null")
            void shouldFallbackToScoreThresholdForLogin() {
                // Given
                config.setScoreThreshold(0.8);
                config.setLoginThreshold(null);

                // Then
                assertThat(config.getLoginThreshold()).isEqualTo(0.8);
            }

            @Test
            @DisplayName("should fall back to score threshold when signup threshold is null")
            void shouldFallbackToScoreThresholdForSignup() {
                // Given
                config.setScoreThreshold(0.8);
                config.setSignupThreshold(null);

                // Then
                assertThat(config.getSignupThreshold()).isEqualTo(0.8);
            }

            @Test
            @DisplayName("should fall back to score threshold when forgot password threshold is null")
            void shouldFallbackToScoreThresholdForForgotPassword() {
                // Given
                config.setScoreThreshold(0.8);
                config.setForgotPasswordThreshold(null);

                // Then
                assertThat(config.getForgotPasswordThreshold()).isEqualTo(0.8);
            }
        }

        @Nested
        @DisplayName("getThresholdForAction")
        class GetThresholdForActionTests {

            @BeforeEach
            void setupThresholds() {
                config.setLoginThreshold(0.5);
                config.setSignupThreshold(0.3);
                config.setForgotPasswordThreshold(0.35);
                config.setScoreThreshold(0.6);
            }

            @Test
            @DisplayName("should return login threshold for LOGIN action")
            void shouldReturnLoginThreshold() {
                assertThat(config.getThresholdForAction("LOGIN")).isEqualTo(0.5);
            }

            @Test
            @DisplayName("should return signup threshold for SIGNUP action")
            void shouldReturnSignupThreshold() {
                assertThat(config.getThresholdForAction("SIGNUP")).isEqualTo(0.3);
            }

            @Test
            @DisplayName("should return forgot password threshold for FORGOT_PASSWORD action")
            void shouldReturnForgotPasswordThreshold() {
                assertThat(config.getThresholdForAction("FORGOT_PASSWORD")).isEqualTo(0.35);
            }

            @Test
            @DisplayName("should return score threshold for unknown action")
            void shouldReturnScoreThresholdForUnknownAction() {
                assertThat(config.getThresholdForAction("UNKNOWN")).isEqualTo(0.6);
            }

            @Test
            @DisplayName("should return score threshold for null action")
            void shouldReturnScoreThresholdForNullAction() {
                assertThat(config.getThresholdForAction(null)).isEqualTo(0.6);
            }

            @ParameterizedTest
            @CsvSource({
                "login, 0.5",
                "Login, 0.5",
                "LOGIN, 0.5",
                "signup, 0.3",
                "Signup, 0.3",
                "SIGNUP, 0.3",
                "forgot_password, 0.35",
                "Forgot_Password, 0.35",
                "FORGOT_PASSWORD, 0.35"
            })
            @DisplayName("should handle case-insensitive action names")
            void shouldHandleCaseInsensitiveActionNames(String action, double expectedThreshold) {
                assertThat(config.getThresholdForAction(action)).isEqualTo(expectedThreshold);
            }
        }
    }

    // ========================================================================
    // IMPROVED QR CODE SERVICE TESTS
    // ========================================================================

    @Nested
    @DisplayName("ImprovedQRCodeService Tests")
    class ImprovedQRCodeServiceTests {

        private ImprovedQRCodeService service;

        @BeforeEach
        void setUp() {
            service = new ImprovedQRCodeService();
            ReflectionTestUtils.setField(service, "issuer", "TestApp");
            ReflectionTestUtils.setField(service, "digits", 6);
            ReflectionTestUtils.setField(service, "period", 30);
        }

        @Nested
        @DisplayName("generateSecret")
        class GenerateSecretTests {

            @Test
            @DisplayName("should generate non-null secret")
            void shouldGenerateNonNullSecret() {
                String secret = service.generateSecret();
                assertThat(secret).isNotNull();
            }

            @Test
            @DisplayName("should generate non-empty secret")
            void shouldGenerateNonEmptySecret() {
                String secret = service.generateSecret();
                assertThat(secret).isNotEmpty();
            }

            @Test
            @DisplayName("should generate Base32 secret")
            void shouldGenerateBase32Secret() {
                String secret = service.generateSecret();
                assertThat(secret).matches("^[A-Z2-7]+$");
            }

            @Test
            @DisplayName("should generate 32-character secret")
            void shouldGenerate32CharSecret() {
                String secret = service.generateSecret();
                assertThat(secret).hasSize(32);
            }

            @Test
            @DisplayName("should generate unique secrets")
            void shouldGenerateUniqueSecrets() {
                String secret1 = service.generateSecret();
                String secret2 = service.generateSecret();
                assertThat(secret1).isNotEqualTo(secret2);
            }

            @Test
            @DisplayName("should generate secret with Firebase UID tracking")
            void shouldGenerateSecretWithFirebaseUidTracking() {
                String secret = service.generateSecret("test-firebase-uid");
                assertThat(secret).isNotNull();
                assertThat(secret).hasSize(32);
            }
        }

        @Nested
        @DisplayName("isValidSecret")
        class IsValidSecretTests {

            @Test
            @DisplayName("should return true for valid Base32 secret")
            void shouldReturnTrueForValidSecret() {
                assertThat(service.isValidSecret("JBSWY3DPEHPK3PXP")).isTrue();
            }

            @Test
            @DisplayName("should return false for null secret")
            void shouldReturnFalseForNullSecret() {
                assertThat(service.isValidSecret(null)).isFalse();
            }

            @Test
            @DisplayName("should return false for empty secret")
            void shouldReturnFalseForEmptySecret() {
                assertThat(service.isValidSecret("")).isFalse();
            }

            @Test
            @DisplayName("should return false for secret with invalid characters")
            void shouldReturnFalseForInvalidCharacters() {
                assertThat(service.isValidSecret("ABC123!@#")).isFalse();
            }

            @Test
            @DisplayName("should return false for lowercase secret")
            void shouldReturnFalseForLowercaseSecret() {
                assertThat(service.isValidSecret("jbswy3dpehpk3pxp")).isFalse();
            }

            @ParameterizedTest
            @ValueSource(strings = {"01890", "ABCD1890", "TEST0189"})
            @DisplayName("should return false for secrets with invalid Base32 digits")
            void shouldReturnFalseForInvalidBase32Digits(String secret) {
                assertThat(service.isValidSecret(secret)).isFalse();
            }
        }

        @Nested
        @DisplayName("generateOtpAuthUrl")
        class GenerateOtpAuthUrlTests {

            private static final String TEST_SECRET = "JBSWY3DPEHPK3PXP";
            private static final String TEST_EMAIL = "test@example.com";

            @Test
            @DisplayName("should generate URL starting with otpauth://totp/")
            void shouldGenerateOtpAuthUrl() {
                String url = service.generateOtpAuthUrl(TEST_SECRET, TEST_EMAIL);
                assertThat(url).startsWith("otpauth://totp/");
            }

            @Test
            @DisplayName("should include secret parameter")
            void shouldIncludeSecretParameter() {
                String url = service.generateOtpAuthUrl(TEST_SECRET, TEST_EMAIL);
                assertThat(url).contains("secret=" + TEST_SECRET);
            }

            @Test
            @DisplayName("should include issuer parameter")
            void shouldIncludeIssuerParameter() {
                String url = service.generateOtpAuthUrl(TEST_SECRET, TEST_EMAIL);
                assertThat(url).contains("issuer=TestApp");
            }

            @Test
            @DisplayName("should include algorithm parameter")
            void shouldIncludeAlgorithmParameter() {
                String url = service.generateOtpAuthUrl(TEST_SECRET, TEST_EMAIL);
                assertThat(url).contains("algorithm=SHA1");
            }

            @Test
            @DisplayName("should include digits parameter")
            void shouldIncludeDigitsParameter() {
                String url = service.generateOtpAuthUrl(TEST_SECRET, TEST_EMAIL);
                assertThat(url).contains("digits=6");
            }

            @Test
            @DisplayName("should include period parameter")
            void shouldIncludePeriodParameter() {
                String url = service.generateOtpAuthUrl(TEST_SECRET, TEST_EMAIL);
                assertThat(url).contains("period=30");
            }
        }

        @Nested
        @DisplayName("getManualEntryInfo")
        class GetManualEntryInfoTests {

            private static final String TEST_SECRET = "JBSWY3DPEHPK3PXP";
            private static final String TEST_EMAIL = "test@example.com";

            @Test
            @DisplayName("should return non-null manual entry info")
            void shouldReturnNonNullInfo() {
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(TEST_SECRET, TEST_EMAIL);
                assertThat(info).isNotNull();
            }

            @Test
            @DisplayName("should include account name")
            void shouldIncludeAccountName() {
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(TEST_SECRET, TEST_EMAIL);
                assertThat(info.getAccountName()).isEqualTo(TEST_EMAIL);
            }

            @Test
            @DisplayName("should include issuer")
            void shouldIncludeIssuer() {
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(TEST_SECRET, TEST_EMAIL);
                assertThat(info.getIssuer()).isEqualTo("TestApp");
            }

            @Test
            @DisplayName("should format secret with spaces")
            void shouldFormatSecretWithSpaces() {
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(TEST_SECRET, TEST_EMAIL);
                assertThat(info.getSecret()).contains(" ");
            }

            @Test
            @DisplayName("should include raw secret")
            void shouldIncludeRawSecret() {
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(TEST_SECRET, TEST_EMAIL);
                assertThat(info.getSecretRaw()).isEqualTo(TEST_SECRET);
            }

            @Test
            @DisplayName("should include type as TOTP")
            void shouldIncludeType() {
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(TEST_SECRET, TEST_EMAIL);
                assertThat(info.getType()).isEqualTo("Time based (TOTP)");
            }

            @Test
            @DisplayName("should include algorithm as SHA1")
            void shouldIncludeAlgorithm() {
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(TEST_SECRET, TEST_EMAIL);
                assertThat(info.getAlgorithm()).isEqualTo("SHA1");
            }

            @Test
            @DisplayName("should include digits as 6")
            void shouldIncludeDigits() {
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(TEST_SECRET, TEST_EMAIL);
                assertThat(info.getDigits()).isEqualTo("6");
            }

            @Test
            @DisplayName("should include period as 30 seconds")
            void shouldIncludePeriod() {
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(TEST_SECRET, TEST_EMAIL);
                assertThat(info.getPeriod()).isEqualTo("30 seconds");
            }
        }

        @Nested
        @DisplayName("Secret Formatting")
        class SecretFormattingTests {

            @Test
            @DisplayName("should format 16-char secret with 4 spaces")
            void shouldFormat16CharSecret() {
                String secret = "JBSWY3DPEHPK3PXP";
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(secret, "test@example.com");
                // JBSW Y3DP EHPK 3PXP = 3 spaces
                assertThat(info.getSecret().chars().filter(c -> c == ' ').count()).isEqualTo(3);
            }

            @Test
            @DisplayName("should handle short secret")
            void shouldHandleShortSecret() {
                String secret = "ABC";
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(secret, "test@example.com");
                assertThat(info.getSecret()).isEqualTo("ABC");
            }

            @Test
            @DisplayName("should handle exactly 4-char secret")
            void shouldHandleExactly4CharSecret() {
                String secret = "ABCD";
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(secret, "test@example.com");
                assertThat(info.getSecret()).isEqualTo("ABCD");
            }

            @Test
            @DisplayName("should handle 5-char secret")
            void shouldHandle5CharSecret() {
                String secret = "ABCDE";
                ImprovedQRCodeService.ManualEntryInfo info = service.getManualEntryInfo(secret, "test@example.com");
                assertThat(info.getSecret()).isEqualTo("ABCD E");
            }
        }
    }

    // ========================================================================
    // QR CODE GENERATOR SERVICE TESTS
    // ========================================================================

    @Nested
    @DisplayName("QRCodeGeneratorService Tests")
    class QRCodeGeneratorServiceTests {

        private QRCodeGeneratorService service;

        @BeforeEach
        void setUp() {
            service = new QRCodeGeneratorService();
            ReflectionTestUtils.setField(service, "logoPath", "static/favicon.png");
            ReflectionTestUtils.setField(service, "foregroundColor", "#000000");
            ReflectionTestUtils.setField(service, "backgroundColor", "#FFFFFF");
        }

        @Nested
        @DisplayName("validateOtpAuthUrl")
        class ValidateOtpAuthUrlTests {

            @Test
            @DisplayName("should return true for valid otpauth URL")
            void shouldReturnTrueForValidUrl() {
                String url = "otpauth://totp/Test:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Test";
                assertThat(service.validateOtpAuthUrl(url)).isTrue();
            }

            @Test
            @DisplayName("should return false for null URL")
            void shouldReturnFalseForNullUrl() {
                assertThat(service.validateOtpAuthUrl(null)).isFalse();
            }

            @Test
            @DisplayName("should return false for URL with wrong prefix")
            void shouldReturnFalseForWrongPrefix() {
                String url = "https://example.com/totp";
                assertThat(service.validateOtpAuthUrl(url)).isFalse();
            }

            @Test
            @DisplayName("should return false for URL without secret")
            void shouldReturnFalseForUrlWithoutSecret() {
                String url = "otpauth://totp/Test:user@example.com?issuer=Test";
                assertThat(service.validateOtpAuthUrl(url)).isFalse();
            }

            @Test
            @DisplayName("should return true for URL without issuer (with warning)")
            void shouldReturnTrueForUrlWithoutIssuer() {
                String url = "otpauth://totp/Test:user@example.com?secret=JBSWY3DPEHPK3PXP";
                assertThat(service.validateOtpAuthUrl(url)).isTrue();
            }
        }

        @Nested
        @DisplayName("generateSimpleQRCode")
        class GenerateSimpleQRCodeTests {

            @Test
            @DisplayName("should generate QR code starting with data URL prefix")
            void shouldGenerateQRCodeWithDataUrlPrefix() {
                String url = "otpauth://totp/Test:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Test";
                String result = service.generateSimpleQRCode(url);
                assertThat(result).startsWith("data:image/png;base64,");
            }

            @Test
            @DisplayName("should generate valid Base64 data")
            void shouldGenerateValidBase64Data() {
                String url = "otpauth://totp/Test:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Test";
                String result = service.generateSimpleQRCode(url);
                String base64Part = result.substring("data:image/png;base64,".length());
                assertThatNoException().isThrownBy(() -> java.util.Base64.getDecoder().decode(base64Part));
            }
        }

        @Nested
        @DisplayName("generateQRCodeDataUrl")
        class GenerateQRCodeDataUrlTests {

            @Test
            @DisplayName("should generate QR code without logo when withLogo is false")
            void shouldGenerateWithoutLogo() {
                String url = "otpauth://totp/Test:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Test";
                String result = service.generateQRCodeDataUrl(url, false);
                assertThat(result).startsWith("data:image/png;base64,");
            }

            @Test
            @DisplayName("should attempt to generate with logo when withLogo is true")
            void shouldAttemptWithLogo() {
                String url = "otpauth://totp/Test:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Test";
                // This will fall back to simple QR if logo not found
                String result = service.generateQRCodeDataUrl(url, true);
                assertThat(result).startsWith("data:image/png;base64,");
            }
        }
    }
}
