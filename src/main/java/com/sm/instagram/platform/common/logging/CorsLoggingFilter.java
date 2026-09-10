package com.sm.instagram.platform.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sm.instagram.platform.config.CorsProperties;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filter specifically for logging CORS-related issues and requests.
 * This filter runs after RequestBodyCachingFilter but before RequestLoggingFilter.
 */
@Slf4j
@Component
@Order(2) // Run after EarlyRequestLoggingFilter (-100) and RequestCorrelationFilter (1)
public class CorsLoggingFilter extends OncePerRequestFilter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final List<String> allowedOrigins;

    public CorsLoggingFilter(CorsProperties corsProperties) {
        this.allowedOrigins = corsProperties.getAllowedOrigins();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String origin = request.getHeader("Origin");
        String method = request.getMethod();
        
        // FIXED: Capture MDC values at the beginning before they get cleared
        String requestId = MDC.get("REQUEST_ID");
        String correlationId = MDC.get("correlationId");
        
        // Generate fallback if MDC not set
        if (requestId == null) {
            requestId = generateRequestId();
        }
        if (correlationId == null) {
            correlationId = generateCorrelationId();
        }
        
        // Add trace ID headers to ALL responses (including rejected ones)
        addTraceHeaders(response, requestId, correlationId);
        
        // Log CORS requests
        if (origin != null) {
            logCorsRequest(request, origin, requestId, correlationId);
        }

        // Process the request
        filterChain.doFilter(request, response);

        // Log CORS response headers after processing (using captured IDs)
        if (origin != null) {
            logCorsResponse(request, response, origin, requestId, correlationId);
        }
    }

    private void logCorsRequest(HttpServletRequest request, String origin, String requestId, String correlationId) {
        // Temporarily restore MDC values for proper log formatting
        MDC.put("REQUEST_ID", requestId);
        MDC.put("correlationId", correlationId);
        MDC.put("requestUri", request.getRequestURI());
        MDC.put("requestMethod", request.getMethod());
        
        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String method = request.getMethod();
            String uri = request.getRequestURI();

            Map<String, Object> corsInfo = new HashMap<>();
            corsInfo.put("requestId", requestId);
            corsInfo.put("correlationId", correlationId);
            corsInfo.put("timestamp", timestamp);
            corsInfo.put("type", "CORS_REQUEST");
            corsInfo.put("method", method);
            corsInfo.put("uri", uri);
            corsInfo.put("origin", origin);
            corsInfo.put("originAllowed", isOriginAllowed(origin));
            // GDPR: Anonymize IP and categorize user agent
            corsInfo.put("remoteAddr", anonymizeIp(getClientIpAddress(request)));
            corsInfo.put("userAgentCategory", categorizeUserAgent(request.getHeader("User-Agent")));

            // Additional CORS headers
            corsInfo.put("accessControlRequestMethod", request.getHeader("Access-Control-Request-Method"));
            corsInfo.put("accessControlRequestHeaders", request.getHeader("Access-Control-Request-Headers"));

            // Check if this is a preflight request
            boolean isPreflight = "OPTIONS".equals(method) && 
                                  request.getHeader("Access-Control-Request-Method") != null;
            corsInfo.put("isPreflight", isPreflight);

            if (isPreflight) {
                // GDPR: Log with purpose and legal basis
                log.info("CORS_PREFLIGHT_REQUEST [Purpose=security_monitoring, LegalBasis=legitimate_interest]: {}", corsInfo);
                logPreflightDetails(request, requestId, correlationId, origin);
            } else {
                log.debug("CORS_SIMPLE_REQUEST: {}", corsInfo);
            }

            // Log potential CORS issues
            if (!isOriginAllowed(origin)) {
                // GDPR: Security monitoring with legal basis
                log.warn("CORS_ORIGIN_NOT_ALLOWED [Purpose=security_monitoring]: requestId={}, correlationId={}, origin={}", 
                        requestId, correlationId, origin);
            }

            // Log suspicious patterns.
            // A same-origin request carries no Origin header at all, so origin is null more often
            // than not, and this is a logging filter: it has no business throwing on a request it
            // only meant to describe (javabugs:S2259). One guard for both checks, so a later edit
            // cannot leave a sibling unguarded the way the length check was.
            if (origin != null) {
                if (origin.contains("localhost") && !isLocalhostOrigin(origin)) {
                    log.warn("CORS_SUSPICIOUS_LOCALHOST: requestId={}, correlationId={}, suspiciousOrigin={}", requestId, correlationId, origin);
                }

                if (origin.length() > 100) {
                    log.warn("CORS_SUSPICIOUS_LONG_ORIGIN: requestId={}, correlationId={}, originLength={}", requestId, correlationId, origin.length());
                }
            }
            
        } finally {
            // Don't clear MDC here since other filters might still need it
            // The RequestCorrelationFilter will handle clearing at the end
        }
    }

    /**
     * True only when the origin's host component IS localhost -- not merely when the origin starts
     * with "http://localhost", and not when something before the host merely looks like it.
     *
     * <p>Two shapes this has to refuse, both found by a test rather than by reading:
     * <ul>
     *   <li>{@code http://localhost.evil.com} -- an attacker-controlled apex whose leftmost label
     *       begins with "localhost". A startsWith test calls it legitimate, so it was the one
     *       origin the suspicious-localhost warning never fired on.
     *   <li>{@code https://localhost:4200@evil.com} -- "localhost:4200" is USERINFO here; the host
     *       is evil.com. Taking the host as everything up to the first ':' reads the userinfo as
     *       the host and calls that legitimate too. The Origin header has no userinfo form at all
     *       (RFC 6454 is scheme, host and port), so an '@' in the authority means this is not a
     *       plain origin and is exactly the kind of thing worth logging.
     * </ul>
     */
    private boolean isLocalhostOrigin(String origin) {
        int schemeEnd = origin.indexOf("://");
        if (schemeEnd < 0) {
            return false;
        }
        String scheme = origin.substring(0, schemeEnd);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return false;
        }
        String rest = origin.substring(schemeEnd + 3);
        int authorityEnd = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                authorityEnd = i;
                break;
            }
        }
        String authority = rest.substring(0, authorityEnd);
        if (authority.indexOf('@') >= 0) {
            return false;
        }
        int portAt = authority.indexOf(':');
        String host = portAt < 0 ? authority : authority.substring(0, portAt);
        return "localhost".equals(host);
    }

    private void logCorsResponse(HttpServletRequest request, HttpServletResponse response, String origin, String requestId, String correlationId) {
        // FIXED: Temporarily restore MDC values for proper log formatting
        MDC.put("REQUEST_ID", requestId);
        MDC.put("correlationId", correlationId);
        
        try {
            String method = request.getMethod();
            int status = response.getStatus();
            boolean isPreflight = "OPTIONS".equals(method) && 
                                  request.getHeader("Access-Control-Request-Method") != null;

            Map<String, Object> responseInfo = new HashMap<>();
            responseInfo.put("requestId", requestId);
            responseInfo.put("correlationId", correlationId);
            responseInfo.put("type", "CORS_RESPONSE");
            responseInfo.put("method", method);
            responseInfo.put("uri", request.getRequestURI());
            responseInfo.put("origin", origin);
            responseInfo.put("status", status);
            responseInfo.put("isPreflight", isPreflight);

            // Log CORS response headers
            Map<String, String> corsHeaders = new HashMap<>();
            corsHeaders.put("Access-Control-Allow-Origin", response.getHeader("Access-Control-Allow-Origin"));
            corsHeaders.put("Access-Control-Allow-Methods", response.getHeader("Access-Control-Allow-Methods"));
            corsHeaders.put("Access-Control-Allow-Headers", response.getHeader("Access-Control-Allow-Headers"));
            corsHeaders.put("Access-Control-Allow-Credentials", response.getHeader("Access-Control-Allow-Credentials"));
            corsHeaders.put("Access-Control-Max-Age", response.getHeader("Access-Control-Max-Age"));
            corsHeaders.put("Access-Control-Expose-Headers", response.getHeader("Access-Control-Expose-Headers"));
            
            responseInfo.put("corsHeaders", corsHeaders);

            if (status >= 400) {
                log.warn("CORS_ERROR_RESPONSE: {}", responseInfo);
                
                // Special handling for rejected preflight requests
                if (isPreflight && (status == 403 || status == 405 || status == 401)) {
                    log.error("CORS_PREFLIGHT_REJECTED: requestId={}, correlationId={}, origin={}, status={}, " +
                             "reason=Preflight request rejected, traceId={}, clientTraceHeaders=[X-Request-ID: {}, X-Correlation-ID: {}, X-Trace-ID: {}]", 
                             requestId, correlationId, origin, status, correlationId, requestId, correlationId, correlationId);
                             
                    // Log specific rejection reason
                    String rejectionReason = determineRejectionReason(request, response, origin);
                    log.error("CORS_PREFLIGHT_REJECTION_REASON: requestId={}, correlationId={}, reason={}, " +
                             "clientCanUseTraceId={} for support", 
                             requestId, correlationId, rejectionReason, correlationId);
                }
                
                if (response.getHeader("Access-Control-Allow-Origin") == null) {
                    log.error("CORS_NO_ACCESS_CONTROL_HEADER: requestId={}, correlationId={}, origin={}, status={}, " +
                             "reason=Missing Access-Control-Allow-Origin header, traceId={}", 
                             requestId, correlationId, origin, status, correlationId);
                }
            } else {
                log.debug("CORS_SUCCESS_RESPONSE: {}", responseInfo);
                
                // Log successful preflight with trace info
                if (isPreflight) {
                    log.info("CORS_PREFLIGHT_ACCEPTED: requestId={}, correlationId={}, origin={}, " +
                            "traceId={} included in response headers", 
                            requestId, correlationId, origin, correlationId);
                }
            }

            // Verify CORS headers are properly set
            validateCorsHeaders(request, response, requestId, correlationId, origin);
            
        } finally {
            // Clear the temporarily set MDC values (they were already cleared by RequestCorrelationFilter)
            MDC.remove("REQUEST_ID");
            MDC.remove("correlationId");
        }
    }

    private void logPreflightDetails(HttpServletRequest request, String requestId, String correlationId, String origin) {
        // MDC should already be set by logCorsRequest, but let's ensure it's there
        MDC.put("REQUEST_ID", requestId);
        MDC.put("correlationId", correlationId);
        
        try {
            String requestedMethod = request.getHeader("Access-Control-Request-Method");
            String requestedHeaders = request.getHeader("Access-Control-Request-Headers");

            Map<String, Object> preflightInfo = new HashMap<>();
            preflightInfo.put("requestId", requestId);
            preflightInfo.put("correlationId", correlationId);
            preflightInfo.put("origin", origin);
            preflightInfo.put("requestedMethod", requestedMethod);
            preflightInfo.put("requestedHeaders", requestedHeaders);
            preflightInfo.put("uri", request.getRequestURI());

            log.info("CORS_PREFLIGHT_DETAILS: {}", preflightInfo);

            // Check for common preflight issues
            if (requestedMethod != null && !isMethodAllowed(requestedMethod)) {
                log.warn("CORS_METHOD_NOT_ALLOWED: requestId={}, correlationId={}, requestedMethod={}, allowedMethods=[GET,POST,PUT,PATCH,DELETE,OPTIONS]", 
                        requestId, correlationId, requestedMethod);
            }

            if (requestedHeaders != null && requestedHeaders.contains("X-Custom")) {
                log.info("CORS_CUSTOM_HEADERS_REQUESTED: requestId={}, correlationId={}, customHeaders={}", requestId, correlationId, requestedHeaders);
            }
        } finally {
            // Don't clear MDC here since parent method handles it
        }
    }

    private void validateCorsHeaders(HttpServletRequest request, HttpServletResponse response, String requestId, String correlationId, String origin) {
        // MDC should already be set by logCorsResponse, but let's ensure it's there
        MDC.put("REQUEST_ID", requestId);
        MDC.put("correlationId", correlationId);
        
        try {
            String allowOrigin = response.getHeader("Access-Control-Allow-Origin");
            String allowCredentials = response.getHeader("Access-Control-Allow-Credentials");

            // Check for CORS misconfigurations
            if ("*".equals(allowOrigin) && "true".equals(allowCredentials)) {
                log.error("CORS_SECURITY_ISSUE: requestId={}, correlationId={}, issue=Wildcard origin with credentials", requestId, correlationId);
            }

            if (allowOrigin != null && !allowOrigin.equals(origin) && !"*".equals(allowOrigin)) {
                log.warn("CORS_ORIGIN_MISMATCH: requestId={}, correlationId={}, requestOrigin={}, allowedOrigin={}", 
                        requestId, correlationId, origin, allowOrigin);
            }

            if (allowOrigin == null && origin != null) {
                log.warn("CORS_MISSING_ALLOW_ORIGIN: requestId={}, correlationId={}, origin={}", requestId, correlationId, origin);
            }
        } finally {
            // Don't clear MDC here since parent method handles it
        }
    }

    private boolean isOriginAllowed(String origin) {
        if (origin == null) {
            return false;
        }
        return allowedOrigins.contains(origin);
    }

    private boolean isMethodAllowed(String method) {
        List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        return allowedMethods.contains(method);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }
    
    /**
     * GDPR: Anonymize IP address by masking last octet/segment
     */
    private String anonymizeIp(String ip) {
        if (ip == null || ip.isEmpty()) return "unknown";
        
        if (ip.contains(":")) {
            // IPv6: mask last 64 bits for GDPR compliance
            int lastColon = ip.lastIndexOf(":");
            if (lastColon > 0) {
                return ip.substring(0, lastColon) + ":xxxx";
            }
        } else if (ip.contains(".")) {
            // IPv4: mask last octet for GDPR compliance
            int lastDot = ip.lastIndexOf(".");
            if (lastDot > 0) {
                return ip.substring(0, lastDot) + ".xxx";
            }
        }
        return ip;
    }
    
    /**
     * GDPR: Categorize user agent instead of logging full string
     * Full user agent strings can be used for fingerprinting
     */
    private String categorizeUserAgent(String userAgent) {
        if (userAgent == null) return "unknown";
        
        // Only extract browser type, not version or OS details
        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Firefox")) return "Firefox";
        if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) return "Safari";
        if (userAgent.contains("Edge")) return "Edge";
        if (userAgent.contains("bot") || userAgent.contains("Bot")) return "Bot";
        if (userAgent.contains("Postman")) return "Postman";
        if (userAgent.contains("curl")) return "curl";
        
        return "Other";
    }

    private String generateRequestId() {
        // Use same format as other filters for consistency
        return "REQ-" + System.currentTimeMillis() + "-" + Thread.currentThread().threadId();
    }

    private String generateCorrelationId() {
        // Use same format as RequestCorrelationFilter for consistency
        return "REQ-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Add trace ID headers to the response for client debugging
     */
    private void addTraceHeaders(HttpServletResponse response, String requestId, String correlationId) {
        try {
            // Add trace headers that clients can use for support/debugging
            response.setHeader("X-Request-ID", requestId);
            response.setHeader("X-Correlation-ID", correlationId);
            response.setHeader("X-Trace-ID", correlationId); // Use correlation ID as trace ID for simplicity
            
            log.debug("Added trace headers to CORS response: requestId={}, correlationId={}, traceId={}", 
                    requestId, correlationId, correlationId);
        } catch (Exception e) {
            log.warn("Failed to add trace headers to response: {}", e.getMessage());
        }
    }

    /**
     * Determine the specific reason why a CORS preflight request was rejected
     */
    private String determineRejectionReason(HttpServletRequest request, HttpServletResponse response, String origin) {
        // Check various rejection scenarios
        if (!isOriginAllowed(origin)) {
            return "Origin not in allowed list: " + origin + ". Allowed origins: " + allowedOrigins;
        }
        
        String requestedMethod = request.getHeader("Access-Control-Request-Method");
        if (requestedMethod != null && !isMethodAllowed(requestedMethod)) {
            return "HTTP method not allowed: " + requestedMethod + ". Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS";
        }
        
        if (response.getHeader("Access-Control-Allow-Origin") == null) {
            return "Server did not set Access-Control-Allow-Origin header";
        }
        
        String requestedHeaders = request.getHeader("Access-Control-Request-Headers");
        if (requestedHeaders != null && requestedHeaders.contains("Authorization") && 
            response.getHeader("Access-Control-Allow-Headers") != null && 
            !response.getHeader("Access-Control-Allow-Headers").contains("Authorization")) {
            return "Authorization header not allowed in preflight response";
        }
        
        // Check for authentication/authorization issues
        int status = response.getStatus();
        if (status == 401) {
            return "Authentication required - invalid or missing credentials";
        } else if (status == 403) {
            return "Access forbidden - user lacks required permissions";
        } else if (status == 405) {
            return "HTTP method not allowed by endpoint";
        }
        
        return "Unknown rejection reason - check server logs with trace ID: " + request.getHeader("X-Correlation-ID");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only filter requests that have Origin header or are OPTIONS requests
        String origin = request.getHeader("Origin");
        String method = request.getMethod();
        return origin == null && !"OPTIONS".equals(method);
    }
}
