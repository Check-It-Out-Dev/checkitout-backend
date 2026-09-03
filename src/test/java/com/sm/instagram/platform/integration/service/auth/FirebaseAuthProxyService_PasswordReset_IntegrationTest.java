package com.sm.instagram.platform.integration.service.auth;

import org.junit.jupiter.api.condition.EnabledIf;
import com.sm.instagram.platform.auth.dto.AuthOperationResponse;
import com.sm.instagram.platform.common.exceptions.NetworkTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FirebaseAuthProxyService Password Reset Integration Tests")
@EnabledIf(value = "com.sm.instagram.platform.integration.ExternalCredentialsAvailable#firebase", disabledReason = "Requires real firebase test credentials (.env / classpath)")
class FirebaseAuthProxyService_PasswordReset_IntegrationTest extends FirebaseAuthProxyServiceIntegrationTestBase {

    @Nested
    @DisplayName("verifyResetCode")
    class VerifyResetCodeTests {

        @Test
        @DisplayName("should return success with email in data")
        void verifyResetCode_success_returnsEmailInData() {
            mockRestTemplateSuccess(Map.of("email", "user@test.com", "requestType", "PASSWORD_RESET"));

            AuthOperationResponse response = firebaseAuthProxyService.verifyResetCode("valid-oob-code");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Reset code is valid");
            assertThat(response.getData()).containsEntry("email", "user@test.com");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for INVALID_OOB_CODE")
        void verifyResetCode_invalidOobCode_throwsValidation() {
            mockRestTemplateError("INVALID_OOB_CODE");

            assertThatThrownBy(() -> firebaseAuthProxyService.verifyResetCode("invalid-code"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.invalid_action_code");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for EXPIRED_OOB_CODE")
        void verifyResetCode_expiredOobCode_throwsValidation() {
            mockRestTemplateError("EXPIRED_OOB_CODE");

            assertThatThrownBy(() -> firebaseAuthProxyService.verifyResetCode("expired-code"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.expired_action_code");
        }

        @Test
        @DisplayName("should throw NetworkTranslatableException for unknown Firebase error")
        void verifyResetCode_unknownError_throwsNetwork() {
            mockRestTemplateError("SOME_UNKNOWN_ERROR");

            assertThatThrownBy(() -> firebaseAuthProxyService.verifyResetCode("some-code"))
                    .isInstanceOf(NetworkTranslatableException.class)
                    .hasMessageContaining("error.network.external_service");
        }
    }

    @Nested
    @DisplayName("confirmPasswordReset")
    class ConfirmPasswordResetTests {

        @Test
        @DisplayName("should return success with email when password reset confirms")
        void confirmPasswordReset_success_returnsEmail() {
            mockRestTemplateSuccess(Map.of("email", "user@test.com"));

            AuthOperationResponse response = firebaseAuthProxyService.confirmPasswordReset("valid-oob-code", "NewPassword1");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Password has been reset successfully");
            assertThat(response.getData()).containsEntry("email", "user@test.com");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for INVALID_OOB_CODE")
        void confirmPasswordReset_invalidOobCode_throwsValidation() {
            mockRestTemplateError("INVALID_OOB_CODE");

            assertThatThrownBy(() -> firebaseAuthProxyService.confirmPasswordReset("invalid-code", "NewPassword1"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.invalid_action_code");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for EXPIRED_OOB_CODE")
        void confirmPasswordReset_expiredOobCode_throwsValidation() {
            mockRestTemplateError("EXPIRED_OOB_CODE");

            assertThatThrownBy(() -> firebaseAuthProxyService.confirmPasswordReset("expired-code", "NewPassword1"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.expired_action_code");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for WEAK_PASSWORD")
        void confirmPasswordReset_weakPassword_throwsValidation() {
            mockRestTemplateError("WEAK_PASSWORD : Password should be at least 6 characters");

            assertThatThrownBy(() -> firebaseAuthProxyService.confirmPasswordReset("valid-code", "123"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.weak_password");
        }

        @Test
        @DisplayName("should throw NetworkTranslatableException for unknown Firebase error")
        void confirmPasswordReset_unknownError_throwsNetwork() {
            mockRestTemplateError("SOME_UNKNOWN_ERROR");

            assertThatThrownBy(() -> firebaseAuthProxyService.confirmPasswordReset("some-code", "NewPassword1"))
                    .isInstanceOf(NetworkTranslatableException.class)
                    .hasMessageContaining("error.network.external_service");
        }
    }
}
