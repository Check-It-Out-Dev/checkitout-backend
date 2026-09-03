package com.sm.instagram.platform.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.util.Set;

/**
 * Validator for {@link SocialPostUrl}. The URL must be https and target a
 * trusted Instagram / TikTok host. Null / blank passes so the field stays
 * optional. Mirrors {@link VimeoUrlsValidator}'s exact-host matching — no
 * substring/subdomain tricks ({@code instagram.com.evil.com} is rejected
 * because {@code URI.getHost()} is compared for equality).
 */
public class SocialPostUrlValidator implements ConstraintValidator<SocialPostUrl, String> {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "instagram.com",
            "www.instagram.com",
            "tiktok.com",
            "www.tiktok.com",
            "vm.tiktok.com");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
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
