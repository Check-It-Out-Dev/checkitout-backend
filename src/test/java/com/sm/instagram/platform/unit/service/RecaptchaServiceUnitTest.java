package com.sm.instagram.platform.unit.service;

import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceClient;
import com.google.recaptchaenterprise.v1.*;
import com.sm.instagram.platform.auth.dto.AssessmentResult;
import com.sm.instagram.platform.auth.service.RecaptchaService;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import com.sm.instagram.platform.common.exceptions.RecaptchaValidationException;
import com.sm.instagram.platform.config.RecaptchaConfig;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecaptchaService Unit Tests")
class RecaptchaServiceUnitTest {

    @Mock
    private RecaptchaConfig recaptchaConfig;

    @Mock
    private GoogleCredentialsProvider credentialsProvider;

    @Mock
    private RecaptchaEnterpriseServiceClient recaptchaClient;

    @Mock
    private HttpServletRequest httpRequest;

    private RecaptchaService recaptchaService;

    @BeforeEach
    void setUp() {
        recaptchaService = new RecaptchaService();
        ReflectionTestUtils.setField(recaptchaService, "recaptchaConfig", recaptchaConfig);
        ReflectionTestUtils.setField(recaptchaService, "credentialsProvider", credentialsProvider);
        ReflectionTestUtils.setField(recaptchaService, "recaptchaClient", recaptchaClient);
    }

    @Nested
    @DisplayName("verifyToken - reCAPTCHA Disabled")
    class VerifyTokenDisabledTests {

        @Test
        @DisplayName("should return allowed result when reCAPTCHA is disabled")
        void shouldReturnAllowedWhenDisabled() {
            // Given
            when(recaptchaConfig.isEnabled()).thenReturn(false);

            // When
            AssessmentResult result = recaptchaService.verifyToken("any-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getReason()).isEqualTo("reCAPTCHA disabled");
            assertThat(result.getScore()).isEqualTo(1.0);
            verify(recaptchaClient, never()).createAssessment(any(ProjectName.class), any(Assessment.class));
        }

        @Test
        @DisplayName("should return allowed even without token when disabled")
        void shouldReturnAllowedWithoutTokenWhenDisabled() {
            // Given
            when(recaptchaConfig.isEnabled()).thenReturn(false);

            // When
            AssessmentResult result = recaptchaService.verifyToken(null, "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getStatus()).isEqualTo("ALLOWED");
        }

        @Test
        @DisplayName("should return allowed with empty token when disabled")
        void shouldReturnAllowedWithEmptyTokenWhenDisabled() {
            // Given
            when(recaptchaConfig.isEnabled()).thenReturn(false);

            // When
            AssessmentResult result = recaptchaService.verifyToken("", "SIGNUP", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("verifyToken - Token Validation")
    class VerifyTokenValidationTests {

        @Test
        @DisplayName("should throw exception when token is null and reCAPTCHA is enabled")
        void shouldThrowExceptionWhenTokenIsNull() {
            // Given
            when(recaptchaConfig.isEnabled()).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> recaptchaService.verifyToken(null, "LOGIN", httpRequest))
                .isInstanceOf(RecaptchaValidationException.class)
                .hasMessageContaining("error.auth.recaptcha_token_required");
        }

        @Test
        @DisplayName("should throw exception when token is empty and reCAPTCHA is enabled")
        void shouldThrowExceptionWhenTokenIsEmpty() {
            // Given
            when(recaptchaConfig.isEnabled()).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> recaptchaService.verifyToken("", "LOGIN", httpRequest))
                .isInstanceOf(RecaptchaValidationException.class)
                .hasMessageContaining("error.auth.recaptcha_token_required");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should throw for null and empty tokens when enabled")
        void shouldThrowForNullAndEmptyTokens(String token) {
            // Given
            when(recaptchaConfig.isEnabled()).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> recaptchaService.verifyToken(token, "LOGIN", httpRequest))
                .isInstanceOf(RecaptchaValidationException.class);
        }
    }

    @Nested
    @DisplayName("verifyToken - Client Not Initialized")
    class VerifyTokenClientNotInitializedTests {

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(recaptchaService, "recaptchaClient", null);
        }

        @Test
        @DisplayName("should throw exception when client is null and reCAPTCHA is enabled")
        void shouldThrowExceptionWhenClientIsNullAndEnabled() {
            // Given
            when(recaptchaConfig.isEnabled()).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest))
                .isInstanceOf(RecaptchaValidationException.class)
                .hasMessageContaining("error.auth.recaptcha_unavailable");
        }

        @Test
        @DisplayName("should return allowed when client is null and reCAPTCHA is disabled on second check")
        void shouldReturnAllowedWhenClientNullAndDisabledOnSecondCheck() {
            // Given - First check returns true (enabled), second check returns false (fail open)
            when(recaptchaConfig.isEnabled()).thenReturn(true).thenReturn(false);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getReason()).isEqualTo("reCAPTCHA client not initialized");
        }
    }

    @Nested
    @DisplayName("verifyToken - Successful Verification")
    class VerifyTokenSuccessTests {

        @BeforeEach
        void setUpSuccessScenario() {
            when(recaptchaConfig.isEnabled()).thenReturn(true);
            when(recaptchaConfig.getSiteKey()).thenReturn("test-site-key");
            when(recaptchaConfig.getProjectId()).thenReturn("test-project-id");

            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
            when(httpRequest.getHeader("User-Agent")).thenReturn("Test-Agent/1.0");
        }

        @Test
        @DisplayName("should return success for valid token with high score")
        void shouldReturnSuccessForValidTokenWithHighScore() {
            // Given
            when(recaptchaConfig.getThresholdForAction("LOGIN")).thenReturn(0.5);

            Assessment response = createMockAssessment(true, "LOGIN", 0.9f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getScore()).isCloseTo(0.9, within(0.001));
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            verify(recaptchaClient).createAssessment(any(ProjectName.class), any(Assessment.class));
        }

        @Test
        @DisplayName("should return success when score equals threshold")
        void shouldReturnSuccessWhenScoreEqualsThreshold() {
            // Given
            when(recaptchaConfig.getThresholdForAction("SIGNUP")).thenReturn(0.5);

            Assessment response = createMockAssessment(true, "SIGNUP", 0.5f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "SIGNUP", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getScore()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("should return success for perfect score")
        void shouldReturnSuccessForPerfectScore() {
            // Given
            when(recaptchaConfig.getThresholdForAction("LOGIN")).thenReturn(0.5);

            Assessment response = createMockAssessment(true, "LOGIN", 1.0f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getScore()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("verifyToken - Score Threshold Tests")
    class VerifyTokenScoreThresholdTests {

        @BeforeEach
        void setUpScoreScenario() {
            when(recaptchaConfig.isEnabled()).thenReturn(true);
            when(recaptchaConfig.getSiteKey()).thenReturn("test-site-key");
            when(recaptchaConfig.getProjectId()).thenReturn("test-project-id");

            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
            when(httpRequest.getHeader("User-Agent")).thenReturn("Test-Agent/1.0");
        }

        @Test
        @DisplayName("should return blocked when score is below threshold")
        void shouldReturnBlockedWhenScoreBelowThreshold() {
            // Given
            when(recaptchaConfig.getThresholdForAction("LOGIN")).thenReturn(0.5);

            Assessment response = createMockAssessment(true, "LOGIN", 0.3f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getScore()).isCloseTo(0.3, within(0.001));
            assertThat(result.getStatus()).isEqualTo("BLOCKED");
            assertThat(result.getReason()).isEqualTo("Score below threshold");
        }

        @Test
        @DisplayName("should return blocked when score is zero")
        void shouldReturnBlockedWhenScoreIsZero() {
            // Given
            when(recaptchaConfig.getThresholdForAction("LOGIN")).thenReturn(0.5);

            Assessment response = createMockAssessment(true, "LOGIN", 0.0f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getScore()).isEqualTo(0.0);
        }

        @ParameterizedTest
        @CsvSource({
            "0.4, 0.5, false",
            "0.5, 0.5, true",
            "0.6, 0.5, true",
            "0.1, 0.3, false",
            "0.3, 0.3, true",
            "0.9, 0.3, true"
        })
        @DisplayName("should correctly compare score against threshold")
        void shouldCorrectlyCompareScoreAgainstThreshold(float score, double threshold, boolean expectedAllowed) {
            // Given
            when(recaptchaConfig.getThresholdForAction("LOGIN")).thenReturn(threshold);

            Assessment response = createMockAssessment(true, "LOGIN", score);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isEqualTo(expectedAllowed);
        }
    }

    @Nested
    @DisplayName("verifyToken - Invalid Token Response")
    class VerifyTokenInvalidTokenTests {

        @BeforeEach
        void setUpInvalidScenario() {
            when(recaptchaConfig.isEnabled()).thenReturn(true);
            when(recaptchaConfig.getSiteKey()).thenReturn("test-site-key");
            when(recaptchaConfig.getProjectId()).thenReturn("test-project-id");

            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
            when(httpRequest.getHeader("User-Agent")).thenReturn("Test-Agent/1.0");
        }

        @Test
        @DisplayName("should return invalid when token is invalid")
        void shouldReturnInvalidWhenTokenIsInvalid() {
            // Given
            Assessment response = createInvalidTokenAssessment(TokenProperties.InvalidReason.EXPIRED);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("expired-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getStatus()).isEqualTo("INVALID");
            assertThat(result.getReason()).isEqualTo("EXPIRED");
        }

        @ParameterizedTest
        @ValueSource(strings = {"EXPIRED", "DUPE", "MISSING", "BROWSER_ERROR"})
        @DisplayName("should return invalid for various invalid reasons")
        void shouldReturnInvalidForVariousReasons(String reasonName) {
            // Given
            TokenProperties.InvalidReason reason = TokenProperties.InvalidReason.valueOf(reasonName);
            Assessment response = createInvalidTokenAssessment(reason);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("invalid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getStatus()).isEqualTo("INVALID");
            assertThat(result.getReason()).isEqualTo(reasonName);
        }
    }

    @Nested
    @DisplayName("verifyToken - Action Mismatch")
    class VerifyTokenActionMismatchTests {

        @BeforeEach
        void setUpActionScenario() {
            when(recaptchaConfig.isEnabled()).thenReturn(true);
            when(recaptchaConfig.getSiteKey()).thenReturn("test-site-key");
            when(recaptchaConfig.getProjectId()).thenReturn("test-project-id");

            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
            when(httpRequest.getHeader("User-Agent")).thenReturn("Test-Agent/1.0");
        }

        @Test
        @DisplayName("should return invalid when action does not match")
        void shouldReturnInvalidWhenActionDoesNotMatch() {
            // Given - Token action is SIGNUP but expected LOGIN
            Assessment response = createMockAssessment(true, "SIGNUP", 0.9f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getStatus()).isEqualTo("INVALID");
            assertThat(result.getReason()).isEqualTo("Action mismatch");
        }

        @Test
        @DisplayName("should return success when action matches exactly")
        void shouldReturnSuccessWhenActionMatchesExactly() {
            // Given
            when(recaptchaConfig.getThresholdForAction("SIGNUP")).thenReturn(0.3);

            Assessment response = createMockAssessment(true, "SIGNUP", 0.9f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "SIGNUP", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("verifyToken - Exception Handling")
    class VerifyTokenExceptionHandlingTests {

        @BeforeEach
        void setUpExceptionScenario() {
            when(recaptchaConfig.isEnabled()).thenReturn(true);
            when(recaptchaConfig.getSiteKey()).thenReturn("test-site-key");
            when(recaptchaConfig.getProjectId()).thenReturn("test-project-id");

            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
            when(httpRequest.getHeader("User-Agent")).thenReturn("Test-Agent/1.0");
        }

        @Test
        @DisplayName("should throw exception when API call fails and reCAPTCHA is enabled")
        void shouldThrowExceptionWhenApiCallFails() {
            // Given
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenThrow(new RuntimeException("API error"));

            // When/Then
            assertThatThrownBy(() -> recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest))
                .isInstanceOf(RecaptchaValidationException.class)
                .hasMessageContaining("error.auth.recaptcha_failed");
        }

        @Test
        @DisplayName("should return allowed when API fails and reCAPTCHA is disabled on error check")
        void shouldReturnAllowedWhenApiFailsAndDisabledOnErrorCheck() {
            // Given - First call returns true (enabled), error occurs, second check returns false (fail open)
            when(recaptchaConfig.isEnabled()).thenReturn(true).thenReturn(false);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenThrow(new RuntimeException("API error"));

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getReason()).isEqualTo("reCAPTCHA error, failing open");
        }

        @Test
        @DisplayName("should handle timeout exception")
        void shouldHandleTimeoutException() {
            // Given
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

            // When/Then
            assertThatThrownBy(() -> recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest))
                .isInstanceOf(RecaptchaValidationException.class);
        }
    }

    @Nested
    @DisplayName("verifyToken - IP Address Extraction")
    class VerifyTokenIpExtractionTests {

        @BeforeEach
        void setUpIpScenario() {
            when(recaptchaConfig.isEnabled()).thenReturn(true);
            when(recaptchaConfig.getSiteKey()).thenReturn("test-site-key");
            when(recaptchaConfig.getProjectId()).thenReturn("test-project-id");
            when(recaptchaConfig.getThresholdForAction("LOGIN")).thenReturn(0.5);
            when(httpRequest.getHeader("User-Agent")).thenReturn("Test-Agent/1.0");
        }

        @Test
        @DisplayName("should use X-Forwarded-For when available")
        void shouldUseXForwardedForWhenAvailable() {
            // Given
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");

            Assessment response = createMockAssessment(true, "LOGIN", 0.9f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
            // The service extracts first IP from X-Forwarded-For
            verify(httpRequest).getHeader("X-Forwarded-For");
        }

        @Test
        @DisplayName("should use X-Real-IP when X-Forwarded-For is not available")
        void shouldUseXRealIpWhenXForwardedForNotAvailable() {
            // Given
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn("10.0.0.5");

            Assessment response = createMockAssessment(true, "LOGIN", 0.9f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
            verify(httpRequest).getHeader("X-Real-IP");
        }

        @Test
        @DisplayName("should use remote address as fallback")
        void shouldUseRemoteAddressAsFallback() {
            // Given
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.100");

            Assessment response = createMockAssessment(true, "LOGIN", 0.9f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
            verify(httpRequest).getRemoteAddr();
        }

        @Test
        @DisplayName("should handle empty X-Forwarded-For")
        void shouldHandleEmptyXForwardedFor() {
            // Given
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("");
            when(httpRequest.getHeader("X-Real-IP")).thenReturn("10.0.0.5");

            Assessment response = createMockAssessment(true, "LOGIN", 0.9f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("verifyToken - Project ID Resolution")
    class VerifyTokenProjectIdTests {

        @BeforeEach
        void setUpProjectIdScenario() {
            when(recaptchaConfig.isEnabled()).thenReturn(true);
            when(recaptchaConfig.getSiteKey()).thenReturn("test-site-key");
            when(recaptchaConfig.getThresholdForAction("LOGIN")).thenReturn(0.5);

            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
            when(httpRequest.getHeader("User-Agent")).thenReturn("Test-Agent/1.0");
        }

        @Test
        @DisplayName("should use configured project ID when available")
        void shouldUseConfiguredProjectIdWhenAvailable() {
            // Given
            when(recaptchaConfig.getProjectId()).thenReturn("configured-project-id");

            Assessment response = createMockAssessment(true, "LOGIN", 0.9f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
            verify(recaptchaConfig, atLeastOnce()).getProjectId();
        }

        @Test
        @DisplayName("should fallback to credentials provider project ID when config is null")
        void shouldFallbackToCredentialsProviderProjectId() {
            // Given
            when(recaptchaConfig.getProjectId()).thenReturn(null);
            when(credentialsProvider.getProjectId()).thenReturn("credentials-project-id");

            Assessment response = createMockAssessment(true, "LOGIN", 0.9f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            AssessmentResult result = recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result.isAllowed()).isTrue();
            verify(credentialsProvider).getProjectId();
        }
    }

    @Nested
    @DisplayName("isValidToken")
    class IsValidTokenTests {

        @Test
        @DisplayName("should return true when verification succeeds")
        void shouldReturnTrueWhenVerificationSucceeds() {
            // Given
            when(recaptchaConfig.isEnabled()).thenReturn(true);
            when(recaptchaConfig.getSiteKey()).thenReturn("test-site-key");
            when(recaptchaConfig.getProjectId()).thenReturn("test-project-id");
            when(recaptchaConfig.getThresholdForAction("LOGIN")).thenReturn(0.5);

            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
            when(httpRequest.getHeader("User-Agent")).thenReturn("Test-Agent/1.0");

            Assessment response = createMockAssessment(true, "LOGIN", 0.9f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            boolean result = recaptchaService.isValidToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when verification fails")
        void shouldReturnFalseWhenVerificationFails() {
            // Given
            when(recaptchaConfig.isEnabled()).thenReturn(true);
            when(recaptchaConfig.getSiteKey()).thenReturn("test-site-key");
            when(recaptchaConfig.getProjectId()).thenReturn("test-project-id");
            when(recaptchaConfig.getThresholdForAction("LOGIN")).thenReturn(0.5);

            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
            when(httpRequest.getHeader("User-Agent")).thenReturn("Test-Agent/1.0");

            Assessment response = createMockAssessment(true, "LOGIN", 0.1f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            boolean result = recaptchaService.isValidToken("valid-token", "LOGIN", httpRequest);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when exception occurs and reCAPTCHA is enabled")
        void shouldReturnFalseWhenExceptionOccursAndEnabled() {
            // Given
            when(recaptchaConfig.isEnabled()).thenReturn(true);

            // When
            boolean result = recaptchaService.isValidToken(null, "LOGIN", httpRequest);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when exception occurs and reCAPTCHA is disabled")
        void shouldReturnTrueWhenExceptionOccursAndDisabled() {
            // Given
            when(recaptchaConfig.isEnabled()).thenReturn(false);

            // When
            boolean result = recaptchaService.isValidToken("any-token", "LOGIN", httpRequest);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("init")
    class InitTests {

        @BeforeEach
        void resetClient() {
            ReflectionTestUtils.setField(recaptchaService, "recaptchaClient", null);
        }

        @Test
        @DisplayName("should skip initialization when reCAPTCHA is disabled")
        void shouldSkipInitializationWhenDisabled() {
            // Given
            when(recaptchaConfig.isEnabled()).thenReturn(false);

            // When
            recaptchaService.init();

            // Then
            Object client = ReflectionTestUtils.getField(recaptchaService, "recaptchaClient");
            assertThat(client).isNull();
            verify(credentialsProvider, never()).getCredentialsWithScopes(any());
        }
    }

    @Nested
    @DisplayName("cleanup")
    class CleanupTests {

        @Test
        @DisplayName("should close client when present")
        void shouldCloseClientWhenPresent() {
            // When
            recaptchaService.cleanup();

            // Then
            verify(recaptchaClient).close();
        }

        @Test
        @DisplayName("should not throw when client is null")
        void shouldNotThrowWhenClientIsNull() {
            // Given
            ReflectionTestUtils.setField(recaptchaService, "recaptchaClient", null);

            // When/Then - should not throw
            assertThatCode(() -> recaptchaService.cleanup()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle exception during close")
        void shouldHandleExceptionDuringClose() {
            // Given
            doThrow(new RuntimeException("Close error")).when(recaptchaClient).close();

            // When/Then - should not throw
            assertThatCode(() -> recaptchaService.cleanup()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Action-specific Threshold Tests")
    class ActionSpecificThresholdTests {

        @BeforeEach
        void setUpThresholdScenario() {
            when(recaptchaConfig.isEnabled()).thenReturn(true);
            when(recaptchaConfig.getSiteKey()).thenReturn("test-site-key");
            when(recaptchaConfig.getProjectId()).thenReturn("test-project-id");

            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
            when(httpRequest.getHeader("User-Agent")).thenReturn("Test-Agent/1.0");
        }

        @Test
        @DisplayName("should use LOGIN threshold for LOGIN action")
        void shouldUseLoginThresholdForLoginAction() {
            // Given
            when(recaptchaConfig.getThresholdForAction("LOGIN")).thenReturn(0.5);

            Assessment response = createMockAssessment(true, "LOGIN", 0.6f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            recaptchaService.verifyToken("valid-token", "LOGIN", httpRequest);

            // Then
            verify(recaptchaConfig).getThresholdForAction("LOGIN");
        }

        @Test
        @DisplayName("should use SIGNUP threshold for SIGNUP action")
        void shouldUseSignupThresholdForSignupAction() {
            // Given
            when(recaptchaConfig.getThresholdForAction("SIGNUP")).thenReturn(0.3);

            Assessment response = createMockAssessment(true, "SIGNUP", 0.4f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            recaptchaService.verifyToken("valid-token", "SIGNUP", httpRequest);

            // Then
            verify(recaptchaConfig).getThresholdForAction("SIGNUP");
        }

        @Test
        @DisplayName("should use FORGOT_PASSWORD threshold for forgot password action")
        void shouldUseForgotPasswordThresholdForForgotPasswordAction() {
            // Given
            when(recaptchaConfig.getThresholdForAction("FORGOT_PASSWORD")).thenReturn(0.3);

            Assessment response = createMockAssessment(true, "FORGOT_PASSWORD", 0.4f);
            when(recaptchaClient.createAssessment(any(ProjectName.class), any(Assessment.class)))
                .thenReturn(response);

            // When
            recaptchaService.verifyToken("valid-token", "FORGOT_PASSWORD", httpRequest);

            // Then
            verify(recaptchaConfig).getThresholdForAction("FORGOT_PASSWORD");
        }
    }

    // Helper methods to create mock Assessment objects
    private Assessment createMockAssessment(boolean isValid, String action, float score) {
        TokenProperties tokenProperties = TokenProperties.newBuilder()
            .setValid(isValid)
            .setAction(action)
            .build();

        RiskAnalysis riskAnalysis = RiskAnalysis.newBuilder()
            .setScore(score)
            .build();

        return Assessment.newBuilder()
            .setName("projects/test/assessments/abc123")
            .setTokenProperties(tokenProperties)
            .setRiskAnalysis(riskAnalysis)
            .build();
    }

    private Assessment createInvalidTokenAssessment(TokenProperties.InvalidReason reason) {
        TokenProperties tokenProperties = TokenProperties.newBuilder()
            .setValid(false)
            .setInvalidReason(reason)
            .build();

        return Assessment.newBuilder()
            .setName("projects/test/assessments/abc123")
            .setTokenProperties(tokenProperties)
            .build();
    }
}
