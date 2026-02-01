package com.developer.exception;

/**
 * Types of security threats that can be detected in the system.
 * 
 * Requirements: 3.4
 */
public enum SecurityThreat {
    
    /**
     * SQL injection, XSS, or command injection attempts
     */
    INJECTION_ATTEMPT,
    
    /**
     * Failed authentication attempts
     */
    AUTHENTICATION_FAILURE,
    
    /**
     * Authorization failures for protected resources
     */
    AUTHORIZATION_FAILURE,
    
    /**
     * Attempts to access unauthorized resources
     */
    UNAUTHORIZED_ACCESS,
    
    /**
     * Suspicious activity patterns
     */
    SUSPICIOUS_ACTIVITY,
    
    /**
     * Invalid or malformed security tokens
     */
    INVALID_TOKEN,
    
    /**
     * Session-related security issues
     */
    SESSION_VIOLATION
}