package com.developer.resilience;

/**
 * Enumeration of circuit breaker states.
 * 
 * Requirements: 3.5
 */
public enum CircuitBreakerState {
    /**
     * Circuit is closed - requests are allowed through
     */
    CLOSED,
    
    /**
     * Circuit is open - requests are blocked and fallback is used
     */
    OPEN,
    
    /**
     * Circuit is half-open - limited requests are allowed to test recovery
     */
    HALF_OPEN
}