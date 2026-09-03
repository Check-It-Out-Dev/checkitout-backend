package com.sm.instagram.platform.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.util.Collection;
import java.util.Set;

/**
 * Validator for {@link VimeoUrls}. Each URL must be https and target a
 * trusted Vimeo host (pentest 3.5). Null / empty collections pass so the
 * field stays optional.
 */
public class VimeoUrlsValidator implements ConstraintValidator<VimeoUrls, Collection<String>> {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "vimeo.com",
            "www.vimeo.com",
            "player.vimeo.com");

    @Override
    public boolean isValid(Collection<String> values, ConstraintValidatorContext context) {
        if (values == null || values.isEmpty()) {
            return true;
        }
        for (String value : values) {
            if (!isTrustedVimeoUrl(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean isTrustedVimeoUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        final URI uri;
        try {
            uri = new URI(value.trim());
        } catch (Exception e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return false;
        }
        return ALLOWED_HOSTS.contains(uri.getHost().toLowerCase());
    }
}
