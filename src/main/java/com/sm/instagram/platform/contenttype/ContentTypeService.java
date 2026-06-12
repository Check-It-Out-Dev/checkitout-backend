package com.sm.instagram.platform.contenttype;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Service tier for {@link ContentType} reference data.
 *
 * <p>The three overrides below extend the standard {@link BaseService}
 * surface with explicit {@code @Transactional} boundaries — content types
 * are pulled by paged queries that historically tripped over
 * {@code LazyInitializationException} when the projection happened outside
 * a session.
 *
 * <p>Mutating operations record a GDPR-flavoured audit log keyed by the
 * caller's Firebase UID; if the security context is empty (e.g. boot-time
 * seeding) the principal is recorded as {@code SYSTEM}.
 */
@Slf4j
@Service
public class ContentTypeService extends BaseService<ContentType, Long, ContentTypeDto> {

    private static final String SYSTEM_PRINCIPAL = "SYSTEM";

    protected ContentTypeService(SpecificationBuilder<ContentType> specificationBuilder,
                                 BaseRepository<ContentType, Long> repository,
                                 ModelMapper modelMapper,
                                 RepositoryResolver repositoryResolver,
                                 ApplicationContext applicationContext) {
        super(applicationContext, specificationBuilder, repository, modelMapper, repositoryResolver);
    }

    /**
     * Map a {@link ContentType} entity to {@link ContentTypeDtoOut}.
     * Returns {@code null} for a {@code null} entity so callers can chain
     * through optional lookups. Must run inside a transaction so any lazy
     * associations resolve before the proxy goes cold.
     */
    @Override
    @Transactional(readOnly = true)
    public <DTOOUT> DTOOUT toDto(ContentType entity) {
        if (entity == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        DTOOUT result = (DTOOUT) modelMapper.map(entity, ContentTypeDtoOut.class);
        return result;
    }

    /**
     * Persist a new {@link ContentType} from its inbound DTO and return the
     * stored representation. Records a GDPR audit line on entry and on
     * success — both keyed by the caller's Firebase UID — so the operation
     * is traceable end-to-end.
     */
    @Override
    @Transactional
    public <DTOOUT> DTOOUT createFromDtoAsDto(ContentTypeDto dto) {
        String firebaseUid = currentPrincipal();
        log.info("GDPR: Operation=createContentType, FirebaseUID={}, ContentTypeName={}, Purpose=reference_data_creation",
                firebaseUid, dto.getName());

        ContentType entity = modelMapper.map(dto, ContentType.class);
        ContentType saved = save(entity);

        log.info("GDPR: DataCreated=content_type, FirebaseUID={}, ContentTypeID={}, Purpose=reference_data",
                firebaseUid, saved.getId());
        return toDto(saved);
    }

    /**
     * Page over content types with arbitrary filter criteria, projecting
     * each row into {@link ContentTypeDtoOut} inside the same transaction
     * so lazy fields materialize before serialization. The hand-rolled
     * mapping keeps the projection scalar-only — no ModelMapper round-trip.
     */
    @Override
    @Transactional(readOnly = true)
    public <DTOOUT> Page<DTOOUT> getDataPagedAndFilteredAsDtos(Pageable pageable,
                                                               Map<String, String> filters) {
        String firebaseUid = currentPrincipal();
        log.info("GDPR: Operation=getContentTypesPaged, FirebaseUID={}, Purpose=reference_data_retrieval",
                firebaseUid);

        Page<ContentType> page = getDataPagedAndFiltered(pageable, filters);
        Page<ContentTypeDtoOut> dtoPage = page.map(ContentTypeService::projectScalar);

        log.info("GDPR: DataAccessed=content_types, FirebaseUID={}, RecordCount={}, Purpose=reference_data_listing",
                firebaseUid, dtoPage.getTotalElements());

        @SuppressWarnings("unchecked")
        Page<DTOOUT> result = (Page<DTOOUT>) dtoPage;
        return result;
    }

    private static ContentTypeDtoOut projectScalar(ContentType entity) {
        ContentTypeDtoOut dto = new ContentTypeDtoOut();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setOriginalName(entity.getName());
        return dto;
    }

    private static String currentPrincipal() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return auth.getPrincipal().toString();
            }
        } catch (RuntimeException ignored) {
            // No security context — fall through to the system principal.
        }
        return SYSTEM_PRINCIPAL;
    }
}
