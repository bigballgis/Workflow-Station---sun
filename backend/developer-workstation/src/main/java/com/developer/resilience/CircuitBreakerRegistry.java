package com.developer.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry for managing circuit breaker instances.
 * 
 * Requirements: 3.5
 */
@Slf4j
@Component
public class CircuitBreakerRegistry {
    
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerConfig> configs = new ConcurrentHashMap<>();
    
    /**
     * Get or create a circuit breaker with default configuration
     */
    public CircuitBreaker getCircuitBreaker(String name) {
        return circuitBreakers.computeIfAbsent(name, n -> {
            CircuitBreakerConfig config = configs.getOrDefault(name, CircuitBreakerConfig.defaultConfig());
            log.info("Creating circuit breaker: {}", name);
            return new CircuitBreaker(name, config);
        });
    }
    
    /**
     * Get or create a circuit breaker with specific configuration
     */
    public CircuitBreaker getCircuitBreaker(String name, CircuitBreakerConfig config) {
        configs.put(name, config);
        return circuitBreakers.computeIfAbsent(name, n -> {
            log.info("Creating circuit breaker: {} with custom config", name);
            return new CircuitBreaker(name, config);
        });
    }
    
    /**
     * Register a pre-configured circuit breaker
     */
    public void registerCircuitBreaker(String name, CircuitBreaker circuitBreaker) {
        circuitBreakers.put(name, circuitBreaker);
        log.info("Registered circuit breaker: {}", name);
    }
    
    /**
     * Remove a circuit breaker from the registry
     */
    public void removeCircuitBreaker(String name) {
        CircuitBreaker removed = circuitBreakers.remove(name);
        configs.remove(name);
        if (removed != null) {
            log.info("Removed circuit breaker: {}", name);
        }
    }
    
    /**
     * Get all registered circuit breaker names
     */
    public Set<String> getCircuitBreakerNames() {
        return circuitBreakers.keySet();
    }
    
    /**
     * Get circuit breaker status information
     */
    public Map<String, CircuitBreakerStatus> getStatus() {
        Map<String, CircuitBreakerStatus> status = new ConcurrentHashMap<>();
        
        circuitBreakers.forEach((name, cb) -> {
            status.put(name, CircuitBreakerStatus.builder()
                    .name(name)
                    .state(cb.getState())
                    .failureCount(cb.getFailureCount())
                    .successCount(cb.getSuccessCount())
                    .build());
        });
        
        return status;
    }
    
    /**
     * Reset all circuit breakers to CLOSED state
     */
    public void resetAll() {
        circuitBreakers.values().forEach(CircuitBreaker::reset);
        log.info("Reset all circuit breakers");
    }
    
    /**
     * Force all circuit breakers to OPEN state
     */
    public void forceOpenAll() {
        circuitBreakers.values().forEach(CircuitBreaker::forceOpen);
        log.warn("Forced all circuit breakers to OPEN state");
    }
}