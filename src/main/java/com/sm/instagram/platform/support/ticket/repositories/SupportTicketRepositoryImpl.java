package com.sm.instagram.platform.support.ticket.repositories;

import com.sm.instagram.platform.common.repository.AbstractMultiBagFetchRepositoryImpl;
import com.sm.instagram.platform.support.ticket.models.SupportTicket;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Custom implementation for SupportTicket repository to handle multiple bag fetch issues.
 * This implementation prevents MultipleBagFetchException when fetching SupportTicket entities
 * with their responses and attachments collections by using separate queries.
 */
@Repository
public class SupportTicketRepositoryImpl extends AbstractMultiBagFetchRepositoryImpl<SupportTicket, Long> 
        implements SupportTicketRepositoryCustom {

    @Override
    protected Class<SupportTicket> getEntityClass() {
        return SupportTicket.class;
    }

    @Override
    protected String getIdFieldName() {
        return "id";
    }

    @Override
    protected List<String> getFetchQueries() {
        return List.of(
            // Query 1: Fetch tickets with responses
            "SELECT DISTINCT st FROM SupportTicket st LEFT JOIN FETCH st.responses WHERE st.id IN :ids",
            
            // Query 2: Fetch tickets with attachments
            "SELECT DISTINCT st FROM SupportTicket st LEFT JOIN FETCH st.attachments WHERE st.id IN :ids"
        );
    }
}
