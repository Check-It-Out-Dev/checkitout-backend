package com.sm.instagram.platform.common.exceptions.handlers;

import com.sm.instagram.platform.common.exceptions.*;
import com.sm.instagram.platform.subscription.exception.PaymentsDisabledException;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.MappingException;
import org.modelmapper.spi.ErrorMessage;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Exception handler for business logic and domain exceptions.
 * Handles business rules, data integrity, and domain-specific errors.
 */
@Slf4j
@ControllerAdvice
@Order(4)
public class BusinessExceptionHandler {

    private final BaseExceptionHandler baseHandler;

    public BusinessExceptionHandler(MessageSource messageSource) {
        this.baseHandler = new BaseExceptionHandler(messageSource) {
        };
    }

    /**
     * Handle custom ItemNotFoundException
     */
    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleItemNotFoundException(
            ItemNotFoundException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.NOT_FOUND, request, traceId);

        String localizedMessage = baseHandler.getLocalizedMessage(
                ex.getMessageKey(),
                ex.getArgs(),
                request
        );

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
     * Handle custom ResourceNotFoundException
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.NOT_FOUND, request, traceId);

        String localizedMessage = baseHandler.getLocalizedMessage(
                ex.getMessageKey(),
                ex.getArgs(),
                request
        );


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
     * Handle consent required exceptions — HTTP 451 (Unavailable For Legal Reasons)
     */
    @ExceptionHandler(ConsentRequiredTranslatableException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleConsentRequiredException(
            ConsentRequiredTranslatableException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        HttpStatus status = HttpStatus.valueOf(451);
        baseHandler.logException(ex, status, request, traceId);

        String localizedMessage = baseHandler.getLocalizedMessage(
                ex.getMessageKey(), ex.getArgs(), request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                451, "Unavailable For Legal Reasons", localizedMessage, baseHandler.getPath(request));
        errorResponse.setRequestId(traceId);
        errorResponse.setMessageKey(ex.getMessageKey());

        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * Handle payments-disabled violations — HTTP 503 (Service Unavailable).
     * Dedicated handler must be declared BEFORE the generic BusinessRuleViolationException
     * handler so Spring's most-specific-first resolution picks this one up.
     */
    @ExceptionHandler(PaymentsDisabledException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handlePaymentsDisabledException(
            PaymentsDisabledException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.SERVICE_UNAVAILABLE, request, traceId);

        String localizedMessage = baseHandler.getLocalizedMessage(
                ex.getMessageKey(),
                ex.getArgs(),
                request
        );

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Service Unavailable",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);
        errorResponse.setMessageKey(ex.getMessageKey());

        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Handle business rule violation exceptions
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleBusinessRuleViolationException(
            BusinessRuleViolationException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.BAD_REQUEST, request, traceId);

        // Additional business context logging
        log.debug("Business rule violation details [trace={}, rule={}, context={}]",
                traceId, ex.getRuleCode(), ex.getContext());

        String localizedMessage = baseHandler.getLocalizedMessage(
                ex.getMessageKey(),
                ex.getArgs(),
                request
        );


        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Business Rule Violation",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle follower validation exceptions
     */
    @ExceptionHandler(FollowerValidationException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleFollowerValidationException(
            FollowerValidationException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.BAD_REQUEST, request, traceId);

        String messageKey = "error.business.rule_violation";
        String localizedMessage = baseHandler.getLocalizedMessage(
                ex.getMessageKey(),
                ex.getArgs(),
                request
        );

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Follower Validation Failed",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle insufficient permissions exceptions
     */
    @ExceptionHandler(InsufficientPermissionsException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleInsufficientPermissionsException(
            InsufficientPermissionsException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.FORBIDDEN, request, traceId);

        log.debug("Insufficient permissions details [trace={}, user={}, operation={}, resource={}]",
                traceId, ex.getUserId(), ex.getOperation(), ex.getResource());

        // GDPR logging for access control
        if (ex.getUserId() != null) {
            log.warn("GDPR: Operation=access_denied, FirebaseUID={}, RequestedOperation={}, Resource={}, Purpose=access_control",
                    ex.getUserId(), ex.getOperation(), ex.getResource());
        }

        String messageKey = "error.business.insufficient_permissions";
        String localizedMessage = baseHandler.getLocalizedMessage(
                ex.getMessageKey(),
                ex.getArgs(),
                request
        );

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Insufficient Permissions",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.BAD_REQUEST, request, traceId);

        String messageKey = "error.business.invalid_argument";
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                BaseExceptionHandler.BAD_REQUEST,
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle illegal state exceptions (business logic state violations)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleIllegalStateException(
            IllegalStateException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.CONFLICT, request, traceId);

        String messageKey = "error.business.invalid_state";
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handle a ModelMapper failure by the converter failure underneath it.
     *
     * <p>{@link MappingException} carries no cause: its only constructor takes a list of
     * {@link ErrorMessage}, and each of those holds the throwable a converter raised. So walking
     * {@code getCause()} finds nothing however deep it goes, and this handler answered 500 to
     * every converter failure -- {@code POST /user-social-connection} with {@code "platform": 0}
     * came back as a server fault when the caller had simply named a platform that does not exist.
     *
     * <p>Seven converters signal a missing reference this way ({@code Currency not found: 99},
     * {@code Platforms not found: [...]}, and so on), so the shape of the answer is one decision,
     * not seven: a {@link TranslatableException} is a 404 with its own message, an
     * {@link IllegalArgumentException} is a 400, and anything else really is ours to own as a 500.
     *
     * <p>Neither 400 nor 404 echoes the converter's message. It names the entity and the id the
     * lookup missed -- "Platform not found for id: 0" -- which answers a probe for what exists
     * with what exists.
     */
    @ExceptionHandler(MappingException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleMappingException(
            MappingException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();

        TranslatableException translatable = firstCause(ex, TranslatableException.class);
        if (translatable != null) {
            baseHandler.logException(ex, HttpStatus.NOT_FOUND, request, traceId);

            String localizedMessage = baseHandler.getLocalizedMessage(translatable.getMessageKey(), translatable.getArgs(), request);

            BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                    HttpStatus.NOT_FOUND.value(),
                    BaseExceptionHandler.NOT_FOUND,
                    localizedMessage,
                    baseHandler.getPath(request)
            );
            errorResponse.setRequestId(traceId);

            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        }

        // A converter refusing an id the caller supplied, e.g. "Currency not found: 99".
        if (firstCause(ex, IllegalArgumentException.class) != null) {
            baseHandler.logException(ex, HttpStatus.BAD_REQUEST, request, traceId);

            String messageKey = "error.business.invalid_argument";
            String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);

            BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    BaseExceptionHandler.BAD_REQUEST,
                    localizedMessage,
                    baseHandler.getPath(request)
            );
            errorResponse.setRequestId(traceId);

            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }

        // Default to server error for unknown mapping issues
        baseHandler.logException(ex, HttpStatus.INTERNAL_SERVER_ERROR, request, traceId);

        String messageKey = "error.general.internal_server";
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

    /** How deep a wrapped converter failure is worth following before giving up. */
    private static final int MAX_CAUSE_DEPTH = 10;

    /**
     * The first throwable of the given type anywhere under a mapping failure: in the exception's
     * own cause chain, or in the chain of any of the converter errors it aggregates.
     */
    static <T extends Throwable> T firstCause(MappingException ex, Class<T> type) {
        List<Throwable> roots = new ArrayList<>();
        roots.add(ex.getCause());
        if (ex.getErrorMessages() != null) {
            ex.getErrorMessages().forEach(error -> roots.add(error.getCause()));
        }
        for (Throwable root : roots) {
            for (int depth = 0; root != null && depth < MAX_CAUSE_DEPTH; depth++) {
                if (type.isInstance(root)) {
                    return type.cast(root);
                }
                root = root.getCause() == root ? null : root.getCause();
            }
        }
        return null;
    }

    /**
     * Handle data integrity violations
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.CONFLICT, request, traceId);

        // Log additional database context
        String rootCause = ex.getRootCause() != null ? ex.getRootCause().getMessage() : "Unknown";
        log.error("Database constraint violation [trace={}, rootCause={}]", traceId, rootCause);

        String userMessage = mapDatabaseError(rootCause, request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                userMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    private String mapDatabaseError(String rootCause, WebRequest request) {
        if (rootCause.contains("duplicate key") || rootCause.contains("unique constraint")) {
            String messageKey = "error.business.duplicate_entry";
            return baseHandler.getLocalizedMessage(messageKey, null, request);
        } else if (rootCause.contains("foreign key constraint")) {
            String messageKey = "error.business.foreign_key";
            return baseHandler.getLocalizedMessage(messageKey, null, request);
        } else if (rootCause.contains("not null constraint")) {
            String messageKey = "error.business.not_null";
            return baseHandler.getLocalizedMessage(messageKey, null, request);
        }
        String messageKey = "error.business.data_integrity";
        return baseHandler.getLocalizedMessage(messageKey, null, request);
    }

    /**
     * Handle optimistic locking failures (concurrent updates detected)
     * This is thrown when @Version mismatch occurs - two users edited same record concurrently.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleOptimisticLocking(
            OptimisticLockingFailureException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        
        // Log at WARN level - optimistic lock failures are expected in concurrent scenarios
        log.warn("Optimistic locking failure detected [trace={}]: {}", traceId, ex.getMessage());
        baseHandler.logException(ex, HttpStatus.CONFLICT, request, traceId);

        String messageKey = "error.database.concurrent_modification";
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handle database query timeouts
     */
    @ExceptionHandler(QueryTimeoutException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleQueryTimeout(
            QueryTimeoutException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.REQUEST_TIMEOUT, request, traceId);

        log.error("Database query timeout [trace={}]: {}", traceId, ex.getMessage());

        String messageKey = "error.database.query_timeout";
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.REQUEST_TIMEOUT.value(),
                "Request Timeout",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.REQUEST_TIMEOUT);
    }

    /**
     * Handle database deadlocks (transaction lost deadlock resolution)
     */
    @ExceptionHandler(DeadlockLoserDataAccessException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleDeadlock(
            DeadlockLoserDataAccessException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();

        // Log at WARN level - deadlocks are expected under high concurrency, not errors
        log.warn("Database deadlock detected [trace={}]: {}", traceId, ex.getMessage());
        baseHandler.logException(ex, HttpStatus.CONFLICT, request, traceId);

        String messageKey = "error.database.deadlock";
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }
}
