package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.translation.TranslationService;
import com.sm.instagram.platform.servicetype.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for the ServiceType package.
 * Tests entity, controller, converter, mapper, and DTOs.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceType Full Unit Tests")
class ServiceTypeFullUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== ServiceType Entity Tests ====================

    @Nested
    @DisplayName("ServiceType Entity Tests")
    class ServiceTypeEntityTests {

        @Test
        @DisplayName("should create valid service type with all fields using builder")
        void shouldCreateValidServiceTypeWithBuilder() {
            ServiceType serviceType = ServiceType.builder()
                    .id(1L)
                    .name("Restaurant")
                    .description("Food and dining establishments")
                    .category("Food & Beverage")
                    .build();

            assertThat(serviceType.getId()).isEqualTo(1L);
            assertThat(serviceType.getName()).isEqualTo("Restaurant");
            assertThat(serviceType.getDescription()).isEqualTo("Food and dining establishments");
            assertThat(serviceType.getCategory()).isEqualTo("Food & Beverage");
        }

        @Test
        @DisplayName("should create service type using all-args constructor")
        void shouldCreateServiceTypeUsingAllArgsConstructor() {
            ServiceType serviceType = new ServiceType(1L, "Hotel", "Accommodation services", "Hospitality");

            assertThat(serviceType.getId()).isEqualTo(1L);
            assertThat(serviceType.getName()).isEqualTo("Hotel");
            assertThat(serviceType.getDescription()).isEqualTo("Accommodation services");
            assertThat(serviceType.getCategory()).isEqualTo("Hospitality");
        }

        @Test
        @DisplayName("should create service type using no-args constructor with setters")
        void shouldCreateServiceTypeUsingNoArgsConstructorWithSetters() {
            ServiceType serviceType = new ServiceType();
            serviceType.setId(2L);
            serviceType.setName("Spa");
            serviceType.setDescription("Wellness services");
            serviceType.setCategory("Beauty");

            assertThat(serviceType.getId()).isEqualTo(2L);
            assertThat(serviceType.getName()).isEqualTo("Spa");
            assertThat(serviceType.getDescription()).isEqualTo("Wellness services");
            assertThat(serviceType.getCategory()).isEqualTo("Beauty");
        }

        @Test
        @DisplayName("should have null fields when using no-args constructor")
        void shouldHaveNullFieldsWhenUsingNoArgsConstructor() {
            ServiceType serviceType = new ServiceType();

            assertThat(serviceType.getId()).isNull();
            assertThat(serviceType.getName()).isNull();
            assertThat(serviceType.getDescription()).isNull();
            assertThat(serviceType.getCategory()).isNull();
        }

        @ParameterizedTest
        @CsvSource({
                "Restaurant, Food service, Food",
                "Spa, Wellness center, Beauty",
                "Gym, Fitness center, Sports",
                "Salon, Hair salon, Beauty",
                "Bakery, Fresh baked goods, Food"
        })
        @DisplayName("should store various service types correctly")
        void shouldStoreVariousServiceTypes(String name, String description, String category) {
            ServiceType serviceType = ServiceType.builder()
                    .name(name)
                    .description(description)
                    .category(category)
                    .build();

            assertThat(serviceType.getName()).isEqualTo(name);
            assertThat(serviceType.getDescription()).isEqualTo(description);
            assertThat(serviceType.getCategory()).isEqualTo(category);
        }

        @Test
        @DisplayName("should allow modification of fields after creation")
        void shouldAllowModificationOfFieldsAfterCreation() {
            ServiceType serviceType = ServiceType.builder()
                    .name("Original")
                    .build();

            serviceType.setName("Updated");
            serviceType.setDescription("New Description");
            serviceType.setCategory("New Category");

            assertThat(serviceType.getName()).isEqualTo("Updated");
            assertThat(serviceType.getDescription()).isEqualTo("New Description");
            assertThat(serviceType.getCategory()).isEqualTo("New Category");
        }

        @Test
        @DisplayName("should update ID field correctly")
        void shouldUpdateIdFieldCorrectly() {
            ServiceType serviceType = new ServiceType();
            serviceType.setId(1L);

            assertThat(serviceType.getId()).isEqualTo(1L);

            serviceType.setId(2L);
            assertThat(serviceType.getId()).isEqualTo(2L);
        }
    }

    // ==================== ServiceType Validation Tests ====================

    @Nested
    @DisplayName("ServiceType Validation Tests")
    class ServiceTypeValidationTests {

        @Test
        @DisplayName("should pass validation for valid service type")
        void shouldPassValidationForValidServiceType() {
            ServiceType serviceType = ServiceType.builder()
                    .name("Restaurant")
                    .description("Food establishment")
                    .category("Food")
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with only required name field")
        void shouldPassValidationWithOnlyName() {
            ServiceType serviceType = ServiceType.builder()
                    .name("Test Service")
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t", "\n"})
        @DisplayName("should fail validation for blank name")
        void shouldFailValidationForBlankName(String name) {
            ServiceType serviceType = ServiceType.builder()
                    .name(name)
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("name"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for name shorter than 2 characters")
        void shouldFailValidationForShortName() {
            ServiceType serviceType = ServiceType.builder()
                    .name("A")
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("between 2 and 100"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for name longer than 100 characters")
        void shouldFailValidationForLongName() {
            String longName = "A".repeat(101);
            ServiceType serviceType = ServiceType.builder()
                    .name(longName)
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should pass validation for name with exactly 2 characters")
        void shouldPassValidationForMinLengthName() {
            ServiceType serviceType = ServiceType.builder()
                    .name("AB")
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
            assertThat(serviceType.getName()).hasSize(2);
        }

        @Test
        @DisplayName("should pass validation for name with exactly 100 characters")
        void shouldPassValidationForMaxLengthName() {
            String maxName = "A".repeat(100);
            ServiceType serviceType = ServiceType.builder()
                    .name(maxName)
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
            assertThat(serviceType.getName()).hasSize(100);
        }

        @Test
        @DisplayName("should fail validation for description longer than 500 characters")
        void shouldFailValidationForLongDescription() {
            String longDescription = "A".repeat(501);
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .description(longDescription)
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("cannot exceed 500"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for description with exactly 500 characters")
        void shouldPassValidationForMaxLengthDescription() {
            String maxDescription = "A".repeat(500);
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .description(maxDescription)
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
            assertThat(serviceType.getDescription()).hasSize(500);
        }

        @Test
        @DisplayName("should fail validation for category longer than 100 characters")
        void shouldFailValidationForLongCategory() {
            String longCategory = "A".repeat(101);
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .category(longCategory)
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("cannot exceed 100"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for category with exactly 100 characters")
        void shouldPassValidationForMaxLengthCategory() {
            String maxCategory = "A".repeat(100);
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .category(maxCategory)
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with null optional fields")
        void shouldPassValidationWithNullOptionalFields() {
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .description(null)
                    .category(null)
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle special characters in name")
        void shouldHandleSpecialCharactersInName() {
            ServiceType serviceType = ServiceType.builder()
                    .name("Cafe & Restaurant")
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle Polish characters in name")
        void shouldHandlePolishCharactersInName() {
            ServiceType serviceType = ServiceType.builder()
                    .name("Zolta Kawiarnia")
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle Unicode characters in description")
        void shouldHandleUnicodeCharactersInDescription() {
            ServiceType serviceType = ServiceType.builder()
                    .name("Test Service")
                    .description("Description with unicode chars")
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== ServiceTypeDto Tests ====================

    @Nested
    @DisplayName("ServiceTypeDto Tests")
    class ServiceTypeDtoTests {

        @Test
        @DisplayName("should create DTO with all fields")
        void shouldCreateDtoWithAllFields() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setId(1L);
            dto.setName("Restaurant");
            dto.setDescription("Food services");
            dto.setCategory("Food");

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Restaurant");
            assertThat(dto.getDescription()).isEqualTo("Food services");
            assertThat(dto.getCategory()).isEqualTo("Food");
        }

        @Test
        @DisplayName("should pass validation for valid DTO")
        void shouldPassValidationForValidDto() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("Restaurant");
            dto.setDescription("Food services");
            dto.setCategory("Food");

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);

            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t"})
        @DisplayName("should fail validation for blank name in DTO")
        void shouldFailValidationForBlankNameInDto(String name) {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName(name);

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for name shorter than 2 characters in DTO")
        void shouldFailValidationForShortNameInDto() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("A");

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for name longer than 100 characters in DTO")
        void shouldFailValidationForLongNameInDto() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("A".repeat(101));

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for description longer than 500 characters in DTO")
        void shouldFailValidationForLongDescriptionInDto() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("Valid Name");
            dto.setDescription("A".repeat(501));

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for category longer than 100 characters in DTO")
        void shouldFailValidationForLongCategoryInDto() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("Valid Name");
            dto.setCategory("A".repeat(101));

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should allow null optional fields in DTO")
        void shouldAllowNullOptionalFieldsInDto() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("Valid Name");
            dto.setDescription(null);
            dto.setCategory(null);

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should have null ID by default")
        void shouldHaveNullIdByDefault() {
            ServiceTypeDto dto = new ServiceTypeDto();

            assertThat(dto.getId()).isNull();
        }

        @Test
        @DisplayName("should pass validation for name with exactly 2 characters")
        void shouldPassValidationForMinLengthNameInDto() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("AB");

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation for name with exactly 100 characters")
        void shouldPassValidationForMaxLengthNameInDto() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("A".repeat(100));

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== ServiceTypeDtoOut Tests ====================

    @Nested
    @DisplayName("ServiceTypeDtoOut Tests")
    class ServiceTypeDtoOutTests {

        @Test
        @DisplayName("should create output DTO with builder")
        void shouldCreateOutputDtoWithBuilder() {
            ServiceTypeDtoOut dto = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Translated Name")
                    .originalName("Original Name")
                    .description("Translated Description")
                    .originalDescription("Original Description")
                    .category("Translated Category")
                    .originalCategory("Original Category")
                    .build();

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Translated Name");
            assertThat(dto.getOriginalName()).isEqualTo("Original Name");
            assertThat(dto.getDescription()).isEqualTo("Translated Description");
            assertThat(dto.getOriginalDescription()).isEqualTo("Original Description");
            assertThat(dto.getCategory()).isEqualTo("Translated Category");
            assertThat(dto.getOriginalCategory()).isEqualTo("Original Category");
        }

        @Test
        @DisplayName("should create output DTO with no-args constructor and setters")
        void shouldCreateOutputDtoWithNoArgsConstructorAndSetters() {
            ServiceTypeDtoOut dto = new ServiceTypeDtoOut();
            dto.setId(2L);
            dto.setName("Name");
            dto.setOriginalName("Original");
            dto.setDescription("Desc");
            dto.setOriginalDescription("Original Desc");
            dto.setCategory("Cat");
            dto.setOriginalCategory("Original Cat");

            assertThat(dto.getId()).isEqualTo(2L);
            assertThat(dto.getName()).isEqualTo("Name");
            assertThat(dto.getOriginalName()).isEqualTo("Original");
            assertThat(dto.getDescription()).isEqualTo("Desc");
            assertThat(dto.getOriginalDescription()).isEqualTo("Original Desc");
            assertThat(dto.getCategory()).isEqualTo("Cat");
            assertThat(dto.getOriginalCategory()).isEqualTo("Original Cat");
        }

        @Test
        @DisplayName("should create output DTO with all-args constructor")
        void shouldCreateOutputDtoWithAllArgsConstructor() {
            ServiceTypeDtoOut dto = new ServiceTypeDtoOut(
                    1L, "Name", "Original", "Desc", "OrigDesc", "Cat", "OrigCat"
            );

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Name");
            assertThat(dto.getOriginalName()).isEqualTo("Original");
            assertThat(dto.getDescription()).isEqualTo("Desc");
            assertThat(dto.getOriginalDescription()).isEqualTo("OrigDesc");
            assertThat(dto.getCategory()).isEqualTo("Cat");
            assertThat(dto.getOriginalCategory()).isEqualTo("OrigCat");
        }

        @Test
        @DisplayName("should have null fields with no-args constructor")
        void shouldHaveNullFieldsWithNoArgsConstructor() {
            ServiceTypeDtoOut dto = new ServiceTypeDtoOut();

            assertThat(dto.getId()).isNull();
            assertThat(dto.getName()).isNull();
            assertThat(dto.getOriginalName()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getOriginalDescription()).isNull();
            assertThat(dto.getCategory()).isNull();
            assertThat(dto.getOriginalCategory()).isNull();
        }

        @Test
        @DisplayName("should implement equals and hashCode correctly")
        void shouldImplementEqualsAndHashCodeCorrectly() {
            ServiceTypeDtoOut dto1 = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Test")
                    .build();
            ServiceTypeDtoOut dto2 = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Test")
                    .build();
            ServiceTypeDtoOut dto3 = ServiceTypeDtoOut.builder()
                    .id(2L)
                    .name("Other")
                    .build();

            assertThat(dto1).isEqualTo(dto2);
            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
            assertThat(dto1).isNotEqualTo(dto3);
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            ServiceTypeDtoOut dto = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Test")
                    .build();

            String result = dto.toString();

            assertThat(result).contains("ServiceTypeDtoOut");
            assertThat(result).contains("id=1");
            assertThat(result).contains("name=Test");
        }

        @Test
        @DisplayName("should not equal null")
        void shouldNotEqualNull() {
            ServiceTypeDtoOut dto = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Test")
                    .build();

            assertThat(dto).isNotEqualTo(null);
        }

        @Test
        @DisplayName("should not equal different class")
        void shouldNotEqualDifferentClass() {
            ServiceTypeDtoOut dto = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Test")
                    .build();

            assertThat(dto).isNotEqualTo("string");
        }

        @Test
        @DisplayName("should equal itself")
        void shouldEqualItself() {
            ServiceTypeDtoOut dto = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Test")
                    .build();

            assertThat(dto).isEqualTo(dto);
        }
    }

    // ==================== ServiceTypeConverter Tests ====================

    @Nested
    @DisplayName("ServiceTypeConverter Tests")
    class ServiceTypeConverterTests {

        @Mock
        private ServiceTypeRepository serviceTypeRepository;

        private ServiceTypeConverter converter;

        @BeforeEach
        void setUp() {
            converter = new ServiceTypeConverter(serviceTypeRepository);
        }

        @Test
        @DisplayName("should convert ID to ServiceType when found")
        void shouldConvertIdToServiceTypeWhenFound() {
            ServiceType serviceType = ServiceType.builder()
                    .id(1L)
                    .name("Restaurant")
                    .build();
            when(serviceTypeRepository.findById(1L)).thenReturn(Optional.of(serviceType));

            org.modelmapper.Converter<Long, ServiceType> modelMapperConverter = converter.toServiceTypeConverter();
            org.modelmapper.spi.MappingContext<Long, ServiceType> context = mock(org.modelmapper.spi.MappingContext.class);
            when(context.getSource()).thenReturn(1L);

            ServiceType result = modelMapperConverter.convert(context);

            assertThat(result).isEqualTo(serviceType);
            verify(serviceTypeRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw exception when ServiceType not found")
        void shouldThrowExceptionWhenServiceTypeNotFound() {
            when(serviceTypeRepository.findById(999L)).thenReturn(Optional.empty());

            org.modelmapper.Converter<Long, ServiceType> modelMapperConverter = converter.toServiceTypeConverter();
            org.modelmapper.spi.MappingContext<Long, ServiceType> context = mock(org.modelmapper.spi.MappingContext.class);
            when(context.getSource()).thenReturn(999L);

            assertThatThrownBy(() -> modelMapperConverter.convert(context))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ServiceType not found: 999");
        }

        @Test
        @DisplayName("should return converter from factory method")
        void shouldReturnConverterFromFactoryMethod() {
            org.modelmapper.Converter<Long, ServiceType> result = converter.toServiceTypeConverter();

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should call repository with correct ID")
        void shouldCallRepositoryWithCorrectId() {
            ServiceType serviceType = ServiceType.builder()
                    .id(42L)
                    .name("Test")
                    .build();
            when(serviceTypeRepository.findById(42L)).thenReturn(Optional.of(serviceType));

            org.modelmapper.Converter<Long, ServiceType> modelMapperConverter = converter.toServiceTypeConverter();
            org.modelmapper.spi.MappingContext<Long, ServiceType> context = mock(org.modelmapper.spi.MappingContext.class);
            when(context.getSource()).thenReturn(42L);

            modelMapperConverter.convert(context);

            verify(serviceTypeRepository).findById(42L);
        }
    }

    // ==================== ServiceTypeMapper Tests ====================

    @Nested
    @DisplayName("ServiceTypeMapper Tests")
    class ServiceTypeMapperTests {

        private ServiceTypeMapper mapper;

        @BeforeEach
        void setUp() {
            mapper = new ServiceTypeMapper();
        }

        @Test
        @DisplayName("should configure mapping to skip ID field")
        void shouldConfigureMappingToSkipIdField() {
            ModelMapper modelMapper = new ModelMapper();

            ModelMapper result = mapper.configureMapping(modelMapper);

            assertThat(result).isNotNull();
            TypeMap<ServiceTypeDto, ServiceType> typeMap = result.getTypeMap(ServiceTypeDto.class, ServiceType.class);
            assertThat(typeMap).isNotNull();
        }

        @Test
        @DisplayName("should map DTO to entity without ID")
        void shouldMapDtoToEntityWithoutId() {
            ModelMapper modelMapper = new ModelMapper();
            mapper.configureMapping(modelMapper);

            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setId(999L);
            dto.setName("Test");
            dto.setDescription("Description");
            dto.setCategory("Category");

            ServiceType entity = modelMapper.map(dto, ServiceType.class);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getName()).isEqualTo("Test");
            assertThat(entity.getDescription()).isEqualTo("Description");
            assertThat(entity.getCategory()).isEqualTo("Category");
        }

        @Test
        @DisplayName("should preserve existing entity ID when mapping from DTO")
        void shouldPreserveExistingEntityIdWhenMappingFromDto() {
            ModelMapper modelMapper = new ModelMapper();
            mapper.configureMapping(modelMapper);

            ServiceType existingEntity = ServiceType.builder()
                    .id(1L)
                    .name("Old Name")
                    .build();

            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setId(999L);
            dto.setName("New Name");
            dto.setDescription("New Description");

            modelMapper.map(dto, existingEntity);

            assertThat(existingEntity.getId()).isEqualTo(1L);
            assertThat(existingEntity.getName()).isEqualTo("New Name");
            assertThat(existingEntity.getDescription()).isEqualTo("New Description");
        }

        @Test
        @DisplayName("should return ModelMapper instance from configureMapping")
        void shouldReturnModelMapperInstanceFromConfigureMapping() {
            ModelMapper modelMapper = new ModelMapper();

            ModelMapper result = mapper.configureMapping(modelMapper);

            assertThat(result).isSameAs(modelMapper);
        }

        @Test
        @DisplayName("should handle null fields in DTO mapping")
        void shouldHandleNullFieldsInDtoMapping() {
            ModelMapper modelMapper = new ModelMapper();
            mapper.configureMapping(modelMapper);

            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("Test");
            dto.setDescription(null);
            dto.setCategory(null);

            ServiceType entity = modelMapper.map(dto, ServiceType.class);

            assertThat(entity.getName()).isEqualTo("Test");
            assertThat(entity.getDescription()).isNull();
            assertThat(entity.getCategory()).isNull();
        }

        @Test
        @DisplayName("should throw exception when configuring same mapping twice")
        void shouldThrowExceptionWhenConfiguringMappingTwice() {
            ModelMapper modelMapper = new ModelMapper();
            mapper.configureMapping(modelMapper);

            assertThatThrownBy(() -> mapper.configureMapping(modelMapper))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TypeMap already exists");
        }
    }

    // ==================== ServiceTypeController Tests (using reflection) ====================

    @Nested
    @DisplayName("ServiceTypeController Tests")
    class ServiceTypeControllerTests {

        @Mock
        private ServiceTypeService serviceTypeService;

        @Mock
        private TranslationService translationService;

        @Mock
        private HttpServletRequest request;

        @Mock
        private SecurityContext securityContext;

        @Mock
        private Authentication authentication;

        private ServiceTypeController controller;

        @BeforeEach
        void setUp() throws Exception {
            // Use reflection to access the protected constructor
            Constructor<ServiceTypeController> constructor = ServiceTypeController.class.getDeclaredConstructor(
                    ServiceTypeService.class, TranslationService.class, HttpServletRequest.class
            );
            constructor.setAccessible(true);
            controller = constructor.newInstance(serviceTypeService, translationService, request);

            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn("test-firebase-uid");
        }

        @Nested
        @DisplayName("getById Method Tests")
        class GetByIdTests {

            @Test
            @DisplayName("should return service type when found")
            void shouldReturnServiceTypeWhenFound() {
                ServiceType serviceType = ServiceType.builder()
                        .id(1L)
                        .name("Restaurant")
                        .description("Food service")
                        .category("Food")
                        .build();

                when(request.getHeader("Accept-Language")).thenReturn("pl");
                when(serviceTypeService.findById(1L)).thenReturn(serviceType);
                when(translationService.translateServiceType("Restaurant", Locale.forLanguageTag("pl")))
                        .thenReturn("Restauracja");
                when(translationService.getServiceTypeDescription("Restaurant", Locale.forLanguageTag("pl")))
                        .thenReturn("Usluga gastronomiczna");
                when(translationService.translateServiceCategory("Food", Locale.forLanguageTag("pl")))
                        .thenReturn("Jedzenie");

                ResponseEntity<ServiceTypeDtoOut> response = controller.getById(1L);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getId()).isEqualTo(1L);
                assertThat(response.getBody().getName()).isEqualTo("Restauracja");
                assertThat(response.getBody().getOriginalName()).isEqualTo("Restaurant");
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for null ID")
            void shouldThrowExceptionForNullId() {
                assertThatThrownBy(() -> controller.getById(null))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for zero ID")
            void shouldThrowExceptionForZeroId() {
                assertThatThrownBy(() -> controller.getById(0L))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for negative ID")
            void shouldThrowExceptionForNegativeId() {
                assertThatThrownBy(() -> controller.getById(-1L))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should use default Polish locale when Accept-Language is null")
            void shouldUseDefaultPolishLocaleWhenAcceptLanguageIsNull() {
                ServiceType serviceType = ServiceType.builder()
                        .id(1L)
                        .name("Restaurant")
                        .build();

                when(request.getHeader("Accept-Language")).thenReturn(null);
                when(serviceTypeService.findById(1L)).thenReturn(serviceType);
                when(translationService.translateServiceType(eq("Restaurant"), any(Locale.class)))
                        .thenReturn("Restauracja");

                controller.getById(1L);

                verify(translationService).translateServiceType("Restaurant", Locale.forLanguageTag("pl"));
            }

            @Test
            @DisplayName("should parse Accept-Language header correctly")
            void shouldParseAcceptLanguageHeaderCorrectly() {
                ServiceType serviceType = ServiceType.builder()
                        .id(1L)
                        .name("Restaurant")
                        .build();

                when(request.getHeader("Accept-Language")).thenReturn("en-US,en;q=0.9");
                when(serviceTypeService.findById(1L)).thenReturn(serviceType);
                when(translationService.translateServiceType(eq("Restaurant"), any(Locale.class)))
                        .thenReturn("Restaurant");

                controller.getById(1L);

                verify(translationService).translateServiceType("Restaurant", Locale.forLanguageTag("en-US"));
            }

            @Test
            @DisplayName("should handle null description correctly")
            void shouldHandleNullDescriptionCorrectly() {
                ServiceType serviceType = ServiceType.builder()
                        .id(1L)
                        .name("Restaurant")
                        .description(null)
                        .build();

                when(request.getHeader("Accept-Language")).thenReturn("pl");
                when(serviceTypeService.findById(1L)).thenReturn(serviceType);
                when(translationService.translateServiceType("Restaurant", Locale.forLanguageTag("pl")))
                        .thenReturn("Restauracja");

                ResponseEntity<ServiceTypeDtoOut> response = controller.getById(1L);

                assertThat(response.getBody().getDescription()).isNull();
                assertThat(response.getBody().getOriginalDescription()).isNull();
            }

            @Test
            @DisplayName("should fallback to original description when translation not found")
            void shouldFallbackToOriginalDescriptionWhenTranslationNotFound() {
                ServiceType serviceType = ServiceType.builder()
                        .id(1L)
                        .name("Restaurant")
                        .description("Original description")
                        .build();

                when(request.getHeader("Accept-Language")).thenReturn("pl");
                when(serviceTypeService.findById(1L)).thenReturn(serviceType);
                when(translationService.translateServiceType("Restaurant", Locale.forLanguageTag("pl")))
                        .thenReturn("Restauracja");
                when(translationService.getServiceTypeDescription("Restaurant", Locale.forLanguageTag("pl")))
                        .thenReturn(null);

                ResponseEntity<ServiceTypeDtoOut> response = controller.getById(1L);

                assertThat(response.getBody().getDescription()).isEqualTo("Original description");
            }

            @Test
            @DisplayName("should handle null category correctly")
            void shouldHandleNullCategoryCorrectly() {
                ServiceType serviceType = ServiceType.builder()
                        .id(1L)
                        .name("Restaurant")
                        .category(null)
                        .build();

                when(request.getHeader("Accept-Language")).thenReturn("pl");
                when(serviceTypeService.findById(1L)).thenReturn(serviceType);
                when(translationService.translateServiceType("Restaurant", Locale.forLanguageTag("pl")))
                        .thenReturn("Restauracja");

                ResponseEntity<ServiceTypeDtoOut> response = controller.getById(1L);

                assertThat(response.getBody().getCategory()).isNull();
                assertThat(response.getBody().getOriginalCategory()).isNull();
                verify(translationService, never()).translateServiceCategory(any(), any());
            }

            @Test
            @DisplayName("should handle empty Accept-Language header")
            void shouldHandleEmptyAcceptLanguageHeader() {
                ServiceType serviceType = ServiceType.builder()
                        .id(1L)
                        .name("Restaurant")
                        .build();

                when(request.getHeader("Accept-Language")).thenReturn("");
                when(serviceTypeService.findById(1L)).thenReturn(serviceType);
                when(translationService.translateServiceType(eq("Restaurant"), any(Locale.class)))
                        .thenReturn("Restauracja");

                controller.getById(1L);

                verify(translationService).translateServiceType("Restaurant", Locale.forLanguageTag("pl"));
            }
        }

        @Nested
        @DisplayName("findPaginated Method Tests")
        class FindPaginatedTests {

            @Test
            @DisplayName("should return paginated service types")
            void shouldReturnPaginatedServiceTypes() {
                ServiceType serviceType = ServiceType.builder()
                        .id(1L)
                        .name("Restaurant")
                        .description("Food service")
                        .category("Food")
                        .build();
                Page<ServiceType> page = new PageImpl<>(List.of(serviceType));
                Pageable pageable = PageRequest.of(0, 10);

                when(request.getHeader("Accept-Language")).thenReturn("pl");
                when(serviceTypeService.getDataPagedAndFiltered(eq(pageable), any())).thenReturn(page);
                when(translationService.translateServiceType("Restaurant", Locale.forLanguageTag("pl")))
                        .thenReturn("Restauracja");
                when(translationService.getServiceTypeDescription("Restaurant", Locale.forLanguageTag("pl")))
                        .thenReturn("Usluga gastronomiczna");
                when(translationService.translateServiceCategory("Food", Locale.forLanguageTag("pl")))
                        .thenReturn("Jedzenie");

                ResponseEntity<Page<ServiceTypeDtoOut>> response = controller.findPaginated(pageable, new HashMap<>());

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getTotalElements()).isEqualTo(1);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for page size exceeding 1000")
            void shouldThrowExceptionForPageSizeExceeding1000() {
                Pageable pageable = PageRequest.of(0, 1001);

                assertThatThrownBy(() -> controller.findPaginated(pageable, new HashMap<>()))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should remove pagination parameters from filters")
            void shouldRemovePaginationParametersFromFilters() {
                ServiceType serviceType = ServiceType.builder()
                        .id(1L)
                        .name("Restaurant")
                        .build();
                Page<ServiceType> page = new PageImpl<>(List.of(serviceType));
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();
                filters.put("page", "0");
                filters.put("size", "10");
                filters.put("sort", "name");
                filters.put("direction", "ASC");
                filters.put("name", "Restaurant");

                when(request.getHeader("Accept-Language")).thenReturn("pl");
                when(serviceTypeService.getDataPagedAndFiltered(eq(pageable), any())).thenReturn(page);
                when(translationService.translateServiceType(any(), any())).thenReturn("Restauracja");

                controller.findPaginated(pageable, filters);

                verify(serviceTypeService).getDataPagedAndFiltered(eq(pageable), argThat(f ->
                        !f.containsKey("page") &&
                        !f.containsKey("size") &&
                        !f.containsKey("sort") &&
                        !f.containsKey("direction") &&
                        f.containsKey("name")
                ));
            }

            @Test
            @DisplayName("should handle empty page result")
            void shouldHandleEmptyPageResult() {
                Page<ServiceType> emptyPage = new PageImpl<>(Collections.emptyList());
                Pageable pageable = PageRequest.of(0, 10);

                when(request.getHeader("Accept-Language")).thenReturn("pl");
                when(serviceTypeService.getDataPagedAndFiltered(eq(pageable), any())).thenReturn(emptyPage);

                ResponseEntity<Page<ServiceTypeDtoOut>> response = controller.findPaginated(pageable, new HashMap<>());

                assertThat(response.getBody().getTotalElements()).isZero();
                assertThat(response.getBody().getContent()).isEmpty();
            }

            @Test
            @DisplayName("should handle multiple service types in page")
            void shouldHandleMultipleServiceTypesInPage() {
                ServiceType serviceType1 = ServiceType.builder()
                        .id(1L)
                        .name("Restaurant")
                        .category("Food")
                        .build();
                ServiceType serviceType2 = ServiceType.builder()
                        .id(2L)
                        .name("Spa")
                        .category("Beauty")
                        .build();
                Page<ServiceType> page = new PageImpl<>(List.of(serviceType1, serviceType2));
                Pageable pageable = PageRequest.of(0, 10);

                when(request.getHeader("Accept-Language")).thenReturn("pl");
                when(serviceTypeService.getDataPagedAndFiltered(eq(pageable), any())).thenReturn(page);
                when(translationService.translateServiceType(any(), any())).thenReturn("Translated");
                when(translationService.translateServiceCategory(any(), any())).thenReturn("TranslatedCat");

                ResponseEntity<Page<ServiceTypeDtoOut>> response = controller.findPaginated(pageable, new HashMap<>());

                assertThat(response.getBody().getTotalElements()).isEqualTo(2);
                assertThat(response.getBody().getContent()).hasSize(2);
            }
        }

        @Nested
        @DisplayName("Authentication Tests")
        class AuthenticationTests {

            @Test
            @DisplayName("should throw AuthenticationTranslatableException when authentication is null")
            void shouldThrowExceptionWhenAuthenticationIsNull() {
                when(securityContext.getAuthentication()).thenReturn(null);

                assertThatThrownBy(() -> controller.getById(1L))
                        .isInstanceOf(AuthenticationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw AuthenticationTranslatableException when principal is null")
            void shouldThrowExceptionWhenPrincipalIsNull() {
                when(authentication.getPrincipal()).thenReturn(null);

                assertThatThrownBy(() -> controller.getById(1L))
                        .isInstanceOf(AuthenticationTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("getService Method Tests")
        class GetServiceTests {

            @Test
            @DisplayName("should return service type service")
            void shouldReturnServiceTypeService() throws Exception {
                Method getServiceMethod = ServiceTypeController.class.getDeclaredMethod("getService");
                getServiceMethod.setAccessible(true);

                Object result = getServiceMethod.invoke(controller);

                assertThat(result).isEqualTo(serviceTypeService);
            }
        }
    }

    // ==================== Edge Cases and Boundary Tests ====================

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle empty strings in optional fields")
        void shouldHandleEmptyStringsInOptionalFields() {
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .description("")
                    .category("")
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle whitespace-only description")
        void shouldHandleWhitespaceOnlyDescription() {
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .description("   ")
                    .category("   ")
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle maximum Long value for ID")
        void shouldHandleMaximumLongValueForId() {
            ServiceType serviceType = ServiceType.builder()
                    .id(Long.MAX_VALUE)
                    .name("Test")
                    .build();

            assertThat(serviceType.getId()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("should handle multiline description")
        void shouldHandleMultilineDescription() {
            String multilineDescription = "Line 1\nLine 2\nLine 3";
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .description(multilineDescription)
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
            assertThat(serviceType.getDescription()).contains("\n");
        }

        @Test
        @DisplayName("should handle tab characters in description")
        void shouldHandleTabCharactersInDescription() {
            String descriptionWithTabs = "Column1\tColumn2\tColumn3";
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .description(descriptionWithTabs)
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle minimum Long value for ID")
        void shouldHandleMinimumLongValueForId() {
            ServiceType serviceType = ServiceType.builder()
                    .id(Long.MIN_VALUE)
                    .name("Test")
                    .build();

            assertThat(serviceType.getId()).isEqualTo(Long.MIN_VALUE);
        }

        @Test
        @DisplayName("should handle carriage return in description")
        void shouldHandleCarriageReturnInDescription() {
            String descriptionWithCr = "Line 1\r\nLine 2";
            ServiceType serviceType = ServiceType.builder()
                    .name("Valid Name")
                    .description(descriptionWithCr)
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }
    }

    // ==================== Builder Pattern Tests ====================

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("should create entity with minimal fields using builder")
        void shouldCreateEntityWithMinimalFieldsUsingBuilder() {
            ServiceType serviceType = ServiceType.builder()
                    .name("Test")
                    .build();

            assertThat(serviceType.getName()).isEqualTo("Test");
            assertThat(serviceType.getId()).isNull();
            assertThat(serviceType.getDescription()).isNull();
            assertThat(serviceType.getCategory()).isNull();
        }

        @Test
        @DisplayName("should allow method chaining with builder")
        void shouldAllowMethodChainingWithBuilder() {
            ServiceType serviceType = ServiceType.builder()
                    .id(1L)
                    .name("Test")
                    .description("Desc")
                    .category("Cat")
                    .build();

            assertThat(serviceType).isNotNull();
        }

        @Test
        @DisplayName("should create DTO out with builder method chaining")
        void shouldCreateDtoOutWithBuilderMethodChaining() {
            ServiceTypeDtoOut dto = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Name")
                    .originalName("Original")
                    .description("Desc")
                    .originalDescription("OrigDesc")
                    .category("Cat")
                    .originalCategory("OrigCat")
                    .build();

            assertThat(dto).isNotNull();
            assertThat(dto.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should create empty entity with builder")
        void shouldCreateEmptyEntityWithBuilder() {
            ServiceType serviceType = ServiceType.builder().build();

            assertThat(serviceType).isNotNull();
            assertThat(serviceType.getId()).isNull();
            assertThat(serviceType.getName()).isNull();
        }
    }

    // ==================== Lombok Features Tests ====================

    @Nested
    @DisplayName("Lombok Features Tests")
    class LombokFeaturesTests {

        @Test
        @DisplayName("ServiceTypeDtoOut should have equals implementation")
        void serviceTypeDtoOutShouldHaveEqualsImplementation() {
            ServiceTypeDtoOut dto1 = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Test")
                    .build();
            ServiceTypeDtoOut dto2 = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Test")
                    .build();

            assertThat(dto1).isEqualTo(dto2);
        }

        @Test
        @DisplayName("ServiceTypeDtoOut should have hashCode implementation")
        void serviceTypeDtoOutShouldHaveHashCodeImplementation() {
            ServiceTypeDtoOut dto1 = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Test")
                    .build();
            ServiceTypeDtoOut dto2 = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Test")
                    .build();

            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }

        @Test
        @DisplayName("ServiceTypeDtoOut should have toString implementation")
        void serviceTypeDtoOutShouldHaveToStringImplementation() {
            ServiceTypeDtoOut dto = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Test")
                    .build();

            assertThat(dto.toString()).isNotNull();
            assertThat(dto.toString()).contains("1");
            assertThat(dto.toString()).contains("Test");
        }

        @Test
        @DisplayName("ServiceTypeDto should have working getters and setters")
        void serviceTypeDtoShouldHaveWorkingGettersAndSetters() {
            ServiceTypeDto dto = new ServiceTypeDto();

            dto.setId(1L);
            dto.setName("Name");
            dto.setDescription("Desc");
            dto.setCategory("Cat");

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Name");
            assertThat(dto.getDescription()).isEqualTo("Desc");
            assertThat(dto.getCategory()).isEqualTo("Cat");
        }

        @Test
        @DisplayName("ServiceType entity should have working getters")
        void serviceTypeEntityShouldHaveWorkingGetters() {
            ServiceType entity = new ServiceType(1L, "Name", "Desc", "Cat");

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getName()).isEqualTo("Name");
            assertThat(entity.getDescription()).isEqualTo("Desc");
            assertThat(entity.getCategory()).isEqualTo("Cat");
        }

        @Test
        @DisplayName("ServiceType entity should have working setters")
        void serviceTypeEntityShouldHaveWorkingSetters() {
            ServiceType entity = new ServiceType();
            entity.setId(1L);
            entity.setName("Name");
            entity.setDescription("Desc");
            entity.setCategory("Cat");

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getName()).isEqualTo("Name");
            assertThat(entity.getDescription()).isEqualTo("Desc");
            assertThat(entity.getCategory()).isEqualTo("Cat");
        }
    }

    // ==================== Integration-like Tests ====================

    @Nested
    @DisplayName("Mapping Integration Tests")
    class MappingIntegrationTests {

        @Test
        @DisplayName("should map entity to output DTO correctly")
        void shouldMapEntityToOutputDtoCorrectly() {
            ServiceType entity = ServiceType.builder()
                    .id(1L)
                    .name("Restaurant")
                    .description("Food service")
                    .category("Food")
                    .build();

            ServiceTypeDtoOut dto = ServiceTypeDtoOut.builder()
                    .id(entity.getId())
                    .name(entity.getName())
                    .originalName(entity.getName())
                    .description(entity.getDescription())
                    .originalDescription(entity.getDescription())
                    .category(entity.getCategory())
                    .originalCategory(entity.getCategory())
                    .build();

            assertThat(dto.getId()).isEqualTo(entity.getId());
            assertThat(dto.getName()).isEqualTo(entity.getName());
            assertThat(dto.getOriginalName()).isEqualTo(entity.getName());
        }

        @Test
        @DisplayName("should handle ModelMapper configuration for DTO to entity")
        void shouldHandleModelMapperConfigurationForDtoToEntity() {
            ModelMapper modelMapper = new ModelMapper();
            ServiceTypeMapper mapper = new ServiceTypeMapper();
            mapper.configureMapping(modelMapper);

            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setId(100L);
            dto.setName("Test");
            dto.setDescription("Description");
            dto.setCategory("Category");

            ServiceType entity = modelMapper.map(dto, ServiceType.class);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getName()).isEqualTo("Test");
        }

        @Test
        @DisplayName("should map DTO to existing entity correctly")
        void shouldMapDtoToExistingEntityCorrectly() {
            ModelMapper modelMapper = new ModelMapper();
            ServiceTypeMapper mapper = new ServiceTypeMapper();
            mapper.configureMapping(modelMapper);

            ServiceType existingEntity = ServiceType.builder()
                    .id(5L)
                    .name("Old")
                    .description("Old Description")
                    .category("Old Category")
                    .build();

            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setId(999L);
            dto.setName("New");
            dto.setDescription("New Description");
            dto.setCategory("New Category");

            modelMapper.map(dto, existingEntity);

            assertThat(existingEntity.getId()).isEqualTo(5L);
            assertThat(existingEntity.getName()).isEqualTo("New");
            assertThat(existingEntity.getDescription()).isEqualTo("New Description");
            assertThat(existingEntity.getCategory()).isEqualTo("New Category");
        }
    }

    // ==================== Additional Validation Edge Cases ====================

    @Nested
    @DisplayName("Additional Validation Edge Cases")
    class AdditionalValidationTests {

        @Test
        @DisplayName("should validate entity with all null fields except name")
        void shouldValidateEntityWithAllNullFieldsExceptName() {
            ServiceType serviceType = new ServiceType();
            serviceType.setName("Valid");

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should report all violations when multiple fields are invalid")
        void shouldReportAllViolationsWhenMultipleFieldsAreInvalid() {
            ServiceType serviceType = new ServiceType();
            serviceType.setName("A");
            serviceType.setDescription("A".repeat(501));
            serviceType.setCategory("A".repeat(101));

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should pass validation for name at boundary")
        void shouldPassValidationForNameAtBoundary() {
            ServiceType serviceType = ServiceType.builder()
                    .name("AB")
                    .build();

            Set<ConstraintViolation<ServiceType>> violations = validator.validate(serviceType);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should validate DTO with empty description")
        void shouldValidateDtoWithEmptyDescription() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("Valid");
            dto.setDescription("");

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);

            assertThat(violations).isEmpty();
        }
    }
}
