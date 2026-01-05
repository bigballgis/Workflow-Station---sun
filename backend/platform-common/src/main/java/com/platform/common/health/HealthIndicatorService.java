package com.platform.common.health;

import java.util.Map;

/**
 * Service interface for health indicators.
 * Validates: Requirements 1.8
 */
public interface HealthIndicatorService {
    
    /**
     * Get overall health status.
     * 
     * @return Health status
     */
    HealthStatus getHealth();
    
    /**
     * Get detailed health information.
     * 
     * @return Map of component name to health details
     */
    Map<String, ComponentHealth> getDetailedHealth();
    
    /**
     * Check if the service is ready to accept traffic.
     * 
     * @return true if ready
     */
    boolean isReady();
    
    /**
     * Check if the service is alive.
     * 
     * @return true if alive
     */
    boolean isAlive();
}
