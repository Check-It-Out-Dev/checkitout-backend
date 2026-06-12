package com.sm.instagram.platform.common.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for handling request context operations like trace ID generation,
 * IP address extraction, and request context building.
 */
@Slf4j
public class RequestContextUtils {

    private RequestContextUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Generate trace ID with specified prefix, using MDC correlation ID when available
     */
    public static String generateTraceId(String prefix) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.trim().isEmpty()) {
            return correlationId;
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generate trace ID with default ERR prefix
     */
    public static String generateTraceId() {
        return generateTraceId("ERR");
    }

    /**
     * Extract client IP address from request headers
     */
    public static String getClientIpAddress(HttpServletRequest request) {
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

    /**
     * Get current authenticated user from various sources
     */
    public static String getCurrentUser(HttpServletRequest request) {
        try {
            // Try to get from Security Context first
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                Principal principal = SecurityContextHolder.getContext().getAuthentication();
                if (principal != null && principal.getName() != null) {
                    return principal.getName();
                }
            }

            // Fallback to request principal
            Principal principal = request.getUserPrincipal();
            return principal != null ? principal.getName() : "anonymous";
        } catch (Exception e) {
            log.debug("Could not extract user principal", e);
            return "anonymous";
        }
    }

    /**
     * Build detailed request context with full request information for error/warning scenarios
     */
    public static String buildDetailedRequestContext(HttpServletRequest request, String traceId) {
        Map<String, Object> requestDetails = buildFullRequestDetails(request);
        return String.format("trace=%s, path=%s, method=%s, user=%s, ip=%s, requestDetails=%s",
                traceId,
                request.getRequestURI(),
                request.getMethod(),
                getCurrentUser(request),
                getClientIpAddress(request),
                requestDetails);
    }

    /**
     * Build full request details including body, headers, and parameters
     */
    public static Map<String, Object> buildFullRequestDetails(HttpServletRequest request) {
        Map<String, Object> requestDetails = new HashMap<>();
        
        try {
            // Basic request info
            requestDetails.put("method", request.getMethod());
            requestDetails.put("path", request.getRequestURI());
            requestDetails.put("queryString", request.getQueryString());
            requestDetails.put("contentType", request.getContentType());
            requestDetails.put("contentLength", request.getContentLength());
            
            // Headers (with sensitive data masked)
            requestDetails.put("headers", extractHeaders(request));
            
            // Parameters - SANITIZED to mask sensitive query params
            Map<String, String[]> parameterMap = request.getParameterMap();
            if (!parameterMap.isEmpty()) {
                requestDetails.put("parameters", sanitizeParameters(parameterMap));
            }
            
            // Path variables (if available from request attributes)
            Map<String, Object> pathVariables = extractPathVariables(request);
            if (!pathVariables.isEmpty()) {
                requestDetails.put("pathVariables", pathVariables);
            }
            
            // Request body (JSON or other content) - SANITIZED to mask passwords/tokens/secrets
            String requestBody = extractRequestBody(request);
            if (requestBody != null && !requestBody.isEmpty()) {
                requestDetails.put("body", sanitizeSensitiveData(requestBody));
                requestDetails.put("bodySize", requestBody.length());
            }
            
        } catch (Exception e) {
            requestDetails.put("error", "Failed to extract request details: " + e.getMessage());
            log.error("Error building full request details", e);
        }
        
        return requestDetails;
    }

    /**
     * Extract headers from request with sensitive data masking
     */
    private static Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Collections.list(request.getHeaderNames()).forEach(headerName -> {
            String headerValue = request.getHeader(headerName);
            
            // Mask sensitive headers
            if (isSensitiveHeader(headerName)) {
                headerValue = "***MASKED***";
            }
            
            headers.put(headerName, headerValue);
        });
        
        return headers;
    }

    /**
     * Extract path variables from request attributes
     */
    private static Map<String, Object> extractPathVariables(HttpServletRequest request) {
        Map<String, Object> pathVariables = new HashMap<>();
        
        try {
            // Spring stores path variables in request attributes
            @SuppressWarnings("unchecked")
            Map<String, String> uriTemplateVars = (Map<String, String>) request.getAttribute("org.springframework.web.servlet.HandlerMapping.uriTemplateVariables");
            if (uriTemplateVars != null) {
                pathVariables.putAll(uriTemplateVars);
            }
        } catch (Exception e) {
            log.debug("Could not extract path variables", e);
        }
        
        return pathVariables;
    }

    /**
     * Extract request body content using multiple strategies
     */
    private static String extractRequestBody(HttpServletRequest request) {
        try {
            // Strategy 1: Check if body is cached by RequestBodyCachingFilter
            Object cachedBody = request.getAttribute("CACHED_REQUEST_BODY");
            if (cachedBody instanceof String && !((String) cachedBody).isEmpty()) {
                String body = (String) cachedBody;
                
                // Apply size limit for logging
                int maxBodySize = 2048; // 2KB limit
                if (body.length() > maxBodySize) {
                    return body.substring(0, maxBodySize) + "... [TRUNCATED - Total size: " + body.length() + " chars]";
                }
                
                return body;
            }

            // Strategy 2: Try ContentCachingRequestWrapper
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
                
                log.debug("ContentCachingRequestWrapper found but no content cached yet");
            }
            
            // Strategy 3: Try to read from input stream (risky - can only be read once)
            if (request.getContentLength() > 0 && 
                ("application/json".equals(request.getContentType()) || 
                 (request.getContentType() != null && request.getContentType().startsWith("application/json")))) {
                
                // Only attempt if content length is reasonable
                if (request.getContentLength() <= 8192) { // 8KB max
                    try {
                        StringBuilder body = new StringBuilder();
                        String line;
                        try (var reader = request.getReader()) {
                            while ((line = reader.readLine()) != null) {
                                body.append(line);
                            }
                        }
                        
                        String requestBody = body.toString();
                        if (!requestBody.isEmpty()) {
                            // Cache it for future use
                            request.setAttribute("CACHED_REQUEST_BODY", requestBody);
                            
                            // Apply size limit
                            int maxBodySize = 2048;
                            if (requestBody.length() > maxBodySize) {
                                return requestBody.substring(0, maxBodySize) + "... [TRUNCATED - Total size: " + requestBody.length() + " chars]";
                            }
                            
                            return requestBody;
                        }
                    } catch (IllegalStateException ise) {
                        log.debug("Input stream already consumed: {}", ise.getMessage());
                        return "Request body stream already consumed";
                    }
                }
            }
            
            // Strategy 4: Return informative message about why body couldn't be extracted
            if (request.getContentLength() <= 0) {
                return "No request body (content-length: " + request.getContentLength() + ")";
            }
            
            return String.format("Request body not available (type: %s, length: %d, cached: %s)", 
                    request.getClass().getSimpleName(), 
                    request.getContentLength(),
                    request.getAttribute("CACHED_REQUEST_BODY") != null ? "yes" : "no");
                    
        } catch (Exception e) {
            log.debug("Could not extract request body: {}", e.getMessage());
            return "Error extracting body: " + e.getMessage();
        }
    }

    /**
     * Check if header contains sensitive information
     */
    private static boolean isSensitiveHeader(String headerName) {
        if (headerName == null) return false;
        String lowerName = headerName.toLowerCase();
        return lowerName.contains("authorization") ||
               lowerName.contains("cookie") ||
               lowerName.contains("password") ||
               lowerName.contains("token") ||
               lowerName.contains("secret") ||
               lowerName.contains("key");
    }

    /**
     * Sensitive field names that should be masked in request body/parameters
     */
    private static final List<String> SENSITIVE_FIELD_PATTERNS = Arrays.asList(
            "password", "passwd", "pwd", "pass",
            "secret", "apiSecret", "clientSecret",
            "token", "accessToken", "refreshToken", "idToken", "bearerToken",
            "credential", "credentials",
            "key", "apiKey", "privateKey", "secretKey",
            "authorization", "auth",
            "pin", "otp", "totp", "mfa",
            "ssn", "socialSecurity",
            "cardNumber", "cvv", "cvc", "securityCode",
            "accountNumber"
    );

    /**
     * Pattern to match sensitive fields in JSON body
     * Matches: "fieldName":"value" or "fieldName": "value" (with optional spaces)
     */
    private static final Pattern SENSITIVE_JSON_PATTERN = buildSensitiveJsonPattern();

    private static Pattern buildSensitiveJsonPattern() {
        String fieldNamesPattern = SENSITIVE_FIELD_PATTERNS.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        // Match JSON key-value pairs: "sensitiveField" : "anyValue" or "sensitiveField":"anyValue"
        // Also handles escaped quotes within values
        return Pattern.compile(
                "\"(" + fieldNamesPattern + ")\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"",
                Pattern.CASE_INSENSITIVE
        );
    }

    /**
     * Sanitize sensitive data in request body (JSON format)
     * Masks values of sensitive fields like password, token, secret, etc.
     */
    public static String sanitizeSensitiveData(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }

        try {
            Matcher matcher = SENSITIVE_JSON_PATTERN.matcher(body);
            StringBuffer result = new StringBuffer();

            while (matcher.find()) {
                String fieldName = matcher.group(1);
                // Replace the entire match with masked version
                String replacement = "\"" + fieldName + "\":\"***MASKED***\"";
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(result);

            return result.toString();
        } catch (Exception e) {
            log.debug("Could not sanitize request body: {}", e.getMessage());
            // If sanitization fails, mask the entire body for safety
            return "***BODY_SANITIZATION_FAILED***";
        }
    }

    /**
     * Sanitize parameter map by masking sensitive values
     */
    public static Map<String, String[]> sanitizeParameters(Map<String, String[]> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return parameters;
        }

        Map<String, String[]> sanitized = new HashMap<>();
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();

            if (isSensitiveField(key)) {
                // Mask all values for sensitive fields
                String[] maskedValues = new String[values.length];
                Arrays.fill(maskedValues, "***MASKED***");
                sanitized.put(key, maskedValues);
            } else {
                sanitized.put(key, values);
            }
        }
        return sanitized;
    }

    /**
     * Check if a field name is sensitive
     */
    private static boolean isSensitiveField(String fieldName) {
        if (fieldName == null) return false;
        String lowerName = fieldName.toLowerCase();
        return SENSITIVE_FIELD_PATTERNS.stream()
                .anyMatch(pattern -> lowerName.contains(pattern.toLowerCase()));
    }

    /**
     * Build consistent request context string for logging
     */
    public static String buildRequestContext(HttpServletRequest request, String traceId) {
        return String.format("trace=%s, path=%s, method=%s, user=%s, ip=%s",
                traceId,
                request.getRequestURI(),
                request.getMethod(),
                getCurrentUser(request),
                getClientIpAddress(request));
    }

    /**
     * Extract HTTP method from request
     */
    public static String getHttpMethod(HttpServletRequest request) {
        try {
            return request.getMethod();
        } catch (Exception e) {
            log.debug("Could not extract HTTP method", e);
            return "UNKNOWN";
        }
    }

    /**
     * Extract request path
     */
    public static String getRequestPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
