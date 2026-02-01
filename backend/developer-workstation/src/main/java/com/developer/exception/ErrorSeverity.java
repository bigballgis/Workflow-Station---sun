package com.developer.exception;

/**
 * Severity levels for errors to determine logging and alerting behavior.
 * 
 * Requirements: 3.3
 */
public enum ErrorSeverity {
    
    /**
     * Informational - no action required
     */
    INFO,
    
    /**
     * Warning - should be monitored but not critical
     */
    WARN,
    
    /**
     * Error - requires attention and logging
     */
    ERROR,
    
    /**
     * Critical - requires immediate attention and alerting
     */
    CRITICAL
}