package com.sm.instagram.platform.integration.service.faq;

import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.support.faq.dtos.FaqCategoryDtoIn;
import com.sm.instagram.platform.support.faq.dtos.FaqDtoIn;
import com.sm.instagram.platform.support.faq.models.Faq;
import com.sm.instagram.platform.support.faq.models.FaqCategory;
import com.sm.instagram.platform.support.faq.repositories.FaqCategoryRepository;
import com.sm.instagram.platform.support.faq.repositories.FaqRepository;
import com.sm.instagram.platform.support.faq.services.FaqCategoryService;
import com.sm.instagram.platform.support.faq.services.FaqService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base class for FAQ integration tests.
 * Provides common fixtures and helper methods for testing FAQ functionality.
 */
public abstract class FaqServiceIntegrationTestBase extends BaseServiceIntegrationTest {

    @Autowired
    protected FaqService faqService;

    @Autowired
    protected FaqCategoryService faqCategoryService;

    @Autowired
    protected FaqRepository faqRepository;

    @Autowired
    protected FaqCategoryRepository faqCategoryRepository;

    /**
     * Creates and saves a FAQ category.
     *
     * @param name The category name
     * @param description The category description
     * @return The created FaqCategory entity
     */
    protected FaqCategory createCategory(String name, String description) {
        FaqCategory category = new FaqCategory();
        category.setName(name);
        category.setDescription(description);
        category.setActive(true);
        category.setDisplayOrder(0);
        return faqCategoryRepository.save(category);
    }

    /**
     * Creates and saves a FAQ category with a specific display order.
     *
     * @param name The category name
     * @param description The category description
     * @param displayOrder The display order
     * @return The created FaqCategory entity
     */
    protected FaqCategory createCategory(String name, String description, int displayOrder) {
        FaqCategory category = new FaqCategory();
        category.setName(name);
        category.setDescription(description);
        category.setActive(true);
        category.setDisplayOrder(displayOrder);
        return faqCategoryRepository.save(category);
    }

    /**
     * Creates and saves a FAQ.
     *
     * @param category The category the FAQ belongs to
     * @param question The question text
     * @param answer The answer text
     * @return The created Faq entity
     */
    protected Faq createFaq(FaqCategory category, String question, String answer) {
        Faq faq = new Faq();
        faq.setCategory(category);
        faq.setQuestion(question);
        faq.setAnswer(answer);
        faq.setActive(true);
        faq.setDisplayOrder(0);
        return faqRepository.save(faq);
    }

    /**
     * Creates and saves a FAQ with a specific display order.
     *
     * @param category The category the FAQ belongs to
     * @param question The question text
     * @param answer The answer text
     * @param displayOrder The display order
     * @return The created Faq entity
     */
    protected Faq createFaq(FaqCategory category, String question, String answer, int displayOrder) {
        Faq faq = new Faq();
        faq.setCategory(category);
        faq.setQuestion(question);
        faq.setAnswer(answer);
        faq.setActive(true);
        faq.setDisplayOrder(displayOrder);
        return faqRepository.save(faq);
    }

    /**
     * Creates an inactive (soft-deleted) FAQ.
     *
     * @param category The category the FAQ belongs to
     * @param question The question text
     * @param answer The answer text
     * @return The created inactive Faq entity
     */
    protected Faq createInactiveFaq(FaqCategory category, String question, String answer) {
        Faq faq = new Faq();
        faq.setCategory(category);
        faq.setQuestion(question);
        faq.setAnswer(answer);
        faq.setActive(false);
        faq.setDisplayOrder(0);
        return faqRepository.save(faq);
    }

    /**
     * Creates an inactive (soft-deleted) category.
     *
     * @param name The category name
     * @param description The category description
     * @return The created inactive FaqCategory entity
     */
    protected FaqCategory createInactiveCategory(String name, String description) {
        FaqCategory category = new FaqCategory();
        category.setName(name);
        category.setDescription(description);
        category.setActive(false);
        category.setDisplayOrder(0);
        return faqCategoryRepository.save(category);
    }

    /**
     * Creates a FaqCategoryDtoIn for testing category creation.
     *
     * @param name The category name
     * @param description The category description
     * @return A populated FaqCategoryDtoIn
     */
    protected FaqCategoryDtoIn createCategoryDtoIn(String name, String description) {
        FaqCategoryDtoIn dto = new FaqCategoryDtoIn();
        dto.setName(name);
        dto.setDescription(description);
        dto.setActive(true);
        dto.setDisplayOrder(0);
        return dto;
    }

    /**
     * Creates a FaqDtoIn for testing FAQ creation.
     *
     * @param categoryId The category ID
     * @param question The question text
     * @param answer The answer text
     * @return A populated FaqDtoIn
     */
    protected FaqDtoIn createFaqDtoIn(Long categoryId, String question, String answer) {
        FaqDtoIn dto = new FaqDtoIn();
        dto.setCategoryId(categoryId);
        dto.setQuestion(question);
        dto.setAnswer(answer);
        dto.setActive(true);
        dto.setDisplayOrder(0);
        return dto;
    }

    /**
     * Creates a FaqDtoIn with specific display order.
     *
     * @param categoryId The category ID
     * @param question The question text
     * @param answer The answer text
     * @param displayOrder The display order
     * @return A populated FaqDtoIn
     */
    protected FaqDtoIn createFaqDtoIn(Long categoryId, String question, String answer, int displayOrder) {
        FaqDtoIn dto = new FaqDtoIn();
        dto.setCategoryId(categoryId);
        dto.setQuestion(question);
        dto.setAnswer(answer);
        dto.setActive(true);
        dto.setDisplayOrder(displayOrder);
        return dto;
    }
}
