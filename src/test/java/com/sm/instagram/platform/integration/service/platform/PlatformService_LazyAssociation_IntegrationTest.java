package com.sm.instagram.platform.integration.service.platform;

import com.sm.instagram.platform.contenttype.ContentType;
import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformDto;
import com.sm.instagram.platform.platform.PlatformRepository;
import com.sm.instagram.platform.platform.PlatformService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code contentTypes} is required by the contract, so it has to be in the response.
 *
 * <p>It was not, and no unit test could have seen why. {@code ModelMapperConfig} sets a property
 * condition that skips any source which is an uninitialized Hibernate {@code PersistentCollection}
 * -- deliberately, so a cold proxy cannot throw mid-mapping -- and the condition does not touch the
 * collection, which is the only thing that would have loaded it. A lazy {@code @ManyToMany} was
 * therefore dropped rather than resolved, the {@code NON_NULL} serializer omitted the resulting
 * null, and the property disappeared. It is {@code @NotNull}, so springdoc publishes it as
 * required, and every fuzz run reported {@code GET /platform/paged} returning a body its own
 * document called invalid.
 *
 * <p>The {@code entityManager.clear()} is the whole test. Without it the entity is still the one
 * this method created, with its collection already in memory, and the mapping succeeds for a reason
 * production never has.
 */
@DisplayName("PlatformService - lazy associations survive the mapping")
class PlatformService_LazyAssociation_IntegrationTest extends BaseServiceIntegrationTest {

    @Autowired
    private PlatformService platformService;

    @Autowired
    private PlatformRepository platformRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Platform persistPlatformWithContentType(String name) {
        ContentType contentType = new ContentType();
        contentType.setName("VIDEO_" + name);
        entityManager.persist(contentType);

        Platform platform = new Platform();
        platform.setName(name);
        platform.setActive(true);
        platform.setContentTypes(Set.of(contentType));
        return platformRepository.save(platform);
    }

    /** Load the row again as a request would: nothing of it left in the session. */
    private void detachEverything() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("the paged listing carries contentTypes for a row it did not just create")
    void pagedListingCarriesContentTypes() {
        String name = "LazyGram";
        persistPlatformWithContentType(name);
        detachEverything();

        Page<PlatformDto> page =
                platformService.getDataPagedAndFilteredAsDtos(PageRequest.of(0, 200), Map.of());

        PlatformDto dto = page.getContent().stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the platform this test created is not in the page"));
        assertThat(dto.getContentTypes())
                .as("contentTypes is @NotNull, so the document publishes it as required")
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    @DisplayName("a single read carries them too — it is the same mapping")
    void singleReadCarriesContentTypes() {
        Platform saved = persistPlatformWithContentType("LazyTok");
        detachEverything();

        PlatformDto dto = platformService.toDto(platformRepository.findById(saved.getId()).orElseThrow());

        assertThat(dto.getContentTypes()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("a platform with no content types maps to an empty set, not to an absent field")
    void emptyAssociationIsStillPresent() {
        Platform platform = new Platform();
        platform.setName("EmptyGram");
        platform.setActive(true);
        platformRepository.save(platform);
        detachEverything();

        PlatformDto dto = platformService.toDto(
                platformRepository.findById(platform.getId()).orElseThrow());

        assertThat(dto.getContentTypes())
                .as("an empty set serialises as [], a null is dropped by NON_NULL and breaks the schema")
                .isNotNull()
                .isEmpty();
    }
}
