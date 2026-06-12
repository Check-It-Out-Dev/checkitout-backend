package com.sm.instagram.platform.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a LocalDateTime field represents a date that is today or in the future.
 * Only the date part is considered, time is ignored.
 * 
 * This is useful when frontend sends dates with time set to 00:00 but you want to allow
 * today's date regardless of the current time.
 */
@Documented
@Constraint(validatedBy = FutureOrPresentDateValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FutureOrPresentDate {
    String message() default "{validation.futureOrPresentDate}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
