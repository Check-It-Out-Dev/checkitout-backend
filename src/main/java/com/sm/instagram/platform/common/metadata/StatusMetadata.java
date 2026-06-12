package com.sm.instagram.platform.common.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Getter
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatusMetadata {

    /**
     * The enum value (e.g., "APPLIED", "ACTIVE")
     */
    private final String value;

    /**
     * Localized display label
     */
    private final String label;

    /**
     * Localized description/legend
     */
    private final String description;

    /**
     * Color theme for UI styling (e.g., "primary", "success", "danger")
     */
    private final String colorTheme;

    /**
     * Icon identifier (e.g., "user-check", "clock")
     */
    private final String icon;

    /**
     * Alternative names/aliases for this status
     */
    private final List<String> aliases;

    /**
     * List of statuses this can transition to
     */
    private final List<String> possibleTransitions;

    /**
     * Whether this is a terminal status (no further transitions)
     */
    private final Boolean isTerminal;

    /**
     * Whether this represents a successful completion
     */
    private final Boolean isSuccessful;

    /**
     * Whether this status allows login (for account statuses)
     */
    private final Boolean canLogin;

    /**
     * Whether this status is considered active (for account statuses)
     */
    private final Boolean isActive;

    /**
     * Whether this action has positive connotation (for consent actions)
     */
    private final Boolean isPositiveAction;

    /**
     * Whether this action has negative connotation (for consent actions)
     */
    private final Boolean isNegativeAction;

    /**
     * Whether this action represents a modification (for consent actions)
     */
    private final Boolean isModificationAction;
}
