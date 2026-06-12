package com.sm.instagram.platform.support.ticket.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Service for generating unique ticket reference codes.
 */
@Slf4j
@Service
public class TicketReferenceService {
    private static final String PREFIX = "CIO";
    private static final int RANDOM_PART_LENGTH = 4;
    private final Random random = new Random();

    /**
     * Generate a unique ticket reference code.
     * @return A unique ticket reference code
     */
    public String generateTicketReference() {
        LocalDate today = LocalDate.now();
        String datePart = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String randomPart = String.format("%0" + RANDOM_PART_LENGTH + "d", random.nextInt((int) Math.pow(10, RANDOM_PART_LENGTH)));

        String ticketRef = PREFIX + "-" + datePart + "-" + randomPart;
        
        log.debug("GDPR: Operation=generateTicketReference, TicketRef={}, Purpose=ticket_creation", ticketRef);
        
        return ticketRef;
    }
}