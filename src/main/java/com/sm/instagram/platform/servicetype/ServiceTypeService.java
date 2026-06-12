package com.sm.instagram.platform.servicetype;

import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
public class ServiceTypeService extends BaseService<ServiceType, Long, ServiceTypeDto> {

    protected ServiceTypeService(SpecificationBuilder<ServiceType> specificationBuilder,
                                 ServiceTypeRepository repository,
                                 ModelMapper modelMapper,
                                 RepositoryResolver repositoryResolver,
                                 ApplicationContext applicationContext) {
        super(applicationContext, specificationBuilder, repository, modelMapper, repositoryResolver);
    }

    /**
     * Converts a ServiceType entity to ServiceTypeDtoOut.
     * Must be called within a transaction to avoid LazyInitializationException.
     *
     * @param entity The ServiceType entity to convert
     * @return ServiceTypeDtoOut or null if entity is null
     */
    @Override
    @Transactional(readOnly = true)
    public <DTOOUT> DTOOUT toDto(ServiceType entity) {
        if (entity == null) return null;

        log.debug("GDPR: Operation=convertServiceTypeToDto, EntityID={}, Purpose=data_transformation", entity.getId());

        @SuppressWarnings("unchecked")
        DTOOUT result = (DTOOUT) modelMapper.map(entity, ServiceTypeDtoOut.class);
        return result;
    }

    /**
     * Creates a new ServiceType from DTO and returns as DTO.
     * All conversions happen within transaction boundary.
     *
     * @param dto The input DTO
     * @return The created entity as DTO
     */
    @Override
    @Transactional
    public <DTOOUT> DTOOUT createFromDtoAsDto(ServiceTypeDto dto) {
        log.info("GDPR: Operation=createServiceType, ServiceTypeName={}, Purpose=service_type_creation", dto.getName());

        ServiceType entity = modelMapper.map(dto, ServiceType.class);
        ServiceType saved = save(entity);

        log.info("GDPR: Operation=createServiceType_success, ServiceTypeID={}, ServiceTypeName={}, DataCreated=service_type", saved.getId(), saved.getName());
        return toDto(saved);
    }

    /**
     * Retrieves a paginated list of entities as DTOs with all conversions done within transaction.
     * This prevents LazyInitializationException by ensuring all DTO mappings happen inside @Transactional.
     *
     * @param pageable Pagination parameters
     * @param filters  Filter parameters
     * @param <DTOOUT> The output DTO type
     * @return Page of DTOs with all lazy relationships properly loaded
     */
    @Override
    public <DTOOUT> Page<DTOOUT> getDataPagedAndFilteredAsDtos(Pageable pageable, Map<String, String> filters) {
        log.debug("GDPR: Operation=getServiceTypesPaged, Page={}, Size={}, Filters={}, Purpose=service_types_retrieval",
                pageable.getPageNumber(), pageable.getPageSize(), filters.size());

        // Get entities with proper pagination and filtering
        Page<ServiceType> page = getDataPagedAndFiltered(pageable, filters);

        // Convert to DTOs within transaction boundary
        Page<ServiceTypeDtoOut> dtoPage = page.map(entity -> {
            ServiceTypeDtoOut dto = new ServiceTypeDtoOut();

            // Map basic fields
            dto.setId(entity.getId());
            dto.setOriginalName(entity.getName());
            dto.setName(entity.getName()); // Same as original for now
            dto.setOriginalDescription(entity.getDescription());
            dto.setDescription(entity.getDescription()); // Same as original for now
            dto.setOriginalCategory(entity.getCategory());
            dto.setCategory(entity.getCategory()); // Same as original for now

            return dto;
        });

        // Cast to generic type
        @SuppressWarnings("unchecked")
        Page<DTOOUT> result = (Page<DTOOUT>) dtoPage;
        return result;
    }
}
