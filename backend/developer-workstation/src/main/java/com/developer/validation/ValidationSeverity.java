package com.developer.validation;

/**
 * Enumeration of validation severity levels
 */
public enum ValidationSeverity {
    /**
     * Information level - for informational messages
     */
    INFO,
    
    /**
     * Warning level - validation concerns that don't prevent processing
     */
    WARNING,
    
    /**
     * Error level - validation failures that prevent processing
     */
    ERROR,
    
    /**
     * Critical level - security or system-critical validation failures
     */
    CRITICAL
}