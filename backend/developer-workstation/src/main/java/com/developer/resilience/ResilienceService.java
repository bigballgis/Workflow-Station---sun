package com.developer.resilience;

import com.developer.exception.ErrorContext;
import com.developer.resilience.fallback.DatabaseFallbackStrategy;
import com.developer.resilience.fallback.ExternalServiceFallbackStrategy;
import com.developer.resilience.fallback.SecurityFallbackStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.List;
import java.util.Optional;

/**
 * Service providing resilience patterns including circuit breakers and fallback mechanisms.
 * Integrates with the existing error handling framework.
 * 
 * Requirements: 3.5
 */
@Slf4j
@Service
public class ResilienceService {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final GracefulDegradationManager degradationManager;
    
    public ResilienceService(CircuitBreakerRegistry circuitBreakerRegistry, 
                           GracefulDegradationManager degradationManager) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.degradationManager = degradationManager;
        
        initializeCircuitBreakers();
    }
    
    /**
     * Execute a database operation with circuit breaker protection and fallback
     */
    public <T> List<T> executeDatabaseListOperation(String operationName, 
                                                   Callable<List<T>> operation, 
                                                   ErrorContext context) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.getCircuitBreaker("database-" + operationName);
        
        try {
            return circuitBreaker.execute(operation, DatabaseFallbackStrategy.emptyList(), context);
        } catch (Exception e) {
            log.error("Database list operation failed: {}", operationName, e);
            return DatabaseFallbackStrategy.<T>emptyList().execute(context, e);
        }
    }
    
    /**
     * Execute a database operation that returns an optional result
     */
    public <T> Optional<T> executeDatabaseOptionalOperation(String operationName, 
                                                           Callable<Optional<T>> operation, 
                                                           ErrorContext context) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.getCircuitBreaker("database-" + operationName);
        
        try {
            return circuitBreaker.execute(operation, DatabaseFallbackStrategy.emptyOptional(), context);
        } catch (Exception e) {
            log.error("Database optional operation failed: {}", operationName, e);
            return DatabaseFallbackStrategy.<T>emptyOptional().execute(context, e);
        }
    }
    
    /**
     * Execute a database count operation with fallback to zero
     */
    public Long executeDatabaseCountOperation(String operationName, 
                                            Callable<Long> operation, 
                                            ErrorContext context) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.getCircuitBreaker("database-" + operationName);
        
        try {
            return circuitBreaker.execute(operation, DatabaseFallbackStrategy.zeroCount(), context);
        } catch (Exception e) {
            log.error("Database count operation failed: {}", operationName, e);
            return DatabaseFallbackStrategy.zeroCount().execute(context, e);
        }
    }
    
    /**
     * Execute an external service call with circuit breaker protection
     */
    public <T> T executeExternalServiceCall(String serviceName, 
                                          Callable<T> operation, 
                                          T cachedResponse,
                                          ErrorContext context) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.getCircuitBreaker("external-" + serviceName, 
                CircuitBreakerConfig.critical());
        
        try {
            return circuitBreaker.execute(operation, ExternalServiceFallbackStrategy.cachedResponse(cachedResponse), context);
        } catch (Exception e) {
            log.error("External service call failed: {}", serviceName, e);
            return ExternalServiceFallbackStrategy.cachedResponse(cachedResponse).execute(context, e);
        }
    }
    
    /**
     * Execute a security operation with strict fallback
     */
    public Boolean executeSecurityOperation(String operationName, 
                                          Callable<Boolean> operation, 
                                          ErrorContext context) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.getCircuitBreaker("security-" + operationName, 
                CircuitBreakerConfig.critical());
        
        try {
            return circuitBreaker.execute(operation, SecurityFallbackStrategy.denyAccess(), context);
        } catch (Exception e) {
            log.error("Security operation failed: {}", operationName, e);
            return SecurityFallbackStrategy.denyAccess().execute(context, e);
        }
    }
    
    /**
     * Execute an operation with graceful degradation
     */
    public <T> T executeWithGracefulDegradation(String featureName,
                                              Callable<T> primaryOperation,
                                              Callable<T> degradedOperation,
                                              ErrorContext context) {
        return degradationManager.executeWithDegradation(
                featureName,
                () -> {
                    try {
                        return primaryOperation.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> {
                    try {
                        return degradedOperation.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                context
        );
    }
    
    /**
     * Execute a critical operation that should fail fast if not available
     */
    public <T> T executeCriticalOperation(String operationName, 
                                        Callable<T> operation, 
                                        ErrorContext context) throws Exception {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.getCircuitBreaker("critical-" + operationName, 
                CircuitBreakerConfig.critical());
        
        return circuitBreaker.execute(operation);
    }
    
    /**
     * Get the health status of all circuit breakers
     */
    public ResilienceHealthStatus getHealthStatus() {
        var circuitBreakerStatuses = circuitBreakerRegistry.getStatus();
        var degradationStatuses = degradationManager.getDegradationStatus();
        
        return ResilienceHealthStatus.builder()
                .circuitBreakers(circuitBreakerStatuses)
                .features(degradationStatuses)
                .overallHealthy(isSystemHealthy(circuitBreakerStatuses, degradationStatuses))
                .build();
    }
    
    /**
     * Perform health check and adjust degradation levels
     */
    public void performHealthCheck() {
        degradationManager.performHealthCheck();
    }
    
    /**
     * Reset all circuit breakers (for administrative purposes)
     */
    public void resetAllCircuitBreakers() {
        circuitBreakerRegistry.resetAll();
        log.info("All circuit breakers have been reset");
    }
    
    /**
     * Enter emergency mode - disable non-critical features
     */
    public void enterEmergencyMode() {
        degradationManager.enterEmergencyMode();
        log.error("System entered emergency mode");
    }
    
    /**
     * Exit emergency mode - restore all features
     */
    public void exitEmergencyMode() {
        degradationManager.exitEmergencyMode();
        log.info("System exited emergency mode");
    }
    
    private void initializeCircuitBreakers() {
        // Pre-configure circuit breakers for common operations
        circuitBreakerRegistry.getCircuitBreaker("database-read", CircuitBreakerConfig.defaultConfig());
        circuitBreakerRegistry.getCircuitBreaker("database-write", CircuitBreakerConfig.critical());
        circuitBreakerRegistry.getCircuitBreaker("external-auth", CircuitBreakerConfig.critical());
        circuitBreakerRegistry.getCircuitBreaker("external-notification", CircuitBreakerConfig.defaultConfig());
        
        log.info("Initialized circuit breakers for common operations");
    }
    
    private boolean isSystemHealthy(java.util.Map<String, CircuitBreakerStatus> circuitBreakers,
                                   java.util.Map<String, DegradationStatus> features) {
        // System is healthy if no critical circuit breakers are open and no critical features are disabled
        boolean circuitBreakersHealthy = circuitBreakers.values().stream()
                .noneMatch(status -> status.isFailing() && status.getName().contains("critical"));
        
        boolean featuresHealthy = features.values().stream()
                .filter(status -> isCriticalFeature(status.getFeatureName()))
                .noneMatch(DegradationStatus::isDisabled);
        
        return circuitBreakersHealthy && featuresHealthy;
    }
    
    private boolean isCriticalFeature(String featureName) {
        return featureName.equals("authentication") || 
               featureName.equals("authorization") || 
               featureName.equals("basic-crud");
    }
}