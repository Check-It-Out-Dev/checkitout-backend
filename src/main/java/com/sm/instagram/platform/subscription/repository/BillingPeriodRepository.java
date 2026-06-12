package com.sm.instagram.platform.subscription.repository;

import com.sm.instagram.platform.subscription.entity.BillingPeriod;
import com.sm.instagram.platform.subscription.entity.BillingPeriodStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BillingPeriodRepository extends JpaRepository<BillingPeriod, Long> {

    @Query("SELECT bp FROM BillingPeriod bp WHERE bp.user.id = :userId AND bp.status = 'ACTIVE'")
    Optional<BillingPeriod> findActiveByUserId(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bp FROM BillingPeriod bp WHERE bp.user.id = :userId AND bp.status = 'ACTIVE'")
    Optional<BillingPeriod> findActiveByUserIdForUpdate(@Param("userId") Long userId);

    @Query("SELECT bp FROM BillingPeriod bp " +
           "WHERE bp.status = 'PENDING_DOWNGRADE' AND bp.endDate <= :now")
    List<BillingPeriod> findExpiredPendingDowngrades(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(po) FROM PartnershipOpportunity po " +
           "WHERE po.company.id = :userId AND po.createdTime BETWEEN :start AND :end")
    long countCampaignsInPeriod(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
