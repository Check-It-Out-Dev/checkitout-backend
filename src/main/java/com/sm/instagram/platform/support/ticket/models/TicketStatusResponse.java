package com.sm.instagram.platform.support.ticket.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Response for ticket status check requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatusResponse {

    /**
     * Whether the ticket was found.
     */
    private boolean found;

    /**
     * Reference code for the ticket.
     */
    private String ticketReference;

    /**
     * Subject of the ticket.
     */
    private String subject;

    /**
     * Current status of the ticket.
     */
    private TicketStatus status;

    /**
     * User-friendly display name of the status.
     */
    private String statusDisplay;

    /**
     * When the ticket was created.
     */
    private LocalDateTime createdTime;

    /**
     * When the ticket was last updated.
     */
    private LocalDateTime lastUpdateTime;

    /**
     * Message to display to the user.
     */
    private String message;

    /**
     * Number of responses on the ticket.
     */
    private int responseCount;

    /**
     * Whether the ticket has a response from admin.
     */
    private boolean hasAdminResponse;

    /**
     * Constructor for when ticket is not found.
     */
    public TicketStatusResponse(boolean found, String message) {
        this.found = found;
        this.message = message;
    }
}