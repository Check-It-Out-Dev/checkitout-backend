package com.sm.instagram.platform.common.exceptions;

import com.sm.instagram.platform.common.exceptions.handlers.BaseExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Global default exception handler for unhandled exceptions.
 * Acts as a fallback for exceptions not caught by specialized handlers.
 * Most specific exception handling is delegated to:
 * - NetworkExceptionHandler (network/external service errors)
 * - ValidationExceptionHandler (validation errors)
 * - AuthenticationExceptionHandler (auth/security errors)
 * - BusinessExceptionHandler (business logic errors)
 * - StorageExceptionHandler (file storage errors)
 */
@Slf4j
@ControllerAdvice
@Order(100) // Lowest priority - acts as fallback
public class GlobalDefaultExceptionHandler extends ResponseEntityExceptionHandler {

    private final BaseExceptionHandler baseHandler;

    @Autowired
    public GlobalDefaultExceptionHandler(MessageSource messageSource) {
        this.baseHandler = new BaseExceptionHandler(messageSource) {};
    }

    /**
     * Handle all TranslatableException and its subclasses
     */
    @ExceptionHandler({
        TranslatableException.class,
        AuthenticationTranslatableException.class,
        ValidationTranslatableException.class,
        BusinessRuleTranslatableException.class,
        StorageTranslatableException.class,
        NetworkTranslatableException.class,
        ResourceNotFoundException.class
    })
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleTranslatableException(
            TranslatableException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();

        // Convert Serializable[] to Object[] properly to avoid array wrapping issues
        Object[] args = null;
        if (ex.getArgs() != null && ex.getArgs().length > 0) {
            args = new Object[ex.getArgs().length];
            System.arraycopy(ex.getArgs(), 0, args, 0, ex.getArgs().length);
        }

        // Get localized message from properties file
        String localizedMessage = baseHandler.getLocalizedMessage(
            ex.getMessageKey(), 
            args, 
            request
        );

        // Determine HTTP status based on exception type
        HttpStatus status = determineStatus(ex);

        baseHandler.logException(ex, status, request, traceId);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);
        errorResponse.setMessageKey(ex.getMessageKey());

        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * Determine HTTP status based on TranslatableException type
     */
    private HttpStatus determineStatus(TranslatableException ex) {
        if (ex instanceof AuthenticationTranslatableException) {
            return HttpStatus.UNAUTHORIZED;
        } else if (ex instanceof ValidationTranslatableException) {
            return HttpStatus.BAD_REQUEST;
        } else if (ex instanceof BusinessRuleTranslatableException) {
            return HttpStatus.CONFLICT;
        } else if (ex instanceof StorageTranslatableException) {
            return HttpStatus.INSUFFICIENT_STORAGE;
        } else if (ex instanceof NetworkTranslatableException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        } else if (ex instanceof ResourceNotFoundException) {
            return HttpStatus.NOT_FOUND;
        } else if (ex instanceof ItemNotFoundException) {
            return HttpStatus.NOT_FOUND;
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * Handle unsupported HTTP methods
     */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.METHOD_NOT_ALLOWED, request, traceId);

        String supportedMethods = ex.getSupportedHttpMethods() != null ?
                ex.getSupportedHttpMethods().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(", ")) : "";

        String messageKey = "error.http.method_not_allowed";
        Object[] args = new Object[]{ex.getMethod(), supportedMethods};
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, args, request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "Method Not Allowed",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Handle unsupported media types
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE, request, traceId);

        String supportedTypes = ex.getSupportedMediaTypes().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));

        String messageKey = "error.http.unsupported_media_type";
        Object[] args = new Object[]{ex.getContentType(), supportedTypes};
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, args, request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "Unsupported Media Type",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * Handle 404 - No handler found
     */
    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.NOT_FOUND, request, traceId);

        String messageKey = "error.http.not_found";
        Object[] args = new Object[]{ex.getHttpMethod(), ex.getRequestURL()};
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, args, request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                BaseExceptionHandler.NOT_FOUND,
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle runtime exceptions with specific messages
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleRuntimeException(
            RuntimeException ex, WebRequest request) {

        // Check for specific Firebase deletion error
        if (ex.getMessage() != null && ex.getMessage().contains("Failed to delete user from Firebase Authentication")) {
            String traceId = baseHandler.generateTraceId();
            baseHandler.logException(ex, HttpStatus.INTERNAL_SERVER_ERROR, request, traceId);

            String messageKey = "error.auth.service_unavailable";
            String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);

            BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    BaseExceptionHandler.INTERNAL_SERVER_ERROR,
                    localizedMessage,
                    baseHandler.getPath(request)
            );
            errorResponse.setRequestId(traceId);

            return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Log and handle as general exception
        log.error("Unexpected runtime exception: {}", ex.getMessage(), ex);
        return handleGeneralException(ex, request);
    }

    /**
     * Handle all other exceptions - Ultimate fallback
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleGeneralException(
            Exception ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        
        // Log with full details for debugging
        String detailedRequestInfo = baseHandler.buildDetailedRequestContext(request, traceId);
        log.error("UNHANDLED_EXCEPTION [{}]: {}", detailedRequestInfo, ex.getMessage(), ex);

        // For security, don't expose internal error details to client
        String messageKey = "error.general.unexpected";
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                BaseExceptionHandler.INTERNAL_SERVER_ERROR,
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Override handleExceptionInternal to use our custom error response format
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        HttpStatus status = HttpStatus.valueOf(statusCode.value());

        baseHandler.logException(ex, status, request, traceId);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                status.getReasonPhrase(),  // Hardened 2026-06: generic message only, no raw framework exception text in responses (pentest info-disclosure)
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, headers, status);
    }
}
