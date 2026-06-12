package com.sm.instagram.platform.support.ticket.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response for ticket creation requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketCreationResponse {

    /**
     * Whether the ticket was created successfully.
     */
    private boolean success;

    /**
     * Message to display to the user.
     */
    private String message;

    /**
     * Reference code for the ticket.
     * Only present if ticket was created successfully.
     */
    private String ticketReference;

    /**
     * Constructor without ticket reference for error responses.
     */
    public TicketCreationResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}