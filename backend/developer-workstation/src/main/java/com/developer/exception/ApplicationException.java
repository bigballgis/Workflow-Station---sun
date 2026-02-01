package com.developer.exception;

import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Base class for all application-specific exceptions.
 * Provides common error handling functionality and context management.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4
 */
@Getter
public abstract class ApplicationException extends Exception {
    
    protected final String errorCode;
    protected final ErrorContext context;
    protected final Instant timestamp;
    
    protected ApplicationException(String errorCode, String message, ErrorContext context) {
        super(message);
        this.errorCode = errorCode;
        this.context = context;
        this.timestamp = Instant.now();
    }
    
    protected ApplicationException(String errorCode, String message, ErrorContext context, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = context;
        this.timestamp = Instant.now();
    }
    
    /**
     * Get the error category for this exception type
     */
    public abstract ErrorCategory getCategory();
    
    /**
     * Get the severity level of this exception
     */
    public abstract ErrorSeverity getSeverity();
    
    /**
     * Get additional metadata for this exception
     */
    public Map<String, Object> getMetadata() {
        return context != null ? context.getParameters() : Map.of();
    }
    
    /**
     * Check if this exception should be logged
     */
    public boolean shouldLog() {
        return getSeverity().ordinal() >= ErrorSeverity.WARN.ordinal();
    }
    
    /**
     * Check if this exception should trigger alerts
     */
    public boolean shouldAlert() {
        return getSeverity() == ErrorSeverity.CRITICAL;
    }
}