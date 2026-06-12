package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.city.*;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
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
import org.modelmapper.Converter;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for the City package.
 * Tests City entity, CityDto, and CityConverter.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("City Full Unit Tests")
class CityFullUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== City Entity Tests ====================

    @Nested
    @DisplayName("City Entity - Basic Operations")
    class CityEntityBasicTests {

        @Test
        @DisplayName("should create city with default constructor")
        void shouldCreateCityWithDefaultConstructor() {
            // Given
            City city = new City();

            // Then
            assertThat(city.getId()).isNull();
            assertThat(city.getName()).isNull();
            assertThat(city.getState()).isNull();
            assertThat(city.getCountry()).isEqualTo("Polska");
        }

        @Test
        @DisplayName("should set and get id")
        void shouldSetAndGetId() {
            // Given
            City city = new City();

            // When
            city.setId(123L);

            // Then
            assertThat(city.getId()).isEqualTo(123L);
        }

        @Test
        @DisplayName("should set and get name")
        void shouldSetAndGetName() {
            // Given
            City city = new City();

            // When
            city.setName("Warszawa");

            // Then
            assertThat(city.getName()).isEqualTo("Warszawa");
        }

        @Test
        @DisplayName("should set and get state")
        void shouldSetAndGetState() {
            // Given
            City city = new City();

            // When
            city.setState("Mazowieckie");

            // Then
            assertThat(city.getState()).isEqualTo("Mazowieckie");
        }

        @Test
        @DisplayName("should set and get country")
        void shouldSetAndGetCountry() {
            // Given
            City city = new City();

            // When
            city.setCountry("Germany");

            // Then
            assertThat(city.getCountry()).isEqualTo("Germany");
        }

        @Test
        @DisplayName("should create valid city with all fields")
        void shouldCreateValidCityWithAllFields() {
            // Given
            City city = new City();
            city.setId(1L);
            city.setName("Warszawa");
            city.setState("Mazowieckie");
            city.setCountry("Polska");

            // Then
            assertThat(city.getId()).isEqualTo(1L);
            assertThat(city.getName()).isEqualTo("Warszawa");
            assertThat(city.getState()).isEqualTo("Mazowieckie");
            assertThat(city.getCountry()).isEqualTo("Polska");
        }

        @Test
        @DisplayName("should have default country as Polska")
        void shouldHaveDefaultCountry() {
            // Given
            City city = new City();
            city.setName("Krakow");

            // Then
            assertThat(city.getCountry()).isEqualTo("Polska");
        }

        @Test
        @DisplayName("should override toString to return name")
        void shouldReturnNameInToString() {
            // Given
            City city = new City();
            city.setName("Gdansk");

            // When
            String result = city.toString();

            // Then
            assertThat(result).isEqualTo("Gdansk");
        }

        @Test
        @DisplayName("should return null in toString when name is null")
        void shouldReturnNullInToStringWhenNameIsNull() {
            // Given
            City city = new City();

            // When
            String result = city.toString();

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle large id values")
        void shouldHandleLargeIdValues() {
            // Given
            City city = new City();

            // When
            city.setId(Long.MAX_VALUE);

            // Then
            assertThat(city.getId()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("should handle zero id")
        void shouldHandleZeroId() {
            // Given
            City city = new City();

            // When
            city.setId(0L);

            // Then
            assertThat(city.getId()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("City Entity - Validation")
    class CityEntityValidationTests {

        @Test
        @DisplayName("should pass validation for valid city")
        void shouldPassValidationForValidCity() {
            // Given
            City city = new City();
            city.setName("Warszawa");
            city.setState("Mazowieckie");
            city.setCountry("Polska");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t", "\n"})
        @DisplayName("should fail validation for blank name")
        void shouldFailValidationForBlankName(String name) {
            // Given
            City city = new City();
            city.setName(name);

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

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
            City city = new City();
            city.setName(longName);

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("255"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for name with exactly 255 characters")
        void shouldPassValidationForMaxLengthName() {
            // Given
            String maxName = "A".repeat(255);
            City city = new City();
            city.setName(maxName);

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
            assertThat(city.getName()).hasSize(255);
        }

        @Test
        @DisplayName("should fail validation for state longer than 255 characters")
        void shouldFailValidationForLongState() {
            // Given
            String longState = "A".repeat(256);
            City city = new City();
            city.setName("Test");
            city.setState(longState);

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("State"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for state with exactly 255 characters")
        void shouldPassValidationForMaxLengthState() {
            // Given
            String maxState = "A".repeat(255);
            City city = new City();
            city.setName("Test");
            city.setState(maxState);

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation for country longer than 255 characters")
        void shouldFailValidationForLongCountry() {
            // Given
            String longCountry = "A".repeat(256);
            City city = new City();
            city.setName("Test");
            city.setCountry(longCountry);

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Country"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation with null state")
        void shouldPassValidationWithNullState() {
            // Given
            City city = new City();
            city.setName("Test");
            city.setState(null);

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with empty state")
        void shouldPassValidationWithEmptyState() {
            // Given
            City city = new City();
            city.setName("Test");
            city.setState("");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation for country with exactly 255 characters")
        void shouldPassValidationForMaxLengthCountry() {
            // Given
            String maxCountry = "A".repeat(255);
            City city = new City();
            city.setName("Test");
            city.setCountry(maxCountry);

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("City Entity - Polish Cities")
    class CityEntityPolishCitiesTests {

        @ParameterizedTest
        @CsvSource({
                "Warszawa, Mazowieckie",
                "Krakow, Malopolskie",
                "Wroclaw, Dolnoslaskie",
                "Poznan, Wielkopolskie",
                "Gdansk, Pomorskie",
                "Lodz, Lodzkie",
                "Szczecin, Zachodniopomorskie",
                "Bydgoszcz, Kujawsko-pomorskie",
                "Lublin, Lubelskie",
                "Katowice, Slaskie"
        })
        @DisplayName("should store various Polish cities")
        void shouldStoreVariousPolishCities(String name, String state) {
            // Given
            City city = new City();
            city.setName(name);
            city.setState(state);

            // Then
            assertThat(city.getName()).isEqualTo(name);
            assertThat(city.getState()).isEqualTo(state);
        }

        @Test
        @DisplayName("should handle Polish diacritics in city name")
        void shouldHandlePolishDiacriticsInCityName() {
            // Given
            City city = new City();
            city.setName("Lodz");
            city.setState("Lodzkie");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
            assertThat(city.getName()).isEqualTo("Lodz");
        }

        @Test
        @DisplayName("should handle hyphenated city name")
        void shouldHandleHyphenatedCityName() {
            // Given
            City city = new City();
            city.setName("Bielsko-Biala");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle city with space in name")
        void shouldHandleCityWithSpaceInName() {
            // Given
            City city = new City();
            city.setName("Zielona Gora");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Dolnoslaskie",
                "Kujawsko-pomorskie",
                "Lubelskie",
                "Lubuskie",
                "Lodzkie",
                "Malopolskie",
                "Mazowieckie",
                "Opolskie",
                "Podkarpackie",
                "Podlaskie",
                "Pomorskie",
                "Slaskie",
                "Swietokrzyskie",
                "Warminsko-mazurskie",
                "Wielkopolskie",
                "Zachodniopomorskie"
        })
        @DisplayName("should accept all Polish voivodeships")
        void shouldAcceptAllPolishVoivodeships(String voivodeship) {
            // Given
            City city = new City();
            city.setName("Test City");
            city.setState(voivodeship);

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    // ==================== CityDto Tests ====================

    @Nested
    @DisplayName("CityDto - Basic Operations")
    class CityDtoBasicTests {

        @Test
        @DisplayName("should create DTO with default constructor")
        void shouldCreateDtoWithDefaultConstructor() {
            // Given
            CityDto dto = new CityDto();

            // Then
            assertThat(dto.getId()).isNull();
            assertThat(dto.getName()).isNull();
            assertThat(dto.getState()).isNull();
            assertThat(dto.getCountry()).isEqualTo("Polska");
        }

        @Test
        @DisplayName("should set and get id")
        void shouldSetAndGetId() {
            // Given
            CityDto dto = new CityDto();

            // When
            dto.setId(456L);

            // Then
            assertThat(dto.getId()).isEqualTo(456L);
        }

        @Test
        @DisplayName("should set and get name")
        void shouldSetAndGetName() {
            // Given
            CityDto dto = new CityDto();

            // When
            dto.setName("Krakow");

            // Then
            assertThat(dto.getName()).isEqualTo("Krakow");
        }

        @Test
        @DisplayName("should set and get state")
        void shouldSetAndGetState() {
            // Given
            CityDto dto = new CityDto();

            // When
            dto.setState("Malopolskie");

            // Then
            assertThat(dto.getState()).isEqualTo("Malopolskie");
        }

        @Test
        @DisplayName("should set and get country")
        void shouldSetAndGetCountry() {
            // Given
            CityDto dto = new CityDto();

            // When
            dto.setCountry("Germany");

            // Then
            assertThat(dto.getCountry()).isEqualTo("Germany");
        }

        @Test
        @DisplayName("should create DTO with all fields")
        void shouldCreateDtoWithAllFields() {
            // Given
            CityDto dto = new CityDto();
            dto.setId(1L);
            dto.setName("Warszawa");
            dto.setState("Mazowieckie");
            dto.setCountry("Polska");

            // Then
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Warszawa");
            assertThat(dto.getState()).isEqualTo("Mazowieckie");
            assertThat(dto.getCountry()).isEqualTo("Polska");
        }

        @Test
        @DisplayName("should have default country in DTO")
        void shouldHaveDefaultCountryInDto() {
            // Given
            CityDto dto = new CityDto();

            // Then
            assertThat(dto.getCountry()).isEqualTo("Polska");
        }

        @Test
        @DisplayName("should handle large id values in DTO")
        void shouldHandleLargeIdValuesInDto() {
            // Given
            CityDto dto = new CityDto();

            // When
            dto.setId(Long.MAX_VALUE);

            // Then
            assertThat(dto.getId()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("should handle zero id in DTO")
        void shouldHandleZeroIdInDto() {
            // Given
            CityDto dto = new CityDto();

            // When
            dto.setId(0L);

            // Then
            assertThat(dto.getId()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("CityDto - Validation")
    class CityDtoValidationTests {

        @Test
        @DisplayName("should pass validation for valid DTO")
        void shouldPassValidationForValidDto() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("Warszawa");
            dto.setState("Mazowieckie");

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t"})
        @DisplayName("should fail validation for blank name in DTO")
        void shouldFailValidationForBlankNameInDto(String name) {
            // Given
            CityDto dto = new CityDto();
            dto.setName(name);

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for name shorter than 2 characters")
        void shouldFailValidationForShortName() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("A");

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should pass validation for name with exactly 2 characters")
        void shouldPassValidationForMinLengthName() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("AB");

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation for name longer than 50 characters")
        void shouldFailValidationForLongNameInDto() {
            // Given
            String longName = "A".repeat(51);
            CityDto dto = new CityDto();
            dto.setName(longName);

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should pass validation for name with exactly 50 characters")
        void shouldPassValidationForMaxLengthNameInDto() {
            // Given
            String maxName = "A".repeat(50);
            CityDto dto = new CityDto();
            dto.setName(maxName);

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
            assertThat(dto.getName()).hasSize(50);
        }

        @Test
        @DisplayName("should fail validation for state longer than 255 characters in DTO")
        void shouldFailValidationForLongStateInDto() {
            // Given
            String longState = "A".repeat(256);
            CityDto dto = new CityDto();
            dto.setName("Test");
            dto.setState(longState);

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail validation for country longer than 255 characters in DTO")
        void shouldFailValidationForLongCountryInDto() {
            // Given
            String longCountry = "A".repeat(256);
            CityDto dto = new CityDto();
            dto.setName("Test");
            dto.setCountry(longCountry);

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should pass validation with null state in DTO")
        void shouldPassValidationWithNullStateInDto() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("Test City");
            dto.setState(null);

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with empty state in DTO")
        void shouldPassValidationWithEmptyStateInDto() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("Test City");
            dto.setState("");

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation for state with exactly 255 characters in DTO")
        void shouldPassValidationForMaxLengthStateInDto() {
            // Given
            String maxState = "A".repeat(255);
            CityDto dto = new CityDto();
            dto.setName("Test City");
            dto.setState(maxState);

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation for country with exactly 255 characters in DTO")
        void shouldPassValidationForMaxLengthCountryInDto() {
            // Given
            String maxCountry = "A".repeat(255);
            CityDto dto = new CityDto();
            dto.setName("Test City");
            dto.setCountry(maxCountry);

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    // ==================== CityConverter Tests ====================

    @Nested
    @DisplayName("CityConverter - ToCityConverter")
    class CityConverterToCityTests {

        @Mock
        private CityRepository cityRepository;

        @Test
        @DisplayName("should convert city name to city entity when found")
        void shouldConvertCityNameToCityEntityWhenFound() {
            // Given
            City expectedCity = new City();
            expectedCity.setId(1L);
            expectedCity.setName("Warszawa");
            expectedCity.setState("Mazowieckie");
            expectedCity.setCountry("Polska");

            when(cityRepository.findByName("Warszawa")).thenReturn(Optional.of(expectedCity));

            CityConverter converter = new CityConverter(cityRepository);
            Converter<String, City> toCityConverter = converter.toCityConverter();

            // When
            org.modelmapper.spi.MappingContext<String, City> ctx = mock(org.modelmapper.spi.MappingContext.class);
            when(ctx.getSource()).thenReturn("Warszawa");

            City result = toCityConverter.convert(ctx);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Warszawa");
            assertThat(result.getState()).isEqualTo("Mazowieckie");
            verify(cityRepository).findByName("Warszawa");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when city not found")
        void shouldThrowResourceNotFoundExceptionWhenCityNotFound() {
            // Given
            when(cityRepository.findByName("NonExistent")).thenReturn(Optional.empty());

            CityConverter converter = new CityConverter(cityRepository);
            Converter<String, City> toCityConverter = converter.toCityConverter();

            // When
            org.modelmapper.spi.MappingContext<String, City> ctx = mock(org.modelmapper.spi.MappingContext.class);
            when(ctx.getSource()).thenReturn("NonExistent");

            // Then
            assertThatThrownBy(() -> toCityConverter.convert(ctx))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(cityRepository).findByName("NonExistent");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for null city name")
        void shouldThrowIllegalArgumentExceptionForNullCityName() {
            // Given
            CityConverter converter = new CityConverter(cityRepository);
            Converter<String, City> toCityConverter = converter.toCityConverter();

            // When
            org.modelmapper.spi.MappingContext<String, City> ctx = mock(org.modelmapper.spi.MappingContext.class);
            when(ctx.getSource()).thenReturn(null);

            // Then
            assertThatThrownBy(() -> toCityConverter.convert(ctx))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("City must not be null or empty");
            verifyNoInteractions(cityRepository);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for empty city name")
        void shouldThrowIllegalArgumentExceptionForEmptyCityName() {
            // Given
            CityConverter converter = new CityConverter(cityRepository);
            Converter<String, City> toCityConverter = converter.toCityConverter();

            // When
            org.modelmapper.spi.MappingContext<String, City> ctx = mock(org.modelmapper.spi.MappingContext.class);
            when(ctx.getSource()).thenReturn("");

            // Then
            assertThatThrownBy(() -> toCityConverter.convert(ctx))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("City must not be null or empty");
            verifyNoInteractions(cityRepository);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for whitespace-only city name")
        void shouldThrowIllegalArgumentExceptionForWhitespaceCityName() {
            // Given
            CityConverter converter = new CityConverter(cityRepository);
            Converter<String, City> toCityConverter = converter.toCityConverter();

            // When
            org.modelmapper.spi.MappingContext<String, City> ctx = mock(org.modelmapper.spi.MappingContext.class);
            when(ctx.getSource()).thenReturn("   ");

            // Then
            assertThatThrownBy(() -> toCityConverter.convert(ctx))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("City must not be null or empty");
            verifyNoInteractions(cityRepository);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for tab-only city name")
        void shouldThrowIllegalArgumentExceptionForTabOnlyCityName() {
            // Given
            CityConverter converter = new CityConverter(cityRepository);
            Converter<String, City> toCityConverter = converter.toCityConverter();

            // When
            org.modelmapper.spi.MappingContext<String, City> ctx = mock(org.modelmapper.spi.MappingContext.class);
            when(ctx.getSource()).thenReturn("\t\t");

            // Then
            assertThatThrownBy(() -> toCityConverter.convert(ctx))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("City must not be null or empty");
            verifyNoInteractions(cityRepository);
        }
    }

    @Nested
    @DisplayName("CityConverter - ToCityNameConverter")
    class CityConverterToCityNameTests {

        @Mock
        private CityRepository cityRepository;

        @Test
        @DisplayName("should convert city entity to name")
        void shouldConvertCityEntityToName() {
            // Given
            City city = new City();
            city.setId(1L);
            city.setName("Krakow");

            CityConverter converter = new CityConverter(cityRepository);
            Converter<City, String> toCityNameConverter = converter.toCityNameConverter();

            // When
            org.modelmapper.spi.MappingContext<City, String> ctx = mock(org.modelmapper.spi.MappingContext.class);
            when(ctx.getSource()).thenReturn(city);

            String result = toCityNameConverter.convert(ctx);

            // Then
            assertThat(result).isEqualTo("Krakow");
        }

        @Test
        @DisplayName("should return null when city is null")
        void shouldReturnNullWhenCityIsNull() {
            // Given
            CityConverter converter = new CityConverter(cityRepository);
            Converter<City, String> toCityNameConverter = converter.toCityNameConverter();

            // When
            org.modelmapper.spi.MappingContext<City, String> ctx = mock(org.modelmapper.spi.MappingContext.class);
            when(ctx.getSource()).thenReturn(null);

            String result = toCityNameConverter.convert(ctx);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should convert city with null name to null")
        void shouldConvertCityWithNullNameToNull() {
            // Given
            City city = new City();
            city.setId(1L);
            city.setName(null);

            CityConverter converter = new CityConverter(cityRepository);
            Converter<City, String> toCityNameConverter = converter.toCityNameConverter();

            // When
            org.modelmapper.spi.MappingContext<City, String> ctx = mock(org.modelmapper.spi.MappingContext.class);
            when(ctx.getSource()).thenReturn(city);

            String result = toCityNameConverter.convert(ctx);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should convert city with long name correctly")
        void shouldConvertCityWithLongNameCorrectly() {
            // Given
            String longName = "A".repeat(255);
            City city = new City();
            city.setId(1L);
            city.setName(longName);

            CityConverter converter = new CityConverter(cityRepository);
            Converter<City, String> toCityNameConverter = converter.toCityNameConverter();

            // When
            org.modelmapper.spi.MappingContext<City, String> ctx = mock(org.modelmapper.spi.MappingContext.class);
            when(ctx.getSource()).thenReturn(city);

            String result = toCityNameConverter.convert(ctx);

            // Then
            assertThat(result).isEqualTo(longName);
            assertThat(result).hasSize(255);
        }

        @Test
        @DisplayName("should convert city with special characters in name")
        void shouldConvertCityWithSpecialCharactersInName() {
            // Given
            City city = new City();
            city.setId(1L);
            city.setName("Bielsko-Biala");

            CityConverter converter = new CityConverter(cityRepository);
            Converter<City, String> toCityNameConverter = converter.toCityNameConverter();

            // When
            org.modelmapper.spi.MappingContext<City, String> ctx = mock(org.modelmapper.spi.MappingContext.class);
            when(ctx.getSource()).thenReturn(city);

            String result = toCityNameConverter.convert(ctx);

            // Then
            assertThat(result).isEqualTo("Bielsko-Biala");
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle city entity with minimum valid name")
        void shouldHandleCityEntityWithMinimumValidName() {
            // Given - Entity doesn't have min size constraint like DTO
            City city = new City();
            city.setName("A");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle city DTO with boundary name length 2")
        void shouldHandleCityDtoWithBoundaryNameLength2() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("AB");

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
            assertThat(dto.getName()).hasSize(2);
        }

        @Test
        @DisplayName("should handle city DTO with boundary name length 50")
        void shouldHandleCityDtoWithBoundaryNameLength50() {
            // Given
            String maxName = "A".repeat(50);
            CityDto dto = new CityDto();
            dto.setName(maxName);

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
            assertThat(dto.getName()).hasSize(50);
        }

        @Test
        @DisplayName("should fail for city DTO with name length 1")
        void shouldFailForCityDtoWithNameLength1() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("X");

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should fail for city DTO with name length 51")
        void shouldFailForCityDtoWithNameLength51() {
            // Given
            String tooLongName = "A".repeat(51);
            CityDto dto = new CityDto();
            dto.setName(tooLongName);

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should handle entity with all null optional fields")
        void shouldHandleEntityWithAllNullOptionalFields() {
            // Given
            City city = new City();
            city.setName("Test");
            city.setId(null);
            city.setState(null);
            // Country has default value

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle DTO with all null optional fields")
        void shouldHandleDtoWithAllNullOptionalFields() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("Test");
            dto.setId(null);
            dto.setState(null);
            // Country has default value

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle city name with special characters")
        void shouldHandleCityNameWithSpecialCharacters() {
            // Given
            City city = new City();
            city.setName("Test-City'123");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle city name with unicode characters")
        void shouldHandleCityNameWithUnicodeCharacters() {
            // Given
            City city = new City();
            city.setName("Tokyo");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle city with numbers in name")
        void shouldHandleCityWithNumbersInName() {
            // Given
            City city = new City();
            city.setName("City123");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle city DTO with numbers in name")
        void shouldHandleCityDtoWithNumbersInName() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("City123");

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    // ==================== Null Safety Tests ====================

    @Nested
    @DisplayName("Null Safety Tests")
    class NullSafetyTests {

        @Test
        @DisplayName("should handle setting null values on entity")
        void shouldHandleSettingNullValuesOnEntity() {
            // Given
            City city = new City();
            city.setName("Test");
            city.setState("State");
            city.setCountry("Country");

            // When
            city.setName(null);
            city.setState(null);
            city.setCountry(null);

            // Then
            assertThat(city.getName()).isNull();
            assertThat(city.getState()).isNull();
            assertThat(city.getCountry()).isNull();
        }

        @Test
        @DisplayName("should handle setting null values on DTO")
        void shouldHandleSettingNullValuesOnDto() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("Test");
            dto.setState("State");
            dto.setCountry("Country");

            // When
            dto.setName(null);
            dto.setState(null);
            dto.setCountry(null);

            // Then
            assertThat(dto.getName()).isNull();
            assertThat(dto.getState()).isNull();
            assertThat(dto.getCountry()).isNull();
        }

        @Test
        @DisplayName("should handle id as null on new entity")
        void shouldHandleIdAsNullOnNewEntity() {
            // Given
            City city = new City();

            // Then
            assertThat(city.getId()).isNull();
        }

        @Test
        @DisplayName("should handle id as null on new DTO")
        void shouldHandleIdAsNullOnNewDto() {
            // Given
            CityDto dto = new CityDto();

            // Then
            assertThat(dto.getId()).isNull();
        }
    }

    // ==================== Validation Message Tests ====================

    @Nested
    @DisplayName("Validation Message Tests")
    class ValidationMessageTests {

        @Test
        @DisplayName("should have correct message for blank name in entity")
        void shouldHaveCorrectMessageForBlankNameInEntity() {
            // Given
            City city = new City();
            city.setName("");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("blank"))).isTrue();
        }

        @Test
        @DisplayName("should have correct message for name size in DTO")
        void shouldHaveCorrectMessageForNameSizeInDto() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("A");

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("should have constraint violation for null name in entity")
        void shouldHaveConstraintViolationForNullNameInEntity() {
            // Given
            City city = new City();
            city.setName(null);

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("name"))).isTrue();
        }

        @Test
        @DisplayName("should have constraint violation for null name in DTO")
        void shouldHaveConstraintViolationForNullNameInDto() {
            // Given
            CityDto dto = new CityDto();
            dto.setName(null);

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isNotEmpty();
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("name"))).isTrue();
        }
    }

    // ==================== Country Default Value Tests ====================

    @Nested
    @DisplayName("Country Default Value Tests")
    class CountryDefaultValueTests {

        @Test
        @DisplayName("should have Polska as default country in City entity")
        void shouldHavePolskaAsDefaultCountryInEntity() {
            // Given
            City city = new City();

            // Then
            assertThat(city.getCountry()).isEqualTo("Polska");
        }

        @Test
        @DisplayName("should have Polska as default country in CityDto")
        void shouldHavePolskaAsDefaultCountryInDto() {
            // Given
            CityDto dto = new CityDto();

            // Then
            assertThat(dto.getCountry()).isEqualTo("Polska");
        }

        @Test
        @DisplayName("should allow overriding default country in entity")
        void shouldAllowOverridingDefaultCountryInEntity() {
            // Given
            City city = new City();
            city.setName("Berlin");
            city.setCountry("Germany");

            // Then
            assertThat(city.getCountry()).isEqualTo("Germany");
        }

        @Test
        @DisplayName("should allow overriding default country in DTO")
        void shouldAllowOverridingDefaultCountryInDto() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("Berlin");
            dto.setCountry("Germany");

            // Then
            assertThat(dto.getCountry()).isEqualTo("Germany");
        }

        @Test
        @DisplayName("should preserve country when only name changes")
        void shouldPreserveCountryWhenOnlyNameChanges() {
            // Given
            City city = new City();
            city.setName("Warszawa");
            city.setCountry("Polska");

            // When
            city.setName("Krakow");

            // Then
            assertThat(city.getCountry()).isEqualTo("Polska");
        }

        @Test
        @DisplayName("should preserve country in DTO when only name changes")
        void shouldPreserveCountryInDtoWhenOnlyNameChanges() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("Warszawa");
            dto.setCountry("Polska");

            // When
            dto.setName("Krakow");

            // Then
            assertThat(dto.getCountry()).isEqualTo("Polska");
        }
    }

    // ==================== International Cities Tests ====================

    @Nested
    @DisplayName("International Cities Tests")
    class InternationalCitiesTests {

        @ParameterizedTest
        @CsvSource({
                "Berlin, Germany",
                "Paris, France",
                "London, United Kingdom",
                "New York, United States",
                "Tokyo, Japan",
                "Sydney, Australia",
                "Rome, Italy",
                "Madrid, Spain"
        })
        @DisplayName("should accept international cities")
        void shouldAcceptInternationalCities(String cityName, String country) {
            // Given
            City city = new City();
            city.setName(cityName);
            city.setCountry(country);

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
            assertThat(city.getName()).isEqualTo(cityName);
            assertThat(city.getCountry()).isEqualTo(country);
        }

        @ParameterizedTest
        @CsvSource({
                "Berlin, Germany",
                "Paris, France",
                "London, United Kingdom",
                "New York, United States",
                "Tokyo, Japan",
                "Sydney, Australia"
        })
        @DisplayName("should accept international cities in DTO")
        void shouldAcceptInternationalCitiesInDto(String cityName, String country) {
            // Given
            CityDto dto = new CityDto();
            dto.setName(cityName);
            dto.setCountry(country);

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
            assertThat(dto.getName()).isEqualTo(cityName);
            assertThat(dto.getCountry()).isEqualTo(country);
        }
    }
}
