package com.developer.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Security audit logger for comprehensive logging and monitoring.
 * Provides detailed error logging, security event logging, and log categorization.
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.5
 */
@Component
public class SecurityAuditLogger {
    
    private final Logger log;
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // Log categories for different types of events
    private static final String CATEGORY_DATABASE_ERROR = "DATABASE_ERROR";
    private static final String CATEGORY_SECURITY_EVENT = "SECURITY_EVENT";
    private static final String CATEGORY_ACCESS_DENIED = "ACCESS_DENIED";
    private static final String CATEGORY_SYSTEM_ERROR = "SYSTEM_ERROR";
    
    /**
     * Default constructor using standard logger.
     */
    public SecurityAuditLogger() {
        this.log = LoggerFactory.getLogger(SecurityAuditLogger.class);
    }
    
    /**
     * Constructor for testing with injectable logger.
     * 
     * @param logger the logger to use
     */
    SecurityAuditLogger(Logger logger) {
        this.log = logger;
    }
    
    /**
     * Log database failure during permission or role checks.
     * 
     * @param username the username (sanitized)
     * @param operation the operation being performed (permission/role check)
     * @param resource the resource being accessed (permission/role name)
     * @param error the error that occurred
     */
    public void logDatabaseError(String username, String operation, String resource, Throwable error) {
        String sanitizedUsername = sanitizeForLogging(username);
        String sanitizedResource = sanitizeForLogging(resource);
        
        log.error("[{}] Database error during {} check - User: {}, Resource: {}, Error: {}", 
                CATEGORY_DATABASE_ERROR, operation, sanitizedUsername, sanitizedResource, 
                error.getMessage(), error);
        
        // Additional structured logging for monitoring systems
        log.info("AUDIT_EVENT category={} operation={} user={} resource={} success=false error_type={} timestamp={}", 
                CATEGORY_DATABASE_ERROR, operation, sanitizedUsername, sanitizedResource, 
                error.getClass().getSimpleName(), getCurrentTimestamp());
    }
    
    /**
     * Log unauthorized access attempts.
     * 
     * @param username the username attempting access (sanitized)
     * @param operation the operation being attempted
     * @param resource the resource being accessed
     * @param reason the reason for denial
     */
    public void logUnauthorizedAccess(String username, String operation, String resource, String reason) {
        String sanitizedUsername = sanitizeForLogging(username);
        String sanitizedResource = sanitizeForLogging(resource);
        String sanitizedReason = sanitizeForLogging(reason);
        
        log.warn("[{}] Unauthorized access attempt - User: {}, Operation: {}, Resource: {}, Reason: {}", 
                CATEGORY_SECURITY_EVENT, sanitizedUsername, operation, sanitizedResource, sanitizedReason);
        
        // Additional structured logging for security monitoring
        log.info("AUDIT_EVENT category={} operation={} user={} resource={} success=false reason={} timestamp={}", 
                CATEGORY_SECURITY_EVENT, operation, sanitizedUsername, sanitizedResource, 
                sanitizedReason, getCurrentTimestamp());
    }
    
    /**
     * Log successful permission or role checks.
     * 
     * @param username the username (sanitized)
     * @param operation the operation performed
     * @param resource the resource accessed
     * @param fromCache whether result came from cache
     */
    public void logSuccessfulAccess(String username, String operation, String resource, boolean fromCache) {
        String sanitizedUsername = sanitizeForLogging(username);
        String sanitizedResource = sanitizeForLogging(resource);
        
        log.debug("[{}] Successful {} check - User: {}, Resource: {}, FromCache: {}", 
                CATEGORY_SECURITY_EVENT, operation, sanitizedUsername, sanitizedResource, fromCache);
        
        // Structured logging for monitoring (info level for successful access tracking)
        log.info("AUDIT_EVENT category={} operation={} user={} resource={} success=true from_cache={} timestamp={}", 
                CATEGORY_SECURITY_EVENT, operation, sanitizedUsername, sanitizedResource, 
                fromCache, getCurrentTimestamp());
    }
    
    /**
     * Log legitimate access denials (user doesn't have permission/role).
     * 
     * @param username the username (sanitized)
     * @param operation the operation attempted
     * @param resource the resource requested
     */
    public void logAccessDenied(String username, String operation, String resource) {
        String sanitizedUsername = sanitizeForLogging(username);
        String sanitizedResource = sanitizeForLogging(resource);
        
        log.info("[{}] Access denied - User: {}, Operation: {}, Resource: {}", 
                CATEGORY_ACCESS_DENIED, sanitizedUsername, operation, sanitizedResource);
        
        // Structured logging for access pattern analysis
        log.info("AUDIT_EVENT category={} operation={} user={} resource={} success=false reason=insufficient_privileges timestamp={}", 
                CATEGORY_ACCESS_DENIED, operation, sanitizedUsername, sanitizedResource, getCurrentTimestamp());
    }
    
    /**
     * Log system errors that are not database-related.
     * 
     * @param username the username (sanitized)
     * @param operation the operation being performed
     * @param error the system error that occurred
     */
    public void logSystemError(String username, String operation, Throwable error) {
        String sanitizedUsername = sanitizeForLogging(username);
        
        log.error("[{}] System error during {} - User: {}, Error: {}", 
                CATEGORY_SYSTEM_ERROR, operation, sanitizedUsername, error.getMessage(), error);
        
        // Structured logging for system monitoring
        log.info("AUDIT_EVENT category={} operation={} user={} success=false error_type={} timestamp={}", 
                CATEGORY_SYSTEM_ERROR, operation, sanitizedUsername, 
                error.getClass().getSimpleName(), getCurrentTimestamp());
    }
    
    /**
     * Log cache operations for performance monitoring.
     * 
     * @param username the username (sanitized)
     * @param operation the cache operation (hit/miss/invalidate)
     * @param resource the resource being cached
     * @param details additional details about the operation
     */
    public void logCacheOperation(String username, String operation, String resource, String details) {
        String sanitizedUsername = sanitizeForLogging(username);
        String sanitizedResource = sanitizeForLogging(resource);
        String sanitizedDetails = sanitizeForLogging(details);
        
        log.debug("CACHE_EVENT operation={} user={} resource={} details={} timestamp={}", 
                operation, sanitizedUsername, sanitizedResource, sanitizedDetails, getCurrentTimestamp());
    }
    
    /**
     * Log authentication context issues.
     * 
     * @param operation the operation being attempted
     * @param issue the authentication issue encountered
     */
    public void logAuthenticationIssue(String operation, String issue) {
        String sanitizedIssue = sanitizeForLogging(issue);
        
        log.warn("[{}] Authentication issue during {} - Issue: {}", 
                CATEGORY_SECURITY_EVENT, operation, sanitizedIssue);
        
        // Structured logging for authentication monitoring
        log.info("AUDIT_EVENT category={} operation={} success=false reason=authentication_issue issue={} timestamp={}", 
                CATEGORY_SECURITY_EVENT, operation, sanitizedIssue, getCurrentTimestamp());
    }
    
    /**
     * Log configuration validation results.
     * 
     * @param configParameter the configuration parameter being validated
     * @param isValid whether the configuration is valid
     * @param errorMessage error message if invalid (null if valid)
     */
    public void logConfigurationValidation(String configParameter, boolean isValid, String errorMessage) {
        String sanitizedParameter = sanitizeForLogging(configParameter);
        String sanitizedError = sanitizeForLogging(errorMessage);
        
        if (isValid) {
            log.info("Configuration validation successful - Parameter: {}", sanitizedParameter);
        } else {
            log.error("Configuration validation failed - Parameter: {}, Error: {}", 
                    sanitizedParameter, sanitizedError);
        }
        
        // Structured logging for configuration monitoring
        log.info("CONFIG_EVENT parameter={} valid={} error={} timestamp={}", 
                sanitizedParameter, isValid, sanitizedError, getCurrentTimestamp());
    }
    
    /**
     * Get security audit statistics for monitoring.
     * 
     * @return map containing audit statistics
     */
    public Map<String, Object> getAuditStatistics() {
        // This would typically be implemented with actual counters
        // For now, return basic information
        return Map.of(
                "audit_logger_active", true,
                "log_categories", new String[]{
                    CATEGORY_DATABASE_ERROR, 
                    CATEGORY_SECURITY_EVENT, 
                    CATEGORY_ACCESS_DENIED, 
                    CATEGORY_SYSTEM_ERROR
                },
                "timestamp", getCurrentTimestamp()
        );
    }
    
    /**
     * Sanitize input for logging to prevent log injection attacks.
     * Removes or replaces potentially dangerous characters.
     * 
     * @param input the input to sanitize
     * @return sanitized string safe for logging
     */
    private String sanitizeForLogging(String input) {
        if (input == null) {
            return "null";
        }
        
        // Remove or replace characters that could be used for log injection
        return input.replaceAll("[\r\n\t]", "_")  // Replace line breaks and tabs
                   .replaceAll("[\\p{Cntrl}]", "")  // Remove control characters
                   .trim();
    }
    
    /**
     * Get current timestamp in consistent format.
     * 
     * @return formatted timestamp string
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMAT);
    }
}