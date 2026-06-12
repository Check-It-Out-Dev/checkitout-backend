package com.sm.instagram.platform.support.ticket.models;

import org.springframework.context.MessageSource;
import java.util.Locale;

/**
 * Enum representing the possible states of a support ticket.
 */
public enum TicketStatus {
    /**
     * Ticket has been created but no admin has started working on it yet.
     */
    OPEN,

    /**
     * An admin is actively working on the ticket.
     */
    IN_PROGRESS,

    /**
     * Admin has replied and is waiting for the customer to respond.
     */
    WAITING_FOR_CUSTOMER,

    /**
     * The issue has been resolved but the ticket is still active.
     */
    RESOLVED,

    /**
     * The ticket has been closed and no further action will be taken.
     */
    CLOSED;

    /**
     * Get a human-readable label for this status based on the provided locale.
     *
     * @param messageSource The message source for localization
     * @param locale The locale to use for the translation
     * @return Localized status label
     */
    public String getLabel(MessageSource messageSource, Locale locale) {
        return messageSource.getMessage("support.ticket.status." + name(), null, locale);
    }

    /**
     * Get a user-friendly label for this status (without needing MessageSource).
     *
     * @return A user-friendly status label
     */
    public String getDisplayName() {
        return switch(this) {
            case OPEN -> "Open";
            case IN_PROGRESS -> "In Progress";
            case WAITING_FOR_CUSTOMER -> "Awaiting Your Reply";
            case RESOLVED -> "Resolved";
            case CLOSED -> "Closed";
        };
    }

    /**
     * Check if a status transition is valid.
     *
     * @param newStatus The status to transition to
     * @return True if the transition is allowed, false otherwise
     */
    public boolean canTransitionTo(TicketStatus newStatus) {
        return switch(this) {
            case OPEN -> newStatus == IN_PROGRESS || newStatus == RESOLVED || newStatus == CLOSED;
            case IN_PROGRESS -> newStatus == WAITING_FOR_CUSTOMER || newStatus == RESOLVED || newStatus == CLOSED;
            case WAITING_FOR_CUSTOMER -> newStatus == IN_PROGRESS || newStatus == RESOLVED || newStatus == CLOSED;
            case RESOLVED -> newStatus == IN_PROGRESS || newStatus == CLOSED || newStatus == WAITING_FOR_CUSTOMER;
            case CLOSED -> false; // Closed tickets cannot be reopened for simplicity
        };
    }
}