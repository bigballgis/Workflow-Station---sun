package com.platform.common.config;

/**
 * Configuration Validation Warning
 * 
 * Represents a non-critical configuration issue that should be
 * reviewed but doesn't prevent the application from functioning
 * 
 * @author Platform Team
 * @version 1.0
 */
public class ConfigurationValidationWarning {
    
    private final String propertyPath;
    private final String message;
    private final Object currentValue;
    
    public ConfigurationValidationWarning(String propertyPath, String message, Object currentValue) {
        this.propertyPath = propertyPath;
        this.message = message;
        this.currentValue = currentValue;
    }
    
    public String getPropertyPath() {
        return propertyPath;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Object getCurrentValue() {
        return currentValue;
    }
    
    @Override
    public String toString() {
        return String.format("ConfigurationValidationWarning{propertyPath='%s', message='%s', currentValue=%s}", 
                           propertyPath, message, currentValue);
    }
}