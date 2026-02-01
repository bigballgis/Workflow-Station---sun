package com.platform.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Workflow Configuration
 * 
 * Externalized workflow engine settings
 * 
 * @author Platform Team
 * @version 1.0
 */
public class WorkflowConfig {
    
    @NotBlank(message = "Workflow engine URL is required")
    private String engineUrl = "http://localhost:8081";
    
    private boolean enabled = true;
    
    @Min(value = 1000, message = "Connection timeout must be at least 1 second")
    @Max(value = 300000, message = "Connection timeout cannot exceed 5 minutes")
    private long connectionTimeoutMs = 30000;
    
    @Min(value = 1000, message = "Read timeout must be at least 1 second")
    @Max(value = 300000, message = "Read timeout cannot exceed 5 minutes")
    private long readTimeoutMs = 60000;
    
    @Min(value = 1, message = "Max retry attempts must be at least 1")
    @Max(value = 10, message = "Max retry attempts cannot exceed 10")
    private int maxRetryAttempts = 3;
    
    @Min(value = 1000, message = "Retry delay must be at least 1 second")
    @Max(value = 60000, message = "Retry delay cannot exceed 1 minute")
    private long retryDelayMs = 5000;
    
    private boolean enableCircuitBreaker = true;
    
    @Min(value = 1, message = "Circuit breaker failure threshold must be at least 1")
    @Max(value = 100, message = "Circuit breaker failure threshold cannot exceed 100")
    private int circuitBreakerFailureThreshold = 5;
    
    @Min(value = 1000, message = "Circuit breaker timeout must be at least 1 second")
    @Max(value = 300000, message = "Circuit breaker timeout cannot exceed 5 minutes")
    private long circuitBreakerTimeoutMs = 60000;
    
    private boolean enableMetrics = true;
    private boolean enableHealthCheck = true;
    private boolean enableAuditLogging = true;
    
    @Min(value = 1, message = "Default page size must be at least 1")
    @Max(value = 1000, message = "Default page size cannot exceed 1000")
    private int defaultPageSize = 10;
    
    @Min(value = 10, message = "Max page size must be at least 10")
    @Max(value = 10000, message = "Max page size cannot exceed 10000")
    private int maxPageSize = 1000;
    
    @Min(value = 1, message = "Task query timeout must be at least 1 second")
    @Max(value = 300, message = "Task query timeout cannot exceed 5 minutes")
    private int taskQueryTimeoutSeconds = 30;
    
    @Min(value = 1, message = "Process query timeout must be at least 1 second")
    @Max(value = 300, message = "Process query timeout cannot exceed 5 minutes")
    private int processQueryTimeoutSeconds = 60;
    
    private boolean enableTaskCaching = true;
    
    @Min(value = 1, message = "Task cache TTL must be at least 1 minute")
    @Max(value = 1440, message = "Task cache TTL cannot exceed 24 hours")
    private int taskCacheTtlMinutes = 15;
    
    private boolean enableProcessCaching = true;
    
    @Min(value = 1, message = "Process cache TTL must be at least 1 minute")
    @Max(value = 1440, message = "Process cache TTL cannot exceed 24 hours")
    private int processCacheTtlMinutes = 30;
    
    private boolean enableAsyncProcessing = true;
    
    @Min(value = 1, message = "Async thread pool size must be at least 1")
    @Max(value = 100, message = "Async thread pool size cannot exceed 100")
    private int asyncThreadPoolSize = 10;
    
    @Min(value = 10, message = "Async queue capacity must be at least 10")
    @Max(value = 10000, message = "Async queue capacity cannot exceed 10000")
    private int asyncQueueCapacity = 1000;
    
    // Getters and Setters
    public String getEngineUrl() {
        return engineUrl;
    }
    
    public void setEngineUrl(String engineUrl) {
        this.engineUrl = engineUrl;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }
    
    public void setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }
    
    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }
    
    public void setReadTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
    
    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }
    
    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }
    
    public long getRetryDelayMs() {
        return retryDelayMs;
    }
    
    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }
    
    public boolean isEnableCircuitBreaker() {
        return enableCircuitBreaker;
    }
    
    public void setEnableCircuitBreaker(boolean enableCircuitBreaker) {
        this.enableCircuitBreaker = enableCircuitBreaker;
    }
    
    public int getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }
    
    public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
        this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
    }
    
    public long getCircuitBreakerTimeoutMs() {
        return circuitBreakerTimeoutMs;
    }
    
    public void setCircuitBreakerTimeoutMs(long circuitBreakerTimeoutMs) {
        this.circuitBreakerTimeoutMs = circuitBreakerTimeoutMs;
    }
    
    public boolean isEnableMetrics() {
        return enableMetrics;
    }
    
    public void setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }
    
    public boolean isEnableHealthCheck() {
        return enableHealthCheck;
    }
    
    public void setEnableHealthCheck(boolean enableHealthCheck) {
        this.enableHealthCheck = enableHealthCheck;
    }
    
    public boolean isEnableAuditLogging() {
        return enableAuditLogging;
    }
    
    public void setEnableAuditLogging(boolean enableAuditLogging) {
        this.enableAuditLogging = enableAuditLogging;
    }
    
    public int getDefaultPageSize() {
        return defaultPageSize;
    }
    
    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }
    
    public int getMaxPageSize() {
        return maxPageSize;
    }
    
    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }
    
    public int getTaskQueryTimeoutSeconds() {
        return taskQueryTimeoutSeconds;
    }
    
    public void setTaskQueryTimeoutSeconds(int taskQueryTimeoutSeconds) {
        this.taskQueryTimeoutSeconds = taskQueryTimeoutSeconds;
    }
    
    public int getProcessQueryTimeoutSeconds() {
        return processQueryTimeoutSeconds;
    }
    
    public void setProcessQueryTimeoutSeconds(int processQueryTimeoutSeconds) {
        this.processQueryTimeoutSeconds = processQueryTimeoutSeconds;
    }
    
    public boolean isEnableTaskCaching() {
        return enableTaskCaching;
    }
    
    public void setEnableTaskCaching(boolean enableTaskCaching) {
        this.enableTaskCaching = enableTaskCaching;
    }
    
    public int getTaskCacheTtlMinutes() {
        return taskCacheTtlMinutes;
    }
    
    public void setTaskCacheTtlMinutes(int taskCacheTtlMinutes) {
        this.taskCacheTtlMinutes = taskCacheTtlMinutes;
    }
    
    public boolean isEnableProcessCaching() {
        return enableProcessCaching;
    }
    
    public void setEnableProcessCaching(boolean enableProcessCaching) {
        this.enableProcessCaching = enableProcessCaching;
    }
    
    public int getProcessCacheTtlMinutes() {
        return processCacheTtlMinutes;
    }
    
    public void setProcessCacheTtlMinutes(int processCacheTtlMinutes) {
        this.processCacheTtlMinutes = processCacheTtlMinutes;
    }
    
    public boolean isEnableAsyncProcessing() {
        return enableAsyncProcessing;
    }
    
    public void setEnableAsyncProcessing(boolean enableAsyncProcessing) {
        this.enableAsyncProcessing = enableAsyncProcessing;
    }
    
    public int getAsyncThreadPoolSize() {
        return asyncThreadPoolSize;
    }
    
    public void setAsyncThreadPoolSize(int asyncThreadPoolSize) {
        this.asyncThreadPoolSize = asyncThreadPoolSize;
    }
    
    public int getAsyncQueueCapacity() {
        return asyncQueueCapacity;
    }
    
    public void setAsyncQueueCapacity(int asyncQueueCapacity) {
        this.asyncQueueCapacity = asyncQueueCapacity;
    }
}