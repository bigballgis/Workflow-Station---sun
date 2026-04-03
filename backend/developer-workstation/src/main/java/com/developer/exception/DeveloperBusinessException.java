package com.developer.exception;

import lombok.Getter;

/**
 * developer-workstation 业务规则异常（与 platform-common 的 {@code com.platform.common.exception.BusinessException} 区分命名）。
 */
@Getter
public class DeveloperBusinessException extends RuntimeException {

    private final String errorCode;
    private final String businessRule;
    private final String suggestion;
    private final ErrorContext context;

    public DeveloperBusinessException(String errorCode, String message, String businessRule, ErrorContext context) {
        super(message);
        this.errorCode = errorCode;
        this.businessRule = businessRule;
        this.suggestion = null;
        this.context = context;
    }

    public DeveloperBusinessException(String errorCode, String message, String businessRule, String suggestion, ErrorContext context) {
        super(message);
        this.errorCode = errorCode;
        this.businessRule = businessRule;
        this.suggestion = suggestion;
        this.context = context;
    }

    public DeveloperBusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.businessRule = "business_rule";
        this.suggestion = null;
        this.context = ErrorContext.of("business_operation", "BusinessService");
    }

    public DeveloperBusinessException(String errorCode, String message, String businessRule) {
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

    public static DeveloperBusinessException ruleViolation(String rule, String message, ErrorContext context) {
        return new DeveloperBusinessException("BIZ_RULE_VIOLATION", message, rule, context);
    }

    public static DeveloperBusinessException withSuggestion(String errorCode, String message, String suggestion, ErrorContext context) {
        return new DeveloperBusinessException(errorCode, message, "business_rule", suggestion, context);
    }
}
