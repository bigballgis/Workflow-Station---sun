package com.platform.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Application Configuration
 * 
 * Central configuration class that aggregates all application settings
 * from external configuration files and environment variables
 * 
 * @author Platform Team
 * @version 1.0
 */
@ConfigurationProperties(prefix = "app")
public class ApplicationConfiguration {
    
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private DatabaseConfig database = new DatabaseConfig();
    
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private SecurityConfig security = new SecurityConfig();
    
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private ApiConfig api = new ApiConfig();
    
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private MonitoringConfig monitoring = new MonitoringConfig();
    
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private CacheConfig cache = new CacheConfig();
    
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private MessagingConfig messaging = new MessagingConfig();
    
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private WorkflowConfig workflow = new WorkflowConfig();
    
    // Getters and Setters
    public DatabaseConfig getDatabase() {
        return database;
    }
    
    public void setDatabase(DatabaseConfig database) {
        this.database = database;
    }
    
    public SecurityConfig getSecurity() {
        return security;
    }
    
    public void setSecurity(SecurityConfig security) {
        this.security = security;
    }
    
    public ApiConfig getApi() {
        return api;
    }
    
    public void setApi(ApiConfig api) {
        this.api = api;
    }
    
    public MonitoringConfig getMonitoring() {
        return monitoring;
    }
    
    public void setMonitoring(MonitoringConfig monitoring) {
        this.monitoring = monitoring;
    }
    
    public CacheConfig getCache() {
        return cache;
    }
    
    public void setCache(CacheConfig cache) {
        this.cache = cache;
    }
    
    public MessagingConfig getMessaging() {
        return messaging;
    }
    
    public void setMessaging(MessagingConfig messaging) {
        this.messaging = messaging;
    }
    
    public WorkflowConfig getWorkflow() {
        return workflow;
    }
    
    public void setWorkflow(WorkflowConfig workflow) {
        this.workflow = workflow;
    }
}