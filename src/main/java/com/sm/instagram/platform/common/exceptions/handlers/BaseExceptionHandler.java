package com.sm.instagram.platform.common.exceptions.handlers;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sm.instagram.platform.common.util.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.WebRequest;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Base exception handler with common functionality for all exception handlers.
 */
@Slf4j
public class BaseExceptionHandler {
    
    public static final String NOT_FOUND = "Not Found";
    public static final String BAD_REQUEST = "Bad Request";
    public static final String REQUEST = "request";
    public static final String INTERNAL_SERVER_ERROR = "Internal Server Error";
    private static final String TRACE_ID_FORMAT = "trace=%s, path=%s, method=%s, user=%s, ip=%s";
    
    protected final MessageSource messageSource;
    
    protected BaseExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }
    
    /**
     * Centralized logging method for exceptions
     */
    public void logException(Exception ex, HttpStatus status, WebRequest request, String traceId) {
        String exceptionType = ex.getClass().getSimpleName();

        if (status.is5xxServerError()) {
            // Server errors - include full stack trace and detailed request info
            String detailedRequestInfo = buildDetailedRequestContext(request, traceId);
            log.error("SERVER_ERROR - {} [{}]: {}", exceptionType, detailedRequestInfo, ex.getMessage(), ex);
        } else {
            // For 4xx errors, most specific handlers already log details, so just log basic info
            String requestInfo = buildRequestContext(request, traceId);
            log.warn("CLIENT_ERROR - {} [{}]: {}", exceptionType, requestInfo, ex.getMessage());
        }
    }
    
    /**
     * Build detailed request context for error/warning scenarios
     */
    public String buildDetailedRequestContext(WebRequest request, String traceId) {
        try {
            Object nativeRequest = request.resolveReference(REQUEST);
            if (nativeRequest instanceof HttpServletRequest httpRequest) {
                return RequestContextUtils.buildDetailedRequestContext(httpRequest, traceId);
            }
        } catch (Exception e) {
            log.debug("Could not extract HttpServletRequest from WebRequest for detailed context", e);
        }

        // Fallback to basic context if detailed extraction fails
        return buildRequestContext(request, traceId);
    }
    
    /**
     * Build consistent request context for logging
     */
    public String buildRequestContext(WebRequest request, String traceId) {
        try {
            Object nativeRequest = request.resolveReference(REQUEST);
            if (nativeRequest instanceof HttpServletRequest httpRequest) {
                return RequestContextUtils.buildRequestContext(httpRequest, traceId);
            }
        } catch (Exception e) {
            log.debug("Could not extract HttpServletRequest from WebRequest", e);
        }

        // Fallback if we can't get HttpServletRequest
        return String.format(TRACE_ID_FORMAT,
                traceId,
                getPath(request),
                getHttpMethod(request),
                getCurrentUser(request),
                getClientIp(request));
    }
    
    /**
     * Extract HTTP method from request
     */
    public String getHttpMethod(WebRequest request) {
        try {
            Object nativeRequest = request.resolveReference(REQUEST);
            if (nativeRequest instanceof HttpServletRequest httpRequest) {
                return RequestContextUtils.getHttpMethod(httpRequest);
            }
        } catch (Exception e) {
            log.debug("Could not extract HTTP method", e);
        }
        return "UNKNOWN";
    }
    
    /**
     * Get current authenticated user
     */
    public String getCurrentUser(WebRequest request) {
        try {
            Object nativeRequest = request.resolveReference(REQUEST);
            if (nativeRequest instanceof HttpServletRequest httpRequest) {
                return RequestContextUtils.getCurrentUser(httpRequest);
            }
        } catch (Exception e) {
            log.debug("Could not extract user principal", e);
        }
        return "anonymous";
    }
    
    /**
     * Get client IP address
     */
    public String getClientIp(WebRequest request) {
        try {
            Object nativeRequest = request.resolveReference(REQUEST);
            if (nativeRequest instanceof HttpServletRequest httpRequest) {
                return RequestContextUtils.getClientIpAddress(httpRequest);
            }
        } catch (Exception e) {
            log.debug("Could not extract client IP", e);
        }
        return "unknown";
    }
    
    /**
     * Get locale from LocaleContextHolder, which is set by AppLanguageFilter
     * based on the X-App-Language header (user's explicit UI language choice).
     * Falls back to Polish if no locale is set.
     */
    protected Locale getLocale(WebRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        if (locale == null || locale.getLanguage().isEmpty()) {
            return new Locale("pl"); // Default to Polish
        }
        return locale;
    }

    /**
     * Get localized message from MessageSource
     */
    public String getLocalizedMessage(String key, Object[] args, WebRequest request) {
        Locale locale = getLocale(request);
        try {
            // Ensure args is properly handled - convert null to empty array
            Object[] messageArgs = args;
            if (messageArgs == null) {
                messageArgs = new Object[0];
            }
            
            // Additional safety check - if we somehow get a wrapped array, unwrap it
            if (messageArgs.length == 1 && messageArgs[0] != null && messageArgs[0].getClass().isArray()) {
                log.warn("Detected wrapped array in message args for key: {}, unwrapping...", key);
                if (messageArgs[0] instanceof Object[]) {
                    messageArgs = (Object[]) messageArgs[0];
                } else if (messageArgs[0] instanceof Serializable[]) {
                    Serializable[] serialArr = (Serializable[]) messageArgs[0];
                    messageArgs = new Object[serialArr.length];
                    System.arraycopy(serialArr, 0, messageArgs, 0, serialArr.length);
                }
            }
            
            return messageSource.getMessage(key, messageArgs, key, locale);
        } catch (Exception e) {
            log.warn("Failed to get localized message for key: {}, args: {}", key, 
                     args != null ? java.util.Arrays.toString(args) : "null", e);
            return key; // Fallback to key if translation not found
        }
    }

    /**
     * Get localized message from message source (legacy method for backward compatibility)
     */
    public String getMessage(String key, Object[] args, WebRequest request) {
        return getLocalizedMessage(key, args, request);
    }
    
    /**
     * Extract request path from WebRequest
     */
    public String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
    
    /**
     * Get trace ID from MDC or generate a new one
     */
    public String generateTraceId() {
        // First try to get correlationId from MDC (user-friendly, short)
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.trim().isEmpty()) {
            return correlationId; // Return REQ-761f080b (short, user-friendly)
        }

        // Fallback to REQUEST_ID from MDC (this should rarely happen)
        String requestId = MDC.get("REQUEST_ID");
        if (requestId != null && !requestId.trim().isEmpty()) {
            return requestId;
        }

        // Last resort - generate new ID
        return RequestContextUtils.generateTraceId();
    }
    
    /**
     * Standard error response structure.
     *
     * Note: Uses 'requestId' (short format like REQ-a1b2c3d4) which is user-friendly
     * for support purposes. Users can quote this ID when contacting support,
     * and support team can grep Loki logs using it.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorResponse {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private String path;
        private Map<String, String> validationErrors;
        private String requestId;
        private String messageKey;

        public ErrorResponse() {
            this.timestamp = LocalDateTime.now();
        }

        public ErrorResponse(int status, String error, String message, String path) {
            this();
            this.status = status;
            this.error = error;
            this.message = message;
            this.path = path;
        }

        // Getters and setters
        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Map<String, String> getValidationErrors() {
            return validationErrors;
        }

        public void setValidationErrors(Map<String, String> validationErrors) {
            this.validationErrors = validationErrors;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public void setMessageKey(String messageKey) {
            this.messageKey = messageKey;
        }
    }
}
