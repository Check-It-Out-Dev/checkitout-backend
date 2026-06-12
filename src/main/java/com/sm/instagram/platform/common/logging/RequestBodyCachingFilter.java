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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Filter to cache request body content early in the filter chain to ensure it's available
 * for exception handlers and other components that need to read the request body.
 * This filter runs first to set up REQUEST_ID and cache body content.
 */
@Slf4j
@Component
@Order(0) // Run first to set up REQUEST_ID and cache request body
public class RequestBodyCachingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_SIZE = 8192; // 8KB max for caching

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Generate unique REQUEST_ID for this request and set in MDC
        String requestId = generateRequestId();
        MDC.put("REQUEST_ID", requestId);
        
        // Set basic request context
        MDC.put("requestMethod", request.getMethod());
        MDC.put("requestUri", request.getRequestURI());

        try {
            // Only cache for POST/PUT/PATCH requests with JSON content
            if (shouldCacheRequestBody(request)) {
                CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
                filterChain.doFilter(cachedRequest, response);
            } else {
                filterChain.doFilter(request, response);
            }
        } finally {
            // Don't clear MDC here - let RequestLoggingFilter handle it
            // MDC will be cleared by RequestLoggingFilter which runs after this
        }
    }

    /**
     * Generate unique request ID
     */
    private String generateRequestId() {
        return "REQ-" + System.currentTimeMillis() + "-" + Thread.currentThread().threadId();
    }

    private boolean shouldCacheRequestBody(HttpServletRequest request) {
        String method = request.getMethod();
        String contentType = request.getContentType();
        int contentLength = request.getContentLength();

        return ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) &&
               contentType != null &&
               contentType.startsWith("application/json") &&
               contentLength > 0 &&
               contentLength <= MAX_BODY_SIZE;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();

        // Don't cache sensitive authentication endpoints (contain credentials)
        if (uri.startsWith("/api/auth/")) {
            return true;
        }

        // Don't cache webhook endpoints — caching replaces \n with System.lineSeparator()
        // which alters the raw payload bytes and breaks Stripe signature verification
        if (uri.contains("/webhooks/")) {
            return true;
        }

        // Don't filter actuator endpoints to avoid noise
        return uri.startsWith("/actuator/");
    }

    /**
     * Custom HttpServletRequest wrapper that caches the request body
     */
    public static class CachedBodyHttpServletRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] cachedBody;
        private final String cachedBodyString;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);

            // Read and cache the request body
            String body = "";
            byte[] bodyBytes = new byte[0];
            
            try (BufferedReader reader = request.getReader()) {
                body = reader.lines().collect(Collectors.joining(System.lineSeparator()));
                bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                
                // Store in request attributes for easy access
                request.setAttribute("CACHED_REQUEST_BODY", body);
                request.setAttribute("CACHED_REQUEST_BODY_BYTES", bodyBytes);
                
                log.debug("Cached request body: {} chars, {} bytes for URI: {}", 
                         body.length(), bodyBytes.length, request.getRequestURI());
            } catch (Exception e) {
                log.warn("Failed to cache request body for URI {}: {}", request.getRequestURI(), e.getMessage());
                // Keep empty values as initialized
                request.setAttribute("CACHED_REQUEST_BODY", "");
                request.setAttribute("CACHED_REQUEST_BODY_ERROR", e.getMessage());
            }
            
            // Assign to final fields
            this.cachedBodyString = body;
            this.cachedBody = bodyBytes;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() throws IOException {
            return new CachedBodyServletInputStream(cachedBody);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
        }

        public String getCachedBody() {
            return cachedBodyString;
        }

        public byte[] getCachedBodyBytes() {
            return cachedBody.clone();
        }
    }

    /**
     * Custom ServletInputStream that reads from cached body
     */
    public static class CachedBodyServletInputStream extends jakarta.servlet.ServletInputStream {
        private final ByteArrayInputStream inputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.inputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener readListener) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return inputStream.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return inputStream.read(b, off, len);
        }
    }
}
