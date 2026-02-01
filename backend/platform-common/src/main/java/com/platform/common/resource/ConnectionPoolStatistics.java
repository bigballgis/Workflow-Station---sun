package com.platform.common.resource;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Connection Pool Statistics
 * 
 * Contains statistics and metrics for database connection pool monitoring.
 * 
 * @author Platform Team
 * @version 1.0
 */
@Data
@Builder
public class ConnectionPoolStatistics {
    
    /**
     * Name of the connection pool
     */
    private final String poolName;
    
    /**
     * Number of currently active connections
     */
    private final int activeConnections;
    
    /**
     * Number of idle connections in the pool
     */
    private final int idleConnections;
    
    /**
     * Total number of connections (active + idle)
     */
    private final int totalConnections;
    
    /**
     * Maximum allowed connections in the pool
     */
    private final int maxConnections;
    
    /**
     * Number of threads currently waiting for a connection
     */
    private final int threadsAwaitingConnection;
    
    /**
     * Connection utilization as percentage (0.0 to 1.0)
     */
    private final double connectionUtilization;
    
    /**
     * Timestamp when statistics were collected
     */
    private final LocalDateTime timestamp;
    
    /**
     * Check if pool has available connections
     */
    public boolean hasAvailableConnections() {
        return idleConnections > 0 || activeConnections < maxConnections;
    }
    
    /**
     * Check if pool is under high load
     */
    public boolean isUnderHighLoad() {
        return connectionUtilization > 0.8 || threadsAwaitingConnection > 0;
    }
    
    /**
     * Check if pool is at capacity
     */
    public boolean isAtCapacity() {
        return activeConnections >= maxConnections;
    }
    
    /**
     * Get pool efficiency score (0.0 to 1.0)
     */
    public double getEfficiencyScore() {
        if (maxConnections == 0) {
            return 0.0;
        }
        
        // Efficiency is high when:
        // - Utilization is reasonable (not too low, not too high)
        // - No threads are waiting
        // - Good balance of active vs idle connections
        
        double utilizationScore = 1.0 - Math.abs(connectionUtilization - 0.6); // Optimal around 60%
        double waitingPenalty = threadsAwaitingConnection > 0 ? 0.5 : 1.0;
        double balanceScore = totalConnections > 0 ? 
                Math.min(1.0, (double) idleConnections / totalConnections * 2) : 0.0;
        
        return Math.max(0.0, Math.min(1.0, utilizationScore * waitingPenalty * balanceScore));
    }
    
    /**
     * Get pool health status
     */
    public PoolHealthStatus getHealthStatus() {
        if (isAtCapacity() && threadsAwaitingConnection > 5) {
            return PoolHealthStatus.CRITICAL;
        } else if (connectionUtilization > 0.9 || threadsAwaitingConnection > 0) {
            return PoolHealthStatus.WARNING;
        } else {
            return PoolHealthStatus.HEALTHY;
        }
    }
    
    /**
     * Pool health status enumeration
     */
    public enum PoolHealthStatus {
        HEALTHY,
        WARNING,
        CRITICAL
    }
}