package com.developer.resilience;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Overall health status for the resilience system.
 * 
 * Requirements: 3.5
 */
@Getter
@Builder
public class ResilienceHealthStatus {
    
    private final Map<String, CircuitBreakerStatus> circuitBreakers;
    private final Map<String, DegradationStatus> features;
    private final boolean overallHealthy;
    
    /**
     * Get the number of healthy circuit breakers
     */
    public long getHealthyCircuitBreakers() {
        return circuitBreakers.values().stream()
                .mapToLong(status -> status.isHealthy() ? 1 : 0)
                .sum();
    }
    
    /**
     * Get the number of failing circuit breakers
     */
    public long getFailingCircuitBreakers() {
        return circuitBreakers.values().stream()
                .mapToLong(status -> status.isFailing() ? 1 : 0)
                .sum();
    }
    
    /**
     * Get the number of normal features
     */
    public long getNormalFeatures() {
        return features.values().stream()
                .mapToLong(status -> status.isNormal() ? 1 : 0)
                .sum();
    }
    
    /**
     * Get the number of degraded features
     */
    public long getDegradedFeatures() {
        return features.values().stream()
                .mapToLong(status -> status.isDegraded() ? 1 : 0)
                .sum();
    }
    
    /**
     * Get the number of disabled features
     */
    public long getDisabledFeatures() {
        return features.values().stream()
                .mapToLong(status -> status.isDisabled() ? 1 : 0)
                .sum();
    }
}