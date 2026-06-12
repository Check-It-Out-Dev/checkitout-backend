package com.sm.instagram.platform.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import com.sm.instagram.platform.dictionary.DictionaryService;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Getter
@AllArgsConstructor
public enum AccountStatus {

    INACTIVE("secondary", "user-minus", List.of("disabled"),
            "Konto jest nieaktywne i nie może być używane"),

    IN_VALIDATION("warning", "clock", List.of("pending", "under-review"),
            "Konto jest w trakcie weryfikacji przez administratorów"),

    ACTIVE("success", "user-check", List.of("enabled", "verified"),
            "Konto jest aktywne i w pełni funkcjonalne"),

    BANNED("danger", "ban", List.of("suspended", "blocked", "prohibited"),
            "Konto zostało zbanowane i nie może się zalogować ani wykonywać żadnych działań"),

    BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS("warning", "file-text", List.of("consent-blocked"),
            "Konto zablokowane z powodu niezaakceptowania zaktualizowanych warunków"),

    TO_BE_DELETED("danger", "user-x", List.of("marked-for-deletion"),
            "Konto jest oznaczone do usunięcia"),

    DELETED("muted", "trash", List.of("removed"),
            "Konto zostało trwale usunięte");


    private final String colorTheme;
    private final String icon;
    private final List<String> aliases;
    private final String description;

    @JsonCreator
    public static AccountStatus fromString(String value) {
        return valueOf(value.toUpperCase());
    }

    @JsonValue
    public String getValue() {
        return name();
    }

    public String getLabel(DictionaryService dictionaryService, Locale locale) {
        String key = "ACCOUNT_STATUS_" + name();
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(name());
    }

    public String getDescription(DictionaryService dictionaryService, Locale locale) {
        String key = "ACCOUNT_STATUS_" + name() + "_DESC";
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(description);
    }

    public boolean canTransitionTo(AccountStatus newStatus) {
        return switch (this) {
            case INACTIVE -> newStatus == IN_VALIDATION || newStatus == TO_BE_DELETED || newStatus == BANNED;
            case IN_VALIDATION -> newStatus == ACTIVE || newStatus == INACTIVE || newStatus == TO_BE_DELETED || newStatus == BANNED;
            case ACTIVE -> newStatus == INACTIVE || newStatus == TO_BE_DELETED || newStatus == BANNED
                    || newStatus == BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS;
            case BANNED -> newStatus == ACTIVE || newStatus == TO_BE_DELETED; // Can be unbanned or deleted
            case BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS -> newStatus == ACTIVE || newStatus == TO_BE_DELETED; // Unblocked after consent or deleted
            case TO_BE_DELETED ->
                    newStatus == DELETED || newStatus == ACTIVE || newStatus == IN_VALIDATION; // Can reactivate before deletion
            case DELETED -> false; // No transitions from deleted
        };
    }

    public List<AccountStatus> getPossibleTransitions() {
        return Arrays.stream(values())
                .filter(this::canTransitionTo)
                .toList();
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean canLogin() {
        return this == ACTIVE || this == IN_VALIDATION || this == BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS;
    }

    public boolean isTerminal() {
        return this == DELETED;
    }
}
