package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.dto.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Additional comprehensive unit tests for Auth DTOs.
 * Focuses on: validation annotations, builder patterns, equals/hashCode, serialization scenarios.
 * Covers DTOs not fully tested in AuthDtoUnitTest and AuthDtoExtendedUnitTest.
 */
@DisplayName("Auth DTO More Unit Tests")
class AuthDtoMoreUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== FirebaseAuthRequest Additional Tests ====================

    @Nested
    @DisplayName("FirebaseAuthRequest Additional Tests")
    class FirebaseAuthRequestAdditionalTests {

        @Test
        @DisplayName("should create via no-args constructor and setters")
        void shouldCreateViaNoArgsConstructorAndSetters() {
            FirebaseAuthRequest request = new FirebaseAuthRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password1!");
            request.setReturnSecureToken(false);
            request.setRefreshToken("refresh-token");
            request.setToken("custom-token");

            assertThat(request.getEmail()).isEqualTo("test@example.com");
            assertThat(request.getPassword()).isEqualTo("Password1!");
            assertThat(request.isReturnSecureToken()).isFalse();
            assertThat(request.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(request.getToken()).isEqualTo("custom-token");
        }

        @Test
        @DisplayName("should create via all-args constructor")
        void shouldCreateViaAllArgsConstructor() {
            FirebaseAuthRequest request = new FirebaseAuthRequest(
                    "email@test.com", "pass123", true, "refresh", "token"
            );

            assertThat(request.getEmail()).isEqualTo("email@test.com");
            assertThat(request.getPassword()).isEqualTo("pass123");
            assertThat(request.isReturnSecureToken()).isTrue();
            assertThat(request.getRefreshToken()).isEqualTo("refresh");
            assertThat(request.getToken()).isEqualTo("token");
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            FirebaseAuthRequest request1 = FirebaseAuthRequest.builder()
                    .email("test@example.com")
                    .password("Password1!")
                    .build();

            FirebaseAuthRequest request2 = FirebaseAuthRequest.builder()
                    .email("test@example.com")
                    .password("Password1!")
                    .build();

            FirebaseAuthRequest request3 = FirebaseAuthRequest.builder()
                    .email("other@example.com")
                    .password("Password1!")
                    .build();

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
            assertThat(request1).isNotEqualTo(request3);
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("test@example.com")
                    .password("Password1!")
                    .build();

            String str = request.toString();
            assertThat(str).contains("email=test@example.com");
            assertThat(str).contains("password=Password1!");
            assertThat(str).contains("returnSecureToken=true");
        }

        @ParameterizedTest
        @ValueSource(strings = {"user@domain.com", "user.name@domain.co.uk", "user+tag@domain.org"})
        @DisplayName("should accept various valid email formats")
        void shouldAcceptVariousValidEmailFormats(String email) {
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email(email)
                    .password("Password1!")
                    .build();

            Set<ConstraintViolation<FirebaseAuthRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t", "\n"})
        @DisplayName("should fail validation when password is blank")
        void shouldFailValidationWhenPasswordIsBlank(String password) {
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("valid@email.com")
                    .password(password)
                    .build();

            Set<ConstraintViolation<FirebaseAuthRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
        }
    }

    // ==================== FirebaseAuthResponse Additional Tests ====================

    @Nested
    @DisplayName("FirebaseAuthResponse Additional Tests")
    class FirebaseAuthResponseAdditionalTests {

        @Test
        @DisplayName("should create via no-args constructor")
        void shouldCreateViaNoArgsConstructor() {
            FirebaseAuthResponse response = new FirebaseAuthResponse();
            assertThat(response.getIdToken()).isNull();
            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("should create via all-args constructor")
        void shouldCreateViaAllArgsConstructor() {
            FirebaseAuthResponse.FirebaseError error = FirebaseAuthResponse.FirebaseError.builder()
                    .code(401)
                    .message("Unauthorized")
                    .build();

            FirebaseAuthResponse response = new FirebaseAuthResponse(
                    "idToken", "refreshToken", "3600", "localId",
                    "email@test.com", true, "Display Name", true,
                    error, 100L, "corr-123", true, false, "message", "ADMIN", true
            );

            assertThat(response.getIdToken()).isEqualTo("idToken");
            assertThat(response.getRefreshToken()).isEqualTo("refreshToken");
            assertThat(response.getExpiresIn()).isEqualTo("3600");
            assertThat(response.getLocalId()).isEqualTo("localId");
            assertThat(response.getEmail()).isEqualTo("email@test.com");
            assertThat(response.isEmailVerified()).isTrue();
            assertThat(response.getDisplayName()).isEqualTo("Display Name");
            assertThat(response.isRegistered()).isTrue();
            assertThat(response.getError()).isNotNull();
            assertThat(response.getProcessingTime()).isEqualTo(100L);
            assertThat(response.getCorrelationId()).isEqualTo("corr-123");
            assertThat(response.isRequires2FA()).isTrue();
            assertThat(response.isRequires2FASetup()).isFalse();
            assertThat(response.getMessage()).isEqualTo("message");
            assertThat(response.getRole()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            FirebaseAuthResponse response1 = FirebaseAuthResponse.builder()
                    .idToken("token")
                    .email("test@test.com")
                    .build();

            FirebaseAuthResponse response2 = FirebaseAuthResponse.builder()
                    .idToken("token")
                    .email("test@test.com")
                    .build();

            assertThat(response1).isEqualTo(response2);
            assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
        }

        @Test
        @DisplayName("should return false for isSuccess when idToken is null and no error")
        void shouldReturnFalseWhenNoIdTokenAndNoError() {
            FirebaseAuthResponse response = FirebaseAuthResponse.builder().build();
            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("FirebaseError should have proper equals and hashCode")
        void firebaseErrorShouldHaveProperEqualsAndHashCode() {
            FirebaseAuthResponse.FirebaseError error1 = FirebaseAuthResponse.FirebaseError.builder()
                    .code(400)
                    .message("Bad request")
                    .build();

            FirebaseAuthResponse.FirebaseError error2 = FirebaseAuthResponse.FirebaseError.builder()
                    .code(400)
                    .message("Bad request")
                    .build();

            assertThat(error1).isEqualTo(error2);
            assertThat(error1.hashCode()).isEqualTo(error2.hashCode());
        }

        @Test
        @DisplayName("FirebaseError should create via no-args constructor")
        void firebaseErrorShouldCreateViaNoArgsConstructor() {
            FirebaseAuthResponse.FirebaseError error = new FirebaseAuthResponse.FirebaseError();
            error.setCode(500);
            error.setMessage("Internal error");
            error.setErrors(Arrays.asList("error1", "error2"));

            assertThat(error.getCode()).isEqualTo(500);
            assertThat(error.getMessage()).isEqualTo("Internal error");
            assertThat(error.getErrors()).isNotNull();
        }

        @Test
        @DisplayName("FirebaseError should generate proper toString")
        void firebaseErrorShouldGenerateProperToString() {
            FirebaseAuthResponse.FirebaseError error = FirebaseAuthResponse.FirebaseError.builder()
                    .code(404)
                    .message("Not found")
                    .build();

            String str = error.toString();
            assertThat(str).contains("404");
            assertThat(str).contains("Not found");
        }
    }

    // ==================== PasswordChangeRequest Additional Tests ====================

    @Nested
    @DisplayName("PasswordChangeRequest Additional Tests")
    class PasswordChangeRequestAdditionalTests {

        @Test
        @DisplayName("should create via no-args constructor")
        void shouldCreateViaNoArgsConstructor() {
            PasswordChangeRequest request = new PasswordChangeRequest();
            request.setCurrentPassword("oldPass");
            request.setNewPassword("newPass");

            assertThat(request.getCurrentPassword()).isEqualTo("oldPass");
            assertThat(request.getNewPassword()).isEqualTo("newPass");
        }

        @Test
        @DisplayName("should create via all-args constructor")
        void shouldCreateViaAllArgsConstructor() {
            PasswordChangeRequest request = new PasswordChangeRequest("current", "new");

            assertThat(request.getCurrentPassword()).isEqualTo("current");
            assertThat(request.getNewPassword()).isEqualTo("new");
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            PasswordChangeRequest request1 = PasswordChangeRequest.builder()
                    .currentPassword("old")
                    .newPassword("new")
                    .build();

            PasswordChangeRequest request2 = PasswordChangeRequest.builder()
                    .currentPassword("old")
                    .newPassword("new")
                    .build();

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword("current")
                    .newPassword("new")
                    .build();

            String str = request.toString();
            assertThat(str).contains("currentPassword=current");
            assertThat(str).contains("newPassword=new");
        }

        @ParameterizedTest
        @ValueSource(strings = {"abcdef12", "P@ssw0rd!", "very-long-password-with-special-chars1@#$%"})
        @DisplayName("should accept various password formats")
        void shouldAcceptVariousPasswordFormats(String password) {
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword("current")
                    .newPassword(password)
                    .build();

            Set<ConstraintViolation<PasswordChangeRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }

    // ==================== RecaptchaRequest Additional Tests ====================

    @Nested
    @DisplayName("RecaptchaRequest Additional Tests")
    class RecaptchaRequestAdditionalTests {

        @Test
        @DisplayName("should handle complex event map")
        void shouldHandleComplexEventMap() {
            RecaptchaRequest request = new RecaptchaRequest();
            Map<String, Object> event = new HashMap<>();
            event.put("token", "recaptcha-token-123");
            event.put("siteKey", "site-key-456");
            event.put("expectedAction", "LOGIN");
            event.put("userIpAddress", "192.168.1.1");
            event.put("userAgent", "Mozilla/5.0...");
            request.setEvent(event);

            assertThat(request.getEvent()).hasSize(5);
            assertThat(request.getEvent()).containsEntry("expectedAction", "LOGIN");
        }

        @Test
        @DisplayName("should handle null event")
        void shouldHandleNullEvent() {
            RecaptchaRequest request = new RecaptchaRequest();
            request.setEvent(null);

            assertThat(request.getEvent()).isNull();
        }

        @Test
        @DisplayName("should handle empty event map")
        void shouldHandleEmptyEventMap() {
            RecaptchaRequest request = new RecaptchaRequest();
            request.setEvent(new HashMap<>());

            assertThat(request.getEvent()).isEmpty();
        }
    }

    // ==================== RecaptchaResponse Additional Tests ====================

    @Nested
    @DisplayName("RecaptchaResponse Additional Tests")
    class RecaptchaResponseAdditionalTests {

        @Test
        @DisplayName("TokenProperties should have all fields set correctly")
        void tokenPropertiesShouldHaveAllFieldsSetCorrectly() {
            RecaptchaResponse.TokenProperties props = new RecaptchaResponse.TokenProperties();
            props.setValid(true);
            props.setInvalidReason(null);
            props.setHostname("myapp.example.com");
            props.setAction("REGISTER");
            props.setCreateTime("2024-12-30T10:00:00Z");

            assertThat(props.isValid()).isTrue();
            assertThat(props.getInvalidReason()).isNull();
            assertThat(props.getHostname()).isEqualTo("myapp.example.com");
            assertThat(props.getAction()).isEqualTo("REGISTER");
            assertThat(props.getCreateTime()).isEqualTo("2024-12-30T10:00:00Z");
        }

        @Test
        @DisplayName("RiskAnalysis should handle null reasons array")
        void riskAnalysisShouldHandleNullReasonsArray() {
            RecaptchaResponse.RiskAnalysis risk = new RecaptchaResponse.RiskAnalysis();
            risk.setScore(0.5);
            risk.setReasons(null);

            assertThat(risk.getScore()).isEqualTo(0.5);
            assertThat(risk.getReasons()).isNull();
        }

        @Test
        @DisplayName("RiskAnalysis should handle empty reasons array")
        void riskAnalysisShouldHandleEmptyReasonsArray() {
            RecaptchaResponse.RiskAnalysis risk = new RecaptchaResponse.RiskAnalysis();
            risk.setScore(0.9);
            risk.setReasons(new String[]{});

            assertThat(risk.getScore()).isEqualTo(0.9);
            assertThat(risk.getReasons()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, 0.1, 0.5, 0.7, 0.9, 1.0})
        @DisplayName("RiskAnalysis should accept various scores")
        void riskAnalysisShouldAcceptVariousScores(double score) {
            RecaptchaResponse.RiskAnalysis risk = new RecaptchaResponse.RiskAnalysis();
            risk.setScore(score);

            assertThat(risk.getScore()).isEqualTo(score);
        }

        @ParameterizedTest
        @ValueSource(strings = {"EXPIRED", "MISSING", "INVALID", "BROWSER_ERROR", "SITE_MISMATCH"})
        @DisplayName("TokenProperties should accept various invalid reasons")
        void tokenPropertiesShouldAcceptVariousInvalidReasons(String reason) {
            RecaptchaResponse.TokenProperties props = new RecaptchaResponse.TokenProperties();
            props.setValid(false);
            props.setInvalidReason(reason);

            assertThat(props.isValid()).isFalse();
            assertThat(props.getInvalidReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("should set and get all response fields")
        void shouldSetAndGetAllResponseFields() {
            RecaptchaResponse response = new RecaptchaResponse();
            RecaptchaResponse.TokenProperties props = new RecaptchaResponse.TokenProperties();
            RecaptchaResponse.RiskAnalysis risk = new RecaptchaResponse.RiskAnalysis();
            Object event = new HashMap<String, Object>();

            props.setValid(true);
            risk.setScore(0.9);
            response.setTokenProperties(props);
            response.setRiskAnalysis(risk);
            response.setName("projects/test/assessments/123");
            response.setEvent(event);

            assertThat(response.getTokenProperties()).isNotNull();
            assertThat(response.getRiskAnalysis()).isNotNull();
            assertThat(response.getName()).isEqualTo("projects/test/assessments/123");
            assertThat(response.getEvent()).isNotNull();
        }
    }

    // ==================== SocialSignInRequest Additional Tests ====================

    @Nested
    @DisplayName("SocialSignInRequest Additional Tests")
    class SocialSignInRequestAdditionalTests {

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            SocialSignInRequest request1 = new SocialSignInRequest();
            request1.setPlatformName("Instagram");
            request1.setAuthCode("code123");

            SocialSignInRequest request2 = new SocialSignInRequest();
            request2.setPlatformName("Instagram");
            request2.setAuthCode("code123");

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("TikTok");
            request.setAuthCode("authCode456");

            String str = request.toString();
            assertThat(str).contains("platformName=TikTok");
            assertThat(str).contains("authCode=authCode456");
        }

        @ParameterizedTest
        @ValueSource(strings = {"Instagram", "TikTok", "YouTube", "Facebook", "Twitter", "LinkedIn"})
        @DisplayName("should accept various platform names")
        void shouldAcceptVariousPlatformNames(String platform) {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName(platform);
            request.setAuthCode("valid-code");

            Set<ConstraintViolation<SocialSignInRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc123", "very-long-auth-code-with-special-chars-!@#", "CAPS_CODE_123"})
        @DisplayName("should accept various auth code formats")
        void shouldAcceptVariousAuthCodeFormats(String code) {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode(code);

            Set<ConstraintViolation<SocialSignInRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }

    // ==================== SocialSignInResponse Additional Tests ====================

    @Nested
    @DisplayName("SocialSignInResponse Additional Tests")
    class SocialSignInResponseAdditionalTests {

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            SocialSignInResponse response1 = new SocialSignInResponse();
            response1.setSuccess(true);
            response1.setUserId(123L);

            SocialSignInResponse response2 = new SocialSignInResponse();
            response2.setSuccess(true);
            response2.setUserId(123L);

            assertThat(response1).isEqualTo(response2);
            assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            SocialSignInResponse response = new SocialSignInResponse();
            response.setSuccess(true);
            response.setFirebaseUserId("fb-uid-123");

            String str = response.toString();
            assertThat(str).contains("success=true");
            assertThat(str).contains("firebaseUserId=fb-uid-123");
        }

        @Test
        @DisplayName("should handle complex social user data")
        void shouldHandleComplexSocialUserData() {
            SocialSignInResponse response = new SocialSignInResponse();
            Map<String, Object> socialData = new HashMap<>();
            socialData.put("username", "influencer123");
            socialData.put("followers", 1000000);
            socialData.put("verified", true);
            socialData.put("profileUrl", "https://instagram.com/influencer123");
            socialData.put("bio", "Content creator and lifestyle blogger");
            response.setSocialUserData(socialData);

            assertThat(response.getSocialUserData()).hasSize(5);
            assertThat(response.getSocialUserData()).containsEntry("followers", 1000000);
            assertThat(response.getSocialUserData()).containsEntry("verified", true);
        }

        @Test
        @DisplayName("should handle null social user data")
        void shouldHandleNullSocialUserData() {
            SocialSignInResponse response = new SocialSignInResponse();
            response.setSocialUserData(null);

            assertThat(response.getSocialUserData()).isNull();
        }

        @Test
        @DisplayName("should differentiate between existing and new users")
        void shouldDifferentiateBetweenExistingAndNewUsers() {
            SocialSignInResponse existingUser = new SocialSignInResponse();
            existingUser.setSuccess(true);
            existingUser.setUserExists(true);
            existingUser.setUserId(123L);

            SocialSignInResponse newUser = new SocialSignInResponse();
            newUser.setSuccess(true);
            newUser.setUserExists(false);
            newUser.setCustomToken("custom-token-for-registration");

            assertThat(existingUser.isUserExists()).isTrue();
            assertThat(existingUser.getUserId()).isNotNull();
            assertThat(newUser.isUserExists()).isFalse();
            assertThat(newUser.getCustomToken()).isNotNull();
        }
    }

    // ==================== TokenExchangeRequest Additional Tests ====================

    @Nested
    @DisplayName("TokenExchangeRequest Additional Tests")
    class TokenExchangeRequestAdditionalTests {

        @Test
        @DisplayName("should create via no-args constructor")
        void shouldCreateViaNoArgsConstructor() {
            TokenExchangeRequest request = new TokenExchangeRequest();
            request.setIdToken("token123");
            request.setExpirationDays(14);

            assertThat(request.getIdToken()).isEqualTo("token123");
            assertThat(request.getExpirationDays()).isEqualTo(14);
        }

        @Test
        @DisplayName("should create via all-args constructor")
        void shouldCreateViaAllArgsConstructor() {
            TokenExchangeRequest request = new TokenExchangeRequest("id-token", 30);

            assertThat(request.getIdToken()).isEqualTo("id-token");
            assertThat(request.getExpirationDays()).isEqualTo(30);
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            TokenExchangeRequest request1 = TokenExchangeRequest.builder()
                    .idToken("token")
                    .expirationDays(7)
                    .build();

            TokenExchangeRequest request2 = TokenExchangeRequest.builder()
                    .idToken("token")
                    .expirationDays(7)
                    .build();

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            TokenExchangeRequest request = TokenExchangeRequest.builder()
                    .idToken("myToken")
                    .expirationDays(14)
                    .build();

            String str = request.toString();
            assertThat(str).contains("idToken=myToken");
            assertThat(str).contains("expirationDays=14");
        }

        @Test
        @DisplayName("should allow null idToken for OAuth flow")
        void shouldAllowNullIdTokenForOAuthFlow() {
            TokenExchangeRequest request = TokenExchangeRequest.builder()
                    .idToken(null)
                    .expirationDays(7)
                    .build();

            Set<ConstraintViolation<TokenExchangeRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @CsvSource({
                "1, true",
                "7, true",
                "14, true",
                "30, true",
                "0, false",
                "31, false",
                "-1, false"
        })
        @DisplayName("should validate expiration days boundary")
        void shouldValidateExpirationDaysBoundary(int days, boolean shouldPass) {
            TokenExchangeRequest request = TokenExchangeRequest.builder()
                    .expirationDays(days)
                    .build();

            Set<ConstraintViolation<TokenExchangeRequest>> violations = validator.validate(request);
            if (shouldPass) {
                assertThat(violations).isEmpty();
            } else {
                assertThat(violations).isNotEmpty();
            }
        }
    }

    // ==================== TokenExchangeResponse Additional Tests ====================

    @Nested
    @DisplayName("TokenExchangeResponse Additional Tests")
    class TokenExchangeResponseAdditionalTests {

        @Test
        @DisplayName("should create via no-args constructor")
        void shouldCreateViaNoArgsConstructor() {
            TokenExchangeResponse response = new TokenExchangeResponse();
            response.setSuccess(true);
            response.setUserId(123L);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getUserId()).isEqualTo(123L);
        }

        @Test
        @DisplayName("should create via all-args constructor")
        void shouldCreateViaAllArgsConstructor() {
            TokenExchangeResponse response = new TokenExchangeResponse(
                    true, 123L, "email@test.com", "ADMIN", "fb-uid",
                    null, true, false, true, true, "FULL", "7 days"
            );

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getUserId()).isEqualTo(123L);
            assertThat(response.getEmail()).isEqualTo("email@test.com");
            assertThat(response.getRole()).isEqualTo("ADMIN");
            assertThat(response.getFirebaseUid()).isEqualTo("fb-uid");
            assertThat(response.getRequires2FA()).isTrue();
            assertThat(response.getRequires2FASetup()).isFalse();
            assertThat(response.getTwoFactorEnabled()).isTrue();
            assertThat(response.getTwoFactorVerified()).isTrue();
            assertThat(response.getCookieType()).isEqualTo("FULL");
            assertThat(response.getSessionDuration()).isEqualTo("7 days");
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            TokenExchangeResponse response1 = TokenExchangeResponse.builder()
                    .success(true)
                    .userId(123L)
                    .email("test@test.com")
                    .build();

            TokenExchangeResponse response2 = TokenExchangeResponse.builder()
                    .success(true)
                    .userId(123L)
                    .email("test@test.com")
                    .build();

            assertThat(response1).isEqualTo(response2);
            assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            TokenExchangeResponse response = TokenExchangeResponse.builder()
                    .success(true)
                    .userId(456L)
                    .role("COMPANY")
                    .build();

            String str = response.toString();
            assertThat(str).contains("success=true");
            assertThat(str).contains("userId=456");
            assertThat(str).contains("role=COMPANY");
        }

        @ParameterizedTest
        @ValueSource(strings = {"INFLUENCER", "COMPANY", "ADMIN", "USER"})
        @DisplayName("should accept various user roles")
        void shouldAcceptVariousUserRoles(String role) {
            TokenExchangeResponse response = TokenExchangeResponse.success(
                    1L, "test@test.com", role, "fb-uid"
            );

            assertThat(response.getRole()).isEqualTo(role);
        }

        @Test
        @DisplayName("should handle all 2FA states correctly")
        void shouldHandleAll2FAStatesCorrectly() {
            // User with 2FA enabled but not verified
            TokenExchangeResponse needsVerification = TokenExchangeResponse.builder()
                    .success(true)
                    .requires2FA(true)
                    .requires2FASetup(false)
                    .twoFactorEnabled(true)
                    .twoFactorVerified(false)
                    .build();

            // User needs to setup 2FA
            TokenExchangeResponse needsSetup = TokenExchangeResponse.builder()
                    .success(true)
                    .requires2FA(true)
                    .requires2FASetup(true)
                    .twoFactorEnabled(false)
                    .twoFactorVerified(false)
                    .build();

            // User with 2FA complete
            TokenExchangeResponse complete = TokenExchangeResponse.builder()
                    .success(true)
                    .requires2FA(false)
                    .requires2FASetup(false)
                    .twoFactorEnabled(true)
                    .twoFactorVerified(true)
                    .build();

            assertThat(needsVerification.getRequires2FA()).isTrue();
            assertThat(needsVerification.getTwoFactorVerified()).isFalse();

            assertThat(needsSetup.getRequires2FASetup()).isTrue();
            assertThat(needsSetup.getTwoFactorEnabled()).isFalse();

            assertThat(complete.getTwoFactorEnabled()).isTrue();
            assertThat(complete.getTwoFactorVerified()).isTrue();
        }
    }

    // ==================== TotpSetupResponse Additional Tests ====================

    @Nested
    @DisplayName("TotpSetupResponse Additional Tests")
    class TotpSetupResponseAdditionalTests {

        @Test
        @DisplayName("should create via no-args constructor")
        void shouldCreateViaNoArgsConstructor() {
            TotpSetupResponse response = new TotpSetupResponse();
            response.setQrCodeUrl("otpauth://...");
            response.setSecret("SECRET123");

            assertThat(response.getQrCodeUrl()).isEqualTo("otpauth://...");
            assertThat(response.getSecret()).isEqualTo("SECRET123");
        }

        @Test
        @DisplayName("should create via all-args constructor")
        void shouldCreateViaAllArgsConstructor() {
            List<String> codes = Arrays.asList("CODE1", "CODE2", "CODE3");
            TotpSetupResponse response = new TotpSetupResponse(
                    "otpauth://url", "base64image", "chartUrl",
                    codes, "SECRET", "SEC RET", "Issuer", "email@test.com",
                    null, false
            );

            assertThat(response.getQrCodeUrl()).isEqualTo("otpauth://url");
            assertThat(response.getQrCodeImage()).isEqualTo("base64image");
            assertThat(response.getGoogleChartsUrl()).isEqualTo("chartUrl");
            assertThat(response.getBackupCodes()).hasSize(3);
            assertThat(response.getSecret()).isEqualTo("SECRET");
            assertThat(response.getSecretFormatted()).isEqualTo("SEC RET");
            assertThat(response.getIssuer()).isEqualTo("Issuer");
            assertThat(response.getEmail()).isEqualTo("email@test.com");
            assertThat(response.isAlreadyEnabled()).isFalse();
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            TotpSetupResponse response1 = TotpSetupResponse.builder()
                    .secret("SECRET")
                    .email("test@test.com")
                    .build();

            TotpSetupResponse response2 = TotpSetupResponse.builder()
                    .secret("SECRET")
                    .email("test@test.com")
                    .build();

            assertThat(response1).isEqualTo(response2);
            assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            TotpSetupResponse response = TotpSetupResponse.builder()
                    .secret("MYSECRET")
                    .issuer("MyApp")
                    .build();

            String str = response.toString();
            assertThat(str).contains("secret=MYSECRET");
            assertThat(str).contains("issuer=MyApp");
        }

        @Test
        @DisplayName("should handle backup codes list operations")
        void shouldHandleBackupCodesListOperations() {
            List<String> codes = Arrays.asList("AAAA1111", "BBBB2222", "CCCC3333", "DDDD4444",
                    "EEEE5555", "FFFF6666", "GGGG7777", "HHHH8888");
            TotpSetupResponse response = TotpSetupResponse.builder()
                    .backupCodes(codes)
                    .build();

            assertThat(response.getBackupCodes()).hasSize(8);
            assertThat(response.getBackupCodes()).contains("AAAA1111", "HHHH8888");
        }

        @Test
        @DisplayName("should handle formatted secret correctly")
        void shouldHandleFormattedSecretCorrectly() {
            TotpSetupResponse response = TotpSetupResponse.builder()
                    .secret("JBSWY3DPEHPK3PXP")
                    .secretFormatted("JBSW Y3DP EHPK 3PXP")
                    .build();

            assertThat(response.getSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
            assertThat(response.getSecretFormatted()).isEqualTo("JBSW Y3DP EHPK 3PXP");
            assertThat(response.getSecretFormatted()).contains(" ");
        }
    }

    // ==================== TotpValidateRequest Additional Tests ====================

    @Nested
    @DisplayName("TotpValidateRequest Additional Tests")
    class TotpValidateRequestAdditionalTests {

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            TotpValidateRequest request1 = new TotpValidateRequest();
            request1.setUserId("user123");
            request1.setCode("123456");

            TotpValidateRequest request2 = new TotpValidateRequest();
            request2.setUserId("user123");
            request2.setCode("123456");

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            TotpValidateRequest request = new TotpValidateRequest();
            request.setUserId("user456");
            request.setCode("654321");

            String str = request.toString();
            assertThat(str).contains("userId=user456");
            assertThat(str).contains("code=654321");
        }

        @ParameterizedTest
        @ValueSource(strings = {"user-123", "firebase-uid", "long-user-id-with-dashes-and-numbers-123"})
        @DisplayName("should accept various user ID formats")
        void shouldAcceptVariousUserIdFormats(String userId) {
            TotpValidateRequest request = new TotpValidateRequest();
            request.setUserId(userId);
            request.setCode("123456");

            Set<ConstraintViolation<TotpValidateRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @CsvSource({
                "000000, true",
                "000001, true",
                "123456, true",
                "999999, true"
        })
        @DisplayName("should accept valid TOTP code ranges")
        void shouldAcceptValidTotpCodeRanges(String code, boolean shouldPass) {
            TotpValidateRequest request = new TotpValidateRequest();
            request.setUserId("user123");
            request.setCode(code);

            Set<ConstraintViolation<TotpValidateRequest>> violations = validator.validate(request);
            if (shouldPass) {
                assertThat(violations).isEmpty();
            } else {
                assertThat(violations).isNotEmpty();
            }
        }
    }

    // ==================== AuthOperationResponse Additional Tests ====================

    @Nested
    @DisplayName("AuthOperationResponse Additional Tests")
    class AuthOperationResponseAdditionalTests {

        @Test
        @DisplayName("should create via no-args constructor")
        void shouldCreateViaNoArgsConstructor() {
            AuthOperationResponse response = new AuthOperationResponse();
            response.setSuccess(true);
            response.setMessage("Done");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Done");
        }

        @Test
        @DisplayName("should create via all-args constructor")
        void shouldCreateViaAllArgsConstructor() {
            Map<String, Object> data = new HashMap<>();
            data.put("key", "value");

            AuthOperationResponse response = new AuthOperationResponse(
                    true, "Success", "corr-123", 50L, data, null
            );

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Success");
            assertThat(response.getCorrelationId()).isEqualTo("corr-123");
            assertThat(response.getProcessingTime()).isEqualTo(50L);
            assertThat(response.getData()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            AuthOperationResponse response1 = AuthOperationResponse.builder()
                    .success(true)
                    .message("OK")
                    .build();

            AuthOperationResponse response2 = AuthOperationResponse.builder()
                    .success(true)
                    .message("OK")
                    .build();

            assertThat(response1).isEqualTo(response2);
            assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            AuthOperationResponse response = AuthOperationResponse.builder()
                    .success(true)
                    .correlationId("correlation-123")
                    .build();

            String str = response.toString();
            assertThat(str).contains("success=true");
            assertThat(str).contains("correlationId=correlation-123");
        }

        @Test
        @DisplayName("should handle complex data map")
        void shouldHandleComplexDataMap() {
            Map<String, Object> data = new HashMap<>();
            data.put("userId", 123L);
            data.put("email", "test@test.com");
            data.put("roles", Arrays.asList("ADMIN", "USER"));
            data.put("metadata", Map.of("createdAt", "2024-01-01"));

            AuthOperationResponse response = AuthOperationResponse.success("User created", data);

            assertThat(response.getData()).hasSize(4);
            assertThat(response.getData().get("roles")).isInstanceOf(List.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 1, 100, 1000, 10000})
        @DisplayName("should accept various processing times")
        void shouldAcceptVariousProcessingTimes(long time) {
            AuthOperationResponse response = AuthOperationResponse.builder()
                    .success(true)
                    .processingTime(time)
                    .build();

            assertThat(response.getProcessingTime()).isEqualTo(time);
        }
    }

    // ==================== Edge Cases and Boundary Tests ====================

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle very long email addresses")
        void shouldHandleVeryLongEmailAddresses() {
            String longEmail = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + ".com";
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email(longEmail)
                    .password("Password1!")
                    .build();

            // Just verify it can be set, validation depends on regex
            assertThat(request.getEmail()).isEqualTo(longEmail);
        }

        @Test
        @DisplayName("should handle very long passwords")
        void shouldHandleVeryLongPasswords() {
            String longPassword = "P@ss1" + "a".repeat(123);
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword("current")
                    .newPassword(longPassword)
                    .build();

            Set<ConstraintViolation<PasswordChangeRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle Unicode characters in display names")
        void shouldHandleUnicodeCharactersInDisplayNames() {
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .displayName("Emilia Muller")
                    .build();

            assertThat(response.getDisplayName()).isEqualTo("Emilia Muller");
        }

        @Test
        @DisplayName("should handle Unicode in social user data")
        void shouldHandleUnicodeInSocialUserData() {
            SocialSignInResponse response = new SocialSignInResponse();
            Map<String, Object> data = new HashMap<>();
            data.put("bio", "Photographer | Traveler");
            data.put("name", "Jean-Pierre Dubois");
            response.setSocialUserData(data);

            assertThat(response.getSocialUserData().get("name")).isEqualTo("Jean-Pierre Dubois");
        }

        @Test
        @DisplayName("should handle empty maps")
        void shouldHandleEmptyMaps() {
            AuthOperationResponse response = AuthOperationResponse.success("OK", new HashMap<>());

            assertThat(response.getData()).isEmpty();
        }

        @Test
        @DisplayName("should handle null values in maps")
        void shouldHandleNullValuesInMaps() {
            Map<String, Object> data = new HashMap<>();
            data.put("nullKey", null);
            data.put("validKey", "value");

            AuthOperationResponse response = AuthOperationResponse.success("OK", data);

            assertThat(response.getData()).containsEntry("nullKey", null);
            assertThat(response.getData()).containsEntry("validKey", "value");
        }

        @Test
        @DisplayName("should handle special characters in auth tokens")
        void shouldHandleSpecialCharactersInAuthTokens() {
            String specialToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL3NlY3VyZXRva2VuLmdvb2dsZS5jb20vbXktcHJvamVjdCIsImF1ZCI6Im15LXByb2plY3QiLCJhdXRoX3RpbWUiOjE2MDk0NTkyMDAsInVzZXJfaWQiOiJhYmMxMjM0NTY3ODkiLCJzdWIiOiJhYmMxMjM0NTY3ODkiLCJpYXQiOjE2MDk0NTkyMDAsImV4cCI6MTYwOTQ2MjgwMCwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWV9.signature";

            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("test@test.com")
                    .password("password")
                    .token(specialToken)
                    .build();

            assertThat(request.getToken()).isEqualTo(specialToken);
        }

        @Test
        @DisplayName("should handle empty backup codes list")
        void shouldHandleEmptyBackupCodesList() {
            TotpSetupResponse response = TotpSetupResponse.builder()
                    .backupCodes(Arrays.asList())
                    .build();

            assertThat(response.getBackupCodes()).isEmpty();
        }

        @Test
        @DisplayName("should handle whitespace-only strings in validation")
        void shouldHandleWhitespaceOnlyStringsInValidation() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("   ");
            request.setAuthCode("\t\n");

            Set<ConstraintViolation<SocialSignInRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== Serialization Scenario Tests ====================

    @Nested
    @DisplayName("Serialization Scenario Tests")
    class SerializationScenarioTests {

        @Test
        @DisplayName("should maintain all fields after builder pattern usage")
        void shouldMaintainAllFieldsAfterBuilderPatternUsage() {
            TokenExchangeResponse original = TokenExchangeResponse.builder()
                    .success(true)
                    .userId(123L)
                    .email("test@test.com")
                    .role("ADMIN")
                    .firebaseUid("fb-uid")
                    .requires2FA(true)
                    .requires2FASetup(false)
                    .twoFactorEnabled(true)
                    .twoFactorVerified(true)
                    .cookieType("FULL")
                    .sessionDuration("7 days")
                    .build();

            // Simulate "serialization" by creating new object with same values
            TokenExchangeResponse copy = TokenExchangeResponse.builder()
                    .success(original.isSuccess())
                    .userId(original.getUserId())
                    .email(original.getEmail())
                    .role(original.getRole())
                    .firebaseUid(original.getFirebaseUid())
                    .requires2FA(original.getRequires2FA())
                    .requires2FASetup(original.getRequires2FASetup())
                    .twoFactorEnabled(original.getTwoFactorEnabled())
                    .twoFactorVerified(original.getTwoFactorVerified())
                    .cookieType(original.getCookieType())
                    .sessionDuration(original.getSessionDuration())
                    .build();

            assertThat(copy).isEqualTo(original);
        }

        @Test
        @DisplayName("FirebaseAuthResponse should handle all fields for JSON serialization")
        void firebaseAuthResponseShouldHandleAllFieldsForJsonSerialization() {
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .idToken("id-token")
                    .refreshToken("refresh-token")
                    .expiresIn("3600")
                    .localId("local-123")
                    .email("test@test.com")
                    .emailVerified(true)
                    .displayName("Test User")
                    .registered(true)
                    .processingTime(100L)
                    .correlationId("corr-123")
                    .requires2FA(false)
                    .requires2FASetup(false)
                    .message("Success")
                    .role("USER")
                    .success(true)
                    .build();

            // Verify all fields are accessible
            assertThat(response.getIdToken()).isNotNull();
            assertThat(response.getRefreshToken()).isNotNull();
            assertThat(response.getExpiresIn()).isNotNull();
            assertThat(response.getLocalId()).isNotNull();
            assertThat(response.getEmail()).isNotNull();
            assertThat(response.getDisplayName()).isNotNull();
            assertThat(response.getProcessingTime()).isNotNull();
            assertThat(response.getCorrelationId()).isNotNull();
            assertThat(response.getMessage()).isNotNull();
            assertThat(response.getRole()).isNotNull();
        }

        @Test
        @DisplayName("AuthOperationResponse should handle nested data structures")
        void authOperationResponseShouldHandleNestedDataStructures() {
            Map<String, Object> userData = new HashMap<>();
            userData.put("profile", Map.of("firstName", "John", "lastName", "Doe"));
            userData.put("preferences", Map.of("theme", "dark", "notifications", true));
            userData.put("roles", Arrays.asList("USER", "ADMIN"));

            AuthOperationResponse response = AuthOperationResponse.success("User data", userData);

            assertThat(response.getData()).containsKey("profile");
            assertThat(response.getData()).containsKey("preferences");
            assertThat(response.getData()).containsKey("roles");
        }
    }
}
