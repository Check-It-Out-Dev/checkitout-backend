package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.user.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for User package - AccountStatus, UserType, ProfileFieldCriticality enums and DTOs.
 */
@DisplayName("User Package Unit Tests")
class UserPackageUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== AccountStatus Enum Tests ====================

    @Nested
    @DisplayName("AccountStatus Enum Tests")
    class AccountStatusEnumTests {

        @Nested
        @DisplayName("Enum Properties Tests")
        class EnumPropertiesTests {

            @Test
            @DisplayName("should have 7 status values")
            void shouldHaveSixStatusValues() {
                assertThat(AccountStatus.values()).hasSize(7);
            }

            @ParameterizedTest
            @EnumSource(AccountStatus.class)
            @DisplayName("all statuses should have non-null colorTheme")
            void allStatusesShouldHaveNonNullColorTheme(AccountStatus status) {
                assertThat(status.getColorTheme()).isNotBlank();
            }

            @ParameterizedTest
            @EnumSource(AccountStatus.class)
            @DisplayName("all statuses should have non-null icon")
            void allStatusesShouldHaveNonNullIcon(AccountStatus status) {
                assertThat(status.getIcon()).isNotBlank();
            }

            @ParameterizedTest
            @EnumSource(AccountStatus.class)
            @DisplayName("all statuses should have aliases")
            void allStatusesShouldHaveAliases(AccountStatus status) {
                assertThat(status.getAliases()).isNotEmpty();
            }

            @ParameterizedTest
            @EnumSource(AccountStatus.class)
            @DisplayName("all statuses should have description")
            void allStatusesShouldHaveDescription(AccountStatus status) {
                assertThat(status.getDescription()).isNotBlank();
            }

            @Test
            @DisplayName("INACTIVE should have correct properties")
            void inactiveShouldHaveCorrectProperties() {
                AccountStatus status = AccountStatus.INACTIVE;
                assertThat(status.getColorTheme()).isEqualTo("secondary");
                assertThat(status.getIcon()).isEqualTo("user-minus");
                assertThat(status.getAliases()).contains("disabled");
            }

            @Test
            @DisplayName("ACTIVE should have correct properties")
            void activeShouldHaveCorrectProperties() {
                AccountStatus status = AccountStatus.ACTIVE;
                assertThat(status.getColorTheme()).isEqualTo("success");
                assertThat(status.getIcon()).isEqualTo("user-check");
                assertThat(status.getAliases()).containsExactlyInAnyOrder("enabled", "verified");
            }

            @Test
            @DisplayName("BANNED should have correct properties")
            void bannedShouldHaveCorrectProperties() {
                AccountStatus status = AccountStatus.BANNED;
                assertThat(status.getColorTheme()).isEqualTo("danger");
                assertThat(status.getIcon()).isEqualTo("ban");
                assertThat(status.getAliases()).containsExactlyInAnyOrder("suspended", "blocked", "prohibited");
            }

            @Test
            @DisplayName("DELETED should have correct properties")
            void deletedShouldHaveCorrectProperties() {
                AccountStatus status = AccountStatus.DELETED;
                assertThat(status.getColorTheme()).isEqualTo("muted");
                assertThat(status.getIcon()).isEqualTo("trash");
                assertThat(status.getAliases()).contains("removed");
            }
        }

        @Nested
        @DisplayName("State Machine Tests - canTransitionTo")
        class CanTransitionToTests {

            @Test
            @DisplayName("INACTIVE can transition to IN_VALIDATION, TO_BE_DELETED, BANNED")
            void inactiveCanTransitionCorrectly() {
                AccountStatus status = AccountStatus.INACTIVE;
                assertThat(status.canTransitionTo(AccountStatus.IN_VALIDATION)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.TO_BE_DELETED)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.BANNED)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.ACTIVE)).isFalse();
                assertThat(status.canTransitionTo(AccountStatus.DELETED)).isFalse();
            }

            @Test
            @DisplayName("IN_VALIDATION can transition to ACTIVE, INACTIVE, TO_BE_DELETED, BANNED")
            void inValidationCanTransitionCorrectly() {
                AccountStatus status = AccountStatus.IN_VALIDATION;
                assertThat(status.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.INACTIVE)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.TO_BE_DELETED)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.BANNED)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.DELETED)).isFalse();
            }

            @Test
            @DisplayName("ACTIVE can transition to INACTIVE, TO_BE_DELETED, BANNED")
            void activeCanTransitionCorrectly() {
                AccountStatus status = AccountStatus.ACTIVE;
                assertThat(status.canTransitionTo(AccountStatus.INACTIVE)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.TO_BE_DELETED)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.BANNED)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.IN_VALIDATION)).isFalse();
                assertThat(status.canTransitionTo(AccountStatus.DELETED)).isFalse();
            }

            @Test
            @DisplayName("BANNED can transition to ACTIVE, TO_BE_DELETED only")
            void bannedCanTransitionCorrectly() {
                AccountStatus status = AccountStatus.BANNED;
                assertThat(status.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.TO_BE_DELETED)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.INACTIVE)).isFalse();
                assertThat(status.canTransitionTo(AccountStatus.IN_VALIDATION)).isFalse();
                assertThat(status.canTransitionTo(AccountStatus.DELETED)).isFalse();
            }

            @Test
            @DisplayName("TO_BE_DELETED can transition to DELETED, ACTIVE, IN_VALIDATION")
            void toBeDeletedCanTransitionCorrectly() {
                AccountStatus status = AccountStatus.TO_BE_DELETED;
                assertThat(status.canTransitionTo(AccountStatus.DELETED)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.IN_VALIDATION)).isTrue();
                assertThat(status.canTransitionTo(AccountStatus.INACTIVE)).isFalse();
                assertThat(status.canTransitionTo(AccountStatus.BANNED)).isFalse();
            }

            @Test
            @DisplayName("DELETED cannot transition to any status")
            void deletedCannotTransition() {
                AccountStatus status = AccountStatus.DELETED;
                for (AccountStatus target : AccountStatus.values()) {
                    assertThat(status.canTransitionTo(target)).isFalse();
                }
            }

            @ParameterizedTest
            @EnumSource(AccountStatus.class)
            @DisplayName("no status can transition to itself")
            void noStatusCanTransitionToItself(AccountStatus status) {
                assertThat(status.canTransitionTo(status)).isFalse();
            }
        }

        @Nested
        @DisplayName("GetPossibleTransitions Tests")
        class GetPossibleTransitionsTests {

            @Test
            @DisplayName("INACTIVE should list correct possible transitions")
            void inactiveShouldListCorrectTransitions() {
                List<AccountStatus> transitions = AccountStatus.INACTIVE.getPossibleTransitions();
                assertThat(transitions).containsExactlyInAnyOrder(
                        AccountStatus.IN_VALIDATION,
                        AccountStatus.TO_BE_DELETED,
                        AccountStatus.BANNED
                );
            }

            @Test
            @DisplayName("DELETED should have no possible transitions")
            void deletedShouldHaveNoTransitions() {
                List<AccountStatus> transitions = AccountStatus.DELETED.getPossibleTransitions();
                assertThat(transitions).isEmpty();
            }

            @Test
            @DisplayName("IN_VALIDATION should list 4 possible transitions")
            void inValidationShouldList4Transitions() {
                List<AccountStatus> transitions = AccountStatus.IN_VALIDATION.getPossibleTransitions();
                assertThat(transitions).hasSize(4);
            }
        }

        @Nested
        @DisplayName("Helper Methods Tests")
        class HelperMethodsTests {

            @Test
            @DisplayName("only ACTIVE should return true for isActive")
            void onlyActiveShouldReturnTrueForIsActive() {
                assertThat(AccountStatus.ACTIVE.isActive()).isTrue();
                assertThat(AccountStatus.INACTIVE.isActive()).isFalse();
                assertThat(AccountStatus.IN_VALIDATION.isActive()).isFalse();
                assertThat(AccountStatus.BANNED.isActive()).isFalse();
                assertThat(AccountStatus.TO_BE_DELETED.isActive()).isFalse();
                assertThat(AccountStatus.DELETED.isActive()).isFalse();
            }

            @Test
            @DisplayName("ACTIVE and IN_VALIDATION can login")
            void activeAndInValidationCanLogin() {
                assertThat(AccountStatus.ACTIVE.canLogin()).isTrue();
                assertThat(AccountStatus.IN_VALIDATION.canLogin()).isTrue();
                assertThat(AccountStatus.INACTIVE.canLogin()).isFalse();
                assertThat(AccountStatus.BANNED.canLogin()).isFalse();
                assertThat(AccountStatus.TO_BE_DELETED.canLogin()).isFalse();
                assertThat(AccountStatus.DELETED.canLogin()).isFalse();
            }

            @Test
            @DisplayName("only DELETED is terminal (BANNED is recoverable)")
            void deletedAndBannedAreTerminal() {
                assertThat(AccountStatus.DELETED.isTerminal()).isTrue();
                assertThat(AccountStatus.BANNED.isTerminal()).isFalse();
                assertThat(AccountStatus.ACTIVE.isTerminal()).isFalse();
                assertThat(AccountStatus.INACTIVE.isTerminal()).isFalse();
                assertThat(AccountStatus.IN_VALIDATION.isTerminal()).isFalse();
                assertThat(AccountStatus.TO_BE_DELETED.isTerminal()).isFalse();
            }
        }

        @Nested
        @DisplayName("JSON Serialization Tests")
        class JsonSerializationTests {

            @ParameterizedTest
            @CsvSource({
                    "INACTIVE, INACTIVE",
                    "IN_VALIDATION, IN_VALIDATION",
                    "ACTIVE, ACTIVE",
                    "BANNED, BANNED",
                    "TO_BE_DELETED, TO_BE_DELETED",
                    "DELETED, DELETED"
            })
            @DisplayName("fromString should parse correctly")
            void fromStringShouldParseCorrectly(String input, AccountStatus expected) {
                assertThat(AccountStatus.fromString(input)).isEqualTo(expected);
            }

            @ParameterizedTest
            @CsvSource({
                    "inactive, INACTIVE",
                    "Active, ACTIVE",
                    "IN_validation, IN_VALIDATION"
            })
            @DisplayName("fromString should be case insensitive")
            void fromStringShouldBeCaseInsensitive(String input, AccountStatus expected) {
                assertThat(AccountStatus.fromString(input)).isEqualTo(expected);
            }

            @Test
            @DisplayName("fromString should throw for invalid value")
            void fromStringShouldThrowForInvalidValue() {
                assertThatThrownBy(() -> AccountStatus.fromString("INVALID"))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @ParameterizedTest
            @EnumSource(AccountStatus.class)
            @DisplayName("getValue should return name")
            void getValueShouldReturnName(AccountStatus status) {
                assertThat(status.getValue()).isEqualTo(status.name());
            }
        }
    }

    // ==================== UserType Enum Tests ====================

    @Nested
    @DisplayName("UserType Enum Tests")
    class UserTypeEnumTests {

        @Test
        @DisplayName("should have 4 user types")
        void shouldHaveFourUserTypes() {
            assertThat(UserType.values()).hasSize(4);
        }

        @Test
        @DisplayName("should contain expected values")
        void shouldContainExpectedValues() {
            assertThat(UserType.values()).containsExactlyInAnyOrder(
                    UserType.ADMIN,
                    UserType.PENDING_ADMIN,
                    UserType.INFLUENCER,
                    UserType.COMPANY
            );
        }

        @ParameterizedTest
        @EnumSource(UserType.class)
        @DisplayName("all user types should have valid name")
        void allUserTypesShouldHaveValidName(UserType type) {
            assertThat(type.name()).isNotBlank();
        }

        @Test
        @DisplayName("valueOf should work for all types")
        void valueOfShouldWorkForAllTypes() {
            assertThat(UserType.valueOf("ADMIN")).isEqualTo(UserType.ADMIN);
            assertThat(UserType.valueOf("PENDING_ADMIN")).isEqualTo(UserType.PENDING_ADMIN);
            assertThat(UserType.valueOf("INFLUENCER")).isEqualTo(UserType.INFLUENCER);
            assertThat(UserType.valueOf("COMPANY")).isEqualTo(UserType.COMPANY);
        }

        @Test
        @DisplayName("valueOf should throw for invalid type")
        void valueOfShouldThrowForInvalidType() {
            assertThatThrownBy(() -> UserType.valueOf("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================== ProfileFieldCriticality Enum Tests ====================

    @Nested
    @DisplayName("ProfileFieldCriticality Enum Tests")
    class ProfileFieldCriticalityEnumTests {

        @Test
        @DisplayName("should have 2 criticality levels")
        void shouldHaveTwoCriticalityLevels() {
            assertThat(ProfileFieldCriticality.values()).hasSize(2);
        }

        @Test
        @DisplayName("should contain CRITICAL and NON_CRITICAL")
        void shouldContainExpectedValues() {
            assertThat(ProfileFieldCriticality.values()).containsExactlyInAnyOrder(
                    ProfileFieldCriticality.CRITICAL,
                    ProfileFieldCriticality.NON_CRITICAL
            );
        }

        @Nested
        @DisplayName("Critical Fields Tests")
        class CriticalFieldsTests {

            @ParameterizedTest
            @ValueSource(strings = {"firstName", "lastName", "email", "phoneNumber", "name", "nip"})
            @DisplayName("critical fields should be identified correctly")
            void criticalFieldsShouldBeIdentifiedCorrectly(String fieldName) {
                assertThat(ProfileFieldCriticality.forField(fieldName))
                        .isEqualTo(ProfileFieldCriticality.CRITICAL);
                assertThat(ProfileFieldCriticality.isCriticalField(fieldName)).isTrue();
            }

            @Test
            @DisplayName("getCriticalFields should return all critical fields")
            void getCriticalFieldsShouldReturnAllCriticalFields() {
                Set<String> criticalFields = ProfileFieldCriticality.getCriticalFields();
                assertThat(criticalFields).containsExactlyInAnyOrder(
                        "firstName", "lastName", "email", "phoneNumber", "name", "nip"
                );
            }
        }

        @Nested
        @DisplayName("Non-Critical Fields Tests")
        class NonCriticalFieldsTests {

            @ParameterizedTest
            @ValueSource(strings = {"profilePicture", "companyDescription", "addresses", "addressesIds", "addressIds"})
            @DisplayName("non-critical fields should be identified correctly")
            void nonCriticalFieldsShouldBeIdentifiedCorrectly(String fieldName) {
                assertThat(ProfileFieldCriticality.forField(fieldName))
                        .isEqualTo(ProfileFieldCriticality.NON_CRITICAL);
                assertThat(ProfileFieldCriticality.isCriticalField(fieldName)).isFalse();
            }

            @Test
            @DisplayName("getNonCriticalFields should return all non-critical fields")
            void getNonCriticalFieldsShouldReturnAllNonCriticalFields() {
                Set<String> nonCriticalFields = ProfileFieldCriticality.getNonCriticalFields();
                assertThat(nonCriticalFields).containsExactlyInAnyOrder(
                        "profilePicture", "companyDescription", "addresses", "addressesIds", "addressIds"
                );
            }
        }

        @Nested
        @DisplayName("Unknown Fields Tests")
        class UnknownFieldsTests {

            @ParameterizedTest
            @ValueSource(strings = {"unknownField", "randomField", "someOtherField", ""})
            @DisplayName("unknown fields should default to NON_CRITICAL")
            void unknownFieldsShouldDefaultToNonCritical(String fieldName) {
                assertThat(ProfileFieldCriticality.forField(fieldName))
                        .isEqualTo(ProfileFieldCriticality.NON_CRITICAL);
            }

            @Test
            @DisplayName("null field should throw NullPointerException")
            void nullFieldShouldThrowNullPointerException() {
                // Set.contains(null) throws NullPointerException for immutable sets
                assertThatThrownBy(() -> ProfileFieldCriticality.forField(null))
                        .isInstanceOf(NullPointerException.class);
            }
        }
    }

    // ==================== UserDtoIn Validation Tests ====================

    @Nested
    @DisplayName("UserDtoIn Validation Tests")
    class UserDtoInValidationTests {

        private UserDtoIn createValidUserDtoIn() {
            UserDtoIn dto = new UserDtoIn();
            dto.setEmail("user@example.com");
            dto.setUserType(UserType.INFLUENCER);
            dto.setAccountStatus(AccountStatus.ACTIVE);
            return dto;
        }

        @Test
        @DisplayName("should pass validation with minimum required fields")
        void shouldPassValidationWithMinimumFields() {
            UserDtoIn dto = createValidUserDtoIn();

            Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with all fields populated")
        void shouldPassValidationWithAllFields() {
            UserDtoIn dto = createValidUserDtoIn();
            dto.setId(1L);
            dto.setFirebaseUserId("firebase-uid-123");
            dto.setFirstName("John");
            dto.setLastName("Doe");
            dto.setName("John Doe");
            dto.setProfilePicture("https://example.com/image.jpg");
            dto.setPhoneNumber("+48123456789");
            dto.setNoteFromAdmin("Admin note here");

            Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Nested
        @DisplayName("Email Validation")
        class EmailValidationTests {

            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = {"  ", "\t"})
            @DisplayName("should fail when email is blank")
            void shouldFailWhenEmailIsBlank(String email) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setEmail(email);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
            }

            @ParameterizedTest
            @ValueSource(strings = {"invalid", "invalid@", "@example.com", "invalid email@test.com"})
            @DisplayName("should fail with invalid email format")
            void shouldFailWithInvalidEmailFormat(String email) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setEmail(email);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
            }

            @ParameterizedTest
            @ValueSource(strings = {"user@example.com", "test.user@domain.co.uk", "a@b.pl"})
            @DisplayName("should accept valid email formats")
            void shouldAcceptValidEmailFormats(String email) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setEmail(email);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }

            @Test
            @DisplayName("should reject email exceeding max length")
            void shouldRejectEmailExceedingMaxLength() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setEmail("a".repeat(250) + "@b.com"); // Over 255 chars

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
            }
        }

        @Nested
        @DisplayName("UserType Validation")
        class UserTypeValidationTests {

            @Test
            @DisplayName("should fail when userType is null")
            void shouldFailWhenUserTypeIsNull() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setUserType(null);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("userType"));
            }

            @ParameterizedTest
            @EnumSource(UserType.class)
            @DisplayName("should accept all user types")
            void shouldAcceptAllUserTypes(UserType type) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setUserType(type);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }
        }

        @Nested
        @DisplayName("AccountStatus Validation")
        class AccountStatusValidationTests {

            @Test
            @DisplayName("should fail when accountStatus is null")
            void shouldFailWhenAccountStatusIsNull() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setAccountStatus(null);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("accountStatus"));
            }

            @ParameterizedTest
            @EnumSource(AccountStatus.class)
            @DisplayName("should accept all account statuses")
            void shouldAcceptAllAccountStatuses(AccountStatus status) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setAccountStatus(status);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }
        }

        @Nested
        @DisplayName("FirebaseUserId Validation")
        class FirebaseUserIdValidationTests {

            @ParameterizedTest
            @ValueSource(strings = {"valid_user_id", "user-123", "user.name.123", "ABC123"})
            @DisplayName("should accept valid firebase user IDs")
            void shouldAcceptValidFirebaseUserIds(String uid) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setFirebaseUserId(uid);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }

            @ParameterizedTest
            @ValueSource(strings = {"invalid uid", "user@id", "user#123", "user$id"})
            @DisplayName("should reject invalid firebase user ID patterns")
            void shouldRejectInvalidFirebaseUserIdPatterns(String uid) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setFirebaseUserId(uid);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("firebaseUserId"));
            }

            @Test
            @DisplayName("should reject firebase user ID exceeding max length")
            void shouldRejectFirebaseUserIdExceedingMaxLength() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setFirebaseUserId("a".repeat(256));

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("firebaseUserId"));
            }

            @Test
            @DisplayName("should accept null firebase user ID")
            void shouldAcceptNullFirebaseUserId() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setFirebaseUserId(null);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }
        }

        @Nested
        @DisplayName("Name Fields Validation")
        class NameFieldsValidationTests {

            @ParameterizedTest
            @ValueSource(strings = {"J", "A"})
            @DisplayName("should reject firstName shorter than 2 chars")
            void shouldRejectFirstNameShorterThanMinLength(String name) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setFirstName(name);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("firstName"));
            }

            @Test
            @DisplayName("should reject firstName longer than 50 chars")
            void shouldRejectFirstNameLongerThanMaxLength() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setFirstName("A".repeat(51));

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("firstName"));
            }

            @ParameterizedTest
            @ValueSource(strings = {"Jo", "John", "Alexander"})
            @DisplayName("should accept valid firstName lengths")
            void shouldAcceptValidFirstNameLengths(String name) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setFirstName(name);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }

            @Test
            @DisplayName("should accept firstName at max length")
            void shouldAcceptFirstNameAtMaxLength() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setFirstName("A".repeat(50));

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }

            @ParameterizedTest
            @ValueSource(strings = {"D", "X"})
            @DisplayName("should reject lastName shorter than 2 chars")
            void shouldRejectLastNameShorterThanMinLength(String name) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setLastName(name);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("lastName"));
            }

            @Test
            @DisplayName("should accept null firstName and lastName")
            void shouldAcceptNullNames() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setFirstName(null);
                dto.setLastName(null);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }
        }

        @Nested
        @DisplayName("ProfilePicture URL Validation")
        class ProfilePictureValidationTests {

            @ParameterizedTest
            @ValueSource(strings = {
                    "https://example.com/image.jpg",
                    "https://cdn.example.com/path/to/image.png",
                    "https://storage.googleapis.com/bucket/image.gif"
            })
            @DisplayName("should accept valid HTTPS URLs")
            void shouldAcceptValidHttpsUrls(String url) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setProfilePicture(url);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }

            @ParameterizedTest
            @ValueSource(strings = {
                    "http://example.com/image.jpg",
                    "ftp://example.com/image.jpg",
                    "file:///path/to/image.jpg"
            })
            @DisplayName("should reject non-HTTPS URLs")
            void shouldRejectNonHttpsUrls(String url) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setProfilePicture(url);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("profilePicture"));
            }

            @Test
            @DisplayName("should reject URL exceeding max length")
            void shouldRejectUrlExceedingMaxLength() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setProfilePicture("https://example.com/" + "a".repeat(2030));

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("profilePicture"));
            }

            @Test
            @DisplayName("should accept null profile picture")
            void shouldAcceptNullProfilePicture() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setProfilePicture(null);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }
        }

        @Nested
        @DisplayName("PhoneNumber Validation")
        class PhoneNumberValidationTests {

            @ParameterizedTest
            @ValueSource(strings = {
                    "+48123456789",
                    "123-456-789",
                    "(123)456-7890",
                    "+1-555-123-4567"
            })
            @DisplayName("should accept valid phone numbers")
            void shouldAcceptValidPhoneNumbers(String phone) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setPhoneNumber(phone);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }

            @ParameterizedTest
            @ValueSource(strings = {"abc123", "12345", "phone@number"})
            @DisplayName("should reject invalid phone number formats")
            void shouldRejectInvalidPhoneNumberFormats(String phone) {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setPhoneNumber(phone);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phoneNumber"));
            }

            @Test
            @DisplayName("should reject phone number exceeding max length")
            void shouldRejectPhoneNumberExceedingMaxLength() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setPhoneNumber("+".repeat(26));

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phoneNumber"));
            }
        }

        @Nested
        @DisplayName("NoteFromAdmin Validation")
        class NoteFromAdminValidationTests {

            @Test
            @DisplayName("should accept note at max length")
            void shouldAcceptNoteAtMaxLength() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setNoteFromAdmin("A".repeat(1000));

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }

            @Test
            @DisplayName("should reject note exceeding max length")
            void shouldRejectNoteExceedingMaxLength() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setNoteFromAdmin("A".repeat(1001));

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("noteFromAdmin"));
            }

            @Test
            @DisplayName("should accept null note")
            void shouldAcceptNullNote() {
                UserDtoIn dto = createValidUserDtoIn();
                dto.setNoteFromAdmin(null);

                Set<ConstraintViolation<UserDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle state machine complete workflow for account")
        void shouldHandleStateMachineCompleteWorkflow() {
            // Simulate account lifecycle: INACTIVE -> IN_VALIDATION -> ACTIVE -> TO_BE_DELETED -> DELETED
            AccountStatus current = AccountStatus.INACTIVE;

            // Step 1: INACTIVE -> IN_VALIDATION
            assertThat(current.canTransitionTo(AccountStatus.IN_VALIDATION)).isTrue();
            current = AccountStatus.IN_VALIDATION;

            // Step 2: IN_VALIDATION -> ACTIVE
            assertThat(current.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
            current = AccountStatus.ACTIVE;

            // Step 3: ACTIVE -> TO_BE_DELETED
            assertThat(current.canTransitionTo(AccountStatus.TO_BE_DELETED)).isTrue();
            current = AccountStatus.TO_BE_DELETED;

            // Step 4: TO_BE_DELETED -> DELETED
            assertThat(current.canTransitionTo(AccountStatus.DELETED)).isTrue();
            current = AccountStatus.DELETED;

            // Final: DELETED is terminal
            assertThat(current.isTerminal()).isTrue();
            assertThat(current.getPossibleTransitions()).isEmpty();
        }

        @Test
        @DisplayName("should handle ban and unban workflow")
        void shouldHandleBanAndUnbanWorkflow() {
            AccountStatus current = AccountStatus.ACTIVE;

            // Ban the account
            assertThat(current.canTransitionTo(AccountStatus.BANNED)).isTrue();
            current = AccountStatus.BANNED;
            assertThat(current.canLogin()).isFalse();

            // Unban (restore to active)
            assertThat(current.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
            current = AccountStatus.ACTIVE;
            assertThat(current.canLogin()).isTrue();
        }

        @Test
        @DisplayName("should handle reactivation from TO_BE_DELETED")
        void shouldHandleReactivationFromToBeDeleted() {
            AccountStatus current = AccountStatus.TO_BE_DELETED;

            // Can be reactivated
            assertThat(current.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
            assertThat(current.canTransitionTo(AccountStatus.IN_VALIDATION)).isTrue();

            // Cannot go back to INACTIVE or BANNED
            assertThat(current.canTransitionTo(AccountStatus.INACTIVE)).isFalse();
            assertThat(current.canTransitionTo(AccountStatus.BANNED)).isFalse();
        }
    }
}
