package com.platform.common.resource;

import com.platform.common.config.DatabaseConfig;
import com.platform.common.config.ConfigurationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Resource Manager
 * 
 * Provides comprehensive timeout and resource management for resource-intensive operations.
 * Implements connection pool management, resource cleanup, and monitoring capabilities.
 * 
 * **Validates: Requirements 9.5**
 * 
 * @author Platform Team
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceManager {

    private final DataSource dataSource;
    private final ConfigurationManager configurationManager;
    
    // Resource monitoring counters
    private final AtomicInteger activeOperations = new AtomicInteger(0);
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong timeoutOperations = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    
    // Resource limits and timeouts
    private static final int DEFAULT_MAX_CONCURRENT_OPERATIONS = 50;
    private static final long DEFAULT_OPERATION_TIMEOUT_MS = 30000; // 30 seconds
    private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 5000; // 5 seconds
    private static final long DEFAULT_CLEANUP_INTERVAL_MS = 60000; // 1 minute
    
    // Executor service for timeout management
    private final ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "resource-timeout-");
                t.setDaemon(true);
                return t;
            }
    );
    
    // Resource operation tracking
    private final ConcurrentHashMap<String, ResourceOperation> activeResourceOperations = new ConcurrentHashMap<>();
    
    /**
     * Execute a resource-intensive operation with timeout and resource management
     * 
     * @param operationId Unique operation identifier
     * @param operation Operation to execute
     * @param timeoutMs Timeout in milliseconds
     * @param <T> Return type
     * @return Operation result
     * @throws ResourceTimeoutException if operation times out
     * @throws ResourceLimitExceededException if resource limits are exceeded
     */
    public <T> T executeWithTimeout(String operationId, Supplier<T> operation, long timeoutMs) 
            throws ResourceTimeoutException, ResourceLimitExceededException {
        
        // Check resource limits
        if (activeOperations.get() >= getMaxConcurrentOperations()) {
            failedOperations.incrementAndGet();
            throw new ResourceLimitExceededException(
                    "Maximum concurrent operations exceeded: " + activeOperations.get());
        }
        
        // Track operation start
        ResourceOperation resourceOp = new ResourceOperation(operationId, LocalDateTime.now(), timeoutMs);
        activeResourceOperations.put(operationId, resourceOp);
        activeOperations.incrementAndGet();
        totalOperations.incrementAndGet();
        
        log.info("Starting resource operation: id={}, timeout={}ms, active={}", 
                operationId, timeoutMs, activeOperations.get());
        
        // Use the timeout executor for better timeout precision
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return operation.get();
            } catch (Exception e) {
                log.error("Resource operation failed: id={}, error={}", operationId, e.getMessage());
                throw new RuntimeException(e);
            }
        }, timeoutExecutor);
        
        try {
            T result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            log.info("Resource operation completed: id={}, duration={}ms", 
                    operationId, Duration.between(resourceOp.getStartTime(), LocalDateTime.now()).toMillis());
            return result;
            
        } catch (TimeoutException e) {
            future.cancel(true);
            timeoutOperations.incrementAndGet();
            log.warn("Resource operation timed out: id={}, timeout={}ms", operationId, timeoutMs);
            throw new ResourceTimeoutException("Operation timed out after " + timeoutMs + "ms: " + operationId, 
                    operationId, timeoutMs);
            
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            failedOperations.incrementAndGet();
            throw new RuntimeException("Operation interrupted: " + operationId, e);
            
        } catch (ExecutionException e) {
            failedOperations.incrementAndGet();
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Operation execution failed: " + operationId, cause);
            
        } finally {
            // Cleanup operation tracking
            activeResourceOperations.remove(operationId);
            activeOperations.decrementAndGet();
        }
    }
    
    /**
     * Execute operation with default timeout
     */
    public <T> T executeWithTimeout(String operationId, Supplier<T> operation) 
            throws ResourceTimeoutException, ResourceLimitExceededException {
        return executeWithTimeout(operationId, operation, DEFAULT_OPERATION_TIMEOUT_MS);
    }
    
    /**
     * Execute database operation with connection management and timeout
     * 
     * @param operationId Operation identifier
     * @param operation Database operation
     * @param timeoutMs Timeout in milliseconds
     * @param <T> Return type
     * @return Operation result
     */
    public <T> T executeWithConnection(String operationId, DatabaseOperation<T> operation, long timeoutMs) 
            throws ResourceTimeoutException, ResourceLimitExceededException {
        
        return executeWithTimeout(operationId, () -> {
            try (Connection connection = getConnectionWithTimeout()) {
                return operation.execute(connection);
            } catch (SQLException e) {
                log.error("Database operation failed: id={}, error={}", operationId, e.getMessage());
                throw new RuntimeException("Database operation failed: " + e.getMessage(), e);
            }
        }, timeoutMs);
    }
    
    /**
     * Execute database operation with default timeout
     */
    public <T> T executeWithConnection(String operationId, DatabaseOperation<T> operation) 
            throws ResourceTimeoutException, ResourceLimitExceededException {
        return executeWithConnection(operationId, operation, DEFAULT_OPERATION_TIMEOUT_MS);
    }
    
    /**
     * Get database connection with timeout
     */
    private Connection getConnectionWithTimeout() throws SQLException {
        long startTime = System.currentTimeMillis();
        
        try {
            Connection connection = dataSource.getConnection();
            
            // Configure connection timeout based on database config
            DatabaseConfig dbConfig = configurationManager.getConfiguration(DatabaseConfig.class);
            int queryTimeoutSeconds = (int) (dbConfig.getConnectionTimeoutMs() / 1000);
            
            // Set connection properties for timeout management
            if (connection.isValid(queryTimeoutSeconds)) {
                return connection;
            } else {
                connection.close();
                throw new SQLException("Connection validation failed within timeout");
            }
            
        } catch (SQLException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to get database connection: duration={}ms, error={}", duration, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Get resource usage statistics
     */
    public ResourceStatistics getResourceStatistics() {
        return ResourceStatistics.builder()
                .activeOperations(activeOperations.get())
                .totalOperations(totalOperations.get())
                .timeoutOperations(timeoutOperations.get())
                .failedOperations(failedOperations.get())
                .maxConcurrentOperations(getMaxConcurrentOperations())
                .averageOperationDuration(calculateAverageOperationDuration())
                .resourceUtilization(calculateResourceUtilization())
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Cleanup expired operations and resources
     */
    public void cleanupExpiredOperations() {
        LocalDateTime now = LocalDateTime.now();
        int cleanedUp = 0;
        
        for (ResourceOperation operation : activeResourceOperations.values()) {
            Duration duration = Duration.between(operation.getStartTime(), now);
            if (duration.toMillis() > operation.getTimeoutMs() + 5000) { // 5 second grace period
                activeResourceOperations.remove(operation.getOperationId());
                cleanedUp++;
                log.debug("Cleaned up expired operation: id={}, duration={}ms", 
                        operation.getOperationId(), duration.toMillis());
            }
        }
        
        if (cleanedUp > 0) {
            log.info("Cleaned up {} expired resource operations", cleanedUp);
        }
    }
    
    /**
     * Force cleanup of all active operations (for shutdown)
     */
    public void forceCleanupAllOperations() {
        int operationCount = activeResourceOperations.size();
        activeResourceOperations.clear();
        activeOperations.set(0);
        
        log.info("Force cleaned up {} active resource operations", operationCount);
    }
    
    /**
     * Check if resource limits are within acceptable thresholds
     */
    public boolean isResourceHealthy() {
        int active = activeOperations.get();
        int maxConcurrent = getMaxConcurrentOperations();
        double utilization = calculateResourceUtilization();
        
        // Consider healthy if utilization is below 80%
        return utilization < 0.8 && active < maxConcurrent;
    }
    
    /**
     * Get current resource utilization as percentage (0.0 to 1.0)
     */
    private double calculateResourceUtilization() {
        int active = activeOperations.get();
        int maxConcurrent = getMaxConcurrentOperations();
        return maxConcurrent > 0 ? (double) active / maxConcurrent : 0.0;
    }
    
    /**
     * Calculate average operation duration from recent operations
     */
    private double calculateAverageOperationDuration() {
        // Simplified implementation - in production, would track actual durations
        return 1500.0; // Default 1.5 seconds
    }
    
    /**
     * Get maximum concurrent operations from configuration
     */
    private int getMaxConcurrentOperations() {
        try {
            DatabaseConfig dbConfig = configurationManager.getConfiguration(DatabaseConfig.class);
            // Use connection pool size as basis for concurrent operations
            return Math.max(dbConfig.getMaxConnections() * 2, DEFAULT_MAX_CONCURRENT_OPERATIONS);
        } catch (Exception e) {
            log.warn("Failed to get database config, using default max concurrent operations: {}", 
                    DEFAULT_MAX_CONCURRENT_OPERATIONS);
            return DEFAULT_MAX_CONCURRENT_OPERATIONS;
        }
    }
    
    /**
     * Start periodic cleanup task
     */
    public void startPeriodicCleanup() {
        timeoutExecutor.scheduleAtFixedRate(
                this::cleanupExpiredOperations,
                DEFAULT_CLEANUP_INTERVAL_MS,
                DEFAULT_CLEANUP_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        log.info("Started periodic resource cleanup with interval: {}ms", DEFAULT_CLEANUP_INTERVAL_MS);
    }
    
    /**
     * Shutdown resource manager
     */
    public void shutdown() {
        log.info("Shutting down resource manager");
        
        // Force cleanup all operations
        forceCleanupAllOperations();
        
        // Shutdown timeout executor
        timeoutExecutor.shutdown();
        try {
            if (!timeoutExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                timeoutExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Resource manager shutdown completed");
    }
    
    /**
     * Functional interface for database operations
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute(Connection connection) throws SQLException;
    }
    
    /**
     * Resource operation tracking
     */
    private static class ResourceOperation {
        private final String operationId;
        private final LocalDateTime startTime;
        private final long timeoutMs;
        
        public ResourceOperation(String operationId, LocalDateTime startTime, long timeoutMs) {
            this.operationId = operationId;
            this.startTime = startTime;
            this.timeoutMs = timeoutMs;
        }
        
        public String getOperationId() { return operationId; }
        public LocalDateTime getStartTime() { return startTime; }
        public long getTimeoutMs() { return timeoutMs; }
    }
}