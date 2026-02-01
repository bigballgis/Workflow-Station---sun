package com.platform.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * API Configuration
 * 
 * Externalized API settings including timeouts, rate limiting,
 * and service endpoints. Supports runtime updates for most properties.
 * 
 * @author Platform Team
 * @version 1.0
 */
@RuntimeUpdatable(requiresRestart = {"basePath", "version"})
public class ApiConfig {
    
    @Min(value = 1000, message = "Request timeout must be at least 1 second")
    @Max(value = 300000, message = "Request timeout cannot exceed 5 minutes")
    private long requestTimeoutMs = 30000;
    
    @Min(value = 1000, message = "Connection timeout must be at least 1 second")
    @Max(value = 60000, message = "Connection timeout cannot exceed 1 minute")
    private long connectionTimeoutMs = 10000;
    
    @Min(value = 1000, message = "Read timeout must be at least 1 second")
    @Max(value = 300000, message = "Read timeout cannot exceed 5 minutes")
    private long readTimeoutMs = 30000;
    
    @Min(value = 1, message = "Max connections must be at least 1")
    @Max(value = 1000, message = "Max connections cannot exceed 1000")
    private int maxConnections = 100;
    
    @Min(value = 1, message = "Max connections per route must be at least 1")
    @Max(value = 500, message = "Max connections per route cannot exceed 500")
    private int maxConnectionsPerRoute = 50;
    
    @Min(value = 1, message = "Rate limit requests per minute must be at least 1")
    @Max(value = 10000, message = "Rate limit requests per minute cannot exceed 10000")
    private int rateLimitRequestsPerMinute = 1000;
    
    @Min(value = 1, message = "Rate limit burst size must be at least 1")
    @Max(value = 1000, message = "Rate limit burst size cannot exceed 1000")
    private int rateLimitBurstSize = 100;
    
    private boolean enableRateLimiting = true;
    private boolean enableRequestLogging = true;
    private boolean enableResponseLogging = false;
    private boolean enableMetrics = true;
    
    @NotBlank(message = "API version is required")
    private String version = "v1";
    
    @NotBlank(message = "API base path is required")
    private String basePath = "/api";
    
    @NotNull
    private String corsAllowedOrigins = "*";
    
    @NotNull
    private String corsAllowedMethods = "GET,POST,PUT,DELETE,OPTIONS";
    
    @NotNull
    private String corsAllowedHeaders = "Content-Type,Authorization,X-User-Id";
    
    private boolean corsAllowCredentials = true;
    
    @Min(value = 0, message = "CORS max age cannot be negative")
    @Max(value = 86400, message = "CORS max age cannot exceed 24 hours")
    private int corsMaxAgeSeconds = 3600;
    
    // External Service URLs
    @NotBlank(message = "Workflow engine URL is required")
    private String workflowEngineUrl = "http://localhost:8081";
    
    @NotBlank(message = "User service URL is required")
    private String userServiceUrl = "http://localhost:8082";
    
    @NotBlank(message = "Notification service URL is required")
    private String notificationServiceUrl = "http://localhost:8083";
    
    private boolean enableCircuitBreaker = true;
    
    @Min(value = 1, message = "Circuit breaker failure threshold must be at least 1")
    @Max(value = 100, message = "Circuit breaker failure threshold cannot exceed 100")
    private int circuitBreakerFailureThreshold = 5;
    
    @Min(value = 1000, message = "Circuit breaker timeout must be at least 1 second")
    @Max(value = 300000, message = "Circuit breaker timeout cannot exceed 5 minutes")
    private long circuitBreakerTimeoutMs = 60000;
    
    // Getters and Setters
    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }
    
    public void setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
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
    
    public int getMaxConnections() {
        return maxConnections;
    }
    
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }
    
    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }
    
    public void setMaxConnectionsPerRoute(int maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
    }
    
    public int getRateLimitRequestsPerMinute() {
        return rateLimitRequestsPerMinute;
    }
    
    public void setRateLimitRequestsPerMinute(int rateLimitRequestsPerMinute) {
        this.rateLimitRequestsPerMinute = rateLimitRequestsPerMinute;
    }
    
    public int getRateLimitBurstSize() {
        return rateLimitBurstSize;
    }
    
    public void setRateLimitBurstSize(int rateLimitBurstSize) {
        this.rateLimitBurstSize = rateLimitBurstSize;
    }
    
    public boolean isEnableRateLimiting() {
        return enableRateLimiting;
    }
    
    public void setEnableRateLimiting(boolean enableRateLimiting) {
        this.enableRateLimiting = enableRateLimiting;
    }
    
    public boolean isEnableRequestLogging() {
        return enableRequestLogging;
    }
    
    public void setEnableRequestLogging(boolean enableRequestLogging) {
        this.enableRequestLogging = enableRequestLogging;
    }
    
    public boolean isEnableResponseLogging() {
        return enableResponseLogging;
    }
    
    public void setEnableResponseLogging(boolean enableResponseLogging) {
        this.enableResponseLogging = enableResponseLogging;
    }
    
    public boolean isEnableMetrics() {
        return enableMetrics;
    }
    
    public void setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getBasePath() {
        return basePath;
    }
    
    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
    
    public String getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }
    
    public void setCorsAllowedOrigins(String corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }
    
    public String getCorsAllowedMethods() {
        return corsAllowedMethods;
    }
    
    public void setCorsAllowedMethods(String corsAllowedMethods) {
        this.corsAllowedMethods = corsAllowedMethods;
    }
    
    public String getCorsAllowedHeaders() {
        return corsAllowedHeaders;
    }
    
    public void setCorsAllowedHeaders(String corsAllowedHeaders) {
        this.corsAllowedHeaders = corsAllowedHeaders;
    }
    
    public boolean isCorsAllowCredentials() {
        return corsAllowCredentials;
    }
    
    public void setCorsAllowCredentials(boolean corsAllowCredentials) {
        this.corsAllowCredentials = corsAllowCredentials;
    }
    
    public int getCorsMaxAgeSeconds() {
        return corsMaxAgeSeconds;
    }
    
    public void setCorsMaxAgeSeconds(int corsMaxAgeSeconds) {
        this.corsMaxAgeSeconds = corsMaxAgeSeconds;
    }
    
    public String getWorkflowEngineUrl() {
        return workflowEngineUrl;
    }
    
    public void setWorkflowEngineUrl(String workflowEngineUrl) {
        this.workflowEngineUrl = workflowEngineUrl;
    }
    
    public String getUserServiceUrl() {
        return userServiceUrl;
    }
    
    public void setUserServiceUrl(String userServiceUrl) {
        this.userServiceUrl = userServiceUrl;
    }
    
    public String getNotificationServiceUrl() {
        return notificationServiceUrl;
    }
    
    public void setNotificationServiceUrl(String notificationServiceUrl) {
        this.notificationServiceUrl = notificationServiceUrl;
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
}