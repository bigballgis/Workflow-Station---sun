package com.developer.exception;

import lombok.Getter;

/**
 * Exception for database and data access related errors.
 * 
 * Requirements: 3.1, 3.4
 */
@Getter
public class DataAccessException extends ApplicationException {
    
    private final String operation;
    private final String entityType;
    
    public DataAccessException(String errorCode, String message, String operation, ErrorContext context) {
        super(errorCode, message, context);
        this.operation = operation;
        this.entityType = null;
    }
    
    public DataAccessException(String errorCode, String message, String operation, String entityType, ErrorContext context) {
        super(errorCode, message, context);
        this.operation = operation;
        this.entityType = entityType;
    }
    
    public DataAccessException(String errorCode, String message, String operation, ErrorContext context, Throwable cause) {
        super(errorCode, message, context, cause);
        this.operation = operation;
        this.entityType = null;
    }
    
    @Override
    public ErrorCategory getCategory() {
        return ErrorCategory.DATA_ACCESS;
    }
    
    @Override
    public ErrorSeverity getSeverity() {
        return ErrorSeverity.ERROR;
    }
    
    /**
     * Create a data access exception for database connection issues
     */
    public static DataAccessException connectionFailure(String message, ErrorContext context, Throwable cause) {
        return new DataAccessException("DATA_CONNECTION_FAILED", message, "connection", context, cause);
    }
    
    /**
     * Create a data access exception for query execution failures
     */
    public static DataAccessException queryFailure(String message, ErrorContext context, Throwable cause) {
        return new DataAccessException("DATA_QUERY_FAILED", message, "query", context, cause);
    }
    
    /**
     * Create a data access exception for transaction failures
     */
    public static DataAccessException transactionFailure(String message, ErrorContext context, Throwable cause) {
        return new DataAccessException("DATA_TRANSACTION_FAILED", message, "transaction", context, cause);
    }
    
    /**
     * Create a data access exception for constraint violations
     */
    public static DataAccessException constraintViolation(String constraint, String message, ErrorContext context) {
        return new DataAccessException("DATA_CONSTRAINT_VIOLATION", message, "constraint_check", context);
    }
}