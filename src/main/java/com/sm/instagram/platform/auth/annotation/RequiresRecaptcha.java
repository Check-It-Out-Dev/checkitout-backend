package com.sm.instagram.platform.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark endpoints that require reCAPTCHA validation
 * 
 * Usage:
 * @RequiresRecaptcha(action = "LOGIN")
 * public ResponseEntity<?> login(...) { ... }
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRecaptcha {
    
    /**
     * The expected action name for this endpoint
     * Must match the action used when generating the token on frontend
     * Examples: LOGIN, SIGNUP, FORGOT_PASSWORD
     */
    String action();
    
    /**
     * Minimum score required for this action
     * Defaults to the configured threshold for the action
     */
    double minScore() default -1.0; // -1 means use default from config
    
    /**
     * Whether to block the request if reCAPTCHA validation fails
     * If false, logs the failure but allows the request to proceed
     */
    boolean enforceValidation() default true;
}
