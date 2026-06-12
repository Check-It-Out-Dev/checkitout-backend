package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.servicetype.ServiceType;
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
 * Unit tests for ServiceType entity.
 * Tests entity validation and basic behavior.
 */
@DisplayName("ServiceType Unit Tests")
class ServiceTypeServiceUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== ServiceType Entity Tests ====================

    @Nested
    @DisplayName("ServiceType Entity")
    class ServiceTypeEntityTests {

        @Test
        @DisplayName("should create valid service type with all fields")
        void shouldCreateValidServiceType() {
            // Given
            ServiceType serviceType = new ServiceType();
            serviceType.setId(1L);
            serviceType.setName("Restaurant");
            serviceType.setDescription("Food and dining establishments");
            serviceType.setCategory("Food & Beverage");

            // Then
            assertThat(serviceType.getId()).isEqualTo(1L);
            assertThat(serviceType.getName()).isEqualTo("Restaurant");
            assertThat(serviceType.getDescription()).isEqualTo("Food and dining establishments");
            assertThat(serviceType.getCategory()).isEqualTo("Food & Beverage");
        }

        @Test
        @DisplayName("should create service type using builder")
        void shouldCreateServiceTypeUsingBuilder() {
            // Given
            ServiceType serviceType = ServiceType.builder()
                    .id(1L)
                    .name("Cafe")
                    .description("Coffee shops and cafes")
                    .category("Food & Beverage")
                    .build();

            // Then
            assertThat(serviceType.getId()).isEqualTo(1L);
            assertThat(serviceType.getName()).isEqualTo("Cafe");
            assertThat(serviceType.getDescription()).isEqualTo("Coffee shops and cafes");
            assertThat(serviceType.getCategory()).isEqualTo("Food & Beverage");
        }

        @Test
        @DisplayName("should create service type using all-args constructor")
        void shouldCreateServiceTypeUsingAllArgsConstructor() {
            // Given
            ServiceType serviceType = new ServiceType(1L, "Hotel", "Accommodation", "Hospitality");

            // Then
            assertThat(serviceType.getId()).isEqualTo(1L);
            assertThat(serviceType.getName()).isEqualTo("Hotel");
            assertThat(serviceType.getDescription()).isEqualTo("Accommodation");
            assertThat(serviceType.getCategory()).isEqualTo("Hospitality");
        }

        @Test
        @DisplayName("should create service type using no-args constructor")
        void shouldCreateServiceTypeUsingNoArgsConstructor() {
            // Given
            ServiceType serviceType = new ServiceType();

            // Then
            assertThat(serviceType.getId()).isNull();
            assertThat(serviceType.getName()).isNull();
            assertThat(serviceType.getDescription()).isNull();
            assertThat(serviceType.getCategory()).isNull();
        }

        @ParameterizedTest
        @CsvSource({
                "Restaurant, Food establishments, Food",
                "Spa, Wellness and relaxation, Beauty",
                "Gym, Fitness centers, Sports",
                "Salon, Hair and beauty, Beauty",
                "Bakery, Fresh baked goods, Food"
        })
        @DisplayName("should store various service types")
        void shouldStoreVariousServiceTypes(String name, String description, String category) {
            // Given
            ServiceType serviceType = ServiceType.builder()
                    .name(name)
                    .description(description)
                    .category(category)
                    .build();

            // Then
            assertThat(serviceType.getName()).isEqualTo(name);
            assertThat(serviceType.getDescription()).isEqualTo(description);
            assertThat(serviceType.getCategory()).isEqualTo(category);
        }
    }

    // ==================== ServiceType Validation Tests ====================

    @Nested
    @DisplayName("ServiceType Validation")
    class ServiceTypeValidationTests {

        @Test
        @DisplayName("should pass validation for valid service type")
        void shouldPassValidationForValidServiceType() {
            // Given
            ServiceType serviceType = ServiceType.builder()
                    .name("Restaurant")
                    .description("Food establishment")
                    .category("Food")
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with only required field (name)")
        void shouldPassValidationWithOnlyName() {
            // Given
            ServiceType serviceType = ServiceType.builder()
                    .name("Test Service")
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t"})
        @DisplayName("should fail validation for blank name")
        void shouldFailValidationForBlankName(String name) {
            // Given
            ServiceType serviceType = ServiceType.builder()
                    .name(name)
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("name"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for name shorter than 2 characters")
        void shouldFailValidationForShortName() {
            // Given
            ServiceType serviceType = ServiceType.builder()
                    .name("A")
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("between 2 and 100"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for name longer than 100 characters")
        void shouldFailValidationForLongName() {
            // Given
            String longName = "A".repeat(101);
            ServiceType serviceType = ServiceType.builder()
                    .name(longName)
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("between 2 and 100"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for description longer than 500 characters")
        void shouldFailValidationForLongDescription() {
            // Given
            String longDescription = "A".repeat(501);
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .description(longDescription)
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("cannot exceed 500"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for category longer than 100 characters")
        void shouldFailValidationForLongCategory() {
            // Given
            String longCategory = "A".repeat(101);
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .category(longCategory)
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("cannot exceed 100"))).isTrue();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle name with exactly 2 characters (minimum)")
        void shouldHandleMinLengthName() {
            // Given
            ServiceType serviceType = ServiceType.builder()
                    .name("AB")
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isEmpty();
            assertThat(serviceType.getName()).hasSize(2);
        }

        @Test
        @DisplayName("should handle name with exactly 100 characters (maximum)")
        void shouldHandleMaxLengthName() {
            // Given
            String maxName = "A".repeat(100);
            ServiceType serviceType = ServiceType.builder()
                    .name(maxName)
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isEmpty();
            assertThat(serviceType.getName()).hasSize(100);
        }

        @Test
        @DisplayName("should handle description with exactly 500 characters (maximum)")
        void shouldHandleMaxLengthDescription() {
            // Given
            String maxDescription = "A".repeat(500);
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .description(maxDescription)
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isEmpty();
            assertThat(serviceType.getDescription()).hasSize(500);
        }

        @Test
        @DisplayName("should handle category with exactly 100 characters (maximum)")
        void shouldHandleMaxLengthCategory() {
            // Given
            String maxCategory = "A".repeat(100);
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .category(maxCategory)
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isEmpty();
            assertThat(serviceType.getCategory()).hasSize(100);
        }

        @Test
        @DisplayName("should handle null optional fields")
        void shouldHandleNullOptionalFields() {
            // Given
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .description(null)
                    .category(null)
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle special characters in name")
        void shouldHandleSpecialCharactersInName() {
            // Given
            ServiceType serviceType = ServiceType.builder()
                    .name("Café & Restaurant")
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle Polish characters in name")
        void shouldHandlePolishCharactersInName() {
            // Given
            ServiceType serviceType = ServiceType.builder()
                    .name("Żółta Kawiarnia")
                    .build();

            // When
            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    // ==================== Builder Pattern Tests ====================

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPatternTests {

        @Test
        @DisplayName("should build with partial fields")
        void shouldBuildWithPartialFields() {
            // Given
            ServiceType serviceType = ServiceType.builder()
                    .name("Test")
                    .build();

            // Then
            assertThat(serviceType.getName()).isEqualTo("Test");
            assertThat(serviceType.getDescription()).isNull();
            assertThat(serviceType.getCategory()).isNull();
        }

        @Test
        @DisplayName("should allow setting fields after build")
        void shouldAllowSettingFieldsAfterBuild() {
            // Given
            ServiceType serviceType = ServiceType.builder()
                    .name("Test")
                    .build();

            // When
            serviceType.setDescription("New Description");
            serviceType.setCategory("New Category");

            // Then
            assertThat(serviceType.getDescription()).isEqualTo("New Description");
            assertThat(serviceType.getCategory()).isEqualTo("New Category");
        }
    }
}
