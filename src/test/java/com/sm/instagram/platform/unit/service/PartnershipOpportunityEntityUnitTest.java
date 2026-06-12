package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.partnershipopportunities.CompensationType;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityPhoto;
import com.sm.instagram.platform.user.User;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for PartnershipOpportunity entity.
 * Tests validation constraints, @AssertTrue business rules, and bidirectional relationships.
 */
@DisplayName("PartnershipOpportunity Entity Unit Tests")
class PartnershipOpportunityEntityUnitTest {

    private static Validator validator;
    private PartnershipOpportunity opportunity;
    private City city;
    private Address address;
    private User company;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @BeforeEach
    void setUp() {
        city = new City();
        city.setId(1L);
        city.setName("Warszawa");

        address = new Address();
        address.setId(1L);

        company = new User();
        company.setId(1L);

        opportunity = createValidOpportunity();
    }

    private PartnershipOpportunity createValidOpportunity() {
        PartnershipOpportunity opp = new PartnershipOpportunity();
        opp.setName("Valid Partnership Name");
        opp.setTitle("Valid Partnership Title");
        opp.setCity(city);
        opp.setAddress(address);
        opp.setCompany(company);
        opp.setCompensationType(CompensationType.CASH);
        opp.setCompensationAmountMin(100);
        opp.setCompensationAmountMax(500);
        opp.setFollowersMin(1000);
        opp.setFollowersMax(10000);
        return opp;
    }

    // ==================== @AssertTrue isEndDateNotBeforeStartDate Tests ====================

    @Nested
    @DisplayName("isEndDateNotBeforeStartDate Business Rule")
    class EndDateNotBeforeStartDateTests {

        @Test
        @DisplayName("should return true when both dates are null")
        void shouldReturnTrueWhenBothDatesNull() {
            opportunity.setStartDate(null);
            opportunity.setEndDate(null);

            assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("End date must be after start date"))).isFalse();
        }

        @Test
        @DisplayName("should return true when only start date is set")
        void shouldReturnTrueWhenOnlyStartDateSet() {
            opportunity.setStartDate(LocalDateTime.now().plusDays(1));
            opportunity.setEndDate(null);

            assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();
        }

        @Test
        @DisplayName("should return true when only end date is set")
        void shouldReturnTrueWhenOnlyEndDateSet() {
            opportunity.setStartDate(null);
            opportunity.setEndDate(LocalDateTime.now().plusDays(1));

            assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();
        }

        @Test
        @DisplayName("should return true when end date equals start date")
        void shouldReturnTrueWhenEndDateEqualsStartDate() {
            LocalDateTime sameDate = LocalDateTime.now().plusDays(1);
            opportunity.setStartDate(sameDate);
            opportunity.setEndDate(sameDate);

            assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();
        }

        @Test
        @DisplayName("should return true when end date is after start date")
        void shouldReturnTrueWhenEndDateAfterStartDate() {
            LocalDateTime start = LocalDateTime.now().plusDays(1);
            opportunity.setStartDate(start);
            opportunity.setEndDate(start.plusDays(7));

            assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();
        }

        @Test
        @DisplayName("should return false when end date is before start date")
        void shouldReturnFalseWhenEndDateBeforeStartDate() {
            LocalDateTime start = LocalDateTime.now().plusDays(7);
            opportunity.setStartDate(start);
            opportunity.setEndDate(start.minusDays(1));

            assertThat(opportunity.isEndDateNotBeforeStartDate()).isFalse();
        }

        @Test
        @DisplayName("should produce validation violation when end date is before start date")
        void shouldProduceViolationWhenEndDateBeforeStartDate() {
            LocalDateTime start = LocalDateTime.now().plusDays(7);
            opportunity.setStartDate(start);
            opportunity.setEndDate(start.minusDays(1));

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("End date must be after start date"))).isTrue();
        }
    }

    // ==================== @AssertTrue isValidCompensationRange Tests ====================

    @Nested
    @DisplayName("isValidCompensationRange Business Rule")
    class ValidCompensationRangeTests {

        @Test
        @DisplayName("should return true when max >= min within limit")
        void shouldReturnTrueWhenMaxGreaterThanMin() {
            opportunity.setCompensationAmountMin(100);
            opportunity.setCompensationAmountMax(500);

            assertThat(opportunity.isValidCompensationRange()).isTrue();
        }

        @Test
        @DisplayName("should return true when max equals min")
        void shouldReturnTrueWhenMaxEqualsMin() {
            opportunity.setCompensationAmountMin(300);
            opportunity.setCompensationAmountMax(300);

            assertThat(opportunity.isValidCompensationRange()).isTrue();
        }

        @Test
        @DisplayName("should return true when max equals 1,000,000")
        void shouldReturnTrueWhenMaxEqualsLimit() {
            opportunity.setCompensationAmountMin(0);
            opportunity.setCompensationAmountMax(1_000_000);

            assertThat(opportunity.isValidCompensationRange()).isTrue();
        }

        @Test
        @DisplayName("should return false when max < min")
        void shouldReturnFalseWhenMaxLessThanMin() {
            opportunity.setCompensationAmountMin(500);
            opportunity.setCompensationAmountMax(100);

            assertThat(opportunity.isValidCompensationRange()).isFalse();
        }

        @Test
        @DisplayName("should return false when max exceeds 1,000,000")
        void shouldReturnFalseWhenMaxExceedsLimit() {
            opportunity.setCompensationAmountMin(0);
            opportunity.setCompensationAmountMax(1_000_001);

            assertThat(opportunity.isValidCompensationRange()).isFalse();
        }

        @Test
        @DisplayName("should produce validation violation when max < min")
        void shouldProduceViolationWhenMaxLessThanMin() {
            opportunity.setCompensationAmountMin(500);
            opportunity.setCompensationAmountMax(100);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Maximum compensation amount must be greater than or equal to minimum"))).isTrue();
        }

        @Test
        @DisplayName("should return true when both min and max are zero")
        void shouldReturnTrueWhenBothZero() {
            opportunity.setCompensationAmountMin(0);
            opportunity.setCompensationAmountMax(0);

            assertThat(opportunity.isValidCompensationRange()).isTrue();
        }
    }

    // ==================== @AssertTrue isValidFollowersRange Tests ====================

    @Nested
    @DisplayName("isValidFollowersRange Business Rule")
    class ValidFollowersRangeTests {

        @Test
        @DisplayName("should return true when max >= min")
        void shouldReturnTrueWhenMaxGreaterThanMin() {
            opportunity.setFollowersMin(1000);
            opportunity.setFollowersMax(10000);

            assertThat(opportunity.isValidFollowersRange()).isTrue();
        }

        @Test
        @DisplayName("should return true when max equals min")
        void shouldReturnTrueWhenMaxEqualsMin() {
            opportunity.setFollowersMin(5000);
            opportunity.setFollowersMax(5000);

            assertThat(opportunity.isValidFollowersRange()).isTrue();
        }

        @Test
        @DisplayName("should return false when max < min")
        void shouldReturnFalseWhenMaxLessThanMin() {
            opportunity.setFollowersMin(10000);
            opportunity.setFollowersMax(1000);

            assertThat(opportunity.isValidFollowersRange()).isFalse();
        }

        @Test
        @DisplayName("should produce validation violation when max < min")
        void shouldProduceViolationWhenMaxLessThanMin() {
            opportunity.setFollowersMin(10000);
            opportunity.setFollowersMax(1000);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Maximum followers amount must be greater than or equal to minimum"))).isTrue();
        }

        @Test
        @DisplayName("should return true when both min and max are zero")
        void shouldReturnTrueWhenBothZero() {
            opportunity.setFollowersMin(0);
            opportunity.setFollowersMax(0);

            assertThat(opportunity.isValidFollowersRange()).isTrue();
        }
    }

    // ==================== @NotBlank Validation Tests ====================

    @Nested
    @DisplayName("@NotBlank Validation")
    class NotBlankValidationTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("should fail validation for blank name")
        void shouldFailForBlankName(String name) {
            opportunity.setName(name);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("name"))).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("should fail validation for blank title")
        void shouldFailForBlankTitle(String title) {
            opportunity.setTitle(title);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("title"))).isTrue();
        }
    }

    // ==================== @NotNull Validation Tests ====================

    @Nested
    @DisplayName("@NotNull Validation")
    class NotNullValidationTests {

        @Test
        @DisplayName("should fail validation for null city")
        void shouldFailForNullCity() {
            opportunity.setCity(null);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("city"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for null company")
        void shouldFailForNullCompany() {
            opportunity.setCompany(null);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("company"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for null address")
        void shouldFailForNullAddress() {
            opportunity.setAddress(null);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("address"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for null compensation type")
        void shouldFailForNullCompensationType() {
            opportunity.setCompensationType(null);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("compensationType"))).isTrue();
        }
    }

    // ==================== @Size Constraint Tests ====================

    @Nested
    @DisplayName("@Size Constraints")
    class SizeConstraintTests {

        @Test
        @DisplayName("should fail validation for name exceeding 255 characters")
        void shouldFailForNameExceeding255Chars() {
            opportunity.setName("A".repeat(256));

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("name"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for name with exactly 255 characters")
        void shouldPassForNameWith255Chars() {
            opportunity.setName("A".repeat(255));

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("name") &&
                            v.getMessage().contains("exceed"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for title exceeding 255 characters")
        void shouldFailForTitleExceeding255Chars() {
            opportunity.setTitle("A".repeat(256));

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("title"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for details exceeding 2000 characters")
        void shouldFailForDetailsExceeding2000Chars() {
            opportunity.setDetails("A".repeat(2001));

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("details"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for details with exactly 2000 characters")
        void shouldPassForDetailsWith2000Chars() {
            opportunity.setDetails("A".repeat(2000));

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("details"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for requirements exceeding 2000 characters")
        void shouldFailForRequirementsExceeding2000Chars() {
            opportunity.setRequirements("A".repeat(2001));

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("requirements"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for compensationDescription exceeding 500 characters")
        void shouldFailForCompensationDescExceeding500Chars() {
            opportunity.setCompensationDescription("A".repeat(501));

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("compensationDescription"))).isTrue();
        }

        @Test
        @DisplayName("should pass validation for compensationDescription with exactly 500 characters")
        void shouldPassForCompensationDescWith500Chars() {
            opportunity.setCompensationDescription("A".repeat(500));

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("compensationDescription"))).isTrue();
        }

        @Test
        @DisplayName("should fail validation for updaterId exceeding 255 characters")
        void shouldFailForUpdaterIdExceeding255Chars() {
            opportunity.setUpdaterId("A".repeat(256));

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("updaterId"))).isTrue();
        }
    }

    // ==================== @Pattern and @Min/@Max Tests ====================

    @Nested
    @DisplayName("@Pattern and @Min/@Max Constraints")
    class PatternAndMinMaxTests {

        @ParameterizedTest
        @CsvSource({
                "abc123, true",
                "ABC123, true",
                "a1B2c3, true",
                "user-123, true",
                "user_123, true",
                "user.123, true",
                "user@123, false"
        })
        @DisplayName("should validate updaterId pattern correctly")
        void shouldValidateUpdaterIdPattern(String updaterId, boolean shouldPass) {
            opportunity.setUpdaterId(updaterId);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            boolean hasPatternViolation = violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("updaterId") &&
                            v.getMessage().contains("must contain only letters"));

            assertThat(hasPatternViolation).isEqualTo(!shouldPass);
        }

        @Test
        @DisplayName("should fail for negative compensationAmountMin")
        void shouldFailForNegativeCompensationAmountMin() {
            opportunity.setCompensationAmountMin(-1);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("compensationAmountMin"))).isTrue();
        }

        @Test
        @DisplayName("should fail for negative compensationAmountMax")
        void shouldFailForNegativeCompensationAmountMax() {
            opportunity.setCompensationAmountMax(-1);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("compensationAmountMax"))).isTrue();
        }

        @Test
        @DisplayName("should fail for negative followersMin")
        void shouldFailForNegativeFollowersMin() {
            opportunity.setFollowersMin(-1);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("followersMin"))).isTrue();
        }

        @Test
        @DisplayName("should fail for negative followersMax")
        void shouldFailForNegativeFollowersMax() {
            opportunity.setFollowersMax(-1);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("followersMax"))).isTrue();
        }
    }

    // ==================== setPhotos() Bidirectional Relationship Tests ====================

    @Nested
    @DisplayName("setPhotos() Bidirectional Relationship")
    class SetPhotosBidirectionalTests {

        @Test
        @DisplayName("should set photos and update bidirectional relationship")
        void shouldSetPhotosAndUpdateRelationship() {
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
        @DisplayName("should clear existing photos when setting new list")
        void shouldClearExistingPhotosWhenSettingNewList() {
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
            assertThat(newPhoto.getPartnershipOpportunity()).isEqualTo(opportunity);
        }

        @Test
        @DisplayName("should handle null input gracefully")
        void shouldHandleNullInput() {
            PartnershipOpportunityPhoto existingPhoto = new PartnershipOpportunityPhoto();
            existingPhoto.setUrl("https://example.com/existing.jpg");
            opportunity.getPhotos().add(existingPhoto);

            opportunity.setPhotos(null);

            assertThat(opportunity.getPhotos()).isEmpty();
        }

        @Test
        @DisplayName("should handle empty list input")
        void shouldHandleEmptyListInput() {
            PartnershipOpportunityPhoto existingPhoto = new PartnershipOpportunityPhoto();
            existingPhoto.setUrl("https://example.com/existing.jpg");
            opportunity.getPhotos().add(existingPhoto);

            opportunity.setPhotos(new ArrayList<>());

            assertThat(opportunity.getPhotos()).isEmpty();
        }

        @Test
        @DisplayName("should update all photos with correct parent reference")
        void shouldUpdateAllPhotosWithCorrectParentReference() {
            List<PartnershipOpportunityPhoto> photos = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto();
                photo.setUrl("https://example.com/photo" + i + ".jpg");
                photos.add(photo);
            }

            opportunity.setPhotos(photos);

            assertThat(opportunity.getPhotos()).hasSize(5);
            for (PartnershipOpportunityPhoto photo : opportunity.getPhotos()) {
                assertThat(photo.getPartnershipOpportunity()).isEqualTo(opportunity);
            }
        }
    }

    // ==================== Default Values Tests ====================

    @Nested
    @DisplayName("Default Values")
    class DefaultValuesTests {

        @Test
        @DisplayName("should have active as true by default")
        void shouldHaveActiveAsTrueByDefault() {
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

        @Test
        @DisplayName("should have empty appliedOpportunities list by default")
        void shouldHaveEmptyAppliedOpportunitiesListByDefault() {
            PartnershipOpportunity newOpportunity = new PartnershipOpportunity();
            assertThat(newOpportunity.getAppliedOpportunities()).isNotNull().isEmpty();
        }
    }

    // ==================== Valid Entity Tests ====================

    @Nested
    @DisplayName("Valid Entity")
    class ValidEntityTests {

        @Test
        @DisplayName("should pass validation for fully valid opportunity")
        void shouldPassValidationForValidOpportunity() {
            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with null optional fields")
        void shouldPassValidationWithNullOptionalFields() {
            opportunity.setDetails(null);
            opportunity.setRequirements(null);
            opportunity.setCompensationDescription(null);
            opportunity.setUpdaterId(null);
            opportunity.setCurrency(null);
            opportunity.setStartDate(null);
            opportunity.setEndDate(null);
            opportunity.setServiceType(null);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations).isEmpty();
        }
    }
}
