package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.city.CityDto;
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
 * Unit tests for City entity and DTOs.
 * Tests entity validation and basic behavior.
 */
@DisplayName("City Unit Tests")
class CityServiceUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== City Entity Tests ====================

    @Nested
    @DisplayName("City Entity")
    class CityEntityTests {

        @Test
        @DisplayName("should create valid city with all fields")
        void shouldCreateValidCity() {
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
            city.setName("Kraków");

            // Then - Default value from entity
            assertThat(city.getCountry()).isEqualTo("Polska");
        }

        @Test
        @DisplayName("should override toString to return name")
        void shouldReturnNameInToString() {
            // Given
            City city = new City();
            city.setName("Gdańsk");

            // When
            String result = city.toString();

            // Then
            assertThat(result).isEqualTo("Gdańsk");
        }

        @ParameterizedTest
        @CsvSource({
                "Warszawa, Mazowieckie",
                "Kraków, Małopolskie",
                "Wrocław, Dolnośląskie",
                "Poznań, Wielkopolskie",
                "Gdańsk, Pomorskie",
                "Łódź, Łódzkie"
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
    }

    // ==================== City Validation Tests ====================

    @Nested
    @DisplayName("City Validation")
    class CityValidationTests {

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
        @ValueSource(strings = {" ", "  ", "\t"})
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
    }

    // ==================== CityDto Tests ====================

    @Nested
    @DisplayName("CityDto")
    class CityDtoTests {

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
        @ValueSource(strings = {" ", "  "})
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
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle city with exactly 255 character name")
        void shouldHandleMaxLengthName() {
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
        @DisplayName("should handle city name with exactly 2 characters (minimum for DTO)")
        void shouldHandleMinLengthNameForDto() {
            // Given
            CityDto dto = new CityDto();
            dto.setName("AB");

            // When
            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle city name with exactly 50 characters (maximum for DTO)")
        void shouldHandleMaxLengthNameForDto() {
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
        @DisplayName("should handle Polish characters in city name")
        void shouldHandlePolishCharactersInCityName() {
            // Given
            City city = new City();
            city.setName("Łódź");
            city.setState("Łódzkie");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
            assertThat(city.getName()).isEqualTo("Łódź");
        }

        @Test
        @DisplayName("should handle city with all Polish diacritics")
        void shouldHandleAllPolishDiacritics() {
            // Given - city name with all Polish diacritics
            City city = new City();
            city.setName("Żółćęśąńź");
            city.setState("Świętokrzyskie");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle city with hyphenated name")
        void shouldHandleHyphenatedCityName() {
            // Given
            City city = new City();
            city.setName("Bielsko-Biała");

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
            city.setName("Zielona Góra");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    // ==================== Polish Voivodeships ====================

    @Nested
    @DisplayName("Polish Voivodeships")
    class PolishVoivodeshipsTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "Dolnośląskie",
                "Kujawsko-pomorskie",
                "Lubelskie",
                "Lubuskie",
                "Łódzkie",
                "Małopolskie",
                "Mazowieckie",
                "Opolskie",
                "Podkarpackie",
                "Podlaskie",
                "Pomorskie",
                "Śląskie",
                "Świętokrzyskie",
                "Warmińsko-mazurskie",
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

    // ==================== Major Polish Cities ====================

    @Nested
    @DisplayName("Major Polish Cities")
    class MajorPolishCitiesTests {

        @ParameterizedTest
        @CsvSource({
                "Warszawa, Mazowieckie",
                "Kraków, Małopolskie",
                "Łódź, Łódzkie",
                "Wrocław, Dolnośląskie",
                "Poznań, Wielkopolskie",
                "Gdańsk, Pomorskie",
                "Szczecin, Zachodniopomorskie",
                "Bydgoszcz, Kujawsko-pomorskie",
                "Lublin, Lubelskie",
                "Białystok, Podlaskie"
        })
        @DisplayName("should accept major Polish cities")
        void shouldAcceptMajorPolishCities(String cityName, String voivodeship) {
            // Given
            City city = new City();
            city.setName(cityName);
            city.setState(voivodeship);
            city.setCountry("Polska");

            // When
            Set<ConstraintViolation<City>> violations = validator.validate(city);

            // Then
            assertThat(violations).isEmpty();
        }
    }
}
