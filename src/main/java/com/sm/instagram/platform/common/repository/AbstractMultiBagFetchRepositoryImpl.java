package com.sm.instagram.platform.common.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Abstract base implementation for repositories that need to handle fetching entities
 * with multiple collections (bags) to avoid MultipleBagFetchException.
 * 
 * This class provides a generic implementation that executes multiple queries
 * and leverages Hibernate's first-level cache to merge the results.
 * 
 * @param <T> The entity type
 * @param <ID> The ID type of the entity
 */
@Transactional(readOnly = true)
public abstract class AbstractMultiBagFetchRepositoryImpl<T, ID> implements MultiBagFetchRepository<T, ID> {
    
    @PersistenceContext
    protected EntityManager entityManager;
    
    protected abstract Class<T> getEntityClass();
    protected abstract String getIdFieldName();
    
    /**
     * Define the fetch queries that should be executed.
     * Each query should fetch a different collection to avoid MultipleBagFetchException.
     * 
     * Example implementation:
     * <pre>
     * return List.of(
     *     "SELECT DISTINCT e FROM Entity e LEFT JOIN FETCH e.collection1 WHERE e.id IN :ids",
     *     "SELECT DISTINCT e FROM Entity e LEFT JOIN FETCH e.collection2 WHERE e.id IN :ids"
     * );
     * </pre>
     * 
     * @return List of JPQL queries to execute
     */
    protected abstract List<String> getFetchQueries();
    
    @Override
    public Page<T> findAllWithAssociationsFetched(Specification<T> spec, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        Class<T> entityClass = getEntityClass();
        
        // Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> countRoot = countQuery.from(entityClass);
        countQuery.select(cb.countDistinct(countRoot));
        
        if (spec != null) {
            Predicate predicate = spec.toPredicate(countRoot, countQuery, cb);
            if (predicate != null) {
                countQuery.where(predicate);
            }
        }
        
        Long total = entityManager.createQuery(countQuery).getSingleResult();
        
        // For PostgreSQL compatibility with DISTINCT and ORDER BY, we need to select the entire entity
        // and then extract IDs. This is because PostgreSQL requires ORDER BY columns to be in SELECT when using DISTINCT.
        // Note: This approach fetches entities twice (once here for IDs, once in fetchWithMultipleQueries),
        // but it's necessary to maintain correct pagination with sorting while avoiding the PostgreSQL constraint.
        // The first query here is lightweight as it doesn't fetch associations.
        CriteriaQuery<T> entityQuery = cb.createQuery(entityClass);
        Root<T> entityRoot = entityQuery.from(entityClass);
        entityQuery.select(entityRoot).distinct(true);
        
        if (spec != null) {
            Predicate predicate = spec.toPredicate(entityRoot, entityQuery, cb);
            if (predicate != null) {
                entityQuery.where(predicate);
            }
        }
        
        // Apply sorting
        if (pageable.getSort().isSorted()) {
            entityQuery.orderBy(QueryUtils.toOrders(pageable.getSort(), entityRoot, cb));
        } else {
            entityQuery.orderBy(cb.asc(entityRoot.get(getIdFieldName())));
        }
        
        TypedQuery<T> typedEntityQuery = entityManager.createQuery(entityQuery);
        typedEntityQuery.setFirstResult((int) pageable.getOffset());
        typedEntityQuery.setMaxResults(pageable.getPageSize());
        
        // Extract IDs from the results
        List<ID> ids = typedEntityQuery.getResultList().stream()
            .map(this::getEntityId)
            .collect(Collectors.toList());
        
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        
        // Fetch entities with multiple queries
        List<T> results = fetchWithMultipleQueries(ids);
        
        // Sort results according to the original ID order
        List<T> sortedResults = sortResultsByIdOrder(ids, results);
        
        return new PageImpl<>(sortedResults, pageable, total);
    }
    
    @Override
    public Optional<T> findByIdWithAssociationsFetched(ID id) {
        List<T> results = fetchWithMultipleQueries(List.of(id));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    /**
     * Fetches entities with their associations using multiple queries to avoid MultipleBagFetchException.
     * Hibernate's first-level cache will automatically merge the results.
     */
    protected List<T> fetchWithMultipleQueries(List<ID> ids) {
        List<String> queries = getFetchQueries();
        
        if (queries.isEmpty()) {
            throw new IllegalStateException("No fetch queries defined. Override getFetchQueries() method.");
        }
        
        // Execute the first query to get the base entities
        TypedQuery<T> firstQuery = entityManager.createQuery(queries.get(0), getEntityClass());
        firstQuery.setParameter("ids", ids);
        List<T> results = firstQuery.getResultList();
        
        // Execute remaining queries to fetch additional associations
        // These will use the entities already in the persistence context
        for (int i = 1; i < queries.size(); i++) {
            TypedQuery<T> query = entityManager.createQuery(queries.get(i), getEntityClass());
            query.setParameter("ids", ids);
            query.getResultList(); // Results are merged into existing entities in persistence context
        }
        
        return results;
    }
    
    /**
     * Sorts the results according to the original ID order.
     */
    protected List<T> sortResultsByIdOrder(List<ID> ids, List<T> results) {
        return ids.stream()
            .map(id -> results.stream()
                .filter(entity -> getEntityId(entity).equals(id))
                .findFirst()
                .orElse(null))
            .filter(entity -> entity != null)
            .collect(Collectors.toList());
    }
    
    /**
     * Get the ID value from an entity instance.
     * Override this if your entity has a non-standard ID field name.
     */
    protected ID getEntityId(T entity) {
        try {
            return (ID) entity.getClass().getMethod("getId").invoke(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get ID from entity", e);
        }
    }
    
    /**
     * Get the ID type class.
     * Override this if needed.
     */
    @SuppressWarnings("unchecked")
    protected Class<ID> getIdType() {
        // This is a simplified implementation
        // In production, you might want to use reflection to determine the actual ID type
        return (Class<ID>) Long.class;
    }
}
