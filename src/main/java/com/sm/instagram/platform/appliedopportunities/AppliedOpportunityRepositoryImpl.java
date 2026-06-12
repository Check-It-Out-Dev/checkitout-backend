package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.common.repository.AbstractMultiBagFetchRepositoryImpl;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Custom repository implementation for AppliedOpportunity that handles complex fetching
 * using multiple queries to avoid MultipleBagFetchException.
 */
@Repository
public class AppliedOpportunityRepositoryImpl 
        extends AbstractMultiBagFetchRepositoryImpl<AppliedOpportunity, Long> 
        implements AppliedOpportunityRepositoryCustom {
    
    @Override
    protected Class<AppliedOpportunity> getEntityClass() {
        return AppliedOpportunity.class;
    }
    
    @Override
    protected String getIdFieldName() {
        return "id";
    }
    
    @Override
    protected List<String> getFetchQueries() {
        return List.of(
            // Query 1: Fetch AppliedOpportunity with contentSubmissions (first bag)
            "SELECT DISTINCT ao FROM AppliedOpportunity ao " +
            "LEFT JOIN FETCH ao.contentSubmissions " +
            "LEFT JOIN FETCH ao.influencer " +
            "LEFT JOIN FETCH ao.partnershipOpportunity po " +
            "LEFT JOIN FETCH po.company " +
            "LEFT JOIN FETCH po.address " +
            "LEFT JOIN FETCH po.city " +
            "LEFT JOIN FETCH po.currency " +
            "LEFT JOIN FETCH po.serviceType " +
            "WHERE ao.id IN :ids",
            
            // Query 2: Fetch the same entities with partnershipOpportunity.photos (second bag)
            "SELECT DISTINCT ao FROM AppliedOpportunity ao " +
            "LEFT JOIN FETCH ao.partnershipOpportunity po " +
            "LEFT JOIN FETCH po.photos " +
            "WHERE ao.id IN :ids",
            
            // Query 3: Fetch the platforms and contentTypes (Sets, not bags)
            "SELECT DISTINCT ao FROM AppliedOpportunity ao " +
            "LEFT JOIN FETCH ao.partnershipOpportunity po " +
            "LEFT JOIN FETCH po.platforms " +
            "LEFT JOIN FETCH po.contentTypes " +
            "WHERE ao.id IN :ids"
        );
    }

}
