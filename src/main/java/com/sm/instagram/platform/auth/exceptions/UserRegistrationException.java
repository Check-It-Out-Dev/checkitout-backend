package com.sm.instagram.platform.auth.exceptions;

import com.sm.instagram.platform.common.exceptions.BaseException;

/**
 * Exception thrown when user registration operations fail.
 * Used for duplicate users, invalid registration data, and registration business rule violations.
 */
public class UserRegistrationException extends BaseException {
    private static final long serialVersionUID = 1L;
    
    private final String email;
    private final String userType;
    private final RegistrationFailureReason reason;

    public enum RegistrationFailureReason {
        DUPLICATE_EMAIL,
        INVALID_USER_TYPE,
        SOCIAL_DATA_REQUIRED,
        ADDRESS_REQUIRED,
        EXTERNAL_SERVICE_FAILURE,
        BUSINESS_RULE_VIOLATION
    }

    public UserRegistrationException(String message) {
        super(message);
        this.email = null;
        this.userType = null;
        this.reason = RegistrationFailureReason.BUSINESS_RULE_VIOLATION;
    }

    public UserRegistrationException(String message, String email) {
        super(message);
        this.email = email;
        this.userType = null;
        this.reason = RegistrationFailureReason.BUSINESS_RULE_VIOLATION;
    }

    public UserRegistrationException(String message, String email, String userType, RegistrationFailureReason reason) {
        super(message);
        this.email = email;
        this.userType = userType;
        this.reason = reason;
    }

    public UserRegistrationException(String message, Throwable cause) {
        super(message, cause);
        this.email = null;
        this.userType = null;
        this.reason = RegistrationFailureReason.EXTERNAL_SERVICE_FAILURE;
    }

    public UserRegistrationException(String message, String email, Throwable cause) {
        super(message, cause);
        this.email = email;
        this.userType = null;
        this.reason = RegistrationFailureReason.EXTERNAL_SERVICE_FAILURE;
    }

    public String getEmail() {
        return email;
    }

    public String getUserType() {
        return userType;
    }

    public RegistrationFailureReason getReason() {
        return reason;
    }
}