package com.sm.instagram.platform.common.metadata;

import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Locale;

/**
 * Interface for enums that provide rich metadata for UI display and API consumption.
 * Implement this interface to enable automatic legend generation and metadata exposure.
 */
public interface EnrichableEnum {

    /**
     * Get the enum name/value
     */
    String name();

    /**
     * Get localized display label
     */
    String getLabel(MessageSource messageSource, Locale locale);

    /**
     * Get localized description
     */
    String getDescription(MessageSource messageSource, Locale locale);

    /**
     * Get color theme for UI styling (e.g., "primary", "success", "danger", "warning")
     */
    String getColorTheme();

    /**
     * Get icon identifier for UI display
     */
    String getIcon();

    /**
     * Get alternative names/aliases for this status
     */
    List<String> getAliases();

    /**
     * Check if this enum can transition to another enum value
     */
    default boolean canTransitionTo(EnrichableEnum target) {
        return false;
    }

    /**
     * Get list of possible transitions from this enum value
     */
    default List<? extends EnrichableEnum> getPossibleTransitions() {
        return List.of();
    }

    /**
     * Whether this is a terminal state (no further transitions possible)
     */
    default boolean isTerminal() {
        return getPossibleTransitions().isEmpty();
    }
}
