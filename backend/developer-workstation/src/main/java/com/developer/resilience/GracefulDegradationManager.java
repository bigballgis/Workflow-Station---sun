package com.developer.resilience;

import com.developer.exception.ErrorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manager for graceful degradation of system functionality during partial failures.
 * 
 * Requirements: 3.5
 */
@Slf4j
@Component
public class GracefulDegradationManager {
    
    private final Map<String, AtomicBoolean> featureFlags = new ConcurrentHashMap<>();
    private final Map<String, DegradationLevel> degradationLevels = new ConcurrentHashMap<>();
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    public GracefulDegradationManager(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        initializeFeatureFlags();
    }
    
    /**
     * Check if a feature is available (not degraded)
     */
    public boolean isFeatureAvailable(String featureName) {
        return featureFlags.getOrDefault(featureName, new AtomicBoolean(true)).get();
    }
    
    /**
     * Get the current degradation level for a feature
     */
    public DegradationLevel getDegradationLevel(String featureName) {
        return degradationLevels.getOrDefault(featureName, DegradationLevel.NORMAL);
    }
    
    /**
     * Degrade a feature to a specific level
     */
    public void degradeFeature(String featureName, DegradationLevel level, String reason) {
        DegradationLevel previousLevel = degradationLevels.put(featureName, level);
        
        if (level == DegradationLevel.DISABLED) {
            featureFlags.computeIfAbsent(featureName, k -> new AtomicBoolean(true)).set(false);
        } else {
            featureFlags.computeIfAbsent(featureName, k -> new AtomicBoolean(true)).set(true);
        }
        
        log.warn("Feature '{}' degraded from {} to {} - Reason: {}", 
                featureName, previousLevel, level, reason);
    }
    
    /**
     * Restore a feature to normal operation
     */
    public void restoreFeature(String featureName) {
        DegradationLevel previousLevel = degradationLevels.put(featureName, DegradationLevel.NORMAL);
        featureFlags.computeIfAbsent(featureName, k -> new AtomicBoolean(true)).set(true);
        
        log.info("Feature '{}' restored from {} to NORMAL", featureName, previousLevel);
    }
    
    /**
     * Execute an operation with graceful degradation support
     */
    public <T> T executeWithDegradation(String featureName, 
                                       OperationExecutor<T> primaryOperation,
                                       OperationExecutor<T> degradedOperation,
                                       ErrorContext context) {
        DegradationLevel level = getDegradationLevel(featureName);
        
        switch (level) {
            case NORMAL:
                try {
                    return primaryOperation.execute();
                } catch (Exception e) {
                    log.warn("Primary operation failed for feature '{}', attempting degraded operation", featureName, e);
                    degradeFeature(featureName, DegradationLevel.REDUCED, "Primary operation failure");
                    try {
                        return degradedOperation.execute();
                    } catch (Exception degradedException) {
                        throw new RuntimeException("Both primary and degraded operations failed", degradedException);
                    }
                }
                
            case REDUCED:
                log.debug("Feature '{}' is in reduced mode, using degraded operation", featureName);
                try {
                    return degradedOperation.execute();
                } catch (Exception e) {
                    throw new RuntimeException("Degraded operation failed", e);
                }
                
            case DISABLED:
                log.warn("Feature '{}' is disabled, throwing exception", featureName);
                throw new FeatureDisabledException("Feature '" + featureName + "' is currently disabled");
                
            default:
                throw new IllegalStateException("Unknown degradation level: " + level);
        }
    }
    
    /**
     * Check system health and automatically adjust degradation levels
     */
    public void performHealthCheck() {
        Map<String, CircuitBreakerStatus> circuitBreakerStatuses = circuitBreakerRegistry.getStatus();
        
        circuitBreakerStatuses.forEach((name, status) -> {
            String featureName = mapCircuitBreakerToFeature(name);
            
            if (status.isFailing()) {
                degradeFeature(featureName, DegradationLevel.REDUCED, "Circuit breaker is open");
            } else if (status.isHealthy() && getDegradationLevel(featureName) != DegradationLevel.NORMAL) {
                restoreFeature(featureName);
            }
        });
    }
    
    /**
     * Get all features and their current degradation status
     */
    public Map<String, DegradationStatus> getDegradationStatus() {
        Map<String, DegradationStatus> status = new ConcurrentHashMap<>();
        
        Set<String> allFeatures = new HashSet<>(featureFlags.keySet());
        allFeatures.addAll(degradationLevels.keySet());
        
        allFeatures.forEach(feature -> {
            status.put(feature, DegradationStatus.builder()
                    .featureName(feature)
                    .available(isFeatureAvailable(feature))
                    .level(getDegradationLevel(feature))
                    .build());
        });
        
        return status;
    }
    
    /**
     * Disable all non-critical features for emergency mode
     */
    public void enterEmergencyMode() {
        log.error("Entering emergency mode - disabling non-critical features");
        
        // Define critical features that should remain available
        Set<String> criticalFeatures = Set.of(
                "authentication",
                "authorization", 
                "basic-crud",
                "error-handling"
        );
        
        featureFlags.keySet().forEach(feature -> {
            if (!criticalFeatures.contains(feature)) {
                degradeFeature(feature, DegradationLevel.DISABLED, "Emergency mode activated");
            }
        });
    }
    
    /**
     * Exit emergency mode and restore all features
     */
    public void exitEmergencyMode() {
        log.info("Exiting emergency mode - restoring all features");
        
        featureFlags.keySet().forEach(this::restoreFeature);
    }
    
    private void initializeFeatureFlags() {
        // Initialize common features
        featureFlags.put("authentication", new AtomicBoolean(true));
        featureFlags.put("authorization", new AtomicBoolean(true));
        featureFlags.put("basic-crud", new AtomicBoolean(true));
        featureFlags.put("advanced-search", new AtomicBoolean(true));
        featureFlags.put("reporting", new AtomicBoolean(true));
        featureFlags.put("notifications", new AtomicBoolean(true));
        featureFlags.put("audit-logging", new AtomicBoolean(true));
        featureFlags.put("external-integrations", new AtomicBoolean(true));
    }
    
    private String mapCircuitBreakerToFeature(String circuitBreakerName) {
        // Map circuit breaker names to feature names
        if (circuitBreakerName.contains("database")) {
            return "basic-crud";
        } else if (circuitBreakerName.contains("auth")) {
            return "authentication";
        } else if (circuitBreakerName.contains("external")) {
            return "external-integrations";
        } else {
            return circuitBreakerName; // Default mapping
        }
    }
    
    /**
     * Functional interface for operations that can be executed
     */
    @FunctionalInterface
    public interface OperationExecutor<T> {
        T execute() throws Exception;
    }
    
    /**
     * Exception thrown when a feature is disabled
     */
    public static class FeatureDisabledException extends RuntimeException {
        public FeatureDisabledException(String message) {
            super(message);
        }
    }
}