package com.sm.instagram.platform.consent;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserConsentRepository extends BaseRepository<UserConsent, Long> {

    List<UserConsent> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserConsent> findByUserIdAndConsentVersionConsentDefinitionIdOrderByCreatedAtDesc(
            Long userId, Long consentDefinitionId);

    @Query("SELECT uc FROM UserConsent uc " +
            "WHERE uc.user.id = :userId " +
            "AND uc.consentVersion.consentDefinition.consentType = :consentType " +
            "ORDER BY uc.createdAt DESC")
    List<UserConsent> findByUserIdAndConsentType(@Param("userId") Long userId,
                                                 @Param("consentType") String consentType);

    @Query("SELECT uc FROM UserConsent uc " +
            "WHERE uc.user.id = :userId " +
            "AND uc.consentVersion.consentDefinition.id = :definitionId " +
            "ORDER BY uc.createdAt DESC")
    Optional<UserConsent> findLatestByUserIdAndDefinitionId(@Param("userId") Long userId,
                                                            @Param("definitionId") Long definitionId);

    @Query("SELECT uc FROM UserConsent uc " +
            "WHERE uc.user.id = :userId " +
            "AND uc.consentVersion.consentDefinition.consentType = :consentType " +
            "ORDER BY uc.createdAt DESC")
    Optional<UserConsent> findLatestByUserIdAndConsentType(@Param("userId") Long userId,
                                                           @Param("consentType") String consentType);
}
