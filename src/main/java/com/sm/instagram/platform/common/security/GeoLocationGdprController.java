package com.sm.instagram.platform.common.security;

import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for GDPR operations on location data.
 * <p>
 * Provides endpoints for:
 * - Data deletion (Right to erasure)
 * - Data export (Right to data portability)
 * - Compliance status
 * <p>
 * All endpoints require admin authentication.
 */
@RestController
@RequestMapping("/gdpr/location")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "geoip.gdpr.enabled", havingValue = "true")
@ConditionalOnBean(GeoLocationGdprService.class)  // Only create if GDPR service exists
public class GeoLocationGdprController {

    private final GeoLocationGdprService gdprService;

    /**
     * Delete all location data for a specific IP address.
     * GDPR Article 17 - Right to erasure.
     */
    @DeleteMapping("/ip/{ip}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteIpData(@PathVariable String ip) {
        String firebaseUid = getAuthenticatedAdminUid();


        log.warn("GDPR: DELETION Operation=deleteIpLocationData, FirebaseUID={}, AdminAction=true, IP={}, Purpose=gdpr_erasure",
                firebaseUid, anonymizeIp(ip));

        int deleted = gdprService.deleteIpData(ip);

        log.info("GDPR: DELETION_COMPLETE FirebaseUID={}, IP={}, RecordsDeleted={}, GDPRArticle=17",
                firebaseUid, anonymizeIp(ip), deleted);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "deleted", deleted,
                "ip", ip,
                "gdprArticle", "17"
        ));
    }

    /**
     * Delete all location data for a specific user.
     * GDPR Article 17 - Right to erasure.
     *
     * SECURITY: Users can only delete their OWN data (userId must match JWT userId claim).
     * Admins can delete any user's data.
     */
    @DeleteMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('ADMIN') or #userId.toString().equals(authentication.details['userId'].toString())")
    public ResponseEntity<Map<String, Object>> deleteUserData(@PathVariable Long userId) {
        String firebaseUid = getAuthenticatedAdminUid();


        log.warn("GDPR: DELETION Operation=deleteUserLocationData, FirebaseUID={}, TargetUserID={}, Purpose=gdpr_erasure",
                firebaseUid, userId);

        int deleted = gdprService.deleteUserLocationData(userId);

        log.info("GDPR: DELETION_COMPLETE FirebaseUID={}, TargetUserID={}, RecordsDeleted={}, GDPRArticle=17",
                firebaseUid, userId, deleted);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "deleted", deleted,
                "userId", userId,
                "gdprArticle", "17"
        ));
    }

    /**
     * Export all location data for a user.
     * GDPR Article 20 - Right to data portability.
     *
     * SECURITY: Users can only export their OWN data (userId must match JWT userId claim).
     * Admins can export any user's data.
     */
    @GetMapping("/export/user/{userId}")
    @PreAuthorize("hasAuthority('ADMIN') or #userId.toString().equals(authentication.details['userId'].toString())")
    public ResponseEntity<Map<String, Object>> exportUserData(@PathVariable Long userId) {
        String firebaseUid = getAuthenticatedAdminUid();


        log.info("GDPR: Operation=exportUserLocationData, FirebaseUID={}, TargetUserID={}, Purpose=gdpr_data_portability",
                firebaseUid, userId);

        Map<String, Object> data = gdprService.exportUserLocationData(userId);

        return ResponseEntity.ok(data);
    }

    /**
     * Get data retention summary for a user.
     *
     * SECURITY: Users can only view their OWN retention summary (userId must match JWT userId claim).
     * Admins can view any user's summary.
     */
    @GetMapping("/retention/user/{userId}")
    @PreAuthorize("hasAuthority('ADMIN') or #userId.toString().equals(authentication.details['userId'].toString())")
    public ResponseEntity<Map<String, Object>> getRetentionSummary(@PathVariable Long userId) {
        String firebaseUid = getAuthenticatedAdminUid();


        log.info("GDPR: Operation=getRetentionSummary, FirebaseUID={}, TargetUserID={}, Purpose=gdpr_transparency",
                firebaseUid, userId);

        Map<String, Object> summary = gdprService.getDataRetentionSummary(userId);

        return ResponseEntity.ok(summary);
    }

    /**
     * Get GDPR compliance status.
     * Shows implemented features and compliance level.
     */
    @GetMapping("/compliance")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> getComplianceStatus() {
        String firebaseUid = getAuthenticatedAdminUid();


        log.info("GDPR: Operation=getComplianceStatus, FirebaseUID={}, AdminAction=true, Purpose=compliance_monitoring",
                firebaseUid);

        Map<String, Object> status = gdprService.getComplianceStatus();

        return ResponseEntity.ok(status);
    }

    /**
     * Anonymize old location data.
     * Implements data minimization principle.
     */
    @PostMapping("/anonymize")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> anonymizeOldData(
            @RequestParam(defaultValue = "30") int olderThanDays) {

        String firebaseUid = getAuthenticatedAdminUid();


        log.warn("GDPR: Operation=anonymizeLocationData, FirebaseUID={}, AdminAction=true, OlderThanDays={}, Purpose=data_minimization",
                firebaseUid, olderThanDays);

        int anonymized = gdprService.anonymizeOldData(olderThanDays);

        log.info("GDPR: Operation=anonymize_complete, FirebaseUID={}, RecordsAnonymized={}, GDPRPrinciple=DataMinimization",
                firebaseUid, anonymized);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "anonymized", anonymized,
                "olderThanDays", olderThanDays,
                "gdprPrinciple", "Data Minimization"
        ));
    }

    /**
     * Anonymize IP address for logging
     */
    private String anonymizeIp(String ip) {
        if (ip == null) return "unknown";
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".xxx.xxx";
        }
        return "xxx.xxx.xxx.xxx";
    }

    // ===== HELPER METHODS =====

    /**
     * Gets the authenticated admin UID from the security context.
     *
     * @return The Firebase UID of the authenticated admin user
     * @throws ResourceNotFoundException if authentication context is missing
     */
    private String getAuthenticatedAdminUid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            log.error("Authentication not available in security context");
            throw new AuthenticationTranslatableException("error.auth.not_authenticated");
        }
        return auth.getPrincipal().toString();
    }
}
