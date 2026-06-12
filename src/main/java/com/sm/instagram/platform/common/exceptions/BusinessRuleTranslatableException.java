package com.sm.instagram.platform.common.exceptions;

import java.io.Serializable;

/**
 * Exception for business logic and rule violations.
 * <p>
 * Use this exception when business rules are violated, operations are not allowed
 * due to business constraints, or domain-specific conditions are not met.
 *
 * @example throw new BusinessRuleTranslatableException("error.business.insufficient_balance");
 * @example throw new BusinessRuleTranslatableException("error.business.duplicate_entry", "username");
 * @example throw new BusinessRuleTranslatableException("error.business.operation_not_allowed", "delete");
 */
public class BusinessRuleTranslatableException extends TranslatableException {
    /**
     * Creates a new business rule exception.
     *
     * @param messageKey The i18n message key (e.g., "error.business.account_locked")
     * @param args       Optional parameters for message formatting
     */
    public BusinessRuleTranslatableException(String messageKey, Serializable... args) {
        super(messageKey, args);
    }
}
