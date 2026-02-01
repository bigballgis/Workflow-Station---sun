package com.developer.resilience;

import lombok.Builder;
import lombok.Getter;

/**
 * Status information for a circuit breaker.
 * 
 * Requirements: 3.5
 */
@Getter
@Builder
public class CircuitBreakerStatus {
    
    private final String name;
    private final CircuitBreakerState state;
    private final int failureCount;
    private final int successCount;
    
    /**
     * Check if the circuit breaker is currently failing (OPEN state)
     */
    public boolean isFailing() {
        return state == CircuitBreakerState.OPEN;
    }
    
    /**
     * Check if the circuit breaker is healthy (CLOSED state)
     */
    public boolean isHealthy() {
        return state == CircuitBreakerState.CLOSED;
    }
    
    /**
     * Check if the circuit breaker is in recovery mode (HALF_OPEN state)
     */
    public boolean isRecovering() {
        return state == CircuitBreakerState.HALF_OPEN;
    }
}