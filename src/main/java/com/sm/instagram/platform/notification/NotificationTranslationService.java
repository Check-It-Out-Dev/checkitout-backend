package com.sm.instagram.platform.notification;

import com.sm.instagram.platform.common.util.HtmlEncoder;
import com.sm.instagram.platform.dictionary.DictionaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for translating notification content.
 *
 * Uses DictionaryService to fetch translations and performs placeholder replacement.
 *
 * Dictionary key format for notifications:
 * - Title: NOTIFICATION_{TYPE}_TITLE (e.g., NOTIFICATION_APPLICATION_RECEIVED_TITLE)
 * - Message: NOTIFICATION_{TYPE}_MESSAGE (e.g., NOTIFICATION_APPLICATION_RECEIVED_MESSAGE)
 * - Action: NOTIFICATION_{TYPE}_ACTION (e.g., NOTIFICATION_APPLICATION_RECEIVED_ACTION)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationTranslationService {

    private final DictionaryService dictionaryService;

    /**
     * Default language fallback.
     */
    private static final String DEFAULT_LANGUAGE = "en";

    /**
     * Pattern for matching placeholders like {influencerName}.
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Get translated title for a notification type.
     *
     * @param type         notification type
     * @param languageCode user's language (e.g., "en", "pl")
     * @param parameters   values for placeholder replacement
     * @return translated title with placeholders replaced
     */
    public String getTitle(NotificationType type, String languageCode, Map<String, String> parameters) {
        String key = type.getTranslationKeyPrefix() + "_TITLE";
        return getTranslatedText(key, languageCode, parameters);
    }

    /**
     * Get translated message for a notification type.
     *
     * @param type         notification type
     * @param languageCode user's language (e.g., "en", "pl")
     * @param parameters   values for placeholder replacement
     * @return translated message with placeholders replaced
     */
    public String getMessage(NotificationType type, String languageCode, Map<String, String> parameters) {
        String key = type.getTranslationKeyPrefix() + "_MESSAGE";
        return getTranslatedText(key, languageCode, parameters);
    }

    /**
     * Get translated action label for a notification type.
     *
     * @param type         notification type
     * @param languageCode user's language (e.g., "en", "pl")
     * @return translated action label (no placeholders needed)
     */
    public String getActionLabel(NotificationType type, String languageCode) {
        String key = type.getTranslationKeyPrefix() + "_ACTION";
        return getTranslatedText(key, languageCode, null);
    }

    // ========================================================================
    // TRANSLATION CORE
    // ========================================================================

    /**
     * Get translated text with placeholder replacement.
     *
     * @param key          dictionary key
     * @param languageCode user's language
     * @param parameters   placeholder values (can be null)
     * @return translated text with placeholders replaced
     */
    private String getTranslatedText(String key, String languageCode, Map<String, String> parameters) {
        // Try user's language first
        String text = dictionaryService.getTranslation(key, languageCode)
                .orElse(null);

        // Fallback to English if not found
        if (text == null && !DEFAULT_LANGUAGE.equals(languageCode)) {
            text = dictionaryService.getTranslation(key, DEFAULT_LANGUAGE)
                    .orElse(null);

            if (text != null) {
                log.debug("Translation fallback: key={}, requested={}, fallback={}",
                        key, languageCode, DEFAULT_LANGUAGE);
            }
        }

        // Ultimate fallback: return key itself
        if (text == null) {
            log.warn("Missing translation: key={}, language={}", key, languageCode);
            return key; // At least show the key so developers know what's missing
        }

        // Replace placeholders if parameters provided
        if (parameters != null && !parameters.isEmpty()) {
            text = replacePlaceholders(text, parameters);
        }

        return text;
    }

    /**
     * Replace {placeholder} patterns with actual values.
     *
     * @param template   text with placeholders like {influencerName}
     * @param parameters map of placeholder name → value
     * @return text with placeholders replaced
     */
    private String replacePlaceholders(String template, Map<String, String> parameters) {
        if (template == null || parameters == null || parameters.isEmpty()) {
            return template;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String placeholderName = matcher.group(1);
            String replacement = parameters.getOrDefault(placeholderName, "{" + placeholderName + "}");

            // Escape special regex characters in replacement
            replacement = HtmlEncoder.encode(replacement);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    // ========================================================================
    // HELPER: BUILD PARAMETERS MAP
    // ========================================================================

    /**
     * Build parameters map for partnership notifications.
     *
     * @param influencerName  name of the influencer
     * @param companyName     name of the company
     * @param opportunityName name of the opportunity/campaign
     * @return parameters map for translation
     */
    public static Map<String, String> buildPartnershipParams(
            String influencerName,
            String companyName,
            String opportunityName) {

        return Map.of(
                "influencerName", nullSafe(influencerName, "Influencer"),
                "companyName", nullSafe(companyName, "Company"),
                "opportunityName", nullSafe(opportunityName, "Campaign")
        );
    }

    /**
     * Build parameters map for support ticket notifications.
     *
     * @param ticketSubject ticket title
     * @return parameters map for translation
     */
    public static Map<String, String> buildSupportParams(String ticketSubject) {
        return Map.of("ticketSubject", nullSafe(ticketSubject, "N/A"));
    }

    /**
     * Null-safe string helper.
     */
    private static String nullSafe(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}