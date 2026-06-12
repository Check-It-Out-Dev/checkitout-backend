package com.sm.instagram.platform.partnershipopportunities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sm.instagram.platform.dictionary.DictionaryService;
import lombok.Getter;

import java.util.List;
import java.util.Locale;

@Getter
public enum CompensationType {

    CASH("primary", "dollar-sign", List.of("money"),
            "Płatność gotówką"),

    BARTER("warning", "swap", List.of("trade", "exchange"),
            "Wymiana barterowa");

    private final String colorTheme;
    private final String icon;
    private final List<String> aliases;
    private final String defaultDescription;

    CompensationType(String colorTheme, String icon, List<String> aliases, String defaultDescription) {
        this.colorTheme = colorTheme;
        this.icon = icon;
        this.aliases = aliases;
        this.defaultDescription = defaultDescription;
    }

    @JsonCreator
    public static CompensationType fromString(String value) {
        return valueOf(value.toUpperCase());
    }

    @JsonValue
    public String getValue() {
        return name();
    }

    public String getLabel(DictionaryService dictionaryService, Locale locale) {
        String key = "COMPENSATION_TYPE_" + name();
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(name());
    }

    public String getDescription(DictionaryService dictionaryService, Locale locale) {
        String key = "COMPENSATION_TYPE_" + name() + "_DESC";
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(defaultDescription);
    }
}