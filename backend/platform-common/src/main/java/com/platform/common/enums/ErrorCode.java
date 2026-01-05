package com.platform.common.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Platform-wide error codes following a structured convention.
 * Format: XXXX where:
 * - 1xxx: Authentication errors
 * - 2xxx: Authorization/Permission errors
 * - 3xxx: Business logic errors
 * - 4xxx: Validation errors
 * - 5xxx: System/Infrastructure errors
 */
@Getter
public enum ErrorCode {
    
    // Authentication errors (1xxx)
    UNAUTHORIZED("1000", "Unauthorized", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID("1001", "Invalid authentication token", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRED("1002", "Authentication token expired", HttpStatus.UNAUTHORIZED),
    AUTH_CREDENTIALS_INVALID("1003", "Invalid credentials", HttpStatus.UNAUTHORIZED),
    AUTH_ACCOUNT_LOCKED("1004", "Account is locked", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_DISABLED("1005", "Account is disabled", HttpStatus.FORBIDDEN),
    AUTH_SESSION_EXPIRED("1006", "Session has expired", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_TOKEN_INVALID("1007", "Invalid refresh token", HttpStatus.UNAUTHORIZED),
    AUTH_MFA_REQUIRED("1008", "Multi-factor authentication required", HttpStatus.UNAUTHORIZED),
    
    // Authorization/Permission errors (2xxx)
    FORBIDDEN("2000", "Forbidden", HttpStatus.FORBIDDEN),
    PERMISSION_DENIED("2001", "Permission denied", HttpStatus.FORBIDDEN),
    PERMISSION_INSUFFICIENT("2002", "Insufficient permissions for this operation", HttpStatus.FORBIDDEN),
    PERMISSION_ROLE_REQUIRED("2003", "Required role not assigned", HttpStatus.FORBIDDEN),
    PERMISSION_DATA_ACCESS_DENIED("2004", "Data access denied", HttpStatus.FORBIDDEN),
    PERMISSION_API_ACCESS_DENIED("2005", "API access denied", HttpStatus.FORBIDDEN),
    PERMISSION_DELEGATION_INVALID("2006", "Invalid permission delegation", HttpStatus.BAD_REQUEST),
    
    // Business logic errors (3xxx)
    RESOURCE_NOT_FOUND("3001", "Resource not found", HttpStatus.NOT_FOUND),
    RESOURCE_ALREADY_EXISTS("3002", "Resource already exists", HttpStatus.CONFLICT),
    RESOURCE_IN_USE("3003", "Resource is in use and cannot be modified", HttpStatus.CONFLICT),
    DEPENDENCY_CONFLICT("3004", "Dependency conflict detected", HttpStatus.CONFLICT),
    STATE_INVALID("3005", "Invalid state for this operation", HttpStatus.BAD_REQUEST),
    OPERATION_NOT_ALLOWED("3006", "Operation not allowed", HttpStatus.BAD_REQUEST),
    QUOTA_EXCEEDED("3007", "Quota exceeded", HttpStatus.TOO_MANY_REQUESTS),
    VERSION_CONFLICT("3008", "Version conflict - resource was modified", HttpStatus.CONFLICT),
    WORKFLOW_ERROR("3009", "Workflow execution error", HttpStatus.INTERNAL_SERVER_ERROR),
    DEPLOYMENT_FAILED("3010", "Deployment failed", HttpStatus.INTERNAL_SERVER_ERROR),
    
    // Validation errors (4xxx)
    VALIDATION_ERROR("4000", "Validation error", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("4001", "Validation failed", HttpStatus.BAD_REQUEST),
    VALIDATION_FIELD_REQUIRED("4002", "Required field is missing", HttpStatus.BAD_REQUEST),
    VALIDATION_FIELD_INVALID("4003", "Field value is invalid", HttpStatus.BAD_REQUEST),
    VALIDATION_FORMAT_ERROR("4004", "Invalid format", HttpStatus.BAD_REQUEST),
    VALIDATION_SIZE_EXCEEDED("4005", "Size limit exceeded", HttpStatus.BAD_REQUEST),
    VALIDATION_CONSTRAINT_VIOLATION("4006", "Constraint violation", HttpStatus.BAD_REQUEST),
    
    // System/Infrastructure errors (5xxx)
    INTERNAL_ERROR("5001", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE("5002", "Service temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    DATABASE_ERROR("5003", "Database operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    CACHE_ERROR("5004", "Cache operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    MESSAGE_QUEUE_ERROR("5005", "Message queue operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    EXTERNAL_SERVICE_ERROR("5006", "External service error", HttpStatus.BAD_GATEWAY),
    RATE_LIMIT_EXCEEDED("5007", "Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    TIMEOUT_ERROR("5008", "Operation timed out", HttpStatus.GATEWAY_TIMEOUT),
    CONFIGURATION_ERROR("5009", "Configuration error", HttpStatus.INTERNAL_SERVER_ERROR);
    
    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
    
    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
    
    /**
     * Get the i18n message key for this error code
     */
    public String getMessageKey() {
        return "error." + this.name().toLowerCase();
    }
}
