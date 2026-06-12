package com.sm.instagram.platform.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GDPR-compliant controller for managing rate limit data.
 * Provides endpoints for users to access and delete their rate limit data.
 */
@RestController
@RequestMapping("/privacy/rate-limit")
@PreAuthorize("isAuthenticated()")
@Slf4j
@RequiredArgsConstructor
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
public class RateLimitPrivacyController {

    // Constants for duplicated strings
    private static final String DESCRIPTION_KEY = "description";
    private static final String ENDPOINT_KEY = "endpoint";
    private static final String EXPORT_ENDPOINT = "GET /privacy/rate-limit/export";
    private static final String DELETE_ENDPOINT = "DELETE /privacy/rate-limit";
    private static final String INFO_ENDPOINT = "GET /privacy/rate-limit/info";
    private final GdprCompliantRateLimiterService rateLimiterService;

    /**
     * Export user's rate limit data (GDPR right to access).
     * Returns all rate limit violations and related data for the authenticated user.
     */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> exportMyRateLimitData(Authentication authentication) {
        String userId = authentication.getName();
        log.info("User {} requested rate limit data export", userId);

        Map<String, Object> data = rateLimiterService.exportUserRateLimitData(userId);
        return ResponseEntity.ok(data);
    }

    /**
     * Delete user's rate limit AUDIT data (GDPR right to erasure).
     * NOTE: This only removes audit logs. Active rate limits remain in effect
     * to prevent abuse of the system.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteMyRateLimitData(Authentication authentication) {
        String userId = authentication.getName();
        log.info("User {} requested rate limit audit data deletion", userId);

        rateLimiterService.deleteUserRateLimitData(userId);

        return ResponseEntity.ok(Map.of(
                "message", "Rate limit audit data deleted successfully",
                "userId", userId,
                "note", "Active rate limits remain in effect for security reasons"
        ));
    }

    /**
     * Get information about rate limit data retention and privacy.
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getRateLimitPrivacyInfo() {
        return ResponseEntity.ok(Map.of(
                DESCRIPTION_KEY, "Rate limiting data privacy information",
                "data_types", Map.of(
                        "active_rate_limits", Map.of(
                                DESCRIPTION_KEY, "Current rate limit counters",
                                "retention", "Duration of rate limit window (e.g., 1 hour)",
                                "erasable", false,
                                "reason", "Required for security and abuse prevention"
                        ),
                        "audit_logs", Map.of(
                                DESCRIPTION_KEY, "Historical record of rate limit violations",
                                "retention", "24 hours",
                                "erasable", true,
                                "data", Map.of(
                                        "user_id", "Hashed user identifier (anonymized)",
                                        "timestamp", "When violation occurred",
                                        ENDPOINT_KEY, "Which rate limit was exceeded"
                                )
                        )
                ),
                "anonymization", Map.of(
                        "user_ids", "One-way SHA-256 hash",
                        "ip_addresses", "Last octet removed (IPv4) or last 64 bits (IPv6)"
                ),
                "purpose", "Security, abuse prevention, and system stability",
                "legal_basis", "Legitimate interest for security (GDPR Article 6(1)(f))",
                "rights", Map.of(
                        "access", Map.of(
                                ENDPOINT_KEY, EXPORT_ENDPOINT,
                                DESCRIPTION_KEY, "Export your rate limit violation history"
                        ),
                        "erasure", Map.of(
                                ENDPOINT_KEY, DELETE_ENDPOINT,
                                DESCRIPTION_KEY, "Delete audit logs (active limits remain for security)"
                        ),
                        "information", Map.of(
                                ENDPOINT_KEY, INFO_ENDPOINT,
                                DESCRIPTION_KEY, "This privacy information"
                        )
                )
        ));
    }

    /**
     * Get current rate limit metrics (admin only).
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRateLimitMetrics() {
        return ResponseEntity.ok(rateLimiterService.getMetrics());
    }
}
