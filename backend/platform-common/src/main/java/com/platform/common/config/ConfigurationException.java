package com.platform.common.config;

/**
 * Configuration Exception
 * 
 * Thrown when configuration loading, validation, or update operations fail
 * 
 * @author Platform Team
 * @version 1.0
 */
public class ConfigurationException extends RuntimeException {
    
    private final String configurationKey;
    private final String source;
    
    public ConfigurationException(String message) {
        super(message);
        this.configurationKey = null;
        this.source = null;
    }
    
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
        this.configurationKey = null;
        this.source = null;
    }
    
    public ConfigurationException(String message, String configurationKey, String source) {
        super(message);
        this.configurationKey = configurationKey;
        this.source = source;
    }
    
    public ConfigurationException(String message, String configurationKey, String source, Throwable cause) {
        super(message, cause);
        this.configurationKey = configurationKey;
        this.source = source;
    }
    
    public String getConfigurationKey() {
        return configurationKey;
    }
    
    public String getSource() {
        return source;
    }
    
    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        if (configurationKey != null) {
            sb.append(" [key: ").append(configurationKey).append("]");
        }
        if (source != null) {
            sb.append(" [source: ").append(source).append("]");
        }
        return sb.toString();
    }
}