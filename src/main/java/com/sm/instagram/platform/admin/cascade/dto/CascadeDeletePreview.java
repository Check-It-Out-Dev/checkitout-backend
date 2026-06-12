package com.sm.instagram.platform.admin.cascade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for previewing what will be deleted in a cascade delete operation.
 * Includes counts by entity type and systems that will be cleaned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CascadeDeletePreview {
    /**
     * User ID being deleted.
     */
    private Long userId;

    /**
     * Firebase UID of the user.
     */
    private String firebaseUserId;

    /**
     * User email for identification.
     */
    private String userEmail;

    /**
     * User type (INFLUENCER, COMPANY, ADMIN).
     */
    private String userType;

    /**
     * User's display name.
     */
    private String userName;

    /**
     * Total count of PostgreSQL entities to be deleted.
     */
    private int totalEntityCount;

    /**
     * Breakdown of entity counts by type.
     */
    private List<EntityTypeCount> entityBreakdown;

    /**
     * List of external systems that will be cleaned.
     */
    private List<String> systemsToClean;

    /**
     * Warnings about potential issues (e.g., no Firebase ID, orphaned data).
     */
    private List<String> warnings;

    /**
     * Whether the user has data in Firestore instagramUsers collection.
     */
    private boolean hasFirestoreInstagramData;

    /**
     * Whether the user has data in Firestore totpSecrets collection.
     */
    private boolean hasFirestoreTotpData;

    /**
     * Whether the user has files in Firebase Storage.
     */
    private boolean hasFirebaseStorageData;

    /**
     * Whether the user exists in Firebase Auth.
     */
    private boolean existsInFirebaseAuth;

    /**
     * Confirmation code required to execute deletion.
     * Format: CASCADE-DELETE-{userId}
     */
    private String confirmationCode;
}
