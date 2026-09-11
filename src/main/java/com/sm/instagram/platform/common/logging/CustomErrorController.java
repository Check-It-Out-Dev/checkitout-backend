package com.sm.instagram.platform.common.logging;

import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Custom error controller to handle and log servlet-level errors that don't reach application controllers.
 * This captures errors like 404s, method not allowed, etc.
 *
 * <p>{@code @Hidden} because this is a servlet forward target, not an operation any client calls.
 * springdoc published it anyway, so {@code /error} was in the OpenAPI document, the frontend
 * generated a CustomErrorControllerApi nothing has ever called, and a caller reading the contract
 * would conclude the API has an endpoint that reports errors on request. It does not: the status
 * comes from the {@code jakarta.servlet.error.status_code} request attribute the container sets
 * during a forward, and a direct call arrives without it, so every direct call is a 500 by
 * construction. Schemathesis dutifully tried all six methods and reported six server errors.
 */
@Hidden
@Slf4j
@RestController
@RequiredArgsConstructor
@RateLimit(profile = RateLimitProfile.RELAXED)  // 60 req/min for cooperation endpoints
public class CustomErrorController implements ErrorController {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    
    private final MessageSource messageSource;

    @RequestMapping("/error")
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        Map<String, Object> errorDetails = extractErrorDetails(request);
        
        // Extract Firebase UID if available
        String firebaseUid = extractFirebaseUid();
        
        // Log GDPR entry for error handling
        log.info("GDPR: Operation=handleError, FirebaseUID={}, RequestURI={}, Purpose=error_tracking", 
            firebaseUid, request.getRequestURI());

        // Log the servlet-level error
        logServletError(request, errorDetails, firebaseUid);

        // Return appropriate response
        HttpStatus status = getStatus(request);
        return new ResponseEntity<>(createErrorResponse(errorDetails, status, request), status);
    }
    
    private String extractFirebaseUid() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                return auth.getPrincipal().toString();
            }
        } catch (Exception e) {
            // Authentication context might not be available for all errors (e.g., during filter chain errors)
            log.debug("Unable to extract Firebase UID during error handling: {}", e.getMessage());
        }
        return "ANONYMOUS";
    }

    private void logServletError(HttpServletRequest request, Map<String, Object> errorDetails, String firebaseUid) {
        String requestId = generateRequestId();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        Map<String, Object> logInfo = new HashMap<>();
        logInfo.put("requestId", requestId);
        logInfo.put("timestamp", timestamp);
        logInfo.put("type", "SERVLET_ERROR");
        logInfo.put("firebaseUid", firebaseUid);  // Add Firebase UID to log info
        logInfo.put("method", request.getMethod());
        logInfo.put("uri", request.getRequestURI());
        logInfo.put("queryString", request.getQueryString());
        logInfo.put("remoteAddr", getClientIpAddress(request));
        logInfo.put("userAgent", request.getHeader("User-Agent"));
        logInfo.put("referer", request.getHeader("Referer"));
        logInfo.put("errorDetails", errorDetails);

        Integer statusCode = (Integer) errorDetails.get("status");

        if (statusCode != null) {
            if (statusCode >= 500) {
                log.error("SERVLET_ERROR_5XX: {}", logInfo);
            } else if (statusCode >= 400) {
                log.warn("SERVLET_ERROR_4XX: {}", logInfo);

                // Log specific error types
                logSpecificServletErrors(request, statusCode, requestId, firebaseUid);
            } else {
                log.info("SERVLET_ERROR_OTHER: {}", logInfo);
            }
        } else {
            log.error("SERVLET_ERROR_UNKNOWN: {}", logInfo);
        }
    }

    private void logSpecificServletErrors(HttpServletRequest request, Integer statusCode, String requestId, String firebaseUid) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String origin = request.getHeader("Origin");

        switch (statusCode) {
            case 400:
                log.warn("GDPR: SERVLET_BAD_REQUEST FirebaseUID={}, requestId={}, uri={}, method={}, Purpose=error_tracking",
                        firebaseUid, requestId, uri, method);
                break;
            case 401:
                log.warn("GDPR: SERVLET_UNAUTHORIZED FirebaseUID={}, requestId={}, uri={}, method={}, Purpose=auth_failure_tracking",
                        firebaseUid, requestId, uri, method);
                break;
            case 403:
                log.warn("GDPR: SERVLET_FORBIDDEN FirebaseUID={}, requestId={}, uri={}, method={}, Purpose=access_denial_tracking",
                        firebaseUid, requestId, uri, method);

                // Check for CORS issues
                if (origin != null) {
                    log.warn("CORS_REJECTION: requestId={}, origin={}, uri={}, method={}, reason=CORS policy violation",
                            requestId, origin, uri, method);
                }
                break;
            case 404:
                log.warn("GDPR: SERVLET_NOT_FOUND FirebaseUID={}, requestId={}, uri={}, method={}, Purpose=endpoint_discovery_attempt",
                        firebaseUid, requestId, uri, method);
                break;
            case 405:
                log.warn("GDPR: SERVLET_METHOD_NOT_ALLOWED FirebaseUID={}, requestId={}, uri={}, method={}, Purpose=method_error_tracking",
                        firebaseUid, requestId, uri, method);
                break;
            case 406:
                log.warn("SERVLET_NOT_ACCEPTABLE: requestId={}, uri={}, method={}, reason=Accept header incompatible",
                        requestId, uri, method);
                break;
            case 415:
                log.warn("SERVLET_UNSUPPORTED_MEDIA_TYPE: requestId={}, uri={}, method={}, reason=Content-Type not supported",
                        requestId, uri, method);
                break;
            case 429:
                log.warn("SERVLET_RATE_LIMITED: requestId={}, uri={}, method={}, reason=Too many requests",
                        requestId, uri, method);
                break;
        }

        // Additional CORS-specific logging
        if ("OPTIONS".equals(method) && statusCode >= 400) {
            log.warn("PREFLIGHT_REJECTION: requestId={}, origin={}, uri={}, status={}, reason=CORS preflight failed",
                    requestId, origin, uri, statusCode);
        }
    }

    private Map<String, Object> extractErrorDetails(HttpServletRequest request) {
        Map<String, Object> errorDetails = new HashMap<>();

        errorDetails.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        errorDetails.put("status", request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        errorDetails.put("error", request.getAttribute(RequestDispatcher.ERROR_MESSAGE));
        errorDetails.put("exception", request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE));
        errorDetails.put("path", request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));

        // Add additional context
        Exception exception = (Exception) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (exception != null) {
            errorDetails.put("exceptionMessage", exception.getMessage());
            errorDetails.put("exceptionClass", exception.getClass().getSimpleName());
        }

        return errorDetails;
    }

    private Map<String, Object> createErrorResponse(Map<String, Object> errorDetails, HttpStatus status, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", errorDetails.get("timestamp"));
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());
        response.put("path", errorDetails.get("path"));

        // Get localized error message (using LocaleContextHolder)
        String message = getErrorMessage(status);
        response.put("message", message);

        // Add requestId for support purposes
        String requestId = getRequestIdFromMdc();
        response.put("requestId", requestId);

        return response;
    }

    /**
     * Get localized error message using LocaleContextHolder (set by AppLanguageFilter
     * from X-App-Language header). This ensures error messages use the user's explicit
     * UI language choice, not the browser's Accept-Language header.
     */
    private String getErrorMessage(HttpStatus status) {
        Locale locale = getLocaleFromContext();
        String messageKey;

        switch (status) {
            case BAD_REQUEST:
                messageKey = "error.validation.failed";
                break;
            case UNAUTHORIZED:
                messageKey = "error.auth.not_authenticated";
                break;
            case FORBIDDEN:
                messageKey = "error.auth.insufficient_permissions";
                break;
            case NOT_FOUND:
                messageKey = "error.not_found";
                break;
            case METHOD_NOT_ALLOWED:
                messageKey = "error.http.method_not_allowed";
                break;
            case NOT_ACCEPTABLE:
                messageKey = "error.http.unsupported_media_type";
                break;
            case UNSUPPORTED_MEDIA_TYPE:
                messageKey = "error.http.unsupported_media_type";
                break;
            case TOO_MANY_REQUESTS:
                messageKey = "error.business.rate_limit_exceeded";
                break;
            case INTERNAL_SERVER_ERROR:
                messageKey = "error.general.internal_server";
                break;
            default:
                messageKey = "error.general.unexpected";
        }

        return messageSource.getMessage(messageKey, null, messageKey, locale);
    }
    
    /**
     * Get locale from LocaleContextHolder (set by AppLanguageFilter from X-App-Language header).
     * This ensures error messages use the user's explicit UI language choice,
     * not the browser's Accept-Language header.
     */
    private Locale getLocaleFromContext() {
        Locale locale = LocaleContextHolder.getLocale();
        if (locale == null || locale.getLanguage().isEmpty()) {
            return new Locale("pl"); // Default to Polish
        }
        return locale;
    }

    /**
     * Get request ID from MDC correlationId or generate a new one.
     * Uses the short format (REQ-xxxxxxxx) which is user-friendly for support.
     */
    private String getRequestIdFromMdc() {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.trim().isEmpty()) {
            return correlationId;
        }
        // Fallback - generate a new request ID
        return "REQ-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private HttpStatus getStatus(HttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        try {
            return HttpStatus.valueOf(statusCode);
        } catch (Exception ex) {
            // Non-standard HTTP status code (e.g., 499 from Nginx)
            log.warn("Unknown HTTP status code encountered: {}, defaulting to 500", statusCode);
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
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

    private String generateRequestId() {
        return "ERR-" + System.currentTimeMillis() + "-" + Thread.currentThread().threadId();
    }
}
