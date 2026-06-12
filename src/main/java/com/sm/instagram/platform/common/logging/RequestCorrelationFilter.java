package com.sm.instagram.platform.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Enhanced filter to work with existing dual request correlation IDs.
 * 
 * Works with EarlyRequestLoggingFilter - uses existing MDC keys:
 * 1. correlationId - User-friendly, short identifier returned to users (e.g., REQ-ff4f353d)
 * 2. REQUEST_ID - Internal detailed identifier for logging correlation (e.g., REQ-1749371478007-53)
 * 
 * The correlationId is returned in response headers and error responses for user support.
 * The REQUEST_ID is used internally for detailed logging and debugging.
 */
@Component
@Order(1) // Execute after EarlyRequestLoggingFilter (-100) but before other filters
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String REQUEST_ID_MDC_KEY = "REQUEST_ID";
    private static final String REQUEST_URI_MDC_KEY = "requestUri";
    private static final String REQUEST_METHOD_MDC_KEY = "requestMethod";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        try {
            // Generate both IDs (only if not already set by EarlyRequestLoggingFilter)
            String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
            if (correlationId == null || correlationId.trim().isEmpty()) {
                correlationId = getOrGenerateCorrelationId(request);
                MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            }
            
            String requestId = MDC.get(REQUEST_ID_MDC_KEY);
            if (requestId == null || requestId.trim().isEmpty()) {
                requestId = generateRequestId();
                MDC.put(REQUEST_ID_MDC_KEY, requestId);
            }
            
            // Always set request context info
            MDC.put(REQUEST_URI_MDC_KEY, request.getRequestURI());
            MDC.put(REQUEST_METHOD_MDC_KEY, request.getMethod());
            
            // Add correlation ID (user-friendly) to response headers
            response.setHeader(TRACE_ID_HEADER, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId); // For backward compatibility
            
            // Continue with the request
            filterChain.doFilter(request, response);
            
        } finally {
            // Clean up MDC to prevent memory leaks
            MDC.clear();
        }
    }

    /**
     * Get user-friendly correlation ID from request header or generate a new one.
     * This allows clients to pass their own correlation ID for request correlation.
     */
    private String getOrGenerateCorrelationId(HttpServletRequest request) {
        // Try both headers for backward compatibility
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = request.getHeader(TRACE_ID_HEADER);
        }
        
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = generateCorrelationId();
        }
        
        return correlationId;
    }

    /**
     * Generate user-friendly correlation ID (your existing format).
     * Format: REQ-{SHORT_UUID}
     * Example: REQ-ff4f353d
     */
    private String generateCorrelationId() {
        return "REQ-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generate internal request ID (detailed, for logging).
     * Format: REQ-{TIMESTAMP}-{RANDOM}
     * Example: REQ-1749371478007-53
     */
    private String generateRequestId() {
        return "REQ-" + System.currentTimeMillis() + "-" + Thread.currentThread().threadId();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Don't filter actuator endpoints to avoid noise
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/");
    }
}
