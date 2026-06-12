package com.sm.instagram.platform.user.dto;

import com.sm.instagram.platform.dictionary.DictionaryService;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Locale;

/**
 * Categories for different types of deletion blockers.
 */
@Getter
@AllArgsConstructor
public enum DeletionBlockerCategory {
    ACTIVE_OPPORTUNITIES("warning", "briefcase"),
    PENDING_OPPORTUNITIES("info", "clock"),
    ACTIVE_PARTNERSHIP_OPPORTUNITIES("warning", "handshake"),
    OPPORTUNITIES_WITH_APPLICATIONS("warning", "users"),
    LAST_ADMIN("danger", "shield-alert"),
    OPEN_SUPPORT_TICKETS("warning", "help-circle"),
    SUPPORT_TICKETS_HISTORY("info", "archive"),
    RECENT_ACTIVITY("info", "activity"),
    DATA_RETENTION_REQUIRED("warning", "database");

    private final String colorTheme;
    private final String icon;

    /**
     * Gets the translated label for this blocker category.
     */
    public String getLabel(DictionaryService dictionaryService, Locale locale) {
        String key = "DELETION_BLOCKER_" + name();
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(name());
    }

    /**
     * Gets the translated description for this blocker category.
     */
    public String getDescription(DictionaryService dictionaryService, Locale locale) {
        String key = "DELETION_BLOCKER_" + name() + "_DESC";
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(name());
    }
}
