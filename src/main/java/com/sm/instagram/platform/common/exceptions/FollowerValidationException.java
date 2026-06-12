package com.sm.instagram.platform.common.exceptions;

/**
 * Exception thrown when an influencer's follower count doesn't meet
 * the requirements of a partnership opportunity they're trying to apply to.
 * <p>
 * This exception is part of the common exceptions package as it represents
 * a general validation concern that could be reused across different parts
 * of the application dealing with follower count validation.
 */
public class FollowerValidationException extends TranslatableException {
    public FollowerValidationException(String message) {
        super(message);
    }

    public FollowerValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
