package com.sm.instagram.platform.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Validator for @FutureOrPresentDate annotation.
 * Validates that a LocalDateTime represents a date that is today or in the future,
 * ignoring the time component.
 */
public class FutureOrPresentDateValidator implements ConstraintValidator<FutureOrPresentDate, LocalDateTime> {

    @Override
    public void initialize(FutureOrPresentDate constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(LocalDateTime value, ConstraintValidatorContext context) {
        // Null values are considered valid (use @NotNull for null checking)
        if (value == null) {
            return true;
        }

        // Extract just the date part from the LocalDateTime
        LocalDate dateToValidate = value.toLocalDate();
        LocalDate today = LocalDate.now();

        // Check if the date is today or in the future
        return !dateToValidate.isBefore(today);
    }
}
