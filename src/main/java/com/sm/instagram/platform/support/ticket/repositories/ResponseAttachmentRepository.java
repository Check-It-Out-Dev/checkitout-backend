package com.sm.instagram.platform.support.ticket.repositories;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.support.ticket.models.ResponseAttachment;
import com.sm.instagram.platform.support.ticket.models.TicketResponse;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ResponseAttachment entities.
 */
@Repository
public interface ResponseAttachmentRepository extends BaseRepository<ResponseAttachment, Long> {

    /**
     * Find all attachments for a specific response.
     *
     * @param response The response
     * @return List of attachments
     */
    List<ResponseAttachment> findByResponse(TicketResponse response);

    /**
     * Find all attachments for a specific response ID.
     *
     * @param responseId The response ID
     * @return List of attachments
     */
    List<ResponseAttachment> findByResponseId(Long responseId);

    /**
     * Delete all attachments for a specific response.
     *
     * @param response The response
     */
    void deleteByResponse(TicketResponse response);
}