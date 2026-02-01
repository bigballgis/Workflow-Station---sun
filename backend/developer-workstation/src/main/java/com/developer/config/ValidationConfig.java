package com.developer.config;

import com.developer.validation.ValidationRuleEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for the validation framework.
 * This class sets up validation components and provides configuration options.
 */
@Configuration
@Slf4j
public class ValidationConfig {
    
    /**
     * Creates the validation rule engine bean
     */
    @Bean
    public ValidationRuleEngine validationRuleEngine() {
        log.info("Initializing ValidationRuleEngine with configurable rule groups");
        return new ValidationRuleEngine();
    }
    
    /**
     * Configuration properties for validation settings
     */
    @ConfigurationProperties(prefix = "app.validation")
    public static class ValidationProperties {
        
        /**
         * Whether input validation is enabled globally
         */
        private boolean enabled = true;
        
        /**
         * Whether to log validation failures for security monitoring
         */
        private boolean logSecurityViolations = true;
        
        /**
         * Whether to sanitize input automatically
         */
        private boolean autoSanitize = false;
        
        /**
         * Maximum input length for validation
         */
        private int maxInputLength = 10000;
        
        /**
         * Whether to enable strict validation mode
         */
        private boolean strictMode = true;
        
        // Getters and setters
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public boolean isLogSecurityViolations() {
            return logSecurityViolations;
        }
        
        public void setLogSecurityViolations(boolean logSecurityViolations) {
            this.logSecurityViolations = logSecurityViolations;
        }
        
        public boolean isAutoSanitize() {
            return autoSanitize;
        }
        
        public void setAutoSanitize(boolean autoSanitize) {
            this.autoSanitize = autoSanitize;
        }
        
        public int getMaxInputLength() {
            return maxInputLength;
        }
        
        public void setMaxInputLength(int maxInputLength) {
            this.maxInputLength = maxInputLength;
        }
        
        public boolean isStrictMode() {
            return strictMode;
        }
        
        public void setStrictMode(boolean strictMode) {
            this.strictMode = strictMode;
        }
    }
}