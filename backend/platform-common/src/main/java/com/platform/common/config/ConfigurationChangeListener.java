package com.platform.common.config;

import java.util.Map;

/**
 * Configuration Change Listener Interface
 * 
 * Allows components to be notified when configuration changes occur
 * 
 * @author Platform Team
 * @version 1.0
 */
public interface ConfigurationChangeListener {
    
    /**
     * Called when configuration changes
     * 
     * @param configClass The configuration class that changed
     * @param oldValues Previous configuration values
     * @param newValues New configuration values
     */
    void onConfigurationChanged(Class<?> configClass, Map<String, Object> oldValues, Map<String, Object> newValues);
    
    /**
     * Called when configuration reload occurs
     */
    void onConfigurationReloaded();
    
    /**
     * Get the configuration classes this listener is interested in
     * 
     * @return Array of configuration classes to monitor
     */
    Class<?>[] getInterestedConfigurationClasses();
}