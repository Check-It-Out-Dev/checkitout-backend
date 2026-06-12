package com.sm.instagram.platform.admin.cascade.dto;

import com.sm.instagram.platform.admin.cascade.SystemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for tracking deletion status across all external systems.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatuses {
    private SystemStatus postgresql;
    private SystemStatus firestoreInstagram;
    private SystemStatus firestoreTotp;
    private SystemStatus firebaseStorage;
    private SystemStatus firebaseAuth;

    /**
     * Returns true if all systems have completed (SUCCESS or SKIPPED).
     */
    public boolean isFullyCompleted() {
        return (postgresql == SystemStatus.SUCCESS || postgresql == SystemStatus.SKIPPED)
            && (firestoreInstagram == SystemStatus.SUCCESS || firestoreInstagram == SystemStatus.SKIPPED)
            && (firestoreTotp == SystemStatus.SUCCESS || firestoreTotp == SystemStatus.SKIPPED)
            && (firebaseStorage == SystemStatus.SUCCESS || firebaseStorage == SystemStatus.SKIPPED)
            && (firebaseAuth == SystemStatus.SUCCESS || firebaseAuth == SystemStatus.SKIPPED);
    }

    /**
     * Returns true if any system has FAILED status.
     */
    public boolean hasFailures() {
        return postgresql == SystemStatus.FAILED
            || firestoreInstagram == SystemStatus.FAILED
            || firestoreTotp == SystemStatus.FAILED
            || firebaseStorage == SystemStatus.FAILED
            || firebaseAuth == SystemStatus.FAILED;
    }
}
