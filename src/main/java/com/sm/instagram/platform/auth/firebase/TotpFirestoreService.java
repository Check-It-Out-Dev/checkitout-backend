package com.sm.instagram.platform.auth.firebase;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.sm.instagram.model.firestore.TotpSecretDocument;
import com.sm.instagram.platform.auth.exceptions.TwoFactorAuthException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.security.TotpEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Service for managing TOTP secrets in Firestore.
 * Stores encrypted TOTP secrets and backup codes at the root level.
 *
 * Uses injected Firestore bean instead of FirestoreClient.getFirestore()
 * to ensure proper Spring lifecycle management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TotpFirestoreService {

    private static final String TOTP_SECRETS_COLLECTION = "totpSecrets";
    private static final String AUDIT_LOG_SUBCOLLECTION = "auditLog";

    private final Firestore firestore;
    private final TotpEncryptionService encryptionService;
    
    /**
     * Store a new TOTP secret for a user
     * @param userId Firebase user ID
     * @param secret Plain TOTP secret
     * @param backupCodes List of plain backup codes
     */
    public void storeTotpSecret(String userId, String secret, List<String> backupCodes) {
        log.info("GDPR: Operation=storeTotpSecret, FirebaseUID={}, DataAccessed=2fa_secret,backup_codes, Purpose=security_setup", userId);
        try {
            // Using injected Firestore bean (firestore field)
            
            // Encrypt secret
            String encryptedSecret = encryptionService.encryptTotpSecret(secret);
            
            // Encrypt backup codes (BCrypt + KMS)
            String encryptedBackupCodes = encryptionService.encryptBackupCodes(backupCodes);
            
            // Create backup codes object
            TotpSecretDocument.BackupCodes backupCodesData = TotpSecretDocument.BackupCodes.builder()
                .encryptedCodes(encryptedBackupCodes)
                .usedCodes(new ArrayList<>())
                .generatedAt(com.google.cloud.Timestamp.now())
                .build();
            
            // Create document using model
            TotpSecretDocument document = TotpSecretDocument.builder()
                .encryptedSecret(encryptedSecret)
                .enabled(false)
                .setupAt(com.google.cloud.Timestamp.now())
                .backupCodes(backupCodesData)
                .build();
            
            // Store in Firestore (root-level collection)
            DocumentReference docRef = firestore.collection(TOTP_SECRETS_COLLECTION).document(userId);
            ApiFuture<WriteResult> future = docRef.set(document);
            future.get(); // Wait for completion
            
            log.info("TOTP secret stored for user: {}", userId);
            
            // Log audit event
            logAuditEvent(userId, "TOTP_SETUP", "SUCCESS", null, null);
            
        } catch (Exception e) {
            log.error("Failed to store TOTP secret for user {}: {}", userId, e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.data_integrity");
        }
    }
    
    /**
     * Get the decrypted TOTP secret for a user
     * @param userId Firebase user ID
     * @return Plain TOTP secret or null if not found
     */
    public String getTotpSecret(String userId) {
        log.info("GDPR: Operation=getTotpSecret, FirebaseUID={}, DataAccessed=2fa_secret, Purpose=authentication", userId);
        try {
            // Using injected Firestore bean (firestore field)
            DocumentReference docRef = firestore.collection(TOTP_SECRETS_COLLECTION).document(userId);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot doc = future.get();
            
            if (!doc.exists()) {
                log.debug("No TOTP secret found for user: {}", userId);
                return null;
            }
            
            TotpSecretDocument document = doc.toObject(TotpSecretDocument.class);
            if (document == null || document.getEncryptedSecret() == null) {
                log.warn("TOTP document exists but no secret found for user: {}", userId);
                return null;
            }
            
            // Update last used timestamp
            docRef.update("lastUsedAt", com.google.cloud.Timestamp.now());
            
            return encryptionService.decryptTotpSecret(document.getEncryptedSecret());
            
        } catch (Exception e) {
            log.error("Failed to get TOTP secret for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if 2FA is enabled for a user.
     *
     * @param userId Firebase user ID
     * @return true if 2FA is enabled, false if not configured
     * @throws TwoFactorAuthException if unable to verify 2FA status (Firestore failure)
     */
    public boolean is2FAEnabled(String userId) {
        log.debug("GDPR: Operation=check2FAStatus, FirebaseUID={}, DataAccessed=2fa_enabled_flag, Purpose=security_verification", userId);
        try {
            DocumentReference docRef = firestore.collection(TOTP_SECRETS_COLLECTION).document(userId);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot doc = future.get();

            if (!doc.exists()) {
                log.debug("No 2FA document found for user: {} - 2FA not configured", userId);
                return false;
            }

            TotpSecretDocument document = doc.toObject(TotpSecretDocument.class);
            boolean enabled = document != null && Boolean.TRUE.equals(document.getEnabled());
            log.debug("2FA status for user {}: {}", userId, enabled ? "ENABLED" : "NOT_ENABLED");
            return enabled;

        } catch (Exception e) {
            // CRITICAL: Do NOT return false here! Returning false would incorrectly
            // indicate that 2FA is not configured, causing users with existing 2FA
            // to be shown the setup screen instead of the TOTP verification screen.
            log.error("Failed to check 2FA status for user {} - Firestore error: {}", userId, e.getMessage(), e);
            throw new TwoFactorAuthException("Unable to verify 2FA status due to infrastructure error", e);
        }
    }

    /**
     * Check if TOTP document exists for a user (regardless of enabled state).
     * Used for integrity checking - if document exists but enabled=false, there's a data issue.
     * @param userId Firebase user ID
     * @return true if document exists, false otherwise
     */
    public boolean totpSecretExists(String userId) {
        log.debug("GDPR: Operation=checkTotpExists, FirebaseUID={}, DataAccessed=document_existence, Purpose=integrity_check", userId);
        try {
            // Using injected Firestore bean (firestore field)
            DocumentReference docRef = firestore.collection(TOTP_SECRETS_COLLECTION).document(userId);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot doc = future.get();
            return doc.exists();
        } catch (Exception e) {
            log.error("Failed to check TOTP document existence for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Enable 2FA for a user after successful verification
     * @param userId Firebase user ID
     */
    public void enable2FA(String userId) {
        log.info("GDPR: Operation=enable2FA, FirebaseUID={}, DataAccessed=2fa_settings, Purpose=security_enhancement", userId);
        try {
            // Using injected Firestore bean (firestore field)
            DocumentReference docRef = firestore.collection(TOTP_SECRETS_COLLECTION).document(userId);
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("enabled", true);
            updates.put("verifiedAt", com.google.cloud.Timestamp.now());
            
            ApiFuture<WriteResult> future = docRef.update(updates);
            future.get();
            
            log.info("2FA enabled for user: {}", userId);
            
            // Log audit event
            logAuditEvent(userId, "2FA_ENABLED", "SUCCESS", null, null);
            
        } catch (Exception e) {
            log.error("Failed to enable 2FA for user {}: {}", userId, e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.data_integrity");
        }
    }
    
    /**
     * Disable 2FA for a user
     * @param userId Firebase user ID
     */
    public void disable2FA(String userId) {
        log.warn("GDPR: Operation=disable2FA, FirebaseUID={}, DataAccessed=2fa_settings, Purpose=security_downgrade", userId);
        try {
            // Using injected Firestore bean (firestore field)
            DocumentReference docRef = firestore.collection(TOTP_SECRETS_COLLECTION).document(userId);
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("enabled", false);
            updates.put("disabledAt", com.google.cloud.Timestamp.now());
            
            ApiFuture<WriteResult> future = docRef.update(updates);
            future.get();
            
            log.info("2FA disabled for user: {}", userId);
            
            // Log audit event
            logAuditEvent(userId, "2FA_DISABLED", "SUCCESS", null, null);
            
        } catch (Exception e) {
            log.error("Failed to disable 2FA for user {}: {}", userId, e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.data_integrity");
        }
    }
    
    /**
     * Verify and use a backup code
     * @param userId Firebase user ID
     * @param backupCode The backup code to verify
     * @return true if valid and not already used, false otherwise
     */
    public boolean verifyBackupCode(String userId, String backupCode) {
        log.info("GDPR: Operation=verifyBackupCode, FirebaseUID={}, DataAccessed=backup_codes, Purpose=authentication", userId);
        try {
            // Using injected Firestore bean (firestore field)
            DocumentReference docRef = firestore.collection(TOTP_SECRETS_COLLECTION).document(userId);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot doc = future.get();
            
            if (!doc.exists()) {
                return false;
            }
            
            TotpSecretDocument document = doc.toObject(TotpSecretDocument.class);
            if (document == null || document.getBackupCodes() == null) {
                return false;
            }
            
            TotpSecretDocument.BackupCodes backupData = document.getBackupCodes();
            String encryptedCodes = backupData.getEncryptedCodes();
            List<String> usedCodes = backupData.getUsedCodes();
            
            if (encryptedCodes == null) {
                return false;
            }
            
            // Check if code was already used
            if (usedCodes != null && usedCodes.contains(backupCode)) {
                log.warn("Backup code already used for user: {}", userId);
                logAuditEvent(userId, "BACKUP_CODE_REUSE", "FAILED", null, null);
                return false;
            }
            
            // Verify the code
            boolean valid = encryptionService.verifyBackupCode(backupCode, encryptedCodes);
            
            if (valid) {
                // Mark code as used
                Map<String, Object> updates = new HashMap<>();
                if (usedCodes == null) {
                    usedCodes = new ArrayList<>();
                }
                usedCodes.add(backupCode);
                updates.put("backupCodes.usedCodes", usedCodes);
                updates.put("backupCodes.lastUsedAt", com.google.cloud.Timestamp.now());
                
                docRef.update(updates);
                
                log.info("Backup code used successfully for user: {}", userId);
                logAuditEvent(userId, "BACKUP_CODE_USED", "SUCCESS", null, null);
            } else {
                logAuditEvent(userId, "BACKUP_CODE_INVALID", "FAILED", null, null);
            }
            
            return valid;
            
        } catch (Exception e) {
            log.error("Failed to verify backup code for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the backup codes for a user (for display after setup)
     * @param userId Firebase user ID
     * @return Map containing backup codes info or null
     */
    public Map<String, Object> getBackupCodesInfo(String userId) {
        log.info("GDPR: Operation=getBackupCodesInfo, FirebaseUID={}, DataAccessed=backup_codes_metadata, Purpose=account_management", userId);
        try {
            // Using injected Firestore bean (firestore field)
            DocumentReference docRef = firestore.collection(TOTP_SECRETS_COLLECTION).document(userId);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot doc = future.get();
            
            if (!doc.exists()) {
                return null;
            }
            
            TotpSecretDocument document = doc.toObject(TotpSecretDocument.class);
            if (document == null || document.getBackupCodes() == null) {
                return null;
            }
            
            TotpSecretDocument.BackupCodes backupData = document.getBackupCodes();
            List<String> usedCodes = backupData.getUsedCodes();
            
            // Return sanitized info (not the encrypted codes themselves)
            Map<String, Object> info = new HashMap<>();
            info.put("totalCodes", 10); // We generate 10 codes
            info.put("usedCodes", usedCodes != null ? usedCodes.size() : 0);
            info.put("remainingCodes", 10 - (usedCodes != null ? usedCodes.size() : 0));
            info.put("generatedAt", backupData.getGeneratedAt());
            info.put("lastUsedAt", backupData.getLastUsedAt());
            
            return info;
            
        } catch (Exception e) {
            log.error("Failed to get backup codes info for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Log an audit event for 2FA operations
     * @param userId Firebase user ID
     * @param action The action performed
     * @param result The result (SUCCESS/FAILED)
     * @param ipAddress Client IP address (optional)
     * @param userAgent User agent string (optional)
     */
    public void logAuditEvent(String userId, String action, String result, 
                              String ipAddress, String userAgent) {
        try {
            // Using injected Firestore bean (firestore field)
            
            Map<String, Object> audit = new HashMap<>();
            audit.put("action", action);
            audit.put("result", result);
            audit.put("timestamp", System.currentTimeMillis());
            
            if (ipAddress != null) {
                audit.put("ipAddress", ipAddress);
            }
            if (userAgent != null) {
                audit.put("userAgent", userAgent);
            }
            
            // Store in subcollection
            String auditId = String.valueOf(System.currentTimeMillis());
            firestore.collection(TOTP_SECRETS_COLLECTION)
                .document(userId)
                .collection(AUDIT_LOG_SUBCOLLECTION)
                .document(auditId)
                .set(audit);
            
            log.debug("Audit event logged: {} - {} for user: {}", action, result, userId);
            
        } catch (Exception e) {
            // Don't fail the main operation if audit logging fails
            log.error("Failed to log audit event for user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Delete all 2FA data for a user (for account deletion)
     * @param userId Firebase user ID
     */
    public void deleteTotpData(String userId) {
        log.warn("GDPR: DELETION Operation=deleteTotpData, FirebaseUID={}, DataAccessed=2fa_data,audit_logs, Purpose=account_deletion", userId);
        try {
            // Using injected Firestore bean (firestore field)
            DocumentReference docRef = firestore.collection(TOTP_SECRETS_COLLECTION).document(userId);
            
            // Delete all audit logs first
            CollectionReference auditRef = docRef.collection(AUDIT_LOG_SUBCOLLECTION);
            deleteCollection(auditRef, 10);
            
            // Delete the main document
            ApiFuture<WriteResult> future = docRef.delete();
            future.get();
            
            log.info("All 2FA data deleted for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to delete 2FA data for user {}: {}", userId, e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.data_integrity");
        }
    }
    
    /**
     * Helper method to delete a collection
     */
    private void deleteCollection(CollectionReference collection, int batchSize) 
            throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = collection.limit(batchSize).get();
        int deleted = 0;
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        for (QueryDocumentSnapshot document : documents) {
            document.getReference().delete();
            deleted++;
        }
        if (deleted >= batchSize) {
            // There might be more documents, recurse
            deleteCollection(collection, batchSize);
        }
    }
}
