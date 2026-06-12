package com.sm.instagram.platform.integration.service.faq;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.support.faq.dtos.FaqDtoIn;
import com.sm.instagram.platform.support.faq.models.Faq;
import com.sm.instagram.platform.support.faq.models.FaqCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for FaqService.
 * Tests cover: retrieval, search, creation, update, and soft-delete operations.
 */
@DisplayName("FaqService Integration Tests")
class FaqService_IntegrationTest extends FaqServiceIntegrationTestBase {

    private FaqCategory testCategory;

    @BeforeEach
    void setUpAuthAndCategory() {
        authenticateAs(testAdmin);
        testCategory = createCategory("Test Category", "Category for testing FAQs");
    }

    @Nested
    @DisplayName("getAllActiveFaqs()")
    class GetAllActiveFaqs {

        @Test
        @DisplayName("returns empty list when no FAQs exist")
        void returnsEmptyListWhenNoFaqsExist() {
            List<Faq> result = faqService.getAllActiveFaqs();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns only active FAQs")
        void returnsOnlyActiveFaqs() {
            createFaq(testCategory, "Active Question 1?", "Answer 1");
            createFaq(testCategory, "Active Question 2?", "Answer 2");
            createInactiveFaq(testCategory, "Inactive Question?", "Should not appear");

            List<Faq> result = faqService.getAllActiveFaqs();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Faq::getQuestion)
                    .containsExactlyInAnyOrder("Active Question 1?", "Active Question 2?");
        }

        @Test
        @DisplayName("returns FAQs ordered by displayOrder ascending")
        void returnsFaqsOrderedByDisplayOrder() {
            createFaq(testCategory, "Third?", "Answer", 3);
            createFaq(testCategory, "First?", "Answer", 1);
            createFaq(testCategory, "Second?", "Answer", 2);

            List<Faq> result = faqService.getAllActiveFaqs();

            assertThat(result).hasSize(3);
            assertThat(result).extracting(Faq::getQuestion)
                    .containsExactly("First?", "Second?", "Third?");
        }
    }

    @Nested
    @DisplayName("getFaqsByCategory()")
    class GetFaqsByCategory {

        @Test
        @DisplayName("returns FAQs for specified category")
        void returnsFaqsForSpecifiedCategory() {
            FaqCategory otherCategory = createCategory("Other Category", "Another category");

            createFaq(testCategory, "Category 1 Q1?", "Answer");
            createFaq(testCategory, "Category 1 Q2?", "Answer");
            createFaq(otherCategory, "Category 2 Q1?", "Answer");

            List<Faq> result = faqService.getFaqsByCategory(testCategory.getId());

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Faq::getQuestion)
                    .containsExactlyInAnyOrder("Category 1 Q1?", "Category 1 Q2?");
        }

        @Test
        @DisplayName("returns empty list for category with no FAQs")
        void returnsEmptyListForCategoryWithNoFaqs() {
            FaqCategory emptyCategory = createCategory("Empty Category", "No FAQs here");

            List<Faq> result = faqService.getFaqsByCategory(emptyCategory.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for non-existent category")
        void throwsResourceNotFoundForNonExistentCategory() {
            Long nonExistentId = 99999L;

            assertThatThrownBy(() -> faqService.getFaqsByCategory(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("excludes inactive FAQs from category results")
        void excludesInactiveFaqsFromCategoryResults() {
            createFaq(testCategory, "Active?", "Answer");
            createInactiveFaq(testCategory, "Inactive?", "Should not appear");

            List<Faq> result = faqService.getFaqsByCategory(testCategory.getId());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getQuestion()).isEqualTo("Active?");
        }

        @Test
        @DisplayName("returns FAQs ordered by displayOrder within category")
        void returnsFaqsOrderedByDisplayOrderWithinCategory() {
            createFaq(testCategory, "Third?", "Answer", 3);
            createFaq(testCategory, "First?", "Answer", 1);
            createFaq(testCategory, "Second?", "Answer", 2);

            List<Faq> result = faqService.getFaqsByCategory(testCategory.getId());

            assertThat(result).extracting(Faq::getQuestion)
                    .containsExactly("First?", "Second?", "Third?");
        }
    }

    @Nested
    @DisplayName("searchFaqs()")
    class SearchFaqs {

        @Test
        @DisplayName("finds FAQs matching query in question")
        void findsFaqsMatchingQueryInQuestion() {
            createFaq(testCategory, "How do I reset my password?", "Go to settings...");
            createFaq(testCategory, "How do I change my email?", "Navigate to profile...");
            createFaq(testCategory, "What are the shipping costs?", "Shipping is free...");

            List<Faq> result = faqService.searchFaqs("password");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getQuestion()).contains("password");
        }

        @Test
        @DisplayName("finds FAQs matching query in answer")
        void findsFaqsMatchingQueryInAnswer() {
            createFaq(testCategory, "How do I get support?", "Contact us via email at support@example.com");
            createFaq(testCategory, "What is the return policy?", "You have 30 days to return");

            List<Faq> result = faqService.searchFaqs("email");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAnswer()).contains("email");
        }

        @Test
        @DisplayName("search is case-insensitive")
        void searchIsCaseInsensitive() {
            createFaq(testCategory, "How do I reset my PASSWORD?", "Answer here");

            List<Faq> upperResult = faqService.searchFaqs("PASSWORD");
            List<Faq> lowerResult = faqService.searchFaqs("password");
            List<Faq> mixedResult = faqService.searchFaqs("PaSsWoRd");

            assertThat(upperResult).hasSize(1);
            assertThat(lowerResult).hasSize(1);
            assertThat(mixedResult).hasSize(1);
        }

        @Test
        @DisplayName("returns empty list when no match found")
        void returnsEmptyListWhenNoMatchFound() {
            createFaq(testCategory, "Question about shipping?", "Shipping info here");

            List<Faq> result = faqService.searchFaqs("nonexistent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("excludes inactive FAQs from search results")
        void excludesInactiveFaqsFromSearchResults() {
            createFaq(testCategory, "Active password question?", "Active answer");
            createInactiveFaq(testCategory, "Inactive password question?", "Inactive answer");

            List<Faq> result = faqService.searchFaqs("password");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isActive()).isTrue();
        }

        @Test
        @DisplayName("returns all active FAQs for null query")
        void returnsAllActiveFaqsForNullQuery() {
            createFaq(testCategory, "Question 1?", "Answer 1");
            createFaq(testCategory, "Question 2?", "Answer 2");

            List<Faq> result = faqService.searchFaqs(null);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns all active FAQs for empty query")
        void returnsAllActiveFaqsForEmptyQuery() {
            createFaq(testCategory, "Question 1?", "Answer 1");
            createFaq(testCategory, "Question 2?", "Answer 2");

            List<Faq> result = faqService.searchFaqs("   ");

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("createFaq()")
    class CreateFaq {

        @Test
        @DisplayName("creates FAQ with all fields")
        void createsFaqWithAllFields() {
            FaqDtoIn dto = createFaqDtoIn(testCategory.getId(), "New Question?", "New Answer", 5);

            Faq result = faqService.createFaq(dto);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getQuestion()).isEqualTo("New Question?");
            assertThat(result.getAnswer()).isEqualTo("New Answer");
            assertThat(result.getDisplayOrder()).isEqualTo(5);
            assertThat(result.isActive()).isTrue();
            assertThat(result.getCategory().getId()).isEqualTo(testCategory.getId());
        }

        @Test
        @DisplayName("auto-assigns display order when set to 0")
        void autoAssignsDisplayOrderWhenZero() {
            createFaq(testCategory, "Existing 1?", "Answer", 1);
            createFaq(testCategory, "Existing 2?", "Answer", 2);

            FaqDtoIn dto = createFaqDtoIn(testCategory.getId(), "New Question?", "Answer");
            dto.setDisplayOrder(0);

            Faq result = faqService.createFaq(dto);

            assertThat(result.getDisplayOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for invalid category ID")
        void throwsResourceNotFoundForInvalidCategoryId() {
            FaqDtoIn dto = createFaqDtoIn(99999L, "Question?", "Answer");

            assertThatThrownBy(() -> faqService.createFaq(dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("creates FAQ with active status true by default")
        void createsFaqWithActiveStatusTrueByDefault() {
            FaqDtoIn dto = createFaqDtoIn(testCategory.getId(), "Question?", "Answer");

            Faq result = faqService.createFaq(dto);

            assertThat(result.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("updates all FAQ fields")
        void updatesAllFaqFields() {
            Faq original = createFaq(testCategory, "Original Question?", "Original Answer", 1);

            FaqDtoIn updateDto = new FaqDtoIn();
            updateDto.setQuestion("Updated Question?");
            updateDto.setAnswer("Updated Answer");
            updateDto.setDisplayOrder(10);
            updateDto.setActive(false);
            updateDto.setCategoryId(testCategory.getId());

            Faq result = faqService.update(original.getId(), updateDto);

            assertThat(result.getQuestion()).isEqualTo("Updated Question?");
            assertThat(result.getAnswer()).isEqualTo("Updated Answer");
            assertThat(result.getDisplayOrder()).isEqualTo(10);
            assertThat(result.isActive()).isFalse();
        }

        @Test
        @DisplayName("can move FAQ to different category")
        void canMoveFaqToDifferentCategory() {
            FaqCategory newCategory = createCategory("New Category", "Description");
            Faq faq = createFaq(testCategory, "Question?", "Answer");

            FaqDtoIn updateDto = createFaqDtoIn(newCategory.getId(), "Question?", "Answer");

            Faq result = faqService.update(faq.getId(), updateDto);

            assertThat(result.getCategory().getId()).isEqualTo(newCategory.getId());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for non-existent FAQ")
        void throwsResourceNotFoundForNonExistentFaq() {
            FaqDtoIn updateDto = createFaqDtoIn(testCategory.getId(), "Question?", "Answer");

            assertThatThrownBy(() -> faqService.update(99999L, updateDto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for invalid new category")
        void throwsResourceNotFoundForInvalidNewCategory() {
            Faq faq = createFaq(testCategory, "Question?", "Answer");
            FaqDtoIn updateDto = createFaqDtoIn(99999L, "Question?", "Answer");

            assertThatThrownBy(() -> faqService.update(faq.getId(), updateDto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("softDelete()")
    class SoftDelete {

        @Test
        @DisplayName("sets FAQ to inactive")
        void setsFaqToInactive() {
            Faq faq = createFaq(testCategory, "To Delete?", "Will be soft deleted");
            assertThat(faq.isActive()).isTrue();

            faqService.softDelete(faq.getId());

            Faq deleted = faqRepository.findById(faq.getId()).orElseThrow();
            assertThat(deleted.isActive()).isFalse();
        }

        @Test
        @DisplayName("soft-deleted FAQ not returned by getAllActiveFaqs")
        void softDeletedFaqNotReturnedByGetAllActive() {
            Faq faq = createFaq(testCategory, "To Delete?", "Answer");

            faqService.softDelete(faq.getId());

            List<Faq> activeFaqs = faqService.getAllActiveFaqs();
            assertThat(activeFaqs).extracting(Faq::getId)
                    .doesNotContain(faq.getId());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for non-existent FAQ")
        void throwsResourceNotFoundForNonExistentFaq() {
            Long nonExistentId = 99999L;

            assertThatThrownBy(() -> faqService.softDelete(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateDisplayOrder()")
    class UpdateDisplayOrder {

        @Test
        @DisplayName("updates display order successfully")
        void updatesDisplayOrderSuccessfully() {
            Faq faq = createFaq(testCategory, "Question?", "Answer", 1);

            Faq result = faqService.updateDisplayOrder(faq.getId(), 10);

            assertThat(result.getDisplayOrder()).isEqualTo(10);
        }

        @Test
        @DisplayName("can set display order to zero")
        void canSetDisplayOrderToZero() {
            Faq faq = createFaq(testCategory, "Question?", "Answer", 5);

            Faq result = faqService.updateDisplayOrder(faq.getId(), 0);

            assertThat(result.getDisplayOrder()).isEqualTo(0);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for non-existent FAQ")
        void throwsResourceNotFoundForNonExistentFaq() {
            Long nonExistentId = 99999L;

            assertThatThrownBy(() -> faqService.updateDisplayOrder(nonExistentId, 5))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
