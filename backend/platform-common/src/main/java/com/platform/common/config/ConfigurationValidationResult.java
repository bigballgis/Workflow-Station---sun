package com.platform.common.config;

import java.util.List;

/**
 * Configuration Validation Result
 * 
 * Contains the result of configuration validation including
 * errors, warnings, and overall validation status
 * 
 * @author Platform Team
 * @version 1.0
 */
public class ConfigurationValidationResult {
    
    private final boolean valid;
    private final List<ConfigurationValidationError> errors;
    private final List<ConfigurationValidationWarning> warnings;
    
    public ConfigurationValidationResult(boolean valid, 
                                       List<ConfigurationValidationError> errors,
                                       List<ConfigurationValidationWarning> warnings) {
        this.valid = valid;
        this.errors = errors;
        this.warnings = warnings;
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public List<ConfigurationValidationError> getErrors() {
        return errors;
    }
    
    public List<ConfigurationValidationWarning> getWarnings() {
        return warnings;
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    public int getErrorCount() {
        return errors.size();
    }
    
    public int getWarningCount() {
        return warnings.size();
    }
}