package com.developer.exception;

import lombok.Getter;

/**
 * Exception for business logic violations and rule enforcement failures.
 * Extends ApplicationException to provide proper error handling framework integration.
 * 
 * Requirements: 3.1, 3.4
 */
@Getter
public class BusinessLogicException extends ApplicationException {
    
    private final String businessRule;
    private final String suggestion;
    
    public BusinessLogicException(String errorCode, String message, String businessRule, ErrorContext context) {
        super(errorCode, message, context);
        this.businessRule = businessRule;
        this.suggestion = null;
    }
    
    public BusinessLogicException(String errorCode, String message, String businessRule, String suggestion, ErrorContext context) {
        super(errorCode, message, context);
        this.businessRule = businessRule;
        this.suggestion = suggestion;
    }
    
    @Override
    public ErrorCategory getCategory() {
        return ErrorCategory.BUSINESS_LOGIC;
    }
    
    @Override
    public ErrorSeverity getSeverity() {
        return ErrorSeverity.WARN;
    }
    
    /**
     * Create a business logic exception for rule violations
     */
    public static BusinessLogicException ruleViolation(String rule, String message, ErrorContext context) {
        return new BusinessLogicException("BIZ_RULE_VIOLATION", message, rule, context);
    }
    
    /**
     * Create a business logic exception with suggestion
     */
    public static BusinessLogicException withSuggestion(String errorCode, String message, String suggestion, ErrorContext context) {
        return new BusinessLogicException(errorCode, message, "business_rule", suggestion, context);
    }
}