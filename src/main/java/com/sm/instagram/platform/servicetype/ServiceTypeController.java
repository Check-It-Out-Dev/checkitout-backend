package com.sm.instagram.platform.servicetype;

import com.sm.instagram.platform.common.base.BaseController;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.common.translation.TranslationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

/**
 * REST controller for managing service types.
 * Provides endpoints for retrieving service type data with localized names and descriptions.
 * Requires admin authentication for all operations.
 */
@Slf4j
@RestController
@RequestMapping("service-type")
@PreAuthorize("hasAuthority('ADMIN')")
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
public class ServiceTypeController extends BaseController<ServiceType, Long, ServiceTypeDto, ServiceTypeDtoOut> {

    private final ServiceTypeService serviceTypeService;
    private final TranslationService translationService;
    private final HttpServletRequest request;

    protected ServiceTypeController(ServiceTypeService serviceTypeService,
                                    TranslationService translationService, HttpServletRequest request) {
        super(ServiceType.class);
        this.serviceTypeService = serviceTypeService;
        this.translationService = translationService;
        this.request = request;
    }

    /**
     * Get locale from Accept-Language header
     *
     * @return Locale parsed from Accept-Language header or default Polish locale
     */
    private Locale getLocaleFromRequest() {
        String acceptLanguageHeader = request.getHeader("Accept-Language");
        if (acceptLanguageHeader != null && !acceptLanguageHeader.isEmpty()) {
            // Parse the first language from Accept-Language header
            String language = acceptLanguageHeader.split(",")[0].split(";")[0].trim();
            return Locale.forLanguageTag(language);
        }
        return Locale.forLanguageTag("pl"); // Default to Polish
    }

    /**
     * Extracts description with translation logic.
     * This method replaces the nested ternary operation for better readability.
     *
     * @param entity The service type entity
     * @param locale The target locale for translation
     * @return Translated description or original description if translation not available
     */
    private String extractDescription(ServiceType entity, Locale locale) {
        if (entity.getDescription() == null) {
            return null;
        }

        String translatedDescription = translationService.getServiceTypeDescription(entity.getName(), locale);
        return translatedDescription != null ? translatedDescription : entity.getDescription();
    }

    /**
     * Get service type by ID with localized name and description.
     *
     * @param id The service type ID
     * @return Service type data with translated fields
     * @throws ValidationTranslatableException if ID is invalid
     * @throws ResourceNotFoundException       if authentication missing or service type not found
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<ServiceTypeDtoOut> getById(@PathVariable Long id) {
        // Validate ID parameter
        if (id == null || id <= 0) {
            throw new ValidationTranslatableException("error.validation.invalid_id", "id");
        }

        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=getServiceType, FirebaseUID={}, ServiceTypeID={}, Purpose=service_type_retrieval", firebaseUid, id);
        long startTime = System.currentTimeMillis();

        Locale locale = getLocaleFromRequest();
        ServiceType entity = serviceTypeService.findById(id);

        // Extract nested ternary operation into independent statement
        String description = extractDescription(entity, locale);

        // Convert to translation DTO with manual mapping
        ServiceTypeDtoOut dto = ServiceTypeDtoOut.builder()
                .id(entity.getId())
                .originalName(entity.getName())
                .name(translationService.translateServiceType(entity.getName(), locale))
                .originalDescription(entity.getDescription())
                .description(description)
                .originalCategory(entity.getCategory())
                .category(entity.getCategory() != null ?
                        translationService.translateServiceCategory(entity.getCategory(), locale) : null)
                .build();

        long duration = System.currentTimeMillis() - startTime;
        log.info("GDPR: Operation=getServiceType_success, FirebaseUID={}, ServiceTypeID={}, DataAccessed=service_type.name,service_type.description,service_type.category, Duration={}ms", firebaseUid, id, duration);
        log.debug("Retrieved service type: {} - {}", dto.getName(), dto.getDescription());

        return ResponseEntity.ok(dto);
    }

    /**
     * Get paginated service types with localized names and descriptions.
     *
     * @param pageable Pagination parameters
     * @param filters  Filter parameters
     * @return Page of service types with translated fields
     * @throws ValidationTranslatableException if pagination parameters are invalid
     * @throws ResourceNotFoundException       if authentication missing
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/paged")
    public ResponseEntity<Page<ServiceTypeDtoOut>> findPaginated(Pageable pageable, @RequestParam Map<String, String> filters) {
        // Validate pagination parameters
        if (pageable.getPageSize() > 1000) {
            throw new ValidationTranslatableException("error.validation.list_too_large", "1000");
        }
        if (pageable.getPageNumber() < 0) {
            throw new ValidationTranslatableException("error.validation.invalid_parameter", "page number must be non-negative");
        }

        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=getServiceTypesPaginated, FirebaseUID={}, Page={}, Size={}, Filters={}, Purpose=service_types_listing",
                firebaseUid, pageable.getPageNumber(), pageable.getPageSize(), filters.keySet());
        long startTime = System.currentTimeMillis();

        Locale locale = getLocaleFromRequest();

        // Remove pagination parameters from filters
        filters.remove("page");
        filters.remove("size");
        filters.remove("sort");
        filters.remove("direction");

        Page<ServiceType> page = serviceTypeService.getDataPagedAndFiltered(pageable, filters);

        // Convert to translation DTOs with manual mapping
        Page<ServiceTypeDtoOut> dtoPage = page.map(entity -> {
            String translatedDescription = extractDescription(entity, locale);

            return ServiceTypeDtoOut.builder()
                    .id(entity.getId())
                    .originalName(entity.getName())
                    .name(translationService.translateServiceType(entity.getName(), locale))
                    .originalDescription(entity.getDescription())
                    .description(translatedDescription)
                    .originalCategory(entity.getCategory())
                    .category(entity.getCategory() != null ?
                            translationService.translateServiceCategory(entity.getCategory(), locale) : null)
                    .build();
        });

        long duration = System.currentTimeMillis() - startTime;
        log.info("GDPR: Operation=getServiceTypesPaginated_success, FirebaseUID={}, DataAccessed=service_types_list, RecordsReturned={}, TotalRecords={}, Duration={}ms",
                firebaseUid, dtoPage.getNumberOfElements(), dtoPage.getTotalElements(), duration);
        log.debug("Applied filters: {}", filters);

        return ResponseEntity.ok(dtoPage);
    }

    @Override
    protected BaseService<ServiceType, Long, ServiceTypeDto> getService() {
        return serviceTypeService;
    }

    // ===== HELPER METHODS =====

    /**
     * Gets the authenticated admin UID from the security context.
     *
     * @return The Firebase UID of the authenticated admin user
     * @throws ResourceNotFoundException if authentication context is missing
     */
    private String getAuthenticatedAdminUid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            log.error("Authentication not available in security context");
            throw new AuthenticationTranslatableException("error.auth.not_authenticated");
        }
        return auth.getPrincipal().toString();
    }
}
