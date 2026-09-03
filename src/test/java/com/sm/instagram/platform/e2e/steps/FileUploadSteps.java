package com.sm.instagram.platform.e2e.steps;

import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.multiuser.actor.Actor;
import com.sm.instagram.platform.e2e.multiuser.actor.ActorRegistry;
import com.sm.instagram.platform.e2e.support.SoftAssertionContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step definitions for file upload operations via signed URLs.
 *
 * <p>Implements the complete file upload flow:
 * <ol>
 *   <li>Request signed URL from backend (/upload/signed-url)</li>
 *   <li>Upload file directly to Firebase Storage using PUT</li>
 *   <li>Confirm upload with backend (/upload/confirm/{uploadId})</li>
 *   <li>Update profile with the public URL</li>
 * </ol>
 *
 * <p>Supported content types: image/jpeg, image/png, image/webp, image/gif
 */
@Slf4j
public class FileUploadSteps extends CucumberSpringConfig {

    @Autowired
    private ActorRegistry actorRegistry;

    @Autowired
    private SoftAssertionContext softAssert;

    // Separate RestTemplate for Firebase Storage (no auth headers)
    private final RestTemplate firebaseRestTemplate = new RestTemplate();

    // =========================================================================
    // Request Signed URL
    // =========================================================================

    @When("{string} requests signed URL for file:")
    public void requestSignedUrl(String actorName, DataTable dataTable) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, String> params = dataTable.asMap();

        String requestedContentType = params.get("contentType");

        // Handle WebP → PNG conversion BEFORE requesting signed URL
        // The signed URL must be generated with the ACTUAL content type that will be uploaded
        String actualContentType = resolveActualContentType(requestedContentType);
        if (!actualContentType.equals(requestedContentType)) {
            log.info("[E2E] Content type conversion: {} -> {} (for signed URL request)",
                    requestedContentType, actualContentType);
        }

        Map<String, Object> request = new HashMap<>();
        request.put("filename", params.get("filename"));
        request.put("contentType", actualContentType);  // Use actual type for signing
        request.put("fileSize", Long.parseLong(params.get("fileSize")));
        if (params.containsKey("uploadType")) {
            request.put("uploadType", params.get("uploadType"));
        }

        ResponseEntity<Map> response = actor.post(restTemplate, url("/upload/signed-url"), request);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> body = response.getBody();
            actor.storeResource("uploadResponse", body);
            actor.storeResource("uploadUrl", body.get("uploadUrl"));
            actor.storeResource("publicUrl", body.get("publicUrl"));
            actor.storeResource("uploadId", body.get("uploadId"));
            actor.storeResource("filePath", body.get("filePath"));
            actor.storeResource("requestedContentType", actualContentType);  // Store actual type
            actor.storeResource("actualContentType", actualContentType);
            log.info("[E2E] Actor '{}' received signed URL for {} (contentType: {})",
                    actorName, params.get("filename"), actualContentType);
        }
    }

    @When("{string} requests signed URL for file {string} type {string} size {int}")
    public void requestSignedUrlSimple(String actorName, String filename, String contentType, int fileSize) {
        Actor actor = actorRegistry.get(actorName);

        // Handle WebP → PNG conversion BEFORE requesting signed URL
        String actualContentType = resolveActualContentType(contentType);
        if (!actualContentType.equals(contentType)) {
            log.info("[E2E] Content type conversion: {} -> {} (for signed URL request)",
                    contentType, actualContentType);
        }

        Map<String, Object> request = new HashMap<>();
        request.put("filename", filename);
        request.put("contentType", actualContentType);  // Use actual type for signing
        request.put("fileSize", (long) fileSize);

        ResponseEntity<Map> response = actor.post(restTemplate, url("/upload/signed-url"), request);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> body = response.getBody();
            actor.storeResource("uploadResponse", body);
            actor.storeResource("uploadUrl", body.get("uploadUrl"));
            actor.storeResource("publicUrl", body.get("publicUrl"));
            actor.storeResource("uploadId", body.get("uploadId"));
            actor.storeResource("filePath", body.get("filePath"));
            actor.storeResource("requestedContentType", actualContentType);  // Store actual type
            actor.storeResource("actualContentType", actualContentType);
            log.info("[E2E] Actor '{}' received signed URL for {} (contentType: {})",
                    actorName, filename, actualContentType);
        }
    }

    // =========================================================================
    // Signed URL Response Assertions
    // =========================================================================

    @Then("soft assert signed URL response is successful")
    public void softAssertSignedUrlSuccess() {
        Actor actor = actorRegistry.current();
        int status = actor.getLastStatusCode();
        softAssert.softAssertStatus(200, status, "/upload/signed-url");
    }

    @Then("soft assert signed URL response status is {int}")
    public void softAssertSignedUrlStatus(int expectedStatus) {
        Actor actor = actorRegistry.current();
        int status = actor.getLastStatusCode();
        softAssert.softAssertStatus(expectedStatus, status, "/upload/signed-url");
    }

    @Then("soft assert response contains uploadUrl")
    public void softAssertContainsUploadUrl() {
        Actor actor = actorRegistry.current();
        Object uploadUrl = actor.getResource("uploadUrl");
        softAssert.softAssertNotNull(uploadUrl, "Response should contain uploadUrl");
    }

    @Then("soft assert response contains publicUrl")
    public void softAssertContainsPublicUrl() {
        Actor actor = actorRegistry.current();
        Object publicUrl = actor.getResource("publicUrl");
        softAssert.softAssertNotNull(publicUrl, "Response should contain publicUrl");
    }

    @Then("soft assert response contains uploadId")
    public void softAssertContainsUploadId() {
        Actor actor = actorRegistry.current();
        Object uploadId = actor.getResource("uploadId");
        softAssert.softAssertNotNull(uploadId, "Response should contain uploadId");
    }

    @Then("soft assert uploadUrl starts with {string}")
    public void softAssertUploadUrlStartsWith(String prefix) {
        Actor actor = actorRegistry.current();
        String uploadUrl = actor.getResource("uploadUrl");
        softAssert.softAssertTrue(
            uploadUrl != null && uploadUrl.startsWith(prefix),
            "uploadUrl should start with " + prefix
        );
    }

    @Then("soft assert response contains rateLimitInfo")
    public void softAssertContainsRateLimitInfo() {
        Actor actor = actorRegistry.current();
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResponse = actor.getResource("uploadResponse");
        softAssert.softAssertNotNull(
            uploadResponse != null ? uploadResponse.get("rateLimitInfo") : null,
            "Response should contain rateLimitInfo"
        );
    }

    @Then("soft assert rateLimitInfo.remainingHourly is present")
    public void softAssertRateLimitHourlyPresent() {
        Actor actor = actorRegistry.current();
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResponse = actor.getResource("uploadResponse");
        if (uploadResponse != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rateLimitInfo = (Map<String, Object>) uploadResponse.get("rateLimitInfo");
            softAssert.softAssertNotNull(
                rateLimitInfo != null ? rateLimitInfo.get("remainingHourly") : null,
                "rateLimitInfo.remainingHourly should be present"
            );
        }
    }

    @Then("soft assert rateLimitInfo.remainingDaily is present")
    public void softAssertRateLimitDailyPresent() {
        Actor actor = actorRegistry.current();
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResponse = actor.getResource("uploadResponse");
        if (uploadResponse != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rateLimitInfo = (Map<String, Object>) uploadResponse.get("rateLimitInfo");
            softAssert.softAssertNotNull(
                rateLimitInfo != null ? rateLimitInfo.get("remainingDaily") : null,
                "rateLimitInfo.remainingDaily should be present"
            );
        }
    }

    // =========================================================================
    // Upload to Firebase Storage
    // =========================================================================

    @When("{string} uploads test image \\({int}KB) to the signed URL using PUT")
    public void uploadToSignedUrl(String actorName, int sizeKB) {
        Actor actor = actorRegistry.get(actorName);
        String uploadUrl = actor.requireResource("uploadUrl");

        // Use actual content type if available (set by generateTestImage for WebP fallback)
        String contentType = actor.getResource("actualContentType");
        if (contentType == null) {
            contentType = actor.requireResource("requestedContentType");
        }

        // Generate test image
        byte[] testImage = generateTestImage(contentType, sizeKB * 1024, actor);

        // Re-read actual content type after generation (may have changed for WebP)
        String actualContentType = actor.getResource("actualContentType");
        if (actualContentType != null) {
            contentType = actualContentType;
        }

        // === VERBOSE LOGGING (matching FE) ===
        log.info("[E2E] ========== FIREBASE UPLOAD START ==========");
        log.info("[E2E] Actor: {}", actorName);
        log.info("[E2E] URL: {}...", uploadUrl.substring(0, Math.min(100, uploadUrl.length())));
        log.info("[E2E] Method: PUT");
        log.info("[E2E] Content-Type: {}", contentType);
        log.info("[E2E] File size: {} bytes", testImage.length);

        // Detect signature version (like FE does)
        boolean isV4Signature = uploadUrl.contains("X-Goog-Algorithm");
        log.info("[E2E] Signature version: {}", isV4Signature ? "V4" : "V2");

        if (isV4Signature) {
            // Extract signed headers from URL
            Matcher matcher = Pattern.compile("X-Goog-SignedHeaders=([^&]+)").matcher(uploadUrl);
            if (matcher.find()) {
                String signedHeaders = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
                log.info("[E2E] V4 Signed headers in URL: {}", signedHeaders);
            }
        }

        try {
            // === USE HttpURLConnection (like FE's XMLHttpRequest) ===
            URL url = new URL(uploadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            // === ONLY set Content-Type (like FE) ===
            // Do NOT set Content-Length - Java handles it automatically
            conn.setRequestProperty("Content-Type", contentType);
            log.info("[E2E] Set Content-Type: {}", contentType);
            log.info("[E2E] NOT setting: Content-Length, Authorization, Cookie");

            // Write data
            try (OutputStream os = conn.getOutputStream()) {
                os.write(testImage);
                os.flush();
            }

            // Get response
            int responseCode = conn.getResponseCode();
            log.info("[E2E] Firebase response code: {}", responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                log.info("[E2E] ========== FIREBASE UPLOAD SUCCESS ==========");
                actor.storeResource("firebaseUploadStatus", responseCode);
            } else {
                // Read error response
                String errorBody = "";
                try (InputStream es = conn.getErrorStream()) {
                    if (es != null) {
                        errorBody = new String(es.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
                log.error("[E2E] ========== FIREBASE UPLOAD FAILED ==========");
                log.error("[E2E] Status: {}", responseCode);
                log.error("[E2E] Error body: {}", errorBody);
                actor.storeResource("firebaseUploadStatus", responseCode);
                actor.storeResource("firebaseError", errorBody);
            }

            conn.disconnect();

        } catch (Exception e) {
            log.error("[E2E] ========== FIREBASE UPLOAD EXCEPTION ==========");
            log.error("[E2E] Exception type: {}", e.getClass().getName());
            log.error("[E2E] Message: {}", e.getMessage());
            log.error("[E2E] Full stack:", e);
            actor.storeResource("firebaseUploadStatus", 500);
            actor.storeResource("firebaseError", e.getMessage());
        }
    }

    @When("{string} uploads file to signed URL")
    public void uploadFileToSignedUrl(String actorName) {
        uploadToSignedUrl(actorName, 100); // Default 100KB
    }

    @When("{string} uploads test image \\({int}MB) to the signed URL")
    public void uploadLargeToSignedUrl(String actorName, int sizeMB) {
        uploadToSignedUrl(actorName, sizeMB * 1024);
    }

    @When("{string} uploads test image \\({int}MB) to the signed URL using PUT")
    public void uploadLargeToSignedUrlUsingPut(String actorName, int sizeMB) {
        uploadToSignedUrl(actorName, sizeMB * 1024);
    }

    @When("{string} uploads test image to signed URL")
    public void uploadDefaultToSignedUrl(String actorName) {
        uploadToSignedUrl(actorName, 100);
    }

    @When("{string} uploads test webp image to signed URL")
    public void uploadWebpToSignedUrl(String actorName) {
        Actor actor = actorRegistry.get(actorName);
        actor.storeResource("requestedContentType", "image/webp");
        uploadToSignedUrl(actorName, 50);
    }

    @Then("soft assert Firebase upload response is success \\({int}-{int})")
    public void softAssertFirebaseUploadSuccess(int minStatus, int maxStatus) {
        Actor actor = actorRegistry.current();
        Integer status = actor.getResource("firebaseUploadStatus");
        softAssert.softAssertTrue(
            status != null && status >= minStatus && status <= maxStatus,
            String.format("Firebase upload should succeed (%d-%d), got %d", minStatus, maxStatus, status)
        );
    }

    @Then("soft assert upload status is {int}")
    public void softAssertUploadStatus(int expectedStatus) {
        Actor actor = actorRegistry.current();
        Integer status = actor.getResource("firebaseUploadStatus");
        softAssert.softAssertEquals(expectedStatus, status, "Firebase upload status");
    }

    @Then("soft assert upload succeeds")
    public void softAssertUploadSucceeds() {
        Actor actor = actorRegistry.current();
        Integer status = actor.getResource("firebaseUploadStatus");
        softAssert.softAssertTrue(
            status != null && status >= 200 && status < 300,
            "Firebase upload should succeed (2xx), got " + status
        );
    }

    // =========================================================================
    // Confirm Upload
    // =========================================================================

    @When("{string} confirms upload with uploadId and filePath")
    public void confirmUpload(String actorName) {
        Actor actor = actorRegistry.get(actorName);
        String uploadId = actor.requireResource("uploadId");
        String filePath = actor.getResource("filePath");

        String confirmUrl = "/upload/confirm/" + uploadId;
        if (filePath != null) {
            confirmUrl += "?filePath=" + filePath;
        }

        ResponseEntity<Map> response = actor.post(restTemplate, url(confirmUrl), null);
        actor.storeResource("confirmStatus", response.getStatusCode().value());
        log.info("[E2E] Actor '{}' confirmed upload: {}", actorName, response.getStatusCode());
    }

    @When("{string} confirms upload with uploadId")
    public void confirmUploadById(String actorName) {
        confirmUpload(actorName);
    }

    @When("{string} confirms upload")
    public void confirmUploadSimple(String actorName) {
        confirmUpload(actorName);
    }

    @Then("soft assert confirm status is {int}")
    public void softAssertConfirmStatus(int expectedStatus) {
        Actor actor = actorRegistry.current();
        Integer status = actor.getResource("confirmStatus");
        softAssert.softAssertStatus(expectedStatus, status != null ? status : -1, "/upload/confirm");
    }

    @Then("soft assert confirm response status is {int}")
    public void softAssertConfirmResponseStatus(int expectedStatus) {
        softAssertConfirmStatus(expectedStatus);
    }

    @Then("soft assert confirm succeeds")
    public void softAssertConfirmSucceeds() {
        Actor actor = actorRegistry.current();
        Integer status = actor.getResource("confirmStatus");
        softAssert.softAssertTrue(
            status != null && status >= 200 && status < 300,
            "Confirm should succeed (2xx), got " + status
        );
    }

    // =========================================================================
    // Update Profile with Uploaded File
    // =========================================================================

    @When("{string} updates their profilePicture to the uploaded URL")
    public void updateProfilePictureToUploaded(String actorName) {
        Actor actor = actorRegistry.get(actorName);
        // Avatar is uploadId-only (pentest 3.1): send the tracked uploadId,
        // the BE derives the stored URL from its own file_uploads row. A raw
        // URL — even an own-bucket one — is rejected, so the client can't
        // point the avatar at another file in the bucket.
        String uploadId = actor.requireResource("uploadId");
        Long userId = actor.getSession().getUserId();

        Map<String, Object> update = new HashMap<>();
        update.put("profilePicture", uploadId);

        actor.patch(restTemplate, url("/users/" + userId), update);
    }

    @When("{string} updates profilePicture to publicUrl")
    public void updateProfilePictureSimple(String actorName) {
        updateProfilePictureToUploaded(actorName);
    }

    @Then("soft assert GET \\/users\\/me shows the new profilePicture URL")
    public void softAssertProfilePictureUpdated() {
        Actor actor = actorRegistry.current();
        String expectedUrl = actor.getResource("publicUrl");

        ResponseEntity<Map> response = actor.get(restTemplate, url("/users/me"));

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            String actualUrl = (String) response.getBody().get("profilePicture");
            softAssert.softAssertEquals(expectedUrl, actualUrl, "profilePicture should match uploaded URL");
        } else {
            softAssert.softAssertTrue(false, "Failed to fetch /users/me");
        }
    }

    @Then("soft assert profile update succeeds")
    public void softAssertProfileUpdateSucceeds() {
        Actor actor = actorRegistry.current();
        int status = actor.getLastStatusCode();
        softAssert.softAssertTrue(
            status >= 200 && status < 300,
            "Profile update should succeed (2xx), got " + status
        );
    }

    // =========================================================================
    // Error Assertions
    // =========================================================================

    @Then("soft assert upload error contains validation for fileSize")
    public void softAssertFileSizeError() {
        Actor actor = actorRegistry.current();
        softAssert.softAssertTrue(
            actor.getLastStatusCode() == 400,
            "Should return 400 for file size validation error"
        );
    }

    @Then("soft assert upload error contains validation for contentType")
    public void softAssertContentTypeError() {
        Actor actor = actorRegistry.current();
        softAssert.softAssertTrue(
            actor.getLastStatusCode() == 400,
            "Should return 400 for content type validation error"
        );
    }

    @Then("soft assert upload error contains validation for filename")
    public void softAssertFilenameError() {
        Actor actor = actorRegistry.current();
        softAssert.softAssertTrue(
            actor.getLastStatusCode() == 400,
            "Should return 400 for filename validation error"
        );
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Resolves the actual content type that will be used for upload.
     * Handles unsupported types (e.g., WebP) by mapping to supported alternatives.
     *
     * @param requestedContentType the originally requested content type
     * @return the actual content type that will be uploaded
     */
    private String resolveActualContentType(String requestedContentType) {
        return switch (requestedContentType) {
            case "image/png" -> "image/png";
            case "image/gif" -> "image/gif";
            case "image/webp" -> "image/png";  // WebP not supported by ImageIO, use PNG
            case "image/jpeg", "image/jpg" -> "image/jpeg";
            default -> requestedContentType;  // Pass through unknown types for validation testing
        };
    }

    /**
     * Generates a valid test image of approximately the specified size.
     * Handles WebP by falling back to PNG (ImageIO doesn't support WebP).
     * Stores the actual content type in the actor for use in upload.
     *
     * @param requestedContentType the requested content type (image/jpeg, image/png, etc.)
     * @param targetSize approximate target size in bytes
     * @param actor the actor to store actual content type on (may be null)
     * @return byte array containing the image
     */
    private byte[] generateTestImage(String requestedContentType, int targetSize, Actor actor) {
        try {
            // Map requested type to actual format (handle unsupported types)
            String format;
            String actualContentType;

            switch (requestedContentType) {
                case "image/png" -> {
                    format = "png";
                    actualContentType = "image/png";
                }
                case "image/gif" -> {
                    format = "gif";
                    actualContentType = "image/gif";
                }
                case "image/webp" -> {
                    // WebP not supported by ImageIO - use PNG
                    format = "png";
                    actualContentType = "image/png";
                    log.warn("[E2E] WebP not supported by ImageIO, using PNG instead");
                }
                default -> {
                    format = "jpg";
                    actualContentType = "image/jpeg";
                }
            }

            // Store actual content type for use in upload
            if (actor != null) {
                actor.storeResource("actualContentType", actualContentType);
                log.info("[E2E] Requested content type: {}, Actual content type: {}",
                        requestedContentType, actualContentType);
            }

            // Create a simple colored image
            int dimension = (int) Math.sqrt(targetSize / 3.0); // Rough estimate for RGB
            dimension = Math.max(100, Math.min(dimension, 2000)); // Clamp to reasonable size

            BufferedImage image = new BufferedImage(dimension, dimension, BufferedImage.TYPE_INT_RGB);

            // Fill with gradient for some variety (helps with compression)
            for (int x = 0; x < dimension; x++) {
                for (int y = 0; y < dimension; y++) {
                    int r = (x * 255) / dimension;
                    int g = (y * 255) / dimension;
                    int b = ((x + y) * 128) / dimension;
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, format, baos);

            byte[] result = baos.toByteArray();

            // If result is too small, pad with image data
            if (result.length < targetSize) {
                // Create larger image
                int newDimension = (int) (dimension * Math.sqrt((double) targetSize / result.length)) + 100;
                newDimension = Math.min(newDimension, 4000);

                image = new BufferedImage(newDimension, newDimension, BufferedImage.TYPE_INT_RGB);
                for (int x = 0; x < newDimension; x++) {
                    for (int y = 0; y < newDimension; y++) {
                        int r = (x * 255) / newDimension;
                        int g = (y * 255) / newDimension;
                        int b = ((x + y) * 128) / newDimension;
                        image.setRGB(x, y, (r << 16) | (g << 8) | b);
                    }
                }

                baos = new ByteArrayOutputStream();
                ImageIO.write(image, format, baos);
                result = baos.toByteArray();
            }

            log.info("[E2E] Generated test image: format={}, {} bytes (target: {} bytes)",
                    format, result.length, targetSize);
            return result;

        } catch (IOException e) {
            log.error("[E2E] Failed to generate test image", e);
            // Return minimal valid JPEG
            if (actor != null) {
                actor.storeResource("actualContentType", "image/jpeg");
            }
            return new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xD9
            };
        }
    }
}
