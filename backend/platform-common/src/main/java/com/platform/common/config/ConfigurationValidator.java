package com.platform.common.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Configuration Validator
 * 
 * Provides comprehensive validation for configuration objects with
 * detailed error reporting and validation rules
 * 
 * @author Platform Team
 * @version 1.0
 */
@Component
public class ConfigurationValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationValidator.class);
    
    private final Validator validator;
    
    public ConfigurationValidator(Validator validator) {
        this.validator = validator;
    }
    
    /**
     * Validate configuration object and return detailed results
     * 
     * @param configuration Configuration object to validate
     * @param <T> Configuration type
     * @return Validation result with errors and warnings
     */
    public <T> ConfigurationValidationResult validate(T configuration) {
        logger.debug("Validating configuration: {}", configuration.getClass().getSimpleName());
        
        Set<ConstraintViolation<T>> violations = validator.validate(configuration);
        
        List<ConfigurationValidationError> errors = new ArrayList<>();
        List<ConfigurationValidationWarning> warnings = new ArrayList<>();
        
        for (ConstraintViolation<T> violation : violations) {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            Object invalidValue = violation.getInvalidValue();
            
            // Determine if this is an error or warning based on severity
            if (isCriticalViolation(violation)) {
                errors.add(new ConfigurationValidationError(propertyPath, message, invalidValue));
            } else {
                warnings.add(new ConfigurationValidationWarning(propertyPath, message, invalidValue));
            }
        }
        
        // Add custom validation rules
        addCustomValidationRules(configuration, errors, warnings);
        
        boolean isValid = errors.isEmpty();
        
        if (isValid) {
            logger.debug("Configuration validation passed for {}", configuration.getClass().getSimpleName());
        } else {
            logger.warn("Configuration validation failed for {} with {} errors and {} warnings", 
                       configuration.getClass().getSimpleName(), errors.size(), warnings.size());
        }
        
        return new ConfigurationValidationResult(isValid, errors, warnings);
    }
    
    /**
     * Validate configuration at startup with clear error messages
     * 
     * @param configuration Configuration to validate
     * @param <T> Configuration type
     * @throws ConfigurationException if validation fails
     */
    public <T> void validateAtStartup(T configuration) {
        ConfigurationValidationResult result = validate(configuration);
        
        if (!result.isValid()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Configuration validation failed at startup for ")
              .append(configuration.getClass().getSimpleName())
              .append(":");
            
            for (ConfigurationValidationError error : result.getErrors()) {
                sb.append("\n  ERROR: ").append(error.getPropertyPath())
                  .append(" - ").append(error.getMessage());
                if (error.getInvalidValue() != null) {
                    sb.append(" (current value: ").append(error.getInvalidValue()).append(")");
                }
            }
            
            for (ConfigurationValidationWarning warning : result.getWarnings()) {
                sb.append("\n  WARNING: ").append(warning.getPropertyPath())
                  .append(" - ").append(warning.getMessage());
            }
            
            throw new ConfigurationException(sb.toString());
        }
        
        // Log warnings even if validation passes
        if (!result.getWarnings().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Configuration warnings for ")
              .append(configuration.getClass().getSimpleName())
              .append(":");
            
            for (ConfigurationValidationWarning warning : result.getWarnings()) {
                sb.append("\n  WARNING: ").append(warning.getPropertyPath())
                  .append(" - ").append(warning.getMessage());
            }
            
            logger.warn(sb.toString());
        }
    }
    
    private <T> boolean isCriticalViolation(ConstraintViolation<T> violation) {
        // Consider violations critical if they involve required fields or security settings
        String propertyPath = violation.getPropertyPath().toString();
        
        return propertyPath.contains("password") ||
               propertyPath.contains("security") ||
               propertyPath.contains("url") ||
               propertyPath.contains("username") ||
               violation.getMessage().contains("required") ||
               violation.getMessage().contains("cannot be null") ||
               violation.getMessage().contains("cannot be blank");
    }
    
    private <T> void addCustomValidationRules(T configuration, 
                                            List<ConfigurationValidationError> errors,
                                            List<ConfigurationValidationWarning> warnings) {
        
        // Add custom validation logic based on configuration type
        if (configuration instanceof DatabaseConfig) {
            validateDatabaseConfig((DatabaseConfig) configuration, errors, warnings);
        } else if (configuration instanceof SecurityConfig) {
            validateSecurityConfig((SecurityConfig) configuration, errors, warnings);
        } else if (configuration instanceof ApiConfig) {
            validateApiConfig((ApiConfig) configuration, errors, warnings);
        }
    }
    
    private void validateDatabaseConfig(DatabaseConfig config, 
                                      List<ConfigurationValidationError> errors,
                                      List<ConfigurationValidationWarning> warnings) {
        
        // Validate database URL format
        if (config.getUrl() != null && !config.getUrl().startsWith("jdbc:")) {
            errors.add(new ConfigurationValidationError("url", 
                      "Database URL must start with 'jdbc:'", config.getUrl()));
        }
        
        // Validate connection pool settings
        if (config.getMaxConnections() < config.getMinIdleConnections()) {
            errors.add(new ConfigurationValidationError("maxConnections", 
                      "Maximum connections must be greater than minimum idle connections", 
                      config.getMaxConnections()));
        }
        
        // Warning for potentially inefficient settings
        if (config.getMaxConnections() > 50) {
            warnings.add(new ConfigurationValidationWarning("maxConnections", 
                        "High connection pool size may impact performance", 
                        config.getMaxConnections()));
        }
    }
    
    private void validateSecurityConfig(SecurityConfig config, 
                                      List<ConfigurationValidationError> errors,
                                      List<ConfigurationValidationWarning> warnings) {
        
        // Validate password policy consistency
        if (config.getPasswordMaxLength() < config.getPasswordMinLength()) {
            errors.add(new ConfigurationValidationError("passwordMaxLength", 
                      "Maximum password length must be greater than minimum length", 
                      config.getPasswordMaxLength()));
        }
        
        // Warning for weak password policies
        if (config.getPasswordMinLength() < 8) {
            warnings.add(new ConfigurationValidationWarning("passwordMinLength", 
                        "Password minimum length below 8 characters is not recommended", 
                        config.getPasswordMinLength()));
        }
        
        // Validate JWT secret key
        if (config.getJwtSecretKey() != null && 
            config.getJwtSecretKey().equals("default-jwt-secret-key-change-in-production")) {
            errors.add(new ConfigurationValidationError("jwtSecretKey", 
                      "Default JWT secret key must be changed in production", 
                      "default-jwt-secret-key"));
        }
    }
    
    private void validateApiConfig(ApiConfig config, 
                                 List<ConfigurationValidationError> errors,
                                 List<ConfigurationValidationWarning> warnings) {
        
        // Validate timeout settings
        if (config.getConnectionTimeoutMs() > config.getRequestTimeoutMs()) {
            warnings.add(new ConfigurationValidationWarning("connectionTimeoutMs", 
                        "Connection timeout should typically be less than request timeout", 
                        config.getConnectionTimeoutMs()));
        }
        
        // Validate service URLs
        validateServiceUrl(config.getWorkflowEngineUrl(), "workflowEngineUrl", errors);
        validateServiceUrl(config.getUserServiceUrl(), "userServiceUrl", errors);
        validateServiceUrl(config.getNotificationServiceUrl(), "notificationServiceUrl", errors);
    }
    
    private void validateServiceUrl(String url, String propertyName, 
                                  List<ConfigurationValidationError> errors) {
        if (url != null && !url.startsWith("http://") && !url.startsWith("https://")) {
            errors.add(new ConfigurationValidationError(propertyName, 
                      "Service URL must start with 'http://' or 'https://'", url));
        }
    }
}