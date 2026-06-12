package com.sm.instagram.platform.legal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    /**
     * Find all consent records for a specific user.
     * Uses JOIN FETCH to eagerly load the document relationship,
     * avoiding LazyInitializationException when mapping to DTOs.
     */
    @Query("""
            SELECT cr FROM ConsentRecord cr
            JOIN FETCH cr.document
            LEFT JOIN FETCH cr.user
            WHERE cr.user.id = :userId
            ORDER BY cr.timestamp DESC
            """)
    List<ConsentRecord> findByUserIdOrderByTimestampDesc(@Param("userId") Long userId);

    /**
     * Find consent records for a user for a specific document type.
     */
    @Query("""
            SELECT cr FROM ConsentRecord cr
            JOIN cr.document ld
            WHERE cr.user.id = :userId AND ld.type = :documentType
            ORDER BY cr.timestamp DESC
            """)
    List<ConsentRecord> findByUserIdAndDocumentType(
            @Param("userId") Long userId,
            @Param("documentType") LegalDocumentType documentType);

    /**
     * Check if a user has accepted the latest version of a specific document type.
     */
    @Query("""
            SELECT COUNT(cr) > 0 FROM ConsentRecord cr
            JOIN cr.document ld
            WHERE cr.user.id = :userId
              AND ld.type = :documentType
              AND ld.version = :version
            """)
    boolean hasUserAcceptedDocumentVersion(
            @Param("userId") Long userId,
            @Param("documentType") LegalDocumentType documentType,
            @Param("version") Integer version);

    /**
     * Find recent anonymous consent records (user_id IS NULL).
     * Used by admin endpoint to check for orphaned records.
     */
    @Query("SELECT cr FROM ConsentRecord cr JOIN FETCH cr.document WHERE cr.user IS NULL AND cr.timestamp > :since ORDER BY cr.timestamp DESC")
    List<ConsentRecord> findRecentAnonymousRecords(@Param("since") LocalDateTime since);

    /**
     * Delete anonymous consent records older than the specified cutoff.
     * Used by the weekly cleanup cron job.
     */
    @Modifying
    @Query("DELETE FROM ConsentRecord cr WHERE cr.user IS NULL AND cr.timestamp < :cutoff")
    int deleteAnonymousRecordsBefore(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Delete all consent records for a specific user.
     * Used by E2E test infrastructure to reset consent state.
     */
    @Modifying
    @Query("DELETE FROM ConsentRecord cr WHERE cr.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
