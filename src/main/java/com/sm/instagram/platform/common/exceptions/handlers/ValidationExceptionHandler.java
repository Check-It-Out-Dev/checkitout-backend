package com.sm.instagram.platform.common.exceptions.handlers;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.sm.instagram.platform.common.util.HtmlEncoder;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exception handler for validation-related errors.
 * Handles @Valid, constraint violations, and parameter validation errors.
 */
@Slf4j
@ControllerAdvice
@Order(2)
public class ValidationExceptionHandler extends ResponseEntityExceptionHandler {
    
    /** A rejected property name is echoed back, so it is bounded before it is. */
    public static final int MAX_ECHOED_PROPERTY_LENGTH = 64;

    private final BaseExceptionHandler baseHandler;
    
    public ValidationExceptionHandler(MessageSource messageSource) {
        this.baseHandler = new BaseExceptionHandler(messageSource) {};
    }
    
    /**
     * A constraint on a controller METHOD parameter, rather than on a body.
     *
     * <p>{@code @RequestParam @Min(1) int limit} raises {@link HandlerMethodValidationException} in
     * Spring 6.1 and later, and {@link ResponseEntityExceptionHandler} renders that as an RFC 7807
     * {@code application/problem+json}. Everything else this application returns is the envelope in
     * {@link BaseExceptionHandler.ErrorResponse}, so the API had two error shapes and the document
     * could only describe one of them: {@code GET /admin/cascade-delete/orphans?limit=0} came back
     * as problem+json and Schemathesis reported an undocumented content type.
     *
     * <p>The shape a caller has to parse should not depend on which annotation the constraint was
     * written on. This override is the whole difference.
     */
    @Override
    public ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String traceId = baseHandler.generateTraceId();

        // The parameter name and the message the constraint carries, both encoded: a rejected value
        // is the caller's own text and travels back to them.
        Map<String, String> validationErrors = new HashMap<>();
        ex.getAllValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> validationErrors.put(
                    HtmlEncoder.encode(name != null ? name : "parameter"),
                    HtmlEncoder.encode(String.valueOf(error.getDefaultMessage()))));
        });

        log.warn("VALIDATION_ERROR [{}]: parameterErrors={}",
                baseHandler.buildDetailedRequestContext(request, traceId), validationErrors);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                BaseExceptionHandler.BAD_REQUEST,
                baseHandler.getLocalizedMessage("error.validation.failed", null, request),
                baseHandler.getPath(request)
        );
        errorResponse.setValidationErrors(validationErrors);
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle validation errors from @Valid annotation
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        
        String traceId = baseHandler.generateTraceId();
        
        Map<String, String> validationErrors = new HashMap<>();
        // Extract field errors - XSS Fix: Encode field names and messages
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(
                HtmlEncoder.encode(error.getField()), 
                HtmlEncoder.encode(error.getDefaultMessage())
            );
        }
        // Extract object errors - XSS Fix: Encode object names and messages
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            validationErrors.put(
                HtmlEncoder.encode(error.getObjectName()), 
                HtmlEncoder.encode(error.getDefaultMessage())
            );
        }
        
        // Single comprehensive log entry with all details
        String detailedRequestInfo = baseHandler.buildDetailedRequestContext(request, traceId);
        log.warn("VALIDATION_ERROR [{}]: validationErrors={}", detailedRequestInfo, validationErrors);
        
        // GDPR logging if validation involves user data
        String currentUser = baseHandler.getCurrentUser(request);
        if (!"anonymous".equals(currentUser)) {
            log.info("GDPR: Operation=validation_error, FirebaseUID={}, ErrorType=validation, Purpose=error_tracking",
                currentUser);
        }
        
        String messageKey = "error.validation.failed";
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);
        
        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                BaseExceptionHandler.BAD_REQUEST,
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setValidationErrors(validationErrors);
        errorResponse.setRequestId(traceId);
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handle constraint validation errors
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        
        String traceId = baseHandler.generateTraceId();
        
        Map<String, String> validationErrors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        violation -> HtmlEncoder.encode(violation.getPropertyPath().toString()),
                        violation -> HtmlEncoder.encode(violation.getMessage()),
                        (existing, replacement) -> existing // Keep first error if duplicate keys
                ));
        
        // Single comprehensive log entry
        String detailedRequestInfo = baseHandler.buildDetailedRequestContext(request, traceId);
        log.warn("CONSTRAINT_VIOLATION [{}]: violations={}", detailedRequestInfo, validationErrors);
        
        String messageKey = "error.validation.failed";
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);
        
        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                BaseExceptionHandler.BAD_REQUEST,
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setValidationErrors(validationErrors);
        errorResponse.setRequestId(traceId);
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handle malformed JSON requests
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        
        String traceId = baseHandler.generateTraceId();
        
        String messageKey;
        if (ex.getCause() instanceof JsonParseException) {
            messageKey = "error.validation.invalid_json";
        } else if (ex.getCause() instanceof JsonMappingException) {
            messageKey = "error.validation.invalid_structure";
        } else {
            messageKey = "error.validation.invalid_json";
        }
        
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);
        
        // Single comprehensive log entry
        String detailedRequestInfo = baseHandler.buildDetailedRequestContext(request, traceId);
        log.warn("JSON_PARSING_ERROR [{}]: message={}, rootCause={}",
                detailedRequestInfo, localizedMessage, ex.getRootCause() != null ? ex.getRootCause().getMessage() : "Unknown");
        
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
     * Handle missing request parameters
     */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        
        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.BAD_REQUEST, request, traceId);
        
        String messageKey = "error.validation.missing_parameter";
        Object[] args = new Object[]{HtmlEncoder.encode(ex.getParameterName())};
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, args, request);
        
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
     * Handle method argument type mismatch
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        
        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.BAD_REQUEST, request, traceId);
        
        String messageKey = "error.validation.type_mismatch";
        Object[] args = new Object[]{
                HtmlEncoder.encode(String.valueOf(ex.getValue())), 
                HtmlEncoder.encode(ex.getName()), 
                HtmlEncoder.encode(ex.getRequiredType().getSimpleName())
        };
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, args, request);
        
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
     * Handle a {@code sort} (or any Spring Data property path) naming a property that does not
     * exist on the entity.
     *
     * <p>Every {@code /paged} endpoint inherits {@code findPaginated(Pageable, Map)} from
     * {@code BaseController}, and Spring binds {@code ?sort=<anything>} into that Pageable without
     * checking it against the entity. The check happens later, when Spring Data derives the query,
     * and a bad name arrives as {@link PropertyReferenceException} from deep inside the repository
     * -- which the default handler reads as a server fault. Thirteen paged endpoints answered 500
     * to {@code ?sort=AAA} until this existed; Schemathesis found all thirteen in one run.
     *
     * <p>It is a 400: the caller named a column that is not there. What comes back is the caller's
     * own value and nothing else. The exception's message carries the entity type and a
     * "Did you mean 'id'" suggestion built from the entity's fields, and echoing that would turn a
     * validation error into a schema oracle for anyone probing the API.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handlePropertyReferenceException(
            PropertyReferenceException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.BAD_REQUEST, request, traceId);

        String property = ex.getPropertyName() == null ? "" : ex.getPropertyName();
        if (property.length() > MAX_ECHOED_PROPERTY_LENGTH) {
            property = property.substring(0, MAX_ECHOED_PROPERTY_LENGTH);
        }
        String localizedMessage = baseHandler.getLocalizedMessage(
                "error.validation.unknown_sort_property",
                new Object[]{HtmlEncoder.encode(property)},
                request);

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
     * Handle a request Spring Security's {@code StrictHttpFirewall} refused to let through.
     *
     * <p>The firewall rejects a URL or a parameter name containing control characters, and it
     * throws from inside parameter binding -- so the same thirteen {@code /paged} endpoints that
     * inherit {@code findPaginated(Pageable, Map<String, String>)} met it, because binding the
     * filter map is the first thing that asks the container to parse every parameter name. The
     * catch-all read the resulting {@link RequestRejectedException} as a server fault and answered
     * 500; a request the server refused to parse is the caller's fault, and 400 is what the
     * document already promises for every operation that takes a parameter.
     *
     * <p>Nothing from the request is echoed. The rejected name is by definition the part that
     * contained the control characters, it reaches the log through
     * {@link BaseExceptionHandler#logException}, and reflecting it would put an attacker's bytes
     * back into a response body.
     */
    @ExceptionHandler(RequestRejectedException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleRequestRejectedException(
            RequestRejectedException ex, WebRequest request) {

        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.BAD_REQUEST, request, traceId);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                BaseExceptionHandler.BAD_REQUEST,
                baseHandler.getLocalizedMessage("error.validation.malformed_request", null, request),
                baseHandler.getPath(request)
        );
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle date/time parsing exceptions
     */
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleDateTimeParseException(
            DateTimeParseException ex, WebRequest request) {
        
        String traceId = baseHandler.generateTraceId();
        baseHandler.logException(ex, HttpStatus.BAD_REQUEST, request, traceId);
        
        // Extract the problematic text from the exception message
        String problematicText = ex.getParsedString();
        
        String messageKey = "error.validation.date_format_invalid";
        Object[] args = new Object[]{problematicText != null ? problematicText : "unknown"};
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, args, request);
        
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
     * Handle form binding exceptions (@ModelAttribute)
     * Mirrors MethodArgumentNotValidException handler for consistency.
     * Standalone @ExceptionHandler (not @Override) for Spring 6.2+ compatibility.
     */
    @ExceptionHandler(org.springframework.validation.BindException.class)
    public ResponseEntity<BaseExceptionHandler.ErrorResponse> handleBindException(
            org.springframework.validation.BindException ex,
            WebRequest request) {

        String traceId = baseHandler.generateTraceId();

        Map<String, String> validationErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String field = HtmlEncoder.encode(error.getField());
            String message = error.getDefaultMessage() != null ?
                    HtmlEncoder.encode(error.getDefaultMessage()) :
                    "Validation error";
            validationErrors.put(field, message);
        }
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            String objectName = HtmlEncoder.encode(error.getObjectName());
            String message = error.getDefaultMessage() != null ?
                    HtmlEncoder.encode(error.getDefaultMessage()) :
                    "Validation error";
            validationErrors.put(objectName, message);
        }

        String detailedRequestInfo = baseHandler.buildDetailedRequestContext(request, traceId);
        log.warn("BIND_ERROR [{}]: validationErrors={}", detailedRequestInfo, validationErrors);

        String messageKey = "error.validation.failed";
        String localizedMessage = baseHandler.getLocalizedMessage(messageKey, null, request);

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                BaseExceptionHandler.BAD_REQUEST,
                localizedMessage,
                baseHandler.getPath(request)
        );
        errorResponse.setValidationErrors(validationErrors);
        errorResponse.setRequestId(traceId);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
}
