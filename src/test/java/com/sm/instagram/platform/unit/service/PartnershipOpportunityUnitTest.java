package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.partnershipopportunities.*;
import com.sm.instagram.platform.user.User;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Partnership Opportunity entities, DTOs, and enums.
 * Tests validation, helper methods, and business rules.
 */
@DisplayName("Partnership Opportunity Unit Tests")
class PartnershipOpportunityUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== CompensationType Enum Tests ====================

    @Nested
    @DisplayName("CompensationType Enum")
    class CompensationTypeEnumTests {

        @Test
        @DisplayName("should have exactly 2 values")
        void shouldHaveExactlyTwoValues() {
            assertThat(CompensationType.values()).hasSize(2);
        }

        @Test
        @DisplayName("should have CASH value")
        void shouldHaveCashValue() {
            assertThat(CompensationType.CASH).isNotNull();
            assertThat(CompensationType.CASH.name()).isEqualTo("CASH");
        }

        @Test
        @DisplayName("should have BARTER value")
        void shouldHaveBarterValue() {
            assertThat(CompensationType.BARTER).isNotNull();
            assertThat(CompensationType.BARTER.name()).isEqualTo("BARTER");
        }

        @ParameterizedTest
        @EnumSource(CompensationType.class)
        @DisplayName("should be able to valueOf all types")
        void shouldBeAbleToValueOfAllTypes(CompensationType type) {
            assertThat(CompensationType.valueOf(type.name())).isEqualTo(type);
        }

        @Nested
        @DisplayName("Value and Properties")
        class ValueAndPropertiesTests {

            @Test
            @DisplayName("CASH should have correct properties")
            void cashShouldHaveCorrectProperties() {
                CompensationType cash = CompensationType.CASH;
                assertThat(cash.getColorTheme()).isEqualTo("primary");
                assertThat(cash.getIcon()).isEqualTo("dollar-sign");
                assertThat(cash.getAliases()).containsExactly("money");
                assertThat(cash.getDefaultDescription()).isNotBlank();
            }

            @Test
            @DisplayName("BARTER should have correct properties")
            void barterShouldHaveCorrectProperties() {
                CompensationType barter = CompensationType.BARTER;
                assertThat(barter.getColorTheme()).isEqualTo("warning");
                assertThat(barter.getIcon()).isEqualTo("swap");
                assertThat(barter.getAliases()).containsExactly("trade", "exchange");
                assertThat(barter.getDefaultDescription()).isEqualTo("Wymiana barterowa");
            }

            @Test
            @DisplayName("getValue should return enum name")
            void getValueShouldReturnEnumName() {
                assertThat(CompensationType.CASH.getValue()).isEqualTo("CASH");
                assertThat(CompensationType.BARTER.getValue()).isEqualTo("BARTER");
            }
        }

        @Nested
        @DisplayName("fromString method")
        class FromStringTests {

            @ParameterizedTest
            @CsvSource({
                    "CASH, CASH",
                    "cash, CASH",
                    "Cash, CASH",
                    "BARTER, BARTER",
                    "barter, BARTER",
                    "Barter, BARTER"
            })
            @DisplayName("should parse various case formats")
            void shouldParseVariousCaseFormats(String input, CompensationType expected) {
                assertThat(CompensationType.fromString(input)).isEqualTo(expected);
            }

            @Test
            @DisplayName("should throw exception for invalid value")
            void shouldThrowExceptionForInvalidValue() {
                assertThatThrownBy(() -> CompensationType.fromString("INVALID"))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    // ==================== PartnershipOpportunity Entity Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunity Entity")
    class PartnershipOpportunityEntityTests {

        private PartnershipOpportunity opportunity;
        private City city;
        private Address address;
        private User company;

        @BeforeEach
        void setUp() {
            city = new City();
            city.setId(1L);
            city.setName("Warszawa");

            address = new Address();
            address.setId(1L);

            company = new User();
            company.setId(1L);

            opportunity = new PartnershipOpportunity();
            opportunity.setName("Test Partnership");
            opportunity.setCity(city);
            opportunity.setAddress(address);
            opportunity.setTitle("Amazing Partnership Opportunity");
            opportunity.setCompensationType(CompensationType.CASH);
            opportunity.setCompensationAmountMin(100);
            opportunity.setCompensationAmountMax(500);
            opportunity.setFollowersMin(1000);
            opportunity.setFollowersMax(10000);
            opportunity.setCompany(company);
        }

        @Nested
        @DisplayName("Validation")
        class ValidationTests {

            @Test
            @DisplayName("should pass validation for valid opportunity")
            void shouldPassValidationForValidOpportunity() {
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isEmpty();
            }

            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = {"   ", "\t"})
            @DisplayName("should fail validation for blank name")
            void shouldFailValidationForBlankName(String name) {
                opportunity.setName(name);
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
                assertThat(violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("name"))).isTrue();
            }

            @Test
            @DisplayName("should fail validation for name exceeding 255 characters")
            void shouldFailValidationForLongName() {
                opportunity.setName("A".repeat(256));
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = {"   ", "\t"})
            @DisplayName("should fail validation for blank title")
            void shouldFailValidationForBlankTitle(String title) {
                opportunity.setTitle(title);
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
                assertThat(violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("title"))).isTrue();
            }

            @Test
            @DisplayName("should fail validation for title exceeding 255 characters")
            void shouldFailValidationForLongTitle() {
                opportunity.setTitle("A".repeat(256));
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for details exceeding 2000 characters")
            void shouldFailValidationForLongDetails() {
                opportunity.setDetails("A".repeat(2001));
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should pass validation for details with exactly 2000 characters")
            void shouldPassValidationForMaxLengthDetails() {
                opportunity.setDetails("A".repeat(2000));
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isEmpty();
            }

            @Test
            @DisplayName("should fail validation for requirements exceeding 2000 characters")
            void shouldFailValidationForLongRequirements() {
                opportunity.setRequirements("A".repeat(2001));
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for null compensation type")
            void shouldFailValidationForNullCompensationType() {
                opportunity.setCompensationType(null);
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
                assertThat(violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("compensationType"))).isTrue();
            }

            @Test
            @DisplayName("should fail validation for null city")
            void shouldFailValidationForNullCity() {
                opportunity.setCity(null);
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for null address")
            void shouldFailValidationForNullAddress() {
                opportunity.setAddress(null);
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for null company")
            void shouldFailValidationForNullCompany() {
                opportunity.setCompany(null);
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for negative compensation amount min")
            void shouldFailValidationForNegativeCompensationAmountMin() {
                opportunity.setCompensationAmountMin(-1);
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for compensation amount min exceeding 1000000")
            void shouldFailValidationForCompensationAmountMinExceedingMax() {
                opportunity.setCompensationAmountMin(1_000_001);
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for negative compensation amount max")
            void shouldFailValidationForNegativeCompensationAmountMax() {
                opportunity.setCompensationAmountMax(-1);
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for negative followers min")
            void shouldFailValidationForNegativeFollowersMin() {
                opportunity.setFollowersMin(-1);
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for negative followers max")
            void shouldFailValidationForNegativeFollowersMax() {
                opportunity.setFollowersMax(-1);
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for compensation description exceeding 500 characters")
            void shouldFailValidationForLongCompensationDescription() {
                opportunity.setCompensationDescription("A".repeat(501));
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for updater ID with invalid characters")
            void shouldFailValidationForUpdaterIdWithInvalidCharacters() {
                opportunity.setUpdaterId("user@123"); // Contains @ (not allowed)
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
                assertThat(violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("updaterId"))).isTrue();
            }

            @Test
            @DisplayName("should pass validation for updater ID with alphanumeric characters only")
            void shouldPassValidationForValidUpdaterId() {
                opportunity.setUpdaterId("user123ABC");
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations.stream()
                        .noneMatch(v -> v.getPropertyPath().toString().equals("updaterId"))).isTrue();
            }

            @Test
            @DisplayName("should pass validation with null optional fields")
            void shouldPassValidationWithNullOptionalFields() {
                opportunity.setDetails(null);
                opportunity.setRequirements(null);
                opportunity.setCompensationDescription(null);
                opportunity.setUpdaterId(null);
                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isEmpty();
            }
        }

        @Nested
        @DisplayName("Business Rules (@AssertTrue)")
        class BusinessRuleTests {

            @Test
            @DisplayName("isEndDateNotBeforeStartDate should return true when both dates are null")
            void isEndDateNotBeforeStartDateShouldReturnTrueWhenBothNull() {
                opportunity.setStartDate(null);
                opportunity.setEndDate(null);
                assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();
            }

            @Test
            @DisplayName("isEndDateNotBeforeStartDate should return true when end date is null")
            void isEndDateNotBeforeStartDateShouldReturnTrueWhenEndDateNull() {
                opportunity.setStartDate(LocalDateTime.now());
                opportunity.setEndDate(null);
                assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();
            }

            @Test
            @DisplayName("isEndDateNotBeforeStartDate should return true when start date is null")
            void isEndDateNotBeforeStartDateShouldReturnTrueWhenStartDateNull() {
                opportunity.setStartDate(null);
                opportunity.setEndDate(LocalDateTime.now());
                assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();
            }

            @Test
            @DisplayName("isEndDateNotBeforeStartDate should return true when end date is after start date")
            void isEndDateNotBeforeStartDateShouldReturnTrueWhenEndAfterStart() {
                LocalDateTime now = LocalDateTime.now();
                opportunity.setStartDate(now);
                opportunity.setEndDate(now.plusDays(1));
                assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();
            }

            @Test
            @DisplayName("isEndDateNotBeforeStartDate should return true when dates are equal")
            void isEndDateNotBeforeStartDateShouldReturnTrueWhenEqual() {
                LocalDateTime now = LocalDateTime.now();
                opportunity.setStartDate(now);
                opportunity.setEndDate(now);
                assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();
            }

            @Test
            @DisplayName("isEndDateNotBeforeStartDate should return false when end date is before start date")
            void isEndDateNotBeforeStartDateShouldReturnFalseWhenEndBeforeStart() {
                LocalDateTime now = LocalDateTime.now();
                opportunity.setStartDate(now);
                opportunity.setEndDate(now.minusDays(1));
                assertThat(opportunity.isEndDateNotBeforeStartDate()).isFalse();
            }

            @Test
            @DisplayName("isValidCompensationRange should return true when max >= min")
            void isValidCompensationRangeShouldReturnTrueWhenValid() {
                opportunity.setCompensationAmountMin(100);
                opportunity.setCompensationAmountMax(500);
                assertThat(opportunity.isValidCompensationRange()).isTrue();
            }

            @Test
            @DisplayName("isValidCompensationRange should return true when max == min")
            void isValidCompensationRangeShouldReturnTrueWhenEqual() {
                opportunity.setCompensationAmountMin(100);
                opportunity.setCompensationAmountMax(100);
                assertThat(opportunity.isValidCompensationRange()).isTrue();
            }

            @Test
            @DisplayName("isValidCompensationRange should return false when max < min")
            void isValidCompensationRangeShouldReturnFalseWhenInvalid() {
                opportunity.setCompensationAmountMin(500);
                opportunity.setCompensationAmountMax(100);
                assertThat(opportunity.isValidCompensationRange()).isFalse();
            }

            @Test
            @DisplayName("isValidCompensationRange should return false when max exceeds 1000000")
            void isValidCompensationRangeShouldReturnFalseWhenMaxExceedsLimit() {
                opportunity.setCompensationAmountMin(100);
                opportunity.setCompensationAmountMax(1_000_001);
                assertThat(opportunity.isValidCompensationRange()).isFalse();
            }

            @Test
            @DisplayName("isValidFollowersRange should return true when max >= min")
            void isValidFollowersRangeShouldReturnTrueWhenValid() {
                opportunity.setFollowersMin(1000);
                opportunity.setFollowersMax(10000);
                assertThat(opportunity.isValidFollowersRange()).isTrue();
            }

            @Test
            @DisplayName("isValidFollowersRange should return true when max == min")
            void isValidFollowersRangeShouldReturnTrueWhenEqual() {
                opportunity.setFollowersMin(5000);
                opportunity.setFollowersMax(5000);
                assertThat(opportunity.isValidFollowersRange()).isTrue();
            }

            @Test
            @DisplayName("isValidFollowersRange should return false when max < min")
            void isValidFollowersRangeShouldReturnFalseWhenInvalid() {
                opportunity.setFollowersMin(10000);
                opportunity.setFollowersMax(1000);
                assertThat(opportunity.isValidFollowersRange()).isFalse();
            }
        }

        @Nested
        @DisplayName("Helper Methods")
        class HelperMethodTests {

            @Test
            @DisplayName("setPhotos should set photos and update bidirectional relationship")
            void setPhotosShouldSetPhotosAndUpdateRelationship() {
                PartnershipOpportunityPhoto photo1 = new PartnershipOpportunityPhoto();
                photo1.setUrl("https://example.com/photo1.jpg");
                PartnershipOpportunityPhoto photo2 = new PartnershipOpportunityPhoto();
                photo2.setUrl("https://example.com/photo2.jpg");

                List<PartnershipOpportunityPhoto> photos = new ArrayList<>();
                photos.add(photo1);
                photos.add(photo2);

                opportunity.setPhotos(photos);

                assertThat(opportunity.getPhotos()).hasSize(2);
                assertThat(photo1.getPartnershipOpportunity()).isEqualTo(opportunity);
                assertThat(photo2.getPartnershipOpportunity()).isEqualTo(opportunity);
            }

            @Test
            @DisplayName("setPhotos should clear existing photos and set new ones")
            void setPhotosShouldClearExistingPhotosAndSetNewOnes() {
                PartnershipOpportunityPhoto oldPhoto = new PartnershipOpportunityPhoto();
                oldPhoto.setUrl("https://example.com/old.jpg");
                opportunity.getPhotos().add(oldPhoto);

                PartnershipOpportunityPhoto newPhoto = new PartnershipOpportunityPhoto();
                newPhoto.setUrl("https://example.com/new.jpg");
                List<PartnershipOpportunityPhoto> newPhotos = new ArrayList<>();
                newPhotos.add(newPhoto);

                opportunity.setPhotos(newPhotos);

                assertThat(opportunity.getPhotos()).hasSize(1);
                assertThat(opportunity.getPhotos().get(0).getUrl()).isEqualTo("https://example.com/new.jpg");
            }

            @Test
            @DisplayName("setPhotos should handle null input")
            void setPhotosShouldHandleNullInput() {
                PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto();
                opportunity.getPhotos().add(photo);

                opportunity.setPhotos(null);

                assertThat(opportunity.getPhotos()).isEmpty();
            }

            @Test
            @DisplayName("setPhotos should handle empty list")
            void setPhotosShouldHandleEmptyList() {
                PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto();
                opportunity.getPhotos().add(photo);

                opportunity.setPhotos(new ArrayList<>());

                assertThat(opportunity.getPhotos()).isEmpty();
            }
        }

        @Nested
        @DisplayName("Default Values")
        class DefaultValueTests {

            @Test
            @DisplayName("should have default active as true")
            void shouldHaveDefaultActiveTrue() {
                PartnershipOpportunity newOpportunity = new PartnershipOpportunity();
                assertThat(newOpportunity.isActive()).isTrue();
            }

            @Test
            @DisplayName("should have empty photos list by default")
            void shouldHaveEmptyPhotosListByDefault() {
                PartnershipOpportunity newOpportunity = new PartnershipOpportunity();
                assertThat(newOpportunity.getPhotos()).isNotNull().isEmpty();
            }

            @Test
            @DisplayName("should have empty platforms set by default")
            void shouldHaveEmptyPlatformsSetByDefault() {
                PartnershipOpportunity newOpportunity = new PartnershipOpportunity();
                assertThat(newOpportunity.getPlatforms()).isNotNull().isEmpty();
            }

            @Test
            @DisplayName("should have empty contentTypes set by default")
            void shouldHaveEmptyContentTypesSetByDefault() {
                PartnershipOpportunity newOpportunity = new PartnershipOpportunity();
                assertThat(newOpportunity.getContentTypes()).isNotNull().isEmpty();
            }
        }
    }

    // ==================== PartnershipOpportunityPhoto Entity Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunityPhoto Entity")
    class PartnershipOpportunityPhotoEntityTests {

        private PartnershipOpportunityPhoto photo;

        @BeforeEach
        void setUp() {
            photo = new PartnershipOpportunityPhoto();
            photo.setUrl("https://example.com/photo.jpg");
            photo.setOrderNumber(0);
            photo.setIsCover(false);
        }

        @Nested
        @DisplayName("Validation")
        class ValidationTests {

            @Test
            @DisplayName("should pass validation for valid photo with HTTPS URL")
            void shouldPassValidationForValidPhoto() {
                Set<ConstraintViolation<PartnershipOpportunityPhoto>> violations = validator.validate(photo);
                assertThat(violations).isEmpty();
            }

            @Test
            @DisplayName("should fail validation for HTTP URL (non-HTTPS)")
            void shouldFailValidationForHttpUrl() {
                photo.setUrl("http://example.com/photo.jpg");
                Set<ConstraintViolation<PartnershipOpportunityPhoto>> violations = validator.validate(photo);
                assertThat(violations).isNotEmpty();
                assertThat(violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("url"))).isTrue();
            }

            @Test
            @DisplayName("should fail validation for URL exceeding 2048 characters")
            void shouldFailValidationForLongUrl() {
                photo.setUrl("https://example.com/" + "a".repeat(2030));
                Set<ConstraintViolation<PartnershipOpportunityPhoto>> violations = validator.validate(photo);
                assertThat(violations).isNotEmpty();
            }

            @ParameterizedTest
            @ValueSource(strings = {
                    "https://example.com/photo.jpg",
                    "https://cdn.example.com/images/photo.png",
                    "https://storage.googleapis.com/bucket/file.gif",
                    "https://s3.amazonaws.com/bucket/image.webp"
            })
            @DisplayName("should accept valid HTTPS URLs")
            void shouldAcceptValidHttpsUrls(String url) {
                photo.setUrl(url);
                Set<ConstraintViolation<PartnershipOpportunityPhoto>> violations = validator.validate(photo);
                assertThat(violations.stream()
                        .noneMatch(v -> v.getPropertyPath().toString().equals("url"))).isTrue();
            }

            @ParameterizedTest
            @ValueSource(strings = {
                    "http://example.com/photo.jpg",
                    "ftp://example.com/photo.jpg",
                    "file:///local/photo.jpg",
                    "example.com/photo.jpg",
                    "not-a-url"
            })
            @DisplayName("should reject invalid URLs")
            void shouldRejectInvalidUrls(String url) {
                photo.setUrl(url);
                Set<ConstraintViolation<PartnershipOpportunityPhoto>> violations = validator.validate(photo);
                assertThat(violations).isNotEmpty();
            }
        }

        @Nested
        @DisplayName("Setters and Getters")
        class SettersAndGettersTests {

            @Test
            @DisplayName("should set and get all fields")
            void shouldSetAndGetAllFields() {
                PartnershipOpportunity opportunity = new PartnershipOpportunity();
                opportunity.setId(1L);

                photo.setId(10L);
                photo.setPartnershipOpportunity(opportunity);
                photo.setUrl("https://example.com/image.png");
                photo.setOrderNumber(5);
                photo.setIsCover(true);

                assertThat(photo.getId()).isEqualTo(10L);
                assertThat(photo.getPartnershipOpportunity()).isEqualTo(opportunity);
                assertThat(photo.getUrl()).isEqualTo("https://example.com/image.png");
                assertThat(photo.getOrderNumber()).isEqualTo(5);
                assertThat(photo.getIsCover()).isTrue();
            }

            @Test
            @DisplayName("setEntity should set partnership opportunity")
            void setEntityShouldSetPartnershipOpportunity() {
                PartnershipOpportunity opportunity = new PartnershipOpportunity();
                opportunity.setId(1L);

                photo.setEntity(opportunity);

                assertThat(photo.getPartnershipOpportunity()).isEqualTo(opportunity);
            }
        }
    }

    // ==================== PartnershipOpportunityDtoIn Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunityDtoIn")
    class PartnershipOpportunityDtoInTests {

        private PartnershipOpportunityDtoIn dto;

        @BeforeEach
        void setUp() {
            dto = new PartnershipOpportunityDtoIn();
            dto.setName("Test Partnership");
            dto.setCity("Warszawa");
            dto.setTitle("Amazing Partnership");
            dto.setCompensationType(CompensationType.CASH);
            dto.setCompensationAmountMin(100);
            dto.setCompensationAmountMax(500);
            dto.setCompany(1L);
        }

        @Nested
        @DisplayName("Validation")
        class ValidationTests {

            @Test
            @DisplayName("should pass validation for valid DTO")
            void shouldPassValidationForValidDto() {
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }

            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = {"   ", "\t"})
            @DisplayName("should fail validation for blank name")
            void shouldFailValidationForBlankName(String name) {
                dto.setName(name);
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for name exceeding 255 characters")
            void shouldFailValidationForLongName() {
                dto.setName("A".repeat(256));
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = {"   ", "\t"})
            @DisplayName("should fail validation for blank city")
            void shouldFailValidationForBlankCity(String city) {
                dto.setCity(city);
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = {"   ", "\t"})
            @DisplayName("should fail validation for blank title")
            void shouldFailValidationForBlankTitle(String title) {
                dto.setTitle(title);
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for title exceeding 255 characters")
            void shouldFailValidationForLongTitle() {
                dto.setTitle("A".repeat(256));
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for details exceeding 2000 characters")
            void shouldFailValidationForLongDetails() {
                dto.setDetails("A".repeat(2001));
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for requirements exceeding 2000 characters")
            void shouldFailValidationForLongRequirements() {
                dto.setRequirements("A".repeat(2001));
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for negative compensation amount min")
            void shouldFailValidationForNegativeCompensationAmountMin() {
                dto.setCompensationAmountMin(-1);
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for compensation amount min exceeding 1000000")
            void shouldFailValidationForCompensationAmountMinExceedingMax() {
                dto.setCompensationAmountMin(1_000_001);
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for negative compensation amount max")
            void shouldFailValidationForNegativeCompensationAmountMax() {
                dto.setCompensationAmountMax(-1);
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for negative followers min")
            void shouldFailValidationForNegativeFollowersMin() {
                dto.setFollowersMin(-1);
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for negative followers max")
            void shouldFailValidationForNegativeFollowersMax() {
                dto.setFollowersMax(-1);
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for compensation description exceeding 500 characters")
            void shouldFailValidationForLongCompensationDescription() {
                dto.setCompensationDescription("A".repeat(501));
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for null company")
            void shouldFailValidationForNullCompany() {
                dto.setCompany(null);
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for photos list exceeding 6 items")
            void shouldFailValidationForTooManyPhotos() {
                List<PartnershipOpportunityPhotoDtoIn> photos = new ArrayList<>();
                for (int i = 0; i < 7; i++) {
                    PartnershipOpportunityPhotoDtoIn photo = new PartnershipOpportunityPhotoDtoIn();
                    photo.setUploadId("upload-" + i);
                    photo.setOrderNumber(i);
                    photo.setIsCover(i == 0);
                    photos.add(photo);
                }
                dto.setPhotos(photos);
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should pass validation for photos list with exactly 6 items")
            void shouldPassValidationForMaxPhotos() {
                List<PartnershipOpportunityPhotoDtoIn> photos = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    PartnershipOpportunityPhotoDtoIn photo = new PartnershipOpportunityPhotoDtoIn();
                    photo.setUploadId("upload-" + i);
                    photo.setOrderNumber(i);
                    photo.setIsCover(i == 0);
                    photos.add(photo);
                }
                dto.setPhotos(photos);
                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations.stream()
                        .noneMatch(v -> v.getPropertyPath().toString().equals("photos"))).isTrue();
            }
        }

        @Nested
        @DisplayName("Default Values")
        class DefaultValueTests {

            @Test
            @DisplayName("should have empty photos list by default")
            void shouldHaveEmptyPhotosListByDefault() {
                PartnershipOpportunityDtoIn newDto = new PartnershipOpportunityDtoIn();
                assertThat(newDto.getPhotos()).isNotNull().isEmpty();
            }
        }
    }

    // ==================== PartnershipOpportunityPhotoDtoIn Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunityPhotoDtoIn")
    class PartnershipOpportunityPhotoDtoInTests {

        private PartnershipOpportunityPhotoDtoIn dto;

        @BeforeEach
        void setUp() {
            // New photo shape: uploadId identifies the tracked upload; the URL
            // is BE-derived (pentest 3.1). No url field on the DTO anymore.
            dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setUploadId("upload-abc");
            dto.setOrderNumber(0);
            dto.setIsCover(false);
        }

        @Nested
        @DisplayName("Validation")
        class ValidationTests {

            @Test
            @DisplayName("should pass validation for a valid new-photo DTO")
            void shouldPassValidationForValidDto() {
                Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }

            @Test
            @DisplayName("should pass validation for an existing-photo DTO (id, no uploadId)")
            void shouldPassValidationForExistingPhoto() {
                dto.setUploadId(null);
                dto.setId(42L);
                Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }

            @Test
            @DisplayName("should fail validation for an over-long uploadId")
            void shouldFailValidationForOverLongUploadId() {
                dto.setUploadId("A".repeat(37));
                Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
                assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("uploadId"));
            }

            @Test
            @DisplayName("should fail validation for null orderNumber")
            void shouldFailValidationForNullOrderNumber() {
                dto.setOrderNumber(null);
                Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for negative orderNumber")
            void shouldFailValidationForNegativeOrderNumber() {
                dto.setOrderNumber(-1);
                Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should fail validation for null isCover")
            void shouldFailValidationForNullIsCover() {
                dto.setIsCover(null);
                Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }
        }

        @Nested
        @DisplayName("Setters and Getters")
        class SettersAndGettersTests {

            @Test
            @DisplayName("should set and get all fields")
            void shouldSetAndGetAllFields() {
                dto.setId(1L);
                dto.setUploadId("upload-xyz");
                dto.setOrderNumber(5);
                dto.setIsCover(true);

                assertThat(dto.getId()).isEqualTo(1L);
                assertThat(dto.getUploadId()).isEqualTo("upload-xyz");
                assertThat(dto.getOrderNumber()).isEqualTo(5);
                assertThat(dto.getIsCover()).isTrue();
            }
        }
    }

    // ==================== DTOs Out Tests ====================

    @Nested
    @DisplayName("CompensationTypeDtoOut")
    class CompensationTypeDtoOutTests {

        @Test
        @DisplayName("should create DTO with builder")
        void shouldCreateDtoWithBuilder() {
            CompensationTypeDtoOut dto = CompensationTypeDtoOut.builder()
                    .value("CASH")
                    .label("Platnosc gotowka")
                    .originalLabel("CASH")
                    .build();

            assertThat(dto.getValue()).isEqualTo("CASH");
            assertThat(dto.getLabel()).isEqualTo("Platnosc gotowka");
            assertThat(dto.getOriginalLabel()).isEqualTo("CASH");
        }

        @Test
        @DisplayName("should create DTO with no-args constructor")
        void shouldCreateDtoWithNoArgsConstructor() {
            CompensationTypeDtoOut dto = new CompensationTypeDtoOut();
            dto.setValue("BARTER");
            dto.setLabel("Wymiana barterowa");

            assertThat(dto.getValue()).isEqualTo("BARTER");
            assertThat(dto.getLabel()).isEqualTo("Wymiana barterowa");
        }

        @Test
        @DisplayName("should create DTO with all-args constructor")
        void shouldCreateDtoWithAllArgsConstructor() {
            CompensationTypeDtoOut dto = new CompensationTypeDtoOut("CASH", "Cash", "CASH");

            assertThat(dto.getValue()).isEqualTo("CASH");
            assertThat(dto.getLabel()).isEqualTo("Cash");
            assertThat(dto.getOriginalLabel()).isEqualTo("CASH");
        }
    }

    @Nested
    @DisplayName("PartnershipOpportunityPhotoDtoOut")
    class PartnershipOpportunityPhotoDtoOutTests {

        @Test
        @DisplayName("should set and get all fields")
        void shouldSetAndGetAllFields() {
            PartnershipOpportunityPhotoDtoOut dto = new PartnershipOpportunityPhotoDtoOut();
            dto.setId(1L);
            dto.setUrl("https://example.com/photo.jpg");
            dto.setOrderNumber(0);
            dto.setIsCover(true);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getUrl()).isEqualTo("https://example.com/photo.jpg");
            assertThat(dto.getOrderNumber()).isEqualTo(0);
            assertThat(dto.getIsCover()).isTrue();
        }
    }

    @Nested
    @DisplayName("PartnershipOpportunityDtoOut")
    class PartnershipOpportunityDtoOutTests {

        @Test
        @DisplayName("should have empty platforms set by default")
        void shouldHaveEmptyPlatformsSetByDefault() {
            PartnershipOpportunityDtoOut dto = new PartnershipOpportunityDtoOut();
            assertThat(dto.getPlatforms()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("should have empty contentTypes set by default")
        void shouldHaveEmptyContentTypesSetByDefault() {
            PartnershipOpportunityDtoOut dto = new PartnershipOpportunityDtoOut();
            assertThat(dto.getContentTypes()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("should set and get basic fields")
        void shouldSetAndGetBasicFields() {
            PartnershipOpportunityDtoOut dto = new PartnershipOpportunityDtoOut();
            dto.setId(1L);
            dto.setName("Test");
            dto.setTitle("Test Title");
            dto.setCity("Warszawa");
            dto.setFollowersMin(1000);
            dto.setFollowersMax(10000);
            dto.setCompensationAmountMin(100);
            dto.setCompensationAmountMax(500);
            dto.setActive(true);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Test");
            assertThat(dto.getTitle()).isEqualTo("Test Title");
            assertThat(dto.getCity()).isEqualTo("Warszawa");
            assertThat(dto.getFollowersMin()).isEqualTo(1000);
            assertThat(dto.getFollowersMax()).isEqualTo(10000);
            assertThat(dto.getCompensationAmountMin()).isEqualTo(100);
            assertThat(dto.getCompensationAmountMax()).isEqualTo(500);
            assertThat(dto.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("PartnershipOpportunitySimpleDtoOut")
    class PartnershipOpportunitySimpleDtoOutTests {

        @Test
        @DisplayName("should have empty platforms set by default")
        void shouldHaveEmptyPlatformsSetByDefault() {
            PartnershipOpportunitySimpleDtoOut dto = new PartnershipOpportunitySimpleDtoOut();
            assertThat(dto.getPlatforms()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("should have empty contentTypes set by default")
        void shouldHaveEmptyContentTypesSetByDefault() {
            PartnershipOpportunitySimpleDtoOut dto = new PartnershipOpportunitySimpleDtoOut();
            assertThat(dto.getContentTypes()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("should set and get basic fields")
        void shouldSetAndGetBasicFields() {
            PartnershipOpportunitySimpleDtoOut dto = new PartnershipOpportunitySimpleDtoOut();
            dto.setId(1L);
            dto.setName("Test");
            dto.setTitle("Test Title");
            dto.setActive(true);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Test");
            assertThat(dto.getTitle()).isEqualTo("Test Title");
            assertThat(dto.isActive()).isTrue();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle opportunity with zero compensation range")
        void shouldHandleOpportunityWithZeroCompensationRange() {
            PartnershipOpportunity opportunity = createValidOpportunity();
            opportunity.setCompensationAmountMin(0);
            opportunity.setCompensationAmountMax(0);

            assertThat(opportunity.isValidCompensationRange()).isTrue();
            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle opportunity with maximum compensation amount")
        void shouldHandleOpportunityWithMaximumCompensationAmount() {
            PartnershipOpportunity opportunity = createValidOpportunity();
            opportunity.setCompensationAmountMin(0);
            opportunity.setCompensationAmountMax(1_000_000);

            assertThat(opportunity.isValidCompensationRange()).isTrue();
            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle opportunity with very large followers range")
        void shouldHandleOpportunityWithVeryLargeFollowersRange() {
            PartnershipOpportunity opportunity = createValidOpportunity();
            opportunity.setFollowersMin(0);
            opportunity.setFollowersMax(Long.MAX_VALUE);

            assertThat(opportunity.isValidFollowersRange()).isTrue();
            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle Unicode characters in title and name")
        void shouldHandleUnicodeCharactersInTitleAndName() {
            PartnershipOpportunity opportunity = createValidOpportunity();
            opportunity.setName("Wspolpraca z influencerem");
            opportunity.setTitle("Niezwykla mozliwosc partnerstwa");
            opportunity.setDetails("Szczegoly wspolpracy z polskimi znakami: acenos");

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations).isEmpty();
        }

        private PartnershipOpportunity createValidOpportunity() {
            City city = new City();
            city.setId(1L);
            city.setName("Warszawa");

            Address address = new Address();
            address.setId(1L);

            User company = new User();
            company.setId(1L);

            PartnershipOpportunity opportunity = new PartnershipOpportunity();
            opportunity.setName("Test");
            opportunity.setCity(city);
            opportunity.setAddress(address);
            opportunity.setTitle("Test Title");
            opportunity.setCompensationType(CompensationType.CASH);
            opportunity.setCompany(company);
            return opportunity;
        }
    }
}
