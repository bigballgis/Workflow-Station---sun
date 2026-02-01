package com.platform.common.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Configuration Access Audit Logger
 * 
 * Provides comprehensive audit logging for configuration access, changes,
 * and security events related to configuration management.
 * 
 * Logs configuration access patterns, sensitive data access, and configuration
 * changes while ensuring sensitive data is not exposed in logs.
 * 
 * @author Platform Team
 * @version 1.0
 */
@Service
public class ConfigurationAuditLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationAuditLogger.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("CONFIGURATION_AUDIT");
    
    private final ConfigurationEncryptionService encryptionService;
    
    // Metrics tracking
    private final AtomicLong configurationAccessCount = new AtomicLong(0);
    private final AtomicLong sensitiveConfigurationAccessCount = new AtomicLong(0);
    private final AtomicLong configurationChangeCount = new AtomicLong(0);
    private final Map<String, AtomicLong> keyAccessCounts = new ConcurrentHashMap<>();
    
    @Autowired
    public ConfigurationAuditLogger(ConfigurationEncryptionService encryptionService) {
        this.encryptionService = encryptionService;
        logger.info("Configuration audit logger initialized");
    }
    
    /**
     * Log configuration value access
     * 
     * @param key Configuration key
     * @param value Configuration value (will be masked if sensitive)
     * @param source Configuration source (file, env, etc.)
     * @param requestContext Additional context information
     */
    public void logConfigurationAccess(String key, String value, String source, Map<String, String> requestContext) {
        configurationAccessCount.incrementAndGet();
        keyAccessCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        
        boolean isSensitive = encryptionService.isSensitiveKey(key);
        if (isSensitive) {
            sensitiveConfigurationAccessCount.incrementAndGet();
        }
        
        String maskedValue = encryptionService.maskSensitiveValue(key, value);
        
        try {
            // Set MDC context for structured logging
            MDC.put("audit.event", "CONFIG_ACCESS");
            MDC.put("audit.timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            MDC.put("config.key", key);
            MDC.put("config.source", source);
            MDC.put("config.sensitive", String.valueOf(isSensitive));
            
            if (requestContext != null) {
                requestContext.forEach((k, v) -> MDC.put("context." + k, v));
            }
            
            auditLogger.info("Configuration accessed: key={}, source={}, sensitive={}, value={}", 
                           key, source, isSensitive, maskedValue);
            
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log configuration value change
     * 
     * @param key Configuration key
     * @param oldValue Previous configuration value
     * @param newValue New configuration value
     * @param source Configuration source
     * @param changeContext Additional context information
     */
    public void logConfigurationChange(String key, String oldValue, String newValue, String source, Map<String, String> changeContext) {
        configurationChangeCount.incrementAndGet();
        
        boolean isSensitive = encryptionService.isSensitiveKey(key);
        String maskedOldValue = encryptionService.maskSensitiveValue(key, oldValue);
        String maskedNewValue = encryptionService.maskSensitiveValue(key, newValue);
        
        try {
            // Set MDC context for structured logging
            MDC.put("audit.event", "CONFIG_CHANGE");
            MDC.put("audit.timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            MDC.put("config.key", key);
            MDC.put("config.source", source);
            MDC.put("config.sensitive", String.valueOf(isSensitive));
            
            if (changeContext != null) {
                changeContext.forEach((k, v) -> MDC.put("context." + k, v));
            }
            
            auditLogger.warn("Configuration changed: key={}, source={}, sensitive={}, oldValue={}, newValue={}", 
                           key, source, isSensitive, maskedOldValue, maskedNewValue);
            
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log configuration reload event
     * 
     * @param reloadContext Context information about the reload
     */
    public void logConfigurationReload(Map<String, String> reloadContext) {
        try {
            MDC.put("audit.event", "CONFIG_RELOAD");
            MDC.put("audit.timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            if (reloadContext != null) {
                reloadContext.forEach((k, v) -> MDC.put("context." + k, v));
            }
            
            auditLogger.warn("Configuration reloaded: totalAccess={}, sensitiveAccess={}, totalChanges={}", 
                           configurationAccessCount.get(), sensitiveConfigurationAccessCount.get(), configurationChangeCount.get());
            
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log configuration validation failure
     * 
     * @param key Configuration key
     * @param value Configuration value
     * @param validationError Validation error message
     * @param validationContext Additional context information
     */
    public void logConfigurationValidationFailure(String key, String value, String validationError, Map<String, String> validationContext) {
        boolean isSensitive = encryptionService.isSensitiveKey(key);
        String maskedValue = encryptionService.maskSensitiveValue(key, value);
        
        try {
            MDC.put("audit.event", "CONFIG_VALIDATION_FAILURE");
            MDC.put("audit.timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            MDC.put("config.key", key);
            MDC.put("config.sensitive", String.valueOf(isSensitive));
            MDC.put("validation.error", validationError);
            
            if (validationContext != null) {
                validationContext.forEach((k, v) -> MDC.put("context." + k, v));
            }
            
            auditLogger.error("Configuration validation failed: key={}, sensitive={}, value={}, error={}", 
                            key, isSensitive, maskedValue, validationError);
            
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log security event related to configuration
     * 
     * @param eventType Type of security event
     * @param description Event description
     * @param securityContext Security context information
     */
    public void logSecurityEvent(String eventType, String description, Map<String, String> securityContext) {
        try {
            MDC.put("audit.event", "CONFIG_SECURITY_EVENT");
            MDC.put("audit.timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            MDC.put("security.event.type", eventType);
            MDC.put("security.description", description);
            
            if (securityContext != null) {
                securityContext.forEach((k, v) -> MDC.put("security." + k, v));
            }
            
            auditLogger.error("Configuration security event: type={}, description={}", eventType, description);
            
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Log encryption/decryption operation
     * 
     * @param operation Operation type (encrypt/decrypt)
     * @param key Configuration key
     * @param success Whether the operation was successful
     * @param errorMessage Error message if operation failed
     */
    public void logEncryptionOperation(String operation, String key, boolean success, String errorMessage) {
        try {
            MDC.put("audit.event", "CONFIG_ENCRYPTION_OPERATION");
            MDC.put("audit.timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            MDC.put("encryption.operation", operation);
            MDC.put("config.key", key);
            MDC.put("encryption.success", String.valueOf(success));
            
            if (errorMessage != null) {
                MDC.put("encryption.error", errorMessage);
            }
            
            if (success) {
                auditLogger.info("Configuration encryption operation: operation={}, key={}, success={}", 
                               operation, key, success);
            } else {
                auditLogger.error("Configuration encryption operation failed: operation={}, key={}, error={}", 
                                operation, key, errorMessage);
            }
            
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Get configuration access metrics
     * 
     * @return Map containing access metrics
     */
    public Map<String, Object> getAccessMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("totalConfigurationAccess", configurationAccessCount.get());
        metrics.put("sensitiveConfigurationAccess", sensitiveConfigurationAccessCount.get());
        metrics.put("configurationChanges", configurationChangeCount.get());
        metrics.put("keyAccessCounts", new ConcurrentHashMap<>(keyAccessCounts));
        metrics.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return metrics;
    }
    
    /**
     * Reset access metrics
     */
    public void resetMetrics() {
        configurationAccessCount.set(0);
        sensitiveConfigurationAccessCount.set(0);
        configurationChangeCount.set(0);
        keyAccessCounts.clear();
        
        auditLogger.info("Configuration access metrics reset");
    }
}