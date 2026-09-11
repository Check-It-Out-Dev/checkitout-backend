package com.sm.instagram.platform.platform;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Service tier for {@link Platform} reference data.
 *
 * <p>Inherits the standard CRUD + paged-query surface from
 * {@link BaseService}; the three overrides below stay inside an explicit
 * transaction so that lazy {@code @ManyToMany} mappings on {@link Platform}
 * (notably {@code contentTypes}) materialize before the entity escapes the
 * persistence context as a DTO.
 */
@Service
public class PlatformService extends BaseService<Platform, Long, PlatformDto> {

    protected PlatformService(SpecificationBuilder<Platform> specificationBuilder,
                              BaseRepository<Platform, Long> repository,
                              ModelMapper modelMapper,
                              RepositoryResolver repositoryResolver,
                              ApplicationContext applicationContext) {
        super(applicationContext, specificationBuilder, repository, modelMapper, repositoryResolver);
    }

    /**
     * Map a {@link Platform} entity to its outbound DTO. Returns {@code null}
     * for a {@code null} entity so callers can chain through optional
     * lookups. Must execute inside a transaction so lazy associations resolve
     * before the proxy goes cold.
     */
    @Override
    @Transactional(readOnly = true)
    public <DTOOUT> DTOOUT toDto(Platform entity) {
        if (entity == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        DTOOUT result = (DTOOUT) modelMapper.map(entity, PlatformDto.class);
        return result;
    }

    /**
     * Persist a new {@link Platform} from its inbound DTO and return the
     * stored representation. Round-trips through {@link #toDto(Platform)} so
     * the returned object always reflects the post-save state (id assigned,
     * server-side defaults applied).
     */
    @Override
    @Transactional
    public <DTOOUT> DTOOUT createFromDtoAsDto(PlatformDto dto) {
        Platform entity = modelMapper.map(dto, Platform.class);
        Platform saved = save(entity);
        return toDto(saved);
    }

    /**
     * Page over platforms applying repository-level filters and map each row
     * through {@link #toDto(Platform)}.
     *
     * <p>This used to be a hand-rolled scalar projection that skipped
     * {@code contentTypes} to keep the query cheap, which left the listing
     * returning a body the published contract said was invalid:
     * {@code contentTypes} is {@code @NotNull}, so it is a required property
     * of {@code PlatformDto}, and the serializer drops nulls — the field was
     * simply absent. The generated clients typed it as always present, so
     * they were wrong at runtime on this route and only on this route.
     *
     * <p>ModelMapper reads only the source properties the destination
     * declares, and {@code PlatformDto} has no {@code partnershipOpportunities}
     * — so the association the projection was written to avoid is still never
     * touched. Resolving {@code contentTypes} costs one query per row, which
     * this reference table (a handful of platforms, and a page size capped by
     * the controller) can carry; the {@code readOnly} transaction is what lets
     * the lazy set resolve at all.
     */
    @Override
    @Transactional(readOnly = true)
    public <DTOOUT> Page<DTOOUT> getDataPagedAndFilteredAsDtos(Pageable pageable,
                                                               Map<String, String> filters) {
        Page<Platform> page = getDataPagedAndFiltered(pageable, filters);
        Page<PlatformDto> dtoPage = page.map(this::toDto);

        @SuppressWarnings("unchecked")
        Page<DTOOUT> result = (Page<DTOOUT>) dtoPage;
        return result;
    }
}
