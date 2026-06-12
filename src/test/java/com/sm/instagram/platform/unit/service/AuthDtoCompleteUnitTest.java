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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Comprehensive unit tests for Auth DTOs.
 * Covers builders, validation annotations, edge cases, equals/hashCode, toString, and factory methods.
 * Targets DTOs not fully covered by existing test files.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Auth DTO Complete Unit Tests")
class AuthDtoCompleteUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== AssessmentResult Complete Tests ====================

    @Nested
    @DisplayName("AssessmentResult Complete Tests")
    class AssessmentResultCompleteTests {

        @Test
        @DisplayName("success factory should set correct defaults")
        void successFactoryShouldSetCorrectDefaults() {
            AssessmentResult result = AssessmentResult.success(0.95);

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getScore()).isEqualTo(0.95);
            assertThat(result.getReason()).isEqualTo("Valid assessment");
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("blocked factory should set correct values")
        void blockedFactoryShouldSetCorrectValues() {
            AssessmentResult result = AssessmentResult.blocked(0.15, "Suspicious activity detected");

            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getScore()).isEqualTo(0.15);
            assertThat(result.getReason()).isEqualTo("Suspicious activity detected");
            assertThat(result.getStatus()).isEqualTo("BLOCKED");
        }

        @Test
        @DisplayName("invalid factory should set score to zero")
        void invalidFactoryShouldSetScoreToZero() {
            AssessmentResult result = AssessmentResult.invalid("Token malformed");

            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getScore()).isEqualTo(0.0);
            assertThat(result.getReason()).isEqualTo("Token malformed");
            assertThat(result.getStatus()).isEqualTo("INVALID");
        }

        @Test
        @DisplayName("allowed factory should set score to one")
        void allowedFactoryShouldSetScoreToOne() {
            AssessmentResult result = AssessmentResult.allowed("Trusted IP");

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getScore()).isEqualTo(1.0);
            assertThat(result.getReason()).isEqualTo("Trusted IP");
            assertThat(result.getStatus()).isEqualTo("ALLOWED");
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, 0.25, 0.5, 0.75, 1.0})
        @DisplayName("should handle boundary scores correctly")
        void shouldHandleBoundaryScoresCorrectly(double score) {
            AssessmentResult result = AssessmentResult.success(score);
            assertThat(result.getScore()).isEqualTo(score);
        }

        @Test
        @DisplayName("toString should contain all relevant fields")
        void toStringShouldContainAllRelevantFields() {
            AssessmentResult result = AssessmentResult.blocked(0.3, "Low confidence");

            String str = result.toString();
            assertThat(str).contains("BLOCKED");
            assertThat(str).contains("0.30");
            assertThat(str).contains("false");
            assertThat(str).contains("Low confidence");
        }

        @Test
        @DisplayName("should handle null reason in blocked")
        void shouldHandleNullReasonInBlocked() {
            AssessmentResult result = AssessmentResult.blocked(0.1, null);

            assertThat(result.getReason()).isNull();
            assertThat(result.isAllowed()).isFalse();
        }

        @Test
        @DisplayName("should handle empty reason in blocked")
        void shouldHandleEmptyReasonInBlocked() {
            AssessmentResult result = AssessmentResult.blocked(0.1, "");

            assertThat(result.getReason()).isEmpty();
        }

        @ParameterizedTest
        @CsvSource({
                "0.0, false",
                "0.3, false",
                "0.5, false",
                "0.7, true",
                "1.0, true"
        })
        @DisplayName("should correctly represent threshold scenarios")
        void shouldCorrectlyRepresentThresholdScenarios(double score, boolean expectedAllowed) {
            AssessmentResult result = expectedAllowed
                ? AssessmentResult.success(score)
                : AssessmentResult.blocked(score, "Below threshold");

            assertThat(result.isAllowed()).isEqualTo(expectedAllowed);
        }
    }

    // ==================== BackupCodeRequest Complete Tests ====================

    @Nested
    @DisplayName("BackupCodeRequest Complete Tests")
    class BackupCodeRequestCompleteTests {

        @Test
        @DisplayName("should pass validation with valid uppercase alphanumeric 8-char code")
        void shouldPassValidationWithValidCode() {
            BackupCodeRequest request = new BackupCodeRequest();
            request.setUserId("firebase-uid-123");
            request.setBackupCode("ABCD1234");

            Set<ConstraintViolation<BackupCodeRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"AAAAAAAA", "12345678", "A1B2C3D4", "ZZZZ9999", "00000000"})
        @DisplayName("should accept valid 8-character uppercase alphanumeric codes")
        void shouldAcceptValidCodes(String code) {
            BackupCodeRequest request = new BackupCodeRequest();
            request.setUserId("user123");
            request.setBackupCode(code);

            Set<ConstraintViolation<BackupCodeRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"abcd1234", "ABCD123", "ABCD12345", "ABCD-123", "ABCD 123", "abcdefgh"})
        @DisplayName("should reject invalid backup code patterns")
        void shouldRejectInvalidPatterns(String code) {
            BackupCodeRequest request = new BackupCodeRequest();
            request.setUserId("user123");
            request.setBackupCode(code);

            Set<ConstraintViolation<BackupCodeRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("backupCode"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail validation when userId is null or empty")
        void shouldFailWhenUserIdIsNullOrEmpty(String userId) {
            BackupCodeRequest request = new BackupCodeRequest();
            request.setUserId(userId);
            request.setBackupCode("ABCD1234");

            Set<ConstraintViolation<BackupCodeRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("userId"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail validation when backupCode is null or empty")
        void shouldFailWhenBackupCodeIsNullOrEmpty(String code) {
            BackupCodeRequest request = new BackupCodeRequest();
            request.setUserId("user123");
            request.setBackupCode(code);

            Set<ConstraintViolation<BackupCodeRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("backupCode"));
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            BackupCodeRequest request1 = new BackupCodeRequest();
            request1.setUserId("user123");
            request1.setBackupCode("ABCD1234");

            BackupCodeRequest request2 = new BackupCodeRequest();
            request2.setUserId("user123");
            request2.setBackupCode("ABCD1234");

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            BackupCodeRequest request = new BackupCodeRequest();
            request.setUserId("user-456");
            request.setBackupCode("XYZ12345");

            String str = request.toString();
            assertThat(str).contains("userId=user-456");
            assertThat(str).contains("backupCode=XYZ12345");
        }
    }

    // ==================== EnableTotpRequest Complete Tests ====================

    @Nested
    @DisplayName("EnableTotpRequest Complete Tests")
    class EnableTotpRequestCompleteTests {

        @Test
        @DisplayName("should pass validation with valid 6-digit code")
        void shouldPassValidationWithValidCode() {
            EnableTotpRequest request = new EnableTotpRequest();
            request.setVerificationCode("123456");

            Set<ConstraintViolation<EnableTotpRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when verificationCode is null")
        void shouldFailWhenVerificationCodeIsNull() {
            EnableTotpRequest request = new EnableTotpRequest();
            request.setVerificationCode(null);

            Set<ConstraintViolation<EnableTotpRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"000000", "000001", "100000", "500000", "999999"})
        @DisplayName("should accept valid TOTP code ranges")
        void shouldAcceptValidTotpCodeRanges(String code) {
            EnableTotpRequest request = new EnableTotpRequest();
            request.setVerificationCode(code);

            Set<ConstraintViolation<EnableTotpRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            EnableTotpRequest request1 = new EnableTotpRequest();
            request1.setVerificationCode("123456");

            EnableTotpRequest request2 = new EnableTotpRequest();
            request2.setVerificationCode("123456");

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            EnableTotpRequest request = new EnableTotpRequest();
            request.setVerificationCode("654321");

            String str = request.toString();
            assertThat(str).contains("verificationCode=654321");
        }
    }

    // ==================== ExchangeTokenRequest Complete Tests ====================

    @Nested
    @DisplayName("ExchangeTokenRequest Complete Tests")
    class ExchangeTokenRequestCompleteTests {

        @Test
        @DisplayName("should pass validation with valid idToken")
        void shouldPassValidationWithValidIdToken() {
            ExchangeTokenRequest request = new ExchangeTokenRequest();
            request.setIdToken("valid.firebase.id.token");

            Set<ConstraintViolation<ExchangeTokenRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t", "\n"})
        @DisplayName("should fail validation when idToken is blank")
        void shouldFailWhenIdTokenIsBlank(String token) {
            ExchangeTokenRequest request = new ExchangeTokenRequest();
            request.setIdToken(token);

            Set<ConstraintViolation<ExchangeTokenRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("idToken"));
        }

        @Test
        @DisplayName("should have default expirationDays of 7")
        void shouldHaveDefaultExpirationDays() {
            ExchangeTokenRequest request = new ExchangeTokenRequest();
            assertThat(request.getExpirationDays()).isEqualTo(7);
        }

        @Test
        @DisplayName("should calculate expirationHours correctly from days")
        void shouldCalculateExpirationHoursCorrectly() {
            ExchangeTokenRequest request = new ExchangeTokenRequest();
            request.setExpirationDays(14);

            assertThat(request.getExpirationHours()).isEqualTo(336);
        }

        @Test
        @DisplayName("should return default 168 hours when expirationDays is null")
        void shouldReturnDefaultHoursWhenDaysNull() {
            ExchangeTokenRequest request = new ExchangeTokenRequest();
            request.setExpirationDays(null);

            assertThat(request.getExpirationHours()).isEqualTo(168);
        }

        @ParameterizedTest
        @CsvSource({
                "1, 24",
                "7, 168",
                "14, 336",
                "30, 720"
        })
        @DisplayName("should correctly convert days to hours")
        void shouldCorrectlyConvertDaysToHours(int days, int expectedHours) {
            ExchangeTokenRequest request = new ExchangeTokenRequest();
            request.setExpirationDays(days);

            assertThat(request.getExpirationHours()).isEqualTo(expectedHours);
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            ExchangeTokenRequest request1 = new ExchangeTokenRequest();
            request1.setIdToken("token123");
            request1.setExpirationDays(7);

            ExchangeTokenRequest request2 = new ExchangeTokenRequest();
            request2.setIdToken("token123");
            request2.setExpirationDays(7);

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            ExchangeTokenRequest request = new ExchangeTokenRequest();
            request.setIdToken("my-token");
            request.setExpirationDays(10);

            String str = request.toString();
            assertThat(str).contains("idToken=my-token");
            assertThat(str).contains("expirationDays=10");
        }
    }

    // ==================== FirebaseLoginRequest Complete Tests ====================

    @Nested
    @DisplayName("FirebaseLoginRequest Complete Tests")
    class FirebaseLoginRequestCompleteTests {

        @Test
        @DisplayName("should pass validation with valid firebaseToken")
        void shouldPassValidationWithValidToken() {
            FirebaseLoginRequest request = new FirebaseLoginRequest();
            request.setFirebaseToken("valid.firebase.token");

            Set<ConstraintViolation<FirebaseLoginRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("should fail validation when firebaseToken is blank")
        void shouldFailWhenTokenIsBlank(String token) {
            FirebaseLoginRequest request = new FirebaseLoginRequest();
            request.setFirebaseToken(token);

            Set<ConstraintViolation<FirebaseLoginRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("firebaseToken"));
        }

        @Test
        @DisplayName("should have default expirationHours of 8")
        void shouldHaveDefaultExpirationHours() {
            FirebaseLoginRequest request = new FirebaseLoginRequest();
            assertThat(request.getExpirationHours()).isEqualTo(8);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 8, 24, 72, 168})
        @DisplayName("should accept various expiration hours")
        void shouldAcceptVariousExpirationHours(int hours) {
            FirebaseLoginRequest request = new FirebaseLoginRequest();
            request.setFirebaseToken("token");
            request.setExpirationHours(hours);

            assertThat(request.getExpirationHours()).isEqualTo(hours);
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            FirebaseLoginRequest request1 = new FirebaseLoginRequest();
            request1.setFirebaseToken("token123");
            request1.setExpirationHours(8);

            FirebaseLoginRequest request2 = new FirebaseLoginRequest();
            request2.setFirebaseToken("token123");
            request2.setExpirationHours(8);

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }

        @Test
        @DisplayName("should generate proper toString")
        void shouldGenerateProperToString() {
            FirebaseLoginRequest request = new FirebaseLoginRequest();
            request.setFirebaseToken("my-firebase-token");
            request.setExpirationHours(24);

            String str = request.toString();
            assertThat(str).contains("firebaseToken=my-firebase-token");
            assertThat(str).contains("expirationHours=24");
        }
    }

    // ==================== RegisterInfluencerRequest Complete Tests ====================

    @Nested
    @DisplayName("RegisterInfluencerRequest Complete Tests")
    class RegisterInfluencerRequestCompleteTests {

        @Test
        @DisplayName("should pass validation with all required fields")
        void shouldPassValidationWithRequiredFields() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("influencer@example.com");
            request.setPlatformName("instagram");

            Set<ConstraintViolation<RegisterInfluencerRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"test@example.com", "user.name@domain.co.uk", "influencer+tag@gmail.com"})
        @DisplayName("should accept various valid email formats")
        void shouldAcceptVariousEmailFormats(String email) {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail(email);
            request.setPlatformName("instagram");

            Set<ConstraintViolation<RegisterInfluencerRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"instagram", "tiktok", "youtube", "facebook", "twitter"})
        @DisplayName("should accept various platform names")
        void shouldAcceptVariousPlatformNames(String platform) {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName(platform);

            Set<ConstraintViolation<RegisterInfluencerRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should accept social user data with various types")
        void shouldAcceptSocialUserDataWithVariousTypes() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName("instagram");

            Map<String, Object> socialData = new HashMap<>();
            socialData.put("userId", "12345");
            socialData.put("username", "testuser");
            socialData.put("followers", 10000);
            socialData.put("verified", true);
            socialData.put("profileUrl", "https://instagram.com/testuser");
            request.setSocialUserData(socialData);

            assertThat(request.getSocialUserData()).hasSize(5);
            assertThat(request.getSocialUserData()).containsEntry("followers", 10000);
            assertThat(request.getSocialUserData()).containsEntry("verified", true);
        }

        @Test
        @DisplayName("should handle null socialUserData gracefully")
        void shouldHandleNullSocialUserData() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName("instagram");
            request.setSocialUserData(null);

            Set<ConstraintViolation<RegisterInfluencerRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
            assertThat(request.getSocialUserData()).isNull();
        }

        @Test
        @DisplayName("should handle empty socialUserData map")
        void shouldHandleEmptySocialUserDataMap() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("test@example.com");
            request.setPlatformName("instagram");
            request.setSocialUserData(new HashMap<>());

            assertThat(request.getSocialUserData()).isEmpty();
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            RegisterInfluencerRequest request1 = new RegisterInfluencerRequest();
            request1.setEmail("test@example.com");
            request1.setPlatformName("instagram");

            RegisterInfluencerRequest request2 = new RegisterInfluencerRequest();
            request2.setEmail("test@example.com");
            request2.setPlatformName("instagram");

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }
    }

    // ==================== TotpVerifyRequest Complete Tests ====================

    @Nested
    @DisplayName("TotpVerifyRequest Complete Tests")
    class TotpVerifyRequestCompleteTests {

        @Test
        @DisplayName("should pass validation with valid code in range")
        void shouldPassValidationWithValidCode() {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with single digit code")
        void shouldPassWithSingleDigitCode() {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("000000");

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with maximum 6-digit code")
        void shouldPassWithMaximumCode() {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("999999");

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when code is null")
        void shouldFailWhenCodeIsNull() {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode(null);

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
        }

        @Test
        @DisplayName("should fail validation when code contains non-digit characters")
        void shouldFailWhenCodeContainsNonDigits() {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("-1");

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
        }

        @Test
        @DisplayName("should fail validation when code exceeds 6 digits")
        void shouldFailWhenCodeExceedsMaxLength() {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("1234567");

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"000000", "000001", "100000", "500000", "999999"})
        @DisplayName("should accept typical TOTP codes")
        void shouldAcceptTypicalTotpCodes(String code) {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode(code);

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            TotpVerifyRequest request1 = new TotpVerifyRequest();
            request1.setCode("123456");

            TotpVerifyRequest request2 = new TotpVerifyRequest();
            request2.setCode("123456");

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }
    }

    // ==================== RegisterUserRequest Complete Tests ====================

    @Nested
    @DisplayName("RegisterUserRequest Complete Tests")
    class RegisterUserRequestCompleteTests {

        @Test
        @DisplayName("should pass validation for complete COMPANY user")
        void shouldPassValidationForCompleteCompanyUser() {
            RegisterUserRequest request = createValidCompanyRequest();

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation for minimal INFLUENCER user")
        void shouldPassValidationForMinimalInfluencerUser() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("influencer@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail for COMPANY without company name")
        void shouldFailForCompanyWithoutName() {
            RegisterUserRequest request = createValidCompanyRequest();
            request.setCompanyName(null);

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("validCompanyData"));
        }

        @Test
        @DisplayName("should fail for COMPANY without required address fields")
        void shouldFailForCompanyWithoutAddress() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("company@example.com");
            request.setPassword("Password1!");
            request.setUserType("COMPANY");
            request.setCompanyName("Test Company");
            // Missing address fields

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("validAddressData"));
        }

        @Test
        @DisplayName("should fail for INFLUENCER with platform but no auth code")
        void shouldFailForInfluencerWithPlatformNoAuthCode() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("influencer@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");
            request.setSocialPlatform("Instagram");
            // Missing socialAuthCode

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("validSocialData"));
        }

        @Test
        @DisplayName("should pass for INFLUENCER with both platform and auth code")
        void shouldPassForInfluencerWithBothPlatformAndAuthCode() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("influencer@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");
            request.setSocialPlatform("Instagram");
            request.setSocialAuthCode("auth-code-123");

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"+48123456789", "123-456-7890", "(123)456-7890", "+1-800-555-1234", "0048505123456"})
        @DisplayName("should accept valid phone number formats")
        void shouldAcceptValidPhoneFormats(String phone) {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");
            request.setPhoneNumber(phone);

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("phoneNumber"))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"invalid", "12345", "abcdefghij"})
        @DisplayName("should reject invalid phone number formats")
        void shouldRejectInvalidPhoneFormats(String phone) {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");
            request.setPhoneNumber(phone);

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phoneNumber"));
        }

        @Test
        @DisplayName("should fail for password shorter than 6 characters")
        void shouldFailForShortPassword() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("test@example.com");
            request.setPassword("12345");
            request.setUserType("INFLUENCER");

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"ADMIN", "USER", "GUEST", "invalid", ""})
        @DisplayName("should fail for invalid userType values")
        void shouldFailForInvalidUserType(String userType) {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password1!");
            request.setUserType(userType);

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should accept valid HTTPS profile picture URL")
        void shouldAcceptValidHttpsProfilePictureUrl() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");
            request.setProfilePictureUrl("https://example.com/profile.jpg");

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("profilePictureUrl"))).isTrue();
        }

        @Test
        @DisplayName("should set and get all optional fields")
        void shouldSetAndGetAllOptionalFields() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("test@example.com");
            request.setPassword("Password1!");
            request.setUserType("COMPANY");
            request.setFirstName("John");
            request.setLastName("Doe");
            request.setCompanyName("Test Corp");
            request.setPhoneNumber("+1234567890");
            request.setAddressStreet("123 Main St");
            request.setAddressCity("New York");
            request.setAddressPostalCode("10001");
            request.setAddressCountry("USA");
            request.setAddressState("NY");
            request.setAddressInfo("Suite 100");

            assertThat(request.getFirstName()).isEqualTo("John");
            assertThat(request.getLastName()).isEqualTo("Doe");
            assertThat(request.getAddressState()).isEqualTo("NY");
            assertThat(request.getAddressInfo()).isEqualTo("Suite 100");
        }

        private RegisterUserRequest createValidCompanyRequest() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("company@example.com");
            request.setPassword("Password1!");
            request.setUserType("COMPANY");
            request.setCompanyName("Test Company");
            request.setAddressStreet("123 Main St");
            request.setAddressCity("Warsaw");
            request.setAddressPostalCode("00-001");
            request.setAddressCountry("Poland");
            return request;
        }
    }

    // ==================== SocialConnectionRequest Complete Tests ====================

    @Nested
    @DisplayName("SocialConnectionRequest Complete Tests")
    class SocialConnectionRequestCompleteTests {

        @Test
        @DisplayName("should pass validation with valid data")
        void shouldPassValidationWithValidData() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("instagram");
            request.setAuthCode("valid-auth-code-123");

            Set<ConstraintViolation<SocialConnectionRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should have default setPrimary as true")
        void shouldHaveDefaultSetPrimaryAsTrue() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            assertThat(request.getSetPrimary()).isTrue();
        }

        @Test
        @DisplayName("should allow changing setPrimary to false")
        void shouldAllowChangingSetPrimaryToFalse() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setSetPrimary(false);
            assertThat(request.getSetPrimary()).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("should fail validation when platformName is blank")
        void shouldFailWhenPlatformNameIsBlank(String platform) {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName(platform);
            request.setAuthCode("valid-code");

            Set<ConstraintViolation<SocialConnectionRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("platformName"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("should fail validation when authCode is blank")
        void shouldFailWhenAuthCodeIsBlank(String code) {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("instagram");
            request.setAuthCode(code);

            Set<ConstraintViolation<SocialConnectionRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("authCode"));
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            SocialConnectionRequest request1 = new SocialConnectionRequest();
            request1.setPlatformName("instagram");
            request1.setAuthCode("code123");

            SocialConnectionRequest request2 = new SocialConnectionRequest();
            request2.setPlatformName("instagram");
            request2.setAuthCode("code123");

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }
    }

    // ==================== SocialConnectionResponse Complete Tests ====================

    @Nested
    @DisplayName("SocialConnectionResponse Complete Tests")
    class SocialConnectionResponseCompleteTests {

        @Test
        @DisplayName("should initialize with default values")
        void shouldInitializeWithDefaultValues() {
            SocialConnectionResponse response = new SocialConnectionResponse();

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getConnectionId()).isNull();
            assertThat(response.getPlatformName()).isNull();
            assertThat(response.getSocialUserId()).isNull();
            assertThat(response.getDisplayName()).isNull();
            assertThat(response.getFollowersCount()).isNull();
            assertThat(response.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("should set all fields correctly for success scenario")
        void shouldSetAllFieldsForSuccess() {
            SocialConnectionResponse response = new SocialConnectionResponse();
            response.setSuccess(true);
            response.setConnectionId(123L);
            response.setPlatformName("instagram");
            response.setSocialUserId("ig-12345");
            response.setDisplayName("Test Influencer");
            response.setFollowersCount(50000);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getConnectionId()).isEqualTo(123L);
            assertThat(response.getPlatformName()).isEqualTo("instagram");
            assertThat(response.getSocialUserId()).isEqualTo("ig-12345");
            assertThat(response.getDisplayName()).isEqualTo("Test Influencer");
            assertThat(response.getFollowersCount()).isEqualTo(50000);
        }

        @Test
        @DisplayName("should set error message for failure scenario")
        void shouldSetErrorMessageForFailure() {
            SocialConnectionResponse response = new SocialConnectionResponse();
            response.setSuccess(false);
            response.setErrorMessage("Invalid authorization code");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorMessage()).isEqualTo("Invalid authorization code");
        }

        @Test
        @DisplayName("should handle zero followers count")
        void shouldHandleZeroFollowersCount() {
            SocialConnectionResponse response = new SocialConnectionResponse();
            response.setFollowersCount(0);

            assertThat(response.getFollowersCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should handle large followers count")
        void shouldHandleLargeFollowersCount() {
            SocialConnectionResponse response = new SocialConnectionResponse();
            response.setFollowersCount(Integer.MAX_VALUE);

            assertThat(response.getFollowersCount()).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            SocialConnectionResponse response1 = new SocialConnectionResponse();
            response1.setSuccess(true);
            response1.setConnectionId(123L);

            SocialConnectionResponse response2 = new SocialConnectionResponse();
            response2.setSuccess(true);
            response2.setConnectionId(123L);

            assertThat(response1).isEqualTo(response2);
            assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
        }
    }

    // ==================== RegisterUserResponse Complete Tests ====================

    @Nested
    @DisplayName("RegisterUserResponse Complete Tests")
    class RegisterUserResponseCompleteTests {

        @Test
        @DisplayName("should initialize with default values")
        void shouldInitializeWithDefaultValues() {
            RegisterUserResponse response = new RegisterUserResponse();

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getFirebaseUserId()).isNull();
            assertThat(response.getUserId()).isNull();
            assertThat(response.getCustomToken()).isNull();
            assertThat(response.getSocialConnection()).isNull();
            assertThat(response.getSocialConnectionError()).isNull();
            assertThat(response.getError()).isNull();
        }

        @Test
        @DisplayName("should set all success fields correctly")
        void shouldSetAllSuccessFieldsCorrectly() {
            RegisterUserResponse response = new RegisterUserResponse();
            response.setSuccess(true);
            response.setFirebaseUserId("fb-uid-123");
            response.setUserId(456L);
            response.setCustomToken("custom.jwt.token");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getFirebaseUserId()).isEqualTo("fb-uid-123");
            assertThat(response.getUserId()).isEqualTo(456L);
            assertThat(response.getCustomToken()).isEqualTo("custom.jwt.token");
        }

        @Test
        @DisplayName("should include nested SocialConnectionResponse")
        void shouldIncludeNestedSocialConnectionResponse() {
            SocialConnectionResponse socialConn = new SocialConnectionResponse();
            socialConn.setSuccess(true);
            socialConn.setPlatformName("instagram");
            socialConn.setFollowersCount(10000);

            RegisterUserResponse response = new RegisterUserResponse();
            response.setSuccess(true);
            response.setSocialConnection(socialConn);

            assertThat(response.getSocialConnection()).isNotNull();
            assertThat(response.getSocialConnection().getPlatformName()).isEqualTo("instagram");
            assertThat(response.getSocialConnection().getFollowersCount()).isEqualTo(10000);
        }

        @Test
        @DisplayName("should handle registration success with social connection failure")
        void shouldHandleSuccessWithSocialConnectionFailure() {
            RegisterUserResponse response = new RegisterUserResponse();
            response.setSuccess(true);
            response.setFirebaseUserId("fb-uid-123");
            response.setUserId(456L);
            response.setSocialConnectionError("Failed to connect Instagram");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getSocialConnectionError()).isEqualTo("Failed to connect Instagram");
            assertThat(response.getSocialConnection()).isNull();
        }

        @Test
        @DisplayName("should set error message for registration failure")
        void shouldSetErrorMessageForRegistrationFailure() {
            RegisterUserResponse response = new RegisterUserResponse();
            response.setSuccess(false);
            response.setError("Email already registered");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Email already registered");
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            RegisterUserResponse response1 = new RegisterUserResponse();
            response1.setSuccess(true);
            response1.setUserId(123L);

            RegisterUserResponse response2 = new RegisterUserResponse();
            response2.setSuccess(true);
            response2.setUserId(123L);

            assertThat(response1).isEqualTo(response2);
            assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
        }
    }

    // ==================== RegistrationResponse Complete Tests ====================

    @Nested
    @DisplayName("RegistrationResponse Complete Tests")
    class RegistrationResponseCompleteTests {

        @Test
        @DisplayName("should create via builder with all fields")
        void shouldCreateViaBuilderWithAllFields() {
            RegistrationResponse response = RegistrationResponse.builder()
                    .success(true)
                    .customToken("jwt.token.here")
                    .userId(123L)
                    .firebaseUid("fb-uid-456")
                    .userType("INFLUENCER")
                    .build();

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getCustomToken()).isEqualTo("jwt.token.here");
            assertThat(response.getUserId()).isEqualTo(123L);
            assertThat(response.getFirebaseUid()).isEqualTo("fb-uid-456");
            assertThat(response.getUserType()).isEqualTo("INFLUENCER");
            assertThat(response.getError()).isNull();
        }

        @Test
        @DisplayName("error factory should create error response correctly")
        void errorFactoryShouldCreateErrorResponse() {
            RegistrationResponse response = RegistrationResponse.error("Registration failed");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Registration failed");
            assertThat(response.getCustomToken()).isNull();
            assertThat(response.getUserId()).isNull();
        }

        @Test
        @DisplayName("should use all-args constructor")
        void shouldUseAllArgsConstructor() {
            RegistrationResponse response = new RegistrationResponse(
                    true, "token", 1L, "uid", "COMPANY", null
            );

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getCustomToken()).isEqualTo("token");
            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getFirebaseUid()).isEqualTo("uid");
            assertThat(response.getUserType()).isEqualTo("COMPANY");
        }

        @Test
        @DisplayName("should use no-args constructor and setters")
        void shouldUseNoArgsConstructorAndSetters() {
            RegistrationResponse response = new RegistrationResponse();
            response.setSuccess(true);
            response.setUserType("ADMIN");
            response.setCustomToken("custom-token");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getUserType()).isEqualTo("ADMIN");
            assertThat(response.getCustomToken()).isEqualTo("custom-token");
        }

        @ParameterizedTest
        @ValueSource(strings = {"INFLUENCER", "COMPANY", "ADMIN"})
        @DisplayName("should accept various user types")
        void shouldAcceptVariousUserTypes(String userType) {
            RegistrationResponse response = RegistrationResponse.builder()
                    .success(true)
                    .userType(userType)
                    .build();

            assertThat(response.getUserType()).isEqualTo(userType);
        }

        @Test
        @DisplayName("should have proper equals and hashCode")
        void shouldHaveProperEqualsAndHashCode() {
            RegistrationResponse response1 = RegistrationResponse.builder()
                    .success(true)
                    .userId(123L)
                    .build();

            RegistrationResponse response2 = RegistrationResponse.builder()
                    .success(true)
                    .userId(123L)
                    .build();

            assertThat(response1).isEqualTo(response2);
            assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
        }
    }

    // ==================== RecaptchaRequest/Response Complete Tests ====================

    @Nested
    @DisplayName("RecaptchaRequest Complete Tests")
    class RecaptchaRequestCompleteTests {

        @Test
        @DisplayName("should create with default constructor")
        void shouldCreateWithDefaultConstructor() {
            RecaptchaRequest request = new RecaptchaRequest();
            assertThat(request.getEvent()).isNull();
        }

        @Test
        @DisplayName("should set and get event map")
        void shouldSetAndGetEventMap() {
            RecaptchaRequest request = new RecaptchaRequest();
            Map<String, Object> event = new HashMap<>();
            event.put("token", "recaptcha-token-123");
            event.put("siteKey", "site-key-456");
            event.put("expectedAction", "LOGIN");
            request.setEvent(event);

            assertThat(request.getEvent()).hasSize(3);
            assertThat(request.getEvent()).containsEntry("expectedAction", "LOGIN");
        }

        @Test
        @DisplayName("should handle complex nested event data")
        void shouldHandleComplexNestedEventData() {
            RecaptchaRequest request = new RecaptchaRequest();
            Map<String, Object> event = new HashMap<>();
            event.put("token", "token-123");
            event.put("userInfo", Map.of("ip", "192.168.1.1", "userAgent", "Mozilla/5.0"));
            request.setEvent(event);

            assertThat(request.getEvent()).containsKey("userInfo");
        }
    }

    @Nested
    @DisplayName("RecaptchaResponse Complete Tests")
    class RecaptchaResponseCompleteTests {

        @Test
        @DisplayName("should set and get all TokenProperties fields")
        void shouldSetAndGetAllTokenPropertiesFields() {
            RecaptchaResponse.TokenProperties props = new RecaptchaResponse.TokenProperties();
            props.setValid(true);
            props.setHostname("myapp.example.com");
            props.setAction("REGISTER");
            props.setCreateTime("2024-12-31T10:00:00Z");

            assertThat(props.isValid()).isTrue();
            assertThat(props.getHostname()).isEqualTo("myapp.example.com");
            assertThat(props.getAction()).isEqualTo("REGISTER");
            assertThat(props.getCreateTime()).isEqualTo("2024-12-31T10:00:00Z");
        }

        @Test
        @DisplayName("should set invalid reason for invalid token")
        void shouldSetInvalidReasonForInvalidToken() {
            RecaptchaResponse.TokenProperties props = new RecaptchaResponse.TokenProperties();
            props.setValid(false);
            props.setInvalidReason("EXPIRED");

            assertThat(props.isValid()).isFalse();
            assertThat(props.getInvalidReason()).isEqualTo("EXPIRED");
        }

        @Test
        @DisplayName("RiskAnalysis should handle various scores")
        void riskAnalysisShouldHandleVariousScores() {
            RecaptchaResponse.RiskAnalysis risk = new RecaptchaResponse.RiskAnalysis();

            risk.setScore(0.0);
            assertThat(risk.getScore()).isEqualTo(0.0);

            risk.setScore(0.5);
            assertThat(risk.getScore()).isEqualTo(0.5);

            risk.setScore(1.0);
            assertThat(risk.getScore()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("RiskAnalysis should handle reasons array")
        void riskAnalysisShouldHandleReasonsArray() {
            RecaptchaResponse.RiskAnalysis risk = new RecaptchaResponse.RiskAnalysis();
            risk.setReasons(new String[]{"AUTOMATION", "LOW_CONFIDENCE_SCORE"});

            assertThat(risk.getReasons()).hasSize(2);
            assertThat(risk.getReasons()).contains("AUTOMATION", "LOW_CONFIDENCE_SCORE");
        }

        @Test
        @DisplayName("should set complete response structure")
        void shouldSetCompleteResponseStructure() {
            RecaptchaResponse response = new RecaptchaResponse();

            RecaptchaResponse.TokenProperties props = new RecaptchaResponse.TokenProperties();
            props.setValid(true);
            props.setAction("LOGIN");

            RecaptchaResponse.RiskAnalysis risk = new RecaptchaResponse.RiskAnalysis();
            risk.setScore(0.9);

            response.setTokenProperties(props);
            response.setRiskAnalysis(risk);
            response.setName("projects/test/assessments/123");

            assertThat(response.getTokenProperties()).isNotNull();
            assertThat(response.getRiskAnalysis()).isNotNull();
            assertThat(response.getName()).isEqualTo("projects/test/assessments/123");
        }
    }

    // ==================== Edge Cases and Boundary Tests ====================

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCaseBoundaryTests {

        @Test
        @DisplayName("should handle very long strings in DTOs")
        void shouldHandleVeryLongStrings() {
            String longString = "a".repeat(1000);

            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName(longString);
            request.setAuthCode(longString);

            // Should not throw, just validate
            assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle Unicode characters in display names")
        void shouldHandleUnicodeCharacters() {
            SocialConnectionResponse response = new SocialConnectionResponse();
            response.setDisplayName("Emilia Muller");

            assertThat(response.getDisplayName()).contains("Muller");
        }

        @Test
        @DisplayName("should handle special characters in auth codes")
        void shouldHandleSpecialCharactersInAuthCodes() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("instagram");
            request.setAuthCode("code-with_special.chars/123!@#$%^&*()");

            Set<ConstraintViolation<SocialConnectionRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle empty maps in responses")
        void shouldHandleEmptyMapsInResponses() {
            SocialSignInResponse response = new SocialSignInResponse();
            response.setSocialUserData(new HashMap<>());

            assertThat(response.getSocialUserData()).isEmpty();
        }

        @Test
        @DisplayName("should handle null values in maps")
        void shouldHandleNullValuesInMaps() {
            Map<String, Object> data = new HashMap<>();
            data.put("nullKey", null);
            data.put("validKey", "value");

            SocialSignInResponse response = new SocialSignInResponse();
            response.setSocialUserData(data);

            assertThat(response.getSocialUserData()).containsEntry("nullKey", null);
            assertThat(response.getSocialUserData()).containsEntry("validKey", "value");
        }

        @Test
        @DisplayName("should handle maximum Long values for user IDs")
        void shouldHandleMaxLongValues() {
            RegisterUserResponse response = new RegisterUserResponse();
            response.setUserId(Long.MAX_VALUE);

            assertThat(response.getUserId()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("should handle minimum Long values for user IDs")
        void shouldHandleMinLongValues() {
            RegisterUserResponse response = new RegisterUserResponse();
            response.setUserId(Long.MIN_VALUE);

            assertThat(response.getUserId()).isEqualTo(Long.MIN_VALUE);
        }

        @Test
        @DisplayName("should handle whitespace-only strings")
        void shouldHandleWhitespaceOnlyStrings() {
            SocialSignInRequest request = new SocialSignInRequest();
            request.setPlatformName("   ");
            request.setAuthCode("\t\n");

            Set<ConstraintViolation<SocialSignInRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== Builder Pattern Complete Tests ====================

    @Nested
    @DisplayName("Builder Pattern Complete Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("TokenExchangeRequest builder should set defaults correctly")
        void tokenExchangeRequestBuilderShouldSetDefaults() {
            TokenExchangeRequest request = TokenExchangeRequest.builder().build();
            assertThat(request.getExpirationDays()).isEqualTo(7);
        }

        @Test
        @DisplayName("FirebaseAuthRequest builder should set returnSecureToken default")
        void firebaseAuthRequestBuilderShouldSetReturnSecureTokenDefault() {
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("test@example.com")
                    .password("password")
                    .build();

            assertThat(request.isReturnSecureToken()).isTrue();
        }

        @Test
        @DisplayName("TokenExchangeResponse builder should handle all 2FA fields")
        void tokenExchangeResponseBuilderShouldHandle2FAFields() {
            TokenExchangeResponse response = TokenExchangeResponse.builder()
                    .success(true)
                    .userId(123L)
                    .requires2FA(true)
                    .requires2FASetup(false)
                    .twoFactorEnabled(true)
                    .twoFactorVerified(false)
                    .cookieType("PARTIAL")
                    .sessionDuration("10 minutes")
                    .build();

            assertThat(response.getRequires2FA()).isTrue();
            assertThat(response.getRequires2FASetup()).isFalse();
            assertThat(response.getTwoFactorEnabled()).isTrue();
            assertThat(response.getTwoFactorVerified()).isFalse();
            assertThat(response.getCookieType()).isEqualTo("PARTIAL");
            assertThat(response.getSessionDuration()).isEqualTo("10 minutes");
        }

        @Test
        @DisplayName("TotpSetupResponse builder should handle all fields")
        void totpSetupResponseBuilderShouldHandleAllFields() {
            List<String> backupCodes = Arrays.asList("AAAA1111", "BBBB2222", "CCCC3333");

            TotpSetupResponse response = TotpSetupResponse.builder()
                    .qrCodeUrl("otpauth://totp/App:user@example.com")
                    .qrCodeImage("base64-encoded-image")
                    .googleChartsUrl("https://quickchart.io/qr?text=...")
                    .backupCodes(backupCodes)
                    .secret("JBSWY3DPEHPK3PXP")
                    .secretFormatted("JBSW Y3DP EHPK 3PXP")
                    .issuer("MyApp")
                    .email("user@example.com")
                    .error(null)
                    .alreadyEnabled(false)
                    .build();

            assertThat(response.getQrCodeUrl()).startsWith("otpauth://");
            assertThat(response.getBackupCodes()).hasSize(3);
            assertThat(response.getSecretFormatted()).contains(" ");
        }

        @Test
        @DisplayName("AuthOperationResponse builder should handle data map")
        void authOperationResponseBuilderShouldHandleDataMap() {
            Map<String, Object> data = new HashMap<>();
            data.put("userId", 123L);
            data.put("roles", Arrays.asList("USER", "ADMIN"));

            AuthOperationResponse response = AuthOperationResponse.builder()
                    .success(true)
                    .message("Success")
                    .correlationId("corr-123")
                    .processingTime(50L)
                    .data(data)
                    .build();

            assertThat(response.getData()).hasSize(2);
            assertThat(response.getData().get("roles")).isInstanceOf(List.class);
        }
    }

    // ==================== Factory Method Tests ====================

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("AuthOperationResponse.success should create success response")
        void authOperationResponseSuccessShouldCreateSuccessResponse() {
            AuthOperationResponse response = AuthOperationResponse.success("Operation completed");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Operation completed");
            assertThat(response.getError()).isNull();
        }

        @Test
        @DisplayName("AuthOperationResponse.success with data should include data")
        void authOperationResponseSuccessWithDataShouldIncludeData() {
            Map<String, Object> data = Map.of("userId", 123L);
            AuthOperationResponse response = AuthOperationResponse.success("Created", data);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).containsEntry("userId", 123L);
        }

        @Test
        @DisplayName("AuthOperationResponse.error should create error response")
        void authOperationResponseErrorShouldCreateErrorResponse() {
            AuthOperationResponse response = AuthOperationResponse.error("Invalid credentials");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Invalid credentials");
        }

        @Test
        @DisplayName("TokenExchangeResponse.success should create success response")
        void tokenExchangeResponseSuccessShouldCreateSuccessResponse() {
            TokenExchangeResponse response = TokenExchangeResponse.success(
                    123L, "user@example.com", "INFLUENCER", "fb-uid-123"
            );

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getUserId()).isEqualTo(123L);
            assertThat(response.getEmail()).isEqualTo("user@example.com");
            assertThat(response.getRole()).isEqualTo("INFLUENCER");
            assertThat(response.getFirebaseUid()).isEqualTo("fb-uid-123");
        }

        @Test
        @DisplayName("TokenExchangeResponse.error should create error response")
        void tokenExchangeResponseErrorShouldCreateErrorResponse() {
            TokenExchangeResponse response = TokenExchangeResponse.error("Token expired");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Token expired");
        }

        @Test
        @DisplayName("FirebaseAuthResponse.error should create error with code 400")
        void firebaseAuthResponseErrorShouldCreateErrorWithCode400() {
            FirebaseAuthResponse response = FirebaseAuthResponse.error("Authentication failed");

            assertThat(response.getError()).isNotNull();
            assertThat(response.getError().getCode()).isEqualTo(400);
            assertThat(response.getError().getMessage()).isEqualTo("Authentication failed");
        }

        @Test
        @DisplayName("RegistrationResponse.error should create error response")
        void registrationResponseErrorShouldCreateErrorResponse() {
            RegistrationResponse response = RegistrationResponse.error("Registration failed");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Registration failed");
        }
    }

    // ==================== isSuccess Method Tests ====================

    @Nested
    @DisplayName("isSuccess Method Tests")
    class IsSuccessMethodTests {

        @Test
        @DisplayName("FirebaseAuthResponse.isSuccess should return true when success flag is true")
        void firebaseAuthResponseIsSuccessShouldReturnTrueWhenFlagTrue() {
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .success(true)
                    .build();

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("FirebaseAuthResponse.isSuccess should return true when has idToken and no error")
        void firebaseAuthResponseIsSuccessShouldReturnTrueWhenHasIdTokenNoError() {
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .idToken("valid-token")
                    .build();

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("FirebaseAuthResponse.isSuccess should return false when has error")
        void firebaseAuthResponseIsSuccessShouldReturnFalseWhenHasError() {
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .error(FirebaseAuthResponse.FirebaseError.builder()
                            .message("Error")
                            .build())
                    .build();

            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("FirebaseAuthResponse.isSuccess should return false when no token and no success flag")
        void firebaseAuthResponseIsSuccessShouldReturnFalseWhenNoTokenNoFlag() {
            FirebaseAuthResponse response = FirebaseAuthResponse.builder().build();

            assertThat(response.isSuccess()).isFalse();
        }
    }

    // ==================== Validation Groups and Cross-Field Validation ====================

    @Nested
    @DisplayName("Cross-Field Validation Tests")
    class CrossFieldValidationTests {

        @Test
        @DisplayName("RegisterUserRequest should validate company data when userType is COMPANY")
        void shouldValidateCompanyDataWhenUserTypeIsCompany() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("company@example.com");
            request.setPassword("Password1!");
            request.setUserType("COMPANY");
            // Missing companyName

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("validCompanyData"));
        }

        @Test
        @DisplayName("RegisterUserRequest should not require company data when userType is INFLUENCER")
        void shouldNotRequireCompanyDataWhenUserTypeIsInfluencer() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("influencer@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("validCompanyData"))).isTrue();
        }

        @Test
        @DisplayName("RegisterUserRequest should validate address data when userType is COMPANY")
        void shouldValidateAddressDataWhenUserTypeIsCompany() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("company@example.com");
            request.setPassword("Password1!");
            request.setUserType("COMPANY");
            request.setCompanyName("Test Company");
            // Missing address fields

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("validAddressData"));
        }

        @Test
        @DisplayName("RegisterUserRequest should validate social data when platform is provided")
        void shouldValidateSocialDataWhenPlatformProvided() {
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("influencer@example.com");
            request.setPassword("Password1!");
            request.setUserType("INFLUENCER");
            request.setSocialPlatform("Instagram");
            // Missing socialAuthCode

            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("validSocialData"));
        }
    }

    // ==================== Serialization/Deserialization Tests ====================

    @Nested
    @DisplayName("Serialization Behavior Tests")
    class SerializationBehaviorTests {

        @Test
        @DisplayName("should handle nested objects correctly")
        void shouldHandleNestedObjectsCorrectly() {
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .idToken("id-token")
                    .error(FirebaseAuthResponse.FirebaseError.builder()
                            .code(401)
                            .message("Unauthorized")
                            .errors(Arrays.asList("error1", "error2"))
                            .build())
                    .build();

            assertThat(response.getError()).isNotNull();
            assertThat(response.getError().getCode()).isEqualTo(401);
            assertThat(response.getError().getErrors()).isNotNull();
        }
    }

    // ==================== Default Value Tests ====================

    @Nested
    @DisplayName("Default Value Tests")
    class DefaultValueTests {

        @Test
        @DisplayName("SocialConnectionRequest should have setPrimary true by default")
        void socialConnectionRequestShouldHaveSetPrimaryTrue() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            assertThat(request.getSetPrimary()).isTrue();
        }

        @Test
        @DisplayName("ExchangeTokenRequest should have expirationDays 7 by default")
        void exchangeTokenRequestShouldHaveExpirationDays7() {
            ExchangeTokenRequest request = new ExchangeTokenRequest();
            assertThat(request.getExpirationDays()).isEqualTo(7);
        }

        @Test
        @DisplayName("FirebaseLoginRequest should have expirationHours 8 by default")
        void firebaseLoginRequestShouldHaveExpirationHours8() {
            FirebaseLoginRequest request = new FirebaseLoginRequest();
            assertThat(request.getExpirationHours()).isEqualTo(8);
        }

        @Test
        @DisplayName("FirebaseAuthRequest should have returnSecureToken true by default")
        void firebaseAuthRequestShouldHaveReturnSecureTokenTrue() {
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("test@example.com")
                    .password("password")
                    .build();
            assertThat(request.isReturnSecureToken()).isTrue();
        }
    }
}
