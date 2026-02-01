package com.developer.exception;

import lombok.Getter;

/**
 * Exception for security-related errors including authentication,
 * authorization, and security threat detection.
 * 
 * Requirements: 3.1, 3.4
 */
@Getter
public class SecurityException extends ApplicationException {
    
    private final SecurityThreat threatType;
    private final String securityContext;
    
    public SecurityException(String errorCode, String message, SecurityThreat threatType, ErrorContext context) {
        super(errorCode, message, context);
        this.threatType = threatType;
        this.securityContext = context != null ? context.getDescription() : null;
    }
    
    public SecurityException(String errorCode, String message, SecurityThreat threatType, ErrorContext context, Throwable cause) {
        super(errorCode, message, context, cause);
        this.threatType = threatType;
        this.securityContext = context != null ? context.getDescription() : null;
    }
    
    @Override
    public ErrorCategory getCategory() {
        return ErrorCategory.SECURITY;
    }
    
    @Override
    public ErrorSeverity getSeverity() {
        switch (threatType) {
            case INJECTION_ATTEMPT:
            case UNAUTHORIZED_ACCESS:
                return ErrorSeverity.CRITICAL;
            case AUTHENTICATION_FAILURE:
            case AUTHORIZATION_FAILURE:
                return ErrorSeverity.ERROR;
            case SUSPICIOUS_ACTIVITY:
                return ErrorSeverity.WARN;
            default:
                return ErrorSeverity.ERROR;
        }
    }
    
    /**
     * Create a security exception for injection attempts
     */
    public static SecurityException injectionAttempt(String message, ErrorContext context) {
        return new SecurityException("SEC_INJECTION_ATTEMPT", message, SecurityThreat.INJECTION_ATTEMPT, context);
    }
    
    /**
     * Create a security exception for authentication failures
     */
    public static SecurityException authenticationFailure(String message, ErrorContext context) {
        return new SecurityException("SEC_AUTH_FAILED", message, SecurityThreat.AUTHENTICATION_FAILURE, context);
    }
    
    /**
     * Create a security exception for authorization failures
     */
    public static SecurityException authorizationFailure(String message, ErrorContext context) {
        return new SecurityException("SEC_AUTHZ_FAILED", message, SecurityThreat.AUTHORIZATION_FAILURE, context);
    }
    
    /**
     * Create a security exception for unauthorized access attempts
     */
    public static SecurityException unauthorizedAccess(String message, ErrorContext context) {
        return new SecurityException("SEC_UNAUTHORIZED", message, SecurityThreat.UNAUTHORIZED_ACCESS, context);
    }
}