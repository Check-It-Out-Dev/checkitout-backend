package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.validation.FutureOrPresentDate;
import com.sm.instagram.platform.common.validation.FutureOrPresentDateValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FutureOrPresentDateValidator.
 * Tests custom validator for dates that must be today or in the future.
 */
@DisplayName("FutureOrPresentDateValidator Unit Tests")
class FutureOrPresentDateValidatorUnitTest {

    private FutureOrPresentDateValidator validator;
    private Validator beanValidator;

    @BeforeEach
    void setUp() {
        validator = new FutureOrPresentDateValidator();
        validator.initialize(null);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        beanValidator = factory.getValidator();
    }

    // ==================== Direct Validator Tests ====================

    @Nested
    @DisplayName("Direct Validator Tests")
    class DirectValidatorTests {

        @Test
        @DisplayName("should return true for null value")
        void shouldReturnTrueForNullValue() {
            boolean result = validator.isValid(null, null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for today's date")
        void shouldReturnTrueForTodaysDate() {
            LocalDateTime today = LocalDateTime.now();
            boolean result = validator.isValid(today, null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for today's date at midnight")
        void shouldReturnTrueForTodaysDateAtMidnight() {
            LocalDateTime todayMidnight = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);
            boolean result = validator.isValid(todayMidnight, null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for today's date at 23:59:59")
        void shouldReturnTrueForTodaysDateAtEndOfDay() {
            LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.of(23, 59, 59));
            boolean result = validator.isValid(todayEnd, null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for tomorrow's date")
        void shouldReturnTrueForTomorrowsDate() {
            LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
            boolean result = validator.isValid(tomorrow, null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for date far in the future")
        void shouldReturnTrueForFutureDateFarInFuture() {
            LocalDateTime farFuture = LocalDateTime.now().plusYears(10);
            boolean result = validator.isValid(farFuture, null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for yesterday's date")
        void shouldReturnFalseForYesterdaysDate() {
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            boolean result = validator.isValid(yesterday, null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for yesterday's date at 23:59:59")
        void shouldReturnFalseForYesterdaysDateAtEndOfDay() {
            LocalDateTime yesterdayEnd = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(23, 59, 59));
            boolean result = validator.isValid(yesterdayEnd, null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for date far in the past")
        void shouldReturnFalseForPastDateFarInPast() {
            LocalDateTime farPast = LocalDateTime.now().minusYears(10);
            boolean result = validator.isValid(farPast, null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for date one week ago")
        void shouldReturnFalseForDateOneWeekAgo() {
            LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
            boolean result = validator.isValid(oneWeekAgo, null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true for date one week in future")
        void shouldReturnTrueForDateOneWeekInFuture() {
            LocalDateTime oneWeekFuture = LocalDateTime.now().plusWeeks(1);
            boolean result = validator.isValid(oneWeekFuture, null);
            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 7, 30, 365, 1000})
        @DisplayName("should return true for various days in the future")
        void shouldReturnTrueForVariousDaysInFuture(int daysToAdd) {
            LocalDateTime future = LocalDateTime.now().plusDays(daysToAdd);
            boolean result = validator.isValid(future, null);
            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 7, 30, 365, 1000})
        @DisplayName("should return false for various days in the past")
        void shouldReturnFalseForVariousDaysInPast(int daysToSubtract) {
            LocalDateTime past = LocalDateTime.now().minusDays(daysToSubtract);
            boolean result = validator.isValid(past, null);
            assertThat(result).isFalse();
        }
    }

    // ==================== Bean Validation Integration Tests ====================

    @Nested
    @DisplayName("Bean Validation Integration")
    class BeanValidationIntegrationTests {

        @Test
        @DisplayName("should pass validation for valid future date")
        void shouldPassValidationForValidFutureDate() {
            TestEntity entity = new TestEntity();
            entity.setDate(LocalDateTime.now().plusDays(1));

            Set<ConstraintViolation<TestEntity>> violations = beanValidator.validate(entity);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation for today's date")
        void shouldPassValidationForTodaysDate() {
            TestEntity entity = new TestEntity();
            entity.setDate(LocalDateTime.now());

            Set<ConstraintViolation<TestEntity>> violations = beanValidator.validate(entity);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation for null date")
        void shouldPassValidationForNullDate() {
            TestEntity entity = new TestEntity();
            entity.setDate(null);

            Set<ConstraintViolation<TestEntity>> violations = beanValidator.validate(entity);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation for past date")
        void shouldFailValidationForPastDate() {
            TestEntity entity = new TestEntity();
            entity.setDate(LocalDateTime.now().minusDays(1));

            Set<ConstraintViolation<TestEntity>> violations = beanValidator.validate(entity);

            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("date"))).isTrue();
        }

        @Test
        @DisplayName("should use custom message from annotation")
        void shouldUseCustomMessageFromAnnotation() {
            TestEntityWithCustomMessage entity = new TestEntityWithCustomMessage();
            entity.setDate(LocalDateTime.now().minusDays(1));

            Set<ConstraintViolation<TestEntityWithCustomMessage>> violations = beanValidator.validate(entity);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Date must be today or in the future");
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle date at very start of today")
        void shouldHandleDateAtVeryStartOfToday() {
            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
            boolean result = validator.isValid(startOfToday, null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should handle date at very end of yesterday")
        void shouldHandleDateAtVeryEndOfYesterday() {
            LocalDateTime endOfYesterday = LocalDate.now().minusDays(1).atTime(23, 59, 59, 999999999);
            boolean result = validator.isValid(endOfYesterday, null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle leap year date in future")
        void shouldHandleLeapYearDateInFuture() {
            // Feb 29, 2028 (leap year)
            LocalDateTime leapYearDate = LocalDateTime.of(2028, 2, 29, 12, 0);
            boolean result = validator.isValid(leapYearDate, null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should handle year boundary in future")
        void shouldHandleYearBoundaryInFuture() {
            LocalDateTime newYearsDay = LocalDateTime.of(LocalDate.now().getYear() + 1, 1, 1, 0, 0);
            boolean result = validator.isValid(newYearsDay, null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should handle very far future date")
        void shouldHandleVeryFarFutureDate() {
            LocalDateTime year3000 = LocalDateTime.of(3000, 1, 1, 12, 0);
            boolean result = validator.isValid(year3000, null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should handle very old past date")
        void shouldHandleVeryOldPastDate() {
            LocalDateTime year1900 = LocalDateTime.of(1900, 1, 1, 12, 0);
            boolean result = validator.isValid(year1900, null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should ignore time component - today at midnight vs end of day")
        void shouldIgnoreTimeComponent() {
            LocalDate today = LocalDate.now();

            LocalDateTime todayMidnight = today.atStartOfDay();
            LocalDateTime todayEndOfDay = today.atTime(23, 59, 59);

            // Both should be valid since they represent today
            assertThat(validator.isValid(todayMidnight, null)).isTrue();
            assertThat(validator.isValid(todayEndOfDay, null)).isTrue();
        }

        @Test
        @DisplayName("should validate correctly regardless of current time")
        void shouldValidateCorrectlyRegardlessOfCurrentTime() {
            // This test verifies that validation is based on date, not time
            LocalDate tomorrow = LocalDate.now().plusDays(1);

            // Even if it's 23:59 now, tomorrow at 00:00 should be valid
            LocalDateTime tomorrowMidnight = tomorrow.atStartOfDay();
            assertThat(validator.isValid(tomorrowMidnight, null)).isTrue();
        }
    }

    // ==================== Test Helper Classes ====================

    /**
     * Test entity for bean validation integration tests.
     */
    private static class TestEntity {
        @FutureOrPresentDate
        private LocalDateTime date;

        public LocalDateTime getDate() {
            return date;
        }

        public void setDate(LocalDateTime date) {
            this.date = date;
        }
    }

    /**
     * Test entity with custom validation message.
     */
    private static class TestEntityWithCustomMessage {
        @FutureOrPresentDate(message = "Date must be today or in the future")
        private LocalDateTime date;

        public LocalDateTime getDate() {
            return date;
        }

        public void setDate(LocalDateTime date) {
            this.date = date;
        }
    }
}
