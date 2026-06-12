package com.sm.instagram.platform.platform;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Platform} entities.
 *
 * <p>Custom finders are kept narrow on purpose — {@link #findByName(String)} is
 * used by E2E test setup and admin tooling to look up the canonical Instagram
 * row, while {@link #findByActiveTrue()} backs the public catalogue endpoint.
 * All other CRUD comes from {@link BaseRepository}.
 */
@Repository
public interface PlatformRepository extends BaseRepository<Platform, Long> {

    /**
     * Look up a platform by its canonical name (e.g. {@code "Instagram"}).
     *
     * @param name exact-match platform name
     * @return the matching platform, or empty if none exists
     */
    Optional<Platform> findByName(String name);

    /**
     * Return every platform whose {@code active} flag is set, in unspecified
     * order. Inactive platforms are filtered out at the DB layer so callers
     * never have to re-check the flag.
     *
     * @return list of currently active platforms (possibly empty)
     */
    List<Platform> findByActiveTrue();
}
