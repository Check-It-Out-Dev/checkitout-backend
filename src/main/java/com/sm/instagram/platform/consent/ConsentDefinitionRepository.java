package com.sm.instagram.platform.consent;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsentDefinitionRepository extends BaseRepository<ConsentDefinition, Long> {

    Optional<ConsentDefinition> findByConsentType(String consentType);

    List<ConsentDefinition> findByIsActiveTrueOrderByConsentType();

    List<ConsentDefinition> findByIsActiveTrueOrderByDisplayOrderAsc();

    boolean existsByConsentType(String consentType);
}
