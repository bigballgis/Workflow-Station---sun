package com.developer.config;

import com.developer.security.SecurityAuditLogger;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for the security permission system.
 * Provides centralized configuration management with validation.
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.5
 */
@Configuration
@ConfigurationProperties(prefix = "security")
@Data
@Validated
@Slf4j
public class SecurityConfigurationProperties {
    
    private final SecurityAuditLogger auditLogger;
    
    public SecurityConfigurationProperties(SecurityAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }
    
    /**
     * Cache configuration properties
     */
    private Cache cache = new Cache();
    
    /**
     * Database configuration properties
     */
    private Database database = new Database();
    
    /**
     * Permission system configuration properties
     */
    private Permission permission = new Permission();
    
    @Data
    public static class Cache {
        
        /**
         * Session timeout in minutes for security cache entries
         */
        @Min(value = 1, message = "Cache session timeout must be at least 1 minute")
        @Max(value = 1440, message = "Cache session timeout must not exceed 1440 minutes (24 hours)")
        private int sessionTimeoutMinutes = 30;
        
        /**
         * Maximum number of users that can be cached simultaneously
         */
        @Min(value = 10, message = "Cache max size must be at least 10")
        @Max(value = 100000, message = "Cache max size must not exceed 100000")
        private int maxSize = 1000;
        
        /**
         * Whether caching is enabled for permission and role checks
         */
        @NotNull(message = "Cache enabled flag must not be null")
        private Boolean enabled = true;
        
        /**
         * Cleanup interval in minutes for expired cache entries
         */
        @Min(value = 1, message = "Cache cleanup interval must be at least 1 minute")
        @Max(value = 60, message = "Cache cleanup interval must not exceed 60 minutes")
        private int cleanupIntervalMinutes = 15;
    }
    
    @Data
    public static class Database {
        
        /**
         * Query timeout in seconds for permission and role database queries
         */
        @Min(value = 1, message = "Database query timeout must be at least 1 second")
        @Max(value = 300, message = "Database query timeout must not exceed 300 seconds")
        private int queryTimeoutSeconds = 30;
        
        /**
         * Maximum number of retry attempts for failed database queries
         */
        @Min(value = 0, message = "Database retry attempts must be at least 0")
        @Max(value = 5, message = "Database retry attempts must not exceed 5")
        private int retryAttempts = 2;
        
        /**
         * Delay in milliseconds between retry attempts
         */
        @Min(value = 100, message = "Database retry delay must be at least 100ms")
        @Max(value = 10000, message = "Database retry delay must not exceed 10000ms")
        private int retryDelayMs = 1000;
        
        /**
         * Whether to enable connection pooling for security queries
         */
        @NotNull(message = "Database connection pooling flag must not be null")
        private Boolean connectionPoolingEnabled = true;
    }
    
    @Data
    public static class Permission {
        
        /**
         * Default permission resolution strategy
         */
        @NotNull(message = "Permission resolution strategy must not be null")
        private ResolutionStrategy resolutionStrategy = ResolutionStrategy.DATABASE_FIRST;
        
        /**
         * Whether to enable strict permission checking (fail on any error)
         */
        @NotNull(message = "Strict permission checking flag must not be null")
        private Boolean strictChecking = true;
        
        /**
         * Whether to log all permission checks for auditing
         */
        @NotNull(message = "Audit logging flag must not be null")
        private Boolean auditLogging = true;
        
        /**
         * Maximum permission name length for validation
         */
        @Min(value = 10, message = "Max permission name length must be at least 10")
        @Max(value = 255, message = "Max permission name length must not exceed 255")
        private int maxPermissionNameLength = 100;
        
        /**
         * Maximum role name length for validation
         */
        @Min(value = 10, message = "Max role name length must be at least 10")
        @Max(value = 255, message = "Max role name length must not exceed 255")
        private int maxRoleNameLength = 100;
    }
    
    /**
     * Permission resolution strategies
     */
    public enum ResolutionStrategy {
        /**
         * Check database first, fall back to cache on error
         */
        DATABASE_FIRST,
        
        /**
         * Check cache first, query database on miss
         */
        CACHE_FIRST,
        
        /**
         * Only use database, no caching
         */
        DATABASE_ONLY,
        
        /**
         * Only use cache, fail if not cached
         */
        CACHE_ONLY
    }
    
    /**
     * Validate configuration parameters at startup
     */
    @PostConstruct
    public void validateConfiguration() {
        log.info("Validating security configuration properties...");
        
        try {
            validateCacheConfiguration();
            validateDatabaseConfiguration();
            validatePermissionConfiguration();
            
            auditLogger.logConfigurationValidation("security.cache", true, null);
            auditLogger.logConfigurationValidation("security.database", true, null);
            auditLogger.logConfigurationValidation("security.permission", true, null);
            
            log.info("Security configuration validation completed successfully");
            
        } catch (Exception e) {
            String errorMessage = "Security configuration validation failed: " + e.getMessage();
            auditLogger.logConfigurationValidation("security", false, errorMessage);
            log.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        }
    }
    
    /**
     * Validate cache configuration
     */
    private void validateCacheConfiguration() {
        if (cache.sessionTimeoutMinutes <= 0 || cache.sessionTimeoutMinutes > 1440) {
            throw new IllegalArgumentException("Cache session timeout must be between 1 and 1440 minutes");
        }
        
        if (cache.maxSize < 10 || cache.maxSize > 100000) {
            throw new IllegalArgumentException("Cache max size must be between 10 and 100000");
        }
        
        if (cache.cleanupIntervalMinutes <= 0 || cache.cleanupIntervalMinutes > 60) {
            throw new IllegalArgumentException("Cache cleanup interval must be between 1 and 60 minutes");
        }
        
        if (cache.cleanupIntervalMinutes > cache.sessionTimeoutMinutes) {
            log.warn("Cache cleanup interval ({} min) is greater than session timeout ({} min), " +
                    "this may cause performance issues", 
                    cache.cleanupIntervalMinutes, cache.sessionTimeoutMinutes);
        }
        
        log.debug("Cache configuration validated: enabled={}, sessionTimeout={}min, maxSize={}, cleanupInterval={}min",
                cache.enabled, cache.sessionTimeoutMinutes, cache.maxSize, cache.cleanupIntervalMinutes);
    }
    
    /**
     * Validate database configuration
     */
    private void validateDatabaseConfiguration() {
        if (database.queryTimeoutSeconds <= 0 || database.queryTimeoutSeconds > 300) {
            throw new IllegalArgumentException("Database query timeout must be between 1 and 300 seconds");
        }
        
        if (database.retryAttempts < 0 || database.retryAttempts > 5) {
            throw new IllegalArgumentException("Database retry attempts must be between 0 and 5");
        }
        
        if (database.retryDelayMs < 100 || database.retryDelayMs > 10000) {
            throw new IllegalArgumentException("Database retry delay must be between 100 and 10000 milliseconds");
        }
        
        if (database.queryTimeoutSeconds > 60) {
            log.warn("Database query timeout ({} seconds) is quite high, " +
                    "this may impact application responsiveness", database.queryTimeoutSeconds);
        }
        
        log.debug("Database configuration validated: queryTimeout={}s, retryAttempts={}, retryDelay={}ms, pooling={}",
                database.queryTimeoutSeconds, database.retryAttempts, database.retryDelayMs, 
                database.connectionPoolingEnabled);
    }
    
    /**
     * Validate permission configuration
     */
    private void validatePermissionConfiguration() {
        if (permission.resolutionStrategy == null) {
            throw new IllegalArgumentException("Permission resolution strategy must not be null");
        }
        
        if (permission.maxPermissionNameLength < 10 || permission.maxPermissionNameLength > 255) {
            throw new IllegalArgumentException("Max permission name length must be between 10 and 255");
        }
        
        if (permission.maxRoleNameLength < 10 || permission.maxRoleNameLength > 255) {
            throw new IllegalArgumentException("Max role name length must be between 10 and 255");
        }
        
        // Validate strategy compatibility with cache settings
        if (permission.resolutionStrategy == ResolutionStrategy.CACHE_ONLY && !cache.enabled) {
            throw new IllegalArgumentException("Cannot use CACHE_ONLY resolution strategy when caching is disabled");
        }
        
        if (permission.resolutionStrategy == ResolutionStrategy.CACHE_FIRST && !cache.enabled) {
            log.warn("CACHE_FIRST resolution strategy is configured but caching is disabled, " +
                    "falling back to DATABASE_FIRST");
        }
        
        log.debug("Permission configuration validated: strategy={}, strictChecking={}, auditLogging={}, " +
                "maxPermissionLength={}, maxRoleLength={}",
                permission.resolutionStrategy, permission.strictChecking, permission.auditLogging,
                permission.maxPermissionNameLength, permission.maxRoleNameLength);
    }
    
    /**
     * Get configuration summary for monitoring
     */
    public String getConfigurationSummary() {
        return String.format(
                "SecurityConfig[cache.enabled=%s, cache.timeout=%dmin, cache.maxSize=%d, " +
                "db.queryTimeout=%ds, db.retryAttempts=%d, permission.strategy=%s, permission.strict=%s]",
                cache.enabled, cache.sessionTimeoutMinutes, cache.maxSize,
                database.queryTimeoutSeconds, database.retryAttempts,
                permission.resolutionStrategy, permission.strictChecking
        );
    }
    
    /**
     * Check if a configuration change requires system restart
     */
    public boolean requiresRestart(SecurityConfigurationProperties other) {
        if (other == null) return true;
        
        // Changes that require restart
        return !this.cache.enabled.equals(other.cache.enabled) ||
               !this.database.connectionPoolingEnabled.equals(other.database.connectionPoolingEnabled) ||
               !this.permission.resolutionStrategy.equals(other.permission.resolutionStrategy);
    }
    
    /**
     * Validate a permission name against configuration limits
     */
    public boolean isValidPermissionName(String permissionName) {
        if (permissionName == null || permissionName.trim().isEmpty()) {
            return false;
        }
        
        return permissionName.length() <= permission.maxPermissionNameLength &&
               !permissionName.contains("\n") &&
               !permissionName.contains("\r") &&
               !permissionName.contains("\t");
    }
    
    /**
     * Validate a role name against configuration limits
     */
    public boolean isValidRoleName(String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) {
            return false;
        }
        
        return roleName.length() <= permission.maxRoleNameLength &&
               !roleName.contains("\n") &&
               !roleName.contains("\r") &&
               !roleName.contains("\t");
    }
}