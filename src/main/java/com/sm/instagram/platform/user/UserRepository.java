package com.sm.instagram.platform.user;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends BaseRepository<User, Long>, UserRepositoryCustom {
    Optional<User> findByFirebaseUserId(String firebaseUserId);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    /**
     * Counts users by user type excluding those with specified account status.
     */
    long countByUserTypeAndAccountStatusNot(UserType userType, AccountStatus excludeStatus);

    /**
     * Find all users with the given user type and active account status.
     * Used for sending admin in-app notifications.
     */
    List<User> findByUserTypeAndAccountStatus(UserType userType, AccountStatus accountStatus);

    /**
     * Fetches paginated users without eagerly loading collections.
     * Collections should be loaded separately when needed to avoid MultipleBagFetchException.
     *
     * @param spec     The specification for filtering
     * @param pageable The pagination information
     * @return Page of users
     */
    Page<User> findAll(Specification<User> spec, Pageable pageable);

    /**
     * Find users who have not accepted the newest consents and have an active-like status.
     * Used by the daily consent enforcement cron job.
     */
    List<User> findByNewestConsentsAcceptedFalseAndAccountStatusIn(List<AccountStatus> statuses);

    /**
     * Reset newestConsentsAccepted flag for all users who currently have it set to true.
     * Used by E2E tests to simulate "new terms published" scenario.
     */
    @Modifying
    @Query("UPDATE User u SET u.newestConsentsAccepted = false WHERE u.newestConsentsAccepted = true")
    int resetAllConsentsAccepted();

    /**
     * Find users with ZERO consent records who were created before the given cutoff date.
     * These are accounts that were created without proper consent (GDPR compliance gap).
     * Used by the weekly no-consent account cleanup cron job.
     */
    @Query("SELECT u FROM User u WHERE u.createdTime < :cutoff " +
           "AND u.accountStatus NOT IN (com.sm.instagram.platform.user.AccountStatus.TO_BE_DELETED, " +
           "com.sm.instagram.platform.user.AccountStatus.DELETED) " +
           "AND NOT EXISTS (SELECT 1 FROM ConsentRecord cr WHERE cr.user = u)")
    List<User> findUsersWithNoConsentRecordsBefore(@Param("cutoff") LocalDateTime cutoff);
}
