package com.sm.instagram.platform.integration.service.auth;

import com.sm.instagram.platform.auth.dto.AuthOperationResponse;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.test.context.transaction.TestTransaction;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("FirebaseAuthProxyService.applyActionCode Integration Tests")
class FirebaseAuthProxyService_ApplyActionCode_IntegrationTest extends FirebaseAuthProxyServiceIntegrationTestBase {

    @Nested
    @DisplayName("Successful Verification (via Admin SDK + Redis)")
    class SuccessfulVerification {

        @Test
        @DisplayName("should return success when applyVerificationCode succeeds")
        void applyActionCode_validOobCode_returns200() {
            // applyActionCode now delegates to emailVerificationService.applyVerificationCode
            when(emailVerificationService.applyVerificationCode("valid-oob-code"))
                    .thenReturn("user@test.com");

            AuthOperationResponse response = firebaseAuthProxyService.applyActionCode("valid-oob-code");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Email verified successfully");
            assertThat(response.getData()).containsEntry("email", "user@test.com");
        }

        @Test
        @DisplayName("should handle null email in response gracefully")
        void applyActionCode_nullEmail_returnsSuccessWithoutData() {
            when(emailVerificationService.applyVerificationCode("valid-oob-code"))
                    .thenReturn(null);

            AuthOperationResponse response = firebaseAuthProxyService.applyActionCode("valid-oob-code");

            assertThat(response.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid oobCode (not in Redis)")
        void applyActionCode_invalidOobCode_throwsValidation() {
            when(emailVerificationService.applyVerificationCode("invalid-code"))
                    .thenThrow(new ValidationTranslatableException("error.auth.invalid_action_code"));

            assertThatThrownBy(() -> firebaseAuthProxyService.applyActionCode("invalid-code"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.invalid_action_code");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for already-used code")
        void applyActionCode_alreadyUsedCode_throwsValidation() {
            when(emailVerificationService.applyVerificationCode("already-used-code"))
                    .thenThrow(new ValidationTranslatableException("error.auth.invalid_action_code"));

            assertThatThrownBy(() -> firebaseAuthProxyService.applyActionCode("already-used-code"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.invalid_action_code");
        }

        @Test
        @DisplayName("should propagate ExternalServiceException from Admin SDK failure")
        void applyActionCode_adminSdkFailure_throwsExternalService() {
            when(emailVerificationService.applyVerificationCode("some-code"))
                    .thenThrow(new ExternalServiceException("Failed to verify email", "Firebase", "updateUser", null));

            assertThatThrownBy(() -> firebaseAuthProxyService.applyActionCode("some-code"))
                    .isInstanceOf(ExternalServiceException.class);
        }
    }
}
