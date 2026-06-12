package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.dto.RegisterUserRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for RegisterUserRequest validation.
 * Tests all validation annotations including @NotBlank, @Email, @Pattern, @Size, and @AssertTrue.
 */
@DisplayName("RegisterUserRequest Unit Tests")
class RegisterUserRequestUnitTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // Helper method to create a valid COMPANY request
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

    // Helper method to create a valid INFLUENCER request
    private RegisterUserRequest createValidInfluencerRequest() {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setEmail("influencer@example.com");
        request.setPassword("Password1!");
        request.setUserType("INFLUENCER");
        return request;
    }

    // ==================== Email Validation Tests ====================

    @Nested
    @DisplayName("Email Validation Tests")
    class EmailValidationTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t", "\n"})
        @DisplayName("should fail validation for blank email")
        void shouldFailValidationForBlankEmail(String email) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setEmail(email);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("email"))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "not-an-email",
                "@nodomain.com",
                "spaces in@email.com",
                "double@@domain.com"
        })
        @DisplayName("should fail validation for invalid email format")
        void shouldFailValidationForInvalidEmailFormat(String email) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setEmail(email);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("email"))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "test@example.com",
                "user.name@domain.org",
                "user+tag@example.com",
                "valid_email@sub.domain.com"
        })
        @DisplayName("should pass validation for valid email formats")
        void shouldPassValidationForValidEmailFormats(String email) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setEmail(email);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("email"))).isTrue();
        }
    }

    // ==================== Password Validation Tests ====================

    @Nested
    @DisplayName("Password Validation Tests")
    class PasswordValidationTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @DisplayName("should fail validation for blank password")
        void shouldFailValidationForBlankPassword(String password) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setPassword(password);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("password"))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"12345", "abc", "1", ""})
        @DisplayName("should fail validation for password less than 6 characters")
        void shouldFailValidationForShortPassword(String password) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setPassword(password);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"Abcdef1!", "Password1!", "LongerPass123!"})
        @DisplayName("should pass validation for valid password (lowercase + uppercase + digit + special)")
        void shouldPassValidationForValidPassword(String password) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setPassword(password);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("password"))).isTrue();
        }
    }

    // ==================== UserType Pattern Validation Tests ====================

    @Nested
    @DisplayName("UserType Pattern Validation Tests")
    class UserTypeValidationTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @DisplayName("should fail validation for blank userType")
        void shouldFailValidationForBlankUserType(String userType) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setUserType(userType);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("userType"))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"ADMIN", "USER", "GUEST", "company", "influencer", "Company", "Influencer"})
        @DisplayName("should fail validation for invalid userType pattern")
        void shouldFailValidationForInvalidUserType(String userType) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setUserType(userType);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("userType"))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"COMPANY", "INFLUENCER"})
        @DisplayName("should pass validation for valid userType pattern")
        void shouldPassValidationForValidUserType(String userType) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setUserType(userType);
            // For COMPANY type, set required fields
            if ("COMPANY".equals(userType)) {
                request.setCompanyName("Test Company");
                request.setAddressStreet("123 Main St");
                request.setAddressCity("Warsaw");
                request.setAddressPostalCode("00-001");
                request.setAddressCountry("Poland");
            }

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("userType"))).isTrue();
        }
    }

    // ==================== Phone Number Pattern Validation Tests ====================

    @Nested
    @DisplayName("Phone Number Pattern Validation Tests")
    class PhoneNumberValidationTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "+48123456789",
                "123-456-7890",
                "(123)456-7890",
                "+1-800-555-1234",
                "1234567",
                "+1(123)456-7890"
        })
        @DisplayName("should pass validation for valid phone numbers")
        void shouldPassValidationForValidPhoneNumbers(String phoneNumber) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setPhoneNumber(phoneNumber);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("phoneNumber"))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "abc",
                "12345",  // Too short (less than 7)
                "+1abc234567",
                "phone: 123456789"
        })
        @DisplayName("should fail validation for invalid phone numbers")
        void shouldFailValidationForInvalidPhoneNumbers(String phoneNumber) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setPhoneNumber(phoneNumber);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("phoneNumber"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for null phone number")
        void shouldPassValidationForNullPhoneNumber() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setPhoneNumber(null);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("phoneNumber"))).isTrue();
        }
    }

    // ==================== Profile Picture URL Pattern Validation Tests ====================

    @Nested
    @DisplayName("Profile Picture URL Validation Tests")
    class ProfilePictureUrlValidationTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://example.com/image.jpg",
                "https://cdn.example.com/users/profile.png",
                "https://storage.googleapis.com/bucket/image.jpg"
        })
        @DisplayName("should pass validation for valid HTTPS URLs")
        void shouldPassValidationForValidHttpsUrls(String url) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setProfilePictureUrl(url);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("profilePictureUrl"))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "http://example.com/image.jpg",
                "ftp://example.com/image.jpg",
                "not-a-url",
                "example.com/image.jpg"
        })
        @DisplayName("should fail validation for non-HTTPS URLs")
        void shouldFailValidationForNonHttpsUrls(String url) {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setProfilePictureUrl(url);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("profilePictureUrl"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for null profile picture URL")
        void shouldPassValidationForNullProfilePictureUrl() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setProfilePictureUrl(null);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("profilePictureUrl"))).isTrue();
        }
    }

    // ==================== isValidCompanyData AssertTrue Validation Tests ====================

    @Nested
    @DisplayName("Company Data Validation Tests (@AssertTrue isValidCompanyData)")
    class CompanyDataValidationTests {

        @Test
        @DisplayName("should pass validation for COMPANY with valid company name")
        void shouldPassValidationForCompanyWithName() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("validCompanyData"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for COMPANY without company name")
        void shouldFailValidationForCompanyWithoutName() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setCompanyName(null);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("validCompanyData"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for COMPANY with empty company name")
        void shouldFailValidationForCompanyWithEmptyName() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setCompanyName("");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("validCompanyData"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for INFLUENCER without company name")
        void shouldPassValidationForInfluencerWithoutCompanyName() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setCompanyName(null);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("validCompanyData"))).isTrue();
        }
    }

    // ==================== isValidAddressData AssertTrue Validation Tests ====================

    @Nested
    @DisplayName("Address Data Validation Tests (@AssertTrue isValidAddressData)")
    class AddressDataValidationTests {

        @Test
        @DisplayName("should pass validation for COMPANY with complete address")
        void shouldPassValidationForCompanyWithCompleteAddress() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("validAddressData"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for COMPANY without address street")
        void shouldFailValidationForCompanyWithoutStreet() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setAddressStreet(null);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("validAddressData"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for COMPANY without address city")
        void shouldFailValidationForCompanyWithoutCity() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setAddressCity(null);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("validAddressData"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for COMPANY without postal code")
        void shouldFailValidationForCompanyWithoutPostalCode() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setAddressPostalCode(null);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("validAddressData"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for COMPANY without country")
        void shouldFailValidationForCompanyWithoutCountry() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setAddressCountry(null);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("validAddressData"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for COMPANY with empty address fields")
        void shouldFailValidationForCompanyWithEmptyAddressFields() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setAddressStreet("");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("validAddressData"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for INFLUENCER without address data")
        void shouldPassValidationForInfluencerWithoutAddress() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("validAddressData"))).isTrue();
        }
    }

    // ==================== isValidSocialData AssertTrue Validation Tests ====================

    @Nested
    @DisplayName("Social Data Validation Tests (@AssertTrue isValidSocialData)")
    class SocialDataValidationTests {

        @Test
        @DisplayName("should pass validation for INFLUENCER with social platform and auth code")
        void shouldPassValidationForInfluencerWithSocialData() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setSocialPlatform("Instagram");
            request.setSocialAuthCode("auth-code-123");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("validSocialData"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for INFLUENCER with social platform but no auth code")
        void shouldFailValidationForInfluencerWithPlatformNoAuthCode() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setSocialPlatform("Instagram");
            request.setSocialAuthCode(null);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("validSocialData"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for INFLUENCER with social platform and empty auth code")
        void shouldFailValidationForInfluencerWithPlatformEmptyAuthCode() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setSocialPlatform("Instagram");
            request.setSocialAuthCode("");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("validSocialData"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for INFLUENCER without social platform")
        void shouldPassValidationForInfluencerWithoutSocialPlatform() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setSocialPlatform(null);
            request.setSocialAuthCode(null);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("validSocialData"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for INFLUENCER with empty social platform")
        void shouldPassValidationForInfluencerWithEmptySocialPlatform() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setSocialPlatform("");
            request.setSocialAuthCode(null);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("validSocialData"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for COMPANY with social platform but no auth code")
        void shouldPassValidationForCompanyWithSocialPlatformNoAuthCode() {
            // Given - COMPANY type, social validation should not apply
            RegisterUserRequest request = createValidCompanyRequest();
            request.setSocialPlatform("Instagram");
            request.setSocialAuthCode(null);

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then - socialData validation only applies to INFLUENCER
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("validSocialData"))).isTrue();
        }
    }

    // ==================== Complete Validation Tests ====================

    @Nested
    @DisplayName("Complete Request Validation Tests")
    class CompleteValidationTests {

        @Test
        @DisplayName("should pass validation for fully valid COMPANY request")
        void shouldPassValidationForCompleteCompanyRequest() {
            // Given
            RegisterUserRequest request = createValidCompanyRequest();
            request.setFirstName("John");
            request.setLastName("Doe");
            request.setPhoneNumber("+48123456789");
            request.setProfilePictureUrl("https://example.com/avatar.jpg");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation for fully valid INFLUENCER request")
        void shouldPassValidationForCompleteInfluencerRequest() {
            // Given
            RegisterUserRequest request = createValidInfluencerRequest();
            request.setFirstName("Jane");
            request.setLastName("Smith");
            request.setSocialPlatform("Instagram");
            request.setSocialAuthCode("auth-code-456");
            request.setProfilePictureUrl("https://example.com/profile.png");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should collect multiple violations")
        void shouldCollectMultipleViolations() {
            // Given - request with multiple issues
            RegisterUserRequest request = new RegisterUserRequest();
            request.setEmail("not-an-email");
            request.setPassword("123");
            request.setUserType("INVALID");

            // When
            Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(request);

            // Then
            assertThat(violations.size()).isGreaterThanOrEqualTo(2);
        }
    }
}
