package com.developer.resilience;

/**
 * Enumeration of feature degradation levels.
 * 
 * Requirements: 3.5
 */
public enum DegradationLevel {
    /**
     * Feature is operating normally with full functionality
     */
    NORMAL,
    
    /**
     * Feature is operating with reduced functionality
     */
    REDUCED,
    
    /**
     * Feature is completely disabled
     */
    DISABLED
}