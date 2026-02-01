package com.developer.validation;

/**
 * Enumeration of different sanitization strategies that can be applied to input
 */
public enum SanitizationStrategy {
    /**
     * Basic HTML encoding of dangerous characters
     */
    HTML_ENCODE("HTML Encode"),
    
    /**
     * XSS prevention focused sanitization
     */
    XSS_PREVENTION("XSS Prevention"),
    
    /**
     * SQL injection prevention focused sanitization
     */
    SQL_SAFE("SQL Safe"),
    
    /**
     * Command injection prevention focused sanitization
     */
    COMMAND_SAFE("Command Safe"),
    
    /**
     * Comprehensive sanitization combining multiple strategies
     */
    COMPREHENSIVE("Comprehensive"),
    
    /**
     * Strict sanitization that removes most special characters
     */
    STRICT("Strict");
    
    private final String displayName;
    
    SanitizationStrategy(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}