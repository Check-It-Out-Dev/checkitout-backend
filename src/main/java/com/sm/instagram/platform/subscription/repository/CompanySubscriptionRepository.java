package com.sm.instagram.platform.subscription.repository;

import com.sm.instagram.platform.subscription.entity.CompanySubscription;
import com.sm.instagram.platform.subscription.entity.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, Long> {

    Optional<CompanySubscription> findByUserId(Long userId);

    @Query("SELECT cs FROM CompanySubscription cs WHERE cs.status = :status")
    List<CompanySubscription> findAllByStatus(@Param("status") SubscriptionStatus status);

    @Query("SELECT cs FROM CompanySubscription cs " +
           "WHERE cs.status = 'TRIAL_ENTERPRISE' AND cs.trialEndDate <= :now")
    List<CompanySubscription> findExpiredTrials(@Param("now") LocalDateTime now);

    @Query("SELECT cs FROM CompanySubscription cs " +
           "WHERE cs.status = 'TERMS_PENDING' AND cs.graceDeadline <= :now AND cs.newestTermsAccepted = false")
    List<CompanySubscription> findExpiredGracePeriods(@Param("now") LocalDateTime now);

    @Query("SELECT cs FROM CompanySubscription cs " +
           "WHERE cs.status = 'TRIAL_ENTERPRISE' AND cs.trialEndDate BETWEEN :from AND :to")
    List<CompanySubscription> findTrialsEndingBetween(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT cs FROM CompanySubscription cs " +
           "WHERE cs.status = 'TERMS_PENDING' AND cs.previousState = 'TRIAL_ENTERPRISE' " +
           "AND cs.trialEndDate <= :now")
    List<CompanySubscription> findTrialsExpiredInTermsPending(@Param("now") LocalDateTime now);

    boolean existsByUserIdAndStripeCustomerIdIsNotNull(Long userId);

    /**
     * Count rows whose status is NOT the supplied value.
     * Used by the payments-disabled boot guard to assert no in-flight paid users
     * remain when {@code app.payments.enabled = false}.
     */
    long countByStatusNot(SubscriptionStatus status);

    /**
     * Count rows whose status is one of the supplied set. Used by the payments-disabled boot
     * guard to flag ONLY stranded in-flight PAID subscriptions, so terminal statuses
     * (ACCOUNT_DEACTIVATED from GDPR deletion, SUSPENDED_LEGAL) — which arise from always-on
     * flows unrelated to the payments toggle — cannot block application startup.
     */
    long countByStatusIn(java.util.Collection<SubscriptionStatus> statuses);

    Optional<CompanySubscription> findByStripeCustomerId(String stripeCustomerId);

    Optional<CompanySubscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    /** Pessimistic lock for webhook processing — prevents concurrent mutations to the same subscription */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM CompanySubscription cs WHERE cs.stripeSubscriptionId = :stripeSubscriptionId")
    Optional<CompanySubscription> findByStripeSubscriptionIdForUpdate(@Param("stripeSubscriptionId") String stripeSubscriptionId);

    /** Pessimistic lock for webhook processing — by Stripe customer ID */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM CompanySubscription cs WHERE cs.stripeCustomerId = :stripeCustomerId")
    Optional<CompanySubscription> findByStripeCustomerIdForUpdate(@Param("stripeCustomerId") String stripeCustomerId);
}
