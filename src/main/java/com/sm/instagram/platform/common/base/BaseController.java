package com.sm.instagram.platform.common.base;

import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)  // Global default: 60 requests per minute per endpoint
public abstract class BaseController<T, I, D, O> {

    private final Class<T> entityClass;

    protected BaseController(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    protected abstract BaseService<T, I, D> getService();

    /**
     * Retrieves an entity by its ID
     *
     * @param id the entity ID
     * @return the entity if found
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get entity by ID", description = "Retrieves an entity by its unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entity found"),
            @ApiResponse(responseCode = "404", description = "Entity not found"),
            @ApiResponse(responseCode = "403", description = "User not authorized to access this entity")
    })
    public ResponseEntity<O> getById(@PathVariable I id) {
        log.info("BaseController: Retrieving {} with ID: {}", entityClass.getSimpleName(), id);
        long startTime = System.currentTimeMillis();

        try {
            // CHANGED: Use service AsDto method instead of modelMapper
            @SuppressWarnings("unchecked")
            O result = (O) getService().findByIdAsDto(id);

            long duration = System.currentTimeMillis() - startTime;
            log.debug("BaseController: Successfully retrieved {} with ID: {} in {}ms",
                    entityClass.getSimpleName(), id, duration);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("BaseController: Failed to retrieve {} with ID: {} after {}ms - {}",
                    entityClass.getSimpleName(), id, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves a paginated list of entities with optional filtering
     *
     * @param pageable pagination information
     * @param filters  map of filter criteria
     * @return paginated list of entities
     */
    @GetMapping(value = "/paged")
    @Operation(summary = "Get paginated entities", description = "Retrieves a paginated list of entities with optional filtering")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful search"),
            @ApiResponse(responseCode = "500", description = "Error"),
            @ApiResponse(responseCode = "403", description = "User not authorized to access this entity")
    })
    public ResponseEntity<Page<O>> findPaginated(Pageable pageable, @RequestParam Map<String, String> filters) {
        log.info("BaseController: Retrieving paginated {} - page: {}, size: {}, filters: {}",
                entityClass.getSimpleName(), pageable.getPageNumber(), pageable.getPageSize(),
                filters.keySet());
        long startTime = System.currentTimeMillis();

        // Remove pagination parameters from filters
        filters.remove("page");
        filters.remove("size");
        filters.remove("sort");
        filters.remove("direction");

        try {
            // Clean implementation: all services must implement getDataPagedAndFilteredAsDtos
            @SuppressWarnings("unchecked")
            Page<O> dtoPage = (Page<O>) getService().getDataPagedAndFilteredAsDtos(pageable, filters);

            long duration = System.currentTimeMillis() - startTime;
            log.info("BaseController: Successfully retrieved {} {} records (total: {}) in {}ms",
                    dtoPage.getNumberOfElements(), entityClass.getSimpleName(),
                    dtoPage.getTotalElements(), duration);

            return ResponseEntity.ok(dtoPage);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("BaseController: Failed to retrieve paginated {} after {}ms - {}",
                    entityClass.getSimpleName(), duration, e.getMessage());
            throw e;
        }
    }

    /**
     * Creates a new entity
     *
     * @param dto the entity data
     * @return the created entity
     */
    @PostMapping
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)  // 10 req/min per endpoint
    @Operation(summary = "Create a new entity", description = "Creates a new entity with the provided data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entity created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "403", description = "User not authorized to create entity")
    })
    public ResponseEntity<O> create(@Valid @RequestBody D dto) {
        log.info("BaseController: Creating new {}", entityClass.getSimpleName());
        log.debug("BaseController: Creating {} with data: {}", entityClass.getSimpleName(), dto);
        long startTime = System.currentTimeMillis();

        try {
            // CHANGED: Use service createFromDtoAsDto method
            @SuppressWarnings("unchecked")
            O result = (O) getService().createFromDtoAsDto(dto);

            long duration = System.currentTimeMillis() - startTime;
            log.info("BaseController: Successfully created {} in {}ms",
                    entityClass.getSimpleName(), duration);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("BaseController: Failed to create {} after {}ms - {}",
                    entityClass.getSimpleName(), duration, e.getMessage());
            throw e;
        }
    }

    /**
     * Updates an existing entity
     *
     * @param id  the entity ID
     * @param dto the updated entity data
     * @return the updated entity
     */
    @PutMapping("/{id}")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)  // 10 req/min per endpoint
    @Operation(summary = "Update entity", description = "Updates an existing entity with the provided data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entity updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "403", description = "User not authorized to update entity"),
            @ApiResponse(responseCode = "404", description = "Entity not found")
    })
    public ResponseEntity<O> update(@PathVariable I id, @Valid @RequestBody D dto) {
        log.info("BaseController: Updating {} with ID: {}", entityClass.getSimpleName(), id);
        log.debug("BaseController: Updating {} with ID: {} using data: {}",
                entityClass.getSimpleName(), id, dto);
        long startTime = System.currentTimeMillis();

        try {
            // CHANGED: Use service updateAsDto method
            @SuppressWarnings("unchecked")
            O result = (O) getService().updateAsDto(id, dto);

            long duration = System.currentTimeMillis() - startTime;
            log.info("BaseController: Successfully updated {} with ID: {} in {}ms",
                    entityClass.getSimpleName(), id, duration);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("BaseController: Failed to update {} with ID: {} after {}ms - {}",
                    entityClass.getSimpleName(), id, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * Partially updates an entity with the provided field values
     *
     * @param id      the entity ID
     * @param updates map of field names and their new values
     * @return the updated entity
     */
    @PatchMapping("/{id}")
    @RateLimit(value = 30, duration = 60, keyType = RateLimitKeyType.USER_ENDPOINT)  // 30 req/min per endpoint
    @Operation(summary = "Partially update entity", description = "Updates specific fields of an existing entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entity updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "403", description = "User not authorized to update entity"),
            @ApiResponse(responseCode = "404", description = "Entity not found")
    })
    public ResponseEntity<O> patch(@PathVariable I id, @Valid @RequestBody Map<String, Object> updates) {
        log.info("BaseController: Patching {} with ID: {} - fields: {}",
                entityClass.getSimpleName(), id, updates.keySet());
        log.debug("BaseController: Patching {} with ID: {} using updates: {}",
                entityClass.getSimpleName(), id, updates);
        long startTime = System.currentTimeMillis();

        try {
            // CHANGED: Use service patchAsDto method
            @SuppressWarnings("unchecked")
            O result = (O) getService().patchAsDto(id, updates);

            long duration = System.currentTimeMillis() - startTime;
            log.info("BaseController: Successfully patched {} with ID: {} in {}ms",
                    entityClass.getSimpleName(), id, duration);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("BaseController: Failed to patch {} with ID: {} after {}ms - {}",
                    entityClass.getSimpleName(), id, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * Deletes one or more entities by their IDs
     *
     * @param ids the entity IDs to delete
     * @return void response with appropriate status code
     */
    @DeleteMapping("/{ids}")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)  // 10 req/min per endpoint
    @Transactional
    @Operation(summary = "Delete entities", description = "Deletes one or more entities if the user has permission")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Entities deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (empty ID list or too many IDs)"),
            @ApiResponse(responseCode = "403", description = "User not authorized to delete one or more entities")
    })
    public ResponseEntity<Void> delete(@PathVariable List<I> ids) {
        log.info("BaseController: Deleting {} {} with IDs: {}",
                ids.size(), entityClass.getSimpleName(), ids);
        long startTime = System.currentTimeMillis();

        if (ids.isEmpty()) {
            log.warn("BaseController: Delete request rejected - ID list is null or empty");
            throw new ValidationTranslatableException("error.validation.empty_list", "IDs");
        }
        if (ids.size() > 100) {
            log.warn("BaseController: Delete request rejected - too many IDs: {}", ids.size());
            throw new ValidationTranslatableException("error.validation.list_too_large", "100");
        }

        try {
            if (ids.size() == 1) {
                getService().delete(ids.getFirst());
                log.info("BaseController: Successfully deleted single {} with ID: {}",
                        entityClass.getSimpleName(), ids.getFirst());
            } else {
                getService().deleteAll(ids);
                log.info("BaseController: Successfully deleted {} {} entities",
                        ids.size(), entityClass.getSimpleName());
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("BaseController: Delete operation completed in {}ms", duration);

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("BaseController: Failed to delete {} {} after {}ms - {}",
                    ids.size(), entityClass.getSimpleName(), duration, e.getMessage());
            throw e;
        }
    }
}
