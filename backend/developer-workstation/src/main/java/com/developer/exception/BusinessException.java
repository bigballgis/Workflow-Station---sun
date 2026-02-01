package com.developer.exception;

import lombok.Getter;

/**
 * Exception for business logic violations and rule enforcement failures.
 * This is an alias for BusinessLogicException to maintain backward compatibility.
 * 
 * Requirements: 3.1, 3.4
 */
@Getter
public class BusinessException extends RuntimeException {
    
    private final String errorCode;
    private final String businessRule;
    private final String suggestion;
    private final ErrorContext context;
    
    public BusinessException(String errorCode, String message, String businessRule, ErrorContext context) {
        super(message);
        this.errorCode = errorCode;
        this.businessRule = businessRule;
        this.suggestion = null;
        this.context = context;
    }
    
    public BusinessException(String errorCode, String message, String businessRule, String suggestion, ErrorContext context) {
        super(message);
        this.errorCode = errorCode;
        this.businessRule = businessRule;
        this.suggestion = suggestion;
        this.context = context;
    }
    
    // Backward compatibility constructors
    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.businessRule = "business_rule";
        this.suggestion = null;
        this.context = ErrorContext.of("business_operation", "BusinessService");
    }
    
    public BusinessException(String errorCode, String message, String businessRule) {
        super(message);
        this.errorCode = errorCode;
        this.businessRule = businessRule;
        this.suggestion = null;
        this.context = ErrorContext.of("business_operation", "BusinessService");
    }
    
    public ErrorCategory getCategory() {
        return ErrorCategory.BUSINESS_LOGIC;
    }
    
    public ErrorSeverity getSeverity() {
        return ErrorSeverity.WARN;
    }
    
    /**
     * Create a business exception for rule violations
     */
    public static BusinessException ruleViolation(String rule, String message, ErrorContext context) {
        return new BusinessException("BIZ_RULE_VIOLATION", message, rule, context);
    }
    
    /**
     * Create a business exception with suggestion
     */
    public static BusinessException withSuggestion(String errorCode, String message, String suggestion, ErrorContext context) {
        return new BusinessException(errorCode, message, "business_rule", suggestion, context);
    }
}