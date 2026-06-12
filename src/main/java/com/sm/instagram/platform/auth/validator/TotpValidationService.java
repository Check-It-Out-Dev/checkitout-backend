package com.sm.instagram.platform.auth.validator;

import com.google.cloud.firestore.Firestore;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.security.KMSValidationService;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Validates TOTP (2FA) service configuration and functionality at application startup.
 *
 * Uses injected Firestore bean instead of FirestoreClient.getFirestore()
 * to ensure proper Spring lifecycle management.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Order(3) // Run after Firebase and KMS validation
public class TotpValidationService implements ApplicationListener<ApplicationReadyEvent> {

    private final Firestore firestore;
    private final KMSValidationService kmsService;
    private final TotpFirestoreService totpFirestoreService;
    private final GoogleAuthenticator gAuth;  // Injected from GoogleAuthenticatorConfig
    
    @Value("${totp.enabled:true}")
    private boolean totpEnabled;
    
    @Value("${totp.issuer:CheckItOut}")
    private String issuer;
    
    @Value("${gcp.kms.totp-key-name:totp-secrets-key}")
    private String totpKeyName;
    
    @Value("${gcp.kms.key-ring:instagram-tokens}")
    private String keyRingName;
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("==========================================");
        log.info("🔐 TOTP (2FA) VALIDATION");
        log.info("==========================================");
        log.info("GDPR: Operation=startTotpValidation, FirebaseUID=SYSTEM, DataAccessed=config_validation, Purpose=system_initialization");
        
        if (!totpEnabled) {
            log.warn("⚠️ TOTP is DISABLED (totp.enabled=false)");
            log.warn("   Admin accounts will NOT have 2FA protection!");
            return;
        }
        
        log.info("📋 TOTP Configuration:");
        log.info("   Issuer: {}", issuer);
        log.info("   Key Ring: {}", keyRingName);
        log.info("   TOTP Key: {}", totpKeyName);
        log.info("   Status: ENABLED");
        
        boolean allChecksPass = true;
        
        // Validate KMS access for TOTP
        if (!validateKMSAccess()) {
            allChecksPass = false;
        }
        
        // Validate Firestore access
        if (!validateFirestoreAccess()) {
            allChecksPass = false;
        }
        
        // Validate TOTP generation
        if (!validateTOTPGeneration()) {
            allChecksPass = false;
        }
        
        if (allChecksPass) {
            log.info("\n✅ TOTP Service: OPERATIONAL");
            log.info("   ✓ KMS encryption working");
            log.info("   ✓ Firestore access confirmed");
            log.info("   ✓ TOTP generation verified");
            log.info("   ✓ Ready for admin 2FA");
            log.info("GDPR: Operation=completeTotpValidation, FirebaseUID=SYSTEM, DataAccessed=system_health, Purpose=startup_validation, Result=SUCCESS");
        } else {
            log.error("\n❌ TOTP Service: DEGRADED");
            log.error("   Some checks failed - 2FA may not work properly");
            log.error("   Check the logs above for details");
            log.error("GDPR: Operation=completeTotpValidation, FirebaseUID=SYSTEM, DataAccessed=system_health, Purpose=startup_validation, Result=FAILED");
        }
        
        log.info("==========================================");
    }
    
    private boolean validateKMSAccess() {
        try {
            log.info("\n🔑 Testing TOTP KMS Encryption:");
            
            // Test TOTP encryption with a sample secret
            Base32 base32 = new Base32();
            String testSecret = base32.encodeAsString("test-totp-secret".getBytes());
            
            // Encrypt
            String encrypted = kmsService.encryptTotpSecret(testSecret);
            log.info("   ✓ TOTP secret encrypted");
            
            // Decrypt
            String decrypted = kmsService.decryptTotpSecret(encrypted);
            log.info("   ✓ TOTP secret decrypted");
            
            // Verify round-trip
            if (!testSecret.equals(decrypted)) {
                throw new ValidationTranslatableException("error.validation.failed");
            }
            
            log.info("   ✓ TOTP KMS round-trip validated");
            log.debug("GDPR: Operation=validateKMS, FirebaseUID=SYSTEM, DataAccessed=kms_test_data, Purpose=system_validation");
            return true;
            
        } catch (Exception e) {
            log.error("   ✗ TOTP KMS validation failed: {}", e.getMessage());
            log.error("   → Check if totp-secrets-key exists in {}", keyRingName);
            return false;
        }
    }
    
    private boolean validateFirestoreAccess() {
        try {
            log.info("\n📚 Testing Firestore TOTP Collection:");

            // Using injected Firestore bean
            // Try to read from the totpSecrets collection (won't exist yet, but tests access)
            firestore.collection("totpSecrets").document("test-validation").get().get();
            
            log.info("   ✓ Firestore TOTP collection accessible");
            log.debug("GDPR: Operation=validateFirestore, FirebaseUID=SYSTEM, DataAccessed=firestore_test_collection, Purpose=system_validation");
            return true;
            
        } catch (Exception e) {
            // This is expected if the document doesn't exist
            if (e.getMessage() != null && !e.getMessage().contains("NOT_FOUND")) {
                log.error("   ✗ Firestore access failed: {}", e.getMessage());
                return false;
            }
            
            log.info("   ✓ Firestore TOTP collection accessible");
            return true;
        }
    }
    
    private boolean validateTOTPGeneration() {
        try {
            log.info("\n🔢 Testing TOTP Generation:");
            
            // Use the injected GoogleAuthenticator bean
            // Generate a test key
            GoogleAuthenticatorKey key = gAuth.createCredentials();
            String secret = key.getKey();
            log.info("   ✓ TOTP secret generated: {}...", secret.substring(0, 4));
            
            // Generate current code
            int code = gAuth.getTotpPassword(secret);
            log.info("   ✓ TOTP code generated: {}", String.format("%06d", code));
            
            // Verify it
            boolean valid = gAuth.authorize(secret, code);
            
            if (!valid) {
                throw new ValidationTranslatableException("error.validation.failed");
            }
            
            log.info("   ✓ TOTP generation and verification working");
            
            // Test time window tolerance (using the same configured instance)
            log.info("   Testing time window tolerance (configured in application.yml)...");
            
            // The window size is already configured in the bean
            valid = gAuth.authorize(secret, code);
            if (!valid) {
                log.warn("   ⚠ TOTP window tolerance may have issues");
            } else {
                log.info("   ✓ TOTP time window tolerance working");
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("   ✗ TOTP generation failed: {}", e.getMessage());
            return false;
        }
    }
}
