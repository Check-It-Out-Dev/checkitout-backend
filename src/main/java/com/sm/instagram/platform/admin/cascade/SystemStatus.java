package com.sm.instagram.platform.admin.cascade;

/**
 * Status for each external system during cascade delete operations.
 * Tracks the outcome of deletion attempts across PostgreSQL, Firestore, and Firebase Auth.
 */
public enum SystemStatus {
    /**
     * Deletion not yet attempted for this system.
     */
    PENDING,

    /**
     * Deletion completed successfully for this system.
     */
    SUCCESS,

    /**
     * Deletion failed for this system. May be retried.
     */
    FAILED,

    /**
     * Deletion skipped (e.g., user had no firebaseUserId, so Firebase cleanup not needed).
     */
    SKIPPED
}
