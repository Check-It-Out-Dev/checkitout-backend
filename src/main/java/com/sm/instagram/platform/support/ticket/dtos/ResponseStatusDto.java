package com.sm.instagram.platform.support.ticket.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response for ticket response creation requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseStatusDto {

    /**
     * Whether the response was created successfully.
     */
    private boolean success;

    /**
     * Message to display to the user.
     */
    private String message;
}