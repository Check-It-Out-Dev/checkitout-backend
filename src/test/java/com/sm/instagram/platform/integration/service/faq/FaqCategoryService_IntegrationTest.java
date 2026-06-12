package com.sm.instagram.platform.integration.service.faq;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.support.faq.dtos.FaqCategoryDtoIn;
import com.sm.instagram.platform.support.faq.models.FaqCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for FaqCategoryService.
 * Tests cover: retrieval, creation, soft-delete, and display order operations.
 */
@DisplayName("FaqCategoryService Integration Tests")
class FaqCategoryService_IntegrationTest extends FaqServiceIntegrationTestBase {

    @BeforeEach
    void setUpAuth() {
        authenticateAs(testAdmin);
    }

    @Nested
    @DisplayName("getAllActiveCategories()")
    class GetAllActiveCategories {

        @Test
        @DisplayName("returns empty list when no categories exist")
        void returnsEmptyListWhenNoCategoriesExist() {
            List<FaqCategory> result = faqCategoryService.getAllActiveCategories();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns only active categories")
        void returnsOnlyActiveCategories() {
            createCategory("Active Category 1", "Description 1");
            createCategory("Active Category 2", "Description 2");
            createInactiveCategory("Inactive Category", "Should not appear");

            List<FaqCategory> result = faqCategoryService.getAllActiveCategories();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(FaqCategory::getName)
                    .containsExactlyInAnyOrder("Active Category 1", "Active Category 2");
        }

        @Test
        @DisplayName("returns categories ordered by displayOrder ascending")
        void returnsCategoriesOrderedByDisplayOrder() {
            createCategory("Third", "Desc", 3);
            createCategory("First", "Desc", 1);
            createCategory("Second", "Desc", 2);

            List<FaqCategory> result = faqCategoryService.getAllActiveCategories();

            assertThat(result).hasSize(3);
            assertThat(result).extracting(FaqCategory::getName)
                    .containsExactly("First", "Second", "Third");
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("returns category when exists")
        void returnsCategoryWhenExists() {
            FaqCategory created = createCategory("Test Category", "Test Description");

            FaqCategory result = faqCategoryService.findById(created.getId());

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(created.getId());
            assertThat(result.getName()).isEqualTo("Test Category");
            assertThat(result.getDescription()).isEqualTo("Test Description");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for non-existent ID")
        void throwsResourceNotFoundForNonExistentId() {
            Long nonExistentId = 99999L;

            assertThatThrownBy(() -> faqCategoryService.findById(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("returns inactive category when accessed by ID")
        void returnsInactiveCategoryWhenAccessedById() {
            // Note: findById doesn't filter by active status, it returns any category
            FaqCategory inactive = createInactiveCategory("Inactive", "Description");

            FaqCategory result = faqCategoryService.findById(inactive.getId());

            assertThat(result).isNotNull();
            assertThat(result.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("createCategory()")
    class CreateCategory {

        @Test
        @DisplayName("creates category with all fields")
        void createsCategoryWithAllFields() {
            FaqCategoryDtoIn dto = createCategoryDtoIn("New Category", "Category Description");
            dto.setDisplayOrder(5);

            FaqCategory result = faqCategoryService.createCategory(dto);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("New Category");
            assertThat(result.getDescription()).isEqualTo("Category Description");
            assertThat(result.getDisplayOrder()).isEqualTo(5);
            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("auto-assigns display order when set to 0")
        void autoAssignsDisplayOrderWhenZero() {
            // Create some existing categories
            createCategory("Existing 1", "Desc", 1);
            createCategory("Existing 2", "Desc", 2);

            FaqCategoryDtoIn dto = createCategoryDtoIn("New Category", "Description");
            dto.setDisplayOrder(0); // Should get auto-assigned

            FaqCategory result = faqCategoryService.createCategory(dto);

            // Display order should be count of all categories + 1
            assertThat(result.getDisplayOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("creates category with active status true by default")
        void createsCategoryWithActiveStatusTrueByDefault() {
            FaqCategoryDtoIn dto = new FaqCategoryDtoIn();
            dto.setName("Category Name");
            dto.setDescription("Description");

            FaqCategory result = faqCategoryService.createCategory(dto);

            assertThat(result.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("softDelete()")
    class SoftDelete {

        @Test
        @DisplayName("sets category to inactive")
        void setsCategoryToInactive() {
            FaqCategory category = createCategory("To Delete", "Will be soft deleted");
            assertThat(category.isActive()).isTrue();

            faqCategoryService.softDelete(category.getId());

            FaqCategory deleted = faqCategoryRepository.findById(category.getId()).orElseThrow();
            assertThat(deleted.isActive()).isFalse();
        }

        @Test
        @DisplayName("soft-deleted category not returned by getAllActiveCategories")
        void softDeletedCategoryNotReturnedByGetAllActive() {
            FaqCategory category = createCategory("To Delete", "Will be soft deleted");

            faqCategoryService.softDelete(category.getId());

            List<FaqCategory> activeCategories = faqCategoryService.getAllActiveCategories();
            assertThat(activeCategories).extracting(FaqCategory::getId)
                    .doesNotContain(category.getId());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for non-existent category")
        void throwsResourceNotFoundForNonExistentCategory() {
            Long nonExistentId = 99999L;

            assertThatThrownBy(() -> faqCategoryService.softDelete(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateDisplayOrder()")
    class UpdateDisplayOrder {

        @Test
        @DisplayName("updates display order successfully")
        void updatesDisplayOrderSuccessfully() {
            FaqCategory category = createCategory("Test Category", "Description", 1);

            FaqCategory result = faqCategoryService.updateDisplayOrder(category.getId(), 10);

            assertThat(result.getDisplayOrder()).isEqualTo(10);
        }

        @Test
        @DisplayName("can set display order to zero")
        void canSetDisplayOrderToZero() {
            FaqCategory category = createCategory("Test Category", "Description", 5);

            FaqCategory result = faqCategoryService.updateDisplayOrder(category.getId(), 0);

            assertThat(result.getDisplayOrder()).isEqualTo(0);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for non-existent category")
        void throwsResourceNotFoundForNonExistentCategory() {
            Long nonExistentId = 99999L;

            assertThatThrownBy(() -> faqCategoryService.updateDisplayOrder(nonExistentId, 5))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
