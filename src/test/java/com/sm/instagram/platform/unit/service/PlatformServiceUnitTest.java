package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.contenttype.ContentType;
import com.sm.instagram.platform.platform.*;
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

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Platform entity and DTOs.
 * Tests entity validation and basic behavior.
 */
@DisplayName("Platform Unit Tests")
class PlatformServiceUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== Platform Entity Tests ====================

    @Nested
    @DisplayName("Platform Entity")
    class PlatformEntityTests {

        @Test
        @DisplayName("should create valid platform with all fields")
        void shouldCreateValidPlatform() {
            // Given
            Platform platform = new Platform();
            platform.setId(1L);
            platform.setName("Instagram");
            platform.setLogoUrl("https://instagram.com/logo.png");
            platform.setActive(true);
            platform.setContentTypes(new HashSet<>());

            // Then
            assertThat(platform.getId()).isEqualTo(1L);
            assertThat(platform.getName()).isEqualTo("Instagram");
            assertThat(platform.getLogoUrl()).isEqualTo("https://instagram.com/logo.png");
            assertThat(platform.getActive()).isTrue();
            assertThat(platform.getContentTypes()).isEmpty();
        }

        @Test
        @DisplayName("should create platform using no-args constructor")
        void shouldCreatePlatformUsingNoArgsConstructor() {
            // Given
            Platform platform = new Platform();

            // Then
            assertThat(platform.getId()).isNull();
            assertThat(platform.getName()).isNull();
            assertThat(platform.getLogoUrl()).isNull();
            assertThat(platform.getActive()).isNull();
        }

        @ParameterizedTest
        @CsvSource({
                "Instagram, true",
                "TikTok, true",
                "YouTube, true",
                "Facebook, false",
                "Twitter, false"
        })
        @DisplayName("should store various platform data")
        void shouldStoreVariousPlatformData(String name, boolean active) {
            // Given
            Platform platform = new Platform();
            platform.setName(name);
            platform.setActive(active);

            // Then
            assertThat(platform.getName()).isEqualTo(name);
            assertThat(platform.getActive()).isEqualTo(active);
        }

        @Test
        @DisplayName("should manage content types collection")
        void shouldManageContentTypesCollection() {
            // Given
            Platform platform = new Platform();
            ContentType photo = new ContentType(1L, "Photo");
            ContentType video = new ContentType(2L, "Video");

            Set<ContentType> contentTypes = new HashSet<>();
            contentTypes.add(photo);
            contentTypes.add(video);
            platform.setContentTypes(contentTypes);

            // Then
            assertThat(platform.getContentTypes()).hasSize(2);
            assertThat(platform.getContentTypes()).contains(photo, video);
        }
    }

    // ==================== Platform Validation Tests ====================

    @Nested
    @DisplayName("Platform Validation")
    class PlatformValidationTests {

        @Test
        @DisplayName("should pass validation for valid platform")
        void shouldPassValidationForValidPlatform() {
            // Given
            Platform platform = new Platform();
            platform.setName("Instagram");
            platform.setLogoUrl("https://instagram.com/logo.png");
            platform.setActive(true);

            // When
            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t"})
        @DisplayName("should fail validation for blank name")
        void shouldFailValidationForBlankName(String name) {
            // Given
            Platform platform = new Platform();
            platform.setName(name);
            platform.setLogoUrl("https://example.com/logo.png");

            // When
            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);

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
            Platform platform = new Platform();
            platform.setName(longName);

            // When
            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("255"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for URL longer than 2048 characters")
        void shouldFailValidationForLongUrl() {
            // Given
            String longUrl = "https://example.com/" + "a".repeat(2030);
            Platform platform = new Platform();
            platform.setName("Test");
            platform.setLogoUrl(longUrl);

            // When
            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("2048"))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "http://example.com/logo.png",
                "ftp://example.com/logo.png",
                "example.com/logo.png",
                "invalid-url"
        })
        @DisplayName("should fail validation for non-HTTPS URLs")
        void shouldFailValidationForNonHttpsUrls(String url) {
            // Given
            Platform platform = new Platform();
            platform.setName("Test");
            platform.setLogoUrl(url);

            // When
            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("HTTPS"))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://example.com/logo.png",
                "https://cdn.instagram.com/v/logo.jpg",
                "https://storage.googleapis.com/bucket/image.webp"
        })
        @DisplayName("should pass validation for valid HTTPS URLs")
        void shouldPassValidationForValidHttpsUrls(String url) {
            // Given
            Platform platform = new Platform();
            platform.setName("Test");
            platform.setLogoUrl(url);
            platform.setActive(true);

            // When
            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    // ==================== PlatformDto Validation Tests ====================

    @Nested
    @DisplayName("PlatformDto Validation")
    class PlatformDtoValidationTests {

        @Test
        @DisplayName("should pass validation for valid DTO")
        void shouldPassValidationForValidDto() {
            // Given
            PlatformDto dto = new PlatformDto();
            dto.setId(1L);
            dto.setName("Instagram");
            dto.setLogoUrl("https://instagram.com/logo.png");
            dto.setActive(true);
            dto.setContentTypes(new HashSet<>());

            // When
            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @DisplayName("should fail validation for blank name in DTO")
        void shouldFailValidationForBlankNameInDto(String name) {
            // Given
            PlatformDto dto = new PlatformDto();
            dto.setName(name);
            dto.setActive(true);
            dto.setContentTypes(new HashSet<>());

            // When
            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for null active field in DTO")
        void shouldFailValidationForNullActiveInDto() {
            // Given
            PlatformDto dto = new PlatformDto();
            dto.setName("Test");
            dto.setActive(null);
            dto.setContentTypes(new HashSet<>());

            // When
            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("active"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for null content types in DTO")
        void shouldFailValidationForNullContentTypesInDto() {
            // Given
            PlatformDto dto = new PlatformDto();
            dto.setName("Test");
            dto.setActive(true);
            dto.setContentTypes(null);

            // When
            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("contentTypes"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for non-HTTPS URL in DTO")
        void shouldFailValidationForNonHttpsUrlInDto() {
            // Given
            PlatformDto dto = new PlatformDto();
            dto.setName("Test");
            dto.setLogoUrl("http://insecure.com/logo.png");
            dto.setActive(true);
            dto.setContentTypes(new HashSet<>());

            // When
            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("HTTPS"))).isTrue();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle platform with exactly 255 character name")
        void shouldHandleMaxLengthName() {
            // Given
            String maxName = "A".repeat(255);
            Platform platform = new Platform();
            platform.setName(maxName);

            // When
            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);

            // Then
            assertThat(violations).isEmpty();
            assertThat(platform.getName()).hasSize(255);
        }

        @Test
        @DisplayName("should handle platform with exactly 2048 character URL")
        void shouldHandleMaxLengthUrl() {
            // Given
            String maxUrl = "https://example.com/" + "a".repeat(2028);
            Platform platform = new Platform();
            platform.setName("Test");
            platform.setLogoUrl(maxUrl);

            // When
            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);

            // Then
            assertThat(violations).isEmpty();
            assertThat(platform.getLogoUrl()).hasSize(2048);
        }

        @Test
        @DisplayName("should handle platform with null logo URL")
        void shouldHandleNullLogoUrl() {
            // Given
            Platform platform = new Platform();
            platform.setName("Test");
            platform.setLogoUrl(null);
            platform.setActive(true);

            // When
            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle empty content types set")
        void shouldHandleEmptyContentTypes() {
            // Given
            Platform platform = new Platform();
            platform.setName("Test");
            platform.setActive(true);
            platform.setContentTypes(new HashSet<>());

            // Then
            assertThat(platform.getContentTypes()).isEmpty();
        }

        @Test
        @DisplayName("should handle platform with special characters in name")
        void shouldHandleSpecialCharactersInName() {
            // Given
            Platform platform = new Platform();
            platform.setName("Test & Demo");

            // When
            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    // ==================== Common Platforms ====================

    @Nested
    @DisplayName("Common Platforms")
    class CommonPlatformsTests {

        @ParameterizedTest
        @ValueSource(strings = {"Instagram", "TikTok", "YouTube", "Facebook", "Twitter", "LinkedIn", "Pinterest", "Snapchat"})
        @DisplayName("should accept common social media platforms")
        void shouldAcceptCommonSocialMediaPlatforms(String platformName) {
            // Given
            Platform platform = new Platform();
            platform.setName(platformName);

            // When
            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should track platform active status")
        void shouldTrackPlatformActiveStatus() {
            // Given
            Platform active = new Platform();
            active.setName("Instagram");
            active.setActive(true);

            Platform inactive = new Platform();
            inactive.setName("Vine");
            inactive.setActive(false);

            // Then
            assertThat(active.getActive()).isTrue();
            assertThat(inactive.getActive()).isFalse();
        }
    }
}
