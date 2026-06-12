package com.sm.instagram.platform.common.security;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.kms.v1.*;
import com.google.protobuf.ByteString;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
public class KMSValidationService {

    @Value("classpath:service-account.json")
    private Resource serviceAccountFile;

    @Value("${firebase.service.account.json.base64:}")
    private String serviceAccountJsonBase64;

    @Value("${gcp.project-id:check-it-out-47c50}")
    private String projectId;

    @Value("${gcp.kms.location:europe-central2}")
    private String kmsLocation;

    @Value("${gcp.kms.key-ring:instagram-tokens}")
    private String keyRingName;

    @Value("${gcp.kms.key-name:token-encryption-key}")
    private String keyName;

    @Value("${gcp.kms.totp-key-name:totp-secrets-key}")
    private String totpKeyName;

    @Value("${gcp.kms.enabled:true}")
    private boolean kmsEnabled;

    private KeyManagementServiceClient kmsClient;
    private CryptoKeyName cryptoKeyName;
    private CryptoKeyName totpCryptoKeyName;

    @PostConstruct
    public void validateKMSAccess() {
        log.info("==========================================");
        log.info("🔐 KMS (KEY MANAGEMENT SERVICE) VALIDATION");
        log.info("==========================================");

        if (!kmsEnabled) {
            log.warn("⚠️ KMS is DISABLED in configuration (gcp.kms.enabled=false)");
            log.warn("   Instagram tokens will NOT be encrypted at rest!");
            return;
        }

        log.info("📍 KMS Configuration:");
        log.info("   Project ID: {}", projectId);
        log.info("   Location: {} (Warsaw, Poland)", kmsLocation);
        log.info("   Key Ring: {}", keyRingName);
        log.info("   Key Name: {}", keyName);

        try {
            // Initialize KMS client with same credentials as Firebase
            initializeKMSClient();

            // Build the full key paths
            cryptoKeyName = CryptoKeyName.of(projectId, kmsLocation, keyRingName, keyName);
            String fullKeyPath = cryptoKeyName.toString();
            log.info("   Full Key Path (Tokens): {}", fullKeyPath);

            // Initialize TOTP key for 2FA
            totpCryptoKeyName = CryptoKeyName.of(projectId, kmsLocation, keyRingName, totpKeyName);
            String totpKeyPath = totpCryptoKeyName.toString();
            log.info("   Full Key Path (TOTP): {}", totpKeyPath);

            // Test encryption/decryption
            log.info("\n🧪 Testing KMS Operations:");
            testKMSOperations();

            log.info("\n✅ KMS VALIDATION SUCCESSFUL");
            log.info("   ✓ Authentication working");
            log.info("   ✓ Key accessible");
            log.info("   ✓ Encryption working");
            log.info("   ✓ Decryption working");
            log.info("   Ready to encrypt Instagram tokens!");

        } catch (Exception e) {
            log.error("\n❌ KMS VALIDATION FAILED!");
            log.error("   Error: {}", e.getMessage());
            log.error("\n   Troubleshooting steps:");
            log.error("   1. Check if KMS API is enabled in Google Cloud Console");
            log.error("   2. Verify the key exists: {}/{}/{}", kmsLocation, keyRingName, keyName);
            log.error("   3. Check service account has 'Cloud KMS CryptoKey Encrypter/Decrypter' role");
            log.error("   4. Verify project ID matches: {}", projectId);

            if (e.getMessage() != null) {
                if (e.getMessage().contains("NOT_FOUND")) {
                    log.error("   → Key or key ring doesn't exist!");
                } else if (e.getMessage().contains("PERMISSION_DENIED")) {
                    log.error("   → Service account lacks KMS permissions!");
                } else if (e.getMessage().contains("UNAUTHENTICATED")) {
                    log.error("   → Invalid service account credentials!");
                }
            }

            // Don't fail startup, but warn severely
            log.warn("⚠️⚠️ CONTINUING WITHOUT KMS - TOKENS WILL BE UNENCRYPTED! ⚠️⚠️");
        }

        log.info("==========================================");
    }

    private void initializeKMSClient() throws IOException {
        log.info("🔑 Initializing KMS client with Firebase credentials...");

        GoogleCredentials credentials = getCredentials();

        // Extract service account email for logging
        if (credentials instanceof ServiceAccountCredentials) {
            ServiceAccountCredentials saCredentials = (ServiceAccountCredentials) credentials;
            log.info("   Using service account: {}", saCredentials.getClientEmail());
        }

        // Create KMS client with the same credentials
        KeyManagementServiceSettings settings = KeyManagementServiceSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();

        kmsClient = KeyManagementServiceClient.create(settings);
        log.info("   ✓ KMS client initialized");
    }

    private void testKMSOperations() throws Exception {
        String testData = "test_instagram_token_" + System.currentTimeMillis();
        log.info("   Test data: '{}'", testData);

        // Test encryption
        log.info("   Testing encryption...");
        EncryptRequest encryptRequest = EncryptRequest.newBuilder()
                .setName(cryptoKeyName.toString())
                .setPlaintext(ByteString.copyFromUtf8(testData))
                .build();

        EncryptResponse encryptResponse = kmsClient.encrypt(encryptRequest);
        byte[] ciphertext = encryptResponse.getCiphertext().toByteArray();
        String encryptedBase64 = Base64.getEncoder().encodeToString(ciphertext);

        log.info("   ✓ Encryption successful");
        log.info("   Encrypted (base64): {}...",
                encryptedBase64.length() > 50 ? encryptedBase64.substring(0, 50) : encryptedBase64);
        log.info("   Encrypted size: {} bytes", ciphertext.length);

        // Test decryption
        log.info("   Testing decryption...");
        DecryptRequest decryptRequest = DecryptRequest.newBuilder()
                .setName(cryptoKeyName.toString())
                .setCiphertext(ByteString.copyFrom(ciphertext))
                .build();

        DecryptResponse decryptResponse = kmsClient.decrypt(decryptRequest);
        String decryptedData = decryptResponse.getPlaintext().toStringUtf8();

        log.info("   ✓ Decryption successful");
        log.info("   Decrypted: '{}'", decryptedData);

        // Verify round-trip
        if (!testData.equals(decryptedData)) {
            throw new BusinessRuleTranslatableException("error.business.data_integrity");
        }

        log.info("   ✓ Round-trip verification passed");

        // Calculate performance
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            kmsClient.encrypt(encryptRequest);
        }
        long avgTime = (System.currentTimeMillis() - startTime) / 5;
        log.info("   Average encryption time: {}ms", avgTime);
    }

    // Reuse the same credential logic as Firebase
    private GoogleCredentials getCredentials() throws IOException {
        // Try base64 property first
        if (StringUtils.hasText(serviceAccountJsonBase64)) {
            String jsonContent = serviceAccountJsonBase64;

            // Decode if base64
            if (isBase64(serviceAccountJsonBase64)) {
                byte[] decodedBytes = Base64.getDecoder().decode(serviceAccountJsonBase64);
                jsonContent = new String(decodedBytes, StandardCharsets.UTF_8);
            }

            try (InputStream credentialsStream = new ByteArrayInputStream(
                    jsonContent.getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(credentialsStream);
            }
        }

        // Fall back to file
        if (serviceAccountFile != null && serviceAccountFile.exists()) {
            try (InputStream credentialsStream = serviceAccountFile.getInputStream()) {
                return GoogleCredentials.fromStream(credentialsStream);
            }
        }

        // Try default credentials
        return GoogleCredentials.getApplicationDefault();
    }

    private boolean isBase64(String str) {
        if (str == null || str.isEmpty() || str.trim().startsWith("{")) {
            return false;
        }

        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            // Try with padding
            if (str.length() > 100) {
                try {
                    int paddingNeeded = 4 - (str.length() % 4);
                    if (paddingNeeded < 4) {
                        String paddedStr = str + "=".repeat(paddingNeeded);
                        Base64.getDecoder().decode(paddedStr);
                        return true;
                    }
                } catch (IllegalArgumentException ex) {
                    return false;
                }
            }
            return false;
        }
    }

    /**
     * Encrypt a token using KMS
     */
    public String encryptToken(String plaintext) {
        if (!kmsEnabled || kmsClient == null) {
            throw new IllegalStateException("KMS not available for token encryption");
        }

        try {
            EncryptRequest request = EncryptRequest.newBuilder()
                    .setName(cryptoKeyName.toString())
                    .setPlaintext(ByteString.copyFromUtf8(plaintext))
                    .build();

            EncryptResponse response = kmsClient.encrypt(request);
            return Base64.getEncoder().encodeToString(response.getCiphertext().toByteArray());
        } catch (Exception e) {
            log.error("Failed to encrypt token: {}", e.getMessage());
            throw new AuthenticationTranslatableException("error.security.kms_encryption_failed");
        }
    }

    /**
     * Decrypt a token using KMS
     */
    public String decryptToken(String encryptedBase64) {
        if (!kmsEnabled || kmsClient == null) {
            throw new IllegalStateException("KMS not available for token decryption");
        }

        try {
            byte[] ciphertext = Base64.getDecoder().decode(encryptedBase64);

            DecryptRequest request = DecryptRequest.newBuilder()
                    .setName(cryptoKeyName.toString())
                    .setCiphertext(ByteString.copyFrom(ciphertext))
                    .build();

            DecryptResponse response = kmsClient.decrypt(request);
            return response.getPlaintext().toStringUtf8();
        } catch (Exception e) {
            log.error("Failed to decrypt token: {}", e.getMessage());
            throw new AuthenticationTranslatableException("error.security.kms_decryption_failed");
        }
    }

    /**
     * Encrypt a TOTP secret using the dedicated TOTP KMS key
     */
    public String encryptTotpSecret(String secret) {
        if (!kmsEnabled || kmsClient == null) {
            throw new IllegalStateException("KMS not available for TOTP encryption");
        }

        try {
            EncryptRequest request = EncryptRequest.newBuilder()
                    .setName(totpCryptoKeyName.toString())
                    .setPlaintext(ByteString.copyFromUtf8(secret))
                    .build();

            EncryptResponse response = kmsClient.encrypt(request);
            return Base64.getEncoder().encodeToString(response.getCiphertext().toByteArray());
        } catch (Exception e) {
            log.error("Failed to encrypt TOTP secret: {}", e.getMessage());
            throw new AuthenticationTranslatableException("error.security.totp_encryption_failed");
        }
    }

    /**
     * Decrypt a TOTP secret using the dedicated TOTP KMS key
     */
    public String decryptTotpSecret(String encryptedBase64) {
        if (!kmsEnabled || kmsClient == null) {
            throw new IllegalStateException("KMS not available for TOTP decryption");
        }

        try {
            byte[] ciphertext = Base64.getDecoder().decode(encryptedBase64);

            DecryptRequest request = DecryptRequest.newBuilder()
                    .setName(totpCryptoKeyName.toString())
                    .setCiphertext(ByteString.copyFrom(ciphertext))
                    .build();

            DecryptResponse response = kmsClient.decrypt(request);
            return response.getPlaintext().toStringUtf8();
        } catch (Exception e) {
            log.error("Failed to decrypt TOTP secret: {}", e.getMessage());
            throw new AuthenticationTranslatableException("error.security.totp_decryption_failed");
        }
    }

    public void shutdown() {
        if (kmsClient != null) {
            kmsClient.close();
        }
    }
}
