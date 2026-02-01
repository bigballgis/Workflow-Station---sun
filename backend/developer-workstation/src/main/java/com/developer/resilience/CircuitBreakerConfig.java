package com.developer.resilience;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

/**
 * Configuration for circuit breaker behavior.
 * 
 * Requirements: 3.5
 */
@Getter
@Builder
public class CircuitBreakerConfig {
    
    /**
     * Number of failures required to open the circuit
     */
    @Builder.Default
    private final int failureThreshold = 5;
    
    /**
     * Time window for counting failures
     */
    @Builder.Default
    private final Duration failureWindow = Duration.ofMinutes(1);
    
    /**
     * Time to wait before transitioning from OPEN to HALF_OPEN
     */
    @Builder.Default
    private final Duration recoveryTimeout = Duration.ofSeconds(30);
    
    /**
     * Number of successful requests required in HALF_OPEN to close circuit
     */
    @Builder.Default
    private final int successThreshold = 3;
    
    /**
     * Maximum time to wait for a request to complete
     */
    @Builder.Default
    private final Duration requestTimeout = Duration.ofSeconds(10);
    
    /**
     * Whether to enable automatic recovery attempts
     */
    @Builder.Default
    private final boolean autoRecovery = true;
    
    /**
     * Create default configuration
     */
    public static CircuitBreakerConfig defaultConfig() {
        return CircuitBreakerConfig.builder().build();
    }
    
    /**
     * Create configuration for fast recovery (testing/development)
     */
    public static CircuitBreakerConfig fastRecovery() {
        return CircuitBreakerConfig.builder()
                .failureThreshold(3)
                .failureWindow(Duration.ofSeconds(30))
                .recoveryTimeout(Duration.ofSeconds(10))
                .successThreshold(2)
                .requestTimeout(Duration.ofSeconds(5))
                .build();
    }
    
    /**
     * Create configuration for critical operations (conservative)
     */
    public static CircuitBreakerConfig critical() {
        return CircuitBreakerConfig.builder()
                .failureThreshold(2)
                .failureWindow(Duration.ofSeconds(30))
                .recoveryTimeout(Duration.ofMinutes(2))
                .successThreshold(5)
                .requestTimeout(Duration.ofSeconds(15))
                .build();
    }
}