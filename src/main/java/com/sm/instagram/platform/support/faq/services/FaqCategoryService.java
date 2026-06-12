package com.sm.instagram.platform.support.faq.services;

import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import com.sm.instagram.platform.support.faq.dtos.FaqCategoryDtoIn;
import com.sm.instagram.platform.support.faq.dtos.FaqCategoryDtoOut;
import com.sm.instagram.platform.support.faq.models.FaqCategory;
import com.sm.instagram.platform.support.faq.repositories.FaqCategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing FAQ categories.
 */
@Slf4j
@Service
@Transactional
public class FaqCategoryService extends BaseService<FaqCategory, Long, FaqCategoryDtoIn> {
    private final FaqCategoryRepository faqCategoryRepository;

    public FaqCategoryService(
            SpecificationBuilder<FaqCategory> specificationBuilder,
            FaqCategoryRepository faqCategoryRepository,
            ModelMapper modelMapper,
            RepositoryResolver repositoryResolver,
            ApplicationContext applicationContext) {
        super(applicationContext, specificationBuilder, faqCategoryRepository, modelMapper, repositoryResolver);
        this.faqCategoryRepository = faqCategoryRepository;
    }

    /**
     * Get all active FAQ categories ordered by display order.
     *
     * @return List of active FAQ categories
     */
    public List<FaqCategory> getAllActiveCategories() {
        String firebaseUid = extractFirebaseUid();
        log.debug("GDPR: Service=getAllActiveCategories, FirebaseUID={}, Purpose=data_retrieval", firebaseUid);

        List<FaqCategory> categories = faqCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc();

        log.debug("GDPR: DatabaseQuery=findActiveCategories, FirebaseUID={}, RecordCount={}, Table=faq_categories",
                firebaseUid, categories.size());
        return categories;
    }

    /**
     * Get a category by its ID with proper error handling.
     *
     * @param id The category ID
     * @return The found category
     * @throws ResourceNotFoundException if category not found
     */
    @Override
    public FaqCategory findById(Long id) {
        String firebaseUid = extractFirebaseUid();
        log.debug("GDPR: Service=findCategoryById, FirebaseUID={}, CategoryID={}, Purpose=data_retrieval",
                firebaseUid, id);

        return faqCategoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("GDPR: Service=findCategoryById_NOT_FOUND, FirebaseUID={}, CategoryID={}", firebaseUid, id);
                    return new ResourceNotFoundException("error.business.item_not_found", "FAQ Category");
                });
    }

    /**
     * Create a new category with the next available display order.
     *
     * @param dto The category data
     * @return The created category
     */
    @Transactional
    public FaqCategory createCategory(FaqCategoryDtoIn dto) {
        String firebaseUid = extractFirebaseUid();
        log.info("GDPR: Service=createCategory, FirebaseUID={}, CategoryName={}, Purpose=data_creation",
                firebaseUid, dto.getName());

        FaqCategory category = modelMapper.map(dto, FaqCategory.class);

        // If display order is 0, set it to the next available order
        if (category.getDisplayOrder() == 0) {
            List<FaqCategory> categories = faqCategoryRepository.findAll();
            category.setDisplayOrder(categories.size() + 1);
        }

        FaqCategory saved = save(category);
        log.info("GDPR: DatabaseInsert=faq_category, FirebaseUID={}, CategoryID={}, Table=faq_categories",
                firebaseUid, saved.getId());
        return saved;
    }

    /**
     * Soft delete a category by setting it to inactive.
     *
     * @param id The category ID
     */
    @Transactional
    public void softDelete(Long id) {
        String firebaseUid = extractFirebaseUid();
        log.warn("GDPR: Service=softDeleteCategory, FirebaseUID={}, CategoryID={}, Purpose=data_archival",
                firebaseUid, id);

        FaqCategory category = findById(id);
        category.setActive(false);
        save(category);

        log.info("GDPR: DatabaseUpdate=soft_delete, FirebaseUID={}, CategoryID={}, Active=false, Table=faq_categories",
                firebaseUid, id);
    }

    /**
     * Update category display order.
     *
     * @param id       The category ID
     * @param newOrder The new display order
     * @return The updated category
     */
    @Transactional
    public FaqCategory updateDisplayOrder(Long id, int newOrder) {
        String firebaseUid = extractFirebaseUid();
        log.info("GDPR: Service=updateDisplayOrder, FirebaseUID={}, CategoryID={}, NewOrder={}, Purpose=data_update",
                firebaseUid, id, newOrder);

        FaqCategory category = findById(id);
        category.setDisplayOrder(newOrder);
        FaqCategory updated = save(category);

        log.debug("GDPR: DatabaseUpdate=display_order, FirebaseUID={}, CategoryID={}, Table=faq_categories",
                firebaseUid, id);
        return updated;
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
        // Get entities with proper pagination and filtering
        Page<FaqCategory> page = getDataPagedAndFiltered(pageable, filters);

        // Convert to DTOs within transaction boundary
        Page<FaqCategoryDtoOut> dtoPage = page.map(entity -> {
            FaqCategoryDtoOut dto = new FaqCategoryDtoOut();

            // Map basic fields
            dto.setId(entity.getId());
            dto.setName(entity.getName());
            dto.setDescription(entity.getDescription());
            dto.setDisplayOrder(entity.getDisplayOrder());
            dto.setActive(entity.isActive());
            dto.setCreatedTime(entity.getCreatedTime());
            dto.setLastUpdateTime(entity.getLastUpdateTime());
            dto.setUpdaterId(entity.getUpdaterId());

            return dto;
        });

        // Cast to generic type
        @SuppressWarnings("unchecked")
        Page<DTOOUT> result = (Page<DTOOUT>) dtoPage;
        return result;
    }

    /**
     * Convert entity to DTO within transaction.
     */
    @Override
    @Transactional(readOnly = true)
    public <DTOOUT> DTOOUT toDto(FaqCategory entity) {
        if (entity == null) return null;
        @SuppressWarnings("unchecked")
        DTOOUT result = (DTOOUT) modelMapper.map(entity, FaqCategoryDtoOut.class);
        return result;
    }

    /**
     * Convert list of entities to DTOs within transaction.
     */
    @Transactional(readOnly = true)
    public List<FaqCategoryDtoOut> toDtoList(List<FaqCategory> entities) {
        if (entities == null || entities.isEmpty()) return new ArrayList<>();
        return entities.stream()
                .map(entity -> (FaqCategoryDtoOut) toDto(entity))
                .collect(Collectors.toList());
    }

    /**
     * Get all active FAQ categories as DTOs.
     */
    @Transactional(readOnly = true)
    public List<FaqCategoryDtoOut> getAllActiveCategoriesAsDto() {
        List<FaqCategory> categories = getAllActiveCategories();
        return toDtoList(categories);
    }

    /**
     * Create a new category and return as DTO.
     */
    @Transactional
    public FaqCategoryDtoOut createCategoryAsDto(FaqCategoryDtoIn dto) {
        FaqCategory category = createCategory(dto);
        return toDto(category);
    }

    /**
     * Find category by ID and return as DTO.
     */
    @Transactional(readOnly = true)
    public FaqCategoryDtoOut findByIdAsDto(Long id) {
        FaqCategory category = findById(id);
        return toDto(category);
    }

    /**
     * Update category display order and return as DTO.
     */
    @Transactional
    public FaqCategoryDtoOut updateDisplayOrderAsDto(Long id, int newOrder) {
        FaqCategory category = updateDisplayOrder(id, newOrder);
        return toDto(category);
    }

    /**
     * Creates a new entity from DTO and returns as DTO.
     * All conversions happen within transaction boundary.
     *
     * @param dto The input DTO
     * @return The created entity as DTO
     */
    @Override
    @Transactional
    public <DTOOUT> DTOOUT createFromDtoAsDto(FaqCategoryDtoIn dto) {
        FaqCategory entity = modelMapper.map(dto, FaqCategory.class);
        FaqCategory saved = save(entity);
        return toDto(saved);
    }

    /**
     * Extract Firebase UID from security context if available.
     * Services may be called from various contexts, so we handle missing auth gracefully.
     */
    private String extractFirebaseUid() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                return auth.getPrincipal().toString();
            }
        } catch (Exception e) {
            log.debug("Could not extract Firebase UID in service layer: {}", e.getMessage());
        }
        return "SERVICE_CONTEXT";
    }
}