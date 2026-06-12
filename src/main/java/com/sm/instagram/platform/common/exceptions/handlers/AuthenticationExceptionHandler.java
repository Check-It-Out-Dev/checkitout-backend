package com.sm.instagram.platform.common.exceptions.handlers;

import com.google.firebase.auth.FirebaseAuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * Exception handler for authentication and authorization errors.
 * Handles Firebase auth, Spring Security, and access control exceptions.
 */
@Slf4j
@ControllerAdvice
@Order(3)
public class AuthenticationExceptionHandler {
    
    private final BaseExceptionHandler baseHandler;
    
    public AuthenticationExceptionHandler(MessageSource messageSource) {
        this.baseHandler = new BaseExceptionHandler(messageSource) {};
    }
    
    /**
     * Handle Firebase authentication exceptions
     */
    @ExceptionHandler(FirebaseAuthException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleFirebaseAuthException(
            FirebaseAuthException ex, WebRequest request) {
        
        String traceId = baseHandler.generateTraceId();
        
        HttpStatus status;
        String messageKey;
        
        // Map Firebase error codes to appropriate HTTP status and message keys
        String errorCode = ex.getErrorCode() != null ? ex.getErrorCode().toString() : "";
        switch (errorCode) {
            case "USER_NOT_FOUND" -> {
                status = HttpStatus.NOT_FOUND;
                messageKey = "error.auth.user_not_found";
            }
            case "EMAIL_ALREADY_EXISTS" -> {
                status = HttpStatus.CONFLICT;
                messageKey = "error.auth.email_already_exists";
            }
            case "INVALID_EMAIL" -> {
                status = HttpStatus.BAD_REQUEST;
                messageKey = "error.validation.invalid_email";
            }
            case "WEAK_PASSWORD" -> {
                status = HttpStatus.BAD_REQUEST;
                messageKey = "error.auth.weak_password";
            }
            case "USER_DISABLED" -> {
                status = HttpStatus.FORBIDDEN;
                messageKey = "error.auth.account_disabled";
            }
            case "TOO_MANY_ATTEMPTS_TRY_LATER" -> {
                status = HttpStatus.TOO_MANY_REQUESTS;
                messageKey = "error.auth.too_many_attempts";
            }
            case "EXPIRED_ID_TOKEN" -> {
                status = HttpStatus.UNAUTHORIZED;
                messageKey = "error.auth.token_expired";
            }
            case "INVALID_ID_TOKEN" -> {
                status = HttpStatus.UNAUTHORIZED;
                messageKey = "error.auth.invalid_token";
            }
            case "REVOKED_ID_TOKEN" -> {
                status = HttpStatus.UNAUTHORIZED;
                messageKey = "error.auth.revoked_token";
            }
            default -> {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                messageKey = "error.auth.service_unavailable";
            }
        }
        
        // Get localized message
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);
        
        baseHandler.logException(ex, status, request, traceId);
        log.error("Firebase auth error details [trace={}, errorCode={}]", traceId, errorCode);
        
        // GDPR logging for authentication errors
        String currentUser = baseHandler.getCurrentUser(request);
        if (!"anonymous".equals(currentUser)) {
            log.warn("GDPR: Operation=authentication_error, FirebaseUID={}, ErrorCode={}, Purpose=authentication",
                currentUser, errorCode);
        }
        
        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);
        
        return new ResponseEntity<>(errorResponse, status);
    }
    
    /**
     * Handle authentication exceptions
     */
    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        
        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.UNAUTHORIZED, request, traceId);
        
        // Don't reveal too much about why authentication failed
        String messageKey = "error.auth.invalid_credentials";
        if (ex instanceof BadCredentialsException) {
            messageKey = "error.auth.invalid_credentials";
        }
        
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);
        
        // GDPR logging
        String currentUser = baseHandler.getCurrentUser(request);
        if (!"anonymous".equals(currentUser)) {
            log.warn("GDPR: Operation=authentication_failed, FirebaseUID={}, Purpose=security",
                currentUser);
        }
        
        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);
        
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }
    
    /**
     * Handle access denied exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        
        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.FORBIDDEN, request, traceId);
        
        String messageKey = "error.auth.insufficient_permissions";
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);
        
        // GDPR logging for access control
        String currentUser = baseHandler.getCurrentUser(request);
        if (!"anonymous".equals(currentUser)) {
            String path = baseHandler.getPath(request);
            log.warn("GDPR: Operation=access_denied, FirebaseUID={}, Resource={}, Purpose=access_control",
                currentUser, path);
        }
        
        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    /**
     * Handle Java SecurityException - catches any unhandled security violations
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleSecurityException(
            SecurityException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.FORBIDDEN, request, traceId);

        log.error("Security exception caught [trace={}]: {}", traceId, ex.getMessage());

        String messageKey = "error.security.access_denied";
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);

        // GDPR logging for security violations
        String currentUser = baseHandler.getCurrentUser(request);
        String path = baseHandler.getPath(request);
        log.warn("GDPR: Operation=security_exception, FirebaseUID={}, Resource={}, Error={}, Purpose=security",
            currentUser, path, ex.getMessage());

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }
}
