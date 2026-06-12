package com.sm.instagram.platform.contenttype;

import com.sm.instagram.platform.common.base.BaseController;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * REST controller for the {@link ContentType} reference catalogue.
 *
 * <p>Both reading endpoints translate the {@code name} field into the
 * caller's locale (parsed from the {@code Accept-Language} header, falling
 * back to Polish) while preserving the canonical name on
 * {@code originalName} so the UI can fall back gracefully when a translation
 * is missing.
 *
 * <p>Writes inherit from {@link BaseController}; this class only customises
 * the read path.
 */
@Slf4j
@RestController
@RequestMapping("content-type")
@PreAuthorize("hasAuthority('ADMIN')")
@RateLimit(profile = RateLimitProfile.RELAXED, keyType = RateLimitKeyType.USER_ENDPOINT)
public class ContentTypeController extends BaseController<ContentType, Long, ContentTypeDto, ContentTypeDtoOut> {

    private static final long MAX_PAGE_SIZE = 1000L;
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("pl");
    private static final List<String> PAGEABLE_QUERY_KEYS = List.of("page", "size", "sort", "direction");

    private final ContentTypeService contentTypeService;
    private final TranslationService translationService;
    private final HttpServletRequest request;

    protected ContentTypeController(ContentTypeService contentTypeService,
                                    TranslationService translationService,
                                    HttpServletRequest request) {
        super(ContentType.class);
        this.contentTypeService = contentTypeService;
        this.translationService = translationService;
        this.request = request;
    }

    /**
     * Resolve a single content type by primary key, returning the localized
     * representation. Validates the id is a positive long; emits a GDPR
     * audit line for the lookup with the caller's Firebase UID.
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<ContentTypeDtoOut> getById(@PathVariable Long id) {
        ensurePositiveId(id);

        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=getContentTypeById, FirebaseUID={}, ContentTypeID={}, Purpose=content_type_retrieval",
                firebaseUid, id);

        long startedAt = System.currentTimeMillis();
        Locale locale = resolveLocale();
        ContentType entity = contentTypeService.findById(id);
        ContentTypeDtoOut dto = toLocalizedDto(entity, locale);
        long elapsedMs = System.currentTimeMillis() - startedAt;

        log.info("GDPR: DataAccessed=content_type.all_fields, FirebaseUID={}, ContentTypeID={}, Duration={}ms, Purpose=display",
                firebaseUid, id, elapsedMs);
        log.debug("Retrieved content type: {}", dto.getName());
        return ResponseEntity.ok(dto);
    }

    /**
     * Page over content types and translate each entry's name. Spring's
     * pagination query parameters are stripped from the filter map before
     * delegation so they are not interpreted as filters; size is capped at
     * {@value #MAX_PAGE_SIZE}.
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/paged")
    public ResponseEntity<Page<ContentTypeDtoOut>> findPaginated(Pageable pageable,
                                                                 @RequestParam Map<String, String> filters) {
        validatePageable(pageable);

        String firebaseUid = getAuthenticatedAdminUid();
        log.info("GDPR: Operation=getContentTypesPaged, FirebaseUID={}, Page={}, Size={}, Purpose=content_type_list_retrieval",
                firebaseUid, pageable.getPageNumber(), pageable.getPageSize());

        long startedAt = System.currentTimeMillis();
        Locale locale = resolveLocale();
        PAGEABLE_QUERY_KEYS.forEach(filters::remove);

        Page<ContentType> entityPage = contentTypeService.getDataPagedAndFiltered(pageable, filters);
        Page<ContentTypeDtoOut> dtoPage = entityPage.map(entity -> toLocalizedDto(entity, locale));
        long elapsedMs = System.currentTimeMillis() - startedAt;

        log.info("GDPR: DataAccessed=content_types[], FirebaseUID={}, RecordCount={}, TotalRecords={}, Duration={}ms, Purpose=list_display",
                firebaseUid, dtoPage.getNumberOfElements(), dtoPage.getTotalElements(), elapsedMs);
        log.debug("Applied filters: {}", filters);
        return ResponseEntity.ok(dtoPage);
    }

    @Override
    protected BaseService<ContentType, Long, ContentTypeDto> getService() {
        return contentTypeService;
    }

    // ----- private helpers -----

    private ContentTypeDtoOut toLocalizedDto(ContentType entity, Locale locale) {
        return ContentTypeDtoOut.builder()
                .id(entity.getId())
                .originalName(entity.getName())
                .name(translationService.translateContentType(entity.getName(), locale))
                .build();
    }

    private Locale resolveLocale() {
        String header = request.getHeader("Accept-Language");
        if (header == null || header.isEmpty()) {
            return DEFAULT_LOCALE;
        }
        String firstTag = header.split(",")[0].split(";")[0].trim();
        return firstTag.isEmpty() ? DEFAULT_LOCALE : Locale.forLanguageTag(firstTag);
    }

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
