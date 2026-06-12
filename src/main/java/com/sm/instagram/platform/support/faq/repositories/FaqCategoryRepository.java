package com.sm.instagram.platform.support.faq.repositories;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.support.faq.models.FaqCategory;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for FaqCategory entities.
 */
@Repository
public interface FaqCategoryRepository extends BaseRepository<FaqCategory, Long> {

    /**
     * Find all active categories ordered by display order.
     *
     * @return List of active categories
     */
    List<FaqCategory> findByActiveTrueOrderByDisplayOrderAsc();

    /**
     * Find a category by name.
     *
     * @param name The category name
     * @return The matching category if found
     */
    FaqCategory findByName(String name);
}