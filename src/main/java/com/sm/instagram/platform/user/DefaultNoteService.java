package com.sm.instagram.platform.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Service for providing language-aware default admin notes for new users.
 * Uses MessageSource with LocaleContextHolder (set by AppLanguageFilter from X-App-Language header).
 *
 * CIO-341: Replaces hardcoded Polish constants with i18n-aware messages.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultNoteService {

    private final MessageSource messageSource;

    private static final String KEY_INFLUENCER = "user.default.note.influencer";
    private static final String KEY_COMPANY = "user.default.note.company";

    // Fallback messages (Polish) in case MessageSource fails
    private static final String FALLBACK_INFLUENCER = "Uzupełnij swoje dane, żeby aktywować konto.";
    private static final String FALLBACK_COMPANY = "Uzupełnij dane swojej firmy, żeby aktywować konto.";

    /**
     * Get the default admin note for a new user based on their type and current locale.
     * Locale is determined by AppLanguageFilter from the X-App-Language header.
     *
     * @param userType The type of user being created
     * @return Localized default admin note
     */
    public String getDefaultNote(UserType userType) {
        Locale locale = LocaleContextHolder.getLocale();
        log.debug("Getting default note for userType={}, locale={}", userType, locale);

        String key;
        String fallback;

        if (userType == UserType.COMPANY) {
            key = KEY_COMPANY;
            fallback = FALLBACK_COMPANY;
        } else {
            // All non-company users (INFLUENCER, ADMIN, PENDING_ADMIN) get influencer note
            key = KEY_INFLUENCER;
            fallback = FALLBACK_INFLUENCER;
        }

        try {
            String message = messageSource.getMessage(key, null, fallback, locale);
            log.debug("Resolved default note for {}: '{}' (locale={})", userType, message, locale);
            return message;
        } catch (Exception e) {
            log.warn("Failed to get message for key '{}', using fallback. Error: {}", key, e.getMessage());
            return fallback;
        }
    }
}
