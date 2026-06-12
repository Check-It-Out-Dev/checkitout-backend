package com.sm.instagram.platform.consent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sm.instagram.platform.dictionary.DictionaryService;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Locale;

@Getter
@AllArgsConstructor
public enum ConsentAction {

    GRANTED("success", "check-circle", List.of("accepted", "approved", "given"),
            "Zgoda została udzielona przez użytkownika"),

    WITHDRAWN("danger", "x-circle", List.of("revoked", "removed", "denied"),
            "Zgoda została wycofana przez użytkownika"),

    UPDATED("warning", "edit", List.of("modified", "changed"),
            "Zgoda została zaktualizowana");

    private final String colorTheme;
    private final String icon;
    private final List<String> aliases;
    private final String description;

    @JsonCreator
    public static ConsentAction fromString(String value) {
        return valueOf(value.toUpperCase());
    }

    @JsonValue
    public String getValue() {
        return name();
    }

    public String getLabel(DictionaryService dictionaryService, Locale locale) {
        String key = "CONSENT_ACTION_" + name();
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(name());
    }

    public String getDescription(DictionaryService dictionaryService, Locale locale) {
        String key = "CONSENT_ACTION_" + name() + "_DESC";
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(description);
    }

    public boolean isPositiveAction() {
        return this == GRANTED;
    }

    public boolean isNegativeAction() {
        return this == WITHDRAWN;
    }

    public boolean isModificationAction() {
        return this == UPDATED;
    }
}
