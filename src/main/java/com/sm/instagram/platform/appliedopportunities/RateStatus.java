package com.sm.instagram.platform.appliedopportunities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sm.instagram.platform.dictionary.DictionaryService;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Locale;

/**
 * Two-sided rating outcome on an applied opportunity.
 *
 * <p>The persisted form is the enum constant name ({@code DEFAULT},
 * {@code POSITIVE}, {@code NEGATIVE}) — those names appear directly in
 * {@code applied_opportunity.rate_status} and {@code company_rate_status},
 * so they are part of the schema and must not be renamed.
 *
 * <p>Each constant carries presentation metadata (color theme, icon, alias
 * keywords for search) and a Polish fallback description used when the
 * dictionary lookup misses.
 */
@Getter
@AllArgsConstructor
public enum RateStatus {

    DEFAULT("secondary", "circle", List.of("neutral"),
            "Domyślna ocena"),

    POSITIVE("success", "thumbs-up", List.of("good", "liked"),
            "Pozytywna ocena"),

    NEGATIVE("danger", "thumbs-down", List.of("bad", "disliked"),
            "Negatywna ocena");

    private final String colorTheme;
    private final String icon;
    private final List<String> aliases;
    private final String defaultDescription;

    @JsonCreator
    public static RateStatus fromString(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String getValue() {
        return name();
    }

    /**
     * Resolve the localized display label, falling back to the enum name when
     * the dictionary has no entry for the requested locale.
     */
    public String getLabel(DictionaryService dictionaryService, Locale locale) {
        String key = "RATE_STATUS_" + name();
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(name());
    }

    /**
     * Resolve the localized long-form description, falling back to the
     * Polish default baked into the enum.
     */
    public String getDescription(DictionaryService dictionaryService, Locale locale) {
        String key = "RATE_STATUS_" + name() + "_DESC";
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(defaultDescription);
    }
}
