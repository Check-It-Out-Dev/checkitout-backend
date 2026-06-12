package com.sm.instagram.platform.support.ticket.repositories;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.support.ticket.models.SupportTicket;
import com.sm.instagram.platform.support.ticket.models.TicketResponse;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for TicketResponse entities.
 */
@Repository
public interface TicketResponseRepository extends BaseRepository<TicketResponse, Long> {

    /**
     * Find all responses for a specific ticket, ordered by creation time.
     *
     * @param ticket The ticket
     * @return List of responses
     */
    List<TicketResponse> findByTicketOrderByCreatedTimeAsc(SupportTicket ticket);

    /**
     * Find all responses for a specific ticket ID, ordered by creation time.
     *
     * @param ticketId The ticket ID
     * @return List of responses
     */
    List<TicketResponse> findByTicketIdOrderByCreatedTimeAsc(Long ticketId);

    /**
     * Find responses that need email notifications sent.
     *
     * @return List of responses
     */
    List<TicketResponse> findByIsEmailSentFalseAndIsFromAdminTrue();
}