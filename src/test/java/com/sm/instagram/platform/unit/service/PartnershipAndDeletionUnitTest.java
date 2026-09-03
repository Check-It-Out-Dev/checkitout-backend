package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.city.CityDto;
import com.sm.instagram.platform.consent.UserConsentDtoIn;
import com.sm.instagram.platform.contenttype.ContentTypeDto;
import com.sm.instagram.platform.dictionary.DictionaryService;
import com.sm.instagram.platform.partnershipopportunities.CompensationType;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityDtoIn;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityPhotoDtoIn;
import com.sm.instagram.platform.servicetype.ServiceTypeDto;
import com.sm.instagram.platform.user.dto.DeletionBlocker;
import com.sm.instagram.platform.user.dto.DeletionBlockerCategory;
import com.sm.instagram.platform.user.dto.DeletionEligibilityDto;
import com.sm.instagram.platform.userpreferences.UserPreferencesDtoIn;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Partnership, Deletion & Related DTO Unit Tests")
class PartnershipAndDeletionUnitTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("PartnershipOpportunityDtoIn Tests")
    class PartnershipOpportunityDtoInTests {

        @Test
        @DisplayName("valid partnership opportunity should pass validation")
        void validPartnershipOpportunityShouldPassValidation() {
            PartnershipOpportunityDtoIn dto = new PartnershipOpportunityDtoIn();
            dto.setName("Test Campaign");
            dto.setCity("Warsaw");
            dto.setTitle("Amazing Opportunity");
            dto.setCompany(1L);

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("missing required fields should fail validation")
        void missingRequiredFieldsShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = new PartnershipOpportunityDtoIn();

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).hasSizeGreaterThanOrEqualTo(3);
            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("name", "city", "title", "company");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("blank name should fail validation")
        void blankNameShouldFailValidation(String name) {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setName(name);

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("name exceeding 255 chars should fail validation")
        void nameExceeding255CharsShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setName("A".repeat(256));

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("title exceeding 255 chars should fail validation")
        void titleExceeding255CharsShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setTitle("T".repeat(256));

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));
        }

        @Test
        @DisplayName("details exceeding 2000 chars should fail validation")
        void detailsExceeding2000CharsShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setDetails("D".repeat(2001));

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("details"));
        }

        @Test
        @DisplayName("requirements exceeding 2000 chars should fail validation")
        void requirementsExceeding2000CharsShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setRequirements("R".repeat(2001));

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("requirements"));
        }

        @Test
        @DisplayName("negative compensationAmountMin should fail validation")
        void negativeCompensationAmountMinShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setCompensationAmountMin(-1);

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("compensationAmountMin"));
        }

        @Test
        @DisplayName("compensationAmountMin exceeding MAX should fail validation")
        void compensationAmountMinExceedingMaxShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setCompensationAmountMin(1_000_001);

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("compensationAmountMin"));
        }

        @Test
        @DisplayName("negative compensationAmountMax should fail validation")
        void negativeCompensationAmountMaxShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setCompensationAmountMax(-1);

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("compensationAmountMax"));
        }

        @Test
        @DisplayName("compensationAmountMax exceeding MAX should fail validation")
        void compensationAmountMaxExceedingMaxShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setCompensationAmountMax(1_000_001);

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("compensationAmountMax"));
        }

        @Test
        @DisplayName("negative followersMin should fail validation")
        void negativeFollowersMinShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setFollowersMin(-1);

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("followersMin"));
        }

        @Test
        @DisplayName("negative followersMax should fail validation")
        void negativeFollowersMaxShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setFollowersMax(-1);

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("followersMax"));
        }

        @Test
        @DisplayName("compensationDescription exceeding 500 chars should fail validation")
        void compensationDescriptionExceeding500CharsShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setCompensationDescription("C".repeat(501));

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("compensationDescription"));
        }

        @Test
        @DisplayName("photos list exceeding 6 items should fail validation")
        void photosExceeding6ItemsShouldFailValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
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
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("photos"));
        }

        @Test
        @DisplayName("valid photos list should pass validation")
        void validPhotosListShouldPassValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
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
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("valid compensation range should pass validation")
        void validCompensationRangeShouldPassValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setCompensationAmountMin(100);
            dto.setCompensationAmountMax(500);

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("edge case compensation values should pass validation")
        void edgeCaseCompensationValuesShouldPassValidation() {
            PartnershipOpportunityDtoIn dto = createValidDto();
            dto.setCompensationAmountMin(0);
            dto.setCompensationAmountMax(1_000_000);

            Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("all optional fields should have getters and setters")
        void allOptionalFieldsShouldHaveGettersAndSetters() {
            PartnershipOpportunityDtoIn dto = new PartnershipOpportunityDtoIn();
            LocalDateTime now = LocalDateTime.now();

            dto.setId(1L);
            dto.setAddressId(10L);
            dto.setCompensationType(CompensationType.CASH);
            dto.setCurrency(1L);
            dto.setPlatforms(Set.of(1L, 2L));
            dto.setContentTypes(Set.of(1L, 2L, 3L));
            dto.setServiceType(5L);
            dto.setActive(true);
            dto.setStartDate(now);
            dto.setEndDate(now.plusDays(30));
            dto.setCreatedTime(now);
            dto.setLastUpdateTime(now);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getAddressId()).isEqualTo(10L);
            assertThat(dto.getCompensationType()).isEqualTo(CompensationType.CASH);
            assertThat(dto.getCurrency()).isEqualTo(1L);
            assertThat(dto.getPlatforms()).containsExactlyInAnyOrder(1L, 2L);
            assertThat(dto.getContentTypes()).containsExactlyInAnyOrder(1L, 2L, 3L);
            assertThat(dto.getServiceType()).isEqualTo(5L);
            assertThat(dto.isActive()).isTrue();
            assertThat(dto.getStartDate()).isEqualTo(now);
            assertThat(dto.getEndDate()).isEqualTo(now.plusDays(30));
            assertThat(dto.getCreatedTime()).isEqualTo(now);
            assertThat(dto.getLastUpdateTime()).isEqualTo(now);
        }

        private PartnershipOpportunityDtoIn createValidDto() {
            PartnershipOpportunityDtoIn dto = new PartnershipOpportunityDtoIn();
            dto.setName("Test Campaign");
            dto.setCity("Warsaw");
            dto.setTitle("Amazing Opportunity");
            dto.setCompany(1L);
            return dto;
        }
    }

    @Nested
    @DisplayName("PartnershipOpportunityPhotoDtoIn Tests")
    class PartnershipOpportunityPhotoDtoInTests {

        // NOTE: the DTO no longer carries a `url` — a new photo references a
        // tracked `uploadId` and the BE derives the URL (pentest 3.1). The
        // URL-host validation now lives in SignedUrlService.resolveOwnedUpload
        // (see SignedUrlServiceResolveUploadUnitTest). These tests cover the
        // remaining DTO-level constraints.

        @Test
        @DisplayName("valid new-photo DTO (uploadId) should pass validation")
        void validPhotoDtoShouldPassValidation() {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setUploadId("upload-abc");
            dto.setOrderNumber(0);
            dto.setIsCover(true);

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("existing-photo DTO (id, no uploadId) should pass validation")
        void existingPhotoDtoShouldPassValidation() {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setId(7L);
            dto.setOrderNumber(0);
            dto.setIsCover(true);

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("over-long uploadId should fail validation")
        void overLongUploadIdShouldFailValidation() {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setUploadId("A".repeat(37));
            dto.setOrderNumber(0);
            dto.setIsCover(true);

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("uploadId"));
        }

        @Test
        @DisplayName("null orderNumber should fail validation")
        void nullOrderNumberShouldFailValidation() {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setUploadId("upload-abc");
            dto.setOrderNumber(null);
            dto.setIsCover(true);

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("orderNumber"));
        }

        @Test
        @DisplayName("negative orderNumber should fail validation")
        void negativeOrderNumberShouldFailValidation() {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setUploadId("upload-abc");
            dto.setOrderNumber(-1);
            dto.setIsCover(true);

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("orderNumber"));
        }

        @Test
        @DisplayName("null isCover should fail validation")
        void nullIsCoverShouldFailValidation() {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setUploadId("upload-abc");
            dto.setOrderNumber(0);
            dto.setIsCover(null);

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("isCover"));
        }

        @Test
        @DisplayName("id getter and setter should work")
        void idGetterAndSetterShouldWork() {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setId(123L);
            assertThat(dto.getId()).isEqualTo(123L);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 5, 100})
        @DisplayName("valid orderNumber values should pass validation")
        void validOrderNumberValuesShouldPassValidation(int orderNumber) {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setUploadId("upload-abc");
            dto.setOrderNumber(orderNumber);
            dto.setIsCover(false);

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("CompensationType Enum Tests")
    class CompensationTypeTests {

        @ParameterizedTest
        @EnumSource(CompensationType.class)
        @DisplayName("all compensation types should have required properties")
        void allCompensationTypesShouldHaveRequiredProperties(CompensationType type) {
            assertThat(type.getColorTheme()).isNotBlank();
            assertThat(type.getIcon()).isNotBlank();
            assertThat(type.getAliases()).isNotNull();
            assertThat(type.getDefaultDescription()).isNotBlank();
        }

        @Test
        @DisplayName("CASH should have correct properties")
        void cashShouldHaveCorrectProperties() {
            CompensationType cash = CompensationType.CASH;
            assertThat(cash.getColorTheme()).isEqualTo("primary");
            assertThat(cash.getIcon()).isEqualTo("dollar-sign");
            assertThat(cash.getAliases()).contains("money");
        }

        @Test
        @DisplayName("BARTER should have correct properties")
        void barterShouldHaveCorrectProperties() {
            CompensationType barter = CompensationType.BARTER;
            assertThat(barter.getColorTheme()).isEqualTo("warning");
            assertThat(barter.getIcon()).isEqualTo("swap");
            assertThat(barter.getAliases()).contains("trade", "exchange");
        }

        @ParameterizedTest
        @CsvSource({
                "CASH, CASH",
                "cash, CASH",
                "Cash, CASH",
                "BARTER, BARTER",
                "barter, BARTER",
                "Barter, BARTER"
        })
        @DisplayName("fromString should be case insensitive")
        void fromStringShouldBeCaseInsensitive(String input, String expected) {
            CompensationType result = CompensationType.fromString(input);
            assertThat(result.name()).isEqualTo(expected);
        }

        @Test
        @DisplayName("fromString with invalid value should throw exception")
        void fromStringWithInvalidValueShouldThrowException() {
            assertThatThrownBy(() -> CompensationType.fromString("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @EnumSource(CompensationType.class)
        @DisplayName("getValue should return name")
        void getValueShouldReturnName(CompensationType type) {
            assertThat(type.getValue()).isEqualTo(type.name());
        }

        @Test
        @DisplayName("getLabel should use dictionary service")
        void getLabelShouldUseDictionaryService() {
            DictionaryService dictionaryService = mock(DictionaryService.class);
            when(dictionaryService.getTranslation("COMPENSATION_TYPE_CASH", "en"))
                    .thenReturn(Optional.of("Cash Payment"));

            String label = CompensationType.CASH.getLabel(dictionaryService, Locale.ENGLISH);
            assertThat(label).isEqualTo("Cash Payment");
        }

        @Test
        @DisplayName("getLabel should return name when translation not found")
        void getLabelShouldReturnNameWhenTranslationNotFound() {
            DictionaryService dictionaryService = mock(DictionaryService.class);
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            String label = CompensationType.CASH.getLabel(dictionaryService, Locale.ENGLISH);
            assertThat(label).isEqualTo("CASH");
        }

        @Test
        @DisplayName("getDescription should use dictionary service")
        void getDescriptionShouldUseDictionaryService() {
            DictionaryService dictionaryService = mock(DictionaryService.class);
            when(dictionaryService.getTranslation("COMPENSATION_TYPE_BARTER_DESC", "pl"))
                    .thenReturn(Optional.of("Translated description"));

            String description = CompensationType.BARTER.getDescription(dictionaryService, Locale.forLanguageTag("pl"));
            assertThat(description).isEqualTo("Translated description");
        }

        @Test
        @DisplayName("getDescription should return default description when translation not found")
        void getDescriptionShouldReturnDefaultWhenTranslationNotFound() {
            DictionaryService dictionaryService = mock(DictionaryService.class);
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            String description = CompensationType.BARTER.getDescription(dictionaryService, Locale.ENGLISH);
            assertThat(description).isEqualTo("Wymiana barterowa");
        }
    }

    @Nested
    @DisplayName("ContentTypeDto Tests")
    class ContentTypeDtoTests {

        @Test
        @DisplayName("valid content type should pass validation")
        void validContentTypeShouldPassValidation() {
            ContentTypeDto dto = new ContentTypeDto();
            dto.setName("Instagram Story");

            Set<ConstraintViolation<ContentTypeDto>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("blank name should fail validation")
        void blankNameShouldFailValidation(String name) {
            ContentTypeDto dto = new ContentTypeDto();
            dto.setName(name);

            Set<ConstraintViolation<ContentTypeDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("name exceeding 255 chars should fail validation")
        void nameExceeding255CharsShouldFailValidation() {
            ContentTypeDto dto = new ContentTypeDto();
            dto.setName("A".repeat(256));

            Set<ConstraintViolation<ContentTypeDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("id getter and setter should work")
        void idGetterAndSetterShouldWork() {
            ContentTypeDto dto = new ContentTypeDto();
            dto.setId(42L);
            assertThat(dto.getId()).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("ServiceTypeDto Tests")
    class ServiceTypeDtoTests {

        @Test
        @DisplayName("valid service type should pass validation")
        void validServiceTypeShouldPassValidation() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("Beauty");

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("blank name should fail validation")
        void blankNameShouldFailValidation(String name) {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName(name);

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("name shorter than 2 chars should fail validation")
        void nameShorterThan2CharsShouldFailValidation() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("A");

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("name exceeding 100 chars should fail validation")
        void nameExceeding100CharsShouldFailValidation() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("A".repeat(101));

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("description exceeding 500 chars should fail validation")
        void descriptionExceeding500CharsShouldFailValidation() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("Valid Name");
            dto.setDescription("D".repeat(501));

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
        }

        @Test
        @DisplayName("category exceeding 100 chars should fail validation")
        void categoryExceeding100CharsShouldFailValidation() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setName("Valid Name");
            dto.setCategory("C".repeat(101));

            Set<ConstraintViolation<ServiceTypeDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("category"));
        }

        @Test
        @DisplayName("all fields should have getters and setters")
        void allFieldsShouldHaveGettersAndSetters() {
            ServiceTypeDto dto = new ServiceTypeDto();
            dto.setId(1L);
            dto.setName("Beauty");
            dto.setDescription("Beauty services");
            dto.setCategory("Personal Care");

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Beauty");
            assertThat(dto.getDescription()).isEqualTo("Beauty services");
            assertThat(dto.getCategory()).isEqualTo("Personal Care");
        }
    }

    @Nested
    @DisplayName("UserPreferencesDtoIn Tests")
    class UserPreferencesDtoInTests {

        @Test
        @DisplayName("empty dto should pass validation")
        void emptyDtoShouldPassValidation() {
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            Set<ConstraintViolation<UserPreferencesDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("language exceeding 10 chars should fail validation")
        void languageExceeding10CharsShouldFailValidation() {
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();
            dto.setLanguage("A".repeat(11));

            Set<ConstraintViolation<UserPreferencesDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("language"));
        }

        @Test
        @DisplayName("timezone exceeding 50 chars should fail validation")
        void timezoneExceeding50CharsShouldFailValidation() {
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();
            dto.setTimezone("T".repeat(51));

            Set<ConstraintViolation<UserPreferencesDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("timezone"));
        }

        @Test
        @DisplayName("all boolean fields should have getters and setters")
        void allBooleanFieldsShouldHaveGettersAndSetters() {
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            dto.setNotificationEmailEnabled(true);
            dto.setNotificationPushEnabled(false);
            dto.setNotificationSmsEnabled(true);
            dto.setDarkModeEnabled(true);
            dto.setGdprMarketingConsent(false);
            dto.setSharePhoneForPayments(true);
            dto.setTwoFactorAuthenticationEnabled(true);

            assertThat(dto.getNotificationEmailEnabled()).isTrue();
            assertThat(dto.getNotificationPushEnabled()).isFalse();
            assertThat(dto.getNotificationSmsEnabled()).isTrue();
            assertThat(dto.getDarkModeEnabled()).isTrue();
            assertThat(dto.getGdprMarketingConsent()).isFalse();
            assertThat(dto.getSharePhoneForPayments()).isTrue();
            assertThat(dto.getTwoFactorAuthenticationEnabled()).isTrue();
        }

        @Test
        @DisplayName("valid preferences should pass validation")
        void validPreferencesShouldPassValidation() {
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();
            dto.setLanguage("pl");
            dto.setTimezone("Europe/Warsaw");
            dto.setCommunicationFrequency("daily");

            Set<ConstraintViolation<UserPreferencesDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("CityDto Tests")
    class CityDtoTests {

        @Test
        @DisplayName("valid city should pass validation")
        void validCityShouldPassValidation() {
            CityDto dto = new CityDto();
            dto.setName("Warsaw");

            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("blank name should fail validation")
        void blankNameShouldFailValidation(String name) {
            CityDto dto = new CityDto();
            dto.setName(name);

            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("name shorter than 2 chars should fail validation")
        void nameShorterThan2CharsShouldFailValidation() {
            CityDto dto = new CityDto();
            dto.setName("A");

            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("name exceeding 50 chars should fail validation")
        void nameExceeding50CharsShouldFailValidation() {
            CityDto dto = new CityDto();
            dto.setName("A".repeat(51));

            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("state exceeding 255 chars should fail validation")
        void stateExceeding255CharsShouldFailValidation() {
            CityDto dto = new CityDto();
            dto.setName("Valid City");
            dto.setState("S".repeat(256));

            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("state"));
        }

        @Test
        @DisplayName("country exceeding 255 chars should fail validation")
        void countryExceeding255CharsShouldFailValidation() {
            CityDto dto = new CityDto();
            dto.setName("Valid City");
            dto.setCountry("C".repeat(256));

            Set<ConstraintViolation<CityDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("country"));
        }

        @Test
        @DisplayName("default country should be Polska")
        void defaultCountryShouldBePolska() {
            CityDto dto = new CityDto();
            assertThat(dto.getCountry()).isEqualTo("Polska");
        }

        @Test
        @DisplayName("all fields should have getters and setters")
        void allFieldsShouldHaveGettersAndSetters() {
            CityDto dto = new CityDto();
            dto.setId(1L);
            dto.setName("Krakow");
            dto.setState("Lesser Poland");
            dto.setCountry("Poland");

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Krakow");
            assertThat(dto.getState()).isEqualTo("Lesser Poland");
            assertThat(dto.getCountry()).isEqualTo("Poland");
        }
    }

    @Nested
    @DisplayName("UserConsentDtoIn Tests")
    class UserConsentDtoInTests {

        @Test
        @DisplayName("valid consent should pass validation")
        void validConsentShouldPassValidation() {
            UserConsentDtoIn dto = new UserConsentDtoIn();
            dto.setConsentType("MARKETING");
            dto.setConsentGiven(true);

            Set<ConstraintViolation<UserConsentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("blank consentType should fail validation")
        void blankConsentTypeShouldFailValidation(String consentType) {
            UserConsentDtoIn dto = new UserConsentDtoIn();
            dto.setConsentType(consentType);
            dto.setConsentGiven(true);

            Set<ConstraintViolation<UserConsentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentType"));
        }

        @Test
        @DisplayName("null consentGiven should fail validation")
        void nullConsentGivenShouldFailValidation() {
            UserConsentDtoIn dto = new UserConsentDtoIn();
            dto.setConsentType("MARKETING");
            dto.setConsentGiven(null);

            Set<ConstraintViolation<UserConsentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentGiven"));
        }

        @Test
        @DisplayName("default collectionMethod should be web_form")
        void defaultCollectionMethodShouldBeWebForm() {
            UserConsentDtoIn dto = new UserConsentDtoIn();
            assertThat(dto.getCollectionMethod()).isEqualTo("web_form");
        }

        @Test
        @DisplayName("userAgent should be settable")
        void userAgentShouldBeSettable() {
            UserConsentDtoIn dto = new UserConsentDtoIn();
            dto.setUserAgent("Mozilla/5.0");

            assertThat(dto.getUserAgent()).isEqualTo("Mozilla/5.0");
        }
    }

    @Nested
    @DisplayName("DeletionBlockerCategory Enum Tests")
    class DeletionBlockerCategoryTests {

        @ParameterizedTest
        @EnumSource(DeletionBlockerCategory.class)
        @DisplayName("all categories should have required properties")
        void allCategoriesShouldHaveRequiredProperties(DeletionBlockerCategory category) {
            assertThat(category.getColorTheme()).isNotBlank();
            assertThat(category.getIcon()).isNotBlank();
        }

        @Test
        @DisplayName("ACTIVE_OPPORTUNITIES should have warning color")
        void activeOpportunitiesShouldHaveWarningColor() {
            assertThat(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES.getColorTheme()).isEqualTo("warning");
            assertThat(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES.getIcon()).isEqualTo("briefcase");
        }

        @Test
        @DisplayName("LAST_ADMIN should have danger color")
        void lastAdminShouldHaveDangerColor() {
            assertThat(DeletionBlockerCategory.LAST_ADMIN.getColorTheme()).isEqualTo("danger");
            assertThat(DeletionBlockerCategory.LAST_ADMIN.getIcon()).isEqualTo("shield-alert");
        }

        @Test
        @DisplayName("info-level categories should have correct theme")
        void infoLevelCategoriesShouldHaveCorrectTheme() {
            assertThat(DeletionBlockerCategory.PENDING_OPPORTUNITIES.getColorTheme()).isEqualTo("info");
            assertThat(DeletionBlockerCategory.SUPPORT_TICKETS_HISTORY.getColorTheme()).isEqualTo("info");
            assertThat(DeletionBlockerCategory.RECENT_ACTIVITY.getColorTheme()).isEqualTo("info");
        }

        @Test
        @DisplayName("getLabel should use dictionary service")
        void getLabelShouldUseDictionaryService() {
            DictionaryService dictionaryService = mock(DictionaryService.class);
            when(dictionaryService.getTranslation("DELETION_BLOCKER_LAST_ADMIN", "en"))
                    .thenReturn(Optional.of("Last Administrator"));

            String label = DeletionBlockerCategory.LAST_ADMIN.getLabel(dictionaryService, Locale.ENGLISH);
            assertThat(label).isEqualTo("Last Administrator");
        }

        @Test
        @DisplayName("getLabel should return name when translation not found")
        void getLabelShouldReturnNameWhenTranslationNotFound() {
            DictionaryService dictionaryService = mock(DictionaryService.class);
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            String label = DeletionBlockerCategory.LAST_ADMIN.getLabel(dictionaryService, Locale.ENGLISH);
            assertThat(label).isEqualTo("LAST_ADMIN");
        }

        @Test
        @DisplayName("getDescription should use dictionary service")
        void getDescriptionShouldUseDictionaryService() {
            DictionaryService dictionaryService = mock(DictionaryService.class);
            when(dictionaryService.getTranslation("DELETION_BLOCKER_OPEN_SUPPORT_TICKETS_DESC", "en"))
                    .thenReturn(Optional.of("You have open support tickets"));

            String description = DeletionBlockerCategory.OPEN_SUPPORT_TICKETS.getDescription(dictionaryService, Locale.ENGLISH);
            assertThat(description).isEqualTo("You have open support tickets");
        }

        @Test
        @DisplayName("getDescription should return name when translation not found")
        void getDescriptionShouldReturnNameWhenTranslationNotFound() {
            DictionaryService dictionaryService = mock(DictionaryService.class);
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            String description = DeletionBlockerCategory.OPEN_SUPPORT_TICKETS.getDescription(dictionaryService, Locale.ENGLISH);
            assertThat(description).isEqualTo("OPEN_SUPPORT_TICKETS");
        }
    }

    @Nested
    @DisplayName("DeletionBlocker Tests")
    class DeletionBlockerTests {

        @Test
        @DisplayName("builder should create valid object")
        void builderShouldCreateValidObject() {
            DeletionBlocker blocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .reason("Active campaigns exist")
                    .description("You have 3 active campaigns")
                    .count(3)
                    .entityIds(List.of(1L, 2L, 3L))
                    .entityType("PartnershipOpportunity")
                    .entityDescription("Active campaign")
                    .build();

            assertThat(blocker.getCategory()).isEqualTo(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES);
            assertThat(blocker.getReason()).isEqualTo("Active campaigns exist");
            assertThat(blocker.getDescription()).isEqualTo("You have 3 active campaigns");
            assertThat(blocker.getCount()).isEqualTo(3);
            assertThat(blocker.getEntityIds()).containsExactly(1L, 2L, 3L);
            assertThat(blocker.getEntityType()).isEqualTo("PartnershipOpportunity");
            assertThat(blocker.getEntityDescription()).isEqualTo("Active campaign");
        }

        @Test
        @DisplayName("no-args constructor should work")
        void noArgsConstructorShouldWork() {
            DeletionBlocker blocker = new DeletionBlocker();
            assertThat(blocker).isNotNull();
            assertThat(blocker.getCategory()).isNull();
        }

        @Test
        @DisplayName("all-args constructor should work")
        void allArgsConstructorShouldWork() {
            DeletionBlocker blocker = new DeletionBlocker(
                    DeletionBlockerCategory.LAST_ADMIN,
                    "reason",
                    "description",
                    1,
                    List.of(1L),
                    "User",
                    "entity desc"
            );

            assertThat(blocker.getCategory()).isEqualTo(DeletionBlockerCategory.LAST_ADMIN);
            assertThat(blocker.getReason()).isEqualTo("reason");
        }

        @Test
        @DisplayName("equals and hashCode should work")
        void equalsAndHashCodeShouldWork() {
            DeletionBlocker blocker1 = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .reason("reason")
                    .build();

            DeletionBlocker blocker2 = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .reason("reason")
                    .build();

            assertThat(blocker1).isEqualTo(blocker2);
            assertThat(blocker1.hashCode()).isEqualTo(blocker2.hashCode());
        }

        @Test
        @DisplayName("toString should include fields")
        void toStringShouldIncludeFields() {
            DeletionBlocker blocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .reason("test reason")
                    .build();

            String str = blocker.toString();
            assertThat(str).contains("ACTIVE_OPPORTUNITIES");
            assertThat(str).contains("test reason");
        }
    }

    @Nested
    @DisplayName("DeletionEligibilityDto Tests")
    class DeletionEligibilityDtoTests {

        @Test
        @DisplayName("builder should create valid object")
        void builderShouldCreateValidObject() {
            DeletionBlocker blocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .build();

            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .userId(1L)
                    .firebaseUserId("firebase-123")
                    .userEmail("test@example.com")
                    .userType("INFLUENCER")
                    .canSoftDelete(true)
                    .canPermanentDelete(false)
                    .softDeleteBlockers(Collections.emptyList())
                    .permanentDeleteBlockers(List.of(blocker))
                    .summary("Can soft delete but not permanent delete")
                    .build();

            assertThat(dto.getUserId()).isEqualTo(1L);
            assertThat(dto.getFirebaseUserId()).isEqualTo("firebase-123");
            assertThat(dto.getUserEmail()).isEqualTo("test@example.com");
            assertThat(dto.getUserType()).isEqualTo("INFLUENCER");
            assertThat(dto.isCanSoftDelete()).isTrue();
            assertThat(dto.isCanPermanentDelete()).isFalse();
            assertThat(dto.getSoftDeleteBlockers()).isEmpty();
            assertThat(dto.getPermanentDeleteBlockers()).hasSize(1);
            assertThat(dto.getSummary()).isEqualTo("Can soft delete but not permanent delete");
        }

        @Test
        @DisplayName("no-args constructor should work")
        void noArgsConstructorShouldWork() {
            DeletionEligibilityDto dto = new DeletionEligibilityDto();
            assertThat(dto).isNotNull();
        }

        @Test
        @DisplayName("all-args constructor should work")
        void allArgsConstructorShouldWork() {
            DeletionEligibilityDto dto = new DeletionEligibilityDto(
                    1L, "firebase", "email@test.com", "COMPANY",
                    true, true, List.of(), List.of(), "summary"
            );

            assertThat(dto.getUserId()).isEqualTo(1L);
            assertThat(dto.isCanSoftDelete()).isTrue();
            assertThat(dto.isCanPermanentDelete()).isTrue();
        }

        @Test
        @DisplayName("equals and hashCode should work")
        void equalsAndHashCodeShouldWork() {
            DeletionEligibilityDto dto1 = DeletionEligibilityDto.builder()
                    .userId(1L)
                    .userEmail("test@example.com")
                    .build();

            DeletionEligibilityDto dto2 = DeletionEligibilityDto.builder()
                    .userId(1L)
                    .userEmail("test@example.com")
                    .build();

            assertThat(dto1).isEqualTo(dto2);
            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }

        @Test
        @DisplayName("setters should work")
        void settersShouldWork() {
            DeletionEligibilityDto dto = new DeletionEligibilityDto();
            dto.setUserId(99L);
            dto.setCanSoftDelete(true);

            assertThat(dto.getUserId()).isEqualTo(99L);
            assertThat(dto.isCanSoftDelete()).isTrue();
        }
    }
}
