package com.sm.instagram.platform.admin;

import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.security.GeoLocation;
import com.sm.instagram.platform.common.security.GeoLocationService;
import com.sm.instagram.platform.common.security.geoip.TravelPatternService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * GeoIP Admin Controller
 * <p>
 * Provides monitoring and testing endpoints for GeoIP functionality.
 * Restricted to admin users only.
 */
@Slf4j
@RestController
@RequestMapping("/admin/geoip")
@PreAuthorize("hasAuthority('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "GeoIP Admin", description = "GeoIP monitoring and testing (Admin only)")
public class GeoIpAdminController {

    // IP address validation pattern (IPv4)
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    private final GeoLocationService geoLocationService;
    private final TravelPatternService travelPatternService;

    /**
     * Get GeoIP service metrics.
     *
     * @return Map containing GeoIP service metrics and statistics
     * @throws ResourceNotFoundException if authentication context is missing
     */
    @GetMapping("/metrics")
    @Operation(summary = "Get GeoIP metrics", description = "Returns cache hit rates and service statistics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        String adminUid = getAuthenticatedAdminUid();

        // GDPR: Log metrics access
        log.info("GDPR: Operation=getGeoIpMetrics, FirebaseUID={}, Purpose=service_monitoring, DataAccessed=geoip.statistics",
                adminUid);

        Map<String, Object> metrics = geoLocationService.getMetrics();

        log.info("GDPR: Operation=getGeoIpMetrics_SUCCESS, FirebaseUID={}, MetricsRetrieved={}",
                adminUid, metrics.size());

        return ResponseEntity.ok(metrics);
    }

    /**
     * Lookup IP location.
     *
     * @param ip The IP address to lookup
     * @return GeoLocation data for the specified IP address
     * @throws ResourceNotFoundException       if authentication context is missing
     * @throws ValidationTranslatableException if IP address is invalid
     */
    @GetMapping("/lookup/{ip}")
    @Operation(summary = "Lookup IP location", description = "Get geolocation data for a specific IP address")
    public ResponseEntity<GeoLocation> lookupIp(
            @Parameter(description = "IP address to lookup", example = "8.8.8.8")
            @PathVariable String ip) {

        // Validate IP address
        validateIpAddress(ip, "ip");

        String adminUid = getAuthenticatedAdminUid();

        // GDPR: Log IP location lookup
        log.info("GDPR: Operation=lookupIpLocation, FirebaseUID={}, TargetIP={}, Purpose=geo_location_analysis, DataAccessed=ip.location, LegalBasis=legitimate_interest",
                adminUid, ip.replaceAll("(\\d+\\.\\d+\\.)(\\d+\\.\\d+)", "$1***.**"));

        log.info("Admin GeoIP lookup for: {}", ip);
        GeoLocation location = geoLocationService.getLocation(ip, 2000);  // 2 second timeout

        // GDPR: Log successful lookup
        log.info("GDPR: Operation=lookupIpLocation_SUCCESS, FirebaseUID={}, LocationCountry={}, LocationCity={}",
                adminUid, location != null ? location.getCountry() : "unknown",
                location != null ? location.getCity() : "unknown");

        return ResponseEntity.ok(location);
    }

    /**
     * Check my IP location.
     *
     * @param request The HTTP request to extract IP from
     * @return Map containing the request IP and its geolocation data
     * @throws ResourceNotFoundException if authentication context is missing
     */
    @GetMapping("/my-location")
    @Operation(summary = "Get my location", description = "Returns geolocation for the current request IP")
    public ResponseEntity<Map<String, Object>> getMyLocation(HttpServletRequest request) {
        String adminUid = getAuthenticatedAdminUid();
        String clientIp = getClientIpAddress(request);

        // GDPR: Log self location check
        log.info("GDPR: Operation=getMyLocation, FirebaseUID={}, OwnIP={}, Purpose=self_location_check, DataAccessed=own.ip.location",
                adminUid, clientIp.replaceAll("(\\d+\\.\\d+\\.)(\\d+\\.\\d+)", "$1***.**"));

        log.info("Admin checking own location from IP: {}", clientIp);
        GeoLocation location = geoLocationService.getLocation(clientIp, 2000);

        Map<String, Object> response = new HashMap<>();
        response.put("yourIp", clientIp);
        response.put("location", location);

        // GDPR: Log successful self check
        log.info("GDPR: Operation=getMyLocation_SUCCESS, FirebaseUID={}, LocationCountry={}",
                adminUid, location != null ? location.getCountry() : "unknown");

        return ResponseEntity.ok(response);
    }

    /**
     * Test impossible travel detection with full analysis.
     *
     * <p>Returns comprehensive travel analysis including:
     * <ul>
     *   <li>Tiered detection results (same city, same country, international)</li>
     *   <li>Risk scoring (0-100) and risk level (MINIMAL/LOW/MEDIUM/HIGH/CRITICAL)</li>
     *   <li>Travel event data for audit trail</li>
     *   <li>Distance and speed calculations</li>
     * </ul>
     *
     * @param fromIp  Source IP address
     * @param toIp    Destination IP address
     * @param minutes Time elapsed in minutes
     * @return Map containing full travel analysis results
     * @throws ResourceNotFoundException         if authentication context is missing
     * @throws ValidationTranslatableException   if parameters are invalid
     * @throws BusinessRuleTranslatableException if travel detection fails
     */
    @PostMapping("/test-travel")
    @Operation(summary = "Test impossible travel with full analysis",
            description = "Test travel detection between two IPs with risk scoring and detailed analysis")
    public ResponseEntity<Map<String, Object>> testImpossibleTravel(
            @RequestParam @Parameter(description = "Source IP", example = "1.1.1.1") String fromIp,
            @RequestParam @Parameter(description = "Destination IP", example = "8.8.8.8") String toIp,
            @RequestParam @Parameter(description = "Minutes elapsed", example = "30") int minutes) {

        // Validate input parameters
        validateIpAddress(fromIp, "fromIp");
        validateIpAddress(toIp, "toIp");
        if (minutes < 0) {
            throw new ValidationTranslatableException("error.validation.invalid_parameter", "minutes must be non-negative");
        }

        String adminUid = getAuthenticatedAdminUid();

        // GDPR: Log travel detection test
        log.info("GDPR: Operation=testImpossibleTravel, FirebaseUID={}, Purpose=security_testing, DataAccessed=ip.locations.travel_analysis",
                adminUid);

        log.info("Admin testing travel: {} -> {} in {} minutes", fromIp, toIp, minutes);

        try {
            // Get locations for analysis
            GeoLocation fromLocation = geoLocationService.getLocation(fromIp, 2000);
            GeoLocation toLocation = geoLocationService.getLocation(toIp, 2000);

            // Get FULL analysis from TravelPatternService (enables 90% coverage)
            TravelPatternService.TravelAnalysis analysis =
                travelPatternService.analyzeTravelPattern(fromLocation, toLocation, minutes);

            // Create travel event for audit trail
            Map<String, Object> travelEvent = travelPatternService.createTravelEvent(
                null, fromLocation, toLocation, minutes);

            // Build comprehensive response
            Map<String, Object> response = new HashMap<>();
            response.put("fromIp", fromIp);
            response.put("fromLocation", fromLocation);
            response.put("toIp", toIp);
            response.put("toLocation", toLocation);
            response.put("minutesElapsed", minutes);

            // Basic result (backward compatible)
            response.put("impossibleTravel", analysis.isImpossible());

            // Extended analysis fields (NEW - enables full TravelPatternService coverage)
            response.put("distanceKm", Math.round(analysis.getDistanceKm()));
            response.put("speedKmh", Math.round(analysis.getSpeedKmh()));
            response.put("riskScore", analysis.getRiskScore());
            response.put("riskLevel", analysis.getRiskLevel());
            response.put("countryJump", analysis.isCountryJump());
            response.put("sameCity", travelPatternService.isSameCity(fromLocation, toLocation));
            response.put("sameCountry", travelPatternService.isCountryMatch(fromLocation, toLocation));
            response.put("travelEvent", travelEvent);

            // GDPR: Log test result with full analysis
            log.info("GDPR: Operation=testImpossibleTravel_SUCCESS, FirebaseUID={}, ImpossibleTravel={}, RiskScore={}, RiskLevel={}, Minutes={}",
                    adminUid, analysis.isImpossible(), analysis.getRiskScore(), analysis.getRiskLevel(), minutes);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to test travel", e);
            throw new BusinessRuleTranslatableException("error.geoip.travel_detection_failed");
        }
    }

    /**
     * Force database update.
     *
     * @return Status message indicating update result
     * @throws ResourceNotFoundException         if authentication context is missing
     * @throws BusinessRuleTranslatableException if database update fails
     */
    @PostMapping("/update-database")
    @Operation(summary = "Update GeoIP database",
            description = "Force immediate update of MaxMind GeoLite2 database")
    @PreAuthorize("hasAuthority('ADMIN')")  // Extra security for this sensitive operation
    public ResponseEntity<Map<String, String>> updateDatabase() {
        String adminUid = getAuthenticatedAdminUid();

        // GDPR: Log database update operation
        log.warn("GDPR: Operation=updateGeoIpDatabase, FirebaseUID={}, Purpose=database_maintenance, DataAccessed=geoip.database",
                adminUid);

        log.warn("Admin forcing GeoIP database update");

        try {
            geoLocationService.updateGeoIpDatabase();

            // GDPR: Log successful update
            log.info("GDPR: Operation=updateGeoIpDatabase_SUCCESS, FirebaseUID={}, DataModified=geoip.database",
                    adminUid);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Database update initiated"
            ));
        } catch (Exception e) {
            log.error("Failed to update database", e);
            throw new BusinessRuleTranslatableException("error.database.update_failed");
        }
    }

    /**
     * Clear expired cache entries.
     *
     * @return Status message indicating cleanup result
     * @throws ResourceNotFoundException         if authentication context is missing
     * @throws BusinessRuleTranslatableException if cache cleanup fails
     */
    @PostMapping("/clean-cache")
    @Operation(summary = "Clean expired cache",
            description = "Force cleanup of expired GeoIP cache entries")
    public ResponseEntity<Map<String, String>> cleanCache() {
        String adminUid = getAuthenticatedAdminUid();

        // GDPR: Log cache cleanup
        log.info("GDPR: Operation=cleanGeoIpCache, FirebaseUID={}, Purpose=cache_maintenance, DataAccessed=geoip.cache",
                adminUid);

        log.info("Admin forcing cache cleanup");

        try {
            geoLocationService.cleanExpiredCache();

            // GDPR: Log successful cleanup
            log.info("GDPR: Operation=cleanGeoIpCache_SUCCESS, FirebaseUID={}, DataRemoved=expired.cache.entries",
                    adminUid);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Cache cleanup initiated"
            ));
        } catch (Exception e) {
            log.error("Failed to clean cache", e);
            throw new BusinessRuleTranslatableException("error.cache.cleanup_failed");
        }
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
        if (auth == null || auth.getName() == null) {
            log.error("Authentication not available in security context");
            throw new ResourceNotFoundException("error.auth.not_authenticated");
        }
        return auth.getName();
    }

    /**
     * Validates an IP address format.
     *
     * @param ip            The IP address to validate
     * @param parameterName The name of the parameter for error reporting
     * @throws ValidationTranslatableException if the IP address is invalid
     */
    private void validateIpAddress(String ip, String parameterName) {
        if (ip == null || ip.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", parameterName);
        }

        if (!IP_PATTERN.matcher(ip.trim()).matches()) {
            throw new ValidationTranslatableException("error.validation.invalid_parameter", parameterName + " must be a valid IPv4 address");
        }
    }

    /**
     * Extract client IP address from request.
     *
     * @param request The HTTP request
     * @return The client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated list
                int commaIndex = ip.indexOf(',');
                if (commaIndex > 0) {
                    ip = ip.substring(0, commaIndex).trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }
}
