package com.sm.instagram.platform.unit.entity;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User Entity Unit Tests")
class UserEntityUnitTest {

    private Validator validator;
    private User user;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        user = createValidUser();
    }

    private User createValidUser() {
        User user = new User();
        user.setFirebaseUserId("validFirebaseId123");
        user.setUserType(UserType.INFLUENCER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        return user;
    }

    // ==================== FirebaseUserId Validation Tests ====================

    @Nested
    @DisplayName("Firebase User ID Validation")
    class FirebaseUserIdValidation {

        @Test
        @DisplayName("should pass validation with valid firebase user ID")
        void shouldPassValidationWithValidFirebaseUserId() {
            user.setFirebaseUserId("validFirebaseId_123-test.user");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when firebase user ID is null")
        void shouldFailValidationWhenFirebaseUserIdIsNull() {
            user.setFirebaseUserId(null);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Firebase User ID cannot be blank"));
        }

        @Test
        @DisplayName("should fail validation when firebase user ID is empty")
        void shouldFailValidationWhenFirebaseUserIdIsEmpty() {
            user.setFirebaseUserId("");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Firebase User ID cannot be blank"));
        }

        @Test
        @DisplayName("should fail validation when firebase user ID is blank")
        void shouldFailValidationWhenFirebaseUserIdIsBlank() {
            user.setFirebaseUserId("   ");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation when firebase user ID exceeds 255 characters")
        void shouldFailValidationWhenFirebaseUserIdExceeds255Characters() {
            user.setFirebaseUserId("a".repeat(256));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Firebase User ID cannot exceed 255 characters"));
        }

        @Test
        @DisplayName("should pass validation when firebase user ID is exactly 255 characters")
        void shouldPassValidationWhenFirebaseUserIdIsExactly255Characters() {
            user.setFirebaseUserId("a".repeat(255));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @DisplayName("should fail validation when firebase user ID contains invalid characters")
        @ValueSource(strings = {"invalid@id", "invalid#id", "invalid id", "invalid$id", "invalid%id", "invalid!id"})
        void shouldFailValidationWhenFirebaseUserIdContainsInvalidCharacters(String invalidId) {
            user.setFirebaseUserId(invalidId);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Firebase User ID must contain only letters, numbers, hyphens, underscores, and dots"));
        }

        @ParameterizedTest
        @DisplayName("should pass validation with valid firebase user ID patterns")
        @ValueSource(strings = {"abc123", "user-name", "user_name", "user.name", "ABC123", "User_Name-123.test"})
        void shouldPassValidationWithValidFirebaseUserIdPatterns(String validId) {
            user.setFirebaseUserId(validId);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== UserType Validation Tests ====================

    @Nested
    @DisplayName("User Type Validation")
    class UserTypeValidation {

        @Test
        @DisplayName("should fail validation when user type is null")
        void shouldFailValidationWhenUserTypeIsNull() {
            user.setUserType(null);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("User Type cannot be blank"));
        }

        @ParameterizedTest
        @DisplayName("should pass validation for all valid user types")
        @EnumSource(UserType.class)
        void shouldPassValidationForAllValidUserTypes(UserType userType) {
            user.setUserType(userType);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== Email Validation Tests ====================

    @Nested
    @DisplayName("Email Validation")
    class EmailValidation {

        @Test
        @DisplayName("should pass validation with valid email")
        void shouldPassValidationWithValidEmail() {
            user.setEmail("test@example.com");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation when email is null")
        void shouldPassValidationWhenEmailIsNull() {
            user.setEmail(null);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @DisplayName("should fail validation with invalid email format")
        @ValueSource(strings = {"invalid", "invalid@", "@example.com", "invalid.com", "test@", "test@."})
        void shouldFailValidationWithInvalidEmailFormat(String invalidEmail) {
            user.setEmail(invalidEmail);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Invalid email format"));
        }

        @Test
        @DisplayName("should fail validation when email exceeds 255 characters")
        void shouldFailValidationWhenEmailExceeds255Characters() {
            user.setEmail("a".repeat(244) + "@example.com");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Email cannot exceed 255 characters"));
        }

        @ParameterizedTest
        @DisplayName("should pass validation with valid email formats")
        @ValueSource(strings = {
                "test@example.com",
                "test.user@example.com",
                "test+tag@example.com",
                "test@subdomain.example.com"
        })
        void shouldPassValidationWithValidEmailFormats(String validEmail) {
            user.setEmail(validEmail);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== First Name Validation Tests ====================

    @Nested
    @DisplayName("First Name Validation")
    class FirstNameValidation {

        @Test
        @DisplayName("should pass validation with valid first name")
        void shouldPassValidationWithValidFirstName() {
            user.setFirstName("John");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation when first name is null")
        void shouldPassValidationWhenFirstNameIsNull() {
            user.setFirstName(null);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when first name is shorter than 2 characters")
        void shouldFailValidationWhenFirstNameIsShorterThan2Characters() {
            user.setFirstName("J");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("First name must be between 2 and 50 characters"));
        }

        @Test
        @DisplayName("should fail validation when first name exceeds 50 characters")
        void shouldFailValidationWhenFirstNameExceeds50Characters() {
            user.setFirstName("J".repeat(51));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("First name must be between 2 and 50 characters"));
        }

        @Test
        @DisplayName("should pass validation when first name is exactly 2 characters")
        void shouldPassValidationWhenFirstNameIsExactly2Characters() {
            user.setFirstName("Jo");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation when first name is exactly 50 characters")
        void shouldPassValidationWhenFirstNameIsExactly50Characters() {
            user.setFirstName("J".repeat(50));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== Last Name Validation Tests ====================

    @Nested
    @DisplayName("Last Name Validation")
    class LastNameValidation {

        @Test
        @DisplayName("should pass validation with valid last name")
        void shouldPassValidationWithValidLastName() {
            user.setLastName("Doe");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation when last name is null")
        void shouldPassValidationWhenLastNameIsNull() {
            user.setLastName(null);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when last name is shorter than 2 characters")
        void shouldFailValidationWhenLastNameIsShorterThan2Characters() {
            user.setLastName("D");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Last name must be between 2 and 50 characters"));
        }

        @Test
        @DisplayName("should fail validation when last name exceeds 50 characters")
        void shouldFailValidationWhenLastNameExceeds50Characters() {
            user.setLastName("D".repeat(51));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Last name must be between 2 and 50 characters"));
        }

        @Test
        @DisplayName("should pass validation when last name is exactly 2 characters")
        void shouldPassValidationWhenLastNameIsExactly2Characters() {
            user.setLastName("Do");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation when last name is exactly 50 characters")
        void shouldPassValidationWhenLastNameIsExactly50Characters() {
            user.setLastName("D".repeat(50));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== Profile Picture URL Validation Tests ====================

    @Nested
    @DisplayName("Profile Picture URL Validation")
    class ProfilePictureValidation {

        @Test
        @DisplayName("should pass validation with valid HTTPS URL")
        void shouldPassValidationWithValidHttpsUrl() {
            user.setProfilePicture("https://example.com/image.jpg");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation when profile picture is null")
        void shouldPassValidationWhenProfilePictureIsNull() {
            user.setProfilePicture(null);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation with HTTP URL")
        void shouldFailValidationWithHttpUrl() {
            user.setProfilePicture("http://example.com/image.jpg");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("URL must use HTTPS protocol"));
        }

        @Test
        @DisplayName("should fail validation when profile picture URL exceeds 2048 characters")
        void shouldFailValidationWhenProfilePictureUrlExceeds2048Characters() {
            user.setProfilePicture("https://example.com/" + "a".repeat(2030));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("URL cannot exceed 2048 characters"));
        }

        @ParameterizedTest
        @DisplayName("should fail validation with invalid URL patterns")
        @ValueSource(strings = {
                "not-a-url",
                "ftp://example.com/image.jpg",
                "https://",
                "https:/example.com"
        })
        void shouldFailValidationWithInvalidUrlPatterns(String invalidUrl) {
            user.setProfilePicture(invalidUrl);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isNotEmpty();
        }

        @ParameterizedTest
        @DisplayName("should pass validation with valid HTTPS URL patterns")
        @ValueSource(strings = {
                "https://example.com/image.jpg",
                "https://sub.example.com/path/to/image.png",
                "https://example.com/image?size=large&format=jpg",
                "https://cdn.example.com/images/profile.webp"
        })
        void shouldPassValidationWithValidHttpsUrlPatterns(String validUrl) {
            user.setProfilePicture(validUrl);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== Phone Number Validation Tests ====================

    @Nested
    @DisplayName("Phone Number Validation")
    class PhoneNumberValidation {

        @Test
        @DisplayName("should pass validation with valid phone number")
        void shouldPassValidationWithValidPhoneNumber() {
            user.setPhoneNumber("+1234567890");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation when phone number is null")
        void shouldPassValidationWhenPhoneNumberIsNull() {
            user.setPhoneNumber(null);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when phone number exceeds 25 characters")
        void shouldFailValidationWhenPhoneNumberExceeds25Characters() {
            user.setPhoneNumber("+1234567890123456789012345678");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Phone number cannot exceed 25 characters"));
        }

        @ParameterizedTest
        @DisplayName("should pass validation with valid phone number formats")
        @ValueSource(strings = {
                "+1234567890",
                "123-456-7890",
                "(123)456-7890",
                "+48123456789",
                "1234567"
        })
        void shouldPassValidationWithValidPhoneNumberFormats(String validPhone) {
            user.setPhoneNumber(validPhone);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @DisplayName("should fail validation with invalid phone number formats")
        @ValueSource(strings = {
                "invalid",
                "abc123",
                "123",
                "++1234567890",
                "phone: 1234567890"
        })
        void shouldFailValidationWithInvalidPhoneNumberFormats(String invalidPhone) {
            user.setPhoneNumber(invalidPhone);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Invalid phone number format"));
        }
    }

    // ==================== Note From Admin Validation Tests ====================

    @Nested
    @DisplayName("Note From Admin Validation")
    class NoteFromAdminValidation {

        @Test
        @DisplayName("should pass validation with valid note from admin")
        void shouldPassValidationWithValidNoteFromAdmin() {
            user.setNoteFromAdmin("This is a valid admin note.");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation when note from admin is null")
        void shouldPassValidationWhenNoteFromAdminIsNull() {
            user.setNoteFromAdmin(null);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when note from admin exceeds 1000 characters")
        void shouldFailValidationWhenNoteFromAdminExceeds1000Characters() {
            user.setNoteFromAdmin("a".repeat(1001));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Note from admin cannot exceed 1000 characters"));
        }

        @Test
        @DisplayName("should pass validation when note from admin is exactly 1000 characters")
        void shouldPassValidationWhenNoteFromAdminIsExactly1000Characters() {
            user.setNoteFromAdmin("a".repeat(1000));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== Account Status Validation Tests ====================

    @Nested
    @DisplayName("Account Status Validation")
    class AccountStatusValidation {

        @Test
        @DisplayName("should fail validation when account status is null")
        void shouldFailValidationWhenAccountStatusIsNull() {
            user.setAccountStatus(null);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Account Status cannot be blank"));
        }

        @ParameterizedTest
        @DisplayName("should pass validation for all valid account statuses")
        @EnumSource(AccountStatus.class)
        void shouldPassValidationForAllValidAccountStatuses(AccountStatus status) {
            user.setAccountStatus(status);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should have default account status as IN_VALIDATION")
        void shouldHaveDefaultAccountStatusAsInValidation() {
            User newUser = new User();

            assertThat(newUser.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
        }
    }

    // ==================== Updater ID Validation Tests ====================

    @Nested
    @DisplayName("Updater ID Validation")
    class UpdaterIdValidation {

        @Test
        @DisplayName("should pass validation with valid updater ID")
        void shouldPassValidationWithValidUpdaterId() {
            user.setUpdaterId("admin123");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation when updater ID is null")
        void shouldPassValidationWhenUpdaterIdIsNull() {
            user.setUpdaterId(null);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when updater ID exceeds 255 characters")
        void shouldFailValidationWhenUpdaterIdExceeds255Characters() {
            user.setUpdaterId("a".repeat(256));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Updater ID cannot exceed 255 characters"));
        }

        @ParameterizedTest
        @DisplayName("should fail validation when updater ID contains invalid characters")
        @ValueSource(strings = {"invalid@id", "invalid#id", "invalid id", "invalid$id"})
        void shouldFailValidationWhenUpdaterIdContainsInvalidCharacters(String invalidId) {
            user.setUpdaterId(invalidId);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Updater ID must contain only letters, numbers, hyphens, underscores, and dots"));
        }

        @ParameterizedTest
        @DisplayName("should pass validation with valid updater ID patterns")
        @ValueSource(strings = {"admin123", "user-name", "user_name", "user.name", "ABC123"})
        void shouldPassValidationWithValidUpdaterIdPatterns(String validId) {
            user.setUpdaterId(validId);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== Company Description Validation Tests ====================

    @Nested
    @DisplayName("Company Description Validation")
    class CompanyDescriptionValidation {

        @Test
        @DisplayName("should pass validation with valid company description")
        void shouldPassValidationWithValidCompanyDescription() {
            user.setCompanyDescription("A great company that does amazing things.");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation when company description is null")
        void shouldPassValidationWhenCompanyDescriptionIsNull() {
            user.setCompanyDescription(null);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when company description exceeds 1000 characters")
        void shouldFailValidationWhenCompanyDescriptionExceeds1000Characters() {
            user.setCompanyDescription("a".repeat(1001));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Company description cannot exceed 1000 characters"));
        }

        @Test
        @DisplayName("should pass validation when company description is exactly 1000 characters")
        void shouldPassValidationWhenCompanyDescriptionIsExactly1000Characters() {
            user.setCompanyDescription("a".repeat(1000));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== NIP Validation Tests ====================

    @Nested
    @DisplayName("NIP Validation")
    class NipValidation {

        @Test
        @DisplayName("should pass validation with valid NIP")
        void shouldPassValidationWithValidNip() {
            user.setNip("1234567890");

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation when NIP is null")
        void shouldPassValidationWhenNipIsNull() {
            user.setNip(null);

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when NIP exceeds 20 characters")
        void shouldFailValidationWhenNipExceeds20Characters() {
            user.setNip("a".repeat(21));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("NIP cannot exceed 20 characters"));
        }

        @Test
        @DisplayName("should pass validation when NIP is exactly 20 characters")
        void shouldPassValidationWhenNipIsExactly20Characters() {
            user.setNip("a".repeat(20));

            Set<ConstraintViolation<User>> violations = validator.validate(user);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== Getter/Setter Tests ====================

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("should get and set id")
        void shouldGetAndSetId() {
            user.setId(123L);

            assertThat(user.getId()).isEqualTo(123L);
        }

        @Test
        @DisplayName("should get and set firebase user id")
        void shouldGetAndSetFirebaseUserId() {
            user.setFirebaseUserId("newFirebaseId");

            assertThat(user.getFirebaseUserId()).isEqualTo("newFirebaseId");
        }

        @Test
        @DisplayName("should get and set user type")
        void shouldGetAndSetUserType() {
            user.setUserType(UserType.COMPANY);

            assertThat(user.getUserType()).isEqualTo(UserType.COMPANY);
        }

        @Test
        @DisplayName("should get and set email")
        void shouldGetAndSetEmail() {
            user.setEmail("newemail@example.com");

            assertThat(user.getEmail()).isEqualTo("newemail@example.com");
        }

        @Test
        @DisplayName("should get and set first name")
        void shouldGetAndSetFirstName() {
            user.setFirstName("Jane");

            assertThat(user.getFirstName()).isEqualTo("Jane");
        }

        @Test
        @DisplayName("should get and set last name")
        void shouldGetAndSetLastName() {
            user.setLastName("Smith");

            assertThat(user.getLastName()).isEqualTo("Smith");
        }

        @Test
        @DisplayName("should get and set name")
        void shouldGetAndSetName() {
            user.setName("Full Name");

            assertThat(user.getName()).isEqualTo("Full Name");
        }

        @Test
        @DisplayName("should get and set profile picture")
        void shouldGetAndSetProfilePicture() {
            user.setProfilePicture("https://example.com/pic.jpg");

            assertThat(user.getProfilePicture()).isEqualTo("https://example.com/pic.jpg");
        }

        @Test
        @DisplayName("should get and set phone number")
        void shouldGetAndSetPhoneNumber() {
            user.setPhoneNumber("+123456789");

            assertThat(user.getPhoneNumber()).isEqualTo("+123456789");
        }

        @Test
        @DisplayName("should get and set note from admin")
        void shouldGetAndSetNoteFromAdmin() {
            user.setNoteFromAdmin("Important note");

            assertThat(user.getNoteFromAdmin()).isEqualTo("Important note");
        }

        @Test
        @DisplayName("should get and set account status")
        void shouldGetAndSetAccountStatus() {
            user.setAccountStatus(AccountStatus.BANNED);

            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.BANNED);
        }

        @Test
        @DisplayName("should get and set version")
        void shouldGetAndSetVersion() {
            user.setVersion(5L);

            assertThat(user.getVersion()).isEqualTo(5L);
        }

        @Test
        @DisplayName("should get and set token version")
        void shouldGetAndSetTokenVersion() {
            user.setTokenVersion(10L);

            assertThat(user.getTokenVersion()).isEqualTo(10L);
        }

        @Test
        @DisplayName("should get and set created time")
        void shouldGetAndSetCreatedTime() {
            LocalDateTime now = LocalDateTime.now();
            user.setCreatedTime(now);

            assertThat(user.getCreatedTime()).isEqualTo(now);
        }

        @Test
        @DisplayName("should get and set last update time")
        void shouldGetAndSetLastUpdateTime() {
            LocalDateTime now = LocalDateTime.now();
            user.setLastUpdateTime(now);

            assertThat(user.getLastUpdateTime()).isEqualTo(now);
        }

        @Test
        @DisplayName("should get and set deleted at")
        void shouldGetAndSetDeletedAt() {
            LocalDateTime now = LocalDateTime.now();
            user.setDeletedAt(now);

            assertThat(user.getDeletedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("should get and set updater id")
        void shouldGetAndSetUpdaterId() {
            user.setUpdaterId("admin123");

            assertThat(user.getUpdaterId()).isEqualTo("admin123");
        }

        @Test
        @DisplayName("should get and set company description")
        void shouldGetAndSetCompanyDescription() {
            user.setCompanyDescription("Great company");

            assertThat(user.getCompanyDescription()).isEqualTo("Great company");
        }

        @Test
        @DisplayName("should get and set NIP")
        void shouldGetAndSetNip() {
            user.setNip("1234567890");

            assertThat(user.getNip()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("should get and set premium")
        void shouldGetAndSetPremium() {
            user.setPremium(true);

            assertThat(user.getPremium()).isTrue();
        }
    }

    // ==================== Token Version Tests ====================

    @Nested
    @DisplayName("Token Version Tests")
    class TokenVersionTests {

        @Test
        @DisplayName("should have default token version of 1")
        void shouldHaveDefaultTokenVersionOf1() {
            User newUser = new User();

            assertThat(newUser.getTokenVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should increment token version")
        void shouldIncrementTokenVersion() {
            user.setTokenVersion(1L);

            user.incrementTokenVersion();

            assertThat(user.getTokenVersion()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should increment token version multiple times")
        void shouldIncrementTokenVersionMultipleTimes() {
            user.setTokenVersion(1L);

            user.incrementTokenVersion();
            user.incrementTokenVersion();
            user.incrementTokenVersion();

            assertThat(user.getTokenVersion()).isEqualTo(4L);
        }
    }

    // ==================== Premium Default Tests ====================

    @Nested
    @DisplayName("Premium Default Tests")
    class PremiumDefaultTests {

        @Test
        @DisplayName("should have default premium as false")
        void shouldHaveDefaultPremiumAsFalse() {
            User newUser = new User();

            assertThat(newUser.getPremium()).isFalse();
        }
    }

    // ==================== Addresses Relationship Tests ====================

    @Nested
    @DisplayName("Addresses Relationship Tests")
    class AddressesRelationshipTests {

        @Test
        @DisplayName("should initialize addresses list as empty")
        void shouldInitializeAddressesListAsEmpty() {
            User newUser = new User();

            assertThat(newUser.getAddresses()).isNotNull();
            assertThat(newUser.getAddresses()).isEmpty();
        }

        @Test
        @DisplayName("should add address to user")
        void shouldAddAddressToUser() {
            Address address = new Address();
            address.setStreet("123 Main St");
            address.setUser(user);

            user.getAddresses().add(address);

            assertThat(user.getAddresses()).hasSize(1);
            assertThat(user.getAddresses().get(0)).isEqualTo(address);
        }

        @Test
        @DisplayName("should add multiple addresses to user")
        void shouldAddMultipleAddressesToUser() {
            Address address1 = new Address();
            address1.setStreet("123 Main St");
            address1.setUser(user);

            Address address2 = new Address();
            address2.setStreet("456 Oak Ave");
            address2.setUser(user);

            user.getAddresses().add(address1);
            user.getAddresses().add(address2);

            assertThat(user.getAddresses()).hasSize(2);
        }

        @Test
        @DisplayName("should remove address from user")
        void shouldRemoveAddressFromUser() {
            Address address = new Address();
            address.setStreet("123 Main St");
            address.setUser(user);

            user.getAddresses().add(address);
            user.getAddresses().remove(address);

            assertThat(user.getAddresses()).isEmpty();
        }

        @Test
        @DisplayName("should set addresses list")
        void shouldSetAddressesList() {
            List<Address> addresses = new ArrayList<>();
            Address address = new Address();
            address.setStreet("123 Main St");
            addresses.add(address);

            user.setAddresses(addresses);

            assertThat(user.getAddresses()).hasSize(1);
        }

        @Test
        @DisplayName("should maintain bidirectional relationship with address")
        void shouldMaintainBidirectionalRelationshipWithAddress() {
            Address address = new Address();
            address.setStreet("123 Main St");
            address.setUser(user);
            user.getAddresses().add(address);

            assertThat(address.getUser()).isEqualTo(user);
            assertThat(user.getAddresses()).contains(address);
        }
    }

    // ==================== Social Connections Relationship Tests ====================

    @Nested
    @DisplayName("Social Connections Relationship Tests")
    class SocialConnectionsRelationshipTests {

        @Test
        @DisplayName("should initialize social connections list as empty")
        void shouldInitializeSocialConnectionsListAsEmpty() {
            User newUser = new User();

            assertThat(newUser.getSocialConnections()).isNotNull();
            assertThat(newUser.getSocialConnections()).isEmpty();
        }

        @Test
        @DisplayName("should add social connection to user")
        void shouldAddSocialConnectionToUser() {
            UserSocialConnection connection = new UserSocialConnection();
            connection.setSocialUserId("social123");
            connection.setUser(user);

            user.getSocialConnections().add(connection);

            assertThat(user.getSocialConnections()).hasSize(1);
            assertThat(user.getSocialConnections().get(0)).isEqualTo(connection);
        }

        @Test
        @DisplayName("should add multiple social connections to user")
        void shouldAddMultipleSocialConnectionsToUser() {
            UserSocialConnection connection1 = new UserSocialConnection();
            connection1.setSocialUserId("social123");
            connection1.setUser(user);

            UserSocialConnection connection2 = new UserSocialConnection();
            connection2.setSocialUserId("social456");
            connection2.setUser(user);

            user.getSocialConnections().add(connection1);
            user.getSocialConnections().add(connection2);

            assertThat(user.getSocialConnections()).hasSize(2);
        }

        @Test
        @DisplayName("should remove social connection from user")
        void shouldRemoveSocialConnectionFromUser() {
            UserSocialConnection connection = new UserSocialConnection();
            connection.setSocialUserId("social123");
            connection.setUser(user);

            user.getSocialConnections().add(connection);
            user.getSocialConnections().remove(connection);

            assertThat(user.getSocialConnections()).isEmpty();
        }

        @Test
        @DisplayName("should set social connections list")
        void shouldSetSocialConnectionsList() {
            List<UserSocialConnection> connections = new ArrayList<>();
            UserSocialConnection connection = new UserSocialConnection();
            connection.setSocialUserId("social123");
            connections.add(connection);

            user.setSocialConnections(connections);

            assertThat(user.getSocialConnections()).hasSize(1);
        }

        @Test
        @DisplayName("should maintain bidirectional relationship with social connection")
        void shouldMaintainBidirectionalRelationshipWithSocialConnection() {
            UserSocialConnection connection = new UserSocialConnection();
            connection.setSocialUserId("social123");
            connection.setUser(user);
            user.getSocialConnections().add(connection);

            assertThat(connection.getUser()).isEqualTo(user);
            assertThat(user.getSocialConnections()).contains(connection);
        }
    }

    // ==================== UserType Enum Tests ====================

    @Nested
    @DisplayName("UserType Enum Tests")
    class UserTypeEnumTests {

        @Test
        @DisplayName("should have ADMIN user type")
        void shouldHaveAdminUserType() {
            assertThat(UserType.valueOf("ADMIN")).isEqualTo(UserType.ADMIN);
        }

        @Test
        @DisplayName("should have PENDING_ADMIN user type")
        void shouldHavePendingAdminUserType() {
            assertThat(UserType.valueOf("PENDING_ADMIN")).isEqualTo(UserType.PENDING_ADMIN);
        }

        @Test
        @DisplayName("should have INFLUENCER user type")
        void shouldHaveInfluencerUserType() {
            assertThat(UserType.valueOf("INFLUENCER")).isEqualTo(UserType.INFLUENCER);
        }

        @Test
        @DisplayName("should have COMPANY user type")
        void shouldHaveCompanyUserType() {
            assertThat(UserType.valueOf("COMPANY")).isEqualTo(UserType.COMPANY);
        }

        @Test
        @DisplayName("should have exactly 4 user types")
        void shouldHaveExactly4UserTypes() {
            assertThat(UserType.values()).hasSize(4);
        }
    }

    // ==================== AccountStatus Enum Tests ====================

    @Nested
    @DisplayName("AccountStatus Enum Tests")
    class AccountStatusEnumTests {

        @Test
        @DisplayName("should have INACTIVE status")
        void shouldHaveInactiveStatus() {
            assertThat(AccountStatus.valueOf("INACTIVE")).isEqualTo(AccountStatus.INACTIVE);
        }

        @Test
        @DisplayName("should have IN_VALIDATION status")
        void shouldHaveInValidationStatus() {
            assertThat(AccountStatus.valueOf("IN_VALIDATION")).isEqualTo(AccountStatus.IN_VALIDATION);
        }

        @Test
        @DisplayName("should have ACTIVE status")
        void shouldHaveActiveStatus() {
            assertThat(AccountStatus.valueOf("ACTIVE")).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("should have BANNED status")
        void shouldHaveBannedStatus() {
            assertThat(AccountStatus.valueOf("BANNED")).isEqualTo(AccountStatus.BANNED);
        }

        @Test
        @DisplayName("should have TO_BE_DELETED status")
        void shouldHaveToBeDeletedStatus() {
            assertThat(AccountStatus.valueOf("TO_BE_DELETED")).isEqualTo(AccountStatus.TO_BE_DELETED);
        }

        @Test
        @DisplayName("should have DELETED status")
        void shouldHaveDeletedStatus() {
            assertThat(AccountStatus.valueOf("DELETED")).isEqualTo(AccountStatus.DELETED);
        }

        @Test
        @DisplayName("should have exactly 7 account statuses")
        void shouldHaveExactly6AccountStatuses() {
            assertThat(AccountStatus.values()).hasSize(7);
        }

        @Test
        @DisplayName("ACTIVE status should return true for isActive")
        void activeStatusShouldReturnTrueForIsActive() {
            assertThat(AccountStatus.ACTIVE.isActive()).isTrue();
        }

        @Test
        @DisplayName("non-ACTIVE status should return false for isActive")
        void nonActiveStatusShouldReturnFalseForIsActive() {
            assertThat(AccountStatus.INACTIVE.isActive()).isFalse();
            assertThat(AccountStatus.BANNED.isActive()).isFalse();
            assertThat(AccountStatus.DELETED.isActive()).isFalse();
        }

        @Test
        @DisplayName("ACTIVE and IN_VALIDATION should allow login")
        void activeAndInValidationShouldAllowLogin() {
            assertThat(AccountStatus.ACTIVE.canLogin()).isTrue();
            assertThat(AccountStatus.IN_VALIDATION.canLogin()).isTrue();
        }

        @Test
        @DisplayName("BANNED and DELETED should not allow login")
        void bannedAndDeletedShouldNotAllowLogin() {
            assertThat(AccountStatus.BANNED.canLogin()).isFalse();
            assertThat(AccountStatus.DELETED.canLogin()).isFalse();
        }

        @Test
        @DisplayName("only DELETED should be terminal")
        void deletedAndBannedShouldBeTerminal() {
            assertThat(AccountStatus.DELETED.isTerminal()).isTrue();
            assertThat(AccountStatus.BANNED.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("ACTIVE should not be terminal")
        void activeShouldNotBeTerminal() {
            assertThat(AccountStatus.ACTIVE.isTerminal()).isFalse();
        }
    }

    // ==================== Account Status Transitions Tests ====================

    @Nested
    @DisplayName("Account Status Transitions Tests")
    class AccountStatusTransitionsTests {

        @Test
        @DisplayName("INACTIVE can transition to IN_VALIDATION")
        void inactiveCanTransitionToInValidation() {
            assertThat(AccountStatus.INACTIVE.canTransitionTo(AccountStatus.IN_VALIDATION)).isTrue();
        }

        @Test
        @DisplayName("INACTIVE can transition to TO_BE_DELETED")
        void inactiveCanTransitionToToBeDeleted() {
            assertThat(AccountStatus.INACTIVE.canTransitionTo(AccountStatus.TO_BE_DELETED)).isTrue();
        }

        @Test
        @DisplayName("INACTIVE can transition to BANNED")
        void inactiveCanTransitionToBanned() {
            assertThat(AccountStatus.INACTIVE.canTransitionTo(AccountStatus.BANNED)).isTrue();
        }

        @Test
        @DisplayName("IN_VALIDATION can transition to ACTIVE")
        void inValidationCanTransitionToActive() {
            assertThat(AccountStatus.IN_VALIDATION.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE can transition to INACTIVE")
        void activeCanTransitionToInactive() {
            assertThat(AccountStatus.ACTIVE.canTransitionTo(AccountStatus.INACTIVE)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE can transition to BANNED")
        void activeCanTransitionToBanned() {
            assertThat(AccountStatus.ACTIVE.canTransitionTo(AccountStatus.BANNED)).isTrue();
        }

        @Test
        @DisplayName("BANNED can transition to ACTIVE")
        void bannedCanTransitionToActive() {
            assertThat(AccountStatus.BANNED.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("DELETED cannot transition to any status")
        void deletedCannotTransitionToAnyStatus() {
            assertThat(AccountStatus.DELETED.canTransitionTo(AccountStatus.ACTIVE)).isFalse();
            assertThat(AccountStatus.DELETED.canTransitionTo(AccountStatus.INACTIVE)).isFalse();
            assertThat(AccountStatus.DELETED.canTransitionTo(AccountStatus.BANNED)).isFalse();
        }

        @Test
        @DisplayName("TO_BE_DELETED can transition to DELETED")
        void toBeDeletedCanTransitionToDeleted() {
            assertThat(AccountStatus.TO_BE_DELETED.canTransitionTo(AccountStatus.DELETED)).isTrue();
        }

        @Test
        @DisplayName("TO_BE_DELETED can transition to ACTIVE for reactivation")
        void toBeDeletedCanTransitionToActiveForReactivation() {
            assertThat(AccountStatus.TO_BE_DELETED.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("getPossibleTransitions returns correct values for ACTIVE")
        void getPossibleTransitionsReturnsCorrectValuesForActive() {
            List<AccountStatus> transitions = AccountStatus.ACTIVE.getPossibleTransitions();

            assertThat(transitions).contains(AccountStatus.INACTIVE, AccountStatus.TO_BE_DELETED, AccountStatus.BANNED);
        }
    }

    // ==================== All Args Constructor Tests ====================

    @Nested
    @DisplayName("All Args Constructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("should create user with all args constructor")
        void shouldCreateUserWithAllArgsConstructor() {
            LocalDateTime now = LocalDateTime.now();
            List<Address> addresses = new ArrayList<>();
            List<UserSocialConnection> socialConnections = new ArrayList<>();

            User fullUser = new User(
                    1L,                                    // id
                    "firebaseId123",                       // firebaseUserId
                    UserType.INFLUENCER,                   // userType
                    "test@example.com",                    // email
                    "John",                                // firstName
                    "Doe",                                 // lastName
                    "John Doe",                            // name
                    "https://example.com/pic.jpg",         // profilePicture
                    addresses,                             // addresses
                    "+1234567890",                         // phoneNumber
                    "Admin note",                          // noteFromAdmin
                    AccountStatus.ACTIVE,                  // accountStatus
                    1L,                                    // version
                    1L,                                    // tokenVersion
                    socialConnections,                     // socialConnections
                    now,                                   // createdTime
                    now,                                   // lastUpdateTime
                    null,                                  // deletedAt
                    "admin123",                            // updaterId
                    "Company description",                 // companyDescription
                    "1234567890",                          // nip
                    false,                                 // premium
                    false,                                 // emailVerified
                    null,                                  // emailVerificationSentAt
                    null,                                  // emailVerifiedAt
                    null,                                  // lastVerifiedEmail
                    null,                                  // passwordResetSentAt
                    false,                                 // newestConsentsAccepted
                    false                                  // initialAccountSetupCompleted
            );

            assertThat(fullUser.getId()).isEqualTo(1L);
            assertThat(fullUser.getFirebaseUserId()).isEqualTo("firebaseId123");
            assertThat(fullUser.getUserType()).isEqualTo(UserType.INFLUENCER);
            assertThat(fullUser.getEmail()).isEqualTo("test@example.com");
            assertThat(fullUser.getFirstName()).isEqualTo("John");
            assertThat(fullUser.getLastName()).isEqualTo("Doe");
            assertThat(fullUser.getName()).isEqualTo("John Doe");
            assertThat(fullUser.getProfilePicture()).isEqualTo("https://example.com/pic.jpg");
            assertThat(fullUser.getAddresses()).isEqualTo(addresses);
            assertThat(fullUser.getPhoneNumber()).isEqualTo("+1234567890");
            assertThat(fullUser.getNoteFromAdmin()).isEqualTo("Admin note");
            assertThat(fullUser.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(fullUser.getVersion()).isEqualTo(1L);
            assertThat(fullUser.getTokenVersion()).isEqualTo(1L);
            assertThat(fullUser.getSocialConnections()).isEqualTo(socialConnections);
            assertThat(fullUser.getCreatedTime()).isEqualTo(now);
            assertThat(fullUser.getLastUpdateTime()).isEqualTo(now);
            assertThat(fullUser.getDeletedAt()).isNull();
            assertThat(fullUser.getUpdaterId()).isEqualTo("admin123");
            assertThat(fullUser.getCompanyDescription()).isEqualTo("Company description");
            assertThat(fullUser.getNip()).isEqualTo("1234567890");
            assertThat(fullUser.getPremium()).isFalse();
        }
    }

    // ==================== No Args Constructor Tests ====================

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("should create user with no args constructor")
        void shouldCreateUserWithNoArgsConstructor() {
            User newUser = new User();

            assertThat(newUser).isNotNull();
            assertThat(newUser.getId()).isNull();
            assertThat(newUser.getFirebaseUserId()).isNull();
            assertThat(newUser.getUserType()).isNull();
            assertThat(newUser.getEmail()).isNull();
            assertThat(newUser.getFirstName()).isNull();
            assertThat(newUser.getLastName()).isNull();
        }

        @Test
        @DisplayName("should have default values from no args constructor")
        void shouldHaveDefaultValuesFromNoArgsConstructor() {
            User newUser = new User();

            assertThat(newUser.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
            assertThat(newUser.getTokenVersion()).isEqualTo(1L);
            assertThat(newUser.getPremium()).isFalse();
            assertThat(newUser.getAddresses()).isNotNull().isEmpty();
            assertThat(newUser.getSocialConnections()).isNotNull().isEmpty();
        }
    }

    // ==================== UpdaterTracking Interface Tests ====================

    @Nested
    @DisplayName("UpdaterTracking Interface Tests")
    class UpdaterTrackingInterfaceTests {

        @Test
        @DisplayName("should implement UpdaterTracking interface")
        void shouldImplementUpdaterTrackingInterface() {
            assertThat(user).isInstanceOf(com.sm.instagram.platform.common.base.UpdaterTracking.class);
        }

        @Test
        @DisplayName("should set updater ID via interface method")
        void shouldSetUpdaterIdViaInterfaceMethod() {
            com.sm.instagram.platform.common.base.UpdaterTracking tracking = user;

            tracking.setUpdaterId("interfaceUpdater");

            assertThat(user.getUpdaterId()).isEqualTo("interfaceUpdater");
        }
    }

    // ==================== Combined Validation Tests ====================

    @Nested
    @DisplayName("Combined Validation Tests")
    class CombinedValidationTests {

        @Test
        @DisplayName("should pass validation with minimum valid user")
        void shouldPassValidationWithMinimumValidUser() {
            User minUser = new User();
            minUser.setFirebaseUserId("validId");
            minUser.setUserType(UserType.INFLUENCER);
            minUser.setAccountStatus(AccountStatus.ACTIVE);

            Set<ConstraintViolation<User>> violations = validator.validate(minUser);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with fully populated user")
        void shouldPassValidationWithFullyPopulatedUser() {
            User fullUser = new User();
            fullUser.setFirebaseUserId("validFirebaseId123");
            fullUser.setUserType(UserType.COMPANY);
            fullUser.setAccountStatus(AccountStatus.ACTIVE);
            fullUser.setEmail("test@example.com");
            fullUser.setFirstName("John");
            fullUser.setLastName("Doe");
            fullUser.setName("John Doe");
            fullUser.setProfilePicture("https://example.com/pic.jpg");
            fullUser.setPhoneNumber("+1234567890");
            fullUser.setNoteFromAdmin("Admin note");
            fullUser.setUpdaterId("admin123");
            fullUser.setCompanyDescription("A great company");
            fullUser.setNip("1234567890");
            fullUser.setPremium(true);

            Set<ConstraintViolation<User>> violations = validator.validate(fullUser);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation with multiple violations")
        void shouldFailValidationWithMultipleViolations() {
            User invalidUser = new User();
            invalidUser.setFirebaseUserId(null);
            invalidUser.setUserType(null);
            invalidUser.setAccountStatus(null);
            invalidUser.setEmail("invalid-email");
            invalidUser.setFirstName("J");
            invalidUser.setLastName("D");
            invalidUser.setProfilePicture("http://not-https.com");
            invalidUser.setPhoneNumber("invalid");

            Set<ConstraintViolation<User>> violations = validator.validate(invalidUser);

            assertThat(violations).isNotEmpty();
            assertThat(violations.size()).isGreaterThanOrEqualTo(4);
        }
    }
}
