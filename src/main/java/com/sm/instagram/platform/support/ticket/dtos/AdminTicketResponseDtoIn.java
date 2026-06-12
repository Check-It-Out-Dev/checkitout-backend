package com.sm.instagram.platform.support.ticket.dtos;

import com.sm.instagram.platform.support.ticket.dtos.TicketResponseDtoIn;
import com.sm.instagram.platform.support.ticket.models.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for incoming admin ticket response creation requests.
 * Extends the basic TicketResponseDtoIn with additional admin-specific fields.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminTicketResponseDtoIn extends TicketResponseDtoIn {

    /**
     * Optional new status to set for the ticket.
     */
    private TicketStatus newStatus;

    /**
     * Optional admin name or identifier.
     */
    @Size(max = 255, message = "{validation.ticketResponse.adminName.size}")
    private String adminName;

    /**
     * Whether to send an email notification for this response.
     */
    private boolean sendEmail = true;
}