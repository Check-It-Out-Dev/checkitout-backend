package com.sm.instagram.platform.support.ticket.dtos;

import com.sm.instagram.platform.support.ticket.models.TicketCategory;
import com.sm.instagram.platform.support.ticket.models.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for outgoing support ticket responses.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketDtoOut {

    /**
     * Unique ID of the ticket.
     */
    private Long id;

    /**
     * Email address for communication.
     */
    private String contactEmail;

    /**
     * Brief description of the issue.
     */
    private String subject;

    /**
     * Detailed description of the issue.
     */
    private String description;

    /**
     * Current status of the ticket.
     */
    private TicketStatus status;

    /**
     * User-friendly display name of the status.
     */
    private String statusDisplay;

    /**
     * Category of the ticket.
     */
    private TicketCategory category;

    /**
     * User-friendly display name of the category.
     */
    private String categoryDisplay;

    /**
     * Unique reference code for the ticket.
     */
    private String ticketReference;

    /**
     * Admin user assigned to handle this ticket.
     */
    private String adminAssignee;

    /**
     * When the ticket was created.
     */
    private LocalDateTime createdTime;

    /**
     * When the ticket was last updated.
     */
    private LocalDateTime lastUpdateTime;

    /**
     * When the ticket was resolved.
     */
    private LocalDateTime resolvedTime;

    /**
     * Response history for this ticket.
     */
    private List<TicketResponseDtoOut> responses = new ArrayList<>();

    /**
     * Attachments for this ticket.
     */
    private List<TicketAttachmentDtoOut> attachments = new ArrayList<>();

    /**
     * Whether this ticket is resolved or closed.
     */
    private boolean resolved;

    /**
     * Technical error dump captured by the FE error-report autofill flow
     * (browser info + raw error JSON, up to 100k chars). Admin eyes only:
     * populated exclusively when the caller of getTicketById holds the
     * ADMIN role; null on every other path, including the owner's own
     * ticket views and the public reference+email lookup.
     */
    private String technicalDescription;
}