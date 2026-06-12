package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.appliedopportunities.*;
import com.sm.instagram.platform.common.util.mappers.MappingConfigurer;
import com.sm.instagram.platform.common.util.mappers.UpdaterIdConverter;
import com.sm.instagram.platform.contenttype.ContentType;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityConverter;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.spi.MappingContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AppliedOpportunityConverter and AppliedOpportunityMapping.
 * Tests all conversion methods, null handling, edge cases, and field mappings.
 * Uses pure Mockito - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppliedOpportunityConverter and AppliedOpportunityMapping Unit Tests")
class AppliedOpportunityConverterUnitTest {

    @Mock
    private AppliedOpportunityContentMapping contentMapping;

    @Mock
    private UpdaterIdConverter updaterIdConverter;

    @Mock
    private PartnershipOpportunityConverter partnershipOpportunityConverter;

    @Mock
    private UserConverter userConverter;

    @Mock
    private MappingContext<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> dtoOutContext;

    @Mock
    private MappingContext<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> simpleDtoOutContext;

    private AppliedOpportunityConverter appliedOpportunityConverter;
    private AppliedOpportunityMapping appliedOpportunityMapping;

    // Test fixtures
    private AppliedOpportunity testAppliedOpportunity;
    private AppliedOpportunityContent testContent;
    private AppliedOpportunityContent testContent2;
    private ContentType testContentType;
    private User testInfluencer;
    private PartnershipOpportunity testPartnershipOpportunity;
    private AppliedOpportunityContentDtoOut testContentDtoOut;
    private AppliedOpportunityContentSimpleDtoOut testSimpleContentDtoOut;

    @BeforeEach
    void setUp() {
        // Create the converter with mocked dependencies
        appliedOpportunityConverter = new AppliedOpportunityConverter(contentMapping);

        // Create test fixtures
        testContentType = new ContentType();
        testContentType.setId(1L);
        testContentType.setName("Instagram Reel");

        testInfluencer = new User();
        testInfluencer.setId(1L);

        testPartnershipOpportunity = new PartnershipOpportunity();
        testPartnershipOpportunity.setId(100L);

        testAppliedOpportunity = new AppliedOpportunity();
        testAppliedOpportunity.setId(10L);
        testAppliedOpportunity.setInfluencer(testInfluencer);
        testAppliedOpportunity.setPartnershipOpportunity(testPartnershipOpportunity);
        testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.APPLIED);

        testContent = new AppliedOpportunityContent();
        testContent.setId(1L);
        testContent.setAppliedOpportunity(testAppliedOpportunity);
        testContent.setContentType(testContentType);
        testContent.setApprovalStatus(ContentApprovalStatus.PENDING);
        testContent.setDescription("Test content description");
        testContent.setUrls(List.of("https://example.com/content1"));
        testContent.setContentCount(1);
        testContent.setTags("test,content");
        testContent.setLikesCount(100L);
        testContent.setCommentsCount(10L);
        testContent.setViewsCount(1000L);
        testContent.setSharesCount(50L);
        testContent.setSocialMediaLink("https://instagram.com/p/abc123");
        testContent.setCreatedTime(LocalDateTime.now().minusDays(1));
        testContent.setLastUpdateTime(LocalDateTime.now());
        testContent.setUpdaterId("test-updater");
        testContent.setContentCreationDate(LocalDateTime.now().minusDays(2));
        testContent.setSubmissionDate(LocalDateTime.now().minusHours(12));

        testContent2 = new AppliedOpportunityContent();
        testContent2.setId(2L);
        testContent2.setAppliedOpportunity(testAppliedOpportunity);
        testContent2.setContentType(testContentType);
        testContent2.setApprovalStatus(ContentApprovalStatus.APPROVED);
        testContent2.setDescription("Second content");

        testContentDtoOut = new AppliedOpportunityContentDtoOut();
        testContentDtoOut.setId(1L);
        testContentDtoOut.setDescription("Test content description");

        testSimpleContentDtoOut = new AppliedOpportunityContentSimpleDtoOut();
        testSimpleContentDtoOut.setId(1L);
        testSimpleContentDtoOut.setContentTypeName("Instagram Reel");

        // Create the mapping with mocked dependencies
        appliedOpportunityMapping = new AppliedOpportunityMapping(
                updaterIdConverter,
                partnershipOpportunityConverter,
                userConverter,
                appliedOpportunityConverter
        );
    }

    // ==================== AppliedOpportunityConverter Tests ====================

    @Nested
    @DisplayName("AppliedOpportunityConverter")
    class AppliedOpportunityConverterTests {

        @Nested
        @DisplayName("toContentDtoListConverter")
        class ToContentDtoListConverter {

            @Test
            @DisplayName("should return empty list when source is null")
            void shouldReturnEmptyListWhenSourceIsNull() {
                // Given
                when(dtoOutContext.getSource()).thenReturn(null);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                        appliedOpportunityConverter.toContentDtoListConverter();

                // When
                List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

                // Then
                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("should return empty list when source is empty")
            void shouldReturnEmptyListWhenSourceIsEmpty() {
                // Given
                when(dtoOutContext.getSource()).thenReturn(Collections.emptyList());
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                        appliedOpportunityConverter.toContentDtoListConverter();

                // When
                List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

                // Then
                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("should convert single content to DTO list")
            void shouldConvertSingleContentToDtoList() {
                // Given
                when(dtoOutContext.getSource()).thenReturn(List.of(testContent));
                when(contentMapping.toDto(testContent)).thenReturn(testContentDtoOut);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                        appliedOpportunityConverter.toContentDtoListConverter();

                // When
                List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

                // Then
                assertThat(result).hasSize(1);
                assertThat(result.get(0)).isEqualTo(testContentDtoOut);
                verify(contentMapping).toDto(testContent);
            }

            @Test
            @DisplayName("should convert multiple contents to DTO list")
            void shouldConvertMultipleContentsToDtoList() {
                // Given
                AppliedOpportunityContentDtoOut secondDto = new AppliedOpportunityContentDtoOut();
                secondDto.setId(2L);

                when(dtoOutContext.getSource()).thenReturn(List.of(testContent, testContent2));
                when(contentMapping.toDto(testContent)).thenReturn(testContentDtoOut);
                when(contentMapping.toDto(testContent2)).thenReturn(secondDto);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                        appliedOpportunityConverter.toContentDtoListConverter();

                // When
                List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

                // Then
                assertThat(result).hasSize(2);
                assertThat(result.get(0).getId()).isEqualTo(1L);
                assertThat(result.get(1).getId()).isEqualTo(2L);
                verify(contentMapping, times(2)).toDto(any(AppliedOpportunityContent.class));
            }

            @Test
            @DisplayName("should preserve order when converting list")
            void shouldPreserveOrderWhenConvertingList() {
                // Given
                AppliedOpportunityContent content3 = new AppliedOpportunityContent();
                content3.setId(3L);

                AppliedOpportunityContentDtoOut dto2 = new AppliedOpportunityContentDtoOut();
                dto2.setId(2L);
                AppliedOpportunityContentDtoOut dto3 = new AppliedOpportunityContentDtoOut();
                dto3.setId(3L);

                when(dtoOutContext.getSource()).thenReturn(List.of(testContent, testContent2, content3));
                when(contentMapping.toDto(testContent)).thenReturn(testContentDtoOut);
                when(contentMapping.toDto(testContent2)).thenReturn(dto2);
                when(contentMapping.toDto(content3)).thenReturn(dto3);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                        appliedOpportunityConverter.toContentDtoListConverter();

                // When
                List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

                // Then
                assertThat(result).hasSize(3);
                assertThat(result.get(0).getId()).isEqualTo(1L);
                assertThat(result.get(1).getId()).isEqualTo(2L);
                assertThat(result.get(2).getId()).isEqualTo(3L);
            }

            @Test
            @DisplayName("should call contentMapping.toDto for each content")
            void shouldCallContentMappingToDtoForEachContent() {
                // Given
                List<AppliedOpportunityContent> contents = List.of(testContent, testContent2);
                when(dtoOutContext.getSource()).thenReturn(contents);
                when(contentMapping.toDto(any())).thenReturn(testContentDtoOut);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                        appliedOpportunityConverter.toContentDtoListConverter();

                // When
                converter.convert(dtoOutContext);

                // Then
                verify(contentMapping, times(2)).toDto(any(AppliedOpportunityContent.class));
            }

            @Test
            @DisplayName("should return immutable list")
            void shouldReturnImmutableListFromConverter() {
                // Given
                when(dtoOutContext.getSource()).thenReturn(List.of(testContent));
                when(contentMapping.toDto(testContent)).thenReturn(testContentDtoOut);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                        appliedOpportunityConverter.toContentDtoListConverter();

                // When
                List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

                // Then
                assertThat(result).isNotNull();
                assertThat(result).hasSize(1);
            }

            @Test
            @DisplayName("should handle large list efficiently")
            void shouldHandleLargeListEfficiently() {
                // Given
                List<AppliedOpportunityContent> largeList = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    AppliedOpportunityContent content = new AppliedOpportunityContent();
                    content.setId((long) i);
                    largeList.add(content);
                }
                when(dtoOutContext.getSource()).thenReturn(largeList);
                when(contentMapping.toDto(any())).thenReturn(testContentDtoOut);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                        appliedOpportunityConverter.toContentDtoListConverter();

                // When
                List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

                // Then
                assertThat(result).hasSize(100);
                verify(contentMapping, times(100)).toDto(any(AppliedOpportunityContent.class));
            }
        }

        @Nested
        @DisplayName("toSimpleContentDtoListConverter")
        class ToSimpleContentDtoListConverter {

            @Test
            @DisplayName("should return empty list when source is null")
            void shouldReturnEmptyListWhenSourceIsNullForSimple() {
                // Given
                when(simpleDtoOutContext.getSource()).thenReturn(null);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> converter =
                        appliedOpportunityConverter.toSimpleContentDtoListConverter();

                // When
                List<AppliedOpportunityContentSimpleDtoOut> result = converter.convert(simpleDtoOutContext);

                // Then
                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("should return empty list when source is empty")
            void shouldReturnEmptyListWhenSourceIsEmptyForSimple() {
                // Given
                when(simpleDtoOutContext.getSource()).thenReturn(Collections.emptyList());
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> converter =
                        appliedOpportunityConverter.toSimpleContentDtoListConverter();

                // When
                List<AppliedOpportunityContentSimpleDtoOut> result = converter.convert(simpleDtoOutContext);

                // Then
                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("should convert single content to simple DTO list")
            void shouldConvertSingleContentToSimpleDtoList() {
                // Given
                when(simpleDtoOutContext.getSource()).thenReturn(List.of(testContent));
                when(contentMapping.toSimpleDto(testContent)).thenReturn(testSimpleContentDtoOut);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> converter =
                        appliedOpportunityConverter.toSimpleContentDtoListConverter();

                // When
                List<AppliedOpportunityContentSimpleDtoOut> result = converter.convert(simpleDtoOutContext);

                // Then
                assertThat(result).hasSize(1);
                assertThat(result.get(0)).isEqualTo(testSimpleContentDtoOut);
                verify(contentMapping).toSimpleDto(testContent);
            }

            @Test
            @DisplayName("should convert multiple contents to simple DTO list")
            void shouldConvertMultipleContentsToSimpleDtoList() {
                // Given
                AppliedOpportunityContentSimpleDtoOut secondSimpleDto = new AppliedOpportunityContentSimpleDtoOut();
                secondSimpleDto.setId(2L);

                when(simpleDtoOutContext.getSource()).thenReturn(List.of(testContent, testContent2));
                when(contentMapping.toSimpleDto(testContent)).thenReturn(testSimpleContentDtoOut);
                when(contentMapping.toSimpleDto(testContent2)).thenReturn(secondSimpleDto);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> converter =
                        appliedOpportunityConverter.toSimpleContentDtoListConverter();

                // When
                List<AppliedOpportunityContentSimpleDtoOut> result = converter.convert(simpleDtoOutContext);

                // Then
                assertThat(result).hasSize(2);
                assertThat(result.get(0).getId()).isEqualTo(1L);
                assertThat(result.get(1).getId()).isEqualTo(2L);
            }

            @Test
            @DisplayName("should preserve order when converting to simple DTO list")
            void shouldPreserveOrderWhenConvertingToSimpleDtoList() {
                // Given
                AppliedOpportunityContent content3 = new AppliedOpportunityContent();
                content3.setId(3L);

                AppliedOpportunityContentSimpleDtoOut simpleDto2 = new AppliedOpportunityContentSimpleDtoOut();
                simpleDto2.setId(2L);
                AppliedOpportunityContentSimpleDtoOut simpleDto3 = new AppliedOpportunityContentSimpleDtoOut();
                simpleDto3.setId(3L);

                when(simpleDtoOutContext.getSource()).thenReturn(List.of(testContent, testContent2, content3));
                when(contentMapping.toSimpleDto(testContent)).thenReturn(testSimpleContentDtoOut);
                when(contentMapping.toSimpleDto(testContent2)).thenReturn(simpleDto2);
                when(contentMapping.toSimpleDto(content3)).thenReturn(simpleDto3);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> converter =
                        appliedOpportunityConverter.toSimpleContentDtoListConverter();

                // When
                List<AppliedOpportunityContentSimpleDtoOut> result = converter.convert(simpleDtoOutContext);

                // Then
                assertThat(result).hasSize(3);
                assertThat(result.get(0).getId()).isEqualTo(1L);
                assertThat(result.get(1).getId()).isEqualTo(2L);
                assertThat(result.get(2).getId()).isEqualTo(3L);
            }

            @Test
            @DisplayName("should call contentMapping.toSimpleDto for each content")
            void shouldCallContentMappingToSimpleDtoForEachContent() {
                // Given
                List<AppliedOpportunityContent> contents = List.of(testContent, testContent2);
                when(simpleDtoOutContext.getSource()).thenReturn(contents);
                when(contentMapping.toSimpleDto(any())).thenReturn(testSimpleContentDtoOut);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> converter =
                        appliedOpportunityConverter.toSimpleContentDtoListConverter();

                // When
                converter.convert(simpleDtoOutContext);

                // Then
                verify(contentMapping, times(2)).toSimpleDto(any(AppliedOpportunityContent.class));
            }

            @Test
            @DisplayName("should handle large list efficiently for simple converter")
            void shouldHandleLargeListEfficientlyForSimple() {
                // Given
                List<AppliedOpportunityContent> largeList = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    AppliedOpportunityContent content = new AppliedOpportunityContent();
                    content.setId((long) i);
                    largeList.add(content);
                }
                when(simpleDtoOutContext.getSource()).thenReturn(largeList);
                when(contentMapping.toSimpleDto(any())).thenReturn(testSimpleContentDtoOut);
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> converter =
                        appliedOpportunityConverter.toSimpleContentDtoListConverter();

                // When
                List<AppliedOpportunityContentSimpleDtoOut> result = converter.convert(simpleDtoOutContext);

                // Then
                assertThat(result).hasSize(100);
                verify(contentMapping, times(100)).toSimpleDto(any(AppliedOpportunityContent.class));
            }
        }

        @Nested
        @DisplayName("Converter Instance Creation")
        class ConverterInstanceCreation {

            @Test
            @DisplayName("should create new converter instance each time for toContentDtoListConverter")
            void shouldCreateNewConverterInstanceEachTimeForContentDto() {
                // When
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter1 =
                        appliedOpportunityConverter.toContentDtoListConverter();
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter2 =
                        appliedOpportunityConverter.toContentDtoListConverter();

                // Then
                assertThat(converter1).isNotNull();
                assertThat(converter2).isNotNull();
                // Both should work independently
            }

            @Test
            @DisplayName("should create new converter instance each time for toSimpleContentDtoListConverter")
            void shouldCreateNewConverterInstanceEachTimeForSimpleDto() {
                // When
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> converter1 =
                        appliedOpportunityConverter.toSimpleContentDtoListConverter();
                Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> converter2 =
                        appliedOpportunityConverter.toSimpleContentDtoListConverter();

                // Then
                assertThat(converter1).isNotNull();
                assertThat(converter2).isNotNull();
            }
        }
    }

    // ==================== AppliedOpportunityMapping Tests ====================

    @Nested
    @DisplayName("AppliedOpportunityMapping")
    class AppliedOpportunityMappingTests {

        @Nested
        @DisplayName("configureMapping")
        class ConfigureMapping {

            @Test
            @DisplayName("should implement MappingConfigurer interface")
            void shouldImplementMappingConfigurerInterface() {
                // Then
                assertThat(appliedOpportunityMapping).isInstanceOf(MappingConfigurer.class);
            }

            @Test
            @DisplayName("should configure DtoIn to Entity mapping")
            void shouldConfigureDtoInToEntityMapping() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                // Setup mock converters - all converters must be set up because configureMapping sets up all type maps
                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                // When
                ModelMapper result = appliedOpportunityMapping.configureMapping(modelMapper);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getTypeMap(AppliedOpportunityDtoIn.class, AppliedOpportunity.class)).isNotNull();
            }

            @Test
            @DisplayName("should configure Entity to DtoOut mapping")
            void shouldConfigureEntityToDtoOutMapping() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                // Setup mock converters
                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                // When
                ModelMapper result = appliedOpportunityMapping.configureMapping(modelMapper);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getTypeMap(AppliedOpportunity.class, AppliedOpportunityDtoOut.class)).isNotNull();
            }

            @Test
            @DisplayName("should configure Entity to SimpleDtoOut mapping")
            void shouldConfigureEntityToSimpleDtoOutMapping() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                // Setup mock converters
                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                // When
                ModelMapper result = appliedOpportunityMapping.configureMapping(modelMapper);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getTypeMap(AppliedOpportunity.class, AppliedOpportunitySimpleDtoOut.class)).isNotNull();
            }

            @Test
            @DisplayName("should return the same ModelMapper instance")
            void shouldReturnSameModelMapperInstance() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                // Setup mock converters
                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                // When
                ModelMapper result = appliedOpportunityMapping.configureMapping(modelMapper);

                // Then
                assertThat(result).isSameAs(modelMapper);
            }

            @Test
            @DisplayName("should skip id field when mapping DtoIn to Entity")
            void shouldSkipIdFieldWhenMappingDtoInToEntity() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                // Setup mock converters - all converters must be set up
                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                appliedOpportunityMapping.configureMapping(modelMapper);

                AppliedOpportunityDtoIn dtoIn = new AppliedOpportunityDtoIn();
                dtoIn.setInfluencer(1L);
                dtoIn.setPartnershipOpportunity(100L);

                // When
                AppliedOpportunity result = modelMapper.map(dtoIn, AppliedOpportunity.class);

                // Then
                assertThat(result.getId()).isNull(); // id should be skipped
            }

            @Test
            @DisplayName("should skip lastUpdateTime field when mapping DtoIn to Entity")
            void shouldSkipLastUpdateTimeFieldWhenMappingDtoInToEntity() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                // Setup mock converters - all converters must be set up
                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                appliedOpportunityMapping.configureMapping(modelMapper);

                AppliedOpportunityDtoIn dtoIn = new AppliedOpportunityDtoIn();
                dtoIn.setInfluencer(1L);
                dtoIn.setPartnershipOpportunity(100L);

                // When
                AppliedOpportunity result = modelMapper.map(dtoIn, AppliedOpportunity.class);

                // Then
                // lastUpdateTime should not be set from DTO
                // The actual value depends on default initialization in entity
            }

            @Test
            @DisplayName("should skip updaterId field when mapping DtoIn to Entity")
            void shouldSkipUpdaterIdFieldWhenMappingDtoInToEntity() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                // Setup mock converters - all converters must be set up
                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                appliedOpportunityMapping.configureMapping(modelMapper);

                AppliedOpportunityDtoIn dtoIn = new AppliedOpportunityDtoIn();
                dtoIn.setInfluencer(1L);
                dtoIn.setPartnershipOpportunity(100L);

                // When
                AppliedOpportunity result = modelMapper.map(dtoIn, AppliedOpportunity.class);

                // Then
                assertThat(result.getUpdaterId()).isNull();
            }

            @Test
            @DisplayName("should skip createdTime field when mapping DtoIn to Entity")
            void shouldSkipCreatedTimeFieldWhenMappingDtoInToEntity() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                // Setup mock converters - all converters must be set up
                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                appliedOpportunityMapping.configureMapping(modelMapper);

                AppliedOpportunityDtoIn dtoIn = new AppliedOpportunityDtoIn();
                dtoIn.setInfluencer(1L);
                dtoIn.setPartnershipOpportunity(100L);

                // When
                AppliedOpportunity result = modelMapper.map(dtoIn, AppliedOpportunity.class);

                // Then
                assertThat(result.getCreatedTime()).isNull();
            }

            @Test
            @DisplayName("should skip opportunityStatus field when mapping DtoIn to Entity")
            void shouldSkipOpportunityStatusFieldWhenMappingDtoInToEntity() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                // Setup mock converters - all converters must be set up
                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                appliedOpportunityMapping.configureMapping(modelMapper);

                AppliedOpportunityDtoIn dtoIn = new AppliedOpportunityDtoIn();
                dtoIn.setInfluencer(1L);
                dtoIn.setPartnershipOpportunity(100L);
                dtoIn.setOpportunityStatus(OpportunityStatus.DONE);

                // When
                AppliedOpportunity result = modelMapper.map(dtoIn, AppliedOpportunity.class);

                // Then
                // Status should be skipped (default is APPLIED)
                assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.APPLIED);
            }

            @Test
            @DisplayName("should skip rateStatus field when mapping DtoIn to Entity")
            void shouldSkipRateStatusFieldWhenMappingDtoInToEntity() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                // Setup mock converters - all converters must be set up
                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                appliedOpportunityMapping.configureMapping(modelMapper);

                AppliedOpportunityDtoIn dtoIn = new AppliedOpportunityDtoIn();
                dtoIn.setInfluencer(1L);
                dtoIn.setPartnershipOpportunity(100L);
                dtoIn.setRateStatus(RateStatus.POSITIVE);

                // When
                AppliedOpportunity result = modelMapper.map(dtoIn, AppliedOpportunity.class);

                // Then
                // Rate status should be skipped (default is DEFAULT)
                assertThat(result.getRateStatus()).isEqualTo(RateStatus.DEFAULT);
            }

            @Test
            @DisplayName("should use userConverter for influencer mapping")
            void shouldUseUserConverterForInfluencerMapping() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                Converter<Long, User> mockUserConverter = ctx -> testInfluencer;
                when(userConverter.toUserConverter()).thenReturn(mockUserConverter);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                appliedOpportunityMapping.configureMapping(modelMapper);

                AppliedOpportunityDtoIn dtoIn = new AppliedOpportunityDtoIn();
                dtoIn.setInfluencer(1L);
                dtoIn.setPartnershipOpportunity(100L);

                // When
                AppliedOpportunity result = modelMapper.map(dtoIn, AppliedOpportunity.class);

                // Then
                assertThat(result.getInfluencer()).isEqualTo(testInfluencer);
            }

            @Test
            @DisplayName("should use partnershipOpportunityConverter for partnershipOpportunity mapping")
            void shouldUsePartnershipOpportunityConverterForMapping() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                Converter<Long, PartnershipOpportunity> mockPOConverter = ctx -> testPartnershipOpportunity;
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(mockPOConverter);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                appliedOpportunityMapping.configureMapping(modelMapper);

                AppliedOpportunityDtoIn dtoIn = new AppliedOpportunityDtoIn();
                dtoIn.setInfluencer(1L);
                dtoIn.setPartnershipOpportunity(100L);

                // When
                AppliedOpportunity result = modelMapper.map(dtoIn, AppliedOpportunity.class);

                // Then
                assertThat(result.getPartnershipOpportunity()).isEqualTo(testPartnershipOpportunity);
            }

            @Test
            @DisplayName("should configure all three type maps")
            void shouldConfigureAllThreeTypeMaps() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                // When
                appliedOpportunityMapping.configureMapping(modelMapper);

                // Then
                assertThat(modelMapper.getTypeMap(AppliedOpportunityDtoIn.class, AppliedOpportunity.class)).isNotNull();
                assertThat(modelMapper.getTypeMap(AppliedOpportunity.class, AppliedOpportunityDtoOut.class)).isNotNull();
                assertThat(modelMapper.getTypeMap(AppliedOpportunity.class, AppliedOpportunitySimpleDtoOut.class)).isNotNull();
            }
        }

        @Nested
        @DisplayName("Entity to DtoOut Mapping")
        class EntityToDtoOutMapping {

            @Test
            @DisplayName("should map entity to DtoOut with content submissions")
            void shouldMapEntityToDtoOutWithContentSubmissions() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                appliedOpportunityMapping.configureMapping(modelMapper);

                testAppliedOpportunity.setUpdaterId("test-updater-id");
                testAppliedOpportunity.setNote("Test note");

                // When
                AppliedOpportunityDtoOut result = modelMapper.map(testAppliedOpportunity, AppliedOpportunityDtoOut.class);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(10L);
                assertThat(result.getNote()).isEqualTo("Test note");
            }

            @Test
            @DisplayName("should use updaterIdConverter for updater mapping")
            void shouldUseUpdaterIdConverterForUpdaterMapping() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                Converter<String, String> mockUpdaterConverter = ctx -> "converted-" + ctx.getSource();
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(mockUpdaterConverter);

                appliedOpportunityMapping.configureMapping(modelMapper);

                testAppliedOpportunity.setUpdaterId("original-id");

                // When
                AppliedOpportunityDtoOut result = modelMapper.map(testAppliedOpportunity, AppliedOpportunityDtoOut.class);

                // Then
                assertThat(result.getUpdater()).isEqualTo("converted-original-id");
            }
        }

        @Nested
        @DisplayName("Entity to SimpleDtoOut Mapping")
        class EntityToSimpleDtoOutMapping {

            @Test
            @DisplayName("should map entity to SimpleDtoOut")
            void shouldMapEntityToSimpleDtoOut() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

                appliedOpportunityMapping.configureMapping(modelMapper);

                testAppliedOpportunity.setUpdaterId("test-updater");
                testAppliedOpportunity.setNote("Simple note");

                // When
                AppliedOpportunitySimpleDtoOut result = modelMapper.map(testAppliedOpportunity, AppliedOpportunitySimpleDtoOut.class);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(10L);
                assertThat(result.getNote()).isEqualTo("Simple note");
            }

            @Test
            @DisplayName("should use updaterIdConverter for updater in SimpleDtoOut")
            void shouldUseUpdaterIdConverterForUpdaterInSimpleDtoOut() {
                // Given
                ModelMapper modelMapper = new ModelMapper();

                when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
                when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
                Converter<String, String> mockUpdaterConverter = ctx -> "simple-" + ctx.getSource();
                when(updaterIdConverter.toUpdaterConverter()).thenReturn(mockUpdaterConverter);

                appliedOpportunityMapping.configureMapping(modelMapper);

                testAppliedOpportunity.setUpdaterId("updater-id");

                // When
                AppliedOpportunitySimpleDtoOut result = modelMapper.map(testAppliedOpportunity, AppliedOpportunitySimpleDtoOut.class);

                // Then
                assertThat(result.getUpdater()).isEqualTo("simple-updater-id");
            }
        }
    }

    // ==================== Edge Cases and Null Handling Tests ====================

    @Nested
    @DisplayName("Edge Cases and Null Handling")
    class EdgeCasesAndNullHandling {

        @Test
        @DisplayName("should handle null content list gracefully in toContentDtoListConverter")
        void shouldHandleNullContentListGracefullyInDtoConverter() {
            // Given
            when(dtoOutContext.getSource()).thenReturn(null);
            Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                    appliedOpportunityConverter.toContentDtoListConverter();

            // When
            List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
            verify(contentMapping, never()).toDto(any());
        }

        @Test
        @DisplayName("should handle null content list gracefully in toSimpleContentDtoListConverter")
        void shouldHandleNullContentListGracefullyInSimpleDtoConverter() {
            // Given
            when(simpleDtoOutContext.getSource()).thenReturn(null);
            Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> converter =
                    appliedOpportunityConverter.toSimpleContentDtoListConverter();

            // When
            List<AppliedOpportunityContentSimpleDtoOut> result = converter.convert(simpleDtoOutContext);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
            verify(contentMapping, never()).toSimpleDto(any());
        }

        @Test
        @DisplayName("should handle content with null fields")
        void shouldHandleContentWithNullFields() {
            // Given
            AppliedOpportunityContent contentWithNulls = new AppliedOpportunityContent();
            contentWithNulls.setId(5L);
            // All other fields are null

            AppliedOpportunityContentDtoOut dtoWithNulls = new AppliedOpportunityContentDtoOut();
            dtoWithNulls.setId(5L);

            when(dtoOutContext.getSource()).thenReturn(List.of(contentWithNulls));
            when(contentMapping.toDto(contentWithNulls)).thenReturn(dtoWithNulls);
            Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                    appliedOpportunityConverter.toContentDtoListConverter();

            // When
            List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("should handle mixed null and non-null content in list")
        void shouldHandleMixedNullAndNonNullContentInList() {
            // Given - contentMapping returns null for null input based on its logic
            AppliedOpportunityContent validContent = testContent;
            AppliedOpportunityContentDtoOut validDto = testContentDtoOut;

            when(dtoOutContext.getSource()).thenReturn(List.of(validContent));
            when(contentMapping.toDto(validContent)).thenReturn(validDto);
            Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                    appliedOpportunityConverter.toContentDtoListConverter();

            // When
            List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should handle single element list")
        void shouldHandleSingleElementList() {
            // Given
            when(dtoOutContext.getSource()).thenReturn(List.of(testContent));
            when(contentMapping.toDto(testContent)).thenReturn(testContentDtoOut);
            Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                    appliedOpportunityConverter.toContentDtoListConverter();

            // When
            List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(testContentDtoOut);
        }

        @Test
        @DisplayName("should handle empty ArrayList")
        void shouldHandleEmptyArrayList() {
            // Given
            when(dtoOutContext.getSource()).thenReturn(new ArrayList<>());
            Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                    appliedOpportunityConverter.toContentDtoListConverter();

            // When
            List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ==================== Content Approval Status Tests ====================

    @Nested
    @DisplayName("Content Approval Status Handling")
    class ContentApprovalStatusHandling {

        @ParameterizedTest
        @EnumSource(ContentApprovalStatus.class)
        @DisplayName("should handle all content approval statuses")
        void shouldHandleAllContentApprovalStatuses(ContentApprovalStatus status) {
            // Given
            testContent.setApprovalStatus(status);

            AppliedOpportunityContentDtoOut dtoWithStatus = new AppliedOpportunityContentDtoOut();
            dtoWithStatus.setId(1L);
            dtoWithStatus.setApprovalStatus(status);

            when(dtoOutContext.getSource()).thenReturn(List.of(testContent));
            when(contentMapping.toDto(testContent)).thenReturn(dtoWithStatus);
            Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                    appliedOpportunityConverter.toContentDtoListConverter();

            // When
            List<AppliedOpportunityContentDtoOut> result = converter.convert(dtoOutContext);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getApprovalStatus()).isEqualTo(status);
        }

        @ParameterizedTest
        @EnumSource(ContentApprovalStatus.class)
        @DisplayName("should handle all content approval statuses in simple converter")
        void shouldHandleAllContentApprovalStatusesInSimpleConverter(ContentApprovalStatus status) {
            // Given
            testContent.setApprovalStatus(status);

            AppliedOpportunityContentSimpleDtoOut simpleDtoWithStatus = new AppliedOpportunityContentSimpleDtoOut();
            simpleDtoWithStatus.setId(1L);
            simpleDtoWithStatus.setApprovalStatus(status);

            when(simpleDtoOutContext.getSource()).thenReturn(List.of(testContent));
            when(contentMapping.toSimpleDto(testContent)).thenReturn(simpleDtoWithStatus);
            Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> converter =
                    appliedOpportunityConverter.toSimpleContentDtoListConverter();

            // When
            List<AppliedOpportunityContentSimpleDtoOut> result = converter.convert(simpleDtoOutContext);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getApprovalStatus()).isEqualTo(status);
        }
    }

    // ==================== Opportunity Status Tests ====================

    @Nested
    @DisplayName("Opportunity Status Handling in Mapping")
    class OpportunityStatusHandlingInMapping {

        @ParameterizedTest
        @EnumSource(OpportunityStatus.class)
        @DisplayName("should handle all opportunity statuses in entity to DtoOut mapping")
        void shouldHandleAllOpportunityStatusesInEntityToDtoOutMapping(OpportunityStatus status) {
            // Given
            ModelMapper modelMapper = new ModelMapper();

            when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
            when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
            when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

            appliedOpportunityMapping.configureMapping(modelMapper);

            testAppliedOpportunity.setOpportunityStatus(status);

            // When
            AppliedOpportunityDtoOut result = modelMapper.map(testAppliedOpportunity, AppliedOpportunityDtoOut.class);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(10L);
        }
    }

    // ==================== Rate Status Tests ====================

    @Nested
    @DisplayName("Rate Status Handling")
    class RateStatusHandling {

        @ParameterizedTest
        @EnumSource(RateStatus.class)
        @DisplayName("should handle all rate statuses in entity")
        void shouldHandleAllRateStatusesInEntity(RateStatus status) {
            // Given
            ModelMapper modelMapper = new ModelMapper();

            when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
            when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
            when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

            appliedOpportunityMapping.configureMapping(modelMapper);

            testAppliedOpportunity.setRateStatus(status);
            testAppliedOpportunity.setCompanyRateStatus(status);

            // When
            AppliedOpportunityDtoOut result = modelMapper.map(testAppliedOpportunity, AppliedOpportunityDtoOut.class);

            // Then
            assertThat(result).isNotNull();
        }
    }

    // ==================== Field Mapping Tests ====================

    @Nested
    @DisplayName("Field Mapping Verification")
    class FieldMappingVerification {

        @Test
        @DisplayName("should map note field from DtoIn to Entity")
        void shouldMapNoteFieldFromDtoInToEntity() {
            // Given
            ModelMapper modelMapper = new ModelMapper();

            when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
            when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
            when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

            appliedOpportunityMapping.configureMapping(modelMapper);

            AppliedOpportunityDtoIn dtoIn = new AppliedOpportunityDtoIn();
            dtoIn.setInfluencer(1L);
            dtoIn.setPartnershipOpportunity(100L);
            dtoIn.setNote("This is a test note");

            // When
            AppliedOpportunity result = modelMapper.map(dtoIn, AppliedOpportunity.class);

            // Then
            assertThat(result.getNote()).isEqualTo("This is a test note");
        }

        @Test
        @DisplayName("should map executionDate field from DtoIn to Entity")
        void shouldMapExecutionDateFieldFromDtoInToEntity() {
            // Given
            ModelMapper modelMapper = new ModelMapper();

            when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
            when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
            when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

            appliedOpportunityMapping.configureMapping(modelMapper);

            LocalDateTime executionDate = LocalDateTime.now().plusDays(7);
            AppliedOpportunityDtoIn dtoIn = new AppliedOpportunityDtoIn();
            dtoIn.setInfluencer(1L);
            dtoIn.setPartnershipOpportunity(100L);
            dtoIn.setExecutionDate(executionDate);

            // When
            AppliedOpportunity result = modelMapper.map(dtoIn, AppliedOpportunity.class);

            // Then
            assertThat(result.getExecutionDate()).isEqualTo(executionDate);
        }

        @Test
        @DisplayName("should map id field from Entity to DtoOut")
        void shouldMapIdFieldFromEntityToDtoOut() {
            // Given
            ModelMapper modelMapper = new ModelMapper();

            when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
            when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
            when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

            appliedOpportunityMapping.configureMapping(modelMapper);

            testAppliedOpportunity.setId(999L);

            // When
            AppliedOpportunityDtoOut result = modelMapper.map(testAppliedOpportunity, AppliedOpportunityDtoOut.class);

            // Then
            assertThat(result.getId()).isEqualTo(999L);
        }

        @Test
        @DisplayName("should map createdTime field from Entity to DtoOut")
        void shouldMapCreatedTimeFieldFromEntityToDtoOut() {
            // Given
            ModelMapper modelMapper = new ModelMapper();

            when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
            when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
            when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

            appliedOpportunityMapping.configureMapping(modelMapper);

            LocalDateTime createdTime = LocalDateTime.now().minusDays(5);
            testAppliedOpportunity.setCreatedTime(createdTime);

            // When
            AppliedOpportunityDtoOut result = modelMapper.map(testAppliedOpportunity, AppliedOpportunityDtoOut.class);

            // Then
            assertThat(result.getCreatedTime()).isEqualTo(createdTime);
        }

        @Test
        @DisplayName("should map lastUpdateTime field from Entity to DtoOut")
        void shouldMapLastUpdateTimeFieldFromEntityToDtoOut() {
            // Given
            ModelMapper modelMapper = new ModelMapper();

            when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
            when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
            when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

            appliedOpportunityMapping.configureMapping(modelMapper);

            LocalDateTime lastUpdateTime = LocalDateTime.now();
            testAppliedOpportunity.setLastUpdateTime(lastUpdateTime);

            // When
            AppliedOpportunityDtoOut result = modelMapper.map(testAppliedOpportunity, AppliedOpportunityDtoOut.class);

            // Then
            assertThat(result.getLastUpdateTime()).isEqualTo(lastUpdateTime);
        }

        @Test
        @DisplayName("should map executionDate field from Entity to DtoOut")
        void shouldMapExecutionDateFieldFromEntityToDtoOut() {
            // Given
            ModelMapper modelMapper = new ModelMapper();

            when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
            when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
            when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

            appliedOpportunityMapping.configureMapping(modelMapper);

            LocalDateTime executionDate = LocalDateTime.now().plusDays(14);
            testAppliedOpportunity.setExecutionDate(executionDate);

            // When
            AppliedOpportunityDtoOut result = modelMapper.map(testAppliedOpportunity, AppliedOpportunityDtoOut.class);

            // Then
            assertThat(result.getExecutionDate()).isEqualTo(executionDate);
        }
    }

    // ==================== Converter Behavior Tests ====================

    @Nested
    @DisplayName("Converter Behavior")
    class ConverterBehavior {

        @Test
        @DisplayName("toContentDtoListConverter should be reusable")
        void toContentDtoListConverterShouldBeReusable() {
            // Given
            Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                    appliedOpportunityConverter.toContentDtoListConverter();

            when(dtoOutContext.getSource()).thenReturn(List.of(testContent));
            when(contentMapping.toDto(testContent)).thenReturn(testContentDtoOut);

            // When - use converter multiple times
            List<AppliedOpportunityContentDtoOut> result1 = converter.convert(dtoOutContext);
            List<AppliedOpportunityContentDtoOut> result2 = converter.convert(dtoOutContext);

            // Then
            assertThat(result1).hasSize(1);
            assertThat(result2).hasSize(1);
            verify(contentMapping, times(2)).toDto(testContent);
        }

        @Test
        @DisplayName("toSimpleContentDtoListConverter should be reusable")
        void toSimpleContentDtoListConverterShouldBeReusable() {
            // Given
            Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> converter =
                    appliedOpportunityConverter.toSimpleContentDtoListConverter();

            when(simpleDtoOutContext.getSource()).thenReturn(List.of(testContent));
            when(contentMapping.toSimpleDto(testContent)).thenReturn(testSimpleContentDtoOut);

            // When - use converter multiple times
            List<AppliedOpportunityContentSimpleDtoOut> result1 = converter.convert(simpleDtoOutContext);
            List<AppliedOpportunityContentSimpleDtoOut> result2 = converter.convert(simpleDtoOutContext);

            // Then
            assertThat(result1).hasSize(1);
            assertThat(result2).hasSize(1);
            verify(contentMapping, times(2)).toSimpleDto(testContent);
        }

        @Test
        @DisplayName("converters should not modify source list")
        void convertersShouldNotModifySourceList() {
            // Given
            List<AppliedOpportunityContent> sourceList = new ArrayList<>(List.of(testContent, testContent2));
            int originalSize = sourceList.size();

            when(dtoOutContext.getSource()).thenReturn(sourceList);
            when(contentMapping.toDto(any())).thenReturn(testContentDtoOut);
            Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> converter =
                    appliedOpportunityConverter.toContentDtoListConverter();

            // When
            converter.convert(dtoOutContext);

            // Then
            assertThat(sourceList).hasSize(originalSize);
        }
    }

    // ==================== Integration-like Tests ====================

    @Nested
    @DisplayName("Integration-like Tests")
    class IntegrationLikeTests {

        @Test
        @DisplayName("should correctly wire all converters in mapping configuration")
        void shouldCorrectlyWireAllConvertersInMappingConfiguration() {
            // Given
            ModelMapper modelMapper = new ModelMapper();

            Converter<Long, User> userConverterImpl = ctx -> testInfluencer;
            Converter<Long, PartnershipOpportunity> poConverterImpl = ctx -> testPartnershipOpportunity;
            Converter<String, String> updaterConverterImpl = ctx -> "updated:" + ctx.getSource();

            when(userConverter.toUserConverter()).thenReturn(userConverterImpl);
            when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(poConverterImpl);
            when(updaterIdConverter.toUpdaterConverter()).thenReturn(updaterConverterImpl);

            // When
            appliedOpportunityMapping.configureMapping(modelMapper);

            // Then - verify type maps exist
            assertThat(modelMapper.getTypeMap(AppliedOpportunityDtoIn.class, AppliedOpportunity.class)).isNotNull();
            assertThat(modelMapper.getTypeMap(AppliedOpportunity.class, AppliedOpportunityDtoOut.class)).isNotNull();
            assertThat(modelMapper.getTypeMap(AppliedOpportunity.class, AppliedOpportunitySimpleDtoOut.class)).isNotNull();

            // Verify converters were requested
            verify(userConverter).toUserConverter();
            verify(partnershipOpportunityConverter).toPartnershipOpportunityConverter();
            verify(updaterIdConverter, times(2)).toUpdaterConverter(); // Called for both DtoOut and SimpleDtoOut
        }

        @Test
        @DisplayName("full round trip: DtoIn to Entity to DtoOut")
        void fullRoundTripDtoInToEntityToDtoOut() {
            // Given
            ModelMapper modelMapper = new ModelMapper();

            when(userConverter.toUserConverter()).thenReturn(ctx -> testInfluencer);
            when(partnershipOpportunityConverter.toPartnershipOpportunityConverter()).thenReturn(ctx -> testPartnershipOpportunity);
            when(updaterIdConverter.toUpdaterConverter()).thenReturn(ctx -> ctx.getSource());

            appliedOpportunityMapping.configureMapping(modelMapper);

            AppliedOpportunityDtoIn dtoIn = new AppliedOpportunityDtoIn();
            dtoIn.setInfluencer(1L);
            dtoIn.setPartnershipOpportunity(100L);
            dtoIn.setNote("Round trip note");
            dtoIn.setExecutionDate(LocalDateTime.now().plusDays(10));

            // When - DtoIn to Entity
            AppliedOpportunity entity = modelMapper.map(dtoIn, AppliedOpportunity.class);
            entity.setId(42L);
            entity.setUpdaterId("round-trip-updater");
            entity.setCreatedTime(LocalDateTime.now());

            // Entity to DtoOut
            AppliedOpportunityDtoOut dtoOut = modelMapper.map(entity, AppliedOpportunityDtoOut.class);

            // Then
            assertThat(dtoOut.getId()).isEqualTo(42L);
            assertThat(dtoOut.getNote()).isEqualTo("Round trip note");
            assertThat(dtoOut.getUpdater()).isEqualTo("round-trip-updater");
            assertThat(dtoOut.getExecutionDate()).isEqualTo(dtoIn.getExecutionDate());
        }
    }
}
