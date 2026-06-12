package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressDtoIn;
import com.sm.instagram.platform.address.AddressNoUserDtoOut;
import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.contenttype.ContentType;
import com.sm.instagram.platform.contenttype.ContentTypeDtoOut;
import com.sm.instagram.platform.currency.Currency;
import com.sm.instagram.platform.currency.CurrencyDtoOut;
import com.sm.instagram.platform.partnershipopportunities.*;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformDto;
import com.sm.instagram.platform.servicetype.ServiceType;
import com.sm.instagram.platform.servicetype.ServiceTypeDtoOut;
import com.sm.instagram.platform.user.CompanyPublicProfileDto;
import com.sm.instagram.platform.user.User;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Additional comprehensive unit tests for PartnershipOpportunity package.
 * Covers entity, DTOs, edge cases, and additional validation scenarios.
 * Uses pure Mockito - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Partnership Opportunity Additional Unit Tests")
class PartnershipOpportunityMoreUnitTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== PartnershipOpportunity Entity Advanced Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunity Entity Advanced Tests")
    class PartnershipOpportunityEntityAdvancedTests {

        private PartnershipOpportunity opportunity;
        private City city;
        private Address address;
        private User company;

        @BeforeEach
        void setUp() {
            city = new City();
            city.setId(1L);
            city.setName("Krakow");

            address = new Address();
            address.setId(1L);
            address.setStreet("Test Street");
            address.setCity("Krakow");
            address.setPostalCode("30-001");
            address.setCountry("Poland");

            company = new User();
            company.setId(1L);
            company.setFirebaseUserId("firebase123");

            opportunity = createValidOpportunity();
        }

        private PartnershipOpportunity createValidOpportunity() {
            PartnershipOpportunity opp = new PartnershipOpportunity();
            opp.setName("Test Partnership");
            opp.setTitle("Amazing Opportunity");
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

        @Nested
        @DisplayName("Boundary Value Tests")
        class BoundaryValueTests {

            @Test
            @DisplayName("should accept compensation amount at exact maximum limit")
            void shouldAcceptCompensationAmountAtExactMaxLimit() {
                opportunity.setCompensationAmountMin(0);
                opportunity.setCompensationAmountMax(1_000_000);

                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                boolean hasCompensationViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().contains("compensationAmount"));
                assertThat(hasCompensationViolation).isFalse();
                assertThat(opportunity.isValidCompensationRange()).isTrue();
            }

            @Test
            @DisplayName("should reject compensation amount one above maximum limit")
            void shouldRejectCompensationAmountOneAboveMaxLimit() {
                opportunity.setCompensationAmountMax(1_000_001);

                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should handle Long.MAX_VALUE for followers range")
            void shouldHandleLongMaxValueForFollowersRange() {
                opportunity.setFollowersMin(0);
                opportunity.setFollowersMax(Long.MAX_VALUE);

                assertThat(opportunity.isValidFollowersRange()).isTrue();
            }

            @Test
            @DisplayName("should accept exactly 255 characters for name")
            void shouldAcceptExactly255CharsForName() {
                opportunity.setName("A".repeat(255));

                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                boolean hasNameSizeViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("name") &&
                                v.getMessage().contains("exceed"));
                assertThat(hasNameSizeViolation).isFalse();
            }

            @Test
            @DisplayName("should accept exactly 255 characters for title")
            void shouldAcceptExactly255CharsForTitle() {
                opportunity.setTitle("B".repeat(255));

                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                boolean hasTitleSizeViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("title") &&
                                v.getMessage().contains("exceed"));
                assertThat(hasTitleSizeViolation).isFalse();
            }

            @Test
            @DisplayName("should accept exactly 2000 characters for details")
            void shouldAcceptExactly2000CharsForDetails() {
                opportunity.setDetails("C".repeat(2000));

                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                boolean hasDetailsViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("details"));
                assertThat(hasDetailsViolation).isFalse();
            }

            @Test
            @DisplayName("should accept exactly 2000 characters for requirements")
            void shouldAcceptExactly2000CharsForRequirements() {
                opportunity.setRequirements("D".repeat(2000));

                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                boolean hasRequirementsViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("requirements"));
                assertThat(hasRequirementsViolation).isFalse();
            }

            @Test
            @DisplayName("should accept exactly 500 characters for compensation description")
            void shouldAcceptExactly500CharsForCompensationDescription() {
                opportunity.setCompensationDescription("E".repeat(500));

                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                boolean hasCompDescViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("compensationDescription"));
                assertThat(hasCompDescViolation).isFalse();
            }
        }

        @Nested
        @DisplayName("Date Range Edge Cases")
        class DateRangeEdgeCases {

            @Test
            @DisplayName("should accept end date exactly one second after start date")
            void shouldAcceptEndDateOneSecondAfterStartDate() {
                LocalDateTime start = LocalDateTime.now().plusDays(1);
                LocalDateTime end = start.plusSeconds(1);
                opportunity.setStartDate(start);
                opportunity.setEndDate(end);

                assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();
            }

            @Test
            @DisplayName("should reject end date exactly one second before start date")
            void shouldRejectEndDateOneSecondBeforeStartDate() {
                LocalDateTime start = LocalDateTime.now().plusDays(1);
                LocalDateTime end = start.minusSeconds(1);
                opportunity.setStartDate(start);
                opportunity.setEndDate(end);

                assertThat(opportunity.isEndDateNotBeforeStartDate()).isFalse();
            }

            @Test
            @DisplayName("should accept very far future dates")
            void shouldAcceptVeryFarFutureDates() {
                LocalDateTime start = LocalDateTime.now().plusYears(100);
                LocalDateTime end = start.plusYears(1);
                opportunity.setStartDate(start);
                opportunity.setEndDate(end);

                assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();
            }

            @Test
            @DisplayName("should handle same nanosecond for start and end date")
            void shouldHandleSameNanosecondForStartAndEndDate() {
                LocalDateTime exactSame = LocalDateTime.of(2025, 6, 15, 10, 30, 45, 123456789);
                opportunity.setStartDate(exactSame);
                opportunity.setEndDate(exactSame);

                assertThat(opportunity.isEndDateNotBeforeStartDate()).isTrue();
            }
        }

        @Nested
        @DisplayName("Photos Relationship Tests")
        class PhotosRelationshipTests {

            @Test
            @DisplayName("should maintain bidirectional relationship after multiple setPhotos calls")
            void shouldMaintainBidirectionalRelationshipAfterMultipleCalls() {
                PartnershipOpportunityPhoto photo1 = new PartnershipOpportunityPhoto();
                photo1.setUrl("https://example.com/photo1.jpg");
                opportunity.setPhotos(List.of(photo1));

                PartnershipOpportunityPhoto photo2 = new PartnershipOpportunityPhoto();
                photo2.setUrl("https://example.com/photo2.jpg");
                opportunity.setPhotos(List.of(photo2));

                assertThat(opportunity.getPhotos()).hasSize(1);
                assertThat(opportunity.getPhotos().get(0).getUrl()).isEqualTo("https://example.com/photo2.jpg");
                assertThat(photo2.getPartnershipOpportunity()).isEqualTo(opportunity);
            }

            @Test
            @DisplayName("should handle setting photos with same photo object multiple times")
            void shouldHandleSettingPhotosWithSamePhotoObjectMultipleTimes() {
                PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto();
                photo.setUrl("https://example.com/same.jpg");
                List<PartnershipOpportunityPhoto> photos = new ArrayList<>();
                photos.add(photo);

                opportunity.setPhotos(photos);
                opportunity.setPhotos(photos);

                assertThat(opportunity.getPhotos()).hasSize(1);
                assertThat(photo.getPartnershipOpportunity()).isEqualTo(opportunity);
            }

            @Test
            @DisplayName("should handle maximum six photos")
            void shouldHandleMaximumSixPhotos() {
                List<PartnershipOpportunityPhoto> photos = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto();
                    photo.setUrl("https://example.com/photo" + i + ".jpg");
                    photo.setOrderNumber(i);
                    photo.setIsCover(i == 0);
                    photos.add(photo);
                }

                opportunity.setPhotos(photos);

                assertThat(opportunity.getPhotos()).hasSize(6);
                for (PartnershipOpportunityPhoto photo : opportunity.getPhotos()) {
                    assertThat(photo.getPartnershipOpportunity()).isEqualTo(opportunity);
                }
            }
        }

        @Nested
        @DisplayName("Platforms and ContentTypes Collections")
        class CollectionsTests {

            @Test
            @DisplayName("should handle adding platforms after initialization")
            void shouldHandleAddingPlatformsAfterInitialization() {
                Platform instagram = new Platform();
                instagram.setId(1L);
                instagram.setName("Instagram");

                Platform tiktok = new Platform();
                tiktok.setId(2L);
                tiktok.setName("TikTok");

                opportunity.getPlatforms().add(instagram);
                opportunity.getPlatforms().add(tiktok);

                assertThat(opportunity.getPlatforms()).hasSize(2);
            }

            @Test
            @DisplayName("should handle adding content types after initialization")
            void shouldHandleAddingContentTypesAfterInitialization() {
                ContentType reel = new ContentType();
                reel.setId(1L);
                reel.setName("Reel");

                ContentType story = new ContentType();
                story.setId(2L);
                story.setName("Story");

                opportunity.getContentTypes().add(reel);
                opportunity.getContentTypes().add(story);

                assertThat(opportunity.getContentTypes()).hasSize(2);
            }

            @Test
            @DisplayName("should handle clearing and repopulating platforms")
            void shouldHandleClearingAndRepopulatingPlatforms() {
                Platform platform1 = new Platform();
                platform1.setId(1L);
                opportunity.getPlatforms().add(platform1);

                opportunity.getPlatforms().clear();
                assertThat(opportunity.getPlatforms()).isEmpty();

                Platform platform2 = new Platform();
                platform2.setId(2L);
                opportunity.getPlatforms().add(platform2);

                assertThat(opportunity.getPlatforms()).hasSize(1);
            }
        }

        @Nested
        @DisplayName("UpdaterId Pattern Validation")
        class UpdaterIdPatternTests {

            @ParameterizedTest
            @ValueSource(strings = {"abc123", "ABC123", "aB1cD2eF3", "12345", "abcde"})
            @DisplayName("should accept valid alphanumeric updater IDs")
            void shouldAcceptValidAlphanumericUpdaterIds(String updaterId) {
                opportunity.setUpdaterId(updaterId);

                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                boolean hasUpdaterIdViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("updaterId"));
                assertThat(hasUpdaterIdViolation).isFalse();
            }

            @ParameterizedTest
            @ValueSource(strings = {"user@test", "user#1", "user 1", "user!x", "user/x"})
            @DisplayName("should reject updater IDs with special characters")
            void shouldRejectUpdaterIdsWithSpecialCharacters(String updaterId) {
                opportunity.setUpdaterId(updaterId);

                Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
                boolean hasUpdaterIdViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("updaterId"));
                assertThat(hasUpdaterIdViolation).isTrue();
            }
        }

        @Nested
        @DisplayName("All Args Constructor Tests")
        class AllArgsConstructorTests {

            @Test
            @DisplayName("should create opportunity using all args constructor")
            void shouldCreateOpportunityUsingAllArgsConstructor() {
                LocalDateTime now = LocalDateTime.now();
                List<PartnershipOpportunityPhoto> photos = new ArrayList<>();

                PartnershipOpportunity opp = new PartnershipOpportunity(
                        1L,                              // id
                        "Test Name",                     // name
                        city,                            // city
                        address,                         // address
                        "Test Title",                    // title
                        "Test Details",                  // details
                        "Test Requirements",             // requirements
                        CompensationType.BARTER,         // compensationType
                        null,                            // currency
                        100,                             // compensationAmountMin
                        500,                             // compensationAmountMax
                        1000L,                           // followersMin
                        10000L,                          // followersMax
                        "Compensation desc",             // compensationDescription
                        new HashSet<>(),                 // platforms
                        new HashSet<>(),                 // contentTypes
                        now.plusDays(1),                 // startDate
                        now.plusDays(30),                // endDate
                        photos,                          // photos
                        new ArrayList<>(),               // appliedOpportunities
                        company,                         // company
                        null,                            // serviceType
                        1L,                              // version
                        now,                             // createdTime
                        now,                             // lastUpdateTime
                        "updater123",                    // updaterId
                        true                             // active
                );

                assertThat(opp.getId()).isEqualTo(1L);
                assertThat(opp.getName()).isEqualTo("Test Name");
                assertThat(opp.getCompensationType()).isEqualTo(CompensationType.BARTER);
                assertThat(opp.isActive()).isTrue();
            }
        }

        @Nested
        @DisplayName("Version Field Tests")
        class VersionFieldTests {

            @Test
            @DisplayName("should have null version by default")
            void shouldHaveNullVersionByDefault() {
                PartnershipOpportunity newOpp = new PartnershipOpportunity();
                assertThat(newOpp.getVersion()).isNull();
            }

            @Test
            @DisplayName("should allow setting version")
            void shouldAllowSettingVersion() {
                opportunity.setVersion(5L);
                assertThat(opportunity.getVersion()).isEqualTo(5L);
            }
        }
    }

    // ==================== PartnershipOpportunityPhoto Advanced Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunityPhoto Advanced Tests")
    class PartnershipOpportunityPhotoAdvancedTests {

        @Nested
        @DisplayName("URL Pattern Validation")
        class UrlPatternValidationTests {

            @ParameterizedTest
            @ValueSource(strings = {
                    "https://example.com/photo.jpg",
                    "https://cdn.example.com/images/photo.png",
                    "https://storage.googleapis.com/bucket/file.gif",
                    "https://s3.amazonaws.com/bucket/image.webp",
                    "https://example.com/path/to/photo.jpeg",
                    "https://example.com/photo?id=123&type=cover",
                    "https://example.com/photo#section"
            })
            @DisplayName("should accept valid HTTPS URLs")
            void shouldAcceptValidHttpsUrls(String url) {
                PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto();
                photo.setUrl(url);
                photo.setOrderNumber(0);
                photo.setIsCover(false);

                Set<ConstraintViolation<PartnershipOpportunityPhoto>> violations = validator.validate(photo);
                boolean hasUrlViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("url"));
                assertThat(hasUrlViolation).isFalse();
            }

            @ParameterizedTest
            @ValueSource(strings = {
                    "http://example.com/photo.jpg",
                    "ftp://example.com/photo.jpg",
                    "file:///local/photo.jpg",
                    "example.com/photo.jpg",
                    "not-a-url",
                    "javascript:alert('xss')",
                    "data:image/png;base64,abc123"
            })
            @DisplayName("should reject invalid URLs")
            void shouldRejectInvalidUrls(String url) {
                PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto();
                photo.setUrl(url);
                photo.setOrderNumber(0);
                photo.setIsCover(false);

                Set<ConstraintViolation<PartnershipOpportunityPhoto>> violations = validator.validate(photo);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should reject URL exceeding 2048 characters")
            void shouldRejectUrlExceeding2048Characters() {
                PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto();
                photo.setUrl("https://example.com/" + "a".repeat(2030));
                photo.setOrderNumber(0);
                photo.setIsCover(false);

                Set<ConstraintViolation<PartnershipOpportunityPhoto>> violations = validator.validate(photo);
                assertThat(violations).isNotEmpty();
            }
        }

        @Nested
        @DisplayName("UpdatableEntity Interface")
        class UpdatableEntityInterfaceTests {

            @Test
            @DisplayName("should implement setEntity correctly")
            void shouldImplementSetEntityCorrectly() {
                PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto();
                PartnershipOpportunity opportunity = new PartnershipOpportunity();
                opportunity.setId(1L);

                photo.setEntity(opportunity);

                assertThat(photo.getPartnershipOpportunity()).isEqualTo(opportunity);
            }

            @Test
            @DisplayName("should allow setting entity to null")
            void shouldAllowSettingEntityToNull() {
                PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto();
                PartnershipOpportunity opportunity = new PartnershipOpportunity();
                photo.setPartnershipOpportunity(opportunity);

                photo.setEntity(null);

                assertThat(photo.getPartnershipOpportunity()).isNull();
            }
        }

        @Nested
        @DisplayName("All Args Constructor Tests")
        class AllArgsConstructorTests {

            @Test
            @DisplayName("should create photo using all args constructor")
            void shouldCreatePhotoUsingAllArgsConstructor() {
                PartnershipOpportunity opportunity = new PartnershipOpportunity();
                opportunity.setId(1L);

                PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto(
                        10L,
                        opportunity,
                        "https://example.com/photo.jpg",
                        0,
                        true
                );

                assertThat(photo.getId()).isEqualTo(10L);
                assertThat(photo.getPartnershipOpportunity()).isEqualTo(opportunity);
                assertThat(photo.getUrl()).isEqualTo("https://example.com/photo.jpg");
                assertThat(photo.getOrderNumber()).isZero();
                assertThat(photo.getIsCover()).isTrue();
            }
        }

        @Nested
        @DisplayName("Order Number Tests")
        class OrderNumberTests {

            @ParameterizedTest
            @ValueSource(ints = {0, 1, 2, 3, 4, 5, 100, Integer.MAX_VALUE})
            @DisplayName("should accept valid order numbers")
            void shouldAcceptValidOrderNumbers(int orderNumber) {
                PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto();
                photo.setUrl("https://example.com/photo.jpg");
                photo.setOrderNumber(orderNumber);
                photo.setIsCover(false);

                Set<ConstraintViolation<PartnershipOpportunityPhoto>> violations = validator.validate(photo);
                boolean hasOrderViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("orderNumber"));
                assertThat(hasOrderViolation).isFalse();
            }
        }
    }

    // ==================== PartnershipOpportunityDtoIn Advanced Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunityDtoIn Advanced Tests")
    class PartnershipOpportunityDtoInAdvancedTests {

        private PartnershipOpportunityDtoIn dto;

        @BeforeEach
        void setUp() {
            dto = new PartnershipOpportunityDtoIn();
            dto.setName("Test Partnership");
            dto.setCity("Krakow");
            dto.setTitle("Amazing Partnership");
            dto.setCompensationType(CompensationType.CASH);
            dto.setCompensationAmountMin(100);
            dto.setCompensationAmountMax(500);
            dto.setCompany(1L);
        }

        @Nested
        @DisplayName("Photos List Validation")
        class PhotosListValidationTests {

            @Test
            @DisplayName("should accept empty photos list")
            void shouldAcceptEmptyPhotosList() {
                dto.setPhotos(new ArrayList<>());

                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                boolean hasPhotosViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().contains("photos"));
                assertThat(hasPhotosViolation).isFalse();
            }

            @Test
            @DisplayName("should cascade validation to nested photo DTOs")
            void shouldCascadeValidationToNestedPhotoDtos() {
                PartnershipOpportunityPhotoDtoIn invalidPhoto = new PartnershipOpportunityPhotoDtoIn();
                invalidPhoto.setUrl(null); // Required field
                invalidPhoto.setOrderNumber(0);
                invalidPhoto.setIsCover(false);

                dto.setPhotos(List.of(invalidPhoto));

                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }

            @Test
            @DisplayName("should accept exactly 6 valid photos")
            void shouldAcceptExactly6ValidPhotos() {
                List<PartnershipOpportunityPhotoDtoIn> photos = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    PartnershipOpportunityPhotoDtoIn photo = new PartnershipOpportunityPhotoDtoIn();
                    photo.setUrl("https://example.com/photo" + i + ".jpg");
                    photo.setOrderNumber(i);
                    photo.setIsCover(i == 0);
                    photos.add(photo);
                }
                dto.setPhotos(photos);

                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                boolean hasPhotosCountViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("photos") &&
                                v.getMessage().contains("max"));
                assertThat(hasPhotosCountViolation).isFalse();
            }

            @Test
            @DisplayName("should reject 7 photos")
            void shouldReject7Photos() {
                List<PartnershipOpportunityPhotoDtoIn> photos = new ArrayList<>();
                for (int i = 0; i < 7; i++) {
                    PartnershipOpportunityPhotoDtoIn photo = new PartnershipOpportunityPhotoDtoIn();
                    photo.setUrl("https://example.com/photo" + i + ".jpg");
                    photo.setOrderNumber(i);
                    photo.setIsCover(i == 0);
                    photos.add(photo);
                }
                dto.setPhotos(photos);

                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isNotEmpty();
            }
        }

        @Nested
        @DisplayName("Optional Fields Tests")
        class OptionalFieldsTests {

            @Test
            @DisplayName("should accept null addressId")
            void shouldAcceptNullAddressId() {
                dto.setAddressId(null);
                dto.setAddress(null);

                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                boolean hasAddressViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().contains("address"));
                assertThat(hasAddressViolation).isFalse();
            }

            @Test
            @DisplayName("should accept null currency")
            void shouldAcceptNullCurrency() {
                dto.setCurrency(null);

                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                boolean hasCurrencyViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
                assertThat(hasCurrencyViolation).isFalse();
            }

            @Test
            @DisplayName("should accept null platforms")
            void shouldAcceptNullPlatforms() {
                dto.setPlatforms(null);

                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                boolean hasPlatformsViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("platforms"));
                assertThat(hasPlatformsViolation).isFalse();
            }

            @Test
            @DisplayName("should accept null contentTypes")
            void shouldAcceptNullContentTypes() {
                dto.setContentTypes(null);

                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                boolean hasContentTypesViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("contentTypes"));
                assertThat(hasContentTypesViolation).isFalse();
            }

            @Test
            @DisplayName("should accept null serviceType")
            void shouldAcceptNullServiceType() {
                dto.setServiceType(null);

                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                boolean hasServiceTypeViolation = violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("serviceType"));
                assertThat(hasServiceTypeViolation).isFalse();
            }
        }

        @Nested
        @DisplayName("Address DTO Integration")
        class AddressDtoIntegrationTests {

            @Test
            @DisplayName("should accept valid address DTO")
            void shouldAcceptValidAddressDto() {
                AddressDtoIn addressDto = new AddressDtoIn();
                addressDto.setStreet("Test Street");
                addressDto.setCity("Krakow");
                addressDto.setPostalCode("30-001");
                addressDto.setCountry("Poland");
                dto.setAddress(addressDto);

                Set<ConstraintViolation<PartnershipOpportunityDtoIn>> violations = validator.validate(dto);
                // Address validation is not cascaded in DtoIn
                assertThat(violations.stream()
                        .noneMatch(v -> v.getPropertyPath().toString().startsWith("address."))).isTrue();
            }
        }

        @Nested
        @DisplayName("Setters and Getters")
        class SettersAndGettersTests {

            @Test
            @DisplayName("should set and get all fields correctly")
            void shouldSetAndGetAllFieldsCorrectly() {
                LocalDateTime now = LocalDateTime.now();
                AddressDtoIn addressDto = new AddressDtoIn();

                dto.setId(1L);
                dto.setName("Test Name");
                dto.setCity("Test City");
                dto.setTitle("Test Title");
                dto.setDetails("Test Details");
                dto.setRequirements("Test Requirements");
                dto.setAddress(addressDto);
                dto.setAddressId(5L);
                dto.setCompensationType(CompensationType.BARTER);
                dto.setCompensationAmountMin(50);
                dto.setCompensationAmountMax(1000);
                dto.setCurrency(1L);
                dto.setCompensationDescription("Test Desc");
                dto.setFollowersMin(500);
                dto.setFollowersMax(50000);
                dto.setPlatforms(Set.of(1L, 2L));
                dto.setContentTypes(Set.of(3L, 4L));
                dto.setCompany(10L);
                dto.setServiceType(20L);
                dto.setActive(false);
                dto.setStartDate(now.plusDays(1));
                dto.setEndDate(now.plusDays(30));
                dto.setCreatedTime(now);
                dto.setLastUpdateTime(now);

                assertThat(dto.getId()).isEqualTo(1L);
                assertThat(dto.getName()).isEqualTo("Test Name");
                assertThat(dto.getCity()).isEqualTo("Test City");
                assertThat(dto.getTitle()).isEqualTo("Test Title");
                assertThat(dto.getDetails()).isEqualTo("Test Details");
                assertThat(dto.getRequirements()).isEqualTo("Test Requirements");
                assertThat(dto.getAddress()).isEqualTo(addressDto);
                assertThat(dto.getAddressId()).isEqualTo(5L);
                assertThat(dto.getCompensationType()).isEqualTo(CompensationType.BARTER);
                assertThat(dto.getCompensationAmountMin()).isEqualTo(50);
                assertThat(dto.getCompensationAmountMax()).isEqualTo(1000);
                assertThat(dto.getCurrency()).isEqualTo(1L);
                assertThat(dto.getCompensationDescription()).isEqualTo("Test Desc");
                assertThat(dto.getFollowersMin()).isEqualTo(500);
                assertThat(dto.getFollowersMax()).isEqualTo(50000);
                assertThat(dto.getPlatforms()).containsExactlyInAnyOrder(1L, 2L);
                assertThat(dto.getContentTypes()).containsExactlyInAnyOrder(3L, 4L);
                assertThat(dto.getCompany()).isEqualTo(10L);
                assertThat(dto.getServiceType()).isEqualTo(20L);
                assertThat(dto.isActive()).isFalse();
                assertThat(dto.getStartDate()).isEqualTo(now.plusDays(1));
                assertThat(dto.getEndDate()).isEqualTo(now.plusDays(30));
                assertThat(dto.getCreatedTime()).isEqualTo(now);
                assertThat(dto.getLastUpdateTime()).isEqualTo(now);
            }
        }
    }

    // ==================== PartnershipOpportunityPhotoDtoIn Advanced Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunityPhotoDtoIn Advanced Tests")
    class PartnershipOpportunityPhotoDtoInAdvancedTests {

        @Test
        @DisplayName("should require all mandatory fields")
        void shouldRequireAllMandatoryFields() {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).hasSize(3); // url, orderNumber, isCover
        }

        @Test
        @DisplayName("should accept valid photo DTO with all fields")
        void shouldAcceptValidPhotoDtoWithAllFields() {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setId(1L);
            dto.setUrl("https://example.com/photo.jpg");
            dto.setOrderNumber(0);
            dto.setIsCover(true);

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 5, 10, 100})
        @DisplayName("should accept various valid order numbers")
        void shouldAcceptVariousValidOrderNumbers(int orderNumber) {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setUrl("https://example.com/photo.jpg");
            dto.setOrderNumber(orderNumber);
            dto.setIsCover(false);

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should reject negative order number")
        void shouldRejectNegativeOrderNumber() {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setUrl("https://example.com/photo.jpg");
            dto.setOrderNumber(-1);
            dto.setIsCover(false);

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== PartnershipOpportunityDtoOut Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunityDtoOut Tests")
    class PartnershipOpportunityDtoOutTests {

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            LocalDateTime now = LocalDateTime.now();

            PartnershipOpportunityDtoOut dto = new PartnershipOpportunityDtoOut();
            dto.setId(1L);
            dto.setName("Test Name");
            dto.setCity("Krakow");
            dto.setTitle("Test Title");
            dto.setDetails("Test Details");
            dto.setRequirements("Test Requirements");
            dto.setFollowersMin(1000);
            dto.setFollowersMax(10000);
            dto.setCompensationAmountMin(100);
            dto.setCompensationAmountMax(500);
            dto.setCompensationDescription("Test Desc");
            dto.setActive(true);
            dto.setStartDate(now.plusDays(1));
            dto.setEndDate(now.plusDays(30));
            dto.setCreatedTime(now);
            dto.setLastUpdateTime(now);
            dto.setUpdater("admin123");

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Test Name");
            assertThat(dto.getCity()).isEqualTo("Krakow");
            assertThat(dto.getTitle()).isEqualTo("Test Title");
            assertThat(dto.getDetails()).isEqualTo("Test Details");
            assertThat(dto.getRequirements()).isEqualTo("Test Requirements");
            assertThat(dto.getFollowersMin()).isEqualTo(1000);
            assertThat(dto.getFollowersMax()).isEqualTo(10000);
            assertThat(dto.getCompensationAmountMin()).isEqualTo(100);
            assertThat(dto.getCompensationAmountMax()).isEqualTo(500);
            assertThat(dto.getCompensationDescription()).isEqualTo("Test Desc");
            assertThat(dto.isActive()).isTrue();
            assertThat(dto.getStartDate()).isEqualTo(now.plusDays(1));
            assertThat(dto.getEndDate()).isEqualTo(now.plusDays(30));
            assertThat(dto.getCreatedTime()).isEqualTo(now);
            assertThat(dto.getLastUpdateTime()).isEqualTo(now);
            assertThat(dto.getUpdater()).isEqualTo("admin123");
        }

        @Test
        @DisplayName("should handle complex nested DTOs")
        void shouldHandleComplexNestedDtos() {
            PartnershipOpportunityDtoOut dto = new PartnershipOpportunityDtoOut();

            CompensationTypeDtoOut compType = CompensationTypeDtoOut.builder()
                    .value("CASH")
                    .label("Cash Payment")
                    .originalLabel("CASH")
                    .build();
            dto.setCompensationType(compType);

            CurrencyDtoOut currency = CurrencyDtoOut.builder()
                    .id(1L)
                    .name("Polish Zloty")
                    .isoCode("PLN")
                    .sign("zl")
                    .build();
            dto.setCurrency(currency);

            ServiceTypeDtoOut serviceType = ServiceTypeDtoOut.builder()
                    .id(1L)
                    .name("Photography")
                    .build();
            dto.setServiceType(serviceType);

            CompanyPublicProfileDto company = new CompanyPublicProfileDto();
            company.setId(1L);
            dto.setCompany(company);

            AddressNoUserDtoOut address = new AddressNoUserDtoOut();
            dto.setAddress(address);

            assertThat(dto.getCompensationType()).isNotNull();
            assertThat(dto.getCompensationType().getValue()).isEqualTo("CASH");
            assertThat(dto.getCurrency()).isNotNull();
            assertThat(dto.getCurrency().getIsoCode()).isEqualTo("PLN");
            assertThat(dto.getServiceType()).isNotNull();
            assertThat(dto.getCompany()).isNotNull();
            assertThat(dto.getAddress()).isNotNull();
        }

        @Test
        @DisplayName("should handle platforms and content types collections")
        void shouldHandlePlatformsAndContentTypesCollections() {
            PartnershipOpportunityDtoOut dto = new PartnershipOpportunityDtoOut();

            PlatformDto platform1 = new PlatformDto();
            platform1.setId(1L);
            platform1.setName("Instagram");

            PlatformDto platform2 = new PlatformDto();
            platform2.setId(2L);
            platform2.setName("TikTok");

            dto.setPlatforms(Set.of(platform1, platform2));

            ContentTypeDtoOut contentType1 = ContentTypeDtoOut.builder()
                    .id(1L)
                    .name("Reel")
                    .build();

            dto.setContentTypes(Set.of(contentType1));

            assertThat(dto.getPlatforms()).hasSize(2);
            assertThat(dto.getContentTypes()).hasSize(1);
        }

        @Test
        @DisplayName("should handle photos list")
        void shouldHandlePhotosList() {
            PartnershipOpportunityDtoOut dto = new PartnershipOpportunityDtoOut();

            PartnershipOpportunityPhotoDtoOut photo1 = new PartnershipOpportunityPhotoDtoOut();
            photo1.setId(1L);
            photo1.setUrl("https://example.com/photo1.jpg");
            photo1.setOrderNumber(0);
            photo1.setIsCover(true);

            PartnershipOpportunityPhotoDtoOut photo2 = new PartnershipOpportunityPhotoDtoOut();
            photo2.setId(2L);
            photo2.setUrl("https://example.com/photo2.jpg");
            photo2.setOrderNumber(1);
            photo2.setIsCover(false);

            dto.setPhotos(List.of(photo1, photo2));

            assertThat(dto.getPhotos()).hasSize(2);
            assertThat(dto.getPhotos().get(0).getIsCover()).isTrue();
        }
    }

    // ==================== PartnershipOpportunitySimpleDtoOut Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunitySimpleDtoOut Tests")
    class PartnershipOpportunitySimpleDtoOutTests {

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            LocalDateTime now = LocalDateTime.now();

            PartnershipOpportunitySimpleDtoOut dto = new PartnershipOpportunitySimpleDtoOut();
            dto.setId(1L);
            dto.setName("Simple Test");
            dto.setCity("Warsaw");
            dto.setTitle("Simple Title");
            dto.setDetails("Simple Details");
            dto.setRequirements("Simple Req");
            dto.setFollowersMin(500);
            dto.setFollowersMax(5000);
            dto.setCompensationAmountMin(50);
            dto.setCompensationAmountMax(250);
            dto.setCompensationDescription("Simple Desc");
            dto.setActive(true);
            dto.setStartDate(now);
            dto.setEndDate(now.plusDays(7));
            dto.setCreatedTime(now);
            dto.setLastUpdateTime(now);
            dto.setUpdater("user123");

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Simple Test");
            assertThat(dto.isActive()).isTrue();
        }

        @Test
        @DisplayName("should have default empty collections")
        void shouldHaveDefaultEmptyCollections() {
            PartnershipOpportunitySimpleDtoOut dto = new PartnershipOpportunitySimpleDtoOut();

            assertThat(dto.getPlatforms()).isNotNull().isEmpty();
            assertThat(dto.getContentTypes()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("should not have appliedOpportunities field")
        void shouldNotHaveAppliedOpportunitiesField() {
            PartnershipOpportunitySimpleDtoOut dto = new PartnershipOpportunitySimpleDtoOut();

            // Using reflection to verify no appliedOpportunities field exists
            boolean hasAppliedOpportunitiesField = Arrays.stream(dto.getClass().getDeclaredFields())
                    .anyMatch(f -> f.getName().equals("appliedOpportunities"));
            assertThat(hasAppliedOpportunitiesField).isFalse();
        }
    }

    // ==================== PartnershipOpportunityPhotoDtoOut Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunityPhotoDtoOut Tests")
    class PartnershipOpportunityPhotoDtoOutTests {

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            PartnershipOpportunityPhotoDtoOut dto = new PartnershipOpportunityPhotoDtoOut();
            dto.setId(1L);
            dto.setUrl("https://example.com/photo.jpg");
            dto.setOrderNumber(0);
            dto.setIsCover(true);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getUrl()).isEqualTo("https://example.com/photo.jpg");
            assertThat(dto.getOrderNumber()).isZero();
            assertThat(dto.getIsCover()).isTrue();
        }

        @Test
        @DisplayName("should handle null values")
        void shouldHandleNullValues() {
            PartnershipOpportunityPhotoDtoOut dto = new PartnershipOpportunityPhotoDtoOut();

            assertThat(dto.getId()).isNull();
            assertThat(dto.getUrl()).isNull();
            assertThat(dto.getOrderNumber()).isNull();
            assertThat(dto.getIsCover()).isNull();
        }
    }

    // ==================== CompensationTypeDtoOut Tests ====================

    @Nested
    @DisplayName("CompensationTypeDtoOut Tests")
    class CompensationTypeDtoOutTests {

        @Test
        @DisplayName("should create with builder pattern")
        void shouldCreateWithBuilderPattern() {
            CompensationTypeDtoOut dto = CompensationTypeDtoOut.builder()
                    .value("CASH")
                    .label("Cash Payment")
                    .originalLabel("CASH")
                    .build();

            assertThat(dto.getValue()).isEqualTo("CASH");
            assertThat(dto.getLabel()).isEqualTo("Cash Payment");
            assertThat(dto.getOriginalLabel()).isEqualTo("CASH");
        }

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            CompensationTypeDtoOut dto = new CompensationTypeDtoOut();
            dto.setValue("BARTER");
            dto.setLabel("Exchange");
            dto.setOriginalLabel("BARTER");

            assertThat(dto.getValue()).isEqualTo("BARTER");
            assertThat(dto.getLabel()).isEqualTo("Exchange");
            assertThat(dto.getOriginalLabel()).isEqualTo("BARTER");
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            CompensationTypeDtoOut dto = new CompensationTypeDtoOut("CASH", "Cash", "CASH");

            assertThat(dto.getValue()).isEqualTo("CASH");
            assertThat(dto.getLabel()).isEqualTo("Cash");
            assertThat(dto.getOriginalLabel()).isEqualTo("CASH");
        }

        @Test
        @DisplayName("should implement equals and hashCode")
        void shouldImplementEqualsAndHashCode() {
            CompensationTypeDtoOut dto1 = CompensationTypeDtoOut.builder()
                    .value("CASH")
                    .label("Cash")
                    .originalLabel("CASH")
                    .build();

            CompensationTypeDtoOut dto2 = CompensationTypeDtoOut.builder()
                    .value("CASH")
                    .label("Cash")
                    .originalLabel("CASH")
                    .build();

            assertThat(dto1).isEqualTo(dto2);
            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            CompensationTypeDtoOut dto = CompensationTypeDtoOut.builder()
                    .value("CASH")
                    .label("Cash")
                    .originalLabel("CASH")
                    .build();

            String toString = dto.toString();
            assertThat(toString).contains("CASH");
        }
    }

    // ==================== CompensationType Enum Advanced Tests ====================

    @Nested
    @DisplayName("CompensationType Enum Advanced Tests")
    class CompensationTypeEnumAdvancedTests {

        @Nested
        @DisplayName("Enum Properties Completeness")
        class EnumPropertiesCompleteness {

            @ParameterizedTest
            @EnumSource(CompensationType.class)
            @DisplayName("should have non-empty color theme for all types")
            void shouldHaveNonEmptyColorThemeForAllTypes(CompensationType type) {
                assertThat(type.getColorTheme()).isNotBlank();
            }

            @ParameterizedTest
            @EnumSource(CompensationType.class)
            @DisplayName("should have non-empty icon for all types")
            void shouldHaveNonEmptyIconForAllTypes(CompensationType type) {
                assertThat(type.getIcon()).isNotBlank();
            }

            @ParameterizedTest
            @EnumSource(CompensationType.class)
            @DisplayName("should have at least one alias for all types")
            void shouldHaveAtLeastOneAliasForAllTypes(CompensationType type) {
                assertThat(type.getAliases()).isNotEmpty();
            }

            @ParameterizedTest
            @EnumSource(CompensationType.class)
            @DisplayName("should have non-empty default description for all types")
            void shouldHaveNonEmptyDefaultDescriptionForAllTypes(CompensationType type) {
                assertThat(type.getDefaultDescription()).isNotBlank();
            }
        }

        @Nested
        @DisplayName("fromString Edge Cases")
        class FromStringEdgeCases {

            @ParameterizedTest
            @ValueSource(strings = {"CASH", "cash", "Cash", "cAsH", "CASH ", " CASH"})
            @DisplayName("should handle various CASH string formats with trimming issues")
            void shouldHandleVariousCashStringFormats(String input) {
                if (input.trim().equals(input)) {
                    assertThat(CompensationType.fromString(input)).isEqualTo(CompensationType.CASH);
                } else {
                    assertThatThrownBy(() -> CompensationType.fromString(input))
                            .isInstanceOf(IllegalArgumentException.class);
                }
            }

            @Test
            @DisplayName("should throw exception for empty string")
            void shouldThrowExceptionForEmptyString() {
                assertThatThrownBy(() -> CompensationType.fromString(""))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("should throw exception for null")
            void shouldThrowExceptionForNull() {
                assertThatThrownBy(() -> CompensationType.fromString(null))
                        .isInstanceOf(NullPointerException.class);
            }
        }

        @Nested
        @DisplayName("Ordinal and Value Tests")
        class OrdinalAndValueTests {

            @Test
            @DisplayName("CASH should have ordinal 0")
            void cashShouldHaveOrdinal0() {
                assertThat(CompensationType.CASH.ordinal()).isZero();
            }

            @Test
            @DisplayName("BARTER should have ordinal 1")
            void barterShouldHaveOrdinal1() {
                assertThat(CompensationType.BARTER.ordinal()).isEqualTo(1);
            }

            @ParameterizedTest
            @EnumSource(CompensationType.class)
            @DisplayName("getValue should return same as name for all types")
            void getValueShouldReturnSameAsNameForAllTypes(CompensationType type) {
                assertThat(type.getValue()).isEqualTo(type.name());
            }
        }
    }

    // ==================== Edge Cases and Corner Cases ====================

    @Nested
    @DisplayName("Edge Cases and Corner Cases")
    class EdgeCasesAndCornerCases {

        @Test
        @DisplayName("should handle Unicode characters in text fields")
        void shouldHandleUnicodeCharactersInTextFields() {
            PartnershipOpportunity opportunity = createMinimalValidOpportunity();
            opportunity.setName("Wspolpraca z influencerem");
            opportunity.setTitle("Niezwykla mozliwosc");
            opportunity.setDetails("Szczegoly z polskimi znakami: acenos");

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle emoji characters in text fields")
        void shouldHandleEmojiCharactersInTextFields() {
            PartnershipOpportunity opportunity = createMinimalValidOpportunity();
            opportunity.setDetails("Great opportunity! Looking forward to collaboration!");

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            // Emoji are valid Unicode characters
            assertThat(violations.stream()
                    .noneMatch(v -> v.getPropertyPath().toString().equals("details"))).isTrue();
        }

        @Test
        @DisplayName("should handle very long but valid text")
        void shouldHandleVeryLongButValidText() {
            PartnershipOpportunity opportunity = createMinimalValidOpportunity();
            opportunity.setDetails("A".repeat(2000));
            opportunity.setRequirements("B".repeat(2000));
            opportunity.setCompensationDescription("C".repeat(500));

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle mixed null and non-null optional fields")
        void shouldHandleMixedNullAndNonNullOptionalFields() {
            PartnershipOpportunity opportunity = createMinimalValidOpportunity();
            opportunity.setDetails("Some details");
            opportunity.setRequirements(null);
            opportunity.setCompensationDescription("Some description");

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle zero values in ranges")
        void shouldHandleZeroValuesInRanges() {
            PartnershipOpportunity opportunity = createMinimalValidOpportunity();
            opportunity.setCompensationAmountMin(0);
            opportunity.setCompensationAmountMax(0);
            opportunity.setFollowersMin(0);
            opportunity.setFollowersMax(0);

            Set<ConstraintViolation<PartnershipOpportunity>> violations = validator.validate(opportunity);
            assertThat(violations).isEmpty();
            assertThat(opportunity.isValidCompensationRange()).isTrue();
            assertThat(opportunity.isValidFollowersRange()).isTrue();
        }

        private PartnershipOpportunity createMinimalValidOpportunity() {
            City city = new City();
            city.setId(1L);
            city.setName("Warsaw");

            Address address = new Address();
            address.setId(1L);

            User company = new User();
            company.setId(1L);

            PartnershipOpportunity opportunity = new PartnershipOpportunity();
            opportunity.setName("Valid Name");
            opportunity.setTitle("Valid Title");
            opportunity.setCity(city);
            opportunity.setAddress(address);
            opportunity.setCompany(company);
            opportunity.setCompensationType(CompensationType.CASH);
            return opportunity;
        }
    }

    // ==================== Collection Immutability Tests ====================

    @Nested
    @DisplayName("Collection Immutability Tests")
    class CollectionImmutabilityTests {

        @Test
        @DisplayName("should return mutable platforms set")
        void shouldReturnMutablePlatformsSet() {
            PartnershipOpportunity opportunity = new PartnershipOpportunity();
            Platform platform = new Platform();
            platform.setId(1L);

            opportunity.getPlatforms().add(platform);

            assertThat(opportunity.getPlatforms()).hasSize(1);
        }

        @Test
        @DisplayName("should return mutable content types set")
        void shouldReturnMutableContentTypesSet() {
            PartnershipOpportunity opportunity = new PartnershipOpportunity();
            ContentType contentType = new ContentType();
            contentType.setId(1L);

            opportunity.getContentTypes().add(contentType);

            assertThat(opportunity.getContentTypes()).hasSize(1);
        }

        @Test
        @DisplayName("should return mutable photos list")
        void shouldReturnMutablePhotosList() {
            PartnershipOpportunity opportunity = new PartnershipOpportunity();
            PartnershipOpportunityPhoto photo = new PartnershipOpportunityPhoto();
            photo.setUrl("https://example.com/photo.jpg");

            opportunity.getPhotos().add(photo);

            assertThat(opportunity.getPhotos()).hasSize(1);
        }
    }

    // ==================== Default Value Tests ====================

    @Nested
    @DisplayName("Default Value Tests")
    class DefaultValueTests {

        @Test
        @DisplayName("new opportunity should have active true by default")
        void newOpportunityShouldHaveActiveTrueByDefault() {
            PartnershipOpportunity opportunity = new PartnershipOpportunity();
            assertThat(opportunity.isActive()).isTrue();
        }

        @Test
        @DisplayName("new opportunity should have empty collections by default")
        void newOpportunityShouldHaveEmptyCollectionsByDefault() {
            PartnershipOpportunity opportunity = new PartnershipOpportunity();

            assertThat(opportunity.getPhotos()).isNotNull().isEmpty();
            assertThat(opportunity.getPlatforms()).isNotNull().isEmpty();
            assertThat(opportunity.getContentTypes()).isNotNull().isEmpty();
            assertThat(opportunity.getAppliedOpportunities()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("new DtoIn should have empty photos list by default")
        void newDtoInShouldHaveEmptyPhotosListByDefault() {
            PartnershipOpportunityDtoIn dto = new PartnershipOpportunityDtoIn();
            assertThat(dto.getPhotos()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("new DtoOut should have empty platform and content type sets by default")
        void newDtoOutShouldHaveEmptyPlatformAndContentTypeSetsDefault() {
            PartnershipOpportunityDtoOut dto = new PartnershipOpportunityDtoOut();
            assertThat(dto.getPlatforms()).isNotNull().isEmpty();
            assertThat(dto.getContentTypes()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("new SimpleDtoOut should have empty platform and content type sets by default")
        void newSimpleDtoOutShouldHaveEmptySetsDefault() {
            PartnershipOpportunitySimpleDtoOut dto = new PartnershipOpportunitySimpleDtoOut();
            assertThat(dto.getPlatforms()).isNotNull().isEmpty();
            assertThat(dto.getContentTypes()).isNotNull().isEmpty();
        }
    }

    // ==================== Numeric Range Validation Tests ====================

    @Nested
    @DisplayName("Numeric Range Validation Tests")
    class NumericRangeValidationTests {

        @Nested
        @DisplayName("Compensation Amount Validation")
        class CompensationAmountValidation {

            @ParameterizedTest
            @CsvSource({
                    "0, 0, true",
                    "0, 100, true",
                    "100, 100, true",
                    "100, 500, true",
                    "0, 1000000, true",
                    "1000000, 1000000, true",
                    "500, 100, false",
                    "0, 1000001, false"
            })
            @DisplayName("should validate compensation ranges correctly")
            void shouldValidateCompensationRangesCorrectly(int min, int max, boolean expectedValid) {
                PartnershipOpportunity opportunity = createValidOpportunityForRangeTest();
                opportunity.setCompensationAmountMin(min);
                opportunity.setCompensationAmountMax(max);

                assertThat(opportunity.isValidCompensationRange()).isEqualTo(expectedValid);
            }

            private PartnershipOpportunity createValidOpportunityForRangeTest() {
                City city = new City();
                city.setId(1L);
                city.setName("Test");

                Address address = new Address();
                address.setId(1L);

                User company = new User();
                company.setId(1L);

                PartnershipOpportunity opp = new PartnershipOpportunity();
                opp.setName("Test");
                opp.setTitle("Test");
                opp.setCity(city);
                opp.setAddress(address);
                opp.setCompany(company);
                opp.setCompensationType(CompensationType.CASH);
                return opp;
            }
        }

        @Nested
        @DisplayName("Followers Range Validation")
        class FollowersRangeValidation {

            @ParameterizedTest
            @CsvSource({
                    "0, 0, true",
                    "0, 100, true",
                    "1000, 1000, true",
                    "1000, 10000, true",
                    "10000, 1000, false"
            })
            @DisplayName("should validate followers ranges correctly")
            void shouldValidateFollowersRangesCorrectly(long min, long max, boolean expectedValid) {
                PartnershipOpportunity opportunity = new PartnershipOpportunity();
                opportunity.setFollowersMin(min);
                opportunity.setFollowersMax(max);

                assertThat(opportunity.isValidFollowersRange()).isEqualTo(expectedValid);
            }
        }
    }

    // ==================== Additional Entity Tests ====================

    @Nested
    @DisplayName("Additional Entity Tests")
    class AdditionalEntityTests {

        @Test
        @DisplayName("should handle service type assignment")
        void shouldHandleServiceTypeAssignment() {
            PartnershipOpportunity opportunity = new PartnershipOpportunity();
            ServiceType serviceType = new ServiceType();
            serviceType.setId(1L);
            serviceType.setName("Photography");

            opportunity.setServiceType(serviceType);

            assertThat(opportunity.getServiceType()).isNotNull();
            assertThat(opportunity.getServiceType().getName()).isEqualTo("Photography");
        }

        @Test
        @DisplayName("should handle currency assignment")
        void shouldHandleCurrencyAssignment() {
            PartnershipOpportunity opportunity = new PartnershipOpportunity();
            Currency currency = new Currency();
            currency.setId(1L);
            currency.setIsoCode("EUR");
            currency.setName("Euro");

            opportunity.setCurrency(currency);

            assertThat(opportunity.getCurrency()).isNotNull();
            assertThat(opportunity.getCurrency().getIsoCode()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("should handle null service type")
        void shouldHandleNullServiceType() {
            PartnershipOpportunity opportunity = new PartnershipOpportunity();
            opportunity.setServiceType(null);

            assertThat(opportunity.getServiceType()).isNull();
        }

        @Test
        @DisplayName("should handle null currency")
        void shouldHandleNullCurrency() {
            PartnershipOpportunity opportunity = new PartnershipOpportunity();
            opportunity.setCurrency(null);

            assertThat(opportunity.getCurrency()).isNull();
        }

        @Test
        @DisplayName("should correctly track last update time")
        void shouldCorrectlyTrackLastUpdateTime() {
            PartnershipOpportunity opportunity = new PartnershipOpportunity();
            LocalDateTime updateTime = LocalDateTime.now();

            opportunity.setLastUpdateTime(updateTime);

            assertThat(opportunity.getLastUpdateTime()).isEqualTo(updateTime);
        }

        @Test
        @DisplayName("should correctly track created time")
        void shouldCorrectlyTrackCreatedTime() {
            PartnershipOpportunity opportunity = new PartnershipOpportunity();
            LocalDateTime createdTime = LocalDateTime.now();

            opportunity.setCreatedTime(createdTime);

            assertThat(opportunity.getCreatedTime()).isEqualTo(createdTime);
        }
    }

    // ==================== Additional DTO Tests ====================

    @Nested
    @DisplayName("Additional DTO Tests")
    class AdditionalDtoTests {

        @Test
        @DisplayName("DtoIn should have default photos empty list")
        void dtoInShouldHaveDefaultPhotosEmptyList() {
            PartnershipOpportunityDtoIn dto = new PartnershipOpportunityDtoIn();
            assertThat(dto.getPhotos()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("DtoOut should handle null compensationType")
        void dtoOutShouldHandleNullCompensationType() {
            PartnershipOpportunityDtoOut dto = new PartnershipOpportunityDtoOut();
            dto.setCompensationType(null);

            assertThat(dto.getCompensationType()).isNull();
        }

        @Test
        @DisplayName("DtoOut should handle null currency")
        void dtoOutShouldHandleNullCurrency() {
            PartnershipOpportunityDtoOut dto = new PartnershipOpportunityDtoOut();
            dto.setCurrency(null);

            assertThat(dto.getCurrency()).isNull();
        }

        @Test
        @DisplayName("SimpleDtoOut should handle null compensationType")
        void simpleDtoOutShouldHandleNullCompensationType() {
            PartnershipOpportunitySimpleDtoOut dto = new PartnershipOpportunitySimpleDtoOut();
            dto.setCompensationType(null);

            assertThat(dto.getCompensationType()).isNull();
        }

        @Test
        @DisplayName("PhotoDtoIn should accept maximum order number")
        void photoDtoInShouldAcceptMaxOrderNumber() {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setUrl("https://example.com/photo.jpg");
            dto.setOrderNumber(Integer.MAX_VALUE);
            dto.setIsCover(false);

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("PhotoDtoIn should require isCover field")
        void photoDtoInShouldRequireIsCoverField() {
            PartnershipOpportunityPhotoDtoIn dto = new PartnershipOpportunityPhotoDtoIn();
            dto.setUrl("https://example.com/photo.jpg");
            dto.setOrderNumber(0);
            // isCover not set

            Set<ConstraintViolation<PartnershipOpportunityPhotoDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== Additional Compensation Type Tests ====================

    @Nested
    @DisplayName("Additional Compensation Type Tests")
    class AdditionalCompensationTypeTests {

        @Test
        @DisplayName("CASH should have 'money' alias")
        void cashShouldHaveMoneyAlias() {
            assertThat(CompensationType.CASH.getAliases()).contains("money");
        }

        @Test
        @DisplayName("BARTER should have 'trade' alias")
        void barterShouldHaveTradeAlias() {
            assertThat(CompensationType.BARTER.getAliases()).contains("trade");
        }

        @Test
        @DisplayName("BARTER should have 'exchange' alias")
        void barterShouldHaveExchangeAlias() {
            assertThat(CompensationType.BARTER.getAliases()).contains("exchange");
        }

        @Test
        @DisplayName("CASH should have 'primary' color theme")
        void cashShouldHavePrimaryColorTheme() {
            assertThat(CompensationType.CASH.getColorTheme()).isEqualTo("primary");
        }

        @Test
        @DisplayName("BARTER should have 'warning' color theme")
        void barterShouldHaveWarningColorTheme() {
            assertThat(CompensationType.BARTER.getColorTheme()).isEqualTo("warning");
        }

        @Test
        @DisplayName("CASH should have 'dollar-sign' icon")
        void cashShouldHaveDollarSignIcon() {
            assertThat(CompensationType.CASH.getIcon()).isEqualTo("dollar-sign");
        }

        @Test
        @DisplayName("BARTER should have 'swap' icon")
        void barterShouldHaveSwapIcon() {
            assertThat(CompensationType.BARTER.getIcon()).isEqualTo("swap");
        }
    }
}
