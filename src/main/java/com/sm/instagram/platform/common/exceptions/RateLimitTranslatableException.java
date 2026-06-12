package com.sm.instagram.platform.common.exceptions;

import java.io.Serializable;

/**
 * Exception for rate limiting errors.
 * <p>
 * Use this exception when rate limits are exceeded, too many requests are made,
 * or upload/API quotas are reached.
 *
 * @example throw new RateLimitTranslatableException("error.storage.rate_limit_exceeded");
 * @example throw new RateLimitTranslatableException("error.api.rate_limit", "100", "hour");
 * @example throw new RateLimitTranslatableException("error.upload.daily_limit_reached", "50");
 */
public class RateLimitTranslatableException extends TranslatableException {
    /**
     * Creates a new rate limit exception.
     *
     * @param messageKey The i18n message key (e.g., "error.storage.rate_limit_exceeded")
     * @param args       Optional parameters for message formatting
     */
    public RateLimitTranslatableException(String messageKey, Serializable... args) {
        super(messageKey, args);
    }
}
