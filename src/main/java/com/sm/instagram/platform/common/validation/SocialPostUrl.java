package com.sm.instagram.platform.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a single URL is an https link to a trusted social-media
 * host where campaign content is published: Instagram
 * ({@code instagram.com}, {@code www.instagram.com}) or TikTok
 * ({@code tiktok.com}, {@code www.tiktok.com}, {@code vm.tiktok.com}).
 * Null / blank values are valid — use {@code @NotBlank} for presence.
 *
 * <p>Security (pentest 3.5 follow-up, 2026-06-13): {@code socialMediaLink}
 * is stored verbatim and rendered as a clickable download/preview link to
 * the reviewing company. The sibling {@code urls} field got the
 * {@link VimeoUrls} allowlist in the original remediation; this closes the
 * same stored-link substitution class on the publication-link field.
 * Backend is authoritative; the frontend mirrors this only as UX.
 */
@Documented
@Constraint(validatedBy = SocialPostUrlValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SocialPostUrl {
    String message() default "{validation.appliedContent.socialMediaLink.host}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
