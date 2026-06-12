package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.support.faq.dtos.FaqCategoryDtoIn;
import com.sm.instagram.platform.support.faq.dtos.FaqCategoryDtoOut;
import com.sm.instagram.platform.support.faq.dtos.FaqDtoIn;
import com.sm.instagram.platform.support.faq.dtos.FaqDtoOut;
import com.sm.instagram.platform.support.faq.models.Faq;
import com.sm.instagram.platform.support.faq.models.FaqCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FAQ entities and DTOs.
 * Tests entity validation, relationships, and DTO behavior.
 */
@DisplayName("FAQ Unit Tests")
class FaqUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== Faq Entity Tests ====================

    @Nested
    @DisplayName("Faq Entity")
    class FaqEntityTests {

        @Test
        @DisplayName("should create FAQ with default values")
        void shouldCreateFaqWithDefaults() {
            // Given
            Faq faq = new Faq();

            // Then
            assertThat(faq.getId()).isNull();
            assertThat(faq.getQuestion()).isNull();
            assertThat(faq.getAnswer()).isNull();
            assertThat(faq.getDisplayOrder()).isEqualTo(0);
            assertThat(faq.isActive()).isTrue();
        }

        @Test
        @DisplayName("should set all FAQ fields")
        void shouldSetAllFaqFields() {
            // Given
            Faq faq = new Faq();
            LocalDateTime now = LocalDateTime.now();

            faq.setId(1L);
            faq.setQuestion("How do I reset my password?");
            faq.setAnswer("Click on 'Forgot Password' link on the login page.");
            faq.setDisplayOrder(5);
            faq.setActive(true);
            faq.setCreatedTime(now);
            faq.setLastUpdateTime(now);
            faq.setUpdaterId("admin-123");

            // Then
            assertThat(faq.getId()).isEqualTo(1L);
            assertThat(faq.getQuestion()).isEqualTo("How do I reset my password?");
            assertThat(faq.getAnswer()).isEqualTo("Click on 'Forgot Password' link on the login page.");
            assertThat(faq.getDisplayOrder()).isEqualTo(5);
            assertThat(faq.isActive()).isTrue();
            assertThat(faq.getCreatedTime()).isEqualTo(now);
            assertThat(faq.getLastUpdateTime()).isEqualTo(now);
            assertThat(faq.getUpdaterId()).isEqualTo("admin-123");
        }

        @Test
        @DisplayName("should create FAQ with all args constructor")
        void shouldCreateFaqWithAllArgsConstructor() {
            // Given
            FaqCategory category = new FaqCategory();
            category.setId(1L);
            LocalDateTime now = LocalDateTime.now();

            Faq faq = new Faq(
                    1L,
                    category,
                    "Question",
                    "Answer",
                    1,
                    true,
                    now,
                    now,
                    "admin"
            );

            // Then
            assertThat(faq.getId()).isEqualTo(1L);
            assertThat(faq.getCategory()).isEqualTo(category);
            assertThat(faq.getQuestion()).isEqualTo("Question");
            assertThat(faq.getAnswer()).isEqualTo("Answer");
        }

        @Test
        @DisplayName("should associate FAQ with category")
        void shouldAssociateFaqWithCategory() {
            // Given
            FaqCategory category = new FaqCategory();
            category.setId(1L);
            category.setName("Account");

            Faq faq = new Faq();
            faq.setQuestion("How to?");
            faq.setAnswer("Do this.");
            faq.setCategory(category);

            // Then
            assertThat(faq.getCategory()).isEqualTo(category);
            assertThat(faq.getCategory().getName()).isEqualTo("Account");
        }
    }

    // ==================== Faq Entity Validation Tests ====================

    @Nested
    @DisplayName("Faq Entity Validation")
    class FaqEntityValidationTests {

        @Test
        @DisplayName("should pass validation for valid FAQ")
        void shouldPassValidationForValidFaq() {
            // Given
            Faq faq = new Faq();
            faq.setQuestion("How do I reset my password?");
            faq.setAnswer("Click on 'Forgot Password' link.");

            // When
            Set<ConstraintViolation<Faq>> violations = validator.validate(faq);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t"})
        @DisplayName("should fail validation for blank question")
        void shouldFailValidationForBlankQuestion(String question) {
            // Given
            Faq faq = new Faq();
            faq.setQuestion(question);
            faq.setAnswer("Valid answer");

            // When
            Set<ConstraintViolation<Faq>> violations = validator.validate(faq);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("question"))).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t"})
        @DisplayName("should fail validation for blank answer")
        void shouldFailValidationForBlankAnswer(String answer) {
            // Given
            Faq faq = new Faq();
            faq.setQuestion("Valid question?");
            faq.setAnswer(answer);

            // When
            Set<ConstraintViolation<Faq>> violations = validator.validate(faq);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("answer"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for question exceeding 500 characters")
        void shouldFailValidationForLongQuestion() {
            // Given
            Faq faq = new Faq();
            faq.setQuestion("A".repeat(501));
            faq.setAnswer("Valid answer");

            // When
            Set<ConstraintViolation<Faq>> violations = validator.validate(faq);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("500"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for question with exactly 500 characters")
        void shouldPassValidationForMaxLengthQuestion() {
            // Given
            Faq faq = new Faq();
            faq.setQuestion("A".repeat(500));
            faq.setAnswer("Valid answer");

            // When
            Set<ConstraintViolation<Faq>> violations = validator.validate(faq);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation for updaterId exceeding 255 characters")
        void shouldFailValidationForLongUpdaterId() {
            // Given
            Faq faq = new Faq();
            faq.setQuestion("Valid question");
            faq.setAnswer("Valid answer");
            faq.setUpdaterId("A".repeat(256));

            // When
            Set<ConstraintViolation<Faq>> violations = validator.validate(faq);

            // Then
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== FaqCategory Entity Tests ====================

    @Nested
    @DisplayName("FaqCategory Entity")
    class FaqCategoryEntityTests {

        @Test
        @DisplayName("should create category with default values")
        void shouldCreateCategoryWithDefaults() {
            // Given
            FaqCategory category = new FaqCategory();

            // Then
            assertThat(category.getId()).isNull();
            assertThat(category.getName()).isNull();
            assertThat(category.getDescription()).isNull();
            assertThat(category.getDisplayOrder()).isEqualTo(0);
            assertThat(category.isActive()).isTrue();
            assertThat(category.getFaqs()).isEmpty();
        }

        @Test
        @DisplayName("should set all category fields")
        void shouldSetAllCategoryFields() {
            // Given
            FaqCategory category = new FaqCategory();
            LocalDateTime now = LocalDateTime.now();

            category.setId(1L);
            category.setName("Account");
            category.setDescription("Account-related questions");
            category.setDisplayOrder(1);
            category.setActive(true);
            category.setCreatedTime(now);
            category.setLastUpdateTime(now);
            category.setUpdaterId("admin-123");

            // Then
            assertThat(category.getId()).isEqualTo(1L);
            assertThat(category.getName()).isEqualTo("Account");
            assertThat(category.getDescription()).isEqualTo("Account-related questions");
            assertThat(category.getDisplayOrder()).isEqualTo(1);
            assertThat(category.isActive()).isTrue();
            assertThat(category.getUpdaterId()).isEqualTo("admin-123");
        }

        @Test
        @DisplayName("should add FAQ to category using helper method")
        void shouldAddFaqToCategory() {
            // Given
            FaqCategory category = new FaqCategory();
            category.setName("Account");
            category.setFaqs(new ArrayList<>());

            Faq faq = new Faq();
            faq.setQuestion("How to reset password?");
            faq.setAnswer("Click forgot password link.");

            // When
            category.addFaq(faq);

            // Then
            assertThat(category.getFaqs()).hasSize(1);
            assertThat(category.getFaqs().get(0)).isEqualTo(faq);
            assertThat(faq.getCategory()).isEqualTo(category);
        }

        @Test
        @DisplayName("should add multiple FAQs to category")
        void shouldAddMultipleFaqsToCategory() {
            // Given
            FaqCategory category = new FaqCategory();
            category.setName("Account");
            category.setFaqs(new ArrayList<>());

            Faq faq1 = new Faq();
            faq1.setQuestion("Question 1");
            faq1.setAnswer("Answer 1");

            Faq faq2 = new Faq();
            faq2.setQuestion("Question 2");
            faq2.setAnswer("Answer 2");

            // When
            category.addFaq(faq1);
            category.addFaq(faq2);

            // Then
            assertThat(category.getFaqs()).hasSize(2);
            assertThat(category.getFaqs()).contains(faq1, faq2);
        }
    }

    // ==================== FaqCategory Validation Tests ====================

    @Nested
    @DisplayName("FaqCategory Validation")
    class FaqCategoryValidationTests {

        @Test
        @DisplayName("should pass validation for valid category")
        void shouldPassValidationForValidCategory() {
            // Given
            FaqCategory category = new FaqCategory();
            category.setName("Account");

            // When
            Set<ConstraintViolation<FaqCategory>> violations = validator.validate(category);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t"})
        @DisplayName("should fail validation for blank name")
        void shouldFailValidationForBlankName(String name) {
            // Given
            FaqCategory category = new FaqCategory();
            category.setName(name);

            // When
            Set<ConstraintViolation<FaqCategory>> violations = validator.validate(category);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("name"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for name exceeding 100 characters")
        void shouldFailValidationForLongName() {
            // Given
            FaqCategory category = new FaqCategory();
            category.setName("A".repeat(101));

            // When
            Set<ConstraintViolation<FaqCategory>> violations = validator.validate(category);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("100"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for description exceeding 500 characters")
        void shouldFailValidationForLongDescription() {
            // Given
            FaqCategory category = new FaqCategory();
            category.setName("Valid Name");
            category.setDescription("A".repeat(501));

            // When
            Set<ConstraintViolation<FaqCategory>> violations = validator.validate(category);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("500"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation with null description")
        void shouldPassValidationWithNullDescription() {
            // Given
            FaqCategory category = new FaqCategory();
            category.setName("Valid Name");
            category.setDescription(null);

            // When
            Set<ConstraintViolation<FaqCategory>> violations = validator.validate(category);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    // ==================== FaqDtoIn Tests ====================

    @Nested
    @DisplayName("FaqDtoIn")
    class FaqDtoInTests {

        @Test
        @DisplayName("should create DTO with default values")
        void shouldCreateDtoWithDefaults() {
            // Given
            FaqDtoIn dto = new FaqDtoIn();

            // Then
            assertThat(dto.getQuestion()).isNull();
            assertThat(dto.getAnswer()).isNull();
            assertThat(dto.getCategoryId()).isNull();
            assertThat(dto.getDisplayOrder()).isEqualTo(0);
            assertThat(dto.isActive()).isTrue();
        }

        @Test
        @DisplayName("should set all DTO fields")
        void shouldSetAllDtoFields() {
            // Given
            FaqDtoIn dto = new FaqDtoIn();
            dto.setQuestion("How to reset password?");
            dto.setAnswer("Click forgot password link.");
            dto.setCategoryId(1L);
            dto.setDisplayOrder(5);
            dto.setActive(false);

            // Then
            assertThat(dto.getQuestion()).isEqualTo("How to reset password?");
            assertThat(dto.getAnswer()).isEqualTo("Click forgot password link.");
            assertThat(dto.getCategoryId()).isEqualTo(1L);
            assertThat(dto.getDisplayOrder()).isEqualTo(5);
            assertThat(dto.isActive()).isFalse();
        }

        @Test
        @DisplayName("should create DTO with all args constructor")
        void shouldCreateDtoWithAllArgsConstructor() {
            // Given
            FaqDtoIn dto = new FaqDtoIn("Question?", "Answer", 1L, 5, true);

            // Then
            assertThat(dto.getQuestion()).isEqualTo("Question?");
            assertThat(dto.getAnswer()).isEqualTo("Answer");
            assertThat(dto.getCategoryId()).isEqualTo(1L);
            assertThat(dto.getDisplayOrder()).isEqualTo(5);
            assertThat(dto.isActive()).isTrue();
        }

        @Test
        @DisplayName("should pass validation for valid DTO")
        void shouldPassValidationForValidDto() {
            // Given
            FaqDtoIn dto = new FaqDtoIn();
            dto.setQuestion("Valid question?");
            dto.setAnswer("Valid answer.");

            // When
            Set<ConstraintViolation<FaqDtoIn>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @DisplayName("should fail validation for blank question")
        void shouldFailValidationForBlankQuestion(String question) {
            // Given
            FaqDtoIn dto = new FaqDtoIn();
            dto.setQuestion(question);
            dto.setAnswer("Valid answer");

            // When
            Set<ConstraintViolation<FaqDtoIn>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for question exceeding 500 characters")
        void shouldFailValidationForLongQuestion() {
            // Given
            FaqDtoIn dto = new FaqDtoIn();
            dto.setQuestion("A".repeat(501));
            dto.setAnswer("Valid answer");

            // When
            Set<ConstraintViolation<FaqDtoIn>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for answer exceeding 10000 characters")
        void shouldFailValidationForLongAnswer() {
            // Given
            FaqDtoIn dto = new FaqDtoIn();
            dto.setQuestion("Valid question");
            dto.setAnswer("A".repeat(10001));

            // When
            Set<ConstraintViolation<FaqDtoIn>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should pass validation for answer with exactly 10000 characters")
        void shouldPassValidationForMaxLengthAnswer() {
            // Given
            FaqDtoIn dto = new FaqDtoIn();
            dto.setQuestion("Valid question");
            dto.setAnswer("A".repeat(10000));

            // When
            Set<ConstraintViolation<FaqDtoIn>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    // ==================== FaqDtoOut Tests ====================

    @Nested
    @DisplayName("FaqDtoOut")
    class FaqDtoOutTests {

        @Test
        @DisplayName("should create DTO with default constructor")
        void shouldCreateDtoWithDefaults() {
            // Given
            FaqDtoOut dto = new FaqDtoOut();

            // Then
            assertThat(dto.getId()).isNull();
            assertThat(dto.getQuestion()).isNull();
            assertThat(dto.getAnswer()).isNull();
            assertThat(dto.getCategoryId()).isNull();
            assertThat(dto.getCategoryName()).isNull();
        }

        @Test
        @DisplayName("should set all DTO fields")
        void shouldSetAllDtoFields() {
            // Given
            FaqDtoOut dto = new FaqDtoOut();
            LocalDateTime now = LocalDateTime.now();

            dto.setId(1L);
            dto.setQuestion("How to?");
            dto.setAnswer("Do this.");
            dto.setCategoryId(5L);
            dto.setCategoryName("Account");
            dto.setDisplayOrder(3);
            dto.setActive(true);
            dto.setCreatedTime(now);
            dto.setLastUpdateTime(now);
            dto.setUpdaterId("admin");

            // Then
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getQuestion()).isEqualTo("How to?");
            assertThat(dto.getAnswer()).isEqualTo("Do this.");
            assertThat(dto.getCategoryId()).isEqualTo(5L);
            assertThat(dto.getCategoryName()).isEqualTo("Account");
            assertThat(dto.getDisplayOrder()).isEqualTo(3);
            assertThat(dto.isActive()).isTrue();
            assertThat(dto.getCreatedTime()).isEqualTo(now);
            assertThat(dto.getLastUpdateTime()).isEqualTo(now);
            assertThat(dto.getUpdaterId()).isEqualTo("admin");
        }

        @Test
        @DisplayName("should create DTO with all args constructor")
        void shouldCreateDtoWithAllArgsConstructor() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            FaqDtoOut dto = new FaqDtoOut(
                    1L,
                    "Question?",
                    "Answer",
                    2L,
                    "Category",
                    1,
                    true,
                    now,
                    now,
                    "admin"
            );

            // Then
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getQuestion()).isEqualTo("Question?");
            assertThat(dto.getCategoryName()).isEqualTo("Category");
        }
    }

    // ==================== FaqCategoryDtoIn Tests ====================

    @Nested
    @DisplayName("FaqCategoryDtoIn")
    class FaqCategoryDtoInTests {

        @Test
        @DisplayName("should create DTO with default values")
        void shouldCreateDtoWithDefaults() {
            // Given
            FaqCategoryDtoIn dto = new FaqCategoryDtoIn();

            // Then
            assertThat(dto.getName()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getDisplayOrder()).isEqualTo(0);
            assertThat(dto.isActive()).isTrue();
        }

        @Test
        @DisplayName("should set all DTO fields")
        void shouldSetAllDtoFields() {
            // Given
            FaqCategoryDtoIn dto = new FaqCategoryDtoIn();
            dto.setName("Account");
            dto.setDescription("Account questions");
            dto.setDisplayOrder(1);
            dto.setActive(false);

            // Then
            assertThat(dto.getName()).isEqualTo("Account");
            assertThat(dto.getDescription()).isEqualTo("Account questions");
            assertThat(dto.getDisplayOrder()).isEqualTo(1);
            assertThat(dto.isActive()).isFalse();
        }

        @Test
        @DisplayName("should create DTO with all args constructor")
        void shouldCreateDtoWithAllArgsConstructor() {
            // Given
            FaqCategoryDtoIn dto = new FaqCategoryDtoIn("Account", "Account questions", 1, true);

            // Then
            assertThat(dto.getName()).isEqualTo("Account");
            assertThat(dto.getDescription()).isEqualTo("Account questions");
        }

        @Test
        @DisplayName("should pass validation for valid DTO")
        void shouldPassValidationForValidDto() {
            // Given
            FaqCategoryDtoIn dto = new FaqCategoryDtoIn();
            dto.setName("Valid Name");

            // When
            Set<ConstraintViolation<FaqCategoryDtoIn>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @DisplayName("should fail validation for blank name")
        void shouldFailValidationForBlankName(String name) {
            // Given
            FaqCategoryDtoIn dto = new FaqCategoryDtoIn();
            dto.setName(name);

            // When
            Set<ConstraintViolation<FaqCategoryDtoIn>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for name exceeding 100 characters")
        void shouldFailValidationForLongName() {
            // Given
            FaqCategoryDtoIn dto = new FaqCategoryDtoIn();
            dto.setName("A".repeat(101));

            // When
            Set<ConstraintViolation<FaqCategoryDtoIn>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for description exceeding 500 characters")
        void shouldFailValidationForLongDescription() {
            // Given
            FaqCategoryDtoIn dto = new FaqCategoryDtoIn();
            dto.setName("Valid Name");
            dto.setDescription("A".repeat(501));

            // When
            Set<ConstraintViolation<FaqCategoryDtoIn>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== FaqCategoryDtoOut Tests ====================

    @Nested
    @DisplayName("FaqCategoryDtoOut")
    class FaqCategoryDtoOutTests {

        @Test
        @DisplayName("should create DTO with default constructor")
        void shouldCreateDtoWithDefaults() {
            // Given
            FaqCategoryDtoOut dto = new FaqCategoryDtoOut();

            // Then
            assertThat(dto.getId()).isNull();
            assertThat(dto.getName()).isNull();
            assertThat(dto.getFaqs()).isEmpty();
        }

        @Test
        @DisplayName("should set all DTO fields")
        void shouldSetAllDtoFields() {
            // Given
            FaqCategoryDtoOut dto = new FaqCategoryDtoOut();
            LocalDateTime now = LocalDateTime.now();

            dto.setId(1L);
            dto.setName("Account");
            dto.setDescription("Account questions");
            dto.setDisplayOrder(1);
            dto.setActive(true);
            dto.setCreatedTime(now);
            dto.setLastUpdateTime(now);
            dto.setUpdaterId("admin");

            // Then
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Account");
            assertThat(dto.getDescription()).isEqualTo("Account questions");
            assertThat(dto.getDisplayOrder()).isEqualTo(1);
            assertThat(dto.isActive()).isTrue();
            assertThat(dto.getUpdaterId()).isEqualTo("admin");
        }

        @Test
        @DisplayName("should handle FAQs list")
        void shouldHandleFaqsList() {
            // Given
            FaqCategoryDtoOut dto = new FaqCategoryDtoOut();
            FaqDtoOut faq1 = new FaqDtoOut();
            faq1.setId(1L);
            faq1.setQuestion("Q1");

            FaqDtoOut faq2 = new FaqDtoOut();
            faq2.setId(2L);
            faq2.setQuestion("Q2");

            // When
            dto.getFaqs().add(faq1);
            dto.getFaqs().add(faq2);

            // Then
            assertThat(dto.getFaqs()).hasSize(2);
            assertThat(dto.getFaqs().get(0).getQuestion()).isEqualTo("Q1");
            assertThat(dto.getFaqs().get(1).getQuestion()).isEqualTo("Q2");
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle FAQ with Markdown in answer")
        void shouldHandleMarkdownInAnswer() {
            // Given
            Faq faq = new Faq();
            faq.setQuestion("How do I format text?");
            faq.setAnswer("# Heading\n**Bold** and *italic* text.\n- List item");

            // When
            Set<ConstraintViolation<Faq>> violations = validator.validate(faq);

            // Then
            assertThat(violations).isEmpty();
            assertThat(faq.getAnswer()).contains("#");
            assertThat(faq.getAnswer()).contains("**");
        }

        @Test
        @DisplayName("should handle FAQ with HTML-like content")
        void shouldHandleHtmlLikeContent() {
            // Given
            Faq faq = new Faq();
            faq.setQuestion("How do I use <code> tags?");
            faq.setAnswer("Wrap code in <code>...</code> tags.");

            // When
            Set<ConstraintViolation<Faq>> violations = validator.validate(faq);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle FAQ with Unicode characters")
        void shouldHandleUnicodeCharacters() {
            // Given
            Faq faq = new Faq();
            faq.setQuestion("Jak zresetować hasło? 🔐");
            faq.setAnswer("Kliknij link 'Zapomniałem hasła' na stronie logowania.");

            // When
            Set<ConstraintViolation<Faq>> violations = validator.validate(faq);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle category with very long question just at limit")
        void shouldHandleQuestionAtLimit() {
            // Given
            FaqDtoIn dto = new FaqDtoIn();
            dto.setQuestion("A".repeat(500));
            dto.setAnswer("Valid answer");

            // When
            Set<ConstraintViolation<FaqDtoIn>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle negative display order")
        void shouldHandleNegativeDisplayOrder() {
            // Given
            Faq faq = new Faq();
            faq.setQuestion("Valid question");
            faq.setAnswer("Valid answer");
            faq.setDisplayOrder(-1);

            // When
            Set<ConstraintViolation<Faq>> violations = validator.validate(faq);

            // Then - No @Min constraint on displayOrder
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle inactive FAQ")
        void shouldHandleInactiveFaq() {
            // Given
            Faq faq = new Faq();
            faq.setQuestion("Old question");
            faq.setAnswer("Old answer");
            faq.setActive(false);

            // Then
            assertThat(faq.isActive()).isFalse();
        }

        @Test
        @DisplayName("should handle null categoryId in DTO")
        void shouldHandleNullCategoryId() {
            // Given
            FaqDtoIn dto = new FaqDtoIn();
            dto.setQuestion("Uncategorized question?");
            dto.setAnswer("Answer without category.");
            dto.setCategoryId(null);

            // When
            Set<ConstraintViolation<FaqDtoIn>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
            assertThat(dto.getCategoryId()).isNull();
        }
    }
}
