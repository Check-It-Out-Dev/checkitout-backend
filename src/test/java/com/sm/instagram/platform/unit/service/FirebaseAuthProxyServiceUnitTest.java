package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.auth.FirebaseAuth;
import com.sm.instagram.platform.auth.dto.AuthOperationResponse;
import com.sm.instagram.platform.auth.service.EmailVerificationService;
import com.sm.instagram.platform.auth.service.FirebaseAuthProxyService;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.NetworkTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FirebaseAuthProxyService Unit Tests")
class FirebaseAuthProxyServiceUnitTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GoogleCredentialsProvider credentialsProvider;

    @Mock
    private EmailVerificationService emailVerificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FirebaseAuthProxyService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new FirebaseAuthProxyService(restTemplate, objectMapper, firebaseAuth);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "credentialsProvider", credentialsProvider);
        ReflectionTestUtils.setField(service, "emailVerificationService", emailVerificationService);
        // A real FirebaseEmulator with no host configured, which is what production is: it hands back the
        // Google endpoints and an empty token, so the service mints a real one through the credentials
        // provider mocked below. The emulator path has its own coverage in the e2e tier.
        ReflectionTestUtils.setField(service, "firebaseEmulator",
                new com.sm.instagram.platform.common.firebase.FirebaseEmulator(""));

        // Mock credentials provider to return a fake access token
        GoogleCredentials mockCredentials = mock(GoogleCredentials.class);
        AccessToken mockToken = new AccessToken("fake-token", null);
        when(mockCredentials.getAccessToken()).thenReturn(mockToken);
        when(credentialsProvider.getCredentialsWithScopes(anyString())).thenReturn(mockCredentials);
    }

    @SuppressWarnings("unchecked")
    private void mockRestTemplateSuccess(Map<String, Object> responseBody) {
        ResponseEntity<Map> responseEntity = ResponseEntity.ok(responseBody);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);
    }

    private void mockRestTemplateError(String errorMessage) {
        String errorJson = "{\"error\":{\"message\":\"" + errorMessage + "\"}}";
        HttpClientErrorException ex = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST, "Bad Request",
                errorJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(ex);
    }

    @Nested
    @DisplayName("applyActionCode Tests")
    class ApplyActionCodeTests {

        @Test
        @DisplayName("should return success with email when applyVerificationCode succeeds")
        void applyActionCode_success_returnsSuccessWithEmail() {
            when(emailVerificationService.applyVerificationCode("valid-oob-code"))
                    .thenReturn("a@b.com");

            AuthOperationResponse response = service.applyActionCode("valid-oob-code");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Email verified successfully");
            assertThat(response.getData()).containsEntry("email", "a@b.com");
        }

        @Test
        @DisplayName("should handle null email gracefully")
        void applyActionCode_success_nullEmail_returnsSuccess() {
            when(emailVerificationService.applyVerificationCode("valid-oob-code"))
                    .thenReturn(null);

            AuthOperationResponse response = service.applyActionCode("valid-oob-code");

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid oobCode")
        void applyActionCode_invalidOobCode_throwsValidation() {
            when(emailVerificationService.applyVerificationCode("invalid-code"))
                    .thenThrow(new ValidationTranslatableException("error.auth.invalid_action_code"));

            assertThatThrownBy(() -> service.applyActionCode("invalid-code"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.invalid_action_code");
        }

        @Test
        @DisplayName("should propagate ExternalServiceException from Admin SDK failure")
        void applyActionCode_adminSdkFailure_throwsExternalService() {
            when(emailVerificationService.applyVerificationCode("some-code"))
                    .thenThrow(new ExternalServiceException("Failed to verify email", "Firebase", "updateUser", null));

            assertThatThrownBy(() -> service.applyActionCode("some-code"))
                    .isInstanceOf(ExternalServiceException.class);
        }
    }

    @Nested
    @DisplayName("verifyResetCode Tests")
    class VerifyResetCodeTests {

        @Test
        @DisplayName("should return success with email and requestType")
        void verifyResetCode_success_returnsEmailAndRequestType() {
            mockRestTemplateSuccess(Map.of("email", "a@b.com", "requestType", "PASSWORD_RESET"));

            AuthOperationResponse response = service.verifyResetCode("valid-oob-code");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Reset code is valid");
            assertThat(response.getData()).containsEntry("email", "a@b.com");
        }

        @Test
        @DisplayName("should return success without email when not present in response")
        void verifyResetCode_success_noEmail_returnsSuccessWithoutEmail() {
            mockRestTemplateSuccess(Map.of("requestType", "PASSWORD_RESET"));

            AuthOperationResponse response = service.verifyResetCode("valid-oob-code");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNull();
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for INVALID_OOB_CODE")
        void verifyResetCode_invalidOobCode_throwsValidation() {
            mockRestTemplateError("INVALID_OOB_CODE");

            assertThatThrownBy(() -> service.verifyResetCode("invalid-code"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.invalid_action_code");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for EXPIRED_OOB_CODE")
        void verifyResetCode_expiredOobCode_throwsValidation() {
            mockRestTemplateError("EXPIRED_OOB_CODE");

            assertThatThrownBy(() -> service.verifyResetCode("expired-code"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.expired_action_code");
        }

        @Test
        @DisplayName("should throw NetworkTranslatableException for unknown error")
        void verifyResetCode_unknownError_throwsNetwork() {
            mockRestTemplateError("SOME_UNKNOWN_ERROR");

            assertThatThrownBy(() -> service.verifyResetCode("some-code"))
                    .isInstanceOf(NetworkTranslatableException.class)
                    .hasMessageContaining("error.network.external_service");
        }
    }

    @Nested
    @DisplayName("confirmPasswordReset Tests")
    class ConfirmPasswordResetTests {

        @Test
        @DisplayName("should return success with email when password reset confirms")
        void confirmPasswordReset_success_returnsSuccessWithEmail() {
            mockRestTemplateSuccess(Map.of("email", "a@b.com"));

            AuthOperationResponse response = service.confirmPasswordReset("valid-oob-code", "NewPassword1");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Password has been reset successfully");
            assertThat(response.getData()).containsEntry("email", "a@b.com");
        }

        @Test
        @DisplayName("should return success without email when not present in response")
        void confirmPasswordReset_success_noEmail_returnsSuccess() {
            mockRestTemplateSuccess(Map.of("requestType", "PASSWORD_RESET"));

            AuthOperationResponse response = service.confirmPasswordReset("valid-oob-code", "NewPassword1");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNull();
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for INVALID_OOB_CODE")
        void confirmPasswordReset_invalidOobCode_throwsValidation() {
            mockRestTemplateError("INVALID_OOB_CODE");

            assertThatThrownBy(() -> service.confirmPasswordReset("invalid-code", "NewPassword1"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.invalid_action_code");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for EXPIRED_OOB_CODE")
        void confirmPasswordReset_expiredOobCode_throwsValidation() {
            mockRestTemplateError("EXPIRED_OOB_CODE");

            assertThatThrownBy(() -> service.confirmPasswordReset("expired-code", "NewPassword1"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.expired_action_code");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for WEAK_PASSWORD")
        void confirmPasswordReset_weakPassword_throwsValidation() {
            mockRestTemplateError("WEAK_PASSWORD : Password should be at least 6 characters");

            assertThatThrownBy(() -> service.confirmPasswordReset("valid-code", "123"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.weak_password");
        }

        @Test
        @DisplayName("should throw NetworkTranslatableException for unknown error")
        void confirmPasswordReset_unknownError_throwsNetwork() {
            mockRestTemplateError("SOME_UNKNOWN_ERROR");

            assertThatThrownBy(() -> service.confirmPasswordReset("some-code", "NewPassword1"))
                    .isInstanceOf(NetworkTranslatableException.class)
                    .hasMessageContaining("error.network.external_service");
        }
    }
}
