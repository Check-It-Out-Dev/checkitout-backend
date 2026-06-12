package com.sm.instagram.platform.common.exceptions.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Exception handler for storage and file upload related errors.
 * Handles Spring's MaxUploadSizeExceededException (multipart file size limit).
 * 
 * Note: Application-level storage exceptions use StorageTranslatableException with message keys
 * for better i18n support. The dead typed exceptions (FileSizeExceededException, etc.) were removed.
 */
@Slf4j
@ControllerAdvice
@Order(5)
public class StorageExceptionHandler extends BaseExceptionHandler {
    
    public StorageExceptionHandler(MessageSource messageSource) {
        super(messageSource);
    }
    
    /**
     * Handle Spring's MaxUploadSizeExceededException.
     * Thrown when multipart file exceeds spring.servlet.multipart.max-file-size
     * before it reaches our custom validation.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, WebRequest request) {

        String requestId = generateTraceId();
        logException(ex, HttpStatus.PAYLOAD_TOO_LARGE, request, requestId);

        long maxSize = ex.getMaxUploadSize();
        log.warn("Spring max upload size exceeded [requestId={}, maxSize={}]", requestId, maxSize);

        String messageKey = "error.storage.file_size_exceeded";
        Object[] args = new Object[]{"uploaded file", formatFileSize(maxSize)};
        String localizedMessage = getLocalizedMessage(messageKey, args, request);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "Payload Too Large",
                localizedMessage,
                getPath(request)
        );
        errorResponse.setRequestId(requestId);

        return new ResponseEntity<>(errorResponse, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /**
     * Format file size for user-friendly display
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }
}
