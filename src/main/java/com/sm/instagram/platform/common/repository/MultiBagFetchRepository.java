package com.sm.instagram.platform.common.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

/**
 * Generic interface for repositories that need to handle fetching entities
 * with multiple collections (bags) to avoid MultipleBagFetchException.
 * 
 * @param <T> The entity type
 * @param <ID> The ID type of the entity
 */
public interface MultiBagFetchRepository<T, ID> {
    
    /**
     * Find all entities with all associations fetched using multiple queries
     * to avoid MultipleBagFetchException.
     * 
     * @param spec the specification for filtering
     * @param pageable the pagination information
     * @return page of fully initialized entities
     */
    Page<T> findAllWithAssociationsFetched(Specification<T> spec, Pageable pageable);
    
    /**
     * Find an entity by ID with all associations fetched using multiple queries
     * to avoid MultipleBagFetchException.
     * 
     * @param id the ID of the entity
     * @return optional of fully initialized entity
     */
    Optional<T> findByIdWithAssociationsFetched(ID id);
}
