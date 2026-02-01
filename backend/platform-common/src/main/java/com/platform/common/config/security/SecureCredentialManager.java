package com.platform.common.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secure Credential Manager
 * 
 * Manages secure storage and retrieval of credentials, API keys, and other
 * sensitive configuration data with encryption and access auditing.
 * 
 * Provides secure credential storage, automatic encryption of sensitive data,
 * and comprehensive audit logging of credential access.
 * 
 * @author Platform Team
 * @version 1.0
 */
@Service
public class SecureCredentialManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SecureCredentialManager.class);
    
    private final Environment environment;
    private final ConfigurationEncryptionService encryptionService;
    private final ConfigurationAuditLogger auditLogger;
    
    // Cache for decrypted credentials to avoid repeated decryption
    private final Map<String, String> credentialCache = new ConcurrentHashMap<>();
    
    @Autowired
    public SecureCredentialManager(Environment environment,
                                 ConfigurationEncryptionService encryptionService,
                                 ConfigurationAuditLogger auditLogger) {
        this.environment = environment;
        this.encryptionService = encryptionService;
        this.auditLogger = auditLogger;
        
        logger.info("Secure credential manager initialized");
    }
    
    /**
     * Get credential value with automatic decryption
     * 
     * @param key Credential key
     * @return Optional containing the decrypted credential value
     */
    public Optional<String> getCredential(String key) {
        return getCredential(key, null);
    }
    
    /**
     * Get credential value with default and automatic decryption
     * 
     * @param key Credential key
     * @param defaultValue Default value if credential not found
     * @return Decrypted credential value or default
     */
    public String getCredentialWithDefault(String key, String defaultValue) {
        Optional<String> credential = getCredential(key, (Map<String, String>) null);
        return credential.orElse(defaultValue);
    }
    
    /**
     * Get credential value with context and automatic decryption
     * 
     * @param key Credential key
     * @param requestContext Additional context for audit logging
     * @return Optional containing the decrypted credential value
     */
    public Optional<String> getCredential(String key, Map<String, String> requestContext) {
        return getCredentialWithDefault(key, null, requestContext);
    }
    
    /**
     * Get credential value with default, context, and automatic decryption
     * 
     * @param key Credential key
     * @param defaultValue Default value if credential not found
     * @param requestContext Additional context for audit logging
     * @return Optional containing the decrypted credential value
     */
    public Optional<String> getCredentialWithDefault(String key, String defaultValue, Map<String, String> requestContext) {
        try {
            // Check cache first
            String cachedValue = credentialCache.get(key);
            if (cachedValue != null) {
                logCredentialAccess(key, "cache", requestContext);
                return Optional.of(cachedValue);
            }
            
            // Get from environment
            String rawValue = environment.getProperty(key, defaultValue);
            if (rawValue == null) {
                logCredentialAccess(key, "not_found", requestContext);
                return Optional.empty();
            }
            
            // Determine source
            String source = determineCredentialSource(key);
            
            // Decrypt if encrypted
            String decryptedValue = encryptionService.decryptValue(rawValue);
            
            // Cache the decrypted value
            credentialCache.put(key, decryptedValue);
            
            // Log access
            logCredentialAccess(key, source, requestContext);
            
            return Optional.of(decryptedValue);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve credential: {}", key, e);
            
            Map<String, String> errorContext = new HashMap<>();
            if (requestContext != null) {
                errorContext.putAll(requestContext);
            }
            errorContext.put("error", e.getMessage());
            
            auditLogger.logSecurityEvent("CREDENTIAL_ACCESS_FAILURE", 
                                        "Failed to retrieve credential: " + key, errorContext);
            
            return Optional.empty();
        }
    }
    
    /**
     * Store encrypted credential
     * 
     * @param key Credential key
     * @param value Credential value (will be encrypted)
     * @param storeContext Additional context for audit logging
     */
    public void storeCredential(String key, String value, Map<String, String> storeContext) {
        try {
            // Encrypt the value
            String encryptedValue = encryptionService.encryptValue(value);
            
            // Store in cache
            credentialCache.put(key, value);
            
            // Log the storage operation
            Map<String, String> context = new HashMap<>();
            if (storeContext != null) {
                context.putAll(storeContext);
            }
            context.put("operation", "store");
            
            auditLogger.logConfigurationChange(key, null, encryptedValue, "runtime", context);
            
            logger.debug("Credential stored successfully: {}", key);
            
        } catch (Exception e) {
            logger.error("Failed to store credential: {}", key, e);
            
            Map<String, String> errorContext = new HashMap<>();
            if (storeContext != null) {
                errorContext.putAll(storeContext);
            }
            errorContext.put("error", e.getMessage());
            
            auditLogger.logSecurityEvent("CREDENTIAL_STORE_FAILURE", 
                                        "Failed to store credential: " + key, errorContext);
        }
    }
    
    /**
     * Remove credential from cache
     * 
     * @param key Credential key
     */
    public void removeCredential(String key) {
        credentialCache.remove(key);
        
        Map<String, String> context = new HashMap<>();
        context.put("operation", "remove");
        
        auditLogger.logConfigurationChange(key, "cached_value", null, "cache", context);
        
        logger.debug("Credential removed from cache: {}", key);
    }
    
    /**
     * Clear all cached credentials
     */
    public void clearCredentialCache() {
        int cacheSize = credentialCache.size();
        credentialCache.clear();
        
        Map<String, String> context = new HashMap<>();
        context.put("operation", "clear_cache");
        context.put("cleared_count", String.valueOf(cacheSize));
        
        auditLogger.logSecurityEvent("CREDENTIAL_CACHE_CLEARED", 
                                    "All cached credentials cleared", context);
        
        logger.info("Credential cache cleared: {} credentials removed", cacheSize);
    }
    
    /**
     * Check if credential exists
     * 
     * @param key Credential key
     * @return true if credential exists
     */
    public boolean hasCredential(String key) {
        return environment.containsProperty(key) || credentialCache.containsKey(key);
    }
    
    /**
     * Get all credential keys (for management purposes)
     * 
     * @return Set of credential keys
     */
    public java.util.Set<String> getCredentialKeys() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        
        // Add keys from cached credentials
        keys.addAll(credentialCache.keySet());
        
        // Add some common sensitive property names that might exist
        String[] commonSensitiveKeys = {
            "app.database.password",
            "app.security.jwt-secret-key", 
            "app.cache.redis-password",
            "app.messaging.email-password",
            "platform.encryption.secret-key"
        };
        
        for (String key : commonSensitiveKeys) {
            if (environment.containsProperty(key)) {
                keys.add(key);
            }
        }
        
        return keys;
    }
    
    /**
     * Validate all stored credentials
     * 
     * @return Map of validation results
     */
    public Map<String, Boolean> validateCredentials() {
        Map<String, Boolean> validationResults = new HashMap<>();
        
        for (String key : getCredentialKeys()) {
            try {
                Optional<String> credential = getCredential(key);
                validationResults.put(key, credential.isPresent() && !credential.get().isEmpty());
            } catch (Exception e) {
                validationResults.put(key, false);
                logger.warn("Credential validation failed for key: {}", key, e);
            }
        }
        
        Map<String, String> context = new HashMap<>();
        context.put("total_credentials", String.valueOf(validationResults.size()));
        context.put("valid_credentials", String.valueOf(validationResults.values().stream().mapToInt(v -> v ? 1 : 0).sum()));
        
        auditLogger.logSecurityEvent("CREDENTIAL_VALIDATION", 
                                    "Credential validation completed", context);
        
        return validationResults;
    }
    
    /**
     * Determine the source of a credential
     * 
     * @param key Credential key
     * @return Source description
     */
    private String determineCredentialSource(String key) {
        if (System.getProperty(key) != null) {
            return "system_property";
        } else if (System.getenv(key.toUpperCase().replace('.', '_')) != null) {
            return "environment_variable";
        } else {
            return "configuration_file";
        }
    }
    
    /**
     * Log credential access for audit purposes
     * 
     * @param key Credential key
     * @param source Credential source
     * @param requestContext Additional context
     */
    private void logCredentialAccess(String key, String source, Map<String, String> requestContext) {
        Map<String, String> context = new HashMap<>();
        if (requestContext != null) {
            context.putAll(requestContext);
        }
        context.put("credential_type", "sensitive");
        
        auditLogger.logConfigurationAccess(key, "***CREDENTIAL***", source, context);
    }
}