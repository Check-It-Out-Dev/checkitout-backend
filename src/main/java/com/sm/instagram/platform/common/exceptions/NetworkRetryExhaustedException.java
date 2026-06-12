package com.sm.instagram.platform.common.exceptions;

import lombok.Getter;

/**
 * Exception thrown when network retries are exhausted.
 * <p>
 * This follows the translatable exception pattern using an i18n message key and optional args.
 * Use it for transient network failures (e.g., connection resets) that continue to fail after retries.
 * <p>
 * Example usage:
 * throw new NetworkRetryExhaustedException(
 * "error.network.retry_exhausted",
 * "PaymentAPI",
 * 3,
 * lastException
 * );
 */
@Getter
public class NetworkRetryExhaustedException extends TranslatableException {
    private final String serviceName;
    private final int attemptsMade;
    private final Throwable lastError;

    /**
     * Create a new NetworkRetryExhaustedException.
     *
     * @param messageKey   The i18n message key (e.g., "error.network.retry_exhausted")
     * @param serviceName  Name of the external service that was called
     * @param attemptsMade Number of retry attempts that were made
     * @param cause        The underlying cause of the failure (last error seen)
     */
    public NetworkRetryExhaustedException(String messageKey, String serviceName, int attemptsMade, Throwable cause) {
        // Pass args so they can be used in the translatable message template if desired
        super(messageKey, serviceName, attemptsMade);
        this.serviceName = serviceName;
        this.attemptsMade = attemptsMade;
        this.lastError = cause;
        if (cause != null) {
            initCause(cause);
        }
    }

    /**
     * Create a new NetworkRetryExhaustedException without a cause.
     *
     * @param messageKey   The i18n message key (e.g., "error.network.retry_exhausted")
     * @param serviceName  Name of the external service that was called
     * @param attemptsMade Number of retry attempts that were made
     */
    public NetworkRetryExhaustedException(String messageKey, String serviceName, int attemptsMade) {
        this(messageKey, serviceName, attemptsMade, null);
    }

    @Override
    public String toString() {
        return String.format(
                "NetworkRetryExhaustedException{service='%s', attempts=%d, messageKey='%s', lastError=%s}",
                serviceName,
                attemptsMade,
                getMessage(), // Falls back to messageKey in TranslatableException
                lastError != null ? lastError.getClass().getSimpleName() : "none"
        );
    }
}