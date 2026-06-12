package com.sm.instagram.platform.consent;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConsentVersionRepository extends BaseRepository<ConsentVersion, Long> {

    List<ConsentVersion> findByConsentDefinitionIdOrderByEffectiveFromDesc(Long consentDefinitionId);

    @Query("SELECT cv FROM ConsentVersion cv WHERE cv.consentDefinition.id = :definitionId " +
            "AND cv.effectiveFrom <= :currentTime " +
            "AND (cv.effectiveUntil IS NULL OR cv.effectiveUntil > :currentTime) " +
            "ORDER BY cv.effectiveFrom DESC")
    Optional<ConsentVersion> findCurrentVersionByDefinitionId(@Param("definitionId") Long definitionId,
                                                              @Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT cv FROM ConsentVersion cv WHERE cv.consentDefinition.consentType = :consentType " +
            "AND cv.effectiveFrom <= :currentTime " +
            "AND (cv.effectiveUntil IS NULL OR cv.effectiveUntil > :currentTime) " +
            "ORDER BY cv.effectiveFrom DESC")
    Optional<ConsentVersion> findCurrentVersionByConsentType(@Param("consentType") String consentType,
                                                             @Param("currentTime") LocalDateTime currentTime);
}
