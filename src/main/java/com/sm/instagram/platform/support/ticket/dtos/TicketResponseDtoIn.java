package com.sm.instagram.platform.support.ticket.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for incoming ticket response creation requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponseDtoIn {

    /**
     * The content of the response.
     */
    @NotBlank(message = "{validation.ticketResponse.content.required}")
    @Size(max = 5000, message = "{validation.ticketResponse.content.size}")
    private String content;
}