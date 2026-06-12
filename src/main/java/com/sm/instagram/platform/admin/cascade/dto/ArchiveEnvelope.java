package com.sm.instagram.platform.admin.cascade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Envelope for archived user data before deletion.
 * Stored in GCS for 2-year retention per GDPR requirements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveEnvelope {
    /**
     * Unique archive ID (UUID).
     */
    private String archiveId;

    /**
     * Version of the archive format.
     */
    @Builder.Default
    private String version = "1.0";

    /**
     * When the archive was created.
     */
    private LocalDateTime archivedAt;

    /**
     * When the archive should be deleted (archivedAt + 2 years).
     */
    private LocalDateTime retentionExpiry;

    /**
     * Reason for archival/deletion.
     */
    private String reason;

    /**
     * Admin who initiated the deletion.
     */
    private String adminFirebaseId;

    // ========== User Data ==========

    /**
     * PostgreSQL user ID.
     */
    private Long userId;

    /**
     * Firebase UID.
     */
    private String firebaseUserId;

    /**
     * User type at time of deletion.
     */
    private String userType;

    /**
     * User email at time of deletion.
     */
    private String userEmail;

    /**
     * Full user entity serialized as JSON.
     */
    private String userJson;

    // ========== Related Entities ==========

    /**
     * User's addresses serialized as JSON.
     */
    private String addressesJson;

    /**
     * User's social connections serialized as JSON.
     */
    private String socialConnectionsJson;

    /**
     * Partnership opportunities (if company) serialized as JSON.
     */
    private String partnershipOpportunitiesJson;

    /**
     * Applied opportunities (if influencer) serialized as JSON.
     */
    private String appliedOpportunitiesJson;

    // ========== Firestore Data ==========

    /**
     * Firestore instagramUsers document serialized as JSON.
     */
    private String firestoreInstagramJson;

    /**
     * Firestore totpSecrets document serialized as JSON (encrypted).
     */
    private String firestoreTotpJson;

    // ========== Metadata ==========

    /**
     * Entity counts at time of deletion.
     */
    private Map<String, Integer> entityCounts;

    /**
     * Checksum of the archive content for integrity verification.
     */
    private String checksum;
}
