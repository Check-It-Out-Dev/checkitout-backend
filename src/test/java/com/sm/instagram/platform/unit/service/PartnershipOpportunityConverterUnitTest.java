package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressRepository;
import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.city.CityRepository;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.util.mappers.MappingConfigurer;
import com.sm.instagram.platform.contenttype.ContentType;
import com.sm.instagram.platform.contenttype.ContentTypeConverter;
import com.sm.instagram.platform.currency.Currency;
import com.sm.instagram.platform.currency.CurrencyConverter;
import com.sm.instagram.platform.dictionary.DictionaryService;
import com.sm.instagram.platform.partnershipopportunities.*;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformConverter;
import com.sm.instagram.platform.servicetype.ServiceType;
import com.sm.instagram.platform.servicetype.ServiceTypeConverter;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.Converter;
import org.modelmapper.MappingException;
import org.modelmapper.ModelMapper;
import org.modelmapper.spi.MappingContext;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PartnershipOpportunityConverter, PartnershipOpportunityMapping,
 * and CompensationType enum.
 * Tests all conversion methods, null handling, edge cases, and field mappings.
 * Uses pure Mockito - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PartnershipOpportunity Converter and Mapping Unit Tests")
class PartnershipOpportunityConverterUnitTest {

    @Mock
    private PartnershipOpportunityRepository partnershipOpportunityRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private CityRepository cityRepository;

    @Mock
    private ServiceTypeConverter serviceTypeConverter;

    @Mock
    private UserConverter userConverter;

    @Mock
    private PlatformConverter platformConverter;

    @Mock
    private ContentTypeConverter contentTypeConverter;

    @Mock
    private CurrencyConverter currencyConverter;

    @Mock
    private DictionaryService dictionaryService;

    @Mock
    private MappingContext<Long, PartnershipOpportunity> longToEntityContext;

    @Mock
    private MappingContext<PartnershipOpportunity, Long> entityToLongContext;

    private PartnershipOpportunityConverter partnershipOpportunityConverter;
    private PartnershipOpportunityMapping partnershipOpportunityMapping;

    // Test fixtures
    private PartnershipOpportunity testOpportunity;
    private PartnershipOpportunityDtoIn testDtoIn;
    private User testCompany;
    private Address testAddress;
    private City testCity;
    private Currency testCurrency;
    private ServiceType testServiceType;
    private Platform testPlatform;
    private ContentType testContentType;

    @BeforeEach
    void setUp() {
        // Create converter with mocked repository
        partnershipOpportunityConverter = new PartnershipOpportunityConverter(partnershipOpportunityRepository);

        // Create test fixtures
        testCompany = new User();
        testCompany.setId(1L);

        testCity = new City();
        testCity.setId(1L);
        testCity.setName("Warsaw");
        testCity.setCountry("Poland");

        testAddress = new Address();
        testAddress.setId(1L);
        testAddress.setStreet("Test Street");
        testAddress.setCity("Warsaw");
        testAddress.setPostalCode("00-001");
        testAddress.setCountry("Poland");

        testCurrency = new Currency();
        testCurrency.setId(1L);
        testCurrency.setIsoCode("PLN");
        testCurrency.setName("Polish Zloty");
        testCurrency.setSign("zl");

        testServiceType = new ServiceType();
        testServiceType.setId(1L);
        testServiceType.setName("Photography");

        testPlatform = new Platform();
        testPlatform.setId(1L);
        testPlatform.setName("Instagram");

        testContentType = new ContentType();
        testContentType.setId(1L);
        testContentType.setName("Reel");

        testOpportunity = new PartnershipOpportunity();
        testOpportunity.setId(100L);
        testOpportunity.setName("Test Opportunity");
        testOpportunity.setTitle("Test Title");
        testOpportunity.setDetails("Test Details");
        testOpportunity.setRequirements("Test Requirements");
        testOpportunity.setCompensationType(CompensationType.CASH);
        testOpportunity.setCompensationAmountMin(1000);
        testOpportunity.setCompensationAmountMax(5000);
        testOpportunity.setCompensationDescription("Cash payment");
        testOpportunity.setFollowersMin(1000);
        testOpportunity.setFollowersMax(100000);
        testOpportunity.setStartDate(LocalDateTime.now().plusDays(1));
        testOpportunity.setEndDate(LocalDateTime.now().plusDays(30));
        testOpportunity.setActive(true);
        testOpportunity.setCompany(testCompany);
        testOpportunity.setCity(testCity);
        testOpportunity.setAddress(testAddress);
        testOpportunity.setCurrency(testCurrency);
        testOpportunity.setServiceType(testServiceType);
        testOpportunity.setPlatforms(Set.of(testPlatform));
        testOpportunity.setContentTypes(Set.of(testContentType));

        testDtoIn = new PartnershipOpportunityDtoIn();
        testDtoIn.setName("Test Opportunity");
        testDtoIn.setTitle("Test Title");
        testDtoIn.setDetails("Test Details");
        testDtoIn.setRequirements("Test Requirements");
        testDtoIn.setCompensationType(CompensationType.CASH);
        testDtoIn.setCompensationAmountMin(1000);
        testDtoIn.setCompensationAmountMax(5000);
        testDtoIn.setCompensationDescription("Cash payment");
        testDtoIn.setFollowersMin(1000);
        testDtoIn.setFollowersMax(100000);
        testDtoIn.setStartDate(LocalDateTime.now().plusDays(1));
        testDtoIn.setEndDate(LocalDateTime.now().plusDays(30));
        testDtoIn.setActive(true);
        testDtoIn.setCompany(1L);
        testDtoIn.setCity("Warsaw");
        testDtoIn.setAddressId(1L);
        testDtoIn.setCurrency(1L);
        testDtoIn.setServiceType(1L);
        testDtoIn.setPlatforms(Set.of(1L));
        testDtoIn.setContentTypes(Set.of(1L));

        // Create the mapping with mocked dependencies
        partnershipOpportunityMapping = new PartnershipOpportunityMapping(
                addressRepository,
                cityRepository,
                serviceTypeConverter,
                userConverter,
                platformConverter,
                contentTypeConverter,
                currencyConverter
        );
    }

    // ==================== PartnershipOpportunityConverter Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunityConverter")
    class PartnershipOpportunityConverterTests {

        @Nested
        @DisplayName("toPartnershipOpportunityConverter")
        class ToPartnershipOpportunityConverter {

            @Test
            @DisplayName("should convert Long to PartnershipOpportunity when found")
            void shouldConvertLongToPartnershipOpportunityWhenFound() {
                // Given
                when(longToEntityContext.getSource()).thenReturn(100L);
                when(partnershipOpportunityRepository.findById(100L)).thenReturn(Optional.of(testOpportunity));
                Converter<Long, PartnershipOpportunity> converter =
                        partnershipOpportunityConverter.toPartnershipOpportunityConverter();

                // When
                PartnershipOpportunity result = converter.convert(longToEntityContext);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(100L);
                assertThat(result.getName()).isEqualTo("Test Opportunity");
                verify(partnershipOpportunityRepository).findById(100L);
            }

            @Test
            @DisplayName("should throw ResourceNotFoundException when opportunity not found")
            void shouldThrowResourceNotFoundExceptionWhenOpportunityNotFound() {
                // Given
                when(longToEntityContext.getSource()).thenReturn(999L);
                when(partnershipOpportunityRepository.findById(999L)).thenReturn(Optional.empty());
                Converter<Long, PartnershipOpportunity> converter =
                        partnershipOpportunityConverter.toPartnershipOpportunityConverter();

                // When/Then
                assertThatThrownBy(() -> converter.convert(longToEntityContext))
                        .isInstanceOf(ResourceNotFoundException.class);
                verify(partnershipOpportunityRepository).findById(999L);
            }

            @Test
            @DisplayName("should throw IllegalArgumentException when ID is null")
            void shouldThrowIllegalArgumentExceptionWhenIdIsNull() {
                // Given
                when(longToEntityContext.getSource()).thenReturn(null);
                Converter<Long, PartnershipOpportunity> converter =
                        partnershipOpportunityConverter.toPartnershipOpportunityConverter();

                // When/Then
                assertThatThrownBy(() -> converter.convert(longToEntityContext))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Partnership opportunity ID cannot be null");
            }

            @Test
            @DisplayName("should call repository findById with correct ID")
            void shouldCallRepositoryFindByIdWithCorrectId() {
                // Given
                when(longToEntityContext.getSource()).thenReturn(42L);
                when(partnershipOpportunityRepository.findById(42L)).thenReturn(Optional.of(testOpportunity));
                Converter<Long, PartnershipOpportunity> converter =
                        partnershipOpportunityConverter.toPartnershipOpportunityConverter();

                // When
                converter.convert(longToEntityContext);

                // Then
                verify(partnershipOpportunityRepository).findById(42L);
            }

            @Test
            @DisplayName("should return opportunity with all fields populated")
            void shouldReturnOpportunityWithAllFieldsPopulated() {
                // Given
                when(longToEntityContext.getSource()).thenReturn(100L);
                when(partnershipOpportunityRepository.findById(100L)).thenReturn(Optional.of(testOpportunity));
                Converter<Long, PartnershipOpportunity> converter =
                        partnershipOpportunityConverter.toPartnershipOpportunityConverter();

                // When
                PartnershipOpportunity result = converter.convert(longToEntityContext);

                // Then
                assertThat(result.getId()).isEqualTo(100L);
                assertThat(result.getName()).isEqualTo("Test Opportunity");
                assertThat(result.getTitle()).isEqualTo("Test Title");
                assertThat(result.getCompensationType()).isEqualTo(CompensationType.CASH);
                assertThat(result.getCompany()).isNotNull();
                assertThat(result.getCity()).isNotNull();
            }

            @Test
            @DisplayName("should handle different valid IDs")
            void shouldHandleDifferentValidIds() {
                // Given
                PartnershipOpportunity opportunity1 = new PartnershipOpportunity();
                opportunity1.setId(1L);
                PartnershipOpportunity opportunity2 = new PartnershipOpportunity();
                opportunity2.setId(2L);

                when(partnershipOpportunityRepository.findById(1L)).thenReturn(Optional.of(opportunity1));
                when(partnershipOpportunityRepository.findById(2L)).thenReturn(Optional.of(opportunity2));

                Converter<Long, PartnershipOpportunity> converter =
                        partnershipOpportunityConverter.toPartnershipOpportunityConverter();

                // When
                when(longToEntityContext.getSource()).thenReturn(1L);
                PartnershipOpportunity result1 = converter.convert(longToEntityContext);

                when(longToEntityContext.getSource()).thenReturn(2L);
                PartnershipOpportunity result2 = converter.convert(longToEntityContext);

                // Then
                assertThat(result1.getId()).isEqualTo(1L);
                assertThat(result2.getId()).isEqualTo(2L);
            }
        }

        @Nested
        @DisplayName("fromPartnershipOpportunityConverter")
        class FromPartnershipOpportunityConverter {

            @Test
            @DisplayName("should convert PartnershipOpportunity to Long ID")
            void shouldConvertPartnershipOpportunityToLongId() {
                // Given
                when(entityToLongContext.getSource()).thenReturn(testOpportunity);
                Converter<PartnershipOpportunity, Long> converter =
                        partnershipOpportunityConverter.fromPartnershipOpportunityConverter();

                // When
                Long result = converter.convert(entityToLongContext);

                // Then
                assertThat(result).isEqualTo(100L);
            }

            @Test
            @DisplayName("should return null when opportunity is null")
            void shouldReturnNullWhenOpportunityIsNull() {
                // Given
                when(entityToLongContext.getSource()).thenReturn(null);
                Converter<PartnershipOpportunity, Long> converter =
                        partnershipOpportunityConverter.fromPartnershipOpportunityConverter();

                // When
                Long result = converter.convert(entityToLongContext);

                // Then
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should return correct ID for opportunity with ID")
            void shouldReturnCorrectIdForOpportunityWithId() {
                // Given
                PartnershipOpportunity opportunity = new PartnershipOpportunity();
                opportunity.setId(999L);
                when(entityToLongContext.getSource()).thenReturn(opportunity);
                Converter<PartnershipOpportunity, Long> converter =
                        partnershipOpportunityConverter.fromPartnershipOpportunityConverter();

                // When
                Long result = converter.convert(entityToLongContext);

                // Then
                assertThat(result).isEqualTo(999L);
            }

            @Test
            @DisplayName("should not interact with repository")
            void shouldNotInteractWithRepository() {
                // Given
                when(entityToLongContext.getSource()).thenReturn(testOpportunity);
                Converter<PartnershipOpportunity, Long> converter =
                        partnershipOpportunityConverter.fromPartnershipOpportunityConverter();

                // When
                converter.convert(entityToLongContext);

                // Then
                verifyNoInteractions(partnershipOpportunityRepository);
            }

            @Test
            @DisplayName("should handle opportunity with null ID")
            void shouldHandleOpportunityWithNullId() {
                // Given
                PartnershipOpportunity opportunity = new PartnershipOpportunity();
                opportunity.setId(null);
                when(entityToLongContext.getSource()).thenReturn(opportunity);
                Converter<PartnershipOpportunity, Long> converter =
                        partnershipOpportunityConverter.fromPartnershipOpportunityConverter();

                // When
                Long result = converter.convert(entityToLongContext);

                // Then
                assertThat(result).isNull();
            }
        }

        @Nested
        @DisplayName("Converter Instance Creation")
        class ConverterInstanceCreation {

            @Test
            @DisplayName("should create new converter instance for toPartnershipOpportunityConverter")
            void shouldCreateNewConverterInstanceForToConverter() {
                // When
                Converter<Long, PartnershipOpportunity> converter1 =
                        partnershipOpportunityConverter.toPartnershipOpportunityConverter();
                Converter<Long, PartnershipOpportunity> converter2 =
                        partnershipOpportunityConverter.toPartnershipOpportunityConverter();

                // Then
                assertThat(converter1).isNotNull();
                assertThat(converter2).isNotNull();
            }

            @Test
            @DisplayName("should create new converter instance for fromPartnershipOpportunityConverter")
            void shouldCreateNewConverterInstanceForFromConverter() {
                // When
                Converter<PartnershipOpportunity, Long> converter1 =
                        partnershipOpportunityConverter.fromPartnershipOpportunityConverter();
                Converter<PartnershipOpportunity, Long> converter2 =
                        partnershipOpportunityConverter.fromPartnershipOpportunityConverter();

                // Then
                assertThat(converter1).isNotNull();
                assertThat(converter2).isNotNull();
            }
        }
    }

    // ==================== PartnershipOpportunityMapping Tests ====================

    @Nested
    @DisplayName("PartnershipOpportunityMapping")
    class PartnershipOpportunityMappingTests {

        @Nested
        @DisplayName("configureMapping Interface")
        class ConfigureMappingInterface {

            @Test
            @DisplayName("should implement MappingConfigurer interface")
            void shouldImplementMappingConfigurerInterface() {
                assertThat(partnershipOpportunityMapping).isInstanceOf(MappingConfigurer.class);
            }

            @Test
            @DisplayName("should return the same ModelMapper instance")
            void shouldReturnSameModelMapperInstance() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                ModelMapper result = partnershipOpportunityMapping.configureMapping(modelMapper);
                assertThat(result).isSameAs(modelMapper);
            }

            @Test
            @DisplayName("should configure DtoIn to Entity type map")
            void shouldConfigureDtoInToEntityTypeMap() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                assertThat(modelMapper.getTypeMap(PartnershipOpportunityDtoIn.class, PartnershipOpportunity.class))
                        .isNotNull();
            }
        }

        @Nested
        @DisplayName("Field Skipping in DtoIn to Entity Mapping")
        class FieldSkippingTests {

            @Test
            @DisplayName("should skip id field when mapping DtoIn to Entity")
            void shouldSkipIdFieldWhenMappingDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setId(999L);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getId()).isNull();
            }

            @Test
            @DisplayName("should skip createdTime field when mapping DtoIn to Entity")
            void shouldSkipCreatedTimeFieldWhenMappingDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setCreatedTime(LocalDateTime.now().minusDays(5));
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getCreatedTime()).isNull();
            }

            @Test
            @DisplayName("should skip updaterId field when mapping DtoIn to Entity")
            void shouldSkipUpdaterIdFieldWhenMappingDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getUpdaterId()).isNull();
            }

            @Test
            @DisplayName("should skip photos field when mapping DtoIn to Entity")
            void shouldSkipPhotosFieldWhenMappingDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                PartnershipOpportunityPhotoDtoIn photoDtoIn = new PartnershipOpportunityPhotoDtoIn();
                testDtoIn.setPhotos(List.of(photoDtoIn));
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getPhotos()).isEmpty();
            }

            @Test
            @DisplayName("should skip appliedOpportunities field when mapping DtoIn to Entity")
            void shouldSkipAppliedOpportunitiesFieldWhenMappingDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getAppliedOpportunities()).isEmpty();
            }
        }

        @Nested
        @DisplayName("Simple Field Mappings")
        class SimpleFieldMappings {

            @Test
            @DisplayName("should map name field from DtoIn to Entity")
            void shouldMapNameFieldFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setName("Unique Opportunity Name");
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getName()).isEqualTo("Unique Opportunity Name");
            }

            @Test
            @DisplayName("should map title field from DtoIn to Entity")
            void shouldMapTitleFieldFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setTitle("Special Title");
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getTitle()).isEqualTo("Special Title");
            }

            @Test
            @DisplayName("should map details field from DtoIn to Entity")
            void shouldMapDetailsFieldFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setDetails("Detailed description of the opportunity");
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getDetails()).isEqualTo("Detailed description of the opportunity");
            }

            @Test
            @DisplayName("should map requirements field from DtoIn to Entity")
            void shouldMapRequirementsFieldFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setRequirements("Must have 10k followers");
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getRequirements()).isEqualTo("Must have 10k followers");
            }

            @Test
            @DisplayName("should map compensationAmountMin field from DtoIn to Entity")
            void shouldMapCompensationAmountMinFieldFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setCompensationAmountMin(500);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getCompensationAmountMin()).isEqualTo(500);
            }

            @Test
            @DisplayName("should map compensationAmountMax field from DtoIn to Entity")
            void shouldMapCompensationAmountMaxFieldFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setCompensationAmountMax(10000);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getCompensationAmountMax()).isEqualTo(10000);
            }

            @Test
            @DisplayName("should map compensationDescription field from DtoIn to Entity")
            void shouldMapCompensationDescriptionFieldFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setCompensationDescription("Monthly payment via bank transfer");
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getCompensationDescription()).isEqualTo("Monthly payment via bank transfer");
            }

            @Test
            @DisplayName("should map followersMin field from DtoIn to Entity")
            void shouldMapFollowersMinFieldFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setFollowersMin(5000);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getFollowersMin()).isEqualTo(5000);
            }

            @Test
            @DisplayName("should map followersMax field from DtoIn to Entity")
            void shouldMapFollowersMaxFieldFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setFollowersMax(500000);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getFollowersMax()).isEqualTo(500000);
            }

            @Test
            @DisplayName("should map startDate field from DtoIn to Entity")
            void shouldMapStartDateFieldFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                LocalDateTime startDate = LocalDateTime.now().plusDays(7);
                testDtoIn.setStartDate(startDate);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getStartDate()).isEqualTo(startDate);
            }

            @Test
            @DisplayName("should map endDate field from DtoIn to Entity")
            void shouldMapEndDateFieldFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                LocalDateTime endDate = LocalDateTime.now().plusDays(60);
                testDtoIn.setEndDate(endDate);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getEndDate()).isEqualTo(endDate);
            }

            @Test
            @DisplayName("should map active field from DtoIn to Entity")
            void shouldMapActiveFieldFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setActive(false);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.isActive()).isFalse();
            }

            @Test
            @DisplayName("should map compensationType enum from DtoIn to Entity")
            void shouldMapCompensationTypeEnumFromDtoInToEntity() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setCompensationType(CompensationType.BARTER);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getCompensationType()).isEqualTo(CompensationType.BARTER);
            }
        }

        @Nested
        @DisplayName("Custom Converter Mappings")
        class CustomConverterMappings {

            @Test
            @DisplayName("should use addressRepository for addressId mapping")
            void shouldUseAddressRepositoryForAddressIdMapping() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                when(addressRepository.findById(1L)).thenReturn(Optional.of(testAddress));
                when(cityRepository.findByName("Warsaw")).thenReturn(Optional.of(testCity));
                partnershipOpportunityMapping.configureMapping(modelMapper);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getAddress()).isNotNull();
                assertThat(result.getAddress().getId()).isEqualTo(1L);
                verify(addressRepository).findById(1L);
            }

            @Test
            @DisplayName("should return null for address when addressId is null")
            void shouldReturnNullForAddressWhenAddressIdIsNull() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                when(cityRepository.findByName("Warsaw")).thenReturn(Optional.of(testCity));
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setAddressId(null);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getAddress()).isNull();
            }

            @Test
            @DisplayName("should throw MappingException with ResourceNotFoundException cause when address not found")
            void shouldThrowResourceNotFoundExceptionWhenAddressNotFound() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                when(addressRepository.findById(999L)).thenReturn(Optional.empty());
                when(cityRepository.findByName("Warsaw")).thenReturn(Optional.of(testCity));
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setAddressId(999L);
                assertThatThrownBy(() -> modelMapper.map(testDtoIn, PartnershipOpportunity.class))
                        .isInstanceOf(MappingException.class)
                        .hasRootCauseInstanceOf(ResourceNotFoundException.class);
            }

            @Test
            @DisplayName("should use cityRepository for city mapping")
            void shouldUseCityRepositoryForCityMapping() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setCity("Warsaw");
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getCity()).isNotNull();
                assertThat(result.getCity().getName()).isEqualTo("Warsaw");
                verify(cityRepository).findByName("Warsaw");
            }

            @Test
            @DisplayName("should throw MappingException with IllegalArgumentException cause when city is null")
            void shouldThrowIllegalArgumentExceptionWhenCityIsNull() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                when(addressRepository.findById(1L)).thenReturn(Optional.of(testAddress));
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setCity(null);
                assertThatThrownBy(() -> modelMapper.map(testDtoIn, PartnershipOpportunity.class))
                        .isInstanceOf(MappingException.class)
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasRootCauseMessage("City must not be null or empty.");
            }

            @Test
            @DisplayName("should throw MappingException with IllegalArgumentException cause when city is empty")
            void shouldThrowIllegalArgumentExceptionWhenCityIsEmpty() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                when(addressRepository.findById(1L)).thenReturn(Optional.of(testAddress));
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setCity("");
                assertThatThrownBy(() -> modelMapper.map(testDtoIn, PartnershipOpportunity.class))
                        .isInstanceOf(MappingException.class)
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasRootCauseMessage("City must not be null or empty.");
            }

            @Test
            @DisplayName("should throw MappingException with IllegalArgumentException cause when city is blank")
            void shouldThrowIllegalArgumentExceptionWhenCityIsBlank() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                when(addressRepository.findById(1L)).thenReturn(Optional.of(testAddress));
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setCity("   ");
                assertThatThrownBy(() -> modelMapper.map(testDtoIn, PartnershipOpportunity.class))
                        .isInstanceOf(MappingException.class)
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasRootCauseMessage("City must not be null or empty.");
            }

            @Test
            @DisplayName("should throw MappingException with ResourceNotFoundException cause when city not found")
            void shouldThrowResourceNotFoundExceptionWhenCityNotFound() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                when(addressRepository.findById(1L)).thenReturn(Optional.of(testAddress));
                when(cityRepository.findByName("UnknownCity")).thenReturn(Optional.empty());
                partnershipOpportunityMapping.configureMapping(modelMapper);
                testDtoIn.setCity("UnknownCity");
                assertThatThrownBy(() -> modelMapper.map(testDtoIn, PartnershipOpportunity.class))
                        .isInstanceOf(MappingException.class)
                        .hasRootCauseInstanceOf(ResourceNotFoundException.class);
            }

            @Test
            @DisplayName("should use userConverter for company mapping")
            void shouldUseUserConverterForCompanyMapping() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getCompany()).isNotNull();
                verify(userConverter).toUserConverter();
            }

            @Test
            @DisplayName("should use currencyConverter for currency mapping")
            void shouldUseCurrencyConverterForCurrencyMapping() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getCurrency()).isNotNull();
                verify(currencyConverter).toCurrencyConverter();
            }

            @Test
            @DisplayName("should use serviceTypeConverter for serviceType mapping")
            void shouldUseServiceTypeConverterForServiceTypeMapping() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getServiceType()).isNotNull();
                verify(serviceTypeConverter).toServiceTypeConverter();
            }

            @Test
            @DisplayName("should use platformConverter for platforms mapping")
            void shouldUsePlatformConverterForPlatformsMapping() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getPlatforms()).isNotNull();
                verify(platformConverter).toPlatformConverter();
            }

            @Test
            @DisplayName("should use contentTypeConverter for contentTypes mapping")
            void shouldUseContentTypeConverterForContentTypesMapping() {
                ModelMapper modelMapper = new ModelMapper();
                setupMockConverters();
                setupRepositoryMocks();
                partnershipOpportunityMapping.configureMapping(modelMapper);
                PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
                assertThat(result.getContentTypes()).isNotNull();
                verify(contentTypeConverter).toContentTypeConverter();
            }
        }
    }

    // ==================== CompensationType Enum Tests ====================

    @Nested
    @DisplayName("CompensationType Enum")
    class CompensationTypeTests {

        @Nested
        @DisplayName("Enum Values")
        class EnumValues {

            @Test
            @DisplayName("should have CASH value")
            void shouldHaveCashValue() {
                assertThat(CompensationType.valueOf("CASH")).isEqualTo(CompensationType.CASH);
            }

            @Test
            @DisplayName("should have BARTER value")
            void shouldHaveBarterValue() {
                assertThat(CompensationType.valueOf("BARTER")).isEqualTo(CompensationType.BARTER);
            }

            @Test
            @DisplayName("should have exactly two values")
            void shouldHaveExactlyTwoValues() {
                assertThat(CompensationType.values()).hasSize(2);
            }

            @ParameterizedTest
            @EnumSource(CompensationType.class)
            @DisplayName("should have non-null properties for all values")
            void shouldHaveNonNullPropertiesForAllValues(CompensationType type) {
                assertThat(type.getColorTheme()).isNotNull();
                assertThat(type.getIcon()).isNotNull();
                assertThat(type.getAliases()).isNotNull();
                assertThat(type.getDefaultDescription()).isNotNull();
            }
        }

        @Nested
        @DisplayName("CASH Type Properties")
        class CashTypeProperties {

            @Test
            @DisplayName("should have correct colorTheme for CASH")
            void shouldHaveCorrectColorThemeForCash() {
                assertThat(CompensationType.CASH.getColorTheme()).isEqualTo("primary");
            }

            @Test
            @DisplayName("should have correct icon for CASH")
            void shouldHaveCorrectIconForCash() {
                assertThat(CompensationType.CASH.getIcon()).isEqualTo("dollar-sign");
            }

            @Test
            @DisplayName("should have correct aliases for CASH")
            void shouldHaveCorrectAliasesForCash() {
                assertThat(CompensationType.CASH.getAliases()).containsExactly("money");
            }

            @Test
            @DisplayName("should have correct defaultDescription for CASH")
            void shouldHaveCorrectDefaultDescriptionForCash() {
                assertThat(CompensationType.CASH.getDefaultDescription()).isNotBlank();
            }
        }

        @Nested
        @DisplayName("BARTER Type Properties")
        class BarterTypeProperties {

            @Test
            @DisplayName("should have correct colorTheme for BARTER")
            void shouldHaveCorrectColorThemeForBarter() {
                assertThat(CompensationType.BARTER.getColorTheme()).isEqualTo("warning");
            }

            @Test
            @DisplayName("should have correct icon for BARTER")
            void shouldHaveCorrectIconForBarter() {
                assertThat(CompensationType.BARTER.getIcon()).isEqualTo("swap");
            }

            @Test
            @DisplayName("should have correct aliases for BARTER")
            void shouldHaveCorrectAliasesForBarter() {
                assertThat(CompensationType.BARTER.getAliases()).containsExactlyInAnyOrder("trade", "exchange");
            }

            @Test
            @DisplayName("should have correct defaultDescription for BARTER")
            void shouldHaveCorrectDefaultDescriptionForBarter() {
                assertThat(CompensationType.BARTER.getDefaultDescription()).isNotBlank();
            }
        }

        @Nested
        @DisplayName("fromString Method")
        class FromStringMethod {

            @Test
            @DisplayName("should parse CASH from string")
            void shouldParseCashFromString() {
                CompensationType result = CompensationType.fromString("CASH");
                assertThat(result).isEqualTo(CompensationType.CASH);
            }

            @Test
            @DisplayName("should parse BARTER from string")
            void shouldParseBarterFromString() {
                CompensationType result = CompensationType.fromString("BARTER");
                assertThat(result).isEqualTo(CompensationType.BARTER);
            }

            @Test
            @DisplayName("should parse lowercase cash")
            void shouldParseLowercaseCash() {
                CompensationType result = CompensationType.fromString("cash");
                assertThat(result).isEqualTo(CompensationType.CASH);
            }

            @Test
            @DisplayName("should parse lowercase barter")
            void shouldParseLowercaseBarter() {
                CompensationType result = CompensationType.fromString("barter");
                assertThat(result).isEqualTo(CompensationType.BARTER);
            }

            @Test
            @DisplayName("should parse mixed case values")
            void shouldParseMixedCaseValues() {
                assertThat(CompensationType.fromString("Cash")).isEqualTo(CompensationType.CASH);
                assertThat(CompensationType.fromString("BARTER")).isEqualTo(CompensationType.BARTER);
                assertThat(CompensationType.fromString("cAsH")).isEqualTo(CompensationType.CASH);
            }

            @Test
            @DisplayName("should throw exception for invalid string")
            void shouldThrowExceptionForInvalidString() {
                assertThatThrownBy(() -> CompensationType.fromString("INVALID"))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Nested
        @DisplayName("getValue Method")
        class GetValueMethod {

            @Test
            @DisplayName("should return CASH for getValue on CASH")
            void shouldReturnCashForGetValueOnCash() {
                assertThat(CompensationType.CASH.getValue()).isEqualTo("CASH");
            }

            @Test
            @DisplayName("should return BARTER for getValue on BARTER")
            void shouldReturnBarterForGetValueOnBarter() {
                assertThat(CompensationType.BARTER.getValue()).isEqualTo("BARTER");
            }

            @ParameterizedTest
            @EnumSource(CompensationType.class)
            @DisplayName("getValue should return name() for all types")
            void getValueShouldReturnNameForAllTypes(CompensationType type) {
                assertThat(type.getValue()).isEqualTo(type.name());
            }
        }

        @Nested
        @DisplayName("getLabel Method")
        class GetLabelMethod {

            @Test
            @DisplayName("should return translated label when translation exists")
            void shouldReturnTranslatedLabelWhenTranslationExists() {
                when(dictionaryService.getTranslation("COMPENSATION_TYPE_CASH", "en"))
                        .thenReturn(Optional.of("Cash Payment"));
                String result = CompensationType.CASH.getLabel(dictionaryService, Locale.ENGLISH);
                assertThat(result).isEqualTo("Cash Payment");
            }

            @Test
            @DisplayName("should return name when translation not found")
            void shouldReturnNameWhenTranslationNotFound() {
                when(dictionaryService.getTranslation("COMPENSATION_TYPE_CASH", "en"))
                        .thenReturn(Optional.empty());
                String result = CompensationType.CASH.getLabel(dictionaryService, Locale.ENGLISH);
                assertThat(result).isEqualTo("CASH");
            }

            @Test
            @DisplayName("should call dictionaryService with correct key")
            void shouldCallDictionaryServiceWithCorrectKey() {
                when(dictionaryService.getTranslation(any(), any())).thenReturn(Optional.empty());
                CompensationType.BARTER.getLabel(dictionaryService, Locale.ENGLISH);
                verify(dictionaryService).getTranslation("COMPENSATION_TYPE_BARTER", "en");
            }
        }

        @Nested
        @DisplayName("getDescription Method")
        class GetDescriptionMethod {

            @Test
            @DisplayName("should return translated description when translation exists")
            void shouldReturnTranslatedDescriptionWhenTranslationExists() {
                when(dictionaryService.getTranslation("COMPENSATION_TYPE_CASH_DESC", "en"))
                        .thenReturn(Optional.of("Payment in cash"));
                String result = CompensationType.CASH.getDescription(dictionaryService, Locale.ENGLISH);
                assertThat(result).isEqualTo("Payment in cash");
            }

            @Test
            @DisplayName("should return default description when translation not found")
            void shouldReturnDefaultDescriptionWhenTranslationNotFound() {
                when(dictionaryService.getTranslation("COMPENSATION_TYPE_CASH_DESC", "en"))
                        .thenReturn(Optional.empty());
                String result = CompensationType.CASH.getDescription(dictionaryService, Locale.ENGLISH);
                assertThat(result).isEqualTo(CompensationType.CASH.getDefaultDescription());
            }

            @Test
            @DisplayName("should call dictionaryService with correct key for description")
            void shouldCallDictionaryServiceWithCorrectKeyForDescription() {
                when(dictionaryService.getTranslation(any(), any())).thenReturn(Optional.empty());
                CompensationType.BARTER.getDescription(dictionaryService, Locale.ENGLISH);
                verify(dictionaryService).getTranslation("COMPENSATION_TYPE_BARTER_DESC", "en");
            }
        }
    }

    // ==================== Edge Cases and Null Handling ====================

    @Nested
    @DisplayName("Edge Cases and Null Handling")
    class EdgeCasesAndNullHandling {

        @Test
        @DisplayName("should handle null values in simple fields")
        void shouldHandleNullValuesInSimpleFields() {
            ModelMapper modelMapper = new ModelMapper();
            setupMockConverters();
            setupRepositoryMocks();
            partnershipOpportunityMapping.configureMapping(modelMapper);
            testDtoIn.setDetails(null);
            testDtoIn.setRequirements(null);
            testDtoIn.setCompensationDescription(null);
            PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
            assertThat(result.getDetails()).isNull();
            assertThat(result.getRequirements()).isNull();
            assertThat(result.getCompensationDescription()).isNull();
        }

        @Test
        @DisplayName("should handle zero values in numeric fields")
        void shouldHandleZeroValuesInNumericFields() {
            ModelMapper modelMapper = new ModelMapper();
            setupMockConverters();
            setupRepositoryMocks();
            partnershipOpportunityMapping.configureMapping(modelMapper);
            testDtoIn.setCompensationAmountMin(0);
            testDtoIn.setCompensationAmountMax(0);
            testDtoIn.setFollowersMin(0);
            testDtoIn.setFollowersMax(0);
            PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
            assertThat(result.getCompensationAmountMin()).isZero();
            assertThat(result.getCompensationAmountMax()).isZero();
            assertThat(result.getFollowersMin()).isZero();
            assertThat(result.getFollowersMax()).isZero();
        }

        @Test
        @DisplayName("should handle maximum compensation amount")
        void shouldHandleMaximumCompensationAmount() {
            ModelMapper modelMapper = new ModelMapper();
            setupMockConverters();
            setupRepositoryMocks();
            partnershipOpportunityMapping.configureMapping(modelMapper);
            testDtoIn.setCompensationAmountMin(1_000_000);
            testDtoIn.setCompensationAmountMax(1_000_000);
            PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
            assertThat(result.getCompensationAmountMin()).isEqualTo(1_000_000);
            assertThat(result.getCompensationAmountMax()).isEqualTo(1_000_000);
        }

        @Test
        @DisplayName("should handle empty sets for platforms")
        void shouldHandleEmptySetsForPlatforms() {
            ModelMapper modelMapper = new ModelMapper();
            when(userConverter.toUserConverter()).thenReturn(ctx -> testCompany);
            when(currencyConverter.toCurrencyConverter()).thenReturn(ctx -> testCurrency);
            when(serviceTypeConverter.toServiceTypeConverter()).thenReturn(ctx -> testServiceType);
            when(platformConverter.toPlatformConverter()).thenReturn(ctx -> new HashSet<>());
            when(contentTypeConverter.toContentTypeConverter()).thenReturn(ctx -> Set.of(testContentType));
            when(addressRepository.findById(1L)).thenReturn(Optional.of(testAddress));
            when(cityRepository.findByName("Warsaw")).thenReturn(Optional.of(testCity));
            partnershipOpportunityMapping.configureMapping(modelMapper);
            testDtoIn.setPlatforms(new HashSet<>());
            PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
            assertThat(result.getPlatforms()).isEmpty();
        }

        @Test
        @DisplayName("should handle empty sets for contentTypes")
        void shouldHandleEmptySetsForContentTypes() {
            ModelMapper modelMapper = new ModelMapper();
            when(userConverter.toUserConverter()).thenReturn(ctx -> testCompany);
            when(currencyConverter.toCurrencyConverter()).thenReturn(ctx -> testCurrency);
            when(serviceTypeConverter.toServiceTypeConverter()).thenReturn(ctx -> testServiceType);
            when(platformConverter.toPlatformConverter()).thenReturn(ctx -> Set.of(testPlatform));
            when(contentTypeConverter.toContentTypeConverter()).thenReturn(ctx -> new HashSet<>());
            when(addressRepository.findById(1L)).thenReturn(Optional.of(testAddress));
            when(cityRepository.findByName("Warsaw")).thenReturn(Optional.of(testCity));
            partnershipOpportunityMapping.configureMapping(modelMapper);
            testDtoIn.setContentTypes(new HashSet<>());
            PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
            assertThat(result.getContentTypes()).isEmpty();
        }

        @Test
        @DisplayName("should handle null platforms set")
        void shouldHandleNullPlatformsSet() {
            ModelMapper modelMapper = new ModelMapper();
            when(userConverter.toUserConverter()).thenReturn(ctx -> testCompany);
            when(currencyConverter.toCurrencyConverter()).thenReturn(ctx -> testCurrency);
            when(serviceTypeConverter.toServiceTypeConverter()).thenReturn(ctx -> testServiceType);
            when(platformConverter.toPlatformConverter()).thenReturn(ctx -> {
                if (ctx.getSource() == null) return new HashSet<>();
                return Set.of(testPlatform);
            });
            when(contentTypeConverter.toContentTypeConverter()).thenReturn(ctx -> Set.of(testContentType));
            when(addressRepository.findById(1L)).thenReturn(Optional.of(testAddress));
            when(cityRepository.findByName("Warsaw")).thenReturn(Optional.of(testCity));
            partnershipOpportunityMapping.configureMapping(modelMapper);
            testDtoIn.setPlatforms(null);
            PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
            assertThat(result.getPlatforms()).isEmpty();
        }
    }

    // ==================== Integration-like Tests ====================

    @Nested
    @DisplayName("Integration-like Tests")
    class IntegrationLikeTests {

        @Test
        @DisplayName("should map all fields correctly in full DtoIn to Entity mapping")
        void shouldMapAllFieldsCorrectlyInFullDtoInToEntityMapping() {
            ModelMapper modelMapper = new ModelMapper();
            setupMockConverters();
            setupRepositoryMocks();
            partnershipOpportunityMapping.configureMapping(modelMapper);
            LocalDateTime startDate = LocalDateTime.now().plusDays(5);
            LocalDateTime endDate = LocalDateTime.now().plusDays(35);
            testDtoIn.setName("Full Test Opportunity");
            testDtoIn.setTitle("Full Test Title");
            testDtoIn.setDetails("Detailed full test");
            testDtoIn.setRequirements("Full requirements");
            testDtoIn.setCompensationType(CompensationType.CASH);
            testDtoIn.setCompensationAmountMin(2000);
            testDtoIn.setCompensationAmountMax(8000);
            testDtoIn.setCompensationDescription("Full compensation desc");
            testDtoIn.setFollowersMin(5000);
            testDtoIn.setFollowersMax(200000);
            testDtoIn.setStartDate(startDate);
            testDtoIn.setEndDate(endDate);
            testDtoIn.setActive(true);
            PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
            assertThat(result.getName()).isEqualTo("Full Test Opportunity");
            assertThat(result.getTitle()).isEqualTo("Full Test Title");
            assertThat(result.getDetails()).isEqualTo("Detailed full test");
            assertThat(result.getRequirements()).isEqualTo("Full requirements");
            assertThat(result.getCompensationType()).isEqualTo(CompensationType.CASH);
            assertThat(result.getCompensationAmountMin()).isEqualTo(2000);
            assertThat(result.getCompensationAmountMax()).isEqualTo(8000);
            assertThat(result.getCompensationDescription()).isEqualTo("Full compensation desc");
            assertThat(result.getFollowersMin()).isEqualTo(5000);
            assertThat(result.getFollowersMax()).isEqualTo(200000);
            assertThat(result.getStartDate()).isEqualTo(startDate);
            assertThat(result.getEndDate()).isEqualTo(endDate);
            assertThat(result.isActive()).isTrue();
            assertThat(result.getCompany()).isNotNull();
            assertThat(result.getCity()).isNotNull();
            assertThat(result.getAddress()).isNotNull();
        }

        @Test
        @DisplayName("should correctly wire all converters in mapping configuration")
        void shouldCorrectlyWireAllConvertersInMappingConfiguration() {
            ModelMapper modelMapper = new ModelMapper();
            setupMockConverters();
            setupRepositoryMocks();
            partnershipOpportunityMapping.configureMapping(modelMapper);
            verify(userConverter).toUserConverter();
            verify(currencyConverter).toCurrencyConverter();
            verify(serviceTypeConverter).toServiceTypeConverter();
            verify(platformConverter).toPlatformConverter();
            verify(contentTypeConverter).toContentTypeConverter();
        }

        @Test
        @DisplayName("should handle multiple mappings with same ModelMapper")
        void shouldHandleMultipleMappingsWithSameModelMapper() {
            ModelMapper modelMapper = new ModelMapper();
            setupMockConverters();
            setupRepositoryMocks();
            partnershipOpportunityMapping.configureMapping(modelMapper);
            PartnershipOpportunity result1 = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
            testDtoIn.setName("Second Opportunity");
            PartnershipOpportunity result2 = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
            assertThat(result1.getName()).isEqualTo("Test Opportunity");
            assertThat(result2.getName()).isEqualTo("Second Opportunity");
        }
    }

    // ==================== Compensation Type in Mapping Context ====================

    @Nested
    @DisplayName("CompensationType in Mapping Context")
    class CompensationTypeInMappingContext {

        @ParameterizedTest
        @EnumSource(CompensationType.class)
        @DisplayName("should correctly map all CompensationType values")
        void shouldCorrectlyMapAllCompensationTypeValues(CompensationType type) {
            ModelMapper modelMapper = new ModelMapper();
            setupMockConverters();
            setupRepositoryMocks();
            partnershipOpportunityMapping.configureMapping(modelMapper);
            testDtoIn.setCompensationType(type);
            PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
            assertThat(result.getCompensationType()).isEqualTo(type);
        }

        @Test
        @DisplayName("should handle null CompensationType in mapping")
        void shouldHandleNullCompensationTypeInMapping() {
            ModelMapper modelMapper = new ModelMapper();
            setupMockConverters();
            setupRepositoryMocks();
            partnershipOpportunityMapping.configureMapping(modelMapper);
            testDtoIn.setCompensationType(null);
            PartnershipOpportunity result = modelMapper.map(testDtoIn, PartnershipOpportunity.class);
            assertThat(result.getCompensationType()).isNull();
        }
    }

    // ==================== Helper Methods ====================

    private void setupMockConverters() {
        when(userConverter.toUserConverter()).thenReturn(ctx -> testCompany);
        when(currencyConverter.toCurrencyConverter()).thenReturn(ctx -> testCurrency);
        when(serviceTypeConverter.toServiceTypeConverter()).thenReturn(ctx -> testServiceType);
        when(platformConverter.toPlatformConverter()).thenReturn(ctx -> Set.of(testPlatform));
        when(contentTypeConverter.toContentTypeConverter()).thenReturn(ctx -> Set.of(testContentType));
    }

    private void setupRepositoryMocks() {
        when(addressRepository.findById(1L)).thenReturn(Optional.of(testAddress));
        when(cityRepository.findByName("Warsaw")).thenReturn(Optional.of(testCity));
    }
}
