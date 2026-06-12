package com.sm.instagram.platform.common.exceptions.handlers;

import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.NetworkRetryExhaustedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * Exception handler for network-related errors.
 * Handles connection resets, timeouts, and external service failures.
 */
@Slf4j
@ControllerAdvice
@Order(1) // High priority for network errors
public class NetworkExceptionHandler extends BaseExceptionHandler {
    
    public NetworkExceptionHandler(MessageSource messageSource) {
        super(messageSource);
    }
    
    /**
     * Handle network retry exhausted exceptions
     */
    @ExceptionHandler(NetworkRetryExhaustedException.class)
    public ResponseEntity<ErrorResponse> handleNetworkRetryExhaustedException(
            NetworkRetryExhaustedException ex, WebRequest request) {
        
        String traceId = generateTraceId();
        
        // Log detailed error information
        log.error("Network retries exhausted [trace={}, service={}, attempts={}, lastError={}]",
                traceId, ex.getServiceName(), ex.getAttemptsMade(), 
                ex.getLastError() != null ? ex.getLastError().getMessage() : "N/A");
        
        // Determine message key based on error type
        String messageKey;
        Object[] args = new Object[]{ex.getServiceName(), ex.getAttemptsMade()};
        
        if (ex.getLastError() instanceof SocketException || 
            ex.getLastError() instanceof ConnectException) {
            messageKey = "error.network.connection_failed";
        } else if (ex.getLastError() instanceof SocketTimeoutException) {
            messageKey = "error.network.timeout";
        } else {
            messageKey = "error.network.retry_exhausted";
        }
        
        // Get localized message
        String localizedMessage = getLocalizedMessage(messageKey, args, request);
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Service Temporarily Unavailable",
                localizedMessage,
                getPath(request)
        );
        errorResponse.setRequestId(traceId);
        
        logException(ex, HttpStatus.SERVICE_UNAVAILABLE, request, traceId);
        
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    /**
     * Handle external service exceptions
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalServiceException(
            ExternalServiceException ex, WebRequest request) {
        
        String traceId = generateTraceId();
        
        // Determine HTTP status based on external service status
        HttpStatus status = determineStatusFromExternalService(ex);
        
        logException(ex, status, request, traceId);
        
        // Additional external service context
        log.error("External service failure details [trace={}, service={}, operation={}, statusCode={}]",
                traceId, ex.getServiceName(), ex.getOperation(), ex.getStatusCode());
        
        // Determine message key based on error type and service
        String messageKey;
        Object[] args = new Object[]{ex.getServiceName()};
        
        if (status.is5xxServerError() && !ex.getServiceName().equals("Instagram")) {
            messageKey = "error.network.external_service_unavailable";
        } else if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            messageKey = "error.auth.external_service_unauthorized";
        } else if (status == HttpStatus.NOT_FOUND) {
            messageKey = "error.network.external_service_not_found";
        } else if (status == HttpStatus.TOO_MANY_REQUESTS) {
            messageKey = "error.network.external_service_rate_limited";
        } else {
            messageKey = "error.network.external_service_error";
        }
        
        // For OAuth/Instagram errors, use the original message if it's user-friendly
        String localizedMessage;
        if (ex.getServiceName().equals("Instagram") && ex.getMessage() != null && 
            !ex.getMessage().contains("HTTP") && !ex.getMessage().contains("Exception")) {
            localizedMessage = ex.getMessage(); // Keep Instagram's user-friendly messages
        } else {
            localizedMessage = getLocalizedMessage(messageKey, args, request);
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                status.value(),
                "External Service Error",
                localizedMessage,
                getPath(request)
        );
        errorResponse.setRequestId(traceId);
        
        return new ResponseEntity<>(errorResponse, status);
    }
    
    /**
     * Handle WebClient request exceptions (connection issues)
     */
    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ErrorResponse> handleWebClientRequestException(
            WebClientRequestException ex, WebRequest request) {
        
        String traceId = generateTraceId();
        logException(ex, HttpStatus.BAD_GATEWAY, request, traceId);
        
        String messageKey;
        Object[] args = new Object[0];
        
        if (ex.getCause() instanceof SocketTimeoutException) {
            messageKey = "error.network.external_service_timeout";
        } else if (ex.getCause() instanceof ConnectException) {
            messageKey = "error.network.external_service_connection_failed";
        } else {
            messageKey = "error.network.external_service_connection_error";
        }
        
        String localizedMessage = getLocalizedMessage(messageKey, args, request);
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_GATEWAY.value(),
                "Bad Gateway",
                localizedMessage,
                getPath(request)
        );
        errorResponse.setRequestId(traceId);
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_GATEWAY);
    }
    
    /**
     * Handle WebClient response exceptions (HTTP errors from external services)
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientResponseException(
            WebClientResponseException ex, WebRequest request) {
        
        String traceId = generateTraceId();
        
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        if (status.is5xxServerError()) {
            status = HttpStatus.BAD_GATEWAY;
        }
        
        logException(ex, status, request, traceId);
        
        String messageKey = "error.network.external_service_http_error";
        Object[] args = new Object[]{ex.getStatusCode().value()};
        String localizedMessage = getLocalizedMessage(messageKey, args, request);
        
        ErrorResponse errorResponse = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                localizedMessage,
                getPath(request)
        );
        errorResponse.setRequestId(traceId);
        
        return new ResponseEntity<>(errorResponse, status);
    }
    
    private HttpStatus determineStatusFromExternalService(ExternalServiceException ex) {
        int statusCode = ex.getStatusCode();
        if (statusCode == 401 || statusCode == 403) return HttpStatus.UNAUTHORIZED;
        if (statusCode == 404) return HttpStatus.NOT_FOUND;
        if (statusCode == 429) return HttpStatus.TOO_MANY_REQUESTS;
        if (statusCode >= 400 && statusCode < 500) return HttpStatus.BAD_REQUEST;
        if (statusCode >= 500) return HttpStatus.BAD_GATEWAY;
        return HttpStatus.BAD_GATEWAY;
    }
}
