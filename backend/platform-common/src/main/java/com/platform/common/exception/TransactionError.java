package com.platform.common.exception;

import com.platform.common.enums.ErrorCode;
import lombok.Getter;

/**
 * Exception for transaction failures during versioned deployment operations.
 * Used when database transactions fail or need to be rolled back.
 * Maps to HTTP 500 Internal Server Error.
 */
@Getter
public class TransactionError extends PlatformException {
    
    private final String operation;
    
    /**
     * Create a transaction error with operation name and cause
     * 
     * @param operation The operation that failed (e.g., "deployment", "rollback")
     * @param cause The underlying exception that caused the transaction failure
     */
    public TransactionError(String operation, Throwable cause) {
        super(ErrorCode.DATABASE_ERROR, 
              String.format("Transaction failed during %s: %s", operation, cause.getMessage()),
              cause);
        this.operation = operation;
    }
    
    /**
     * Create a transaction error with operation name and custom message
     * 
     * @param operation The operation that failed
     * @param message Custom error message
     */
    public TransactionError(String operation, String message) {
        super(ErrorCode.DATABASE_ERROR,
              String.format("Transaction failed during %s: %s", operation, message));
        this.operation = operation;
    }
    
    /**
     * Create a transaction error with operation name, custom message, and cause
     * 
     * @param operation The operation that failed
     * @param message Custom error message
     * @param cause The underlying exception
     */
    public TransactionError(String operation, String message, Throwable cause) {
        super(ErrorCode.DATABASE_ERROR,
              String.format("Transaction failed during %s: %s", operation, message),
              cause);
        this.operation = operation;
    }
}
