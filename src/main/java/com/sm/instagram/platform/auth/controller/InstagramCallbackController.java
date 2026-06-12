package com.sm.instagram.platform.auth.controller;

import com.sm.instagram.platform.auth.dto.DataDeletionResponse;
import com.sm.instagram.platform.auth.dto.DeletionStatusResponse;
import com.sm.instagram.platform.auth.entity.PendingDataDeletionRequest;
import com.sm.instagram.platform.auth.repository.PendingDataDeletionRequestRepository;
import com.sm.instagram.platform.auth.service.InstagramDataDeletionService;
import com.sm.instagram.platform.auth.service.InstagramDeauthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Handles Meta Instagram callbacks for deauthorization and data deletion.
 * All endpoints are public (covered by /auth/** permitAll).
 * Meta always expects HTTP 200 responses.
 */
@Slf4j
@RestController
@RequestMapping("/auth/instagram")
@RequiredArgsConstructor
public class InstagramCallbackController {

    private final InstagramDeauthorizationService deauthorizationService;
    private final InstagramDataDeletionService dataDeletionService;
    private final PendingDataDeletionRequestRepository deletionRequestRepository;

    @Value("${frontend.url:https://localhost:4200}")
    private String frontendUrl;

    /**
     * Meta deauthorization callback.
     * Called when a user removes our app from their Instagram settings.
     */
    @PostMapping(value = "/deauthorize", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> handleDeauthorization(
            @RequestParam("signed_request") String signedRequest) {
        try {
            log.info("GDPR: Received Instagram deauthorization callback");
            deauthorizationService.processDeauthorization(signedRequest);
        } catch (Exception e) {
            log.error("GDPR: Error processing deauthorization callback: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Meta data deletion callback.
     * Called when a user requests deletion of their data via Facebook/Instagram settings.
     * Must return { url, confirmation_code } JSON.
     */
    @PostMapping(value = "/data-deletion", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<DataDeletionResponse> handleDataDeletion(
            @RequestParam("signed_request") String signedRequest) {
        try {
            log.info("GDPR: Received Instagram data deletion callback");
            DataDeletionResponse response = dataDeletionService.processDataDeletion(signedRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("GDPR: Error processing data deletion callback: {}", e.getMessage(), e);
            // Meta requires a valid response — return a dummy confirmation code
            String dummyCode = java.util.UUID.randomUUID().toString();
            return ResponseEntity.ok(new DataDeletionResponse(
                    frontendUrl + "/deletion-status?code=" + dummyCode,
                    dummyCode
            ));
        }
    }

    /**
     * Deletion status check endpoint.
     * Public page where users can check the status of their data deletion request.
     */
    @GetMapping("/deletion-status")
    public ResponseEntity<DeletionStatusResponse> getDeletionStatus(
            @RequestParam("code") String confirmationCode) {

        var requestOpt = deletionRequestRepository.findByConfirmationCode(confirmationCode);

        if (requestOpt.isEmpty()) {
            return ResponseEntity.ok(new DeletionStatusResponse(
                    confirmationCode,
                    "NOT_FOUND",
                    "No deletion request found for this confirmation code.",
                    null
            ));
        }

        PendingDataDeletionRequest request = requestOpt.get();

        String reason = null;
        if (request.getStatus().name().equals("PENDING")) {
            reason = "Active collaborations must complete before data can be deleted.";
        }

        String completedAt = request.getCompletedAt() != null
                ? request.getCompletedAt().toString()
                : null;

        return ResponseEntity.ok(new DeletionStatusResponse(
                request.getConfirmationCode(),
                request.getStatus().name(),
                reason,
                completedAt
        ));
    }
}
