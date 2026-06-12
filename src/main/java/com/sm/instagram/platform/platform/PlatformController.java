package com.sm.instagram.platform.platform;

import com.sm.instagram.platform.common.base.BaseController;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the {@link Platform} reference catalogue.
 *
 * <p>Endpoints are admin-gated by default ({@code @PreAuthorize} at the class
 * level); the read endpoints relax that to {@code isAuthenticated()} so any
 * signed-in user can resolve a platform by id or list the catalogue. Delete
 * is intentionally disabled — platforms are reference data and may only be
 * removed by a paired Liquibase migration.
 *
 * <p>Every endpoint emits a GDPR audit log line with the caller's Firebase
 * UID and the operation semantics; production logs are scraped against this
 * format, so changes to the message structure are coordinated with the
 * observability pipeline.
 */
@Slf4j
@RestController
@RequestMapping("platform")
@PreAuthorize("hasAuthority('ADMIN')")
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
public class PlatformController extends BaseController<Platform, Long, PlatformDto, PlatformDto> {

    private static final long MAX_PAGE_SIZE = 1000L;
    private static final List<String> PAGEABLE_QUERY_KEYS = List.of("page", "size", "sort", "direction");

    private final PlatformService platformService;

    protected PlatformController(PlatformService platformService) {
        super(Platform.class);
        this.platformService = platformService;
    }

    /**
     * Resolve a single platform by primary key. Validates the id is a
     * positive long before delegating; logs duration and (at debug) the
     * resolved name + active flag.
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<PlatformDto> getById(@PathVariable Long id) {
        ensurePositiveId(id);

        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=getPlatform, FirebaseUID={}, PlatformID={}, Purpose=data_retrieval",
                firebaseUid, id);
        log.info("Retrieving social media platform with ID: {}", id);

        long startedAt = System.currentTimeMillis();
        ResponseEntity<PlatformDto> response = super.getById(id);
        long elapsedMs = System.currentTimeMillis() - startedAt;
        log.info("Successfully retrieved platform with ID: {} in {}ms", id, elapsedMs);

        PlatformDto body = response.getBody();
        if (body != null) {
            log.debug("Retrieved platform: {} (active: {})", body.getName(), body.getActive());
        }
        return response;
    }

    /**
     * Page over platforms with arbitrary filter criteria. The page-size
     * ceiling is {@value #MAX_PAGE_SIZE} entries; non-negative page numbers
     * only. Spring's pagination query parameters are stripped from the
     * filter map before delegation so they are not interpreted as filters.
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/paged")
    public ResponseEntity<Page<PlatformDto>> findPaginated(Pageable pageable,
                                                           @RequestParam Map<String, String> filters) {
        validatePageable(pageable);

        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=listPlatforms, FirebaseUID={}, Page={}, Size={}, Filters={}, Purpose=data_browsing",
                firebaseUid, pageable.getPageNumber(), pageable.getPageSize(), filters.keySet());
        log.info("Retrieving paginated social media platforms - page: {}, size: {}, filters: {}",
                pageable.getPageNumber(), pageable.getPageSize(), filters.keySet());

        PAGEABLE_QUERY_KEYS.forEach(filters::remove);

        long startedAt = System.currentTimeMillis();
        ResponseEntity<Page<PlatformDto>> response = super.findPaginated(pageable, filters);
        long elapsedMs = System.currentTimeMillis() - startedAt;

        Page<PlatformDto> page = response.getBody();
        if (page != null) {
            log.info("Successfully retrieved {} platforms (total: {}) in {}ms",
                    page.getNumberOfElements(), page.getTotalElements(), elapsedMs);
            log.debug("Applied filters: {} | Available platforms count: {}",
                    filters, page.getTotalElements());
        }
        return response;
    }

    /**
     * Delete is intentionally disabled. Platforms are reference data managed
     * by Liquibase migrations; removing one via the API would orphan
     * partnership opportunities and social connections that point at it.
     */
    @Override
    @DeleteMapping("/{ids}")
    public ResponseEntity<Void> delete(@PathVariable List<Long> ids) {
        throw new ValidationTranslatableException(
                "error.business.operation_not_allowed",
                "Platform deletion is not allowed via the API");
    }

    @Override
    protected BaseService<Platform, Long, PlatformDto> getService() {
        return platformService;
    }

    // ----- private helpers -----

    private static void ensurePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new ValidationTranslatableException("error.validation.invalid_id", "id");
        }
    }

    private static void validatePageable(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new ValidationTranslatableException(
                    "error.validation.list_too_large",
                    String.valueOf(MAX_PAGE_SIZE));
        }
        if (pageable.getPageNumber() < 0) {
            throw new ValidationTranslatableException(
                    "error.validation.invalid_parameter",
                    "page number must be non-negative");
        }
    }

    private String getAuthenticatedAdminUid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            log.error("Authentication not available in security context");
            throw new AuthenticationTranslatableException("error.auth.not_authenticated");
        }
        return auth.getPrincipal().toString();
    }

}
