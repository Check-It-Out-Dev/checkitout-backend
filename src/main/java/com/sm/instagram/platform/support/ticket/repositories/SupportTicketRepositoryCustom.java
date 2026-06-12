package com.sm.instagram.platform.support.ticket.repositories;

import com.sm.instagram.platform.common.repository.MultiBagFetchRepository;
import com.sm.instagram.platform.support.ticket.models.SupportTicket;

/**
 * Custom repository interface for SupportTicket entities to handle multiple bag fetch issues.
 * This interface extends MultiBagFetchRepository to provide methods that safely fetch
 * SupportTicket entities with their collections (responses and attachments) without
 * causing MultipleBagFetchException.
 */
public interface SupportTicketRepositoryCustom extends MultiBagFetchRepository<SupportTicket, Long> {
    // Inherits all necessary methods from MultiBagFetchRepository
}
