package com.sm.instagram.platform.common.exceptions.handlers;

import com.sm.instagram.platform.common.exceptions.RateLimitTranslatableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * Exception handler for rate limiting exceptions.
 * Returns HTTP 429 Too Many Requests.
 * <p>
 * Priority: Order 1 (highest priority) to ensure rate limit errors are caught first
 */
@Slf4j
@ControllerAdvice
@Order(1)
public class RateLimitExceptionHandler {

    private final BaseExceptionHandler baseHandler;

    public RateLimitExceptionHandler(MessageSource messageSource) {
        this.baseHandler = new BaseExceptionHandler(messageSource) {
        };
    }

    /**
     * Handles RateLimitTranslatableException and returns HTTP 429 Too Many Requests.
     *
     * @param ex      The rate limit exception
     * @param request The web request
     * @return ResponseEntity with HTTP 429 and error details
     */
    @ExceptionHandler(RateLimitTranslatableException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleRateLimitException(
            RateLimitTranslatableException ex,
            WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.TOO_MANY_REQUESTS, request, traceId);

        // Get localized message using the exception's message key and args
        String localizedMessage = baseHandler.getLocalizedMessage(ex.getMessageKey(), ex.getArgs(), request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "rate_limit_exceeded",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.TOO_MANY_REQUESTS);
    }
}
