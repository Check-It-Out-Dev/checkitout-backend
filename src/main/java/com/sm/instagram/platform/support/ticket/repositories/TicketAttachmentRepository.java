package com.sm.instagram.platform.support.ticket.repositories;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.support.ticket.models.SupportTicket;
import com.sm.instagram.platform.support.ticket.models.TicketAttachment;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for TicketAttachment entities.
 */
@Repository
public interface TicketAttachmentRepository extends BaseRepository<TicketAttachment, Long> {

    /**
     * Find all attachments for a specific ticket.
     *
     * @param ticket The ticket
     * @return List of attachments
     */
    List<TicketAttachment> findByTicket(SupportTicket ticket);

    /**
     * Find all attachments for a specific ticket ID.
     *
     * @param ticketId The ticket ID
     * @return List of attachments
     */
    List<TicketAttachment> findByTicketId(Long ticketId);

    /**
     * Delete all attachments for a specific ticket.
     *
     * @param ticket The ticket
     */
    void deleteByTicket(SupportTicket ticket);
}