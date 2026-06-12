package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.util.mappers.EnumTranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for EnumTranslationService.
 * Tests enum translation logic without external dependencies.
 */
@DisplayName("EnumTranslationService Unit Tests")
class EnumTranslationServiceUnitTest {

    private EnumTranslationService service;

    /**
     * Test enum for status translations.
     */
    enum TestStatus {
        ACTIVE, INACTIVE, PENDING, SUSPENDED, DELETED
    }

    /**
     * Test enum for category translations.
     */
    enum TestCategory {
        FOOD, BEVERAGE, LIFESTYLE, TECH
    }

    /**
     * Test enum for role translations.
     */
    enum TestRole {
        ADMIN, COMPANY, INFLUENCER
    }

    /**
     * Test class with enum fields for field-based translation tests.
     */
    static class TestEntity {
        TestStatus status;
        TestCategory category;
        TestRole role;
        String nonEnumField;
    }

    @BeforeEach
    void setUp() {
        service = new EnumTranslationService();
    }

    @Nested
    @DisplayName("translateToEnum - String to Enum conversion")
    class TranslateToEnumTests {

        @Test
        @DisplayName("should translate valid status string to enum")
        void shouldTranslateValidStatusStringToEnum() {
            // When
            TestStatus result = service.translateToEnum(TestStatus.class, "ACTIVE");

            // Then
            assertThat(result).isEqualTo(TestStatus.ACTIVE);
        }

        @ParameterizedTest
        @EnumSource(TestStatus.class)
        @DisplayName("should translate all valid status strings correctly")
        void shouldTranslateAllValidStatusStrings(TestStatus expected) {
            // When
            TestStatus result = service.translateToEnum(TestStatus.class, expected.name());

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw exception for null input value")
        void shouldThrowExceptionForNullInputValue() {
            // When/Then
            assertThatThrownBy(() -> service.translateToEnum(TestStatus.class, null))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"INVALID", "unknown", "NOT_A_STATUS", ""})
        @DisplayName("should throw exception for invalid input values")
        void shouldThrowExceptionForInvalidInputValues(String invalidValue) {
            // When/Then
            assertThatThrownBy(() -> service.translateToEnum(TestStatus.class, invalidValue))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should be case sensitive - lowercase should fail")
        void shouldBeCaseSensitive() {
            // When/Then
            assertThatThrownBy(() -> service.translateToEnum(TestStatus.class, "active"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("translateToString - Enum to String conversion")
    class TranslateToStringTests {

        @Test
        @DisplayName("should translate enum to string correctly")
        void shouldTranslateEnumToStringCorrectly() {
            // When
            String result = service.translateToString(TestStatus.ACTIVE);

            // Then
            assertThat(result).isEqualTo("ACTIVE");
        }

        @ParameterizedTest
        @EnumSource(TestStatus.class)
        @DisplayName("should translate all status enums to correct strings")
        void shouldTranslateAllStatusEnumsToCorrectStrings(TestStatus status) {
            // When
            String result = service.translateToString(status);

            // Then
            assertThat(result).isEqualTo(status.name());
        }

        @Test
        @DisplayName("should return null when input enum is null")
        void shouldReturnNullWhenInputEnumIsNull() {
            // When
            String result = service.translateToString(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should translate category enum to string")
        void shouldTranslateCategoryEnumToString() {
            // When
            String result = service.translateToString(TestCategory.FOOD);

            // Then
            assertThat(result).isEqualTo("FOOD");
        }
    }

    @Nested
    @DisplayName("translateCategory - Category enum translations")
    class TranslateCategoryTests {

        @ParameterizedTest
        @EnumSource(TestCategory.class)
        @DisplayName("should translate all categories correctly")
        void shouldTranslateAllCategoriesCorrectly(TestCategory category) {
            // When
            TestCategory result = service.translateToEnum(TestCategory.class, category.name());

            // Then
            assertThat(result).isEqualTo(category);
        }

        @Test
        @DisplayName("should throw for unknown category")
        void shouldThrowForUnknownCategory() {
            // When/Then
            assertThatThrownBy(() -> service.translateToEnum(TestCategory.class, "UNKNOWN_CATEGORY"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("translateRole - Role enum translations")
    class TranslateRoleTests {

        @Test
        @DisplayName("should translate admin role correctly")
        void shouldTranslateAdminRoleCorrectly() {
            // When
            TestRole result = service.translateToEnum(TestRole.class, "ADMIN");

            // Then
            assertThat(result).isEqualTo(TestRole.ADMIN);
        }

        @Test
        @DisplayName("should translate company role correctly")
        void shouldTranslateCompanyRoleCorrectly() {
            // When
            TestRole result = service.translateToEnum(TestRole.class, "COMPANY");

            // Then
            assertThat(result).isEqualTo(TestRole.COMPANY);
        }

        @Test
        @DisplayName("should translate influencer role correctly")
        void shouldTranslateInfluencerRoleCorrectly() {
            // When
            TestRole result = service.translateToEnum(TestRole.class, "INFLUENCER");

            // Then
            assertThat(result).isEqualTo(TestRole.INFLUENCER);
        }

        @ParameterizedTest
        @EnumSource(TestRole.class)
        @DisplayName("should translate all roles correctly")
        void shouldTranslateAllRolesCorrectly(TestRole role) {
            // Given
            String roleString = role.name();

            // When
            TestRole result = service.translateToEnum(TestRole.class, roleString);

            // Then
            assertThat(result).isEqualTo(role);
        }
    }

    @Nested
    @DisplayName("translate - Field-based translation")
    class TranslateFieldTests {

        @Test
        @DisplayName("should translate string to enum for enum field")
        void shouldTranslateStringToEnumForEnumField() throws NoSuchFieldException {
            // Given
            Field statusField = TestEntity.class.getDeclaredField("status");
            String value = "ACTIVE";

            // When
            Object result = service.translate(statusField, value);

            // Then
            assertThat(result).isInstanceOf(TestStatus.class);
            assertThat(result).isEqualTo(TestStatus.ACTIVE);
        }

        @Test
        @DisplayName("should translate enum to string for enum field")
        void shouldTranslateEnumToStringForEnumField() throws NoSuchFieldException {
            // Given
            Field statusField = TestEntity.class.getDeclaredField("status");
            TestStatus value = TestStatus.PENDING;

            // When
            Object result = service.translate(statusField, value);

            // Then
            assertThat(result).isInstanceOf(String.class);
            assertThat(result).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("should return original value for non-enum field")
        void shouldReturnOriginalValueForNonEnumField() throws NoSuchFieldException {
            // Given
            Field nonEnumField = TestEntity.class.getDeclaredField("nonEnumField");
            String value = "some string value";

            // When
            Object result = service.translate(nonEnumField, value);

            // Then
            assertThat(result).isEqualTo("some string value");
        }

        @Test
        @DisplayName("should handle all category enum field translations")
        void shouldHandleCategoryEnumFieldTranslations() throws NoSuchFieldException {
            // Given
            Field categoryField = TestEntity.class.getDeclaredField("category");

            // When - String to Enum
            Object stringToEnum = service.translate(categoryField, "LIFESTYLE");
            // When - Enum to String
            Object enumToString = service.translate(categoryField, TestCategory.TECH);

            // Then
            assertThat(stringToEnum).isEqualTo(TestCategory.LIFESTYLE);
            assertThat(enumToString).isEqualTo("TECH");
        }
    }

    @Nested
    @DisplayName("getAvailableTranslations - Enum constants retrieval")
    class GetAvailableTranslationsTests {

        @Test
        @DisplayName("should return all status enum constants")
        void shouldReturnAllStatusEnumConstants() {
            // Given
            TestStatus[] constants = TestStatus.class.getEnumConstants();

            // Then
            assertThat(constants).hasSize(5);
            assertThat(constants).containsExactly(
                    TestStatus.ACTIVE,
                    TestStatus.INACTIVE,
                    TestStatus.PENDING,
                    TestStatus.SUSPENDED,
                    TestStatus.DELETED
            );
        }

        @Test
        @DisplayName("should return all category enum constants")
        void shouldReturnAllCategoryEnumConstants() {
            // Given
            TestCategory[] constants = TestCategory.class.getEnumConstants();

            // Then
            assertThat(constants).hasSize(4);
            assertThat(constants).containsExactly(
                    TestCategory.FOOD,
                    TestCategory.BEVERAGE,
                    TestCategory.LIFESTYLE,
                    TestCategory.TECH
            );
        }

        @Test
        @DisplayName("should return all role enum constants")
        void shouldReturnAllRoleEnumConstants() {
            // Given
            TestRole[] constants = TestRole.class.getEnumConstants();

            // Then
            assertThat(constants).hasSize(3);
            assertThat(constants).containsExactly(
                    TestRole.ADMIN,
                    TestRole.COMPANY,
                    TestRole.INFLUENCER
            );
        }
    }

    @Nested
    @DisplayName("isValidEnumValue - Enum value validation")
    class IsValidEnumValueTests {

        @Test
        @DisplayName("should return true for valid enum value")
        void shouldReturnTrueForValidEnumValue() {
            // When
            boolean isValid = isValidEnumValue(TestStatus.class, "ACTIVE");

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid enum value")
        void shouldReturnFalseForInvalidEnumValue() {
            // When
            boolean isValid = isValidEnumValue(TestStatus.class, "INVALID_STATUS");

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should return false for null value")
        void shouldReturnFalseForNullValue() {
            // When
            boolean isValid = isValidEnumValue(TestStatus.class, null);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should return false for empty string")
        void shouldReturnFalseForEmptyString() {
            // When
            boolean isValid = isValidEnumValue(TestStatus.class, "");

            // Then
            assertThat(isValid).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"ACTIVE", "INACTIVE", "PENDING", "SUSPENDED", "DELETED"})
        @DisplayName("should return true for all valid status values")
        void shouldReturnTrueForAllValidStatusValues(String value) {
            // When
            boolean isValid = isValidEnumValue(TestStatus.class, value);

            // Then
            assertThat(isValid).isTrue();
        }

        /**
         * Helper method to check if a value is a valid enum constant.
         */
        private <T extends Enum<T>> boolean isValidEnumValue(Class<T> enumClass, String value) {
            if (value == null) {
                return false;
            }
            try {
                service.translateToEnum(enumClass, value);
                return true;
            } catch (ValidationTranslatableException e) {
                return false;
            }
        }
    }

    @Nested
    @DisplayName("Case sensitivity tests")
    class CaseSensitivityTests {

        @Test
        @DisplayName("should not translate lowercase value")
        void shouldNotTranslateLowercaseValue() {
            // When/Then
            assertThatThrownBy(() -> service.translateToEnum(TestStatus.class, "active"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should not translate mixed case value")
        void shouldNotTranslateMixedCaseValue() {
            // When/Then
            assertThatThrownBy(() -> service.translateToEnum(TestStatus.class, "Active"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should only accept exact case match")
        void shouldOnlyAcceptExactCaseMatch() {
            // Given
            String exactCase = "ACTIVE";
            String lowerCase = "active";
            String mixedCase = "Active";

            // Then
            assertThat(service.translateToEnum(TestStatus.class, exactCase)).isEqualTo(TestStatus.ACTIVE);
            assertThatThrownBy(() -> service.translateToEnum(TestStatus.class, lowerCase))
                    .isInstanceOf(ValidationTranslatableException.class);
            assertThatThrownBy(() -> service.translateToEnum(TestStatus.class, mixedCase))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should preserve case when translating enum to string")
        void shouldPreserveCaseWhenTranslatingEnumToString() {
            // When
            String result = service.translateToString(TestStatus.ACTIVE);

            // Then
            assertThat(result).isEqualTo("ACTIVE");
            assertThat(result).isNotEqualTo("active");
            assertThat(result).isNotEqualTo("Active");
        }
    }

    @Nested
    @DisplayName("Exception handling tests")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("should include value in exception for invalid input")
        void shouldIncludeValueInExceptionForInvalidInput() {
            // When/Then
            assertThatThrownBy(() -> service.translateToEnum(TestStatus.class, "INVALID_VALUE"))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.invalid_argument");
        }

        @Test
        @DisplayName("should throw exception with correct message key")
        void shouldThrowExceptionWithCorrectMessageKey() {
            // When/Then
            try {
                service.translateToEnum(TestStatus.class, "NOT_FOUND");
            } catch (ValidationTranslatableException e) {
                assertThat(e.getMessageKey()).isEqualTo("error.validation.invalid_argument");
            }
        }
    }
}
