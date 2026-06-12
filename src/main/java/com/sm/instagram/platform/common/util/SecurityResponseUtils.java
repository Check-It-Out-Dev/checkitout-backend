package com.sm.instagram.platform.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.common.exceptions.handlers.BaseExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.io.IOException;
import java.util.Locale;

/**
 * Utility class for handling security-related HTTP responses.
 * Used by Spring Security handlers to create consistent error responses.
 */
@Slf4j
public class SecurityResponseUtils {

    private SecurityResponseUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Write authentication failure response
     */
    public static void writeAuthenticationFailureResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            Exception authException,
            ObjectMapper objectMapper) throws IOException {
        writeAuthenticationFailureResponse(request, response, authException, objectMapper, null);
    }

    /**
     * Write authentication failure response with localized message
     */
    public static void writeAuthenticationFailureResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            Exception authException,
            ObjectMapper objectMapper,
            MessageSource messageSource) throws IOException {

        String traceId = RequestContextUtils.generateTraceId("SEC");
        logSecurityEvent("AUTHENTICATION_FAILURE", request, authException, traceId);
        logAuthenticationDetails(request, traceId);

        String message = getLocalizedMessage(messageSource, "error.auth.not_authenticated", "Authentication required");

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                message,
                request.getRequestURI()
        );
        errorResponse.setRequestId(traceId);
        errorResponse.setMessageKey("error.auth.not_authenticated");

        writeJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, errorResponse, objectMapper);
    }

    /**
     * Write access denied response
     */
    public static void writeAccessDeniedResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            Exception accessDeniedException,
            ObjectMapper objectMapper) throws IOException {
        writeAccessDeniedResponse(request, response, accessDeniedException, objectMapper, null);
    }

    /**
     * Write access denied response with localized message
     */
    public static void writeAccessDeniedResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            Exception accessDeniedException,
            ObjectMapper objectMapper,
            MessageSource messageSource) throws IOException {

        String traceId = RequestContextUtils.generateTraceId("SEC");
        logSecurityEvent("ACCESS_DENIED", request, accessDeniedException, traceId);
        logAccessDeniedDetails(request, traceId);

        String message = getLocalizedMessage(messageSource, "error.security.access_denied", "Access denied - insufficient permissions");

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpServletResponse.SC_FORBIDDEN,
                "Forbidden",
                message,
                request.getRequestURI()
        );
        errorResponse.setRequestId(traceId);
        errorResponse.setMessageKey("error.security.access_denied");

        writeJsonResponse(response, HttpServletResponse.SC_FORBIDDEN, errorResponse, objectMapper);
    }

    /**
     * Get localized message from MessageSource, falling back to default if unavailable.
     */
    private static String getLocalizedMessage(MessageSource messageSource, String key, String defaultMessage) {
        if (messageSource == null) {
            return defaultMessage;
        }
        try {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(key, null, defaultMessage, locale);
        } catch (Exception e) {
            log.debug("Failed to get localized message for key: {}", key, e);
            return defaultMessage;
        }
    }

    /**
     * Write JSON response to HttpServletResponse
     */
    private static void writeJsonResponse(
            HttpServletResponse response,
            int statusCode,
            BaseExceptionHandler.ErrorResponse errorResponse,
            ObjectMapper objectMapper) throws IOException {

        response.setContentType("application/json");
        response.setStatus(statusCode);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    /**
     * Log security events with consistent format
     */
    private static void logSecurityEvent(String eventType, HttpServletRequest request, Exception exception, String traceId) {
        String requestInfo = RequestContextUtils.buildRequestContext(request, traceId);
        String exceptionType = exception != null ? exception.getClass().getSimpleName() : "Unknown";
        String message = exception != null ? exception.getMessage() : "Security event";

        log.warn("SECURITY_EVENT - {} [{}]: {}", eventType, requestInfo, message);
    }

    /**
     * Log authentication failure details
     */
    private static void logAuthenticationDetails(HttpServletRequest request, String traceId) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null) {
            if (!authHeader.startsWith("Bearer ")) {
                log.warn("INVALID_AUTH_FORMAT: requestId={}, authHeaderFormat=Invalid", traceId);
            } else if (authHeader.length() < 20) {
                log.warn("SUSPICIOUS_TOKEN: requestId={}, tokenLength=TooShort", traceId);
            }
        } else {
            log.warn("MISSING_AUTH_HEADER: requestId={}, endpoint={}", traceId, request.getRequestURI());
        }
    }

    /**
     * Log access denied details
     */
    private static void logAccessDeniedDetails(HttpServletRequest request, String traceId) {
        String origin = request.getHeader("Origin");
        if (origin != null) {
            log.warn("POTENTIAL_CORS_ACCESS_DENIED: requestId={}, origin={}, uri={}", traceId, origin, request.getRequestURI());
        }
    }
}
