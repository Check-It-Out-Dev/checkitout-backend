package com.sm.instagram.platform.storage.service;

import com.sm.instagram.platform.common.util.Interrupts;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Startup validation service for Signed URL generation capability.
 * This service runs at application startup to validate:
 * - Service account credentials for URL signing
 * - Firebase Storage bucket accessibility
 * - Signed URL generation (upload and download)
 * - ACTUAL UPLOAD TEST to verify Firebase Storage rules
 * <p>
 * This is a DIAGNOSTIC service that performs a complete lifecycle test.
 */
@Slf4j
@Service
@ConditionalOnBean(Storage.class)
public class SignedUrlValidationService {

    // Base64 encoded 1x1 transparent PNG (67 bytes)
    private static final String TEST_IMAGE_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
    private final Storage storage;
    @Value("classpath:service-account.json")
    private Resource serviceAccountFile;
    @Value("${firebase.service.account.json.base64:}")
    private String serviceAccountJsonBase64;
    @Value("${gcp.project-id:check-it-out-47c50}")
    private String projectId;
    @Value("${gcp.bucket-name:check-it-out-47c50.firebasestorage.app}")
    private String bucketName;
    @Value("${file-upload.signed-url-expiration-minutes:5}")
    private int signedUrlExpirationMinutes;
    @Value("${file-upload.validation.test-actual-upload:true}")
    private boolean testActualUpload;
    private ServiceAccountCredentials serviceAccountCredentials;

    @Autowired
    public SignedUrlValidationService(@Qualifier("fileUploadStorage") Storage storage) {
        this.storage = storage;
    }

    @PostConstruct
    public void initialize() {
        log.info("GDPR: Operation=initializeSignedUrlValidation, Purpose=system_startup_validation, DataAccessed=none");

        log.info("==========================================");
        log.info("🔐 SIGNED URL VALIDATION SERVICE");
        log.info("==========================================");

        try {
            // Get service account credentials for signing
            GoogleCredentials credentials = getCredentials();

            if (credentials instanceof ServiceAccountCredentials) {
                this.serviceAccountCredentials = (ServiceAccountCredentials) credentials;
                log.info("   Service Account: {}", serviceAccountCredentials.getClientEmail());
                log.info("   Project ID: {}", serviceAccountCredentials.getProjectId());
                log.info("   Bucket: {}", bucketName);
                log.info("   URL Expiration: {} minutes", signedUrlExpirationMinutes);
                log.info("   Test Actual Upload: {}", testActualUpload);

                // Test signed URL generation AND actual upload
                testSignedUrlLifecycle();

                log.info("✅ Signed URL Validation Service initialized successfully");
            } else {
                log.warn("⚠️ Service account credentials not available");
                log.warn("   Signed URLs will not be available");
                log.warn("   Using application default credentials which don't support URL signing");
            }
        } catch (Exception e) {
            log.error("❌ Failed to initialize Signed URL Service", e);
            log.error("   Uploads may not work properly without signed URLs");
        }

        log.info("==========================================");
    }

    /**
     * Complete lifecycle test: Generate URL → Upload file → Verify → Delete
     */
    private void testSignedUrlLifecycle() {
        log.info("GDPR: Operation=testSignedUrlLifecycle, Purpose=system_validation, DataAccessed=test_data_only");
        log.info("\n🔬 SIGNED URL LIFECYCLE VALIDATION:");

        // Test 1: Service Account Validation
        log.info("\n   Test 1: Service Account for URL Signing");
        if (serviceAccountCredentials == null) {
            log.error("   ❌ FAILED: No service account credentials available");
            return;
        } else {
            log.info("   ✓ PASSED: Service account loaded");
            log.info("   → Email: {}", serviceAccountCredentials.getClientEmail());
        }

        // Test 2: Bucket Access
        log.info("\n   Test 2: Target Bucket Access");
        try {
            Bucket bucket = storage.get(bucketName);
            if (bucket != null && bucket.exists()) {
                log.info("   ✓ PASSED: Bucket '{}' exists", bucketName);
            } else {
                log.error("   ❌ FAILED: Bucket '{}' not found", bucketName);
                return;
            }
        } catch (Exception e) {
            log.error("GDPR: Operation=bucketVerification_failed, BucketName={}, Error={}, Purpose=system_validation", bucketName, e.getMessage());
            log.error("   ❌ FAILED: Could not verify bucket", e);
            return;
        }

        // Test 3: Generate Upload URL
        String testPath = "_validation/test_" + UUID.randomUUID() + ".png";
        log.info("\n   Test 3: Generate Upload URL");
        log.info("   → Test file path: {}", testPath);

        URL uploadUrl;
        try {
            BlobInfo testBlob = BlobInfo.newBuilder(BlobId.of(bucketName, testPath))
                    .setContentType("image/png")
                    .build();

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "image/png");

            uploadUrl = storage.signUrl(
                    testBlob,
                    signedUrlExpirationMinutes,
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                    Storage.SignUrlOption.withExtHeaders(headers),
                    Storage.SignUrlOption.withV4Signature(),
                    Storage.SignUrlOption.signWith(serviceAccountCredentials)
            );

            log.info("   ✓ PASSED: Upload URL generated");
            log.info("   → URL contains signature: {}", uploadUrl.toString().contains("X-Goog-Signature"));
            log.info("   → URL expires in {} minutes", signedUrlExpirationMinutes);

        } catch (Exception e) {
            log.error("GDPR: Operation=generateTestUrl_failed, Error={}, Purpose=system_validation", e.getMessage());
            log.error("   ❌ FAILED: Could not generate upload URL", e);
            return;
        }

        // Test 4: ACTUAL UPLOAD TEST
        if (testActualUpload) {
            log.info("\n   Test 4: ACTUAL FILE UPLOAD");
            log.info("   → Method: PUT");
            log.info("   → Content: 1x1 PNG (67 bytes)");
            log.info("   → Auth: Signed URL (no JWT/cookies)");

            try {
                // Decode test image
                byte[] imageBytes = Base64.getDecoder().decode(TEST_IMAGE_BASE64);
                log.info("   → Image size: {} bytes", imageBytes.length);

                // Perform PUT request to signed URL
                HttpURLConnection connection = (HttpURLConnection) uploadUrl.openConnection();
                connection.setRequestMethod("PUT");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "image/png");
                connection.setRequestProperty("Content-Length", String.valueOf(imageBytes.length));

                // IMPORTANT: No Authorization header, no cookies
                // Authentication is in the signed URL parameters

                log.info("   → Uploading to Firebase Storage...");
                connection.getOutputStream().write(imageBytes);
                connection.getOutputStream().close();

                int responseCode = connection.getResponseCode();
                log.info("   → Response code: {}", responseCode);

                if (responseCode == 200 || responseCode == 201) {
                    log.info("GDPR: Operation=testUpload_success, FilePath={}, FileSize={}, Purpose=system_validation", testPath, imageBytes.length);
                    log.info("   ✅ UPLOAD SUCCESSFUL!");
                    log.info("   → Firebase Storage accepted the signed URL upload");
                    log.info("   → Storage rules are correctly configured");
                } else {
                    log.error("   ❌ UPLOAD FAILED with status: {}", responseCode);

                    // Read error response
                    InputStream errorStream = connection.getErrorStream();
                    if (errorStream != null) {
                        byte[] errorBytes = errorStream.readAllBytes();
                        String errorMessage = new String(errorBytes, StandardCharsets.UTF_8);
                        log.error("   → Error response: {}", errorMessage);

                        if (responseCode == 403) {
                            log.error("   → 403 Forbidden: Firebase Storage rules are blocking the upload");
                            log.error("   → Check: Are rules checking for request.auth?");
                            log.error("   → Fix: Remove auth checks for /content path");
                            log.error("   → Deploy: firebase deploy --only storage:rules");
                        }
                    }
                }

                connection.disconnect();

            } catch (Exception e) {
                log.error("GDPR: Operation=testUpload_failed, Error={}, Purpose=system_validation", e.getMessage());
                log.error("   ❌ UPLOAD TEST FAILED", e);
                log.error("   → Error: {}", e.getMessage());
            }
        }

        // Test 5: Verify Upload
        if (testActualUpload) {
            log.info("\n   Test 5: Verify Uploaded File");
            try {
                Thread.sleep(1000); // Wait for eventual consistency

                Blob blob = storage.get(bucketName, testPath);
                if (blob != null && blob.exists()) {
                    log.info("   ✅ FILE VERIFIED in storage");
                    log.info("   → Size: {} bytes", blob.getSize());
                    log.info("   → Content-Type: {}", blob.getContentType());

                    // Test 6: Generate Download URL
                    log.info("\n   Test 6: Generate Download URL");
                    URL downloadUrl = storage.signUrl(
                            blob,
                            7,
                            TimeUnit.DAYS,
                            Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                            Storage.SignUrlOption.withV4Signature(),
                            Storage.SignUrlOption.signWith(serviceAccountCredentials)
                    );
                    log.info("   ✓ Download URL generated (valid 7 days)");

                    // Test 7: Cleanup
                    log.info("\n   Test 7: Cleanup Test File");
                    blob.delete();
                    log.info("   ✓ Test file deleted");

                } else {
                    log.error("   ❌ FILE NOT FOUND in storage after upload");
                    log.error("   → Upload may have failed silently");
                }
            } catch (Exception e) {
                // A broad catch swallows the interrupt too; put the flag back before handling the failure.
                Interrupts.preserveInterrupt(e);
                log.error("   ❌ VERIFICATION FAILED", e);
            }
        }

        // Summary
        log.info("\n📊 SIGNED URL LIFECYCLE SUMMARY:");
        log.info("   ════════════════════════════════");
        if (testActualUpload) {
            log.info("   Complete lifecycle test performed");
            log.info("   Check logs above for results");
        } else {
            log.info("   Only URL generation tested");
            log.info("   Set file-upload.validation.test-actual-upload=true");
            log.info("   to test actual uploads");
        }
        log.info("\n   ⚠️ IMPORTANT:");
        log.info("   Signed URLs do NOT require JWT tokens!");
        log.info("   Authentication is via URL signature only");
        log.info("   No user login needed for signed URL uploads");
    }

    /**
     * Get validation status
     */
    public boolean isFullyOperational() {
        return serviceAccountCredentials != null && storage != null;
    }

    /**
     * Get detailed validation report
     */
    public Map<String, Object> getValidationReport() {
        Map<String, Object> report = new HashMap<>();
        report.put("storageConfigured", storage != null);
        report.put("credentialsAvailable", serviceAccountCredentials != null);
        report.put("bucketName", bucketName);
        report.put("projectId", projectId);
        report.put("testActualUpload", testActualUpload);

        if (serviceAccountCredentials != null) {
            report.put("serviceAccount", serviceAccountCredentials.getClientEmail());
        }

        return report;
    }

    /**
     * Run manual validation - can be called from actuator endpoint
     */
    public void runValidation() {
        log.info("GDPR: Operation=manualValidation, Purpose=system_diagnostics, DataAccessed=test_data_only");
        log.info("\n🔄 Running manual validation...");
        testSignedUrlLifecycle();
    }

    /**
     * Get Google credentials from service-account.json
     */
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
            return false;
        }
    }
}
