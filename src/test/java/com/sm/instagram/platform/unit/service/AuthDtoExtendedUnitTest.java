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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extended unit tests for Auth DTOs that weren't covered in AuthDtoUnitTest.
 * Tests validation, default values, helper methods, and builder patterns.
 */
@DisplayName("Auth DTO Extended Unit Tests")
class AuthDtoExtendedUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== RegisterInfluencerRequest Tests ====================

    @Nested
    @DisplayName("RegisterInfluencerRequest Tests")
    class RegisterInfluencerRequestTests {

        @Test
        @DisplayName("should pass validation with valid data")
        void shouldPassValidationWithValidData() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("influencer@example.com");
            request.setPlatformName("instagram");
            request.setSocialUserData(new HashMap<>());

            Set<ConstraintViolation<RegisterInfluencerRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t", "\n"})
        @DisplayName("should fail validation when email is blank")
        void shouldFailValidationWhenEmailIsBlank(String email) {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail(email);
            request.setPlatformName("instagram");

            Set<ConstraintViolation<RegisterInfluencerRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"invalid", "invalid@", "@example.com", "invalid email@example.com"})
        @DisplayName("should fail validation with invalid email format")
        void shouldFailValidationWithInvalidEmailFormat(String email) {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail(email);
            request.setPlatformName("instagram");

            Set<ConstraintViolation<RegisterInfluencerRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("should fail validation when platformName is blank")
        void shouldFailValidationWhenPlatformNameIsBlank(String platformName) {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("valid@example.com");
            request.setPlatformName(platformName);

            Set<ConstraintViolation<RegisterInfluencerRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("platformName"));
        }

        @Test
        @DisplayName("should accept null socialUserData")
        void shouldAcceptNullSocialUserData() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            request.setEmail("influencer@example.com");
            request.setPlatformName("instagram");
            request.setSocialUserData(null);

            Set<ConstraintViolation<RegisterInfluencerRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should store socialUserData correctly")
        void shouldStoreSocialUserDataCorrectly() {
            RegisterInfluencerRequest request = new RegisterInfluencerRequest();
            Map<String, Object> socialData = new HashMap<>();
            socialData.put("userId", "12345");
            socialData.put("username", "testuser");
            socialData.put("followers", 10000);
            request.setSocialUserData(socialData);

            assertThat(request.getSocialUserData())
                    .containsEntry("userId", "12345")
                    .containsEntry("username", "testuser")
                    .containsEntry("followers", 10000);
        }
    }

    // ==================== CompleteSocialRegistrationRequest Tests ====================

    @Nested
    @DisplayName("CompleteSocialRegistrationRequest Tests")
    class CompleteSocialRegistrationRequestTests {

        @Test
        @DisplayName("should pass validation with valid data")
        void shouldPassValidationWithValidData() {
            CompleteSocialRegistrationRequest request = CompleteSocialRegistrationRequest.builder()
                    .sessionId("session-123-abc")
                    .email("user@example.com")
                    .build();

            Set<ConstraintViolation<CompleteSocialRegistrationRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should create via no-args constructor and setters")
        void shouldCreateViaNoArgsConstructorAndSetters() {
            CompleteSocialRegistrationRequest request = new CompleteSocialRegistrationRequest();
            request.setSessionId("session-456");
            request.setEmail("test@example.com");

            assertThat(request.getSessionId()).isEqualTo("session-456");
            assertThat(request.getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("should create via all-args constructor")
        void shouldCreateViaAllArgsConstructor() {
            CompleteSocialRegistrationRequest request =
                    new CompleteSocialRegistrationRequest("session-789", "all@example.com");

            assertThat(request.getSessionId()).isEqualTo("session-789");
            assertThat(request.getEmail()).isEqualTo("all@example.com");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail validation when sessionId is blank")
        void shouldFailValidationWhenSessionIdIsBlank(String sessionId) {
            CompleteSocialRegistrationRequest request = CompleteSocialRegistrationRequest.builder()
                    .sessionId(sessionId)
                    .email("user@example.com")
                    .build();

            Set<ConstraintViolation<CompleteSocialRegistrationRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("sessionId"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail validation when email is blank")
        void shouldFailValidationWhenEmailIsBlank(String email) {
            CompleteSocialRegistrationRequest request = CompleteSocialRegistrationRequest.builder()
                    .sessionId("session-123")
                    .email(email)
                    .build();

            Set<ConstraintViolation<CompleteSocialRegistrationRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"not-an-email", "missing@", "@nodomain.com"})
        @DisplayName("should fail validation with invalid email format")
        void shouldFailValidationWithInvalidEmailFormat(String email) {
            CompleteSocialRegistrationRequest request = CompleteSocialRegistrationRequest.builder()
                    .sessionId("session-123")
                    .email(email)
                    .build();

            Set<ConstraintViolation<CompleteSocialRegistrationRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        }
    }

    // ==================== DeleteAccountRequest Tests ====================

    @Nested
    @DisplayName("DeleteAccountRequest Tests")
    class DeleteAccountRequestTests {

        @Test
        @DisplayName("should pass validation with password only")
        void shouldPassValidationWithPasswordOnly() {
            DeleteAccountRequest request = DeleteAccountRequest.builder()
                    .password("mySecurePassword123")
                    .build();

            Set<ConstraintViolation<DeleteAccountRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with password and reason")
        void shouldPassValidationWithPasswordAndReason() {
            DeleteAccountRequest request = DeleteAccountRequest.builder()
                    .password("mySecurePassword123")
                    .reason("Moving to a different platform")
                    .build();

            Set<ConstraintViolation<DeleteAccountRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should accept null reason")
        void shouldAcceptNullReason() {
            DeleteAccountRequest request = DeleteAccountRequest.builder()
                    .password("Password1!")
                    .reason(null)
                    .build();

            Set<ConstraintViolation<DeleteAccountRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
            assertThat(request.getReason()).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t", "\n"})
        @DisplayName("should fail validation when password is blank")
        void shouldFailValidationWhenPasswordIsBlank(String password) {
            DeleteAccountRequest request = DeleteAccountRequest.builder()
                    .password(password)
                    .build();

            Set<ConstraintViolation<DeleteAccountRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
        }

        @Test
        @DisplayName("should use all-args constructor")
        void shouldUseAllArgsConstructor() {
            DeleteAccountRequest request = new DeleteAccountRequest("pass123", "No longer needed");

            assertThat(request.getPassword()).isEqualTo("pass123");
            assertThat(request.getReason()).isEqualTo("No longer needed");
        }
    }

    // ==================== EmailUpdateRequest Tests ====================

    @Nested
    @DisplayName("EmailUpdateRequest Tests")
    class EmailUpdateRequestTests {

        @Test
        @DisplayName("should pass validation with valid data")
        void shouldPassValidationWithValidData() {
            EmailUpdateRequest request = EmailUpdateRequest.builder()
                    .newEmail("newemail@example.com")
                    .password("currentPassword123")
                    .build();

            Set<ConstraintViolation<EmailUpdateRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail validation when newEmail is blank")
        void shouldFailValidationWhenNewEmailIsBlank(String email) {
            EmailUpdateRequest request = EmailUpdateRequest.builder()
                    .newEmail(email)
                    .password("Password1!")
                    .build();

            Set<ConstraintViolation<EmailUpdateRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newEmail"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"invalid-email", "missing@", "@nolocal.com", "spaces in@email.com"})
        @DisplayName("should fail validation with invalid email format")
        void shouldFailValidationWithInvalidEmailFormat(String email) {
            EmailUpdateRequest request = EmailUpdateRequest.builder()
                    .newEmail(email)
                    .password("Password1!")
                    .build();

            Set<ConstraintViolation<EmailUpdateRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newEmail"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail validation when password is blank")
        void shouldFailValidationWhenPasswordIsBlank(String password) {
            EmailUpdateRequest request = EmailUpdateRequest.builder()
                    .newEmail("new@example.com")
                    .password(password)
                    .build();

            Set<ConstraintViolation<EmailUpdateRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
        }

        @Test
        @DisplayName("should use all-args constructor")
        void shouldUseAllArgsConstructor() {
            EmailUpdateRequest request = new EmailUpdateRequest("new@test.com", "pass123");

            assertThat(request.getNewEmail()).isEqualTo("new@test.com");
            assertThat(request.getPassword()).isEqualTo("pass123");
        }
    }

    // ==================== EnableTotpRequest Tests ====================

    @Nested
    @DisplayName("EnableTotpRequest Tests")
    class EnableTotpRequestTests {

        @Test
        @DisplayName("should pass validation with valid verification code")
        void shouldPassValidationWithValidVerificationCode() {
            EnableTotpRequest request = new EnableTotpRequest();
            request.setVerificationCode("123456");

            Set<ConstraintViolation<EnableTotpRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when verificationCode is null")
        void shouldFailValidationWhenVerificationCodeIsNull() {
            EnableTotpRequest request = new EnableTotpRequest();
            request.setVerificationCode(null);

            Set<ConstraintViolation<EnableTotpRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("verificationCode"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"000000", "000001", "999999", "100000"})
        @DisplayName("should accept various valid codes")
        void shouldAcceptVariousValidCodes(String code) {
            EnableTotpRequest request = new EnableTotpRequest();
            request.setVerificationCode(code);

            Set<ConstraintViolation<EnableTotpRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }

    // ==================== ReauthRequest Tests ====================

    @Nested
    @DisplayName("ReauthRequest Tests")
    class ReauthRequestTests {

        @Test
        @DisplayName("should pass validation with valid password")
        void shouldPassValidationWithValidPassword() {
            ReauthRequest request = ReauthRequest.builder()
                    .password("myPassword123")
                    .build();

            Set<ConstraintViolation<ReauthRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t", "\n"})
        @DisplayName("should fail validation when password is blank")
        void shouldFailValidationWhenPasswordIsBlank(String password) {
            ReauthRequest request = ReauthRequest.builder()
                    .password(password)
                    .build();

            Set<ConstraintViolation<ReauthRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
        }

        @Test
        @DisplayName("should use no-args constructor")
        void shouldUseNoArgsConstructor() {
            ReauthRequest request = new ReauthRequest();
            request.setPassword("testPassword");

            assertThat(request.getPassword()).isEqualTo("testPassword");
        }

        @Test
        @DisplayName("should use all-args constructor")
        void shouldUseAllArgsConstructor() {
            ReauthRequest request = new ReauthRequest("directPassword");

            assertThat(request.getPassword()).isEqualTo("directPassword");
        }
    }

    // ==================== BackupCodeRequest Tests ====================

    @Nested
    @DisplayName("BackupCodeRequest Tests")
    class BackupCodeRequestTests {

        @Test
        @DisplayName("should pass validation with valid data")
        void shouldPassValidationWithValidData() {
            BackupCodeRequest request = new BackupCodeRequest();
            request.setUserId("user-123");
            request.setBackupCode("ABCD1234");

            Set<ConstraintViolation<BackupCodeRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail validation when userId is blank")
        void shouldFailValidationWhenUserIdIsBlank(String userId) {
            BackupCodeRequest request = new BackupCodeRequest();
            request.setUserId(userId);
            request.setBackupCode("ABCD1234");

            Set<ConstraintViolation<BackupCodeRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("userId"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail validation when backupCode is blank")
        void shouldFailValidationWhenBackupCodeIsBlank(String code) {
            BackupCodeRequest request = new BackupCodeRequest();
            request.setUserId("user-123");
            request.setBackupCode(code);

            Set<ConstraintViolation<BackupCodeRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("backupCode"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"ABCDEFGH", "12345678", "A1B2C3D4", "XXXXXXXX", "00000000"})
        @DisplayName("should accept valid 8-character alphanumeric backup codes")
        void shouldAcceptValidBackupCodes(String code) {
            BackupCodeRequest request = new BackupCodeRequest();
            request.setUserId("user-123");
            request.setBackupCode(code);

            Set<ConstraintViolation<BackupCodeRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"ABC123", "ABCDEFGHI", "abcd1234", "ABCD-123", "ABCD 123"})
        @DisplayName("should reject invalid backup code patterns")
        void shouldRejectInvalidBackupCodePatterns(String code) {
            BackupCodeRequest request = new BackupCodeRequest();
            request.setUserId("user-123");
            request.setBackupCode(code);

            Set<ConstraintViolation<BackupCodeRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("backupCode"));
        }

        @Test
        @DisplayName("should reject lowercase backup codes")
        void shouldRejectLowercaseBackupCodes() {
            BackupCodeRequest request = new BackupCodeRequest();
            request.setUserId("user-123");
            request.setBackupCode("abcd1234");

            Set<ConstraintViolation<BackupCodeRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("backupCode"));
        }
    }

    // ==================== ExchangeTokenRequest Tests ====================

    @Nested
    @DisplayName("ExchangeTokenRequest Tests")
    class ExchangeTokenRequestTests {

        @Test
        @DisplayName("should pass validation with valid idToken")
        void shouldPassValidationWithValidIdToken() {
            ExchangeTokenRequest request = new ExchangeTokenRequest();
            request.setIdToken("valid.firebase.token");

            Set<ConstraintViolation<ExchangeTokenRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail validation when idToken is blank")
        void shouldFailValidationWhenIdTokenIsBlank(String token) {
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
        @DisplayName("should calculate expirationHours from days")
        void shouldCalculateExpirationHoursFromDays() {
            ExchangeTokenRequest request = new ExchangeTokenRequest();
            request.setExpirationDays(7);

            assertThat(request.getExpirationHours()).isEqualTo(168);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 7, 14, 30})
        @DisplayName("should correctly convert various days to hours")
        void shouldCorrectlyConvertVariousDaysToHours(int days) {
            ExchangeTokenRequest request = new ExchangeTokenRequest();
            request.setExpirationDays(days);

            assertThat(request.getExpirationHours()).isEqualTo(days * 24);
        }

        @Test
        @DisplayName("should return default 168 hours when expirationDays is null")
        void shouldReturnDefaultHoursWhenExpirationDaysIsNull() {
            ExchangeTokenRequest request = new ExchangeTokenRequest();
            request.setExpirationDays(null);

            assertThat(request.getExpirationHours()).isEqualTo(168);
        }
    }

    // ==================== TotpVerifyRequest Tests ====================

    @Nested
    @DisplayName("TotpVerifyRequest Tests")
    class TotpVerifyRequestTests {

        @Test
        @DisplayName("should pass validation with valid code")
        void shouldPassValidationWithValidCode() {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when code is null")
        void shouldFailValidationWhenCodeIsNull() {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode(null);

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
        }

        @Test
        @DisplayName("should accept zero-padded code")
        void shouldAcceptZeroPaddedCode() {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("000000");

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should accept maximum 6-digit code")
        void shouldAcceptMaximumValidCode() {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("999999");

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should reject code with non-digit characters")
        void shouldRejectCodeWithNonDigits() {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("-1");

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
        }

        @Test
        @DisplayName("should reject code exceeding 6 digits")
        void shouldRejectCodeExceedingMaxLength() {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("1234567");

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"100000", "500000", "123456", "654321"})
        @DisplayName("should accept typical 6-digit codes")
        void shouldAcceptTypicalSixDigitCodes(String code) {
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode(code);

            Set<ConstraintViolation<TotpVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }

    // ==================== SocialConnectionRequest Tests ====================

    @Nested
    @DisplayName("SocialConnectionRequest Tests")
    class SocialConnectionRequestTests {

        @Test
        @DisplayName("should pass validation with valid data")
        void shouldPassValidationWithValidData() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("instagram");
            request.setAuthCode("auth-code-123");

            Set<ConstraintViolation<SocialConnectionRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail validation when platformName is blank")
        void shouldFailValidationWhenPlatformNameIsBlank(String platform) {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName(platform);
            request.setAuthCode("auth-code-123");

            Set<ConstraintViolation<SocialConnectionRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("platformName"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail validation when authCode is blank")
        void shouldFailValidationWhenAuthCodeIsBlank(String code) {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("instagram");
            request.setAuthCode(code);

            Set<ConstraintViolation<SocialConnectionRequest>> violations = validator.validate(request);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("authCode"));
        }

        @Test
        @DisplayName("should have default setPrimary as true")
        void shouldHaveDefaultSetPrimaryAsTrue() {
            SocialConnectionRequest request = new SocialConnectionRequest();

            assertThat(request.getSetPrimary()).isTrue();
        }

        @Test
        @DisplayName("should allow setPrimary to be changed")
        void shouldAllowSetPrimaryToBeChanged() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setSetPrimary(false);

            assertThat(request.getSetPrimary()).isFalse();
        }

        @Test
        @DisplayName("should accept various platform names")
        void shouldAcceptVariousPlatformNames() {
            String[] platforms = {"instagram", "facebook", "twitter", "tiktok", "youtube"};

            for (String platform : platforms) {
                SocialConnectionRequest request = new SocialConnectionRequest();
                request.setPlatformName(platform);
                request.setAuthCode("code-123");

                Set<ConstraintViolation<SocialConnectionRequest>> violations = validator.validate(request);
                assertThat(violations).isEmpty();
            }
        }
    }

    // ==================== FirebaseLoginRequest Tests ====================

    @Nested
    @DisplayName("FirebaseLoginRequest Tests")
    class FirebaseLoginRequestTests {

        @Test
        @DisplayName("should pass validation with valid firebaseToken")
        void shouldPassValidationWithValidFirebaseToken() {
            FirebaseLoginRequest request = new FirebaseLoginRequest();
            request.setFirebaseToken("valid.firebase.token");

            Set<ConstraintViolation<FirebaseLoginRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail validation when firebaseToken is blank")
        void shouldFailValidationWhenFirebaseTokenIsBlank(String token) {
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

        @Test
        @DisplayName("should allow custom expirationHours")
        void shouldAllowCustomExpirationHours() {
            FirebaseLoginRequest request = new FirebaseLoginRequest();
            request.setExpirationHours(24);

            assertThat(request.getExpirationHours()).isEqualTo(24);
        }
    }

    // ==================== SocialConnectionResponse Tests ====================

    @Nested
    @DisplayName("SocialConnectionResponse Tests")
    class SocialConnectionResponseTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
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
        @DisplayName("should set success state")
        void shouldSetSuccessState() {
            SocialConnectionResponse response = new SocialConnectionResponse();
            response.setSuccess(true);

            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should set all fields correctly")
        void shouldSetAllFieldsCorrectly() {
            SocialConnectionResponse response = new SocialConnectionResponse();
            response.setSuccess(true);
            response.setConnectionId(123L);
            response.setPlatformName("instagram");
            response.setSocialUserId("ig-user-456");
            response.setDisplayName("Test User");
            response.setFollowersCount(10000);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getConnectionId()).isEqualTo(123L);
            assertThat(response.getPlatformName()).isEqualTo("instagram");
            assertThat(response.getSocialUserId()).isEqualTo("ig-user-456");
            assertThat(response.getDisplayName()).isEqualTo("Test User");
            assertThat(response.getFollowersCount()).isEqualTo(10000);
        }

        @Test
        @DisplayName("should set error message for failed connection")
        void shouldSetErrorMessageForFailedConnection() {
            SocialConnectionResponse response = new SocialConnectionResponse();
            response.setSuccess(false);
            response.setErrorMessage("Invalid auth code");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorMessage()).isEqualTo("Invalid auth code");
        }
    }

    // ==================== RegisterUserResponse Tests ====================

    @Nested
    @DisplayName("RegisterUserResponse Tests")
    class RegisterUserResponseTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
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
        @DisplayName("should set successful registration data")
        void shouldSetSuccessfulRegistrationData() {
            RegisterUserResponse response = new RegisterUserResponse();
            response.setSuccess(true);
            response.setFirebaseUserId("fb-user-123");
            response.setUserId(456L);
            response.setCustomToken("custom.jwt.token");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getFirebaseUserId()).isEqualTo("fb-user-123");
            assertThat(response.getUserId()).isEqualTo(456L);
            assertThat(response.getCustomToken()).isEqualTo("custom.jwt.token");
        }

        @Test
        @DisplayName("should include nested social connection")
        void shouldIncludeNestedSocialConnection() {
            SocialConnectionResponse socialConn = new SocialConnectionResponse();
            socialConn.setSuccess(true);
            socialConn.setPlatformName("instagram");
            socialConn.setFollowersCount(5000);

            RegisterUserResponse response = new RegisterUserResponse();
            response.setSuccess(true);
            response.setSocialConnection(socialConn);

            assertThat(response.getSocialConnection()).isNotNull();
            assertThat(response.getSocialConnection().getPlatformName()).isEqualTo("instagram");
            assertThat(response.getSocialConnection().getFollowersCount()).isEqualTo(5000);
        }

        @Test
        @DisplayName("should set social connection error separately")
        void shouldSetSocialConnectionErrorSeparately() {
            RegisterUserResponse response = new RegisterUserResponse();
            response.setSuccess(true);  // Registration succeeded
            response.setSocialConnectionError("Failed to connect Instagram account");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getSocialConnectionError()).isEqualTo("Failed to connect Instagram account");
            assertThat(response.getSocialConnection()).isNull();
        }

        @Test
        @DisplayName("should set error message for failed registration")
        void shouldSetErrorMessageForFailedRegistration() {
            RegisterUserResponse response = new RegisterUserResponse();
            response.setSuccess(false);
            response.setError("Email already exists");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Email already exists");
        }
    }

    // ==================== RegistrationResponse Tests ====================

    @Nested
    @DisplayName("RegistrationResponse Tests")
    class RegistrationResponseTests {

        @Test
        @DisplayName("should create via builder")
        void shouldCreateViaBuilder() {
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
        @DisplayName("should create error response via static factory")
        void shouldCreateErrorResponseViaStaticFactory() {
            RegistrationResponse response = RegistrationResponse.error("Registration failed");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Registration failed");
            assertThat(response.getCustomToken()).isNull();
            assertThat(response.getUserId()).isNull();
            assertThat(response.getFirebaseUid()).isNull();
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

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getUserType()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("isSuccess should return correct boolean")
        void isSuccessShouldReturnCorrectBoolean() {
            RegistrationResponse successResponse = RegistrationResponse.builder()
                    .success(true)
                    .build();
            RegistrationResponse failResponse = RegistrationResponse.builder()
                    .success(false)
                    .build();

            assertThat(successResponse.isSuccess()).isTrue();
            assertThat(failResponse.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("error factory should set success to false")
        void errorFactoryShouldSetSuccessToFalse() {
            RegistrationResponse response = RegistrationResponse.error("Some error");

            assertThat(response.isSuccess()).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"INFLUENCER", "COMPANY", "ADMIN", "USER"})
        @DisplayName("should accept various user types")
        void shouldAcceptVariousUserTypes(String userType) {
            RegistrationResponse response = RegistrationResponse.builder()
                    .success(true)
                    .userType(userType)
                    .build();

            assertThat(response.getUserType()).isEqualTo(userType);
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle very long email addresses")
        void shouldHandleVeryLongEmailAddresses() {
            EmailUpdateRequest request = EmailUpdateRequest.builder()
                    .newEmail("a".repeat(50) + "@" + "b".repeat(50) + ".com")
                    .password("Password1!")
                    .build();

            Set<ConstraintViolation<EmailUpdateRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle special characters in auth codes")
        void shouldHandleSpecialCharactersInAuthCodes() {
            SocialConnectionRequest request = new SocialConnectionRequest();
            request.setPlatformName("instagram");
            request.setAuthCode("code-with_special.chars/123");

            Set<ConstraintViolation<SocialConnectionRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle Unicode in display name")
        void shouldHandleUnicodeInDisplayName() {
            SocialConnectionResponse response = new SocialConnectionResponse();
            response.setDisplayName("Влад Сорокин");

            assertThat(response.getDisplayName()).isEqualTo("Влад Сорокин");
        }

        @Test
        @DisplayName("should handle zero followers count")
        void shouldHandleZeroFollowersCount() {
            SocialConnectionResponse response = new SocialConnectionResponse();
            response.setFollowersCount(0);

            assertThat(response.getFollowersCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should handle very large followers count")
        void shouldHandleVeryLargeFollowersCount() {
            SocialConnectionResponse response = new SocialConnectionResponse();
            response.setFollowersCount(Integer.MAX_VALUE);

            assertThat(response.getFollowersCount()).isEqualTo(Integer.MAX_VALUE);
        }
    }
}
