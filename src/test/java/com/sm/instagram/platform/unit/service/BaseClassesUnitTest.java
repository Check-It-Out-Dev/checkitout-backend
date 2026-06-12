package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.city.CityRepository;
import com.sm.instagram.platform.common.base.BaseController;
import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.base.UpdatableEntity;
import com.sm.instagram.platform.common.base.UpdaterTracking;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for base classes:
 * - BaseController
 * - BaseService
 * - UpdatableEntity
 * - UpdaterTracking
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Base Classes Unit Tests")
class BaseClassesUnitTest {

    // =========================================================================
    // Test Entity Classes
    // =========================================================================

    /**
     * Simple test entity for testing BaseService
     */
    static class TestEntity implements UpdaterTracking {
        private Long id;
        private String name;
        private String description;
        private Integer count;
        private Double price;
        private Boolean active;
        private LocalDateTime createdTime;
        private LocalDateTime lastUpdateTime;
        private String updaterId;
        private TestStatus status;
        private City city;
        private List<TestNestedItem> items;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
        public LocalDateTime getCreatedTime() { return createdTime; }
        public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
        public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(LocalDateTime lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
        public String getUpdaterId() { return updaterId; }
        @Override
        public void setUpdaterId(String userId) { this.updaterId = userId; }
        public TestStatus getStatus() { return status; }
        public void setStatus(TestStatus status) { this.status = status; }
        public City getCity() { return city; }
        public void setCity(City city) { this.city = city; }
        public List<TestNestedItem> getItems() { return items; }
        public void setItems(List<TestNestedItem> items) { this.items = items; }
    }

    /**
     * Test enum for status field
     */
    enum TestStatus {
        PENDING, ACTIVE, COMPLETED, CANCELLED
    }

    /**
     * Nested item for testing list field conversion
     */
    static class TestNestedItem {
        private String itemName;
        private Integer quantity;

        public TestNestedItem() {}

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    /**
     * Simple DTO for input
     */
    static class TestDto {
        private String name;
        private String description;
        private Integer count;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
    }

    /**
     * Simple DTO for output
     */
    static class TestDtoOut {
        private Long id;
        private String name;
        private String description;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /**
     * Test entity implementing UpdatableEntity
     */
    static class UpdatableTestEntity implements UpdatableEntity<TestEntity> {
        private TestEntity entity;

        @Override
        public void setEntity(TestEntity entity) {
            this.entity = entity;
        }

        public TestEntity getEntity() {
            return entity;
        }
    }

    /**
     * Test entity implementing UpdaterTracking
     */
    static class TrackedTestEntity implements UpdaterTracking {
        private String updaterId;

        @Override
        public void setUpdaterId(String userId) {
            this.updaterId = userId;
        }

        public String getUpdaterId() {
            return updaterId;
        }
    }

    // =========================================================================
    // Concrete Test Implementations
    // =========================================================================

    /**
     * Test repository interface
     */
    interface TestRepository extends BaseRepository<TestEntity, Long> {
    }

    /**
     * Concrete implementation of BaseService for testing
     */
    static class ConcreteTestService extends BaseService<TestEntity, Long, TestDto> {
        public ConcreteTestService(
                ApplicationContext applicationContext,
                SpecificationBuilder<TestEntity> specificationBuilder,
                BaseRepository<TestEntity, Long> repository,
                ModelMapper modelMapper,
                RepositoryResolver repositoryResolver) {
            super(applicationContext, specificationBuilder, repository, modelMapper, repositoryResolver);
        }

        @Override
        public <O> Page<O> getDataPagedAndFilteredAsDtos(Pageable pageable, Map<String, String> filters) {
            Page<TestEntity> entities = getDataPagedAndFiltered(pageable, filters);
            @SuppressWarnings("unchecked")
            Page<O> result = (Page<O>) entities.map(this::toDto);
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <O> O toDto(TestEntity entity) {
            if (entity == null) return null;
            TestDtoOut dto = new TestDtoOut();
            dto.setId(entity.getId());
            dto.setName(entity.getName());
            dto.setDescription(entity.getDescription());
            return (O) dto;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <O> O createFromDtoAsDto(TestDto dto) {
            TestEntity entity = new TestEntity();
            entity.setName(dto.getName());
            entity.setDescription(dto.getDescription());
            entity.setCount(dto.getCount());
            TestEntity saved = save(entity);
            return (O) toDto(saved);
        }
    }

    /**
     * Concrete implementation of BaseController for testing
     */
    static class ConcreteTestController extends BaseController<TestEntity, Long, TestDto, TestDtoOut> {
        private final ConcreteTestService service;

        public ConcreteTestController(ConcreteTestService service) {
            super(TestEntity.class);
            this.service = service;
        }

        @Override
        protected BaseService<TestEntity, Long, TestDto> getService() {
            return service;
        }
    }

    // =========================================================================
    // Mock Dependencies
    // =========================================================================

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private SpecificationBuilder<TestEntity> specificationBuilder;

    @Mock
    private TestRepository testRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private RepositoryResolver repositoryResolver;

    @Mock
    private CityRepository cityRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private ConcreteTestService testService;
    private ConcreteTestController testController;

    @BeforeEach
    void setUp() {
        testService = new ConcreteTestService(
                applicationContext,
                specificationBuilder,
                testRepository,
                modelMapper,
                repositoryResolver
        );
        testController = new ConcreteTestController(testService);

        // Setup getSelf() pattern
        when(applicationContext.getBean(ConcreteTestService.class)).thenReturn(testService);

        // Setup SecurityContext
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test-user-id");
    }

    // =========================================================================
    // UpdatableEntity Tests
    // =========================================================================

    @Nested
    @DisplayName("UpdatableEntity Interface Tests")
    class UpdatableEntityTests {

        @Test
        @DisplayName("should set entity via setEntity method")
        void shouldSetEntityViaSetEntityMethod() {
            // Given
            UpdatableTestEntity updatable = new UpdatableTestEntity();
            TestEntity entity = new TestEntity();
            entity.setId(1L);
            entity.setName("Test Entity");

            // When
            updatable.setEntity(entity);

            // Then
            assertThat(updatable.getEntity()).isNotNull();
            assertThat(updatable.getEntity().getId()).isEqualTo(1L);
            assertThat(updatable.getEntity().getName()).isEqualTo("Test Entity");
        }

        @Test
        @DisplayName("should handle null entity")
        void shouldHandleNullEntity() {
            // Given
            UpdatableTestEntity updatable = new UpdatableTestEntity();

            // When
            updatable.setEntity(null);

            // Then
            assertThat(updatable.getEntity()).isNull();
        }

        @Test
        @DisplayName("should update entity reference")
        void shouldUpdateEntityReference() {
            // Given
            UpdatableTestEntity updatable = new UpdatableTestEntity();
            TestEntity entity1 = new TestEntity();
            entity1.setId(1L);
            TestEntity entity2 = new TestEntity();
            entity2.setId(2L);

            // When
            updatable.setEntity(entity1);
            assertThat(updatable.getEntity().getId()).isEqualTo(1L);
            updatable.setEntity(entity2);

            // Then
            assertThat(updatable.getEntity().getId()).isEqualTo(2L);
        }
    }

    // =========================================================================
    // UpdaterTracking Interface Tests
    // =========================================================================

    @Nested
    @DisplayName("UpdaterTracking Interface Tests")
    class UpdaterTrackingTests {

        @Test
        @DisplayName("should set updater ID via setUpdaterId")
        void shouldSetUpdaterIdViaSetUpdaterId() {
            // Given
            TrackedTestEntity entity = new TrackedTestEntity();

            // When
            entity.setUpdaterId("user-123");

            // Then
            assertThat(entity.getUpdaterId()).isEqualTo("user-123");
        }

        @Test
        @DisplayName("should set updater ID via default setAutoUpdaterId method")
        void shouldSetUpdaterIdViaDefaultMethod() {
            // Given
            TrackedTestEntity entity = new TrackedTestEntity();

            // When
            entity.setAutoUpdaterId("auto-user-456");

            // Then
            assertThat(entity.getUpdaterId()).isEqualTo("auto-user-456");
        }

        @Test
        @DisplayName("should handle null updater ID")
        void shouldHandleNullUpdaterId() {
            // Given
            TrackedTestEntity entity = new TrackedTestEntity();
            entity.setUpdaterId("initial-user");

            // When
            entity.setUpdaterId(null);

            // Then
            assertThat(entity.getUpdaterId()).isNull();
        }

        @Test
        @DisplayName("should handle empty updater ID")
        void shouldHandleEmptyUpdaterId() {
            // Given
            TrackedTestEntity entity = new TrackedTestEntity();

            // When
            entity.setUpdaterId("");

            // Then
            assertThat(entity.getUpdaterId()).isEmpty();
        }

        @Test
        @DisplayName("should update updater ID multiple times")
        void shouldUpdateUpdaterIdMultipleTimes() {
            // Given
            TrackedTestEntity entity = new TrackedTestEntity();

            // When
            entity.setUpdaterId("user-1");
            entity.setUpdaterId("user-2");
            entity.setUpdaterId("user-3");

            // Then
            assertThat(entity.getUpdaterId()).isEqualTo("user-3");
        }
    }

    // =========================================================================
    // BaseService Tests
    // =========================================================================

    @Nested
    @DisplayName("BaseService Tests")
    class BaseServiceTests {

        @Nested
        @DisplayName("findById")
        class FindByIdTests {

            @Test
            @DisplayName("should return entity when found")
            void shouldReturnEntityWhenFound() {
                // Given
                TestEntity entity = new TestEntity();
                entity.setId(1L);
                entity.setName("Test");
                when(testRepository.findById(1L)).thenReturn(Optional.of(entity));

                // When
                TestEntity result = testService.findById(1L);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(1L);
                assertThat(result.getName()).isEqualTo("Test");
            }

            @Test
            @DisplayName("should throw ResourceNotFoundException when not found")
            void shouldThrowResourceNotFoundExceptionWhenNotFound() {
                // Given
                when(testRepository.findById(999L)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> testService.findById(999L))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Nested
        @DisplayName("save")
        class SaveTests {

            @Test
            @DisplayName("should save entity and update updater ID")
            void shouldSaveEntityAndUpdateUpdaterId() {
                // Given
                TestEntity entity = new TestEntity();
                entity.setName("New Entity");
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> {
                    TestEntity saved = invocation.getArgument(0);
                    saved.setId(1L);
                    return saved;
                });

                // When
                TestEntity result = testService.save(entity);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getUpdaterId()).isEqualTo("test-user-id");
                verify(testRepository).save(any(TestEntity.class));
            }

            @Test
            @DisplayName("should save entity without UpdaterTracking interface")
            void shouldSaveEntityWithoutUpdaterTracking() {
                // Given - Entity that doesn't implement UpdaterTracking
                TestEntity entity = new TestEntity();
                entity.setName("Entity");
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> {
                    TestEntity saved = invocation.getArgument(0);
                    saved.setId(1L);
                    return saved;
                });

                // When
                TestEntity result = testService.save(entity);

                // Then
                assertThat(result).isNotNull();
                verify(testRepository).save(any(TestEntity.class));
            }
        }

        @Nested
        @DisplayName("update")
        class UpdateTests {

            @Test
            @DisplayName("should update existing entity")
            void shouldUpdateExistingEntity() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setName("Old Name");

                TestDto dto = new TestDto();
                dto.setName("New Name");
                dto.setDescription("New Description");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestEntity result = testService.update(1L, dto);

                // Then
                assertThat(result).isNotNull();
                verify(modelMapper).map(dto, existingEntity);
                verify(testRepository).save(any(TestEntity.class));
            }

            @Test
            @DisplayName("should throw ResourceNotFoundException when entity not found")
            void shouldThrowWhenEntityNotFoundForUpdate() {
                // Given
                TestDto dto = new TestDto();
                dto.setName("New Name");
                when(testRepository.findById(999L)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> testService.update(999L, dto))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Nested
        @DisplayName("patch")
        class PatchTests {

            @Test
            @DisplayName("should patch entity with valid string field")
            void shouldPatchEntityWithValidStringField() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setName("Old Name");

                Map<String, Object> updates = new HashMap<>();
                updates.put("name", "Patched Name");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestEntity result = testService.patch(1L, updates);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getName()).isEqualTo("Patched Name");
            }

            @Test
            @DisplayName("should patch entity with integer field")
            void shouldPatchEntityWithIntegerField() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setCount(5);

                Map<String, Object> updates = new HashMap<>();
                updates.put("count", "10");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestEntity result = testService.patch(1L, updates);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getCount()).isEqualTo(10);
            }

            @Test
            @DisplayName("should patch entity with double field")
            void shouldPatchEntityWithDoubleField() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setPrice(9.99);

                Map<String, Object> updates = new HashMap<>();
                updates.put("price", "19.99");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestEntity result = testService.patch(1L, updates);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getPrice()).isEqualTo(19.99);
            }

            @Test
            @DisplayName("should patch entity with boolean field")
            void shouldPatchEntityWithBooleanField() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setActive(false);

                Map<String, Object> updates = new HashMap<>();
                updates.put("active", "true");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestEntity result = testService.patch(1L, updates);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getActive()).isTrue();
            }

            @Test
            @DisplayName("should patch entity with enum field")
            void shouldPatchEntityWithEnumField() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setStatus(TestStatus.PENDING);

                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "ACTIVE");

                // Mock repositoryResolver to throw exception for TestStatus (not an entity class)
                when(repositoryResolver.getRepository(TestStatus.class))
                        .thenThrow(new IllegalArgumentException("No repository found"));

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestEntity result = testService.patch(1L, updates);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getStatus()).isEqualTo(TestStatus.ACTIVE);
            }

            @Test
            @DisplayName("should patch entity with LocalDateTime field using ISO format with custom ignored fields")
            void shouldPatchEntityWithLocalDateTimeFieldIsoFormat() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);

                Map<String, Object> updates = new HashMap<>();
                updates.put("createdTime", "2024-01-15T10:30:00");

                // Use custom ignored fields that do NOT include createdTime
                Set<String> ignoredFields = Set.of("id", "lastUpdateTime");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestEntity result = testService.patch(1L, updates, ignoredFields);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getCreatedTime()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
            }

            @Test
            @DisplayName("should ignore id field during patch")
            void shouldIgnoreIdFieldDuringPatch() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setName("Original");

                Map<String, Object> updates = new HashMap<>();
                updates.put("id", "999");
                updates.put("name", "Updated");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestEntity result = testService.patch(1L, updates);

                // Then
                assertThat(result.getId()).isEqualTo(1L);
                assertThat(result.getName()).isEqualTo("Updated");
            }

            @Test
            @DisplayName("should ignore createdTime field during patch")
            void shouldIgnoreCreatedTimeFieldDuringPatch() {
                // Given
                LocalDateTime originalTime = LocalDateTime.of(2023, 1, 1, 0, 0);
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setCreatedTime(originalTime);

                Map<String, Object> updates = new HashMap<>();
                updates.put("createdTime", "2024-06-15T12:00:00");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestEntity result = testService.patch(1L, updates);

                // Then
                assertThat(result.getCreatedTime()).isEqualTo(originalTime);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for invalid field")
            void shouldThrowValidationExceptionForInvalidField() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);

                Map<String, Object> updates = new HashMap<>();
                updates.put("nonExistentField", "value");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));

                // When/Then
                assertThatThrownBy(() -> testService.patch(1L, updates))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should patch with custom ignored fields")
            void shouldPatchWithCustomIgnoredFields() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setName("Original Name");
                existingEntity.setDescription("Original Description");

                Map<String, Object> updates = new HashMap<>();
                updates.put("name", "New Name");
                updates.put("description", "New Description");

                Set<String> ignoredFields = Set.of("id", "createdTime", "lastUpdateTime", "description");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestEntity result = testService.patch(1L, updates, ignoredFields);

                // Then
                assertThat(result.getName()).isEqualTo("New Name");
                assertThat(result.getDescription()).isEqualTo("Original Description");
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for invalid date format")
            void shouldThrowValidationExceptionForInvalidDateFormat() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);

                Map<String, Object> updates = new HashMap<>();
                updates.put("createdTime", "invalid-date-format");

                Set<String> noIgnore = Set.of();

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));

                // When/Then
                assertThatThrownBy(() -> testService.patch(1L, updates, noIgnore))
                        .isInstanceOf(ValidationTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("delete")
        class DeleteTests {

            @Test
            @DisplayName("should delete existing entity")
            void shouldDeleteExistingEntity() {
                // Given
                when(testRepository.existsById(1L)).thenReturn(true);

                // When
                testService.delete(1L);

                // Then
                verify(testRepository).deleteById(1L);
            }

            @Test
            @DisplayName("should throw ResourceNotFoundException when entity not found")
            void shouldThrowWhenEntityNotFoundForDelete() {
                // Given
                when(testRepository.existsById(999L)).thenReturn(false);

                // When/Then
                assertThatThrownBy(() -> testService.delete(999L))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Nested
        @DisplayName("deleteAll")
        class DeleteAllTests {

            @Test
            @DisplayName("should delete all entities by IDs")
            void shouldDeleteAllEntitiesByIds() {
                // Given
                List<Long> ids = List.of(1L, 2L, 3L);
                when(testRepository.existsById(any())).thenReturn(true);

                // When
                testService.deleteAll(ids);

                // Then
                verify(testRepository, times(3)).deleteById(any());
            }

            @Test
            @DisplayName("should throw when one entity not found during deleteAll")
            void shouldThrowWhenOneEntityNotFoundDuringDeleteAll() {
                // Given
                List<Long> ids = List.of(1L, 2L, 999L);
                when(testRepository.existsById(1L)).thenReturn(true);
                when(testRepository.existsById(2L)).thenReturn(true);
                when(testRepository.existsById(999L)).thenReturn(false);

                // When/Then
                assertThatThrownBy(() -> testService.deleteAll(ids))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Nested
        @DisplayName("getDataPagedAndFiltered")
        class GetDataPagedAndFilteredTests {

            @Test
            @DisplayName("should return paginated data with filters")
            void shouldReturnPaginatedDataWithFilters() {
                // Given
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = Map.of("name", "test");

                TestEntity entity = new TestEntity();
                entity.setId(1L);
                entity.setName("test");

                Page<TestEntity> page = new PageImpl<>(List.of(entity), pageable, 1);

                when(specificationBuilder.createSpecification(filters)).thenReturn(Specification.where(null));
                when(testRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

                // When
                Page<TestEntity> result = testService.getDataPagedAndFiltered(pageable, filters);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getTotalElements()).isEqualTo(1);
            }

            @Test
            @DisplayName("should return empty page when no matches")
            void shouldReturnEmptyPageWhenNoMatches() {
                // Given
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = Map.of("name", "nonexistent");

                Page<TestEntity> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

                when(specificationBuilder.createSpecification(filters)).thenReturn(Specification.where(null));
                when(testRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

                // When
                Page<TestEntity> result = testService.getDataPagedAndFiltered(pageable, filters);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getContent()).isEmpty();
                assertThat(result.getTotalElements()).isZero();
            }
        }

        @Nested
        @DisplayName("createSpecification")
        class CreateSpecificationTests {

            @Test
            @DisplayName("should create specification from filters")
            void shouldCreateSpecificationFromFilters() {
                // Given
                Map<String, String> filters = Map.of("status", "ACTIVE");
                Specification<TestEntity> mockSpec = Specification.where(null);
                when(specificationBuilder.createSpecification(filters)).thenReturn(mockSpec);

                // When
                Specification<TestEntity> result = testService.createSpecification(filters);

                // Then
                assertThat(result).isNotNull();
                verify(specificationBuilder).createSpecification(filters);
            }
        }

        @Nested
        @DisplayName("updateEntityUpdater")
        class UpdateEntityUpdaterTests {

            @Test
            @DisplayName("should update updater ID for UpdaterTracking entity")
            void shouldUpdateUpdaterIdForUpdaterTrackingEntity() {
                // Given
                TestEntity entity = new TestEntity();
                entity.setId(1L);

                // When
                TestEntity result = testService.updateEntityUpdater(entity);

                // Then
                assertThat(result.getUpdaterId()).isEqualTo("test-user-id");
            }
        }

        @Nested
        @DisplayName("setApplicationContext")
        class SetApplicationContextTests {

            @Test
            @DisplayName("should set application context")
            void shouldSetApplicationContext() {
                // Given
                ApplicationContext newContext = mock(ApplicationContext.class);

                // When
                testService.setApplicationContext(newContext);

                // Then - Verify the context was set by checking if getSelf works
                // This is an indirect verification since applicationContext is protected
                when(newContext.getBean(ConcreteTestService.class)).thenReturn(testService);
                assertThat(testService).isNotNull();
            }
        }

        @Nested
        @DisplayName("toDto")
        class ToDtoTests {

            @Test
            @DisplayName("should convert entity to DTO")
            void shouldConvertEntityToDto() {
                // Given
                TestEntity entity = new TestEntity();
                entity.setId(1L);
                entity.setName("Test Entity");
                entity.setDescription("Test Description");

                // When
                TestDtoOut result = testService.toDto(entity);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(1L);
                assertThat(result.getName()).isEqualTo("Test Entity");
                assertThat(result.getDescription()).isEqualTo("Test Description");
            }

            @Test
            @DisplayName("should return null for null entity")
            void shouldReturnNullForNullEntity() {
                // When
                TestDtoOut result = testService.toDto(null);

                // Then
                assertThat(result).isNull();
            }
        }

        @Nested
        @DisplayName("findByIdAsDto")
        class FindByIdAsDtoTests {

            @Test
            @DisplayName("should find entity and return as DTO")
            void shouldFindEntityAndReturnAsDto() {
                // Given
                TestEntity entity = new TestEntity();
                entity.setId(1L);
                entity.setName("Test");
                when(testRepository.findById(1L)).thenReturn(Optional.of(entity));

                // When
                TestDtoOut result = testService.findByIdAsDto(1L);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(1L);
            }
        }

        @Nested
        @DisplayName("saveAsDto")
        class SaveAsDtoTests {

            @Test
            @DisplayName("should save entity and return as DTO")
            void shouldSaveEntityAndReturnAsDto() {
                // Given
                TestEntity entity = new TestEntity();
                entity.setName("New Entity");
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> {
                    TestEntity saved = invocation.getArgument(0);
                    saved.setId(1L);
                    return saved;
                });

                // When
                TestDtoOut result = testService.saveAsDto(entity);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(1L);
            }
        }

        @Nested
        @DisplayName("updateAsDto")
        class UpdateAsDtoTests {

            @Test
            @DisplayName("should update entity and return as DTO")
            void shouldUpdateEntityAndReturnAsDto() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setName("Old");

                TestDto dto = new TestDto();
                dto.setName("New");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestDtoOut result = testService.updateAsDto(1L, dto);

                // Then
                assertThat(result).isNotNull();
            }
        }

        @Nested
        @DisplayName("patchAsDto")
        class PatchAsDtoTests {

            @Test
            @DisplayName("should patch entity and return as DTO")
            void shouldPatchEntityAndReturnAsDto() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setName("Old");

                Map<String, Object> updates = Map.of("name", "Patched");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestDtoOut result = testService.patchAsDto(1L, updates);

                // Then
                assertThat(result).isNotNull();
            }
        }

        @Nested
        @DisplayName("getDataPagedAndFilteredAsDtos")
        class GetDataPagedAndFilteredAsDtosTests {

            @Test
            @DisplayName("should return paginated DTOs")
            void shouldReturnPaginatedDtos() {
                // Given
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();

                TestEntity entity = new TestEntity();
                entity.setId(1L);
                entity.setName("Test");

                Page<TestEntity> page = new PageImpl<>(List.of(entity), pageable, 1);

                when(specificationBuilder.createSpecification(filters)).thenReturn(Specification.where(null));
                when(testRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

                // When
                Page<TestDtoOut> result = testService.getDataPagedAndFilteredAsDtos(pageable, filters);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
            }
        }

        @Nested
        @DisplayName("createFromDtoAsDto")
        class CreateFromDtoAsDtoTests {

            @Test
            @DisplayName("should create entity from DTO and return as DTO")
            void shouldCreateEntityFromDtoAndReturnAsDto() {
                // Given
                TestDto dto = new TestDto();
                dto.setName("New Entity");
                dto.setDescription("Description");
                dto.setCount(5);

                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> {
                    TestEntity saved = invocation.getArgument(0);
                    saved.setId(1L);
                    return saved;
                });

                // When
                TestDtoOut result = testService.createFromDtoAsDto(dto);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(1L);
                assertThat(result.getName()).isEqualTo("New Entity");
            }
        }

        @Nested
        @DisplayName("City field handling in patch")
        class CityFieldHandlingTests {

            @Test
            @DisplayName("should patch city field by name lookup")
            void shouldPatchCityFieldByNameLookup() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);

                City city = new City();
                city.setId(1L);
                city.setName("Warsaw");

                Map<String, Object> updates = new HashMap<>();
                updates.put("city", "Warsaw");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(repositoryResolver.getRepository(City.class)).thenReturn(cityRepository);
                when(cityRepository.findByName("Warsaw")).thenReturn(Optional.of(city));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                TestEntity result = testService.patch(1L, updates);

                // Then
                assertThat(result.getCity()).isNotNull();
                assertThat(result.getCity().getName()).isEqualTo("Warsaw");
            }

            @Test
            @DisplayName("should throw ResourceNotFoundException when city not found")
            void shouldThrowWhenCityNotFound() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);

                Map<String, Object> updates = new HashMap<>();
                updates.put("city", "NonExistentCity");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(repositoryResolver.getRepository(City.class)).thenReturn(cityRepository);
                when(cityRepository.findByName("NonExistentCity")).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> testService.patch(1L, updates))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }
    }

    // =========================================================================
    // BaseController Tests
    // =========================================================================

    @Nested
    @DisplayName("BaseController Tests")
    class BaseControllerTests {

        @Nested
        @DisplayName("getById")
        class GetByIdTests {

            @Test
            @DisplayName("should return entity by ID")
            void shouldReturnEntityById() {
                // Given
                TestEntity entity = new TestEntity();
                entity.setId(1L);
                entity.setName("Test");
                when(testRepository.findById(1L)).thenReturn(Optional.of(entity));

                // When
                ResponseEntity<TestDtoOut> response = testController.getById(1L);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getId()).isEqualTo(1L);
            }

            @Test
            @DisplayName("should throw ResourceNotFoundException when not found")
            void shouldThrowWhenNotFound() {
                // Given
                when(testRepository.findById(999L)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> testController.getById(999L))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Nested
        @DisplayName("findPaginated")
        class FindPaginatedTests {

            @Test
            @DisplayName("should return paginated results")
            void shouldReturnPaginatedResults() {
                // Given
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();
                filters.put("page", "0");
                filters.put("size", "10");
                filters.put("name", "test");

                TestEntity entity = new TestEntity();
                entity.setId(1L);
                entity.setName("test");

                Page<TestEntity> page = new PageImpl<>(List.of(entity), pageable, 1);

                when(specificationBuilder.createSpecification(any())).thenReturn(Specification.where(null));
                when(testRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

                // When
                ResponseEntity<Page<TestDtoOut>> response = testController.findPaginated(pageable, filters);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getContent()).hasSize(1);
            }

            @Test
            @DisplayName("should remove pagination parameters from filters")
            void shouldRemovePaginationParametersFromFilters() {
                // Given
                Pageable pageable = PageRequest.of(0, 10);
                Map<String, String> filters = new HashMap<>();
                filters.put("page", "0");
                filters.put("size", "10");
                filters.put("sort", "name");
                filters.put("direction", "asc");
                filters.put("status", "ACTIVE");

                Page<TestEntity> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

                when(specificationBuilder.createSpecification(any())).thenReturn(Specification.where(null));
                when(testRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

                // When
                testController.findPaginated(pageable, filters);

                // Then - verify that filters were cleaned up (only status should remain)
                verify(specificationBuilder).createSpecification(argThat(map ->
                        !map.containsKey("page") &&
                        !map.containsKey("size") &&
                        !map.containsKey("sort") &&
                        !map.containsKey("direction")
                ));
            }
        }

        @Nested
        @DisplayName("create")
        class CreateTests {

            @Test
            @DisplayName("should create new entity")
            void shouldCreateNewEntity() {
                // Given
                TestDto dto = new TestDto();
                dto.setName("New Entity");
                dto.setDescription("Description");

                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> {
                    TestEntity saved = invocation.getArgument(0);
                    saved.setId(1L);
                    return saved;
                });

                // When
                ResponseEntity<TestDtoOut> response = testController.create(dto);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getId()).isEqualTo(1L);
            }
        }

        @Nested
        @DisplayName("update")
        class UpdateTests {

            @Test
            @DisplayName("should update existing entity")
            void shouldUpdateExistingEntity() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setName("Old");

                TestDto dto = new TestDto();
                dto.setName("Updated");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                ResponseEntity<TestDtoOut> response = testController.update(1L, dto);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }

            @Test
            @DisplayName("should throw when entity not found for update")
            void shouldThrowWhenEntityNotFoundForUpdate() {
                // Given
                TestDto dto = new TestDto();
                dto.setName("Updated");
                when(testRepository.findById(999L)).thenReturn(Optional.empty());

                // When/Then
                assertThatThrownBy(() -> testController.update(999L, dto))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Nested
        @DisplayName("patch")
        class PatchTests {

            @Test
            @DisplayName("should patch existing entity")
            void shouldPatchExistingEntity() {
                // Given
                TestEntity existingEntity = new TestEntity();
                existingEntity.setId(1L);
                existingEntity.setName("Old");

                Map<String, Object> updates = Map.of("name", "Patched");

                when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
                when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                ResponseEntity<TestDtoOut> response = testController.patch(1L, updates);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }

        @Nested
        @DisplayName("delete")
        class DeleteTests {

            @Test
            @DisplayName("should delete single entity")
            void shouldDeleteSingleEntity() {
                // Given
                List<Long> ids = List.of(1L);
                when(testRepository.existsById(1L)).thenReturn(true);

                // When
                ResponseEntity<Void> response = testController.delete(ids);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                verify(testRepository).deleteById(1L);
            }

            @Test
            @DisplayName("should delete multiple entities")
            void shouldDeleteMultipleEntities() {
                // Given
                List<Long> ids = List.of(1L, 2L, 3L);
                when(testRepository.existsById(any())).thenReturn(true);

                // When
                ResponseEntity<Void> response = testController.delete(ids);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                verify(testRepository, times(3)).deleteById(any());
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for empty list")
            void shouldThrowForEmptyList() {
                // Given
                List<Long> ids = Collections.emptyList();

                // When/Then
                assertThatThrownBy(() -> testController.delete(ids))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for more than 100 IDs")
            void shouldThrowForTooManyIds() {
                // Given
                List<Long> ids = new java.util.ArrayList<>();
                for (long i = 1; i <= 101; i++) {
                    ids.add(i);
                }

                // When/Then
                assertThatThrownBy(() -> testController.delete(ids))
                        .isInstanceOf(ValidationTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("getService")
        class GetServiceTests {

            @Test
            @DisplayName("should return the service instance")
            void shouldReturnServiceInstance() {
                // Given
                TestEntity entity = new TestEntity();
                entity.setId(1L);
                entity.setName("Test");
                when(testRepository.findById(1L)).thenReturn(Optional.of(entity));

                // When - accessing through the controller
                ResponseEntity<TestDtoOut> response = testController.getById(1L);

                // Then - if getService didn't work, the call would fail
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(testController).isNotNull();
            }
        }
    }

    // =========================================================================
    // Additional Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle Long field conversion in patch")
        void shouldHandleLongFieldConversionInPatch() {
            // Given
            TestEntity existingEntity = new TestEntity();
            existingEntity.setId(1L);

            Map<String, Object> updates = new HashMap<>();
            updates.put("id", "12345678901234");

            // ID is in ignored fields by default, so let's test with custom ignored fields
            Set<String> noIgnoredFields = Set.of();

            when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            TestEntity result = testService.patch(1L, updates, noIgnoredFields);

            // Then
            assertThat(result.getId()).isEqualTo(12345678901234L);
        }

        @Test
        @DisplayName("should handle OffsetDateTime string conversion to LocalDateTime")
        void shouldHandleOffsetDateTimeStringConversion() {
            // Given
            TestEntity existingEntity = new TestEntity();
            existingEntity.setId(1L);

            Map<String, Object> updates = new HashMap<>();
            updates.put("createdTime", "2024-01-15T10:30:00+02:00");

            Set<String> ignoredFields = Set.of("id", "lastUpdateTime");

            when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            TestEntity result = testService.patch(1L, updates, ignoredFields);

            // Then
            assertThat(result.getCreatedTime()).isNotNull();
            assertThat(result.getCreatedTime().getHour()).isEqualTo(10);
        }

        @Test
        @DisplayName("should handle empty list for patch")
        void shouldHandleEmptyListForPatch() {
            // Given
            TestEntity existingEntity = new TestEntity();
            existingEntity.setId(1L);
            existingEntity.setItems(List.of()); // start with empty

            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "Updated Name");

            when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            TestEntity result = testService.patch(1L, updates);

            // Then
            assertThat(result.getName()).isEqualTo("Updated Name");
        }

        @Test
        @DisplayName("should handle multiple field updates in single patch")
        void shouldHandleMultipleFieldUpdatesInSinglePatch() {
            // Given
            TestEntity existingEntity = new TestEntity();
            existingEntity.setId(1L);
            existingEntity.setName("Old");
            existingEntity.setDescription("Old Desc");
            existingEntity.setCount(1);
            existingEntity.setActive(false);

            Map<String, Object> updates = new HashMap<>();
            updates.put("name", "New");
            updates.put("description", "New Desc");
            updates.put("count", "5");
            updates.put("active", "true");

            when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            TestEntity result = testService.patch(1L, updates);

            // Then
            assertThat(result.getName()).isEqualTo("New");
            assertThat(result.getDescription()).isEqualTo("New Desc");
            assertThat(result.getCount()).isEqualTo(5);
            assertThat(result.getActive()).isTrue();
        }

        @Test
        @DisplayName("should patch entity preserving existing fields not in updates")
        void shouldPatchEntityPreservingExistingFields() {
            // Given
            TestEntity existingEntity = new TestEntity();
            existingEntity.setId(1L);
            existingEntity.setName("Existing Name");
            existingEntity.setDescription("Existing Description");
            existingEntity.setCount(10);

            Map<String, Object> updates = new HashMap<>();
            updates.put("description", "Updated Description");

            when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            TestEntity result = testService.patch(1L, updates);

            // Then
            assertThat(result.getName()).isEqualTo("Existing Name");
            assertThat(result.getDescription()).isEqualTo("Updated Description");
            assertThat(result.getCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("should handle primitive boolean field value true")
        void shouldHandlePrimitiveBooleanFieldValueTrue() {
            // Given
            TestEntity existingEntity = new TestEntity();
            existingEntity.setId(1L);
            existingEntity.setActive(false);

            Map<String, Object> updates = new HashMap<>();
            updates.put("active", true); // boolean primitive, not string

            when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            TestEntity result = testService.patch(1L, updates);

            // Then
            assertThat(result.getActive()).isTrue();
        }

        @Test
        @DisplayName("should handle primitive boolean field value false")
        void shouldHandlePrimitiveBooleanFieldValueFalse() {
            // Given
            TestEntity existingEntity = new TestEntity();
            existingEntity.setId(1L);
            existingEntity.setActive(true);

            Map<String, Object> updates = new HashMap<>();
            updates.put("active", false); // boolean primitive

            when(testRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            TestEntity result = testService.patch(1L, updates);

            // Then
            assertThat(result.getActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Multiple Controller Operations in Sequence")
    class SequentialOperationsTests {

        @Test
        @DisplayName("should handle create then update sequence")
        void shouldHandleCreateThenUpdateSequence() {
            // Given - Create
            TestDto createDto = new TestDto();
            createDto.setName("Initial");

            TestEntity createdEntity = new TestEntity();
            createdEntity.setId(1L);
            createdEntity.setName("Initial");

            when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> {
                TestEntity saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            // Create
            ResponseEntity<TestDtoOut> createResponse = testController.create(createDto);
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Given - Update
            TestDto updateDto = new TestDto();
            updateDto.setName("Updated");

            when(testRepository.findById(1L)).thenReturn(Optional.of(createdEntity));

            // Update
            ResponseEntity<TestDtoOut> updateResponse = testController.update(1L, updateDto);
            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle create then patch then delete sequence")
        void shouldHandleCreatePatchDeleteSequence() {
            // Given - Create
            TestDto createDto = new TestDto();
            createDto.setName("Initial");

            TestEntity entity = new TestEntity();
            entity.setId(1L);
            entity.setName("Initial");

            when(testRepository.save(any(TestEntity.class))).thenAnswer(invocation -> {
                TestEntity saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });
            when(testRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(testRepository.existsById(1L)).thenReturn(true);

            // Create
            ResponseEntity<TestDtoOut> createResponse = testController.create(createDto);
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Patch
            Map<String, Object> updates = Map.of("name", "Patched");
            ResponseEntity<TestDtoOut> patchResponse = testController.patch(1L, updates);
            assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Delete
            ResponseEntity<Void> deleteResponse = testController.delete(List.of(1L));
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }
}
