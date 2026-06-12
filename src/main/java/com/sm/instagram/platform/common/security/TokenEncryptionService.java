package com.sm.instagram.platform.common.security;

import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Service for encrypting/decrypting Instagram Business API access tokens using Google Cloud KMS.
 * Pre-MVP implementation - all tokens are encrypted from start.
 * 
 * Note: Instagram Business API uses long-lived access tokens (60-day expiry),
 * not refresh tokens like Basic Display API.
 */
@Slf4j
@Service
public class TokenEncryptionService {

    @Autowired
    private KMSValidationService kmsService;

    @Value("${gcp.kms.enabled:true}")
    private boolean kmsEnabled;

    /**
     * Encrypts token if KMS is enabled, otherwise throws exception (security first).
     * 
     * @param plainToken The plain text token to encrypt
     * @return Base64 encoded encrypted token
     * @throws RuntimeException if KMS is disabled or encryption fails
     */
    public String encryptToken(String plainToken) {
        if (plainToken == null || plainToken.isEmpty()) {
            return null;
        }

        // Extract Firebase UID if available from security context
        String firebaseUid = extractFirebaseUid();
        
        // GDPR: Log token encryption operation
        log.info("GDPR: Operation=token_encryption_started, FirebaseUID={}, DataCategory=access_token, Purpose=secure_storage, ThirdParty=GoogleKMS, LegalBasis=legitimate_interest", 
            firebaseUid != null ? firebaseUid : "system");

        if (!kmsEnabled) {
            log.error("GDPR: Operation=token_encryption_failed, FirebaseUID={}, Error=kms_disabled, Purpose=error_logging",
                firebaseUid != null ? firebaseUid : "system");
            throw new AuthenticationTranslatableException("error.security.encryption_disabled");
        }

        try {
            long startTime = System.currentTimeMillis();
            String encrypted = kmsService.encryptToken(plainToken);
            long duration = System.currentTimeMillis() - startTime;
            
            // GDPR: Log successful encryption
            log.info("GDPR: Operation=token_encrypted, FirebaseUID={}, DurationMs={}, Purpose=secure_storage, DataProtection=AES256_GCM", 
                firebaseUid != null ? firebaseUid : "system", duration);
            
            return encrypted;
        } catch (Exception e) {
            // GDPR: Log encryption failure
            log.error("GDPR: Operation=token_encryption_failed, FirebaseUID={}, Error={}, Purpose=error_logging",
                firebaseUid != null ? firebaseUid : "system", e.getMessage(), e);
            throw new AuthenticationTranslatableException("error.security.encryption_failed");
        }
    }

    /**
     * Decrypts token if KMS is enabled.
     * 
     * @param encryptedToken The encrypted token to decrypt
     * @return Plain text token
     * @throws RuntimeException if decryption fails
     */
    public String decryptToken(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.isEmpty()) {
            return null;
        }

        // Extract Firebase UID if available from security context
        String firebaseUid = extractFirebaseUid();
        
        // GDPR: Log token decryption operation
        log.info("GDPR: Operation=token_decryption_started, FirebaseUID={}, DataCategory=access_token, Purpose=api_usage, ThirdParty=GoogleKMS", 
            firebaseUid != null ? firebaseUid : "system");

        if (!kmsEnabled) {
            log.error("GDPR: Operation=token_decryption_failed, FirebaseUID={}, Error=kms_disabled, Purpose=error_logging",
                firebaseUid != null ? firebaseUid : "system");
            throw new AuthenticationTranslatableException("error.security.decryption_disabled");
        }

        try {
            long startTime = System.currentTimeMillis();
            String decrypted = kmsService.decryptToken(encryptedToken);
            long duration = System.currentTimeMillis() - startTime;
            
            // GDPR: Log successful decryption
            log.info("GDPR: Operation=token_decrypted, FirebaseUID={}, DurationMs={}, Purpose=api_usage, DataAccessed=access_token", 
                firebaseUid != null ? firebaseUid : "system", duration);
            
            return decrypted;
        } catch (Exception e) {
            // GDPR: Log decryption failure
            log.error("GDPR: Operation=token_decryption_failed, FirebaseUID={}, Error={}, Purpose=error_logging",
                firebaseUid != null ? firebaseUid : "system", e.getMessage(), e);
            throw new AuthenticationTranslatableException("error.security.decryption_failed");
        }
    }

    /**
     * Checks if KMS encryption is enabled.
     * 
     * @return true if KMS is enabled
     */
    public boolean isEncryptionEnabled() {
        return kmsEnabled;
    }

    /**
     * Validates KMS service is operational.
     * 
     * @return true if KMS service is working
     */
    public boolean validateKMSService() {
        if (!kmsEnabled) {
            return false;
        }

        // GDPR: Log KMS validation
        log.info("GDPR: Operation=kms_validation_started, Purpose=service_health_check, ThirdParty=GoogleKMS");

        try {
            // Test with dummy data
            String testData = "validation_test_" + System.currentTimeMillis();
            String encrypted = kmsService.encryptToken(testData);
            String decrypted = kmsService.decryptToken(encrypted);
            
            boolean valid = testData.equals(decrypted);
            if (valid) {
                // GDPR: Log successful validation
                log.info("GDPR: Operation=kms_validation_success, Purpose=service_health_check, DataUsed=test_data_only");
            } else {
                // GDPR: Log validation failure
                log.error("GDPR: Operation=kms_validation_failed, Error=data_mismatch, Purpose=error_logging");
            }
            return valid;
        } catch (Exception e) {
            // GDPR: Log validation error
            log.error("GDPR: Operation=kms_validation_error, Error={}, Purpose=error_logging", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Extract Firebase UID from security context if available.
     * 
     * @return Firebase UID or null if not available
     */
    private String extractFirebaseUid() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return auth.getPrincipal().toString();
            }
        } catch (Exception e) {
            // Unable to extract Firebase UID
        }
        return null;
    }
}
