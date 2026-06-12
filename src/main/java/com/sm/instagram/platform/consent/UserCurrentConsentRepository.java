package com.sm.instagram.platform.consent;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserCurrentConsentRepository extends BaseRepository<UserCurrentConsent, UserCurrentConsentId> {

    List<UserCurrentConsent> findByUserId(Long userId);

    Optional<UserCurrentConsent> findByUserIdAndConsentDefinitionId(Long userId, Long consentDefinitionId);

    @Query("SELECT ucc FROM UserCurrentConsent ucc " +
            "JOIN ucc.consentDefinition cd " +
            "WHERE ucc.userId = :userId AND cd.consentType = :consentType")
    Optional<UserCurrentConsent> findByUserIdAndConsentType(@Param("userId") Long userId,
                                                            @Param("consentType") String consentType);

    List<UserCurrentConsent> findByConsentDefinitionIdAndConsentGiven(Long consentDefinitionId, Boolean consentGiven);
}
