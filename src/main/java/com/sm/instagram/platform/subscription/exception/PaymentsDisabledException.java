package com.sm.instagram.platform.subscription.exception;

import com.sm.instagram.platform.common.exceptions.BusinessRuleViolationException;

/**
 * Thrown when a paying code path is reached while {@code app.payments.enabled = false}.
 *
 * <p>In a correctly configured deployment this should never fire: paid controllers are
 * hidden at the bean level, webhooks are disabled, and crons skip Stripe branches. This
 * exception exists as defense-in-depth for internal callers (test controllers, future
 * refactors) and produces HTTP 503 via a dedicated handler in
 * {@code BusinessExceptionHandler}.
 */
public class PaymentsDisabledException extends BusinessRuleViolationException {

    public PaymentsDisabledException() {
        super("error.payments.disabled", "PAYMENTS_DISABLED", null);
    }
}
