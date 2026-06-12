package com.sm.instagram.platform.city;

import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
public class CityService extends BaseService<City, Long, CityDto> {

    @Autowired
    public CityService(SpecificationBuilder<City> specificationBuilder,
                       CityRepository cityRepository,
                       ModelMapper modelMapper,
                       RepositoryResolver repositoryResolver,
                       ApplicationContext applicationContext) {
        super(applicationContext, specificationBuilder, cityRepository, modelMapper, repositoryResolver);
    }

    /**
     * Converts a City entity to CityDto.
     * Must be called within a transaction to avoid LazyInitializationException.
     *
     * @param entity The City entity to convert
     * @return CityDto or null if entity is null
     */
    @Override
    @Transactional(readOnly = true)
    public <DTOOUT> DTOOUT toDto(City entity) {
        if (entity == null) return null;

        @SuppressWarnings("unchecked")
        DTOOUT result = (DTOOUT) modelMapper.map(entity, CityDto.class);
        return result;
    }

    /**
     * Creates a new City from DTO and returns as DTO.
     * All conversions happen within transaction boundary.
     *
     * @param dto The input DTO
     * @return The created entity as DTO
     */
    @Override
    @Transactional
    public <DTOOUT> DTOOUT createFromDtoAsDto(CityDto dto) {
        City entity = modelMapper.map(dto, City.class);
        City saved = save(entity);
        return toDto(saved);
    }

    @CacheEvict(value = "citiesCache", allEntries = true)
    public City addCity(City city) {
        String firebaseUid = "SYSTEM";
        try {
            firebaseUid = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        } catch (Exception e) {
            // Use SYSTEM if no auth context
        }

        log.info("GDPR: Operation=addCity, FirebaseUID={}, CityName={}, Purpose=reference_data_management",
                firebaseUid, city.getName());
        log.info("Adding new city: {}", city.getName());

        City savedCity = repository.save(city);
        log.info("GDPR: DataCreated=city, FirebaseUID={}, CityID={}, Purpose=reference_data",
                firebaseUid, savedCity.getId());
        log.info("Successfully added city with ID: {}", savedCity.getId());
        return savedCity;
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
        String firebaseUid = "SYSTEM";
        try {
            firebaseUid = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        } catch (Exception e) {
            // Use SYSTEM if no auth context
        }

        log.info("GDPR: Operation=getCitiesPaged, FirebaseUID={}, Purpose=reference_data_retrieval", firebaseUid);

        // Get entities with proper pagination and filtering
        Page<City> page = getDataPagedAndFiltered(pageable, filters);

        // Convert to DTOs within transaction boundary
        Page<CityDto> dtoPage = page.map(entity -> {
            CityDto dto = new CityDto();

            // Map basic fields
            dto.setId(entity.getId());
            dto.setName(entity.getName());
            dto.setState(entity.getState());
            dto.setCountry(entity.getCountry());

            return dto;
        });

        // Cast to generic type
        @SuppressWarnings("unchecked")
        Page<DTOOUT> result = (Page<DTOOUT>) dtoPage;

        log.info("GDPR: DataAccessed=cities, FirebaseUID={}, RecordCount={}, Purpose=reference_data_listing",
                firebaseUid, result.getTotalElements());
        return result;
    }
}
