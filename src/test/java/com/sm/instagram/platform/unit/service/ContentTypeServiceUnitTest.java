package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.contenttype.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ContentType entity and DTOs.
 * Tests entity validation and basic behavior.
 */
@DisplayName("ContentType Unit Tests")
class ContentTypeServiceUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== ContentType Entity Tests ====================

    @Nested
    @DisplayName("ContentType Entity")
    class ContentTypeEntityTests {

        @Test
        @DisplayName("should create valid content type with all fields")
        void shouldCreateValidContentType() {
            // Given
            ContentType contentType = new ContentType();
            contentType.setId(1L);
            contentType.setName("Photo");

            // Then
            assertThat(contentType.getId()).isEqualTo(1L);
            assertThat(contentType.getName()).isEqualTo("Photo");
        }

        @Test
        @DisplayName("should create content type using all-args constructor")
        void shouldCreateContentTypeUsingAllArgsConstructor() {
            // Given
            ContentType contentType = new ContentType(1L, "Video");

            // Then
            assertThat(contentType.getId()).isEqualTo(1L);
            assertThat(contentType.getName()).isEqualTo("Video");
        }

        @Test
        @DisplayName("should create content type using no-args constructor")
        void shouldCreateContentTypeUsingNoArgsConstructor() {
            // Given
            ContentType contentType = new ContentType();

            // Then
            assertThat(contentType.getId()).isNull();
            assertThat(contentType.getName()).isNull();
        }

        @ParameterizedTest
        @CsvSource({
                "Photo",
                "Video",
                "Story",
                "Reel",
                "Carousel",
                "IGTV"
        })
        @DisplayName("should store various content type names")
        void shouldStoreVariousContentTypeNames(String name) {
            // Given
            ContentType contentType = new ContentType();
            contentType.setName(name);

            // Then
            assertThat(contentType.getName()).isEqualTo(name);
        }
    }

    // ==================== ContentType Validation Tests ====================

    @Nested
    @DisplayName("ContentType Validation")
    class ContentTypeValidationTests {

        @Test
        @DisplayName("should pass validation for valid content type")
        void shouldPassValidationForValidContentType() {
            // Given
            ContentType contentType = new ContentType(null, "Photo");

            // When
            Set<ConstraintViolation<ContentType>> violations = validator.validate(contentType);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t"})
        @DisplayName("should fail validation for blank name")
        void shouldFailValidationForBlankName(String name) {
            // Given
            ContentType contentType = new ContentType(null, name);

            // When
            Set<ConstraintViolation<ContentType>> violations = validator.validate(contentType);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("name"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for name longer than 255 characters")
        void shouldFailValidationForLongName() {
            // Given
            String longName = "A".repeat(256);
            ContentType contentType = new ContentType(null, longName);

            // When
            Set<ConstraintViolation<ContentType>> violations = validator.validate(contentType);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("255"))).isTrue();
        }
    }

    // ==================== ContentTypeDto Tests ====================

    @Nested
    @DisplayName("ContentTypeDto")
    class ContentTypeDtoTests {

        @Test
        @DisplayName("should create DTO with all fields")
        void shouldCreateDtoWithAllFields() {
            // Given
            ContentTypeDto dto = new ContentTypeDto();
            dto.setId(1L);
            dto.setName("Photo");

            // Then
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Photo");
        }

        @Test
        @DisplayName("should pass validation for valid DTO")
        void shouldPassValidationForValidDto() {
            // Given
            ContentTypeDto dto = new ContentTypeDto();
            dto.setId(1L);
            dto.setName("Video");

            // When
            Set<ConstraintViolation<ContentTypeDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t"})
        @DisplayName("should fail validation for blank name in DTO")
        void shouldFailValidationForBlankNameInDto(String name) {
            // Given
            ContentTypeDto dto = new ContentTypeDto();
            dto.setName(name);

            // When
            Set<ConstraintViolation<ContentTypeDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for name longer than 255 characters in DTO")
        void shouldFailValidationForLongNameInDto() {
            // Given
            String longName = "A".repeat(256);
            ContentTypeDto dto = new ContentTypeDto();
            dto.setName(longName);

            // When
            Set<ConstraintViolation<ContentTypeDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== ContentTypeDtoOut Tests ====================

    @Nested
    @DisplayName("ContentTypeDtoOut")
    class ContentTypeDtoOutTests {

        @Test
        @DisplayName("should create output DTO with all fields")
        void shouldCreateOutputDtoWithAllFields() {
            // Given
            ContentTypeDtoOut dto = ContentTypeDtoOut.builder()
                    .id(1L)
                    .name("Zdjęcie")
                    .originalName("Photo")
                    .build();

            // Then
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Zdjęcie");
            assertThat(dto.getOriginalName()).isEqualTo("Photo");
        }

        @Test
        @DisplayName("should support translation with different name and original name")
        void shouldSupportTranslationWithDifferentNames() {
            // Given - Simulating translated name
            ContentTypeDtoOut dto = ContentTypeDtoOut.builder()
                    .name("Wideo")
                    .originalName("Video")
                    .build();

            // Then
            assertThat(dto.getName()).isEqualTo("Wideo");
            assertThat(dto.getOriginalName()).isEqualTo("Video");
            assertThat(dto.getName()).isNotEqualTo(dto.getOriginalName());
        }

        @Test
        @DisplayName("should create output DTO using no-args constructor")
        void shouldCreateOutputDtoUsingNoArgsConstructor() {
            // Given
            ContentTypeDtoOut dto = new ContentTypeDtoOut();

            // Then
            assertThat(dto.getId()).isNull();
            assertThat(dto.getName()).isNull();
            assertThat(dto.getOriginalName()).isNull();
        }

        @Test
        @DisplayName("should create output DTO using all-args constructor")
        void shouldCreateOutputDtoUsingAllArgsConstructor() {
            // Given
            ContentTypeDtoOut dto = new ContentTypeDtoOut(1L, "Story", "Story");

            // Then
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Story");
            assertThat(dto.getOriginalName()).isEqualTo("Story");
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle content type with exactly 255 characters (maximum)")
        void shouldHandleMaxLengthName() {
            // Given
            String maxName = "A".repeat(255);
            ContentType contentType = new ContentType(null, maxName);

            // When
            Set<ConstraintViolation<ContentType>> violations = validator.validate(contentType);

            // Then
            assertThat(violations).isEmpty();
            assertThat(contentType.getName()).hasSize(255);
        }

        @Test
        @DisplayName("should handle content type with single character")
        void shouldHandleSingleCharacterName() {
            // Given
            ContentType contentType = new ContentType(null, "P");

            // When
            Set<ConstraintViolation<ContentType>> violations = validator.validate(contentType);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle special characters in name")
        void shouldHandleSpecialCharactersInName() {
            // Given
            ContentType contentType = new ContentType(null, "Photo & Video");

            // When
            Set<ConstraintViolation<ContentType>> violations = validator.validate(contentType);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle Polish characters in name")
        void shouldHandlePolishCharactersInName() {
            // Given
            ContentType contentType = new ContentType(null, "Zdjęcie");

            // When
            Set<ConstraintViolation<ContentType>> violations = validator.validate(contentType);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle emoji in name")
        void shouldHandleEmojiInName() {
            // Given
            ContentTypeDtoOut dto = ContentTypeDtoOut.builder()
                    .name("Photo 📷")
                    .originalName("Photo")
                    .build();

            // Then
            assertThat(dto.getName()).contains("📷");
        }
    }

    // ==================== Common Content Types ====================

    @Nested
    @DisplayName("Common Content Types")
    class CommonContentTypesTests {

        @ParameterizedTest
        @ValueSource(strings = {"Photo", "Video", "Story", "Reel", "Carousel", "IGTV", "Live"})
        @DisplayName("should accept common Instagram content types")
        void shouldAcceptCommonInstagramContentTypes(String contentTypeName) {
            // Given
            ContentType contentType = new ContentType(null, contentTypeName);

            // When
            Set<ConstraintViolation<ContentType>> violations = validator.validate(contentType);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"TikTok Video", "YouTube Short", "Facebook Post", "Twitter Thread"})
        @DisplayName("should accept various platform content types")
        void shouldAcceptVariousPlatformContentTypes(String contentTypeName) {
            // Given
            ContentType contentType = new ContentType(null, contentTypeName);

            // When
            Set<ConstraintViolation<ContentType>> violations = validator.validate(contentType);

            // Then
            assertThat(violations).isEmpty();
        }
    }
}
