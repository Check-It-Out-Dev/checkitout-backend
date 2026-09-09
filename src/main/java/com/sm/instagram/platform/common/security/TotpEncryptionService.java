package com.sm.instagram.platform.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for encrypting and decrypting TOTP secrets and backup codes.
 * Uses KMS for encryption and BCrypt for backup code hashing.
 */
@Service
@Slf4j
public class TotpEncryptionService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private KMSValidationService kmsService;
    // Cloud KMS is the cipher in production. A run without a Google credential - the e2e tier on a public
    // runner - sets gcp.kms.enabled=false and encrypts with a configured key instead. KMSValidationService
    // throws when disabled rather than weakening anything silently, which is why the branch is here.
    @Autowired
    private LocalTotpCipher localCipher;
    @Value("${gcp.kms.enabled:true}")
    private boolean kmsEnabled;
    private BCryptPasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        this.passwordEncoder = new BCryptPasswordEncoder(12);
        log.info("GDPR: Operation=initializeTotpEncryption, Purpose=system_startup, DataAccessed=none");
        log.info("TotpEncryptionService initialized with BCrypt strength 12, cipher={}",
                kmsEnabled ? "Cloud KMS" : "local key (gcp.kms.enabled=false)");
    }

    /**
     * Encrypt a TOTP secret using KMS encryption
     *
     * @param plainSecret The plain TOTP secret
     * @return KMS encrypted secret
     */
    public String encryptTotpSecret(String plainSecret) {
        String firebaseUid = extractFirebaseUid();
        log.warn("GDPR: Operation=encryptTotpSecret, FirebaseUID={}, DataAccessed=totp_secret, Purpose=2fa_setup", firebaseUid);

        if (plainSecret == null || plainSecret.isEmpty()) {
            log.error("GDPR: Operation=encryptTotpSecret_failed, FirebaseUID={}, Error=invalid_input, Purpose=2fa_setup", firebaseUid);
            throw new ValidationTranslatableException("error.validation.required_field", "TOTP secret");
        }

        try {
            String encrypted = seal(plainSecret);
            log.info("GDPR: Operation=encryptTotpSecret_success, FirebaseUID={}, Purpose=2fa_setup", firebaseUid);
            return encrypted;
        } catch (Exception e) {
            log.error("GDPR: Operation=encryptTotpSecret_error, FirebaseUID={}, Error={}, Purpose=2fa_setup", firebaseUid, e.getMessage(), e);
            throw new StorageTranslatableException("error.storage.upload_failed");
        }
    }

    /**
     * Decrypt a TOTP secret using KMS decryption
     *
     * @param encrypted The KMS encrypted secret
     * @return Plain TOTP secret
     */
    public String decryptTotpSecret(String encrypted) {
        String firebaseUid = extractFirebaseUid();
        log.warn("GDPR: Operation=decryptTotpSecret, FirebaseUID={}, DataAccessed=totp_secret, Purpose=2fa_verification", firebaseUid);

        if (encrypted == null || encrypted.isEmpty()) {
            log.debug("GDPR: Operation=decryptTotpSecret_empty, FirebaseUID={}, Purpose=2fa_verification", firebaseUid);
            return null;
        }

        try {
            String decrypted = open(encrypted);
            log.info("GDPR: Operation=decryptTotpSecret_success, FirebaseUID={}, Purpose=2fa_verification", firebaseUid);
            return decrypted;
        } catch (Exception e) {
            log.error("GDPR: Operation=decryptTotpSecret_error, FirebaseUID={}, Error={}, Purpose=2fa_verification", firebaseUid, e.getMessage(), e);
            throw new StorageTranslatableException("error.storage.upload_failed");
        }
    }

    /**
     * Encrypt backup codes using BCrypt hashing followed by KMS encryption
     *
     * @param plainCodes List of plain backup codes
     * @return KMS encrypted JSON of BCrypt hashed codes
     */
    public String encryptBackupCodes(List<String> plainCodes) {
        String firebaseUid = extractFirebaseUid();
        log.warn("GDPR: Operation=encryptBackupCodes, FirebaseUID={}, DataAccessed=backup_codes, CodesCount={}, Purpose=2fa_backup_setup",
                firebaseUid, plainCodes != null ? plainCodes.size() : 0);

        if (plainCodes == null || plainCodes.isEmpty()) {
            log.error("GDPR: Operation=encryptBackupCodes_failed, FirebaseUID={}, Error=invalid_input, Purpose=2fa_backup_setup", firebaseUid);
            throw new ValidationTranslatableException("error.validation.required_field", "Backup codes");
        }

        try {
            // First BCrypt hash each code
            List<String> hashedCodes = plainCodes.stream()
                    .map(code -> passwordEncoder.encode(code))
                    .collect(Collectors.toList());

            log.debug("Hashed {} backup codes with BCrypt", hashedCodes.size());

            // Convert to JSON
            String json = objectMapper.writeValueAsString(hashedCodes);

            // Then KMS encrypt the hashed codes
            String encrypted = seal(json);
            log.info("GDPR: Operation=encryptBackupCodes_success, FirebaseUID={}, CodesCount={}, Purpose=2fa_backup_setup",
                    firebaseUid, hashedCodes.size());

            return encrypted;
        } catch (JsonProcessingException e) {
            log.error("GDPR: Operation=encryptBackupCodes_serialization_error, FirebaseUID={}, Error={}, Purpose=2fa_backup_setup",
                    firebaseUid, e.getMessage(), e);
            throw new ValidationTranslatableException("error.validation.invalid_json");
        } catch (Exception e) {
            log.error("GDPR: Operation=encryptBackupCodes_error, FirebaseUID={}, Error={}, Purpose=2fa_backup_setup",
                    firebaseUid, e.getMessage(), e);
            throw new StorageTranslatableException("error.storage.upload_failed");
        }
    }

    /**
     * Verify a backup code against the encrypted codes
     *
     * @param plainCode      The plain backup code to verify
     * @param encryptedCodes The KMS encrypted BCrypt hashed codes
     * @return true if the code matches, false otherwise
     */
    public boolean verifyBackupCode(String plainCode, String encryptedCodes) {
        String firebaseUid = extractFirebaseUid();
        log.warn("GDPR: Operation=verifyBackupCode, FirebaseUID={}, DataAccessed=backup_codes, Purpose=2fa_backup_verification", firebaseUid);

        if (plainCode == null || encryptedCodes == null) {
            log.debug("GDPR: Operation=verifyBackupCode_invalid_input, FirebaseUID={}, Purpose=2fa_backup_verification", firebaseUid);
            return false;
        }

        try {
            // Decrypt from KMS
            String json = open(encryptedCodes);

            // Parse the hashed codes
            List<String> hashedCodes = objectMapper.readValue(json,
                    new TypeReference<List<String>>() {
                    });

            // Check BCrypt match against each hashed code
            boolean matches = hashedCodes.stream()
                    .anyMatch(hash -> passwordEncoder.matches(plainCode, hash));

            if (matches) {
                log.warn("GDPR: Operation=verifyBackupCode_success, FirebaseUID={}, Purpose=2fa_backup_verification", firebaseUid);
            } else {
                log.warn("GDPR: Operation=verifyBackupCode_failed, FirebaseUID={}, Purpose=2fa_backup_verification", firebaseUid);
            }

            return matches;
        } catch (Exception e) {
            log.error("GDPR: Operation=verifyBackupCode_error, FirebaseUID={}, Error={}, Purpose=2fa_backup_verification",
                    firebaseUid, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get the remaining backup codes after one is used
     *
     * @param usedCode       The code that was just used
     * @param encryptedCodes The current encrypted codes
     * @return New encrypted codes without the used one
     */
    public String removeUsedBackupCode(String usedCode, String encryptedCodes) {
        String firebaseUid = extractFirebaseUid();
        log.warn("GDPR: Operation=removeUsedBackupCode, FirebaseUID={}, DataModified=backup_codes, Purpose=2fa_backup_consumption", firebaseUid);

        if (usedCode == null || encryptedCodes == null) {
            log.debug("GDPR: Operation=removeUsedBackupCode_invalid_input, FirebaseUID={}, Purpose=2fa_backup_consumption", firebaseUid);
            return encryptedCodes;
        }

        try {
            // Decrypt from KMS
            String json = open(encryptedCodes);

            // Parse the hashed codes
            List<String> hashedCodes = objectMapper.readValue(json,
                    new TypeReference<List<String>>() {
                    });

            // Find and remove the matching hash
            List<String> remainingCodes = hashedCodes.stream()
                    .filter(hash -> !passwordEncoder.matches(usedCode, hash))
                    .collect(Collectors.toList());

            // Re-encrypt the remaining codes
            String newJson = objectMapper.writeValueAsString(remainingCodes);
            String newEncrypted = seal(newJson);

            log.info("GDPR: Operation=removeUsedBackupCode_success, FirebaseUID={}, RemainingCodes={}, Purpose=2fa_backup_consumption",
                    firebaseUid, remainingCodes.size());

            return newEncrypted;
        } catch (Exception e) {
            log.error("GDPR: Operation=removeUsedBackupCode_error, FirebaseUID={}, Error={}, Purpose=2fa_backup_consumption",
                    firebaseUid, e.getMessage(), e);
            return encryptedCodes;
        }
    }

    /**
     * Extract Firebase UID from security context if available
     *
     * @return Firebase UID or "system" if not available
     */
    /**
     * Encrypt with whichever cipher this run is configured for.
     *
     * <p>Cloud KMS in production. A run with no Google credential - the e2e tier on a public runner -
     * sets {@code gcp.kms.enabled=false} and uses a key from configuration instead;
     * {@link KMSValidationService} throws when disabled rather than weakening anything silently, so the
     * choice has to be made here. Every TOTP value, secret and backup codes alike, goes through this pair,
     * because a document half-written by one cipher and half by the other cannot be read back by either.
     */
    private String seal(String plaintext) {
        return kmsEnabled ? kmsService.encryptTotpSecret(plaintext) : localCipher.encrypt(plaintext);
    }

    /** The other half of {@link #seal}. */
    private String open(String ciphertext) {
        return kmsEnabled ? kmsService.decryptTotpSecret(ciphertext) : localCipher.decrypt(ciphertext);
    }

    private String extractFirebaseUid() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return auth.getPrincipal().toString();
            }
        } catch (Exception e) {
            // Security context not available in this context
        }
        return "system";
    }
}
