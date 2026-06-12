package com.sm.instagram.platform.common.logging;

import com.sm.instagram.platform.config.CorsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Very early filter that logs ALL requests, including those rejected by Spring Security CORS.
 * This filter runs BEFORE Spring Security to capture requests from unauthorized origins.
 * Manually registered in FilterRegistrationConfiguration.
 */
@Slf4j
public class EarlyRequestLoggingFilter extends OncePerRequestFilter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final List<String> allowedOrigins;

    public EarlyRequestLoggingFilter(CorsProperties corsProperties) {
        this.allowedOrigins = corsProperties.getAllowedOrigins();
        log.info("EarlyRequestLoggingFilter initialized - this should run before Spring Security");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Capture request details at the very beginning
        String requestId = generateRequestId();
        String correlationId = generateCorrelationId();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String origin = request.getHeader("Origin");
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String userAgent = request.getHeader("User-Agent");
        String remoteAddr = getClientIpAddress(request);

        // Set in MDC IMMEDIATELY for consistent logging
        MDC.put("REQUEST_ID", requestId);
        MDC.put("correlationId", correlationId);
        MDC.put("requestUri", uri);
        MDC.put("requestMethod", method);

        // ALWAYS log every request that reaches this filter (now with proper MDC context)
        log.info("EarlyRequestLoggingFilter: Processing request {} {} from origin: {}", 
                method, uri, origin);

        // Log the incoming request (especially important for CORS rejections)
        logIncomingRequest(requestId, correlationId, timestamp, origin, method, uri, userAgent, remoteAddr);

        // Log suspicious/unauthorized origins
        if (origin != null && !isAuthorizedOrigin(origin)) {
            logUnauthorizedOrigin(requestId, correlationId, origin, method, uri, userAgent, remoteAddr);
        }

        // Special logging for OPTIONS requests (CORS preflight)
        if ("OPTIONS".equals(method)) {
            logOptionsRequest(requestId, correlationId, origin, uri, request);
        }

        long startTime = System.currentTimeMillis();
        boolean requestCompleted = false;

        try {
            // Continue with the filter chain
            filterChain.doFilter(request, response);
            requestCompleted = true;
        } catch (Exception e) {
            // Ensure MDC context is still available for error logging
            MDC.put("REQUEST_ID", requestId);
            MDC.put("correlationId", correlationId);
            MDC.put("requestUri", uri);
            MDC.put("requestMethod", method);
            
            // Log exceptions that occur during processing
            logRequestException(requestId, correlationId, origin, method, uri, e);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // Ensure MDC context is available for final logging
            MDC.put("REQUEST_ID", requestId);
            MDC.put("correlationId", correlationId);
            MDC.put("requestUri", uri);
            MDC.put("requestMethod", method);
            
            // Log the final response status
            logRequestComplete(requestId, correlationId, origin, method, uri, response.getStatus(), duration, requestCompleted);
            
            // Don't clear MDC here - let RequestCorrelationFilter handle it
        }
    }

    private void logOptionsRequest(String requestId, String correlationId, String origin, String uri, HttpServletRequest request) {
        Map<String, Object> optionsInfo = new HashMap<>();
        optionsInfo.put("requestId", requestId);
        optionsInfo.put("correlationId", correlationId);
        optionsInfo.put("type", "CORS_PREFLIGHT");
        optionsInfo.put("uri", uri);
        optionsInfo.put("origin", origin);
        optionsInfo.put("accessControlRequestMethod", request.getHeader("Access-Control-Request-Method"));
        optionsInfo.put("accessControlRequestHeaders", request.getHeader("Access-Control-Request-Headers"));
        optionsInfo.put("originAuthorized", isAuthorizedOrigin(origin));
        optionsInfo.put("remoteAddr", getClientIpAddress(request));

        if (isAuthorizedOrigin(origin)) {
            log.info("CORS_PREFLIGHT_AUTHORIZED: {}", optionsInfo);
        } else {
            log.warn("CORS_PREFLIGHT_UNAUTHORIZED: {}", optionsInfo);
        }
    }

    private void logIncomingRequest(String requestId, String correlationId, String timestamp, 
                                   String origin, String method, String uri, String userAgent, String remoteAddr) {
        Map<String, Object> requestInfo = new HashMap<>();
        requestInfo.put("requestId", requestId);
        requestInfo.put("correlationId", correlationId);
        requestInfo.put("timestamp", timestamp);
        requestInfo.put("type", "EARLY_REQUEST");
        requestInfo.put("method", method);
        requestInfo.put("uri", uri);
        requestInfo.put("origin", origin);
        requestInfo.put("remoteAddr", remoteAddr);
        requestInfo.put("userAgent", userAgent);
        requestInfo.put("originAuthorized", isAuthorizedOrigin(origin));

        log.info("EARLY_REQUEST_START: {}", requestInfo);
    }

    private void logUnauthorizedOrigin(String requestId, String correlationId, String origin, 
                                     String method, String uri, String userAgent, String remoteAddr) {
        Map<String, Object> suspiciousInfo = new HashMap<>();
        suspiciousInfo.put("requestId", requestId);
        suspiciousInfo.put("correlationId", correlationId);
        suspiciousInfo.put("type", "UNAUTHORIZED_ORIGIN");
        suspiciousInfo.put("method", method);
        suspiciousInfo.put("uri", uri);
        suspiciousInfo.put("origin", origin);
        suspiciousInfo.put("remoteAddr", remoteAddr);
        suspiciousInfo.put("userAgent", userAgent);
        suspiciousInfo.put("reason", "Origin not in allowed list");

        // Check for specific suspicious patterns
        if (origin.length() > 100) {
            suspiciousInfo.put("suspiciousPattern", "LONG_ORIGIN");
        }
        if (origin.contains("localhost") && !origin.startsWith("http://localhost") && !origin.startsWith("https://localhost")) {
            suspiciousInfo.put("suspiciousPattern", "MALFORMED_LOCALHOST");
        }
        if (origin.contains("..") || origin.contains("\\")) {
            suspiciousInfo.put("suspiciousPattern", "PATH_TRAVERSAL_ATTEMPT");
        }

        log.warn("UNAUTHORIZED_ORIGIN_REQUEST: {}", suspiciousInfo);
    }

    private void logRequestException(String requestId, String correlationId, String origin, 
                                   String method, String uri, Exception e) {
        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("requestId", requestId);
        errorInfo.put("correlationId", correlationId);
        errorInfo.put("type", "EARLY_REQUEST_EXCEPTION");
        errorInfo.put("method", method);
        errorInfo.put("uri", uri);
        errorInfo.put("origin", origin);
        errorInfo.put("exception", e.getClass().getSimpleName());
        errorInfo.put("message", e.getMessage());

        log.error("EARLY_REQUEST_EXCEPTION: {}", errorInfo, e);
    }

    private void logRequestComplete(String requestId, String correlationId, String origin, 
                                   String method, String uri, int status, long duration, boolean completed) {
        Map<String, Object> completionInfo = new HashMap<>();
        completionInfo.put("requestId", requestId);
        completionInfo.put("correlationId", correlationId);
        completionInfo.put("type", "EARLY_REQUEST_COMPLETE");
        completionInfo.put("method", method);
        completionInfo.put("uri", uri);
        completionInfo.put("origin", origin);
        completionInfo.put("status", status);
        completionInfo.put("duration", duration + "ms");
        completionInfo.put("completed", completed);
        completionInfo.put("originAuthorized", isAuthorizedOrigin(origin));

        if (status >= 400) {
            if (origin != null && !isAuthorizedOrigin(origin)) {
                log.warn("EARLY_REQUEST_CORS_REJECTED: {}", completionInfo);
            } else {
                log.warn("EARLY_REQUEST_ERROR: {}", completionInfo);
            }
        } else {
            log.debug("EARLY_REQUEST_SUCCESS: {}", completionInfo);
        }
    }

    private boolean isAuthorizedOrigin(String origin) {
        if (origin == null) {
            return true; // Non-CORS requests are okay
        }

        boolean authorized = allowedOrigins.contains(origin);

        log.debug("Origin authorization check: origin='{}', authorized={}", origin, authorized);
        
        return authorized;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
            "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR", "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP", "HTTP_CLIENT_IP", "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED", "HTTP_VIA", "REMOTE_ADDR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    private String generateRequestId() {
        return "REQ-" + System.currentTimeMillis() + "-" + Thread.currentThread().threadId();
    }

    private String generateCorrelationId() {
        return "REQ-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
