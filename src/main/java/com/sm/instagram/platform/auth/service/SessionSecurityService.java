package com.sm.instagram.platform.auth.service;

import com.sm.instagram.platform.auth.filter.HmacUtils;
import com.sm.instagram.platform.common.security.GeoLocationService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Session Security Service - FIXED VERSION
 *
 * Validates session fingerprints to prevent session hijacking.
 * Implements ROBUST impossible travel detection based on industry standards.
 *
 * Security features:
 * - Country-level jump detection (instant flag)
 * - Speed-based detection (500 km/h max)
 * - IP address validation with travel check
 * - User-Agent exact match requirement
 * - Session age validation
 * - Constant-time comparison to prevent timing attacks
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSecurityService {

    @Autowired
    private GeoLocationService geoLocationService;

    @Value("${cookie.hmac.secret}")
    private String cookieHmacSecret;

    @Value("${security.session.fingerprint.enabled:true}")
    private boolean fingerprintValidationEnabled;

    @Value("${security.session.impossible-travel.enabled:true}")
    private boolean impossibleTravelCheckEnabled;

    // FIXED: Use 500 km/h consistently (commercial airplane speed)
    @Value("${security.session.impossible-travel.max-speed-kmh:500}")
    private int maxTravelSpeedKmh;

    @Value("${security.session.impossible-travel.circuit-breaker-threshold:5}")
    private int geoIpCircuitBreakerThreshold;

    private final AtomicInteger geoIpConsecutiveFailures = new AtomicInteger(0);
    
    @jakarta.annotation.PostConstruct
    public void logConfiguration() {
        log.info("SessionSecurityService initialized");
    }

    @Value("${security.session.max-age-days:7}")
    private int maxSessionAgeDays;

    @Value("${security.session.strict-user-agent:true}")
    private boolean strictUserAgentValidation;

    /**
     * Validate session security fingerprint.
     *
     * @param claims JWT claims containing fingerprint data
     * @param request Current HTTP request
     * @return true if session is valid, false if hijacked/invalid
     */
    public boolean validateSession(Claims claims, HttpServletRequest request) {
            
        if (!fingerprintValidationEnabled) {
            log.debug("Session fingerprint validation is disabled");
            return true;
        }

        // Extract Firebase UID if available
        String firebaseUid = extractFirebaseUid(claims);
        
        // GDPR: Log session validation attempt
        log.info("GDPR: Operation=session_validation_started, FirebaseUID={}, Purpose=security_validation", 
            firebaseUid != null ? firebaseUid : "unknown");

        try {
            // Extract stored fingerprint data from JWT
            String storedFingerprint = (String) claims.get("fingerprint");
            String storedIp = (String) claims.get("sessionIp");
            String storedUserAgent = (String) claims.get("sessionUA");
            Long sessionStart = claims.get("sessionStart", Long.class);

            // Check if fingerprint data exists
            if (storedFingerprint == null || storedIp == null || storedUserAgent == null) {
                // GDPR: Log missing fingerprint
                log.warn("GDPR: Operation=session_rejected, FirebaseUID={}, Reason=missing_fingerprint, Purpose=security_enforcement", 
                    firebaseUid != null ? firebaseUid : "unknown");
                return false;  // Reject sessions without fingerprint
            }

            // Check session age
            if (sessionStart != null && isSessionTooOld(sessionStart)) {
                // GDPR: Log expired session
                log.warn("GDPR: Operation=session_expired, FirebaseUID={}, Purpose=security_enforcement", 
                    firebaseUid != null ? firebaseUid : "unknown");
                return false;
            }

            // Extract current request data
            String currentIp = getClientIpAddress(request);
            String currentUserAgent = request.getHeader("User-Agent");
            String role = (String) claims.get("role");

            // Validate User-Agent (strict match required)
            if (strictUserAgentValidation && !validateUserAgent(storedUserAgent, currentUserAgent)) {
                // GDPR: Log user agent mismatch
                log.warn("GDPR: Operation=session_security_violation, FirebaseUID={}, ViolationType=user_agent_mismatch, Purpose=hijack_prevention",
                    firebaseUid != null ? firebaseUid : "unknown");
                logSecurityMetric("USER_AGENT_MISMATCH");
                return false;  // Session hijack attempt
            }

            // Validate IP (with travel check if different)
            if (!validateIpAddress(storedIp, currentIp, sessionStart, role)) {
                // GDPR: Log IP validation failure
                log.warn("GDPR: Operation=session_security_violation, FirebaseUID={}, ViolationType=ip_validation_failed, Purpose=hijack_prevention", 
                    firebaseUid != null ? firebaseUid : "unknown");
                logSecurityMetric("IP_VALIDATION_FAILED");
                return false;  // Session hijack or impossible travel
            }

            // Validate fingerprint (constant-time comparison)
            String expectedFingerprint = generateSessionFingerprint(currentIp, currentUserAgent);
            if (!HmacUtils.constantTimeEquals(storedFingerprint, expectedFingerprint)) {
                // GDPR: Log fingerprint mismatch
                log.warn("GDPR: Operation=session_security_violation, FirebaseUID={}, ViolationType=fingerprint_mismatch, Purpose=hijack_prevention", 
                    firebaseUid != null ? firebaseUid : "unknown");
                logSecurityMetric("FINGERPRINT_MISMATCH");
                return false;  // Definite hijack attempt
            }

            // GDPR: Log successful validation
            log.info("GDPR: Operation=session_validated, FirebaseUID={}, Purpose=security_validation_success", 
                firebaseUid != null ? firebaseUid : "unknown");
            return true;

        } catch (Exception e) {
            // GDPR: Log validation error
            log.error("GDPR: Operation=session_validation_error, FirebaseUID={}, Error={}, Purpose=error_logging", 
                firebaseUid != null ? firebaseUid : "unknown", e.getMessage(), e);
            // Fail secure - reject on any error
            return false;
        }
    }

    /**
     * Check if session is too old.
     *
     * @param sessionStart Session start timestamp
     * @return true if session is expired
     */
    private boolean isSessionTooOld(Long sessionStart) {
        if (sessionStart == null) {
            return false;  // Can't validate, allow for backward compatibility
        }

        Instant start = Instant.ofEpochMilli(sessionStart);
        Instant maxAge = Instant.now().minus(maxSessionAgeDays, ChronoUnit.DAYS);

        return start.isBefore(maxAge);
    }

    /**
     * Validate User-Agent string.
     * Requires exact match for security.
     *
     * @param stored Stored User-Agent
     * @param current Current User-Agent
     * @return true if valid
     */
    private boolean validateUserAgent(String stored, String current) {
        // Both null is valid (some clients don't send UA)
        if (stored == null && current == null) {
            return true;
        }

        // Handle "unknown" placeholder
        if ("unknown".equals(stored) && current == null) {
            return true;
        }

        // One null is invalid (UA changed)
        if (stored == null || current == null) {
            return false;
        }

        // Exact match required (no tolerance for version changes)
        return stored.equals(current);
    }

    /**
     * Validate IP address with optional travel check.
     *
     * @param storedIp Stored IP address
     * @param currentIp Current IP address
     * @param sessionStart Session start time for travel calculation
     * @return true if valid
     */
    private boolean validateIpAddress(String storedIp, String currentIp, Long sessionStart, String role) {
        // Handle OAuth internal IPs (from direct method calls)
        if ("oauth-internal".equals(storedIp) || "oauth-instagram".equals(storedIp)) {
            // OAuth internal session - skipping IP validation
            return true;
        }

        // Exact match is always valid
        if (storedIp.equals(currentIp)) {
            return true;
        }

        if (impossibleTravelCheckEnabled && sessionStart != null) {
            boolean impossibleTravel = isImpossibleTravel(storedIp, currentIp, sessionStart, role);
            if (impossibleTravel) {
            // GDPR: Log metric only, no location/IP data
            logSecurityMetric("IMPOSSIBLE_TRAVEL_DETECTED");
            }
            return !impossibleTravel;
        }

        // If travel check disabled, different IP is allowed
        // (mobile networks, VPNs, etc.)
        // GDPR: No IP logging
        return true;
    }

    /**
     * Check if travel between two IPs is impossible.
     *
     * <p>Delegates to TravelPatternService via GeoLocationFacade for tiered detection:
     * <ul>
     *   <li><b>TIER 1:</b> Same city - Always allow</li>
     *   <li><b>TIER 2:</b> Same country - Speed check (no grace period)</li>
     *   <li><b>TIER 3:</b> Different country - Country jump + speed check</li>
     * </ul>
     *
     * @param fromIp Original IP
     * @param toIp New IP
     * @param sessionStart Session start time
     * @return true if travel is impossible
     */
    private boolean isImpossibleTravel(String fromIp, String toIp, Long sessionStart, String role) {
        if (!impossibleTravelCheckEnabled || geoLocationService == null) {
            return false;
        }

        try {
            long minutesElapsed = (System.currentTimeMillis() - sessionStart) / 60000;

            // Delegate to GeoLocationFacade which uses TravelPatternService
            // This provides unified tiered detection (city → country → international)
            Boolean result = geoLocationService
                .checkImpossibleTravel(null, fromIp, toIp, minutesElapsed)
                .get(500, TimeUnit.MILLISECONDS);

            geoIpConsecutiveFailures.set(0); // Reset on success

            if (result != null && result) {
                logSecurityMetric("IMPOSSIBLE_TRAVEL_DETECTED");
                return true;
            }
            return false;

        } catch (TimeoutException e) {
            int failures = geoIpConsecutiveFailures.incrementAndGet();
            if (failures >= geoIpCircuitBreakerThreshold && isHighPrivilegeRole(role)) {
                log.warn("GeoIP circuit breaker open ({} consecutive failures), failing closed for role={}",
                    failures, role);
                return true;
            }
            log.debug("Impossible travel check timed out, failing open (failures={})", failures);
            return false;
        } catch (Exception e) {
            int failures = geoIpConsecutiveFailures.incrementAndGet();
            if (failures >= geoIpCircuitBreakerThreshold && isHighPrivilegeRole(role)) {
                log.warn("GeoIP circuit breaker open ({} consecutive failures), failing closed for role={}",
                    failures, role);
                return true;
            }
            log.warn("Impossible travel check failed: {}, failing open (failures={})", e.getMessage(), failures);
            return false;
        }
    }

    private boolean isHighPrivilegeRole(String role) {
        return "ADMIN".equals(role) || "PENDING_ADMIN".equals(role) || "COMPANY".equals(role);
    }

    /**
     * Get current user ID from security context.
     * Returns null if not available.
     */
    private Long getCurrentUserId() {
        try {
            // TODO: Get from security context when integrated
            // For now return null - FirestoreGeoLocationService handles null userId
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Log security metric for monitoring (GDPR compliant - no PII).
     * 
     * @param eventType Type of security event
     */
    private void logSecurityMetric(String eventType) {
        // GDPR: Log only aggregated metrics, no PII
        log.warn("SECURITY_METRIC: event_type={}", eventType);
        
        // TODO: Send to metrics service for aggregated monitoring
        // metricsService.incrementSecurityMetric(eventType);
    }

    /**
     * Generate session fingerprint from IP and User-Agent.
     *
     * @param clientIp Client IP address
     * @param userAgent User-Agent header
     * @return HMAC fingerprint
     */
    private String generateSessionFingerprint(String clientIp, String userAgent) {
        String fingerprintData = (clientIp != null ? clientIp : "unknown") + ":" +
                (userAgent != null ? userAgent : "unknown");

        // Use same secret as TokenExchangeService for consistency
        return HmacUtils.generateHMAC(fingerprintData, cookieHmacSecret + "-fingerprint");
    }

    /**
     * Extract client IP address from request.
     * 
     * @param request HTTP request
     * @return Client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // Check for proxy headers (in order of preference)
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED"
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
        
        // Fallback to remote address
        return request.getRemoteAddr();
    }

    /**
     * Mask IP address for privacy-compliant logging.
     *
     * @param ip IP address to mask
     * @return Masked IP address
     */
    private String maskIpForLogging(String ip) {
        if (ip == null) {
            return "unknown";
        }

        if (ip.contains(":")) {
            // IPv6 - mask last 4 segments
            int lastColon = ip.lastIndexOf(':');
            if (lastColon > 0) {
                return ip.substring(0, lastColon) + ":xxxx";
            }
        } else if (ip.contains(".")) {
            // IPv4 - mask last octet
            int lastDot = ip.lastIndexOf('.');
            if (lastDot > 0) {
                return ip.substring(0, lastDot) + ".xxx";
            }
        }

        return ip;
    }

    /**
     * Clear session cookies on security violation.
     *
     * @param response HTTP response
     */
    public void terminateSession(HttpServletResponse response) {
        // Try to get Firebase UID from current context
        String firebaseUid = null;
        try {
            firebaseUid = SecurityContextHolder.getContext().getAuthentication() != null ? 
                SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString() : null;
        } catch (Exception e) {
            // Unable to get Firebase UID
        }
        
        // GDPR: Log session termination
        log.warn("GDPR: Operation=session_terminated, FirebaseUID={}, Purpose=security_enforcement, Action=cookies_cleared", 
            firebaseUid != null ? firebaseUid : "unknown");
        
        // Clear session cookies
        addClearCookie(response, "session");
        addClearCookie(response, "session_sig");
        // Clear partial session cookies (2FA flow)
        addClearCookie(response, "partialSession");
        addClearCookie(response, "partialSessionSig");
        // Clear OAuth cookies (Instagram login flow leftovers)
        addClearCookie(response, "oauth_token");
        addClearCookie(response, "oauth_sig");
        addClearCookie(response, "oauth_meta");

        logSecurityMetric("SESSION_TERMINATED");
    }

    /**
     * Add a cookie that clears an existing cookie.
     *
     * @param response HTTP response
     * @param name Cookie name to clear
     */
    private void addClearCookie(HttpServletResponse response, String name) {
        // Use Set-Cookie header for full control
        response.addHeader("Set-Cookie",
                String.format("%s=; Max-Age=0; Path=/; HttpOnly; Secure; SameSite=Strict",
                        name));
    }
    
    /**
     * Extract Firebase UID from JWT claims.
     *
     * @param claims JWT claims
     * @return Firebase UID or null if not found
     */
    private String extractFirebaseUid(Claims claims) {
        if (claims == null) {
            return null;
        }
        
        // Try different claim names where Firebase UID might be stored
        Object uid = claims.get("firebaseUid");
        if (uid != null) {
            return uid.toString();
        }
        
        uid = claims.get("sub");  // Subject claim often contains Firebase UID
        if (uid != null) {
            return uid.toString();
        }
        
        uid = claims.get("user_id");
        if (uid != null) {
            return uid.toString();
        }
        
        return null;
    }
}
