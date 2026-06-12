package com.sm.instagram.platform.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Filter to log all incoming HTTP requests and responses, including those rejected before reaching controllers.
 * This filter runs after RequestBodyCachingFilter and CorsLoggingFilter to capture all requests.
 */
@Slf4j
@Component
@Order(3) // Run after EarlyRequestLoggingFilter (-100), RequestCorrelationFilter (1), and CorsLoggingFilter (2)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Wrap request and response to enable content reading
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        // Get REQUEST_ID and correlationId from MDC (set by earlier filters)
        String requestId = MDC.get("REQUEST_ID");
        String correlationId = MDC.get("correlationId");
        
        // Generate fallbacks if not set (shouldn't happen with EarlyRequestLoggingFilter)
        if (requestId == null) {
            requestId = generateRequestId();
            MDC.put("REQUEST_ID", requestId);
        }
        if (correlationId == null) {
            correlationId = "REQ-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            MDC.put("correlationId", correlationId);
        }
        
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        // Ensure other context info is set
        MDC.put("requestMethod", wrappedRequest.getMethod());
        MDC.put("requestUri", wrappedRequest.getRequestURI());

        try {
            // Log incoming request
            logIncomingRequest(wrappedRequest, requestId, timestamp);

            long startTime = System.currentTimeMillis();
            boolean requestCompleted = false;

            try {
                filterChain.doFilter(wrappedRequest, wrappedResponse);
                requestCompleted = true;
            } catch (Exception e) {
                // Log exception that occurred during request processing
                logRequestException(wrappedRequest, e, requestId, timestamp);
                throw e;
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                
                // Log response (including rejected requests)
                logResponse(wrappedRequest, wrappedResponse, requestId, timestamp, duration, requestCompleted);
                
                // Copy cached response content back to original response
                wrappedResponse.copyBodyToResponse();
            }
        } finally {
            // Don't clear MDC here - let RequestCorrelationFilter handle it
        }
    }

    private void logIncomingRequest(HttpServletRequest request, String requestId, String timestamp) {
        try {
            String correlationId = MDC.get("correlationId");
            
            // Only log basic incoming request info - detailed logging happens in exception handlers if needed
            Map<String, Object> requestInfo = new HashMap<>();
            requestInfo.put("requestId", requestId);
            requestInfo.put("correlationId", correlationId);
            requestInfo.put("timestamp", timestamp);
            requestInfo.put("method", request.getMethod());
            requestInfo.put("uri", request.getRequestURI());
            requestInfo.put("remoteAddr", anonymizeIp(getClientIpAddress(request)));
            requestInfo.put("contentType", request.getContentType());
            requestInfo.put("contentLength", request.getContentLength());

            log.info("INCOMING_REQUEST: {}", requestInfo);

            // Log preflight requests separately
            if ("OPTIONS".equals(request.getMethod())) {
                String origin = request.getHeader("Origin");
                log.info("PREFLIGHT_REQUEST: requestId={}, correlationId={}, origin={}, accessControlRequestMethod={}", 
                        requestId, correlationId, origin, request.getHeader("Access-Control-Request-Method"));
            }

        } catch (Exception e) {
            log.error("Error logging incoming request: {}", e.getMessage(), e);
        }
    }

    private void logResponse(HttpServletRequest request, HttpServletResponse response, 
                           String requestId, String timestamp, long duration, boolean requestCompleted) {
        try {
            String correlationId = MDC.get("correlationId");
            
            Map<String, Object> responseInfo = new HashMap<>();
            responseInfo.put("requestId", requestId);
            responseInfo.put("correlationId", correlationId);
            responseInfo.put("timestamp", timestamp);
            responseInfo.put("method", request.getMethod());
            responseInfo.put("uri", request.getRequestURI());
            responseInfo.put("status", response.getStatus());
            responseInfo.put("duration", duration + "ms");
            responseInfo.put("requestCompleted", requestCompleted);

            // GDPR COMPLIANT AUDIT LOG: Purpose=security_monitoring, LegalBasis=legitimate_interest
            // Format for Loki/Grafana parsing
            String auditLog = String.format(
                "REQUEST_COMPLETE request_id=\"%s\" correlation_id=\"%s\" method=\"%s\" path=\"%s\" " +
                "status=%d execution_time_ms=%d client_ip=\"%s\" user_agent=\"%s\"",
                requestId, correlationId, request.getMethod(), request.getRequestURI(),
                response.getStatus(), duration,
                anonymizeIp(getClientIpAddress(request)),
                categorizeUserAgent(request.getHeader("User-Agent"))
            );
            
            // Log different levels based on response status
            if (response.getStatus() >= 400) {
                // For errors, just log basic info - detailed logging is handled by GlobalDefaultExceptionHandler
                if (response.getStatus() >= 500) {
                    log.error("REQUEST_ERROR: {}", responseInfo);
                    log.error(auditLog);  // Audit log for metrics
                } else {
                    log.warn("REQUEST_REJECTED: {}", responseInfo);
                    log.warn(auditLog);  // Audit log for metrics
                }
                // Note: Detailed request logging is now handled by GlobalDefaultExceptionHandler to avoid duplication
            } else {
                log.info("REQUEST_SUCCESS: {}", responseInfo);
                log.info(auditLog);  // Audit log for metrics
            }

        } catch (Exception e) {
            log.error("Error logging response for requestId {}: {}", requestId, e.getMessage(), e);
        }
    }

    private void logRequestException(HttpServletRequest request, Exception e, String requestId, String timestamp) {
        String correlationId = MDC.get("correlationId");
        
        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("requestId", requestId);
        errorInfo.put("correlationId", correlationId);
        errorInfo.put("timestamp", timestamp);
        errorInfo.put("exception", e.getClass().getSimpleName());
        errorInfo.put("message", e.getMessage());
        errorInfo.put("fullRequestDetails", buildFullRequestDetails(request));

        log.error("REQUEST_EXCEPTION_DETAILED: {}", errorInfo, e);
    }

    private Map<String, Object> buildFullRequestDetails(HttpServletRequest request) {
        Map<String, Object> requestDetails = new HashMap<>();
        
        try {
            // Basic request info
            requestDetails.put("method", request.getMethod());
            requestDetails.put("path", request.getRequestURI());
            requestDetails.put("queryString", request.getQueryString());
            requestDetails.put("remoteAddr", anonymizeIp(getClientIpAddress(request)));
            requestDetails.put("contentType", request.getContentType());
            requestDetails.put("contentLength", request.getContentLength());
            
            // Headers (with sensitive data masked)
            requestDetails.put("headers", extractHeaders(request));
            
            // Parameters
            requestDetails.put("parameters", extractParameters(request));
            
            // GDPR: Only log body size, not content (may contain personal data)
            String requestBody = extractRequestBody(request);
            if (requestBody != null && !requestBody.isEmpty()) {
                requestDetails.put("bodySize", requestBody.length());
                // Only log body for errors if explicitly needed for debugging
                if (shouldLogRequestBody(request)) {
                    requestDetails.put("body", sanitizeRequestBody(requestBody));
                }
            }
            
        } catch (Exception e) {
            requestDetails.put("error", "Failed to extract request details: " + e.getMessage());
            log.error("Error building full request details", e);
        }
        
        return requestDetails;
    }

    private String extractRequestBody(HttpServletRequest request) {
        try {
            if (request instanceof ContentCachingRequestWrapper) {
                ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
                byte[] content = wrapper.getContentAsByteArray();
                
                if (content.length > 0) {
                    String body = new String(content, StandardCharsets.UTF_8);
                    
                    // Limit body size for logging to prevent huge logs
                    int maxBodySize = 2048; // 2KB limit
                    if (body.length() > maxBodySize) {
                        return body.substring(0, maxBodySize) + "... [TRUNCATED - Total size: " + body.length() + " chars]";
                    }
                    
                    return body;
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract request body: {}", e.getMessage());
            return "Error extracting body: " + e.getMessage();
        }
        
        return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            
            // Mask sensitive headers
            if (isSensitiveHeader(headerName)) {
                headerValue = "***MASKED***";
            }
            
            headers.put(headerName, headerValue);
        }
        
        return headers;
    }

    private Map<String, String> extractResponseHeaders(HttpServletResponse response) {
        Map<String, String> headers = new HashMap<>();
        
        for (String headerName : response.getHeaderNames()) {
            headers.put(headerName, response.getHeader(headerName));
        }
        
        return headers;
    }

    private Map<String, String[]> extractParameters(HttpServletRequest request) {
        Map<String, String[]> params = new HashMap<>(request.getParameterMap());
        // GDPR: Mask sensitive parameters
        for (String key : params.keySet()) {
            if (isSensitiveParameter(key)) {
                params.put(key, new String[]{"***GDPR_MASKED***"});
            }
        }
        return params;
    }

    private boolean isSensitiveHeader(String headerName) {
        return headerName != null && (
            headerName.toLowerCase().contains("authorization") ||
            headerName.toLowerCase().contains("cookie") ||
            headerName.toLowerCase().contains("password") ||
            headerName.toLowerCase().contains("token")
        );
    }

    private String generateRequestId() {
        return "REQ-" + System.currentTimeMillis() + "-" + Thread.currentThread().threadId();
    }

    /**
     * GDPR: Anonymize IP address by masking last octet
     */
    private String anonymizeIp(String ip) {
        if (ip == null || ip.isEmpty()) return "unknown";
        
        if (ip.contains(":")) {
            // IPv6: mask last 64 bits
            int lastColon = ip.lastIndexOf(":");
            if (lastColon > 0) {
                return ip.substring(0, lastColon) + ":xxxx";
            }
        } else if (ip.contains(".")) {
            // IPv4: mask last octet
            int lastDot = ip.lastIndexOf(".");
            if (lastDot > 0) {
                return ip.substring(0, lastDot) + ".xxx";
            }
        }
        return ip;
    }
    
    /**
     * GDPR: Categorize user agent instead of logging full string
     */
    private String categorizeUserAgent(String userAgent) {
        if (userAgent == null) return "unknown";
        
        // Only extract browser type and major version
        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Firefox")) return "Firefox";
        if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) return "Safari";
        if (userAgent.contains("Edge")) return "Edge";
        if (userAgent.contains("bot") || userAgent.contains("Bot")) return "Bot";
        
        return "Other";
    }
    
    /**
     * GDPR: Check if parameter contains sensitive data
     */
    private boolean isSensitiveParameter(String paramName) {
        if (paramName == null) return false;
        String lower = paramName.toLowerCase();
        return lower.contains("email") || 
               lower.contains("password") || 
               lower.contains("name") || 
               lower.contains("phone") || 
               lower.contains("address") ||
               lower.contains("ssn") ||
               lower.contains("dob") ||
               lower.contains("birth");
    }
    
    /**
     * GDPR: Only log request body for specific error scenarios
     */
    private boolean shouldLogRequestBody(HttpServletRequest request) {
        // Only for specific debugging endpoints or error conditions
        String uri = request.getRequestURI();
        return uri.contains("/debug/") || uri.contains("/test/");
    }
    
    /**
     * GDPR: Sanitize request body to remove personal data
     */
    private String sanitizeRequestBody(String body) {
        if (body == null) return null;
        
        // Mask email addresses
        body = body.replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "***EMAIL_MASKED***");
        
        // Mask phone numbers
        body = body.replaceAll("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b", "***PHONE_MASKED***");
        
        // Mask credit card numbers
        body = body.replaceAll("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b", "***CARD_MASKED***");
        
        return body;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Don't filter actuator endpoints to avoid noise
        // Don't filter webhook endpoints — ContentCachingRequestWrapper truncates raw String @RequestBody
        // which breaks Stripe signature verification (payload must be read as-is)
        return uri.startsWith("/actuator/") || uri.contains("/webhooks/");
    }
}
