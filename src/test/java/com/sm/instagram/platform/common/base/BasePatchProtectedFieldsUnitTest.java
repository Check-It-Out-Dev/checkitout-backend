package com.sm.instagram.platform.common.base;

import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The reflective base patch assigns whatever field names the request body
 * carries, so identity, audit, optimistic-lock, soft-delete and session
 * columns must be denied up-front — otherwise an authenticated caller could
 * corrupt {@code version}, un-hide via {@code deletedAt}, forge
 * {@code updaterId} or bump {@code tokenVersion}. Locks
 * {@link BaseService#ALWAYS_IGNORED_PATCH_FIELDS} and the skip behaviour
 * against regression. In the same package as {@link BaseService} so the
 * protected constant is visible.
 */
@DisplayName("BaseService.patch — protected fields are always ignored")
class BasePatchProtectedFieldsUnitTest {

    /** Minimal entity: one business field plus the framework columns. */
    static class TestEntity {
        Long id;
        String name;
        Long version;
        LocalDateTime deletedAt;
    }

    /** Concrete subclass so the reflective patch runs; abstracts unused here. */
    static class TestService extends BaseService<TestEntity, Long, Object> {
        TestService(ApplicationContext ctx, SpecificationBuilder<TestEntity> sb,
                    BaseRepository<TestEntity, Long> repo, ModelMapper mm, RepositoryResolver rr) {
            super(ctx, sb, repo, mm, rr);
        }

        @Override
        public <O> Page<O> getDataPagedAndFilteredAsDtos(Pageable pageable, Map<String, String> filters) {
            return null;
        }

        @Override
        public <O> O toDto(TestEntity entity) {
            return null;
        }

        @Override
        public <O> O createFromDtoAsDto(Object dto) {
            return null;
        }
    }

    private TestService service;
    private TestEntity entity;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        BaseRepository<TestEntity, Long> repository = mock(BaseRepository.class);
        service = new TestService(ctx, mock(SpecificationBuilder.class), repository,
                mock(ModelMapper.class), mock(RepositoryResolver.class));

        doReturn(service).when(ctx).getBean(any(Class.class)); // getSelf()
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        entity = new TestEntity();
        entity.id = 1L;
        entity.name = "original";
        entity.version = 7L;
        entity.deletedAt = null;
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
    }

    @Test
    @DisplayName("applies a business field but silently ignores id / version / deletedAt")
    void ignoresProtectedFields() {
        var updates = new LinkedHashMap<String, Object>();
        updates.put("name", "changed");
        updates.put("version", 999);
        updates.put("id", 424242);
        updates.put("deletedAt", "2020-01-01T00:00:00");

        service.patch(1L, updates);

        assertThat(entity.name).isEqualTo("changed");  // business field applied
        assertThat(entity.version).isEqualTo(7L);      // optimistic lock untouched
        assertThat(entity.id).isEqualTo(1L);           // identity untouched
        assertThat(entity.deletedAt).isNull();         // soft-delete untouched
    }

    @Test
    @DisplayName("a caller-supplied ignore set cannot re-expose a protected field")
    void callerCannotReExposeProtectedField() {
        var updates = new LinkedHashMap<String, Object>();
        updates.put("name", "changed");
        updates.put("version", 999);

        // Deliberately pass an ignore set that OMITS version — it must still be
        // protected because the ALWAYS set is unioned in.
        service.patch(1L, updates, java.util.Set.of("createdTime"));

        assertThat(entity.name).isEqualTo("changed");
        assertThat(entity.version).isEqualTo(7L);
    }

    @Test
    @DisplayName("the protected set covers identity, audit, lock, soft-delete and session fields")
    void protectedSetIsComplete() {
        assertThat(BaseService.ALWAYS_IGNORED_PATCH_FIELDS)
                .contains("id", "version", "createdTime", "lastUpdateTime",
                        "deletedAt", "updaterId", "tokenVersion");
    }
}
