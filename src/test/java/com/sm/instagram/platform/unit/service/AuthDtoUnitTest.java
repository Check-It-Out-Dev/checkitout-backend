package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Auth DTOs.
 * Tests DTO creation, validation, factory methods, and builder patterns.
 */
@DisplayName("Auth DTO Unit Tests")
class AuthDtoUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== AssessmentResult Tests ====================

    @Nested
    @DisplayName("AssessmentResult")
    class AssessmentResultTests {

        @Test
        @DisplayName("should create success result with score")
        void shouldCreateSuccessResult() {
            // When
            AssessmentResult result = AssessmentResult.success(0.9);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getScore()).isEqualTo(0.9);
            assertThat(result.getReason()).isEqualTo("Valid assessment");
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("should create blocked result with score and reason")
        void shouldCreateBlockedResult() {
            // When
            AssessmentResult result = AssessmentResult.blocked(0.2, "Low score detected");

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getScore()).isEqualTo(0.2);
            assertThat(result.getReason()).isEqualTo("Low score detected");
            assertThat(result.getStatus()).isEqualTo("BLOCKED");
        }

        @Test
        @DisplayName("should create invalid result with reason")
        void shouldCreateInvalidResult() {
            // When
            AssessmentResult result = AssessmentResult.invalid("Token expired");

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getScore()).isEqualTo(0.0);
            assertThat(result.getReason()).isEqualTo("Token expired");
            assertThat(result.getStatus()).isEqualTo("INVALID");
        }

        @Test
        @DisplayName("should create allowed result with reason")
        void shouldCreateAllowedResult() {
            // When
            AssessmentResult result = AssessmentResult.allowed("Whitelisted user");

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getScore()).isEqualTo(1.0);
            assertThat(result.getReason()).isEqualTo("Whitelisted user");
            assertThat(result.getStatus()).isEqualTo("ALLOWED");
        }

        @Test
        @DisplayName("should format toString correctly")
        void shouldFormatToString() {
            // Given
            AssessmentResult result = AssessmentResult.success(0.85);

            // When
            String str = result.toString();

            // Then
            assertThat(str).contains("SUCCESS");
            assertThat(str).contains("0.85");
            assertThat(str).contains("true");
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, 0.1, 0.5, 0.9, 1.0})
        @DisplayName("should accept various scores")
        void shouldAcceptVariousScores(double score) {
            // When
            AssessmentResult result = AssessmentResult.success(score);

            // Then
            assertThat(result.getScore()).isEqualTo(score);
        }
    }

    // ==================== AuthOperationResponse Tests ====================

    @Nested
    @DisplayName("AuthOperationResponse")
    class AuthOperationResponseTests {

        @Test
        @DisplayName("should create success response with message")
        void shouldCreateSuccessWithMessage() {
            // When
            AuthOperationResponse response = AuthOperationResponse.success("Operation successful");

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Operation successful");
            assertThat(response.getData()).isNull();
            assertThat(response.getError()).isNull();
        }

        @Test
        @DisplayName("should create success response with data")
        void shouldCreateSuccessWithData() {
            // Given
            Map<String, Object> data = new HashMap<>();
            data.put("userId", 123L);
            data.put("email", "test@example.com");

            // When
            AuthOperationResponse response = AuthOperationResponse.success("User created", data);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("User created");
            assertThat(response.getData()).containsEntry("userId", 123L);
            assertThat(response.getData()).containsEntry("email", "test@example.com");
        }

        @Test
        @DisplayName("should create error response")
        void shouldCreateErrorResponse() {
            // When
            AuthOperationResponse response = AuthOperationResponse.error("Invalid credentials");

            // Then
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Invalid credentials");
        }

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            // When
            AuthOperationResponse response = AuthOperationResponse.builder()
                    .success(true)
                    .message("Complete")
                    .correlationId("corr-123")
                    .processingTime(150L)
                    .build();

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Complete");
            assertThat(response.getCorrelationId()).isEqualTo("corr-123");
            assertThat(response.getProcessingTime()).isEqualTo(150L);
        }
    }

    // ==================== RegisterUserRequest Validation Tests ====================

    @Nested
    @DisplayName("RegisterUserRequest Validation")
    class RegisterUserRequestValidationTests {

        @Test
        @DisplayName("should pass validation for valid COMPANY user")
        void shouldPassValidationForValidCompanyUser() {
            // Given
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("company@example.com");
            request.setPassword("Password1!");
            request.setUserType("COMPANY");
            request.setCompanyName("Test Company");
            request.setAddressStreet("123 Main St");
            request.setAddressCity("Warsaw");
            request.setAddressPostalCode("00-001");
            request.setAddressCountry("Poland");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation for valid INFLUENCER user")
        void shouldPassValidationForValidInfluencerUser() {
            // Given
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("influencer@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @DisplayName("should fail validation for blank email")
        void shouldFailValidationForBlankEmail(String email) {
            // Given
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail(email);
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for invalid email format")
        void shouldFailValidationForInvalidEmail() {
            // Given
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("not-an-email");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("email"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for short password")
        void shouldFailValidationForShortPassword() {
            // Given
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("test@example.com");
            request.setPassword("12345"); // Less than 6 characters
            request.setUserType("INFLUENCER");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("password"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for invalid userType")
        void shouldFailValidationForInvalidUserType() {
            // Given
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password1!");
            request.setUserType("ADMIN"); // Not COMPANY or INFLUENCER

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for COMPANY without company name")
        void shouldFailValidationForCompanyWithoutName() {
            // Given
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("company@example.com");
            request.setPassword("Password1!");
            request.setUserType("COMPANY");
            // Missing companyName
            request.setAddressStreet("123 Main St");
            request.setAddressCity("Warsaw");
            request.setAddressPostalCode("00-001");
            request.setAddressCountry("Poland");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("validCompanyData"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for COMPANY without address")
        void shouldFailValidationForCompanyWithoutAddress() {
            // Given
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("company@example.com");
            request.setPassword("Password1!");
            request.setUserType("COMPANY");
            request.setCompanyName("Test Company");
            // Missing address fields

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("validAddressData"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for INFLUENCER with platform but no auth code")
        void shouldFailValidationForInfluencerWithPlatformNoAuthCode() {
            // Given
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("influencer@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");
            request.setSocialPlatform("Instagram");
            // Missing socialAuthCode

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("validSocialData"))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"+48123456789", "123-456-7890", "(123)456-7890", "+1-800-555-1234"})
        @DisplayName("should accept valid phone numbers")
        void shouldAcceptValidPhoneNumbers(String phone) {
            // Given
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");
            request.setPhoneNumber(phone);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("phoneNumber"))).isTrue();
        }
    }

    // ==================== FirebaseAuthRequest Tests ====================

    @Nested
    @DisplayName("FirebaseAuthRequest")
    class FirebaseAuthRequestTests {

        @Test
        @DisplayName("should pass validation for valid request")
        void shouldPassValidationForValidRequest() {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("test@example.com")
                    .password("Password1!")
                    .build();

            // When
            Set<ConstraintViolation<FirebaseAuthRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should have returnSecureToken true by default")
        void shouldHaveReturnSecureTokenTrueByDefault() {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("test@example.com")
                    .password("Password1!")
                    .build();

            // Then
            assertThat(request.isReturnSecureToken()).isTrue();
        }

        @Test
        @DisplayName("should allow setting refresh token")
        void shouldAllowSettingRefreshToken() {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("test@example.com")
                    .password("Password1!")
                    .refreshToken("refresh-token-123")
                    .build();

            // Then
            assertThat(request.getRefreshToken()).isEqualTo("refresh-token-123");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @DisplayName("should fail validation for blank email")
        void shouldFailValidationForBlankEmail(String email) {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email(email)
                    .password("Password1!")
                    .build();

            // When
            Set<ConstraintViolation<FirebaseAuthRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for invalid email format")
        void shouldFailValidationForInvalidEmailFormat() {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("not-an-email")
                    .password("Password1!")
                    .build();

            // When
            Set<ConstraintViolation<FirebaseAuthRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== FirebaseAuthResponse Tests ====================

    @Nested
    @DisplayName("FirebaseAuthResponse")
    class FirebaseAuthResponseTests {

        @Test
        @DisplayName("should create error response")
        void shouldCreateErrorResponse() {
            // When
            FirebaseAuthResponse response = FirebaseAuthResponse.error("Authentication failed");

            // Then
            assertThat(response.getError()).isNotNull();
            assertThat(response.getError().getMessage()).isEqualTo("Authentication failed");
            assertThat(response.getError().getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("should return true for isSuccess when success flag is set")
        void shouldReturnTrueWhenSuccessFlagSet() {
            // Given
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .success(true)
                    .build();

            // Then
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should return true for isSuccess when has idToken and no error")
        void shouldReturnTrueWhenHasIdTokenNoError() {
            // Given
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .idToken("valid-id-token")
                    .build();

            // Then
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should return false for isSuccess when has error")
        void shouldReturnFalseWhenHasError() {
            // Given
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .error(FirebaseAuthResponse.FirebaseError.builder()
                            .message("Error")
                            .build())
                    .build();

            // Then
            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("should build with all authentication fields")
        void shouldBuildWithAllAuthFields() {
            // Given
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .idToken("id-token")
                    .refreshToken("refresh-token")
                    .expiresIn("3600")
                    .localId("local-123")
                    .email("test@example.com")
                    .emailVerified(true)
                    .displayName("Test User")
                    .registered(true)
                    .role("INFLUENCER")
                    .build();

            // Then
            assertThat(response.getIdToken()).isEqualTo("id-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getExpiresIn()).isEqualTo("3600");
            assertThat(response.getLocalId()).isEqualTo("local-123");
            assertThat(response.getEmail()).isEqualTo("test@example.com");
            assertThat(response.isEmailVerified()).isTrue();
            assertThat(response.getDisplayName()).isEqualTo("Test User");
            assertThat(response.isRegistered()).isTrue();
            assertThat(response.getRole()).isEqualTo("INFLUENCER");
        }

        @Test
        @DisplayName("should handle 2FA fields")
        void shouldHandle2FAFields() {
            // Given
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .requires2FA(true)
                    .requires2FASetup(false)
                    .message("2FA verification required")
                    .build();

            // Then
            assertThat(response.isRequires2FA()).isTrue();
            assertThat(response.isRequires2FASetup()).isFalse();
            assertThat(response.getMessage()).isEqualTo("2FA verification required");
        }

        @Nested
        @DisplayName("FirebaseError")
        class FirebaseErrorTests {

            @Test
            @DisplayName("should build error with all fields")
            void shouldBuildErrorWithAllFields() {
                // Given
                FirebaseAuthResponse.FirebaseError error = FirebaseAuthResponse.FirebaseError.builder()
                        .code(401)
                        .message("Unauthorized")
                        .errors(Arrays.asList("Error 1", "Error 2"))
                        .build();

                // Then
                assertThat(error.getCode()).isEqualTo(401);
                assertThat(error.getMessage()).isEqualTo("Unauthorized");
                assertThat(error.getErrors()).isNotNull();
            }
        }
    }

    // ==================== RecaptchaRequest Tests ====================

    @Nested
    @DisplayName("RecaptchaRequest")
    class RecaptchaRequestTests {

        @Test
        @DisplayName("should create with default constructor")
        void shouldCreateWithDefaultConstructor() {
            // When
            RecaptchaRequest request = new RecaptchaRequest();

            // Then
            assertThat(request.getEvent()).isNull();
        }

        @Test
        @DisplayName("should set and get event")
        void shouldSetAndGetEvent() {
            // Given
            RecaptchaRequest request = new RecaptchaRequest();
            Map<String, Object> event = new HashMap<>();
            event.put("token", "recaptcha-token");
            event.put("siteKey", "site-key-123");

            // When
            request.setEvent(event);

            // Then
            assertThat(request.getEvent()).isEqualTo(event);
        }
    }

    // ==================== RecaptchaResponse Tests ====================

    @Nested
    @DisplayName("RecaptchaResponse")
    class RecaptchaResponseTests {

        @Test
        @DisplayName("should set and get token properties")
        void shouldSetAndGetTokenProperties() {
            // Given
            RecaptchaResponse response = new RecaptchaResponse();
            RecaptchaResponse.TokenProperties props = new RecaptchaResponse.TokenProperties();
            props.setValid(true);
            props.setHostname("example.com");
            props.setAction("login");
            props.setCreateTime("2024-01-01T00:00:00Z");

            // When
            response.setTokenProperties(props);

            // Then
            assertThat(response.getTokenProperties().isValid()).isTrue();
            assertThat(response.getTokenProperties().getHostname()).isEqualTo("example.com");
            assertThat(response.getTokenProperties().getAction()).isEqualTo("login");
            assertThat(response.getTokenProperties().getCreateTime()).isEqualTo("2024-01-01T00:00:00Z");
        }

        @Test
        @DisplayName("should set and get risk analysis")
        void shouldSetAndGetRiskAnalysis() {
            // Given
            RecaptchaResponse response = new RecaptchaResponse();
            RecaptchaResponse.RiskAnalysis risk = new RecaptchaResponse.RiskAnalysis();
            risk.setScore(0.9);
            risk.setReasons(new String[]{"AUTOMATION", "LOW_CONFIDENCE_SCORE"});

            // When
            response.setRiskAnalysis(risk);

            // Then
            assertThat(response.getRiskAnalysis().getScore()).isEqualTo(0.9);
            assertThat(response.getRiskAnalysis().getReasons()).containsExactly("AUTOMATION", "LOW_CONFIDENCE_SCORE");
        }

        @Test
        @DisplayName("should handle invalid token")
        void shouldHandleInvalidToken() {
            // Given
            RecaptchaResponse response = new RecaptchaResponse();
            RecaptchaResponse.TokenProperties props = new RecaptchaResponse.TokenProperties();
            props.setValid(false);
            props.setInvalidReason("EXPIRED");
            response.setTokenProperties(props);

            // Then
            assertThat(response.getTokenProperties().isValid()).isFalse();
            assertThat(response.getTokenProperties().getInvalidReason()).isEqualTo("EXPIRED");
        }

        @Test
        @DisplayName("should set name and event")
        void shouldSetNameAndEvent() {
            // Given
            RecaptchaResponse response = new RecaptchaResponse();
            response.setName("projects/123/assessments/456");
            response.setEvent(new Object());

            // Then
            assertThat(response.getName()).isEqualTo("projects/123/assessments/456");
            assertThat(response.getEvent()).isNotNull();
        }
    }

    // ==================== PasswordChangeRequest Tests ====================

    @Nested
    @DisplayName("PasswordChangeRequest")
    class PasswordChangeRequestTests {

        @Test
        @DisplayName("should pass validation for valid request")
        void shouldPassValidationForValidRequest() {
            // Given
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword("oldPassword123")
                    .newPassword("newPassword456")
                    .build();

            // When
            Set<ConstraintViolation<PasswordChangeRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @DisplayName("should fail validation for blank current password")
        void shouldFailValidationForBlankCurrentPassword(String password) {
            // Given
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword(password)
                    .newPassword("newPassword456")
                    .build();

            // When
            Set<ConstraintViolation<PasswordChangeRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @DisplayName("should fail validation for blank new password")
        void shouldFailValidationForBlankNewPassword(String password) {
            // Given
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword("oldPassword123")
                    .newPassword(password)
                    .build();

            // When
            Set<ConstraintViolation<PasswordChangeRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== TotpSetupResponse Tests ====================

    @Nested
    @DisplayName("TotpSetupResponse")
    class TotpSetupResponseTests {

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            // Given
            TotpSetupResponse response = TotpSetupResponse.builder()
                    .qrCodeUrl("otpauth://totp/App:user@example.com")
                    .qrCodeImage("base64-image-data")
                    .googleChartsUrl("https://quickchart.io/qr?text=...")
                    .backupCodes(Arrays.asList("ABC123", "DEF456", "GHI789"))
                    .secret("JBSWY3DPEHPK3PXP")
                    .secretFormatted("JBSW Y3DP EHPK 3PXP")
                    .issuer("MyApp")
                    .email("user@example.com")
                    .alreadyEnabled(false)
                    .build();

            // Then
            assertThat(response.getQrCodeUrl()).isEqualTo("otpauth://totp/App:user@example.com");
            assertThat(response.getQrCodeImage()).isEqualTo("base64-image-data");
            assertThat(response.getGoogleChartsUrl()).isEqualTo("https://quickchart.io/qr?text=...");
            assertThat(response.getBackupCodes()).hasSize(3);
            assertThat(response.getSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
            assertThat(response.getSecretFormatted()).isEqualTo("JBSW Y3DP EHPK 3PXP");
            assertThat(response.getIssuer()).isEqualTo("MyApp");
            assertThat(response.getEmail()).isEqualTo("user@example.com");
            assertThat(response.isAlreadyEnabled()).isFalse();
        }

        @Test
        @DisplayName("should handle error state")
        void shouldHandleErrorState() {
            // Given
            TotpSetupResponse response = TotpSetupResponse.builder()
                    .error("Failed to generate TOTP secret")
                    .build();

            // Then
            assertThat(response.getError()).isEqualTo("Failed to generate TOTP secret");
            assertThat(response.getQrCodeUrl()).isNull();
        }

        @Test
        @DisplayName("should handle already enabled state")
        void shouldHandleAlreadyEnabledState() {
            // Given
            TotpSetupResponse response = TotpSetupResponse.builder()
                    .alreadyEnabled(true)
                    .email("user@example.com")
                    .build();

            // Then
            assertThat(response.isAlreadyEnabled()).isTrue();
        }
    }

    // ==================== TotpValidateRequest Tests ====================

    @Nested
    @DisplayName("TotpValidateRequest")
    class TotpValidateRequestTests {

        @Test
        @DisplayName("should pass validation for valid request")
        void shouldPassValidationForValidRequest() {
            // Given
            TotpValidateRequest request = new TotpValidateRequest();
            request.setUserId("user-123");
            request.setCode("123456");

            // When
            Set<ConstraintViolation<TotpValidateRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @DisplayName("should fail validation for blank userId")
        void shouldFailValidationForBlankUserId(String userId) {
            // Given
            TotpValidateRequest request = new TotpValidateRequest();
            request.setUserId(userId);
            request.setCode("123456");

            // When
            Set<ConstraintViolation<TotpValidateRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for null code")
        void shouldFailValidationForNullCode() {
            // Given
            TotpValidateRequest request = new TotpValidateRequest();
            request.setUserId("user-123");
            request.setCode(null);

            // When
            Set<ConstraintViolation<TotpValidateRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"000000", "000001", "123456", "999999"})
        @DisplayName("should accept various TOTP codes")
        void shouldAcceptVariousTotpCodes(String code) {
            // Given
            TotpValidateRequest request = new TotpValidateRequest();
            request.setUserId("user-123");
            request.setCode(code);

            // When
            Set<ConstraintViolation<TotpValidateRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    // ==================== SocialSignInRequest Tests ====================

    @Nested
    @DisplayName("SocialSignInRequest")
    class SocialSignInRequestTests {

        @Test
        @DisplayName("should pass validation for valid request")
        void shouldPassValidationForValidRequest() {
            // Given
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode("auth-code-123");

            // When
            Set<ConstraintViolation<SocialSignInRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @DisplayName("should fail validation for blank platformName")
        void shouldFailValidationForBlankPlatformName(String platformName) {
            // Given
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName(platformName);
            request.setAuthCode("auth-code-123");

            // When
            Set<ConstraintViolation<SocialSignInRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @DisplayName("should fail validation for blank authCode")
        void shouldFailValidationForBlankAuthCode(String authCode) {
            // Given
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("Instagram");
            request.setAuthCode(authCode);

            // When
            Set<ConstraintViolation<SocialSignInRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== SocialSignInResponse Tests ====================

    @Nested
    @DisplayName("SocialSignInResponse")
    class SocialSignInResponseTests {

        @Test
        @DisplayName("should set all fields")
        void shouldSetAllFields() {
            // Given
            SocialSignInResponse response = new SocialSignInResponse();
            response.setSuccess(true);
            response.setUserExists(true);
            response.setFirebaseUserId("firebase-uid");
            response.setCustomToken("custom-token-123");
            response.setUserId(123L);

            Map<String, Object> socialData = new HashMap<>();
            socialData.put("username", "test_user");
            socialData.put("followers", 10000);
            response.setSocialUserData(socialData);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.isUserExists()).isTrue();
            assertThat(response.getFirebaseUserId()).isEqualTo("firebase-uid");
            assertThat(response.getCustomToken()).isEqualTo("custom-token-123");
            assertThat(response.getUserId()).isEqualTo(123L);
            assertThat(response.getSocialUserData()).containsEntry("username", "test_user");
        }

        @Test
        @DisplayName("should handle error state")
        void shouldHandleErrorState() {
            // Given
            SocialSignInResponse response = new SocialSignInResponse();
            response.setSuccess(false);
            response.setErrorMessage("Invalid auth code");

            // Then
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorMessage()).isEqualTo("Invalid auth code");
        }
    }

    // ==================== TokenExchangeRequest Tests ====================

    @Nested
    @DisplayName("TokenExchangeRequest")
    class TokenExchangeRequestTests {

        @Test
        @DisplayName("should have default expiration days of 7")
        void shouldHaveDefaultExpirationDays() {
            // Given
            TokenExchangeRequest request = TokenExchangeRequest.builder()
                    .build();

            // Then
            assertThat(request.getExpirationDays()).isEqualTo(7);
        }

        @Test
        @DisplayName("should allow setting idToken")
        void shouldAllowSettingIdToken() {
            // Given
            TokenExchangeRequest request = TokenExchangeRequest.builder()
                    .idToken("id-token-123")
                    .build();

            // Then
            assertThat(request.getIdToken()).isEqualTo("id-token-123");
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 7, 14, 30})
        @DisplayName("should accept valid expiration days")
        void shouldAcceptValidExpirationDays(int days) {
            // Given
            TokenExchangeRequest request = TokenExchangeRequest.builder()
                    .expirationDays(days)
                    .build();

            // When
            Set<ConstraintViolation<TokenExchangeRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isEmpty();
            assertThat(request.getExpirationDays()).isEqualTo(days);
        }

        @Test
        @DisplayName("should fail validation for expiration days less than 1")
        void shouldFailValidationForTooFewDays() {
            // Given
            TokenExchangeRequest request = TokenExchangeRequest.builder()
                    .expirationDays(0)
                    .build();

            // When
            Set<ConstraintViolation<TokenExchangeRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for expiration days more than 30")
        void shouldFailValidationForTooManyDays() {
            // Given
            TokenExchangeRequest request = TokenExchangeRequest.builder()
                    .expirationDays(31)
                    .build();

            // When
            Set<ConstraintViolation<TokenExchangeRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== TokenExchangeResponse Tests ====================

    @Nested
    @DisplayName("TokenExchangeResponse")
    class TokenExchangeResponseTests {

        @Test
        @DisplayName("should create error response")
        void shouldCreateErrorResponse() {
            // When
            TokenExchangeResponse response = TokenExchangeResponse.error("Invalid token");

            // Then
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Invalid token");
        }

        @Test
        @DisplayName("should create success response")
        void shouldCreateSuccessResponse() {
            // When
            TokenExchangeResponse response = TokenExchangeResponse.success(
                    123L, "user@example.com", "INFLUENCER", "firebase-uid"
            );

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getUserId()).isEqualTo(123L);
            assertThat(response.getEmail()).isEqualTo("user@example.com");
            assertThat(response.getRole()).isEqualTo("INFLUENCER");
            assertThat(response.getFirebaseUid()).isEqualTo("firebase-uid");
        }

        @Test
        @DisplayName("should build with 2FA fields")
        void shouldBuildWith2FAFields() {
            // Given
            TokenExchangeResponse response = TokenExchangeResponse.builder()
                    .success(true)
                    .userId(123L)
                    .requires2FA(true)
                    .requires2FASetup(false)
                    .twoFactorEnabled(true)
                    .twoFactorVerified(false)
                    .build();

            // Then
            assertThat(response.getRequires2FA()).isTrue();
            assertThat(response.getRequires2FASetup()).isFalse();
            assertThat(response.getTwoFactorEnabled()).isTrue();
            assertThat(response.getTwoFactorVerified()).isFalse();
        }

        @Test
        @DisplayName("should build with cookie information")
        void shouldBuildWithCookieInfo() {
            // Given
            TokenExchangeResponse response = TokenExchangeResponse.builder()
                    .success(true)
                    .cookieType("FULL")
                    .sessionDuration("7 days")
                    .build();

            // Then
            assertThat(response.getCookieType()).isEqualTo("FULL");
            assertThat(response.getSessionDuration()).isEqualTo("7 days");
        }

        @ParameterizedTest
        @CsvSource({
                "PARTIAL, 10 minutes",
                "FULL, 7 days",
                "FULL, 30 days"
        })
        @DisplayName("should accept various cookie configurations")
        void shouldAcceptVariousCookieConfigs(String cookieType, String sessionDuration) {
            // Given
            TokenExchangeResponse response = TokenExchangeResponse.builder()
                    .success(true)
                    .cookieType(cookieType)
                    .sessionDuration(sessionDuration)
                    .build();

            // Then
            assertThat(response.getCookieType()).isEqualTo(cookieType);
            assertThat(response.getSessionDuration()).isEqualTo(sessionDuration);
        }
    }

    // ==================== ApplyActionCodeRequest Tests ====================

    @Nested
    @DisplayName("ApplyActionCodeRequest")
    class ApplyActionCodeRequestTests {

        @Test
        @DisplayName("should pass validation for valid oobCode")
        void valid_oobCode_noViolations() {
            ApplyActionCodeRequest request = ApplyActionCodeRequest.builder()
                    .oobCode("valid-oob-code-123")
                    .build();

            Set<ConstraintViolation<ApplyActionCodeRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation for null oobCode")
        void null_oobCode_hasViolation() {
            ApplyActionCodeRequest request = ApplyActionCodeRequest.builder()
                    .oobCode(null)
                    .build();

            Set<ConstraintViolation<ApplyActionCodeRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("oobCode"));
        }

        @Test
        @DisplayName("should fail validation for blank oobCode")
        void blank_oobCode_hasViolation() {
            ApplyActionCodeRequest request = ApplyActionCodeRequest.builder()
                    .oobCode("")
                    .build();

            Set<ConstraintViolation<ApplyActionCodeRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("oobCode"));
        }
    }

    // ==================== VerifyResetCodeRequest Tests ====================

    @Nested
    @DisplayName("VerifyResetCodeRequest")
    class VerifyResetCodeRequestTests {

        @Test
        @DisplayName("should pass validation for valid oobCode")
        void valid_oobCode_noViolations() {
            VerifyResetCodeRequest request = VerifyResetCodeRequest.builder()
                    .oobCode("valid-oob-code-456")
                    .build();

            Set<ConstraintViolation<VerifyResetCodeRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation for null oobCode")
        void null_oobCode_hasViolation() {
            VerifyResetCodeRequest request = VerifyResetCodeRequest.builder()
                    .oobCode(null)
                    .build();

            Set<ConstraintViolation<VerifyResetCodeRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("oobCode"));
        }

        @Test
        @DisplayName("should fail validation for blank oobCode")
        void blank_oobCode_hasViolation() {
            VerifyResetCodeRequest request = VerifyResetCodeRequest.builder()
                    .oobCode("")
                    .build();

            Set<ConstraintViolation<VerifyResetCodeRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("oobCode"));
        }
    }

    // ==================== ConfirmPasswordResetRequest Tests ====================

    @Nested
    @DisplayName("ConfirmPasswordResetRequest")
    class ConfirmPasswordResetRequestTests {

        @Test
        @DisplayName("should pass validation for valid request")
        void valid_request_noViolations() {
            ConfirmPasswordResetRequest request = ConfirmPasswordResetRequest.builder()
                    .oobCode("valid-oob-code")
                    .newPassword("Password1")
                    .build();

            Set<ConstraintViolation<ConfirmPasswordResetRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation for null oobCode")
        void null_oobCode_hasViolation() {
            ConfirmPasswordResetRequest request = ConfirmPasswordResetRequest.builder()
                    .oobCode(null)
                    .newPassword("Password1")
                    .build();

            Set<ConstraintViolation<ConfirmPasswordResetRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("oobCode"));
        }

        @Test
        @DisplayName("should fail validation for blank oobCode")
        void blank_oobCode_hasViolation() {
            ConfirmPasswordResetRequest request = ConfirmPasswordResetRequest.builder()
                    .oobCode("")
                    .newPassword("Password1")
                    .build();

            Set<ConstraintViolation<ConfirmPasswordResetRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("oobCode"));
        }

        @Test
        @DisplayName("should fail validation for null newPassword")
        void null_newPassword_hasViolation() {
            ConfirmPasswordResetRequest request = ConfirmPasswordResetRequest.builder()
                    .oobCode("valid-oob-code")
                    .newPassword(null)
                    .build();

            Set<ConstraintViolation<ConfirmPasswordResetRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
        }

        @Test
        @DisplayName("should fail validation for too short newPassword")
        void short_newPassword_hasViolation() {
            ConfirmPasswordResetRequest request = ConfirmPasswordResetRequest.builder()
                    .oobCode("valid-oob-code")
                    .newPassword("Pass1a")
                    .build();

            Set<ConstraintViolation<ConfirmPasswordResetRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
        }

        @Test
        @DisplayName("should fail validation for password without digit")
        void noDigit_newPassword_hasViolation() {
            ConfirmPasswordResetRequest request = ConfirmPasswordResetRequest.builder()
                    .oobCode("valid-oob-code")
                    .newPassword("abcdefgh")
                    .build();

            Set<ConstraintViolation<ConfirmPasswordResetRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
        }

        @Test
        @DisplayName("should fail validation for password without letter")
        void noLetter_newPassword_hasViolation() {
            ConfirmPasswordResetRequest request = ConfirmPasswordResetRequest.builder()
                    .oobCode("valid-oob-code")
                    .newPassword("12345678")
                    .build();

            Set<ConstraintViolation<ConfirmPasswordResetRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
        }
    }
}
