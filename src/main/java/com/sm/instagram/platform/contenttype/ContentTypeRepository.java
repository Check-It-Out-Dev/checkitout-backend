package com.sm.instagram.platform.contenttype;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link ContentType} entities.
 *
 * <p>No custom queries are declared here — all CRUD and paging operations are
 * inherited from {@link BaseRepository}. The interface exists as a typed
 * extension point so future content-type-specific finders can be added without
 * touching call sites.
 */
@Repository
public interface ContentTypeRepository extends BaseRepository<ContentType, Long> {
}
