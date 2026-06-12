package com.sm.instagram.platform.subscription.repository;

import com.sm.instagram.platform.subscription.entity.InvoiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InvoiceRecordRepository extends JpaRepository<InvoiceRecord, Long> {

    @Query("SELECT ir FROM InvoiceRecord ir " +
           "WHERE ir.status IN ('PENDING', 'FAILED') " +
           "AND ir.retryCount < ir.maxRetries " +
           "AND (ir.lastAttemptAt IS NULL OR ir.lastAttemptAt < :cutoff) " +
           "ORDER BY ir.createdTime ASC")
    List<InvoiceRecord> findRetryable(@Param("cutoff") LocalDateTime cutoff);

    List<InvoiceRecord> findByUserIdOrderByCreatedTimeDesc(Long userId);
}
