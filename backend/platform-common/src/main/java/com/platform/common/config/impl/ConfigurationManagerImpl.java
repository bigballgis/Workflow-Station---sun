package com.platform.common.config.impl;

import com.platform.common.config.*;
import com.platform.common.config.security.ConfigurationAuditLogger;
import com.platform.common.config.security.ConfigurationEncryptionService;
import com.platform.common.config.security.SecureCredentialManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Configuration Manager Implementation
 * 
 * Provides centralized configuration management with support for:
 * - External configuration loading from files and environment variables
 * - Runtime configuration updates with validation
 * - Configuration validation with detailed error reporting
 * - Secure configuration handling
 * 
 * @author Platform Team
 * @version 1.0
 */
public class ConfigurationManagerImpl implements ConfigurationManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManagerImpl.class);
    
    private final Environment environment;
    private final Validator validator;
    private final ApplicationConfiguration applicationConfiguration;
    private final ConfigurationValidator configurationValidator;
    private final RuntimeConfigurationUpdater runtimeUpdater;
    private final ConfigurationEncryptionService encryptionService;
    private final ConfigurationAuditLogger auditLogger;
    private final SecureCredentialManager credentialManager;
    
    private final Map<Class<?>, Object> configurationCache = new ConcurrentHashMap<>();
    private final List<ConfigurationChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, String> configurationSources = new ConcurrentHashMap<>();
    
    @Autowired
    public ConfigurationManagerImpl(Environment environment, 
                                  Validator validator,
                                  @Qualifier("applicationConfiguration") ApplicationConfiguration applicationConfiguration,
                                  ConfigurationValidator configurationValidator,
                                  RuntimeConfigurationUpdater runtimeUpdater,
                                  ConfigurationEncryptionService encryptionService,
                                  ConfigurationAuditLogger auditLogger,
                                  SecureCredentialManager credentialManager) {
        this.environment = environment;
        this.validator = validator;
        this.applicationConfiguration = applicationConfiguration;
        this.configurationValidator = configurationValidator;
        this.runtimeUpdater = runtimeUpdater;
        this.encryptionService = encryptionService;
        this.auditLogger = auditLogger;
        this.credentialManager = credentialManager;
        
        // Initialize configuration cache
        initializeConfigurationCache();
        
        // Initialize configuration sources tracking
        initializeConfigurationSources();
        
        // Validate all configurations at startup
        validateAllConfigurations();
        
        logger.info("Configuration Manager initialized with {} configuration classes", 
                   configurationCache.size());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfiguration(Class<T> configClass) {
        T configuration = (T) configurationCache.get(configClass);
        if (configuration == null) {
            throw new ConfigurationException("Configuration not found for class: " + configClass.getName());
        }
        
        return configuration;
    }
    
    @Override
    public Optional<String> getConfigurationValue(String key) {
        // Use secure credential manager for sensitive keys
        if (encryptionService.isSensitiveKey(key)) {
            Map<String, String> context = new HashMap<>();
            context.put("access_method", "getConfigurationValue");
            context.put("thread", Thread.currentThread().getName());
            
            return credentialManager.getCredential(key, context);
        }
        
        String value = environment.getProperty(key);
        if (value != null) {
            // Track the source of this configuration value
            String source = determineConfigurationSource(key);
            configurationSources.put(key, source);
            
            // Log configuration access
            Map<String, String> context = new HashMap<>();
            context.put("access_method", "getConfigurationValue");
            context.put("thread", Thread.currentThread().getName());
            
            auditLogger.logConfigurationAccess(key, value, source, context);
        }
        return Optional.ofNullable(value);
    }
    
    @Override
    public String getConfigurationValue(String key, String defaultValue) {
        return getConfigurationValue(key).orElse(defaultValue);
    }
    
    @Override
    public void validateConfiguration() {
        logger.info("Validating all configurations...");
        validateAllConfigurations();
        logger.info("All configurations validated successfully");
    }
    
    @Override
    public void reloadConfiguration() {
        logger.info("Reloading configuration from external sources...");
        
        try {
            // Store old configurations for change notification
            Map<Class<?>, Object> oldConfigurations = new HashMap<>(configurationCache);
            
            // Clear current cache
            configurationCache.clear();
            
            // Clear credential cache for security
            credentialManager.clearCredentialCache();
            
            // Reload configurations
            initializeConfigurationCache();
            
            // Validate reloaded configurations
            validateAllConfigurations();
            
            // Log reload event
            Map<String, String> reloadContext = new HashMap<>();
            reloadContext.put("reload_method", "manual");
            reloadContext.put("thread", Thread.currentThread().getName());
            auditLogger.logConfigurationReload(reloadContext);
            
            // Notify listeners of changes
            notifyConfigurationReloaded();
            
            // Notify specific configuration changes
            for (Map.Entry<Class<?>, Object> entry : configurationCache.entrySet()) {
                Class<?> configClass = entry.getKey();
                Object newConfig = entry.getValue();
                Object oldConfig = oldConfigurations.get(configClass);
                
                if (oldConfig != null) {
                    Map<String, Object> oldValues = runtimeUpdater.extractValues(oldConfig);
                    Map<String, Object> newValues = runtimeUpdater.extractValues(newConfig);
                    
                    if (!oldValues.equals(newValues)) {
                        notifyConfigurationChanged(configClass, oldValues, newValues);
                    }
                }
            }
            
            logger.info("Configuration reloaded successfully");
            
        } catch (Exception e) {
            logger.error("Failed to reload configuration", e);
            
            Map<String, String> errorContext = new HashMap<>();
            errorContext.put("error", e.getMessage());
            errorContext.put("thread", Thread.currentThread().getName());
            auditLogger.logSecurityEvent("CONFIG_RELOAD_FAILURE", 
                                        "Configuration reload failed", errorContext);
            
            throw new ConfigurationException("Configuration reload failed", e);
        }
    }
    
    @Override
    public boolean supportsRuntimeUpdates(Class<?> configClass) {
        return runtimeUpdater.supportsRuntimeUpdates(configClass);
    }
    
    @Override
    public <T> void updateConfiguration(Class<T> configClass, Map<String, Object> updates) {
        if (!supportsRuntimeUpdates(configClass)) {
            throw new ConfigurationException("Runtime updates not supported for configuration class: " + configClass.getName());
        }
        
        logger.info("Updating configuration for class: {} with {} changes", configClass.getSimpleName(), updates.size());
        
        try {
            @SuppressWarnings("unchecked")
            T currentConfig = (T) configurationCache.get(configClass);
            if (currentConfig == null) {
                throw new ConfigurationException("Configuration not found for class: " + configClass.getName());
            }
            
            // Create a copy of current values for change notification
            Map<String, Object> oldValues = runtimeUpdater.extractValues(currentConfig);
            
            // Apply updates using the runtime updater
            T updatedConfig = runtimeUpdater.applyUpdates(currentConfig, updates);
            
            // Validate updated configuration
            configurationValidator.validateAtStartup(updatedConfig);
            
            // Update cache
            configurationCache.put(configClass, updatedConfig);
            
            // Log configuration changes with audit trail
            Map<String, Object> newValues = runtimeUpdater.extractValues(updatedConfig);
            for (Map.Entry<String, Object> update : updates.entrySet()) {
                String key = configClass.getSimpleName() + "." + update.getKey();
                Object oldValue = oldValues.get(update.getKey());
                Object newValue = update.getValue();
                
                Map<String, String> changeContext = new HashMap<>();
                changeContext.put("config_class", configClass.getSimpleName());
                changeContext.put("update_method", "runtime");
                changeContext.put("thread", Thread.currentThread().getName());
                
                auditLogger.logConfigurationChange(key, 
                                                 oldValue != null ? oldValue.toString() : null,
                                                 newValue != null ? newValue.toString() : null,
                                                 "runtime", changeContext);
            }
            
            // Notify listeners
            notifyConfigurationChanged(configClass, oldValues, newValues);
            
            logger.info("Configuration updated successfully for class: {}", configClass.getSimpleName());
            
        } catch (Exception e) {
            logger.error("Failed to update configuration for class: " + configClass.getSimpleName(), e);
            
            Map<String, String> errorContext = new HashMap<>();
            errorContext.put("config_class", configClass.getSimpleName());
            errorContext.put("error", e.getMessage());
            errorContext.put("thread", Thread.currentThread().getName());
            auditLogger.logSecurityEvent("CONFIG_UPDATE_FAILURE", 
                                        "Configuration update failed for class: " + configClass.getName(), errorContext);
            
            throw new ConfigurationException("Configuration update failed for class: " + configClass.getName(), e);
        }
    }
    
    @Override
    public Map<String, String> getConfigurationSources() {
        return new HashMap<>(configurationSources);
    }
    
    @Override
    public void addConfigurationChangeListener(ConfigurationChangeListener listener) {
        listeners.add(listener);
        logger.debug("Added configuration change listener: {}", listener.getClass().getSimpleName());
    }
    
    @Override
    public void removeConfigurationChangeListener(ConfigurationChangeListener listener) {
        listeners.remove(listener);
        logger.debug("Removed configuration change listener: {}", listener.getClass().getSimpleName());
    }
    
    /**
     * Get secure credential using the credential manager
     * 
     * @param key Credential key
     * @return Optional containing the decrypted credential value
     */
    public Optional<String> getSecureCredential(String key) {
        Map<String, String> context = new HashMap<>();
        context.put("access_method", "getSecureCredential");
        context.put("thread", Thread.currentThread().getName());
        
        return credentialManager.getCredential(key, context);
    }
    
    /**
     * Store secure credential with encryption
     * 
     * @param key Credential key
     * @param value Credential value (will be encrypted)
     */
    public void storeSecureCredential(String key, String value) {
        Map<String, String> context = new HashMap<>();
        context.put("store_method", "storeSecureCredential");
        context.put("thread", Thread.currentThread().getName());
        
        credentialManager.storeCredential(key, value, context);
    }
    
    /**
     * Validate all secure credentials
     * 
     * @return Map of validation results
     */
    public Map<String, Boolean> validateSecureCredentials() {
        return credentialManager.validateCredentials();
    }
    
    /**
     * Get configuration access metrics
     * 
     * @return Map containing access metrics
     */
    public Map<String, Object> getConfigurationMetrics() {
        return auditLogger.getAccessMetrics();
    }
    
    /**
     * Encrypt configuration value if sensitive
     * 
     * @param key Configuration key
     * @param value Configuration value
     * @return Encrypted value if key is sensitive, otherwise original value
     */
    public String encryptConfigurationValue(String key, String value) {
        return encryptionService.encryptIfSensitive(key, value);
    }
    
    /**
     * Check if configuration key is sensitive
     * 
     * @param key Configuration key
     * @return true if the key is considered sensitive
     */
    public boolean isSensitiveConfiguration(String key) {
        return encryptionService.isSensitiveKey(key);
    }
    
    private void initializeConfigurationCache() {
        // Cache the main application configuration and its nested configurations
        configurationCache.put(ApplicationConfiguration.class, applicationConfiguration);
        configurationCache.put(DatabaseConfig.class, applicationConfiguration.getDatabase());
        configurationCache.put(SecurityConfig.class, applicationConfiguration.getSecurity());
        configurationCache.put(ApiConfig.class, applicationConfiguration.getApi());
        configurationCache.put(MonitoringConfig.class, applicationConfiguration.getMonitoring());
        configurationCache.put(CacheConfig.class, applicationConfiguration.getCache());
        configurationCache.put(MessagingConfig.class, applicationConfiguration.getMessaging());
        configurationCache.put(WorkflowConfig.class, applicationConfiguration.getWorkflow());
    }
    
    private void validateAllConfigurations() {
        for (Map.Entry<Class<?>, Object> entry : configurationCache.entrySet()) {
            Class<?> configClass = entry.getKey();
            Object configuration = entry.getValue();
            
            try {
                configurationValidator.validateAtStartup(configuration);
                logger.debug("Configuration validation passed for {}", configClass.getSimpleName());
            } catch (ConfigurationException e) {
                logger.error("Configuration validation failed for {}: {}", configClass.getSimpleName(), e.getMessage());
                
                // Log validation failure for audit
                Map<String, String> validationContext = new HashMap<>();
                validationContext.put("config_class", configClass.getSimpleName());
                validationContext.put("validation_phase", "startup");
                
                auditLogger.logConfigurationValidationFailure(
                    configClass.getSimpleName(), 
                    "configuration_object", 
                    e.getMessage(), 
                    validationContext
                );
                
                throw e;
            }
        }
    }
    
    private void initializeConfigurationSources() {
        // Track common configuration sources
        configurationSources.put("app.database.url", "application.yml");
        configurationSources.put("app.security.password.min-length", "application.yml");
        configurationSources.put("app.api.workflow-engine-url", "application.yml");
        configurationSources.put("app.monitoring.log-level", "application.yml");
        configurationSources.put("app.cache.redis-host", "application.yml");
        configurationSources.put("app.messaging.kafka-bootstrap-servers", "application.yml");
        configurationSources.put("app.workflow.engine-url", "application.yml");
    }
    
    private String determineConfigurationSource(String key) {
        // Simple heuristic to determine configuration source
        if (System.getProperty(key) != null) {
            return "system-property";
        } else if (System.getenv(key.toUpperCase().replace('.', '_')) != null) {
            return "environment-variable";
        } else {
            return "application.yml";
        }
    }
    
    private void notifyConfigurationChanged(Class<?> configClass, Map<String, Object> oldValues, Map<String, Object> newValues) {
        for (ConfigurationChangeListener listener : listeners) {
            try {
                Class<?>[] interestedClasses = listener.getInterestedConfigurationClasses();
                if (interestedClasses == null || Arrays.asList(interestedClasses).contains(configClass)) {
                    listener.onConfigurationChanged(configClass, oldValues, newValues);
                }
            } catch (Exception e) {
                logger.error("Error notifying configuration change listener", e);
            }
        }
    }
    
    private void notifyConfigurationReloaded() {
        for (ConfigurationChangeListener listener : listeners) {
            try {
                listener.onConfigurationReloaded();
            } catch (Exception e) {
                logger.error("Error notifying configuration reload listener", e);
            }
        }
    }
}