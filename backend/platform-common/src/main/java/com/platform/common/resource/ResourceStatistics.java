package com.platform.common.resource;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Resource Statistics
 * 
 * Contains resource usage and performance statistics for monitoring
 * resource-intensive operations and connection pool management.
 * 
 * @author Platform Team
 * @version 1.0
 */
@Data
@Builder
public class ResourceStatistics {
    
    /**
     * Number of currently active operations
     */
    private final int activeOperations;
    
    /**
     * Total number of operations executed since startup
     */
    private final long totalOperations;
    
    /**
     * Number of operations that timed out
     */
    private final long timeoutOperations;
    
    /**
     * Number of operations that failed
     */
    private final long failedOperations;
    
    /**
     * Maximum allowed concurrent operations
     */
    private final int maxConcurrentOperations;
    
    /**
     * Average operation duration in milliseconds
     */
    private final double averageOperationDuration;
    
    /**
     * Current resource utilization (0.0 to 1.0)
     */
    private final double resourceUtilization;
    
    /**
     * Timestamp when statistics were collected
     */
    private final LocalDateTime timestamp;
    
    /**
     * Calculate success rate as percentage
     */
    public double getSuccessRate() {
        if (totalOperations == 0) {
            return 1.0;
        }
        long successfulOperations = totalOperations - failedOperations - timeoutOperations;
        return (double) successfulOperations / totalOperations;
    }
    
    /**
     * Calculate timeout rate as percentage
     */
    public double getTimeoutRate() {
        if (totalOperations == 0) {
            return 0.0;
        }
        return (double) timeoutOperations / totalOperations;
    }
    
    /**
     * Calculate failure rate as percentage
     */
    public double getFailureRate() {
        if (totalOperations == 0) {
            return 0.0;
        }
        return (double) failedOperations / totalOperations;
    }
    
    /**
     * Check if resource utilization is within healthy limits
     */
    public boolean isHealthy() {
        return resourceUtilization < 0.8 && getSuccessRate() > 0.95;
    }
    
    /**
     * Get resource health status
     */
    public ResourceHealthStatus getHealthStatus() {
        if (resourceUtilization > 0.95 || getSuccessRate() < 0.9) {
            return ResourceHealthStatus.CRITICAL;
        } else if (resourceUtilization > 0.8 || getSuccessRate() < 0.95) {
            return ResourceHealthStatus.WARNING;
        } else {
            return ResourceHealthStatus.HEALTHY;
        }
    }
    
    /**
     * Resource health status enumeration
     */
    public enum ResourceHealthStatus {
        HEALTHY,
        WARNING,
        CRITICAL
    }
}