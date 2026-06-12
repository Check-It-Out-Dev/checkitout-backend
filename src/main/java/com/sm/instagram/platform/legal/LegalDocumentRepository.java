package com.sm.instagram.platform.legal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {

    /**
     * Find the latest version of a specific document type and language.
     */
    Optional<LegalDocument> findTopByTypeAndLanguageOrderByVersionDesc(
            LegalDocumentType type, String language);

    /**
     * Find a specific document by type, language, and version.
     */
    Optional<LegalDocument> findByTypeAndLanguageAndVersion(
            LegalDocumentType type, String language, Integer version);

    /**
     * Get current (latest version) documents for all types and languages.
     * Returns the document with the highest version for each (type, language) pair.
     */
    @Query(value = """
            SELECT ld.* FROM legal_document ld
            INNER JOIN (
                SELECT type, language, MAX(version) AS max_version
                FROM legal_document
                GROUP BY type, language
            ) latest ON ld.type = latest.type
                AND ld.language = latest.language
                AND ld.version = latest.max_version
            ORDER BY ld.type, ld.language
            """, nativeQuery = true)
    List<LegalDocument> findAllCurrentDocuments();

    /**
     * Get the most recent published_at timestamp across all documents.
     * Used to compute the 38-day grace period start.
     */
    @Query("SELECT MAX(ld.publishedAt) FROM LegalDocument ld")
    Optional<LocalDateTime> findLatestPublishedAt();

    /**
     * Get the latest version number for a specific document type (across all languages).
     */
    @Query("SELECT MAX(ld.version) FROM LegalDocument ld WHERE ld.type = :type")
    Optional<Integer> findLatestVersionByType(@Param("type") LegalDocumentType type);
}
