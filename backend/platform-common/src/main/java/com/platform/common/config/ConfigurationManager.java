package com.platform.common.config;

import java.util.Map;
import java.util.Optional;

/**
 * Configuration Manager Interface
 * 
 * Provides centralized configuration management with support for:
 * - External configuration loading from files and environment variables
 * - Runtime configuration updates
 * - Configuration validation
 * - Secure configuration handling
 * 
 * @author Platform Team
 * @version 1.0
 */
public interface ConfigurationManager {
    
    /**
     * Get configuration object by class type
     * 
     * @param configClass The configuration class type
     * @param <T> Configuration type
     * @return Configuration instance
     * @throws ConfigurationException if configuration cannot be loaded or validated
     */
    <T> T getConfiguration(Class<T> configClass);
    
    /**
     * Get configuration value by key
     * 
     * @param key Configuration key
     * @return Optional configuration value
     */
    Optional<String> getConfigurationValue(String key);
    
    /**
     * Get configuration value with default
     * 
     * @param key Configuration key
     * @param defaultValue Default value if key not found
     * @return Configuration value or default
     */
    String getConfigurationValue(String key, String defaultValue);
    
    /**
     * Validate all loaded configurations
     * 
     * @throws ConfigurationException if any configuration is invalid
     */
    void validateConfiguration();
    
    /**
     * Reload configuration from external sources
     * 
     * @throws ConfigurationException if reload fails
     */
    void reloadConfiguration();
    
    /**
     * Check if configuration supports runtime updates
     * 
     * @param configClass Configuration class
     * @return true if runtime updates are supported
     */
    boolean supportsRuntimeUpdates(Class<?> configClass);
    
    /**
     * Update configuration at runtime
     * 
     * @param configClass Configuration class
     * @param updates Configuration updates
     * @param <T> Configuration type
     * @throws ConfigurationException if update fails or not supported
     */
    <T> void updateConfiguration(Class<T> configClass, Map<String, Object> updates);
    
    /**
     * Get all configuration keys with their sources
     * 
     * @return Map of configuration keys to their sources (file, env, etc.)
     */
    Map<String, String> getConfigurationSources();
    
    /**
     * Register configuration change listener
     * 
     * @param listener Configuration change listener
     */
    void addConfigurationChangeListener(ConfigurationChangeListener listener);
    
    /**
     * Remove configuration change listener
     * 
     * @param listener Configuration change listener
     */
    void removeConfigurationChangeListener(ConfigurationChangeListener listener);
}