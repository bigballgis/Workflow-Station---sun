package com.platform.common.config;

/**
 * Configuration Validation Error
 * 
 * Represents a critical configuration validation error that
 * prevents the application from starting or functioning correctly
 * 
 * @author Platform Team
 * @version 1.0
 */
public class ConfigurationValidationError {
    
    private final String propertyPath;
    private final String message;
    private final Object invalidValue;
    
    public ConfigurationValidationError(String propertyPath, String message, Object invalidValue) {
        this.propertyPath = propertyPath;
        this.message = message;
        this.invalidValue = invalidValue;
    }
    
    public String getPropertyPath() {
        return propertyPath;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Object getInvalidValue() {
        return invalidValue;
    }
    
    @Override
    public String toString() {
        return String.format("ConfigurationValidationError{propertyPath='%s', message='%s', invalidValue=%s}", 
                           propertyPath, message, invalidValue);
    }
}