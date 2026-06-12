package com.sm.instagram.platform.common.exceptions;

import java.io.Serializable;

/**
 * Exception for external service and network communication errors.
 * <p>
 * Use this exception when external API calls fail, network timeouts occur,
 * third-party services are unavailable, or integration errors happen.
 *
 * @example throw new NetworkTranslatableException("error.network.service_unavailable", "PaymentAPI");
 * @example throw new NetworkTranslatableException("error.network.timeout");
 * @example throw new NetworkTranslatableException("error.network.api_error", "Google OAuth", 500);
 */
public class NetworkTranslatableException extends TranslatableException {
    /**
     * Creates a new network exception.
     *
     * @param messageKey The i18n message key (e.g., "error.network.connection_failed")
     * @param args       Optional parameters for message formatting
     */
    public NetworkTranslatableException(String messageKey, Serializable... args) {
        super(messageKey, args);
    }
}
