package com.sm.instagram.platform.integration.controller;

import org.junit.jupiter.api.condition.EnabledIf;
import com.sm.instagram.platform.auth.service.EmailVerificationService;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.integration.config.ServiceIntegrationTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-level integration tests for the magic link endpoints.
 * Tests the full request -> controller -> service -> exception handler -> response chain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Import(ServiceIntegrationTestConfig.class)
@DisplayName("FirebaseAuthProxyController Magic Link HTTP Integration Tests")
@EnabledIf(value = "com.sm.instagram.platform.integration.ExternalCredentialsAvailable#firebase", disabledReason = "Requires real firebase test credentials (.env / classpath)")
class FirebaseAuthProxyController_MagicLink_IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(name = "restTemplate")
    private RestTemplate restTemplate;

    @MockBean
    private PermissionUtils permissionUtils;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @SuppressWarnings("unchecked")
    private void mockFirebaseSuccess(Map<String, Object> responseBody) {
        ResponseEntity<Map> response = ResponseEntity.ok(responseBody);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);
    }

    private void mockFirebaseError(String errorCode) {
        String errorJson = "{\"error\":{\"message\":\"" + errorCode + "\"}}";
        HttpClientErrorException ex = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST, "Bad Request",
                errorJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(ex);
    }

    // =========================================================================
    // apply-action-code
    // =========================================================================

    @Nested
    @DisplayName("POST /auth/firebase/apply-action-code")
    class ApplyActionCodeHttpTests {

        @Test
        @DisplayName("should return 200 with success JSON when oobCode is valid")
        void applyActionCode_validOobCode_returns200() throws Exception {
            when(emailVerificationService.applyVerificationCode("valid-code"))
                    .thenReturn("a@b.com");

            mockMvc.perform(post("/auth/firebase/apply-action-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"valid-code\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Email verified successfully"));
        }

        @Test
        @DisplayName("should return 400 validation error when oobCode is blank")
        void applyActionCode_blankOobCode_returns400() throws Exception {
            mockMvc.perform(post("/auth/firebase/apply-action-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors.oobCode").exists());
        }

        @Test
        @DisplayName("should return 400 when oobCode is null")
        void applyActionCode_nullOobCode_returns400() throws Exception {
            mockMvc.perform(post("/auth/firebase/apply-action-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":null}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 with messageKey for invalid oobCode (not in Redis)")
        void applyActionCode_invalidOobCode_returns400WithMessageKey() throws Exception {
            when(emailVerificationService.applyVerificationCode("invalid-code"))
                    .thenThrow(new ValidationTranslatableException("error.auth.invalid_action_code"));

            mockMvc.perform(post("/auth/firebase/apply-action-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"invalid-code\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messageKey").value("error.auth.invalid_action_code"));
        }

        @Test
        @DisplayName("should return 400 with messageKey for already-used oobCode")
        void applyActionCode_expiredOobCode_returns400WithMessageKey() throws Exception {
            when(emailVerificationService.applyVerificationCode("expired-code"))
                    .thenThrow(new ValidationTranslatableException("error.auth.invalid_action_code"));

            mockMvc.perform(post("/auth/firebase/apply-action-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"expired-code\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messageKey").value("error.auth.invalid_action_code"));
        }
    }

    // =========================================================================
    // verify-reset-code
    // =========================================================================

    @Nested
    @DisplayName("POST /auth/firebase/verify-reset-code")
    class VerifyResetCodeHttpTests {

        @Test
        @DisplayName("should return 200 with email in data")
        void verifyResetCode_validOobCode_returns200WithEmail() throws Exception {
            mockFirebaseSuccess(Map.of("email", "a@b.com", "requestType", "PASSWORD_RESET"));

            mockMvc.perform(post("/auth/firebase/verify-reset-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"valid-code\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Reset code is valid"));
        }

        @Test
        @DisplayName("should return 400 when oobCode is blank")
        void verifyResetCode_blankOobCode_returns400() throws Exception {
            mockMvc.perform(post("/auth/firebase/verify-reset-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors.oobCode").exists());
        }

        @Test
        @DisplayName("should return 400 with messageKey for INVALID_OOB_CODE")
        void verifyResetCode_invalidOobCode_returns400WithMessageKey() throws Exception {
            mockFirebaseError("INVALID_OOB_CODE");

            mockMvc.perform(post("/auth/firebase/verify-reset-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"invalid-code\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messageKey").value("error.auth.invalid_action_code"));
        }

        @Test
        @DisplayName("should return 400 with messageKey for EXPIRED_OOB_CODE")
        void verifyResetCode_expiredOobCode_returns400WithMessageKey() throws Exception {
            mockFirebaseError("EXPIRED_OOB_CODE");

            mockMvc.perform(post("/auth/firebase/verify-reset-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"expired-code\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messageKey").value("error.auth.expired_action_code"));
        }

        @Test
        @DisplayName("should return 503 for unknown Firebase error")
        void verifyResetCode_firebaseDown_returns503() throws Exception {
            mockFirebaseError("SOME_UNKNOWN_ERROR");

            mockMvc.perform(post("/auth/firebase/verify-reset-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"some-code\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.messageKey").value("error.network.external_service"));
        }
    }

    // =========================================================================
    // confirm-password-reset
    // =========================================================================

    @Nested
    @DisplayName("POST /auth/firebase/confirm-password-reset")
    class ConfirmPasswordResetHttpTests {

        @Test
        @DisplayName("should return 200 when valid oobCode and password")
        void confirmPasswordReset_valid_returns200() throws Exception {
            mockFirebaseSuccess(Map.of("email", "a@b.com"));

            mockMvc.perform(post("/auth/firebase/confirm-password-reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"valid-code\",\"newPassword\":\"NewPassword1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Password has been reset successfully"));
        }

        @Test
        @DisplayName("should return 400 when oobCode is blank")
        void confirmPasswordReset_blankOobCode_returns400() throws Exception {
            mockMvc.perform(post("/auth/firebase/confirm-password-reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"\",\"newPassword\":\"NewPassword1\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors.oobCode").exists());
        }

        @Test
        @DisplayName("should return 400 when password is too short")
        void confirmPasswordReset_shortPassword_returns400() throws Exception {
            mockMvc.perform(post("/auth/firebase/confirm-password-reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"valid-code\",\"newPassword\":\"Abc1\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors.newPassword").exists());
        }

        @Test
        @DisplayName("should return 400 when password has no letter (only digits)")
        void confirmPasswordReset_noLetterPassword_returns400() throws Exception {
            mockMvc.perform(post("/auth/firebase/confirm-password-reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"valid-code\",\"newPassword\":\"12345678\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors.newPassword").exists());
        }

        @Test
        @DisplayName("should return 400 with messageKey for Firebase WEAK_PASSWORD")
        void confirmPasswordReset_weakPassword_returns400WithMessageKey() throws Exception {
            mockFirebaseError("WEAK_PASSWORD : Password should be at least 6 characters");

            mockMvc.perform(post("/auth/firebase/confirm-password-reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oobCode\":\"valid-code\",\"newPassword\":\"NewPassword1\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messageKey").value("error.auth.weak_password"));
        }
    }
}
