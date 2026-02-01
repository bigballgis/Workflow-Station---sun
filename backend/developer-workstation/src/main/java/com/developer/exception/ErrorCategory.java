package com.developer.exception;

/**
 * Categories of errors for classification and handling purposes.
 * 
 * Requirements: 3.4
 */
public enum ErrorCategory {
    
    /**
     * Input validation and data format errors
     */
    VALIDATION,
    
    /**
     * Security-related errors (authentication, authorization, injection attempts)
     */
    SECURITY,
    
    /**
     * Business logic and rule violations
     */
    BUSINESS_LOGIC,
    
    /**
     * Database and data access errors
     */
    DATA_ACCESS,
    
    /**
     * External service integration errors
     */
    INTEGRATION,
    
    /**
     * Configuration and setup errors
     */
    CONFIGURATION,
    
    /**
     * System-level errors (resource exhaustion, infrastructure issues)
     */
    SYSTEM,
    
    /**
     * Unknown or unclassified errors
     */
    UNKNOWN
}