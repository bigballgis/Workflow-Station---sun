package com.platform.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Cache Configuration
 * 
 * Externalized cache settings for Redis and local caching.
 * Supports runtime updates for TTL and pool settings.
 * 
 * @author Platform Team
 * @version 1.0
 */
@RuntimeUpdatable(requiresRestart = {"redisHost", "redisPort"})
public class CacheConfig {
    
    @NotBlank(message = "Redis host is required")
    private String redisHost = "localhost";
    
    @Min(value = 1, message = "Redis port must be at least 1")
    @Max(value = 65535, message = "Redis port cannot exceed 65535")
    private int redisPort = 6379;
    
    private String redisPassword = "redis123";
    
    @Min(value = 0, message = "Redis database cannot be negative")
    @Max(value = 15, message = "Redis database cannot exceed 15")
    private int redisDatabase = 1;
    
    @Min(value = 1000, message = "Redis timeout must be at least 1 second")
    @Max(value = 300000, message = "Redis timeout cannot exceed 5 minutes")
    private long redisTimeoutMs = 5000;
    
    @Min(value = 1, message = "Redis max active connections must be at least 1")
    @Max(value = 1000, message = "Redis max active connections cannot exceed 1000")
    private int redisMaxActive = 20;
    
    @Min(value = 0, message = "Redis max idle connections cannot be negative")
    @Max(value = 100, message = "Redis max idle connections cannot exceed 100")
    private int redisMaxIdle = 10;
    
    @Min(value = 0, message = "Redis min idle connections cannot be negative")
    @Max(value = 50, message = "Redis min idle connections cannot exceed 50")
    private int redisMinIdle = 5;
    
    @Min(value = 1000, message = "Redis max wait must be at least 1 second")
    @Max(value = 60000, message = "Redis max wait cannot exceed 1 minute")
    private long redisMaxWaitMs = 3000;
    
    // Cache TTL Settings (in minutes)
    @Min(value = 1, message = "User cache TTL must be at least 1 minute")
    @Max(value = 1440, message = "User cache TTL cannot exceed 24 hours")
    private int userCacheTtlMinutes = 30;
    
    @Min(value = 1, message = "Permission cache TTL must be at least 1 minute")
    @Max(value = 1440, message = "Permission cache TTL cannot exceed 24 hours")
    private int permissionCacheTtlMinutes = 60;
    
    @Min(value = 1, message = "Dictionary cache TTL must be at least 1 minute")
    @Max(value = 1440, message = "Dictionary cache TTL cannot exceed 24 hours")
    private int dictionaryCacheTtlMinutes = 120;
    
    @Min(value = 1, message = "Session cache TTL must be at least 1 minute")
    @Max(value = 1440, message = "Session cache TTL cannot exceed 24 hours")
    private int sessionCacheTtlMinutes = 30;
    
    @Min(value = 1, message = "Configuration cache TTL must be at least 1 minute")
    @Max(value = 1440, message = "Configuration cache TTL cannot exceed 24 hours")
    private int configurationCacheTtlMinutes = 60;
    
    private boolean enableLocalCache = true;
    private boolean enableDistributedCache = true;
    private boolean enableCacheMetrics = true;
    
    @Min(value = 100, message = "Local cache max size must be at least 100")
    @Max(value = 100000, message = "Local cache max size cannot exceed 100000")
    private int localCacheMaxSize = 10000;
    
    @Min(value = 1, message = "Local cache expire after write must be at least 1 minute")
    @Max(value = 1440, message = "Local cache expire after write cannot exceed 24 hours")
    private int localCacheExpireAfterWriteMinutes = 30;
    
    @Min(value = 1, message = "Local cache expire after access must be at least 1 minute")
    @Max(value = 1440, message = "Local cache expire after access cannot exceed 24 hours")
    private int localCacheExpireAfterAccessMinutes = 15;
    
    // Getters and Setters
    public String getRedisHost() {
        return redisHost;
    }
    
    public void setRedisHost(String redisHost) {
        this.redisHost = redisHost;
    }
    
    public int getRedisPort() {
        return redisPort;
    }
    
    public void setRedisPort(int redisPort) {
        this.redisPort = redisPort;
    }
    
    public String getRedisPassword() {
        return redisPassword;
    }
    
    public void setRedisPassword(String redisPassword) {
        this.redisPassword = redisPassword;
    }
    
    public int getRedisDatabase() {
        return redisDatabase;
    }
    
    public void setRedisDatabase(int redisDatabase) {
        this.redisDatabase = redisDatabase;
    }
    
    public long getRedisTimeoutMs() {
        return redisTimeoutMs;
    }
    
    public void setRedisTimeoutMs(long redisTimeoutMs) {
        this.redisTimeoutMs = redisTimeoutMs;
    }
    
    public int getRedisMaxActive() {
        return redisMaxActive;
    }
    
    public void setRedisMaxActive(int redisMaxActive) {
        this.redisMaxActive = redisMaxActive;
    }
    
    public int getRedisMaxIdle() {
        return redisMaxIdle;
    }
    
    public void setRedisMaxIdle(int redisMaxIdle) {
        this.redisMaxIdle = redisMaxIdle;
    }
    
    public int getRedisMinIdle() {
        return redisMinIdle;
    }
    
    public void setRedisMinIdle(int redisMinIdle) {
        this.redisMinIdle = redisMinIdle;
    }
    
    public long getRedisMaxWaitMs() {
        return redisMaxWaitMs;
    }
    
    public void setRedisMaxWaitMs(long redisMaxWaitMs) {
        this.redisMaxWaitMs = redisMaxWaitMs;
    }
    
    public int getUserCacheTtlMinutes() {
        return userCacheTtlMinutes;
    }
    
    public void setUserCacheTtlMinutes(int userCacheTtlMinutes) {
        this.userCacheTtlMinutes = userCacheTtlMinutes;
    }
    
    public int getPermissionCacheTtlMinutes() {
        return permissionCacheTtlMinutes;
    }
    
    public void setPermissionCacheTtlMinutes(int permissionCacheTtlMinutes) {
        this.permissionCacheTtlMinutes = permissionCacheTtlMinutes;
    }
    
    public int getDictionaryCacheTtlMinutes() {
        return dictionaryCacheTtlMinutes;
    }
    
    public void setDictionaryCacheTtlMinutes(int dictionaryCacheTtlMinutes) {
        this.dictionaryCacheTtlMinutes = dictionaryCacheTtlMinutes;
    }
    
    public int getSessionCacheTtlMinutes() {
        return sessionCacheTtlMinutes;
    }
    
    public void setSessionCacheTtlMinutes(int sessionCacheTtlMinutes) {
        this.sessionCacheTtlMinutes = sessionCacheTtlMinutes;
    }
    
    public int getConfigurationCacheTtlMinutes() {
        return configurationCacheTtlMinutes;
    }
    
    public void setConfigurationCacheTtlMinutes(int configurationCacheTtlMinutes) {
        this.configurationCacheTtlMinutes = configurationCacheTtlMinutes;
    }
    
    public boolean isEnableLocalCache() {
        return enableLocalCache;
    }
    
    public void setEnableLocalCache(boolean enableLocalCache) {
        this.enableLocalCache = enableLocalCache;
    }
    
    public boolean isEnableDistributedCache() {
        return enableDistributedCache;
    }
    
    public void setEnableDistributedCache(boolean enableDistributedCache) {
        this.enableDistributedCache = enableDistributedCache;
    }
    
    public boolean isEnableCacheMetrics() {
        return enableCacheMetrics;
    }
    
    public void setEnableCacheMetrics(boolean enableCacheMetrics) {
        this.enableCacheMetrics = enableCacheMetrics;
    }
    
    public int getLocalCacheMaxSize() {
        return localCacheMaxSize;
    }
    
    public void setLocalCacheMaxSize(int localCacheMaxSize) {
        this.localCacheMaxSize = localCacheMaxSize;
    }
    
    public int getLocalCacheExpireAfterWriteMinutes() {
        return localCacheExpireAfterWriteMinutes;
    }
    
    public void setLocalCacheExpireAfterWriteMinutes(int localCacheExpireAfterWriteMinutes) {
        this.localCacheExpireAfterWriteMinutes = localCacheExpireAfterWriteMinutes;
    }
    
    public int getLocalCacheExpireAfterAccessMinutes() {
        return localCacheExpireAfterAccessMinutes;
    }
    
    public void setLocalCacheExpireAfterAccessMinutes(int localCacheExpireAfterAccessMinutes) {
        this.localCacheExpireAfterAccessMinutes = localCacheExpireAfterAccessMinutes;
    }
}