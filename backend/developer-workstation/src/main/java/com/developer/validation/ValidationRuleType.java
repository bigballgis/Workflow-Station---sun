package com.developer.validation;

/**
 * Enumeration of validation rule types that determine how pattern matching is interpreted
 */
public enum ValidationRuleType {
    /**
     * Security rules - validation fails when pattern matches (detects threats)
     */
    SECURITY,
    
    /**
     * Format rules - validation fails when pattern doesn't match (validates format)
     */
    FORMAT,
    
    /**
     * Business rules - validation fails when pattern doesn't match (validates business logic)
     */
    BUSINESS
}