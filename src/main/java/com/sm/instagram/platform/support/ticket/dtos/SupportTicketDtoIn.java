package com.sm.instagram.platform.support.ticket.dtos;

import com.sm.instagram.platform.support.ticket.models.TicketCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for incoming support ticket creation requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketDtoIn {

    /**
     * Email address for communication.
     * Required for all tickets, even from logged-in users.
     */
    @NotBlank(message = "{validation.ticket.contactEmail.required}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 255, message = "{validation.email.size}")
    private String contactEmail;

    /**
     * Brief description of the issue.
     */
    @NotBlank(message = "{validation.ticket.subject.required}")
    @Size(max = 255, message = "{validation.ticket.subject.size}")
    private String subject;

    /**
     * Detailed description of the issue.
     */
    @NotBlank(message = "{validation.ticket.description.required}")
    @Size(max = 5000, message = "{validation.ticket.description.size}")
    private String description;

    /**
     * Category of the ticket.
     */
    @NotNull(message = "{validation.ticket.category.required}")
    private TicketCategory category;

    /**
     * Technical description containing error logs, parameters, debug information etc.
     * Optional field for error reporting and technical details.
     */
    @Size(max = 100000, message = "{validation.ticket.technicalDescription.size}")
    private String technicalDescription;
}