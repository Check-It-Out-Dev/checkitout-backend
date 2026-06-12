package com.sm.instagram.platform.auth.entity;

/**
 * Status of a Meta data deletion request.
 */
public enum DeletionRequestStatus {
    /** Deletion is queued — active collaborations are blocking immediate execution. */
    PENDING,
    /** Deletion is currently being processed. */
    IN_PROGRESS,
    /** Deletion has been completed successfully. */
    COMPLETED,
    /** Deletion was rejected (e.g., legal hold). */
    REJECTED
}
