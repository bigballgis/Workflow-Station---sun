package com.developer.resilience;

import lombok.Builder;
import lombok.Getter;

/**
 * Status information for feature degradation.
 * 
 * Requirements: 3.5
 */
@Getter
@Builder
public class DegradationStatus {
    
    private final String featureName;
    private final boolean available;
    private final DegradationLevel level;
    
    /**
     * Check if the feature is completely disabled
     */
    public boolean isDisabled() {
        return level == DegradationLevel.DISABLED || !available;
    }
    
    /**
     * Check if the feature is operating normally
     */
    public boolean isNormal() {
        return level == DegradationLevel.NORMAL && available;
    }
    
    /**
     * Check if the feature is operating with reduced functionality
     */
    public boolean isReduced() {
        return level == DegradationLevel.REDUCED && available;
    }
    
    /**
     * Check if the feature is degraded (either reduced or disabled)
     */
    public boolean isDegraded() {
        return level != DegradationLevel.NORMAL || !available;
    }
}