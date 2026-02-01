package com.platform.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Runtime Configuration Updater
 * 
 * Handles runtime updates to configuration objects using reflection
 * and property binding techniques
 * 
 * @author Platform Team
 * @version 1.0
 */
@Component
public class RuntimeConfigurationUpdater {
    
    private static final Logger logger = LoggerFactory.getLogger(RuntimeConfigurationUpdater.class);
    
    /**
     * Apply configuration updates to an existing configuration object
     * 
     * @param configuration The configuration object to update
     * @param updates Map of property names to new values
     * @param <T> Configuration type
     * @return Updated configuration object
     * @throws ConfigurationException if update fails
     */
    @SuppressWarnings("unchecked")
    public <T> T applyUpdates(T configuration, Map<String, Object> updates) {
        logger.info("Applying {} configuration updates to {}", 
                   updates.size(), configuration.getClass().getSimpleName());
        
        try {
            // Create a copy of the configuration object
            T updatedConfig = (T) copyConfiguration(configuration);
            
            // Apply each update
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                String propertyName = entry.getKey();
                Object newValue = entry.getValue();
                
                applyPropertyUpdate(updatedConfig, propertyName, newValue);
            }
            
            logger.info("Successfully applied configuration updates");
            return updatedConfig;
            
        } catch (Exception e) {
            logger.error("Failed to apply configuration updates", e);
            throw new ConfigurationException("Failed to apply configuration updates: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract current configuration values as a map
     * 
     * @param configuration Configuration object
     * @param <T> Configuration type
     * @return Map of property names to current values
     */
    public <T> Map<String, Object> extractValues(T configuration) {
        Map<String, Object> values = new HashMap<>();
        
        try {
            Class<?> configClass = configuration.getClass();
            Field[] fields = configClass.getDeclaredFields();
            
            for (Field field : fields) {
                field.setAccessible(true);
                String propertyName = field.getName();
                Object value = field.get(configuration);
                values.put(propertyName, value);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to extract configuration values", e);
        }
        
        return values;
    }
    
    /**
     * Check if a configuration class supports runtime updates
     * 
     * @param configClass Configuration class
     * @return true if runtime updates are supported
     */
    public boolean supportsRuntimeUpdates(Class<?> configClass) {
        // Check if the class has the @RuntimeUpdatable annotation or is in allowed list
        return configClass == ApiConfig.class || 
               configClass == MonitoringConfig.class ||
               configClass == CacheConfig.class ||
               configClass.isAnnotationPresent(RuntimeUpdatable.class);
    }
    
    private Object copyConfiguration(Object original) throws Exception {
        // Simple copy using reflection - in production, consider using a proper cloning library
        Class<?> configClass = original.getClass();
        Object copy = configClass.getDeclaredConstructor().newInstance();
        
        Field[] fields = configClass.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            Object value = field.get(original);
            field.set(copy, value);
        }
        
        return copy;
    }
    
    private void applyPropertyUpdate(Object configuration, String propertyName, Object newValue) 
            throws Exception {
        
        Class<?> configClass = configuration.getClass();
        
        // Try to find and use setter method first
        String setterName = "set" + capitalize(propertyName);
        Method[] methods = configClass.getMethods();
        
        for (Method method : methods) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                Class<?> parameterType = method.getParameterTypes()[0];
                Object convertedValue = convertValue(newValue, parameterType);
                method.invoke(configuration, convertedValue);
                
                logger.debug("Updated property {} to {} using setter method", propertyName, newValue);
                return;
            }
        }
        
        // Fallback to direct field access
        try {
            Field field = configClass.getDeclaredField(propertyName);
            field.setAccessible(true);
            Object convertedValue = convertValue(newValue, field.getType());
            field.set(configuration, convertedValue);
            
            logger.debug("Updated property {} to {} using direct field access", propertyName, newValue);
            
        } catch (NoSuchFieldException e) {
            throw new ConfigurationException("Property not found: " + propertyName);
        }
    }
    
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        
        // Handle common type conversions
        if (targetType == String.class) {
            return value.toString();
        } else if (targetType == int.class || targetType == Integer.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else {
                return Integer.parseInt(value.toString());
            }
        } else if (targetType == long.class || targetType == Long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            } else {
                return Long.parseLong(value.toString());
            }
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean) {
                return value;
            } else {
                return Boolean.parseBoolean(value.toString());
            }
        }
        
        throw new ConfigurationException("Cannot convert value " + value + " to type " + targetType);
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}