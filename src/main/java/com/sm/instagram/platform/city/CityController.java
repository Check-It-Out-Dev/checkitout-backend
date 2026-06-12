package com.sm.instagram.platform.city;

import com.sm.instagram.platform.common.base.BaseController;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing cities.
 * Provides endpoints for retrieving city data.
 * Requires admin authentication for all operations.
 */
@Slf4j
@RestController
@RequestMapping("city")
@PreAuthorize("hasAuthority('ADMIN')")
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
public class CityController extends BaseController<City, Long, CityDto, CityDto> {
    private final CityService cityService;

    protected CityController(CityService cityService) {
        super(City.class);
        this.cityService = cityService;
    }

    /**
     * Get city by ID.
     *
     * @param id The city ID
     * @return City data
     * @throws ValidationTranslatableException if ID is invalid
     * @throws ResourceNotFoundException       if authentication missing or city not found
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<CityDto> getById(@PathVariable Long id) {
        // Validate ID parameter
        if (id == null || id <= 0) {
            throw new ValidationTranslatableException("error.validation.invalid_id", "id");
        }

        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=getCityById, FirebaseUID={}, CityID={}, Purpose=city_retrieval",
                firebaseUid, id);
        long startTime = System.currentTimeMillis();

        ResponseEntity<CityDto> response = super.getById(id);

        long duration = System.currentTimeMillis() - startTime;
        log.info("GDPR: DataAccessed=city.all_fields, FirebaseUID={}, CityID={}, Duration={}ms, Purpose=display",
                firebaseUid, id, duration);

        return response;
    }

    /**
     * Get paginated cities.
     *
     * @param pageable Pagination parameters
     * @param filters  Filter parameters
     * @return Page of cities
     * @throws ValidationTranslatableException if pagination parameters are invalid
     * @throws ResourceNotFoundException       if authentication missing
     */
    @PreAuthorize("isAuthenticated()")
    @Override
    @GetMapping(value = "/paged")
    public ResponseEntity<Page<CityDto>> findPaginated(Pageable pageable, @RequestParam Map<String, String> filters) {
        // Validate pagination parameters
        if (pageable.getPageSize() > 1000) {
            throw new ValidationTranslatableException("error.validation.list_too_large", "1000");
        }
        if (pageable.getPageNumber() < 0) {
            throw new ValidationTranslatableException("error.validation.invalid_parameter", "page number must be non-negative");
        }

        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=getCitiesPaged, FirebaseUID={}, Page={}, Size={}, Purpose=city_list_retrieval",
                firebaseUid, pageable.getPageNumber(), pageable.getPageSize());
        long startTime = System.currentTimeMillis();

        // Remove pagination parameters from filters
        filters.remove("page");
        filters.remove("size");
        filters.remove("sort");
        filters.remove("direction");

        ResponseEntity<Page<CityDto>> response = super.findPaginated(pageable, filters);
        Page<CityDto> page = response.getBody();

        long duration = System.currentTimeMillis() - startTime;
        if (page != null) {
            log.info("GDPR: DataAccessed=cities[], FirebaseUID={}, RecordCount={}, TotalRecords={}, Duration={}ms, Purpose=list_display",
                    firebaseUid, page.getNumberOfElements(), page.getTotalElements(), duration);
            log.debug("Applied filters: {}", filters);
        }

        return response;
    }

    /**
     * Update city - ADMIN ONLY.
     * Override to explicitly require ADMIN authority.
     *
     * @param id  The city ID
     * @param dto The updated city data
     * @return Updated city
     */
    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<CityDto> update(@PathVariable Long id, @RequestBody CityDto dto) {
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=updateCity, FirebaseUID={}, CityID={}, Purpose=city_update",
                firebaseUid, id);
        long startTime = System.currentTimeMillis();

        ResponseEntity<CityDto> response = super.update(id, dto);

        long duration = System.currentTimeMillis() - startTime;
        log.info("GDPR: DataAccessed=city.all_fields, DataModified=city.all_fields, FirebaseUID={}, CityID={}, Duration={}ms, Purpose=update",
                firebaseUid, id, duration);

        return response;
    }

    /**
     * Partially update city - ADMIN ONLY.
     * Override to explicitly require ADMIN authority.
     *
     * @param id      The city ID
     * @param updates Map of fields to update
     * @return Updated city
     */
    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<CityDto> patch(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=patchCity, FirebaseUID={}, CityID={}, Fields={}, Purpose=partial_city_update",
                firebaseUid, id, updates.keySet());
        long startTime = System.currentTimeMillis();

        ResponseEntity<CityDto> response = super.patch(id, updates);

        long duration = System.currentTimeMillis() - startTime;
        log.info("GDPR: DataAccessed=city.{}, DataModified=city.{}, FirebaseUID={}, CityID={}, Duration={}ms, Purpose=patch",
                updates.keySet(), updates.keySet(), firebaseUid, id, duration);

        return response;
    }

    /**
     * Create new city - ADMIN ONLY.
     * Override to explicitly require ADMIN authority.
     *
     * @param dto The city data
     * @return Created city
     */
    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping
    public ResponseEntity<CityDto> create(@RequestBody CityDto dto) {
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=createCity, FirebaseUID={}, Purpose=city_creation", firebaseUid);
        long startTime = System.currentTimeMillis();

        ResponseEntity<CityDto> response = super.create(dto);

        long duration = System.currentTimeMillis() - startTime;
        log.info("GDPR: DataCreated=city.all_fields, FirebaseUID={}, Duration={}ms, Purpose=create",
                firebaseUid, duration);

        return response;
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    @DeleteMapping("/{ids}")
    public ResponseEntity<Void> delete(@PathVariable List<Long> ids) {
        String firebaseUid = getAuthenticatedUserUid();

        log.info("GDPR: Operation=deleteCity, FirebaseUID={}, Purpose=city_creation", firebaseUid);
        long startTime = System.currentTimeMillis();

        ResponseEntity<Void> response = super.delete(ids);

        long duration = System.currentTimeMillis() - startTime;
        log.info("GDPR: DataDeleted=city.all_fields, FirebaseUID={}, Duration={}ms, Purpose=delete",
                firebaseUid, duration);

        return response;
    }

    @Override
    protected BaseService<City, Long, CityDto> getService() {
        return cityService;
    }

    // ===== HELPER METHODS =====

    /**
     * Gets the authenticated user UID from the security context.
     *
     * @return The Firebase UID of the authenticated user
     * @throws AuthenticationTranslatableException if authentication context is missing
     */
    private String getAuthenticatedUserUid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            log.error("Authentication not available in security context");
            throw new AuthenticationTranslatableException("error.auth.not_authenticated");
        }
        return auth.getPrincipal().toString();
    }
}
