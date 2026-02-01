package com.platform.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Monitoring Configuration
 * 
 * Externalized monitoring and observability settings.
 * Supports runtime updates for most properties.
 * 
 * @author Platform Team
 * @version 1.0
 */
@RuntimeUpdatable
public class MonitoringConfig {
    
    private boolean enableMetrics = true;
    private boolean enableHealthChecks = true;
    private boolean enableTracing = true;
    private boolean enableLogging = true;
    
    @NotBlank(message = "Metrics endpoint path is required")
    private String metricsEndpoint = "/actuator/metrics";
    
    @NotBlank(message = "Health endpoint path is required")
    private String healthEndpoint = "/actuator/health";
    
    @NotBlank(message = "Info endpoint path is required")
    private String infoEndpoint = "/actuator/info";
    
    @Min(value = 1, message = "Metrics collection interval must be at least 1 second")
    @Max(value = 3600, message = "Metrics collection interval cannot exceed 1 hour")
    private int metricsCollectionIntervalSeconds = 60;
    
    @Min(value = 1, message = "Health check interval must be at least 1 second")
    @Max(value = 3600, message = "Health check interval cannot exceed 1 hour")
    private int healthCheckIntervalSeconds = 30;
    
    @Min(value = 1, message = "Log retention days must be at least 1")
    @Max(value = 3650, message = "Log retention days cannot exceed 10 years")
    private int logRetentionDays = 30;
    
    @Min(value = 1, message = "Max log file size must be at least 1 MB")
    @Max(value = 1024, message = "Max log file size cannot exceed 1 GB")
    private int maxLogFileSizeMB = 10;
    
    @NotBlank(message = "Log level is required")
    private String logLevel = "INFO";
    
    @NotBlank(message = "Log pattern is required")
    private String logPattern = "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n";
    
    private boolean enableAuditLogging = true;
    
    @Min(value = 1, message = "Audit retention days must be at least 1")
    @Max(value = 3650, message = "Audit retention days cannot exceed 10 years")
    private int auditRetentionDays = 365;
    
    private boolean enablePerformanceMonitoring = true;
    
    @Min(value = 100, message = "Performance threshold must be at least 100ms")
    @Max(value = 60000, message = "Performance threshold cannot exceed 1 minute")
    private long performanceThresholdMs = 1000;
    
    private boolean enableAlerts = true;
    
    @NotBlank(message = "Alert notification URL is required")
    private String alertNotificationUrl = "http://localhost:8083/notifications/alerts";
    
    // Getters and Setters
    public boolean isEnableMetrics() {
        return enableMetrics;
    }
    
    public void setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }
    
    public boolean isEnableHealthChecks() {
        return enableHealthChecks;
    }
    
    public void setEnableHealthChecks(boolean enableHealthChecks) {
        this.enableHealthChecks = enableHealthChecks;
    }
    
    public boolean isEnableTracing() {
        return enableTracing;
    }
    
    public void setEnableTracing(boolean enableTracing) {
        this.enableTracing = enableTracing;
    }
    
    public boolean isEnableLogging() {
        return enableLogging;
    }
    
    public void setEnableLogging(boolean enableLogging) {
        this.enableLogging = enableLogging;
    }
    
    public String getMetricsEndpoint() {
        return metricsEndpoint;
    }
    
    public void setMetricsEndpoint(String metricsEndpoint) {
        this.metricsEndpoint = metricsEndpoint;
    }
    
    public String getHealthEndpoint() {
        return healthEndpoint;
    }
    
    public void setHealthEndpoint(String healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }
    
    public String getInfoEndpoint() {
        return infoEndpoint;
    }
    
    public void setInfoEndpoint(String infoEndpoint) {
        this.infoEndpoint = infoEndpoint;
    }
    
    public int getMetricsCollectionIntervalSeconds() {
        return metricsCollectionIntervalSeconds;
    }
    
    public void setMetricsCollectionIntervalSeconds(int metricsCollectionIntervalSeconds) {
        this.metricsCollectionIntervalSeconds = metricsCollectionIntervalSeconds;
    }
    
    public int getHealthCheckIntervalSeconds() {
        return healthCheckIntervalSeconds;
    }
    
    public void setHealthCheckIntervalSeconds(int healthCheckIntervalSeconds) {
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
    }
    
    public int getLogRetentionDays() {
        return logRetentionDays;
    }
    
    public void setLogRetentionDays(int logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }
    
    public int getMaxLogFileSizeMB() {
        return maxLogFileSizeMB;
    }
    
    public void setMaxLogFileSizeMB(int maxLogFileSizeMB) {
        this.maxLogFileSizeMB = maxLogFileSizeMB;
    }
    
    public String getLogLevel() {
        return logLevel;
    }
    
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
    
    public String getLogPattern() {
        return logPattern;
    }
    
    public void setLogPattern(String logPattern) {
        this.logPattern = logPattern;
    }
    
    public boolean isEnableAuditLogging() {
        return enableAuditLogging;
    }
    
    public void setEnableAuditLogging(boolean enableAuditLogging) {
        this.enableAuditLogging = enableAuditLogging;
    }
    
    public int getAuditRetentionDays() {
        return auditRetentionDays;
    }
    
    public void setAuditRetentionDays(int auditRetentionDays) {
        this.auditRetentionDays = auditRetentionDays;
    }
    
    public boolean isEnablePerformanceMonitoring() {
        return enablePerformanceMonitoring;
    }
    
    public void setEnablePerformanceMonitoring(boolean enablePerformanceMonitoring) {
        this.enablePerformanceMonitoring = enablePerformanceMonitoring;
    }
    
    public long getPerformanceThresholdMs() {
        return performanceThresholdMs;
    }
    
    public void setPerformanceThresholdMs(long performanceThresholdMs) {
        this.performanceThresholdMs = performanceThresholdMs;
    }
    
    public boolean isEnableAlerts() {
        return enableAlerts;
    }
    
    public void setEnableAlerts(boolean enableAlerts) {
        this.enableAlerts = enableAlerts;
    }
    
    public String getAlertNotificationUrl() {
        return alertNotificationUrl;
    }
    
    public void setAlertNotificationUrl(String alertNotificationUrl) {
        this.alertNotificationUrl = alertNotificationUrl;
    }
}