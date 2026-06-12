package com.sm.instagram.platform.storage.controller;

import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.storage.service.FileTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.sm.instagram.platform.auth.filter.HmacUtils;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("webhooks")
public class WebhookController {

    private final FileTrackingService trackingService;

    @Value("${webhooks.firebase.secret:}")
    private String webhookSecret;

    @Autowired
    public WebhookController(FileTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    /**
     * Receives notifications from Firebase about storage events.
     * Think of this as our delivery notification system.
     */
    @PostMapping("/firebase/storage")
    public ResponseEntity<?> handleFirebaseStorageWebhook(
            @RequestHeader(value = "X-Firebase-Signature", required = false) String signature,
            @RequestBody Map<String, Object> payload) {

        log.info("GDPR: Operation=receiveStorageWebhook, FirebaseUID=WEBHOOK, DataAccessed=storage_event, Purpose=file_tracking");
        log.debug("Received Firebase storage webhook: {}", payload);

        try {
            // Step 1: Verify webhook signature (security check)
            if (webhookSecret != null && !webhookSecret.isEmpty() && !verifyWebhookSignature(signature, payload)) {
                log.warn("GDPR: Operation=rejectInvalidWebhook, FirebaseUID=UNKNOWN, DataAccessed=none, Purpose=security_validation");
                throw new BusinessRuleTranslatableException("error.business.insufficient_permissions");
            }

            // Step 2: Extract event type
            String eventType = (String) payload.get("eventType");
            if (eventType == null) {
                throw new ValidationTranslatableException("error.validation.missing_parameter", "eventType");
            }

            switch (eventType) {
                case "google.storage.object.finalize":
                    handleObjectFinalized(payload);
                    break;

                case "google.storage.object.delete":
                    handleObjectDeleted(payload);
                    break;

                case "google.storage.object.metadataUpdate":
                    handleMetadataUpdate(payload);
                    break;

                default:
                    log.debug("Ignoring event type: {}", eventType);
            }

            return ResponseEntity.ok().build();

        } catch (BusinessRuleTranslatableException | ValidationTranslatableException e) {
            // Re-throw validation/business errors - let handler return appropriate HTTP status
            throw e;
        } catch (com.sm.instagram.platform.common.exceptions.StorageTranslatableException e) {
            // Storage errors should be re-thrown to signal retry to Firebase
            throw e;
        }
        // No generic catch - let other exceptions propagate for proper error handling
    }

    /**
     * Handles successful file upload notifications.
     */
    @SuppressWarnings("unchecked")
    private void handleObjectFinalized(Map<String, Object> payload) {
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "data");
        }

        String objectName = (String) data.get("name");
        if (objectName == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "name");
        }

        Object sizeObj = data.get("size");
        if (sizeObj == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "size");
        }

        Long size = Long.parseLong(sizeObj.toString());
        String contentType = (String) data.get("contentType");
        Map<String, String> metadata = (Map<String, String>) data.get("metadata");

        log.info("GDPR: Operation=handleFileUpload, FirebaseUID={}, DataAccessed=file_metadata, Purpose=storage_tracking", 
            extractUserIdFromPath(objectName) != null ? extractUserIdFromPath(objectName) : "UNKNOWN");
        log.info("File uploaded: {} ({})", objectName, size);

        // Extract user ID from path (content/{userId}/...)
        String userId = extractUserIdFromPath(objectName);

        if (userId != null && metadata != null) {
            // Update tracking
            trackingService.confirmUploadViaWebhook(
                    objectName,
                    userId,
                    size,
                    contentType,
                    metadata
            );
        }
    }

    /**
     * Handles file deletion notifications.
     */
    @SuppressWarnings("unchecked")
    private void handleObjectDeleted(Map<String, Object> payload) {
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "data");
        }

        String objectName = (String) data.get("name");
        if (objectName == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "name");
        }

        String userId = extractUserIdFromPath(objectName);
        log.warn("GDPR: DELETION Operation=handleFileDeleted, FirebaseUID={}, DataAccessed=file_path, Purpose=storage_cleanup", 
            userId != null ? userId : "UNKNOWN");
        log.info("File deleted: {}", objectName);

        trackingService.markFileAsDeleted(objectName);
    }

    /**
     * Handles metadata update notifications.
     */
    @SuppressWarnings("unchecked")
    private void handleMetadataUpdate(Map<String, Object> payload) {
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "data");
        }

        String objectName = (String) data.get("name");
        if (objectName == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "name");
        }

        Map<String, String> metadata = (Map<String, String>) data.get("metadata");

        String userId = extractUserIdFromPath(objectName);
        log.info("GDPR: Operation=handleMetadataUpdate, FirebaseUID={}, DataAccessed=file_metadata, Purpose=metadata_sync", 
            userId != null ? userId : "UNKNOWN");
        log.info("Metadata updated for: {}", objectName);

        trackingService.updateFileMetadata(objectName, metadata);
    }

    /**
     * Verifies webhook signature to ensure it's from Firebase.
     * This prevents unauthorized webhook calls.
     */
    private boolean verifyWebhookSignature(String signature, Map<String, Object> payload) {
        if (signature == null || webhookSecret == null) {
            return false;
        }

        try {
            // Create HMAC signature
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKey);

            // Convert payload to string for signing
            String payloadString = payload.toString();
            byte[] hmac = mac.doFinal(payloadString.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = Base64.getEncoder().encodeToString(hmac);

            return HmacUtils.constantTimeEquals(signature, calculatedSignature);

        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }

    /**
     * Extracts user ID from file path.
     * Path format: content/{userId}/{timestamp}_{filename}
     */
    private String extractUserIdFromPath(String path) {
        if (path == null || !path.startsWith("content/")) {
            return null;
        }

        String[] parts = path.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }

        return null;
    }
}
