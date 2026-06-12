package com.sm.instagram.platform.partnershipopportunities;

import com.sm.instagram.platform.common.repository.AbstractMultiBagFetchRepositoryImpl;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Custom repository implementation for PartnershipOpportunity that handles complex fetching
 * using multiple queries to avoid MultipleBagFetchException.
 */
@Repository
public class PartnershipOpportunityRepositoryImpl 
        extends AbstractMultiBagFetchRepositoryImpl<PartnershipOpportunity, Long> 
        implements PartnershipOpportunityRepositoryCustom {
    
    @Override
    protected Class<PartnershipOpportunity> getEntityClass() {
        return PartnershipOpportunity.class;
    }
    
    @Override
    protected String getIdFieldName() {
        return "id";
    }
    
    @Override
    protected List<String> getFetchQueries() {
        return List.of(
            // Query 1: Fetch PartnershipOpportunity with photos (first bag)
            "SELECT DISTINCT po FROM PartnershipOpportunity po " +
            "LEFT JOIN FETCH po.photos " +
            "LEFT JOIN FETCH po.company " +
            "LEFT JOIN FETCH po.address " +
            "LEFT JOIN FETCH po.city " +
            "LEFT JOIN FETCH po.currency " +
            "LEFT JOIN FETCH po.serviceType " +
            "WHERE po.id IN :ids",
            
            // Query 2: Fetch PartnershipOpportunity with appliedOpportunities (second bag)
            "SELECT DISTINCT po FROM PartnershipOpportunity po " +
            "LEFT JOIN FETCH po.appliedOpportunities " +
            "WHERE po.id IN :ids",
            
            // Query 3: Fetch the platforms and contentTypes (Sets, not bags)
            "SELECT DISTINCT po FROM PartnershipOpportunity po " +
            "LEFT JOIN FETCH po.platforms " +
            "LEFT JOIN FETCH po.contentTypes " +
            "WHERE po.id IN :ids"
        );
    }
}
