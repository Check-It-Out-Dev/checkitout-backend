package com.sm.instagram.platform.support.ticket.services;

import com.sm.instagram.platform.support.ticket.repositories.SupportTicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates unique, unguessable ticket reference codes of the form
 * {@code CIO-yyyyMMdd-XXXXXXXX}.
 *
 * <p>Security (pentest 3.3): the previous scheme used {@link java.util.Random}
 * with a 4-digit suffix — only 10 000 values per day and no uniqueness check,
 * so references were brute-forceable and enumerable (paired with the
 * by-reference/email lookup). This uses {@link SecureRandom} over an 8-char
 * unambiguous alphabet (~40 bits of entropy per day) and re-rolls on the rare
 * database collision.
 */
@Slf4j
@Service
public class TicketReferenceService {

    private static final String PREFIX = "CIO";
    // Crockford base32 — excludes I/L/O/U so codes are unambiguous when read
    // aloud or copied from an email.
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int RANDOM_PART_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 10;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SecureRandom secureRandom = new SecureRandom();
    private final SupportTicketRepository ticketRepository;

    public TicketReferenceService(SupportTicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Generate a unique ticket reference code.
     *
     * @return a reference that does not yet exist in the database
     * @throws IllegalStateException if no free code is found within
     *                               {@value #MAX_ATTEMPTS} attempts (with ~40
     *                               bits of entropy this is effectively
     *                               impossible)
     */
    public String generateTicketReference() {
        String datePart = LocalDate.now().format(DATE_FORMAT);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String ticketRef = PREFIX + "-" + datePart + "-" + randomSuffix();
            if (!ticketRepository.existsByTicketReference(ticketRef)) {
                log.debug("GDPR: Operation=generateTicketReference, TicketRef={}, Purpose=ticket_creation", ticketRef);
                return ticketRef;
            }
            log.warn("Ticket reference collision on attempt {}, retrying", attempt + 1);
        }
        throw new IllegalStateException(
                "Unable to generate a unique ticket reference after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(RANDOM_PART_LENGTH);
        for (int i = 0; i < RANDOM_PART_LENGTH; i++) {
            sb.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
