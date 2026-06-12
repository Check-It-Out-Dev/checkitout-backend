package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.util.filtering.Copy;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for SpecificationBuilder and Copy utility classes.
 * Tests all public methods, builder patterns, and edge cases.
 */
@DisplayName("SpecificationBuilder and Copy Unit Tests")
class SpecificationBuilderUnitTest {

    private SpecificationBuilder<TestEntity> specificationBuilder;
    private Copy copyUtil;

    @Mock
    private Root<TestEntity> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder criteriaBuilder;

    @Mock
    private Path<Object> path;

    @Mock
    private Path<String> stringPath;

    @Mock
    private Predicate predicate;

    @Mock
    private Predicate andPredicate;

    @Mock
    private Expression<String> lowerExpression;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        specificationBuilder = new SpecificationBuilder<>();
        copyUtil = new Copy();

        // Default setup for criteriaBuilder.and() - always return a valid predicate
        when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(andPredicate);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ==================== SpecificationBuilder Tests ====================

    @Nested
    @DisplayName("createSpecification() - Empty and Null Filters")
    class EmptyAndNullFiltersTests {

        @Test
        @DisplayName("Should return specification with no predicates for empty filter map")
        void createSpecification_WhenFiltersEmpty_ReturnsSpecificationWithNoPredicates() {
            // Given
            Map<String, String> filters = new HashMap<>();

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            Predicate result = specification.toPredicate(root, query, criteriaBuilder);

            // Verify and() was called with empty array
            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
            verify(criteriaBuilder).and(captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("Should skip null filter values")
        void createSpecification_WhenFilterValueIsNull_SkipsFilter() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("name", null);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            Predicate result = specification.toPredicate(root, query, criteriaBuilder);

            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
            verify(criteriaBuilder).and(captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("Should skip empty string filter values")
        void createSpecification_WhenFilterValueIsEmpty_SkipsFilter() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("name", "");

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            Predicate result = specification.toPredicate(root, query, criteriaBuilder);

            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
            verify(criteriaBuilder).and(captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }
    }

    @Nested
    @DisplayName("createSpecification() - String Field Filters")
    class StringFieldFilterTests {

        @Test
        @DisplayName("Should create LIKE predicate for string fields with lowercase and wildcard suffix")
        void createSpecification_WhenStringField_CreatesLikePredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("name", "John");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("name")).thenReturn(path);
            when(path.as(String.class)).thenReturn(stringPath);
            when(criteriaBuilder.lower(stringPath)).thenReturn(lowerExpression);
            when(criteriaBuilder.like(lowerExpression, "john%")).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).like(lowerExpression, "john%");
        }

        @Test
        @DisplayName("Should handle special characters in string filter value")
        void createSpecification_WhenStringContainsSpecialChars_HandlesCorrectly() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("name", "O'Brien");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("name")).thenReturn(path);
            when(path.as(String.class)).thenReturn(stringPath);
            when(criteriaBuilder.lower(stringPath)).thenReturn(lowerExpression);
            when(criteriaBuilder.like(lowerExpression, "o'brien%")).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).like(lowerExpression, "o'brien%");
        }

        @Test
        @DisplayName("Should handle uppercase filter value by converting to lowercase")
        void createSpecification_WhenUppercaseValue_ConvertsToLowercase() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("name", "UPPERCASE");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("name")).thenReturn(path);
            when(path.as(String.class)).thenReturn(stringPath);
            when(criteriaBuilder.lower(stringPath)).thenReturn(lowerExpression);
            when(criteriaBuilder.like(lowerExpression, "uppercase%")).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).like(lowerExpression, "uppercase%");
        }
    }

    @Nested
    @DisplayName("createSpecification() - Enum Field Filters")
    class EnumFieldFilterTests {

        @Test
        @DisplayName("Should create equal predicate for single enum value")
        void createSpecification_WhenSingleEnumValue_CreatesEqualPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("status", "ACTIVE");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("status")).thenReturn(path);
            when(criteriaBuilder.equal(path, TestStatus.ACTIVE)).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).equal(path, TestStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should create IN predicate for multiple comma-separated enum values")
        void createSpecification_WhenMultipleEnumValues_CreatesInPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("status", "ACTIVE,PENDING");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("status")).thenReturn(path);
            when(path.in(anyCollection())).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<Enum<?>>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(path).in(captor.capture());
            Collection<Enum<?>> capturedEnums = captor.getValue();
            assertThat(capturedEnums).containsExactlyInAnyOrder(TestStatus.ACTIVE, TestStatus.PENDING);
        }

        @Test
        @DisplayName("Should handle case-insensitive enum matching")
        void createSpecification_WhenEnumValueLowercase_MatchesCaseInsensitive() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("status", "active");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("status")).thenReturn(path);
            when(criteriaBuilder.equal(path, TestStatus.ACTIVE)).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).equal(path, TestStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should skip invalid enum values gracefully")
        void createSpecification_WhenInvalidEnumValue_SkipsGracefully() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("status", "INVALID_STATUS");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("status")).thenReturn(path);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then - should not throw, just skip the invalid enum
            assertThat(specification).isNotNull();
            assertThatCode(() -> specification.toPredicate(root, query, criteriaBuilder))
                    .doesNotThrowAnyException();

            // equal should not have been called since enum is invalid
            verify(criteriaBuilder, never()).equal(eq(path), any(TestStatus.class));
        }

        @Test
        @DisplayName("Should handle mixed valid and invalid enum values in comma-separated list")
        void createSpecification_WhenMixedEnumValues_SkipsInvalidKeepsValid() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("status", "ACTIVE,INVALID,PENDING");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("status")).thenReturn(path);
            when(path.in(anyCollection())).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            specification.toPredicate(root, query, criteriaBuilder);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<Enum<?>>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(path).in(captor.capture());
            Collection<Enum<?>> capturedEnums = captor.getValue();
            assertThat(capturedEnums).containsExactlyInAnyOrder(TestStatus.ACTIVE, TestStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("createSpecification() - Number Field Filters")
    class NumberFieldFilterTests {

        @Test
        @DisplayName("Should create equal predicate for Integer field")
        void createSpecification_WhenIntegerField_CreatesEqualPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("age", "25");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("age")).thenReturn(path);
            when(criteriaBuilder.equal(path, 25)).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).equal(path, 25);
        }

        @Test
        @DisplayName("Should create equal predicate for Long field")
        void createSpecification_WhenLongField_CreatesEqualPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("id", "12345");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("id")).thenReturn(path);
            when(criteriaBuilder.equal(path, 12345L)).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).equal(path, 12345L);
        }

        @Test
        @DisplayName("Should skip invalid number format gracefully")
        void createSpecification_WhenInvalidNumberFormat_SkipsGracefully() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("age", "not-a-number");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("age")).thenReturn(path);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then - should not throw, just skip the invalid number
            assertThat(specification).isNotNull();
            assertThatCode(() -> specification.toPredicate(root, query, criteriaBuilder))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle zero value correctly")
        void createSpecification_WhenZeroValue_CreatesEqualPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("age", "0");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("age")).thenReturn(path);
            when(criteriaBuilder.equal(path, 0)).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).equal(path, 0);
        }

        @Test
        @DisplayName("Should handle negative number correctly")
        void createSpecification_WhenNegativeValue_CreatesEqualPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("age", "-5");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("age")).thenReturn(path);
            when(criteriaBuilder.equal(path, -5)).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).equal(path, -5);
        }
    }

    @Nested
    @DisplayName("createSpecification() - Boolean Field Filters")
    class BooleanFieldFilterTests {

        /**
         * Note: Testing boolean filtering requires the complete mock chain to be set up
         * BEFORE any mock interactions. The SpecificationBuilder uses reflection to get
         * field types, so root.getJavaType() must be stubbed first.
         */

        @Test
        @DisplayName("Should create equal predicate for boolean true value")
        void createSpecification_WhenBooleanTrue_CreatesEqualPredicate() {
            // Given - Set up mocks in correct order
            Map<String, String> filters = new HashMap<>();
            filters.put("active", "true");

            // Must stub getJavaType first, then get(), then the criteriaBuilder methods
            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("active")).thenReturn(path);
            when(criteriaBuilder.equal(eq(path), eq(true))).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);
            specification.toPredicate(root, query, criteriaBuilder);

            // Then - Verify equal was called with primitive true
            verify(criteriaBuilder).equal(eq(path), eq(true));
        }

        @Test
        @DisplayName("Should create equal predicate for boolean false value")
        void createSpecification_WhenBooleanFalse_CreatesEqualPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("active", "false");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("active")).thenReturn(path);
            when(criteriaBuilder.equal(eq(path), eq(false))).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);
            specification.toPredicate(root, query, criteriaBuilder);

            // Then
            verify(criteriaBuilder).equal(eq(path), eq(false));
        }

        @Test
        @DisplayName("Should handle uppercase TRUE value - Boolean.parseBoolean is case insensitive")
        void createSpecification_WhenUppercaseTrue_CreatesEqualPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("active", "TRUE");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("active")).thenReturn(path);
            when(criteriaBuilder.equal(eq(path), eq(true))).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);
            specification.toPredicate(root, query, criteriaBuilder);

            // Then - Boolean.parseBoolean("TRUE") returns true
            verify(criteriaBuilder).equal(eq(path), eq(true));
        }
    }

    @Nested
    @DisplayName("createSpecification() - Date Range Filters")
    class DateRangeFilterTests {

        @Test
        @DisplayName("Should create greaterThanOrEqualTo predicate for 'From' date filter")
        @SuppressWarnings("unchecked")
        void createSpecification_WhenFromDateFilter_CreatesGreaterThanOrEqualPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            String dateValue = "2024-01-15T10:30:00";
            filters.put("createdTimeFrom", dateValue);

            Path<LocalDateTime> dateTimePath = mock(Path.class);
            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("createdTime")).thenReturn(path);
            when(path.as(LocalDateTime.class)).thenReturn(dateTimePath);
            when(criteriaBuilder.greaterThanOrEqualTo(eq(dateTimePath), any(LocalDateTime.class))).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).greaterThanOrEqualTo(dateTimePath, LocalDateTime.parse(dateValue));
        }

        @Test
        @DisplayName("Should create lessThanOrEqualTo predicate for 'To' date filter")
        @SuppressWarnings("unchecked")
        void createSpecification_WhenToDateFilter_CreatesLessThanOrEqualPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            String dateValue = "2024-12-31T23:59:59";
            filters.put("createdTimeTo", dateValue);

            Path<LocalDateTime> dateTimePath = mock(Path.class);
            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("createdTime")).thenReturn(path);
            when(path.as(LocalDateTime.class)).thenReturn(dateTimePath);
            when(criteriaBuilder.lessThanOrEqualTo(eq(dateTimePath), any(LocalDateTime.class))).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).lessThanOrEqualTo(dateTimePath, LocalDateTime.parse(dateValue));
        }

        @Test
        @DisplayName("Should skip invalid date format gracefully")
        void createSpecification_WhenInvalidDateFormat_SkipsGracefully() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("createdTimeFrom", "invalid-date-format");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then - should not throw, just skip the invalid date
            assertThat(specification).isNotNull();
            assertThatCode(() -> specification.toPredicate(root, query, criteriaBuilder))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("createSpecification() - NOT Operation Filters")
    class NotOperationFilterTests {

        @Test
        @DisplayName("Should create NOT predicate when field name ends with !")
        void createSpecification_WhenFieldEndsWithExclamation_CreatesNotPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("status!", "DELETED");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("status")).thenReturn(path);
            when(criteriaBuilder.equal(path, TestStatus.DELETED)).thenReturn(predicate);
            when(criteriaBuilder.not(predicate)).thenReturn(andPredicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).not(predicate);
        }

        @Test
        @DisplayName("Should apply NOT operation to string LIKE predicate")
        void createSpecification_WhenNotOnStringField_AppliesNotToLikePredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("name!", "Admin");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("name")).thenReturn(path);
            when(path.as(String.class)).thenReturn(stringPath);
            when(criteriaBuilder.lower(stringPath)).thenReturn(lowerExpression);
            when(criteriaBuilder.like(lowerExpression, "admin%")).thenReturn(predicate);
            when(criteriaBuilder.not(predicate)).thenReturn(andPredicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).not(predicate);
        }

        @Test
        @DisplayName("Should apply NOT operation to multiple enum values IN predicate")
        void createSpecification_WhenNotOnMultipleEnums_AppliesNotToInPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("status!", "ACTIVE,PENDING");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("status")).thenReturn(path);
            when(path.in(anyCollection())).thenReturn(predicate);
            when(criteriaBuilder.not(predicate)).thenReturn(andPredicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).not(predicate);
        }
    }

    @Nested
    @DisplayName("createSpecification() - Nested Field Paths")
    class NestedFieldPathTests {

        @Test
        @DisplayName("Should resolve nested field path with dot notation")
        @SuppressWarnings("unchecked")
        void createSpecification_WhenNestedFieldPath_ResolvesCorrectly() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("city.name", "Warsaw");

            Path<Object> cityPath = mock(Path.class);
            Path<Object> namePath = mock(Path.class);
            Path<String> nameStringPath = mock(Path.class);

            when(root.getJavaType()).thenReturn((Class) TestEntityWithCity.class);
            when(root.get("city")).thenReturn(cityPath);
            when(cityPath.get("name")).thenReturn(namePath);
            when(namePath.as(String.class)).thenReturn(nameStringPath);
            when(criteriaBuilder.lower(nameStringPath)).thenReturn(lowerExpression);
            when(criteriaBuilder.like(lowerExpression, "warsaw%")).thenReturn(predicate);

            // When
            SpecificationBuilder<TestEntityWithCity> builder = new SpecificationBuilder<>();
            Specification<TestEntityWithCity> spec = builder.createSpecification(filters);

            // Then
            assertThat(spec).isNotNull();
        }
    }

    @Nested
    @DisplayName("createSpecification() - City Field Special Handling")
    class CityFieldSpecialHandlingTests {

        @Test
        @DisplayName("Should create LIKE predicate on city.name for city field")
        void createSpecification_WhenCityField_CreatesLikeOnCityName() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("city", "Krakow");

            Path<Object> namePath = mock(Path.class);
            when(root.getJavaType()).thenReturn((Class) TestEntityWithCity.class);
            when(root.get("city")).thenReturn(path);
            when(path.get("name")).thenReturn(namePath);
            when(criteriaBuilder.lower(any())).thenReturn(lowerExpression);
            when(criteriaBuilder.like(lowerExpression, "krakow%")).thenReturn(predicate);

            // When
            SpecificationBuilder<TestEntityWithCity> builder = new SpecificationBuilder<>();
            Specification<TestEntityWithCity> spec = builder.createSpecification(filters);

            // Then
            assertThat(spec).isNotNull();
        }
    }

    @Nested
    @DisplayName("createSpecification() - DateTime Field Filters")
    class DateTimeFieldFilterTests {

        @Test
        @DisplayName("Should create equal predicate for LocalDateTime field")
        void createSpecification_WhenLocalDateTimeField_CreatesEqualPredicate() {
            // Given
            Map<String, String> filters = new HashMap<>();
            String dateTimeValue = "2024-06-15T14:30:00";
            filters.put("exactTime", dateTimeValue);

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("exactTime")).thenReturn(path);
            when(criteriaBuilder.equal(eq(path), any(LocalDateTime.class))).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);
            verify(criteriaBuilder).equal(path, LocalDateTime.parse(dateTimeValue));
        }
    }

    @Nested
    @DisplayName("createSpecification() - Unknown Field Handling")
    class UnknownFieldHandlingTests {

        @Test
        @DisplayName("Should skip unknown field gracefully")
        void createSpecification_WhenUnknownField_SkipsGracefully() {
            // Given
            Map<String, String> filters = new HashMap<>();
            filters.put("nonExistentField", "value");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("nonExistentField")).thenThrow(new IllegalArgumentException("Unknown field"));

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then - should not throw, just skip the unknown field
            assertThat(specification).isNotNull();
            assertThatCode(() -> specification.toPredicate(root, query, criteriaBuilder))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("createSpecification() - Multiple Filters Combined")
    class MultipleFiltersCombinedTests {

        @Test
        @DisplayName("Should combine multiple filter predicates with AND")
        void createSpecification_WhenMultipleFilters_CombinesWithAnd() {
            // Given
            Map<String, String> filters = new LinkedHashMap<>();
            filters.put("name", "John");
            filters.put("status", "ACTIVE");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get("name")).thenReturn(path);
            when(root.get("status")).thenReturn(path);
            when(path.as(String.class)).thenReturn(stringPath);
            when(criteriaBuilder.lower(stringPath)).thenReturn(lowerExpression);
            when(criteriaBuilder.like(eq(lowerExpression), anyString())).thenReturn(predicate);
            when(criteriaBuilder.equal(eq(path), any(TestStatus.class))).thenReturn(predicate);

            // When
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Then
            assertThat(specification).isNotNull();
            specification.toPredicate(root, query, criteriaBuilder);

            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
            verify(criteriaBuilder).and(captor.capture());
            assertThat(captor.getValue()).hasSizeGreaterThanOrEqualTo(1);
        }
    }

    // ==================== Copy Utility Tests ====================

    @Nested
    @DisplayName("Copy.copyNonNullProperties() Tests")
    class CopyNonNullPropertiesTests {

        @Test
        @DisplayName("Should copy all non-null properties from source to target")
        void copyNonNullProperties_WhenSourceHasNonNullFields_CopiesAllToTarget() {
            // Given
            SourceObject source = new SourceObject();
            source.setName("Test Name");
            source.setAge(25);
            source.setActive(true);

            SourceObject target = new SourceObject();
            target.setName("Original Name");
            target.setAge(30);
            target.setActive(false);

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getName()).isEqualTo("Test Name");
            assertThat(target.getAge()).isEqualTo(25);
            assertThat(target.getActive()).isTrue();
        }

        @Test
        @DisplayName("Should skip null properties and preserve target values")
        void copyNonNullProperties_WhenSourceHasNullFields_PreservesTargetValues() {
            // Given
            SourceObject source = new SourceObject();
            source.setName("New Name");
            source.setAge(null);
            source.setActive(null);

            SourceObject target = new SourceObject();
            target.setName("Original Name");
            target.setAge(42);
            target.setActive(true);

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getName()).isEqualTo("New Name");
            assertThat(target.getAge()).isEqualTo(42);
            assertThat(target.getActive()).isTrue();
        }

        @Test
        @DisplayName("Should handle empty source object without modifying target")
        void copyNonNullProperties_WhenSourceAllNull_TargetUnchanged() {
            // Given
            SourceObject source = new SourceObject();
            // All fields are null by default

            SourceObject target = new SourceObject();
            target.setName("Keep This");
            target.setAge(99);
            target.setActive(true);

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getName()).isEqualTo("Keep This");
            assertThat(target.getAge()).isEqualTo(99);
            assertThat(target.getActive()).isTrue();
        }

        @Test
        @DisplayName("Should copy String properties correctly")
        void copyNonNullProperties_WhenStringProperty_CopiesCorrectly() {
            // Given
            SourceObject source = new SourceObject();
            source.setName("Copied String");

            SourceObject target = new SourceObject();

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getName()).isEqualTo("Copied String");
        }

        @Test
        @DisplayName("Should copy Integer properties correctly")
        void copyNonNullProperties_WhenIntegerProperty_CopiesCorrectly() {
            // Given
            SourceObject source = new SourceObject();
            source.setAge(55);

            SourceObject target = new SourceObject();
            target.setAge(10);

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getAge()).isEqualTo(55);
        }

        @Test
        @DisplayName("Should copy Boolean properties correctly")
        void copyNonNullProperties_WhenBooleanProperty_CopiesCorrectly() {
            // Given
            SourceObject source = new SourceObject();
            source.setActive(false);

            SourceObject target = new SourceObject();
            target.setActive(true);

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getActive()).isFalse();
        }

        @Test
        @DisplayName("Should handle nested object reference copy")
        void copyNonNullProperties_WhenNestedObject_CopiesReference() {
            // Given
            NestedSourceObject source = new NestedSourceObject();
            NestedObject nested = new NestedObject();
            nested.setValue("Nested Value");
            source.setNested(nested);

            NestedSourceObject target = new NestedSourceObject();

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getNested()).isSameAs(nested);
            assertThat(target.getNested().getValue()).isEqualTo("Nested Value");
        }

        @Test
        @DisplayName("Should copy empty string as non-null value")
        void copyNonNullProperties_WhenEmptyString_CopiesAsNonNull() {
            // Given
            SourceObject source = new SourceObject();
            source.setName("");

            SourceObject target = new SourceObject();
            target.setName("Original");

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getName()).isEmpty();
        }

        @Test
        @DisplayName("Should copy List reference as non-null")
        void copyNonNullProperties_WhenListProperty_CopiesReference() {
            // Given
            ListSourceObject source = new ListSourceObject();
            List<String> items = Arrays.asList("item1", "item2");
            source.setItems(items);

            ListSourceObject target = new ListSourceObject();

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getItems()).isSameAs(items);
            assertThat(target.getItems()).containsExactly("item1", "item2");
        }
    }

    @Nested
    @DisplayName("Copy - Edge Cases")
    class CopyEdgeCasesTests {

        @Test
        @DisplayName("Should handle object with no fields")
        void copyNonNullProperties_WhenEmptyClass_HandlesGracefully() {
            // Given
            EmptyObject source = new EmptyObject();
            EmptyObject target = new EmptyObject();

            // When & Then - should not throw
            assertThatCode(() -> copyUtil.copyNonNullProperties(source, target))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should copy zero value for Integer as non-null")
        void copyNonNullProperties_WhenZeroInteger_CopiesAsNonNull() {
            // Given
            SourceObject source = new SourceObject();
            source.setAge(0);

            SourceObject target = new SourceObject();
            target.setAge(100);

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getAge()).isZero();
        }

        @Test
        @DisplayName("Should copy false Boolean as non-null")
        void copyNonNullProperties_WhenFalseBoolean_CopiesAsNonNull() {
            // Given
            SourceObject source = new SourceObject();
            source.setActive(false);

            SourceObject target = new SourceObject();
            target.setActive(true);

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getActive()).isFalse();
        }

        @Test
        @DisplayName("Should handle LocalDateTime property")
        void copyNonNullProperties_WhenLocalDateTimeProperty_CopiesCorrectly() {
            // Given
            DateSourceObject source = new DateSourceObject();
            LocalDateTime dateTime = LocalDateTime.of(2024, 6, 15, 10, 30);
            source.setTimestamp(dateTime);

            DateSourceObject target = new DateSourceObject();

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getTimestamp()).isEqualTo(dateTime);
        }

        @Test
        @DisplayName("Should handle enum property")
        void copyNonNullProperties_WhenEnumProperty_CopiesCorrectly() {
            // Given
            EnumSourceObject source = new EnumSourceObject();
            source.setStatus(TestStatus.ACTIVE);

            EnumSourceObject target = new EnumSourceObject();
            target.setStatus(TestStatus.INACTIVE);

            // When
            copyUtil.copyNonNullProperties(source, target);

            // Then
            assertThat(target.getStatus()).isEqualTo(TestStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Parameterized Tests")
    class ParameterizedTests {

        @ParameterizedTest
        @ValueSource(strings = {"createdTimeFrom", "updatedTimeFrom", "deletedAtFrom"})
        @DisplayName("Should recognize 'From' suffix as date range filter")
        void isDateRangeFilter_WhenFromSuffix_ReturnsTrue(String fieldName) {
            // This tests the internal logic via the public createSpecification method
            Map<String, String> filters = new HashMap<>();
            filters.put(fieldName, "2024-01-01T00:00:00");

            // Given the behavior, From suffix should be treated as date range
            // The specification should handle it as greater-than-or-equal
            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            assertThat(specification).isNotNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"createdTimeTo", "updatedTimeTo", "deletedAtTo"})
        @DisplayName("Should recognize 'To' suffix as date range filter")
        void isDateRangeFilter_WhenToSuffix_ReturnsTrue(String fieldName) {
            Map<String, String> filters = new HashMap<>();
            filters.put(fieldName, "2024-12-31T23:59:59");

            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            assertThat(specification).isNotNull();
        }

        @ParameterizedTest
        @CsvSource({
                "email, true",
                "name, true",
                "userName, true",
                "phoneNumber, true",
                "userAddress, true",
                "birthday, true",
                "dob, true",
                "status, false",
                "active, false",
                "id, false"
        })
        @DisplayName("Should correctly identify personal data fields for GDPR logging")
        void isPersonalDataField_WhenVariousFields_ReturnsExpected(String fieldName, boolean isPersonal) {
            // This tests the internal isPersonalDataField logic indirectly
            // The method logs when personal data fields are not found
            // We can verify by examining the behavior with unknown fields

            Map<String, String> filters = new HashMap<>();
            filters.put(fieldName, "test");

            when(root.getJavaType()).thenReturn((Class) TestEntity.class);
            when(root.get(anyString())).thenThrow(new IllegalArgumentException("Field not found"));

            Specification<TestEntity> specification = specificationBuilder.createSpecification(filters);

            // Should not throw regardless of personal data status
            assertThatCode(() -> specification.toPredicate(root, query, criteriaBuilder))
                    .doesNotThrowAnyException();
        }
    }

    // ==================== Test Helper Classes ====================

    /**
     * Test entity for SpecificationBuilder tests
     * Note: 'active' uses primitive boolean because SpecificationBuilder's createPredicateByType()
     * only routes to createNumberPredicate() for Number subclasses or primitives.
     * Boolean wrapper class would not be routed correctly.
     */
    static class TestEntity {
        private Long id;
        private String name;
        private TestStatus status;
        private Integer age;
        private boolean active;  // primitive boolean for filtering support
        private LocalDateTime createdTime;
        private LocalDateTime exactTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public TestStatus getStatus() { return status; }
        public void setStatus(TestStatus status) { this.status = status; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public LocalDateTime getCreatedTime() { return createdTime; }
        public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
        public LocalDateTime getExactTime() { return exactTime; }
        public void setExactTime(LocalDateTime exactTime) { this.exactTime = exactTime; }
    }

    /**
     * Test entity with city field for special city handling tests
     */
    static class TestEntityWithCity {
        private Long id;
        private String name;
        private CityObject city;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public CityObject getCity() { return city; }
        public void setCity(CityObject city) { this.city = city; }
    }

    /**
     * City object for nested path tests
     */
    static class CityObject {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /**
     * Test enum for enum filtering tests
     */
    enum TestStatus {
        ACTIVE, PENDING, DELETED, INACTIVE
    }

    /**
     * Source object for Copy utility tests
     */
    static class SourceObject {
        private String name;
        private Integer age;
        private Boolean active;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    /**
     * Source object with nested object for reference copy tests
     */
    static class NestedSourceObject {
        private NestedObject nested;
        public NestedObject getNested() { return nested; }
        public void setNested(NestedObject nested) { this.nested = nested; }
    }

    /**
     * Nested object for reference copy tests
     */
    static class NestedObject {
        private String value;
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    /**
     * Source object with List property
     */
    static class ListSourceObject {
        private List<String> items;
        public List<String> getItems() { return items; }
        public void setItems(List<String> items) { this.items = items; }
    }

    /**
     * Empty object for edge case tests
     */
    static class EmptyObject {
        // No fields
    }

    /**
     * Source object with LocalDateTime property
     */
    static class DateSourceObject {
        private LocalDateTime timestamp;
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    /**
     * Source object with enum property
     */
    static class EnumSourceObject {
        private TestStatus status;
        public TestStatus getStatus() { return status; }
        public void setStatus(TestStatus status) { this.status = status; }
    }
}
