package com.sm.instagram.platform.support.ticket.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for outgoing ticket response data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponseDtoOut {

    /**
     * Unique ID of the response.
     */
    private Long id;

    /**
     * ID of the ticket this response belongs to.
     */
    private Long ticketId;

    /**
     * The content of the response.
     */
    private String content;

    /**
     * Whether this response is from an admin or the customer.
     */
    private boolean fromAdmin;

    /**
     * Name of the admin who created this response (if applicable).
     */
    private String adminName;

    /**
     * When this response was created.
     */
    private LocalDateTime createdTime;

    /**
     * Attachments for this response.
     */
    private List<ResponseAttachmentDtoOut> attachments = new ArrayList<>();
}