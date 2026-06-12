package com.sm.instagram.platform.common.exceptions;

import lombok.Getter;

/**
 * Exception thrown when business rules are violated.
 * Used for domain-specific validation failures and business logic constraints.
 */
@Getter
public class BusinessRuleViolationException extends TranslatableException {
    private final String ruleCode;
    private final String context;

    public BusinessRuleViolationException(String message) {
        super(message);
        this.ruleCode = null;
        this.context = null;
    }

    public BusinessRuleViolationException(String message, String ruleCode) {
        super(message);
        this.ruleCode = ruleCode;
        this.context = null;
    }

    public BusinessRuleViolationException(String message, String ruleCode, String context) {
        super(message);
        this.ruleCode = ruleCode;
        this.context = context;
    }

    public BusinessRuleViolationException(String message, Throwable cause) {
        super(message, cause);
        this.ruleCode = null;
        this.context = null;
    }

}