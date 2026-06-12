package com.sm.instagram.platform.common.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when reCAPTCHA validation fails
 */
@Getter
@ResponseStatus(HttpStatus.FORBIDDEN)
public class RecaptchaValidationException extends TranslatableException {
    private Double score;
    private String action;

    public RecaptchaValidationException(String message) {
        super(message);
    }

    public RecaptchaValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RecaptchaValidationException(String message, Double score, String action) {
        super(message);
        this.score = score;
        this.action = action;
    }

}
