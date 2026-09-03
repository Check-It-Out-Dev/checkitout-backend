package com.sm.instagram.platform.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that every entry in a collection of URLs is an https link to a
 * trusted Vimeo host ({@code vimeo.com}, {@code www.vimeo.com},
 * {@code player.vimeo.com}). Null / empty collections are valid — use
 * {@code @NotNull} / {@code @NotEmpty} for presence.
 *
 * <p>Security (pentest 3.5): content video URLs must come from Vimeo and must
 * not be arbitrary attacker-controlled links. Backend is authoritative; the
 * frontend mirrors this only as UX.
 */
@Documented
@Constraint(validatedBy = VimeoUrlsValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface VimeoUrls {
    String message() default "{validation.appliedContent.urls.vimeoOnly}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
