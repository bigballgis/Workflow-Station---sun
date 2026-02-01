package com.platform.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Validation Configuration
 * 
 * Configures input validation components for security and data integrity.
 * This configuration is designed to be imported by modules that need
 * input validation capabilities.
 * 
 * **Validates: Requirements 1.1, 1.4, 4.2**
 * 
 * @author Platform Team
 * @version 1.0
 */
@Configuration
public class ValidationConfiguration {
    
    /**
     * Configure injection detector for security validation
     */
    @Bean
    @ConditionalOnMissingBean(name = "injectionDetector")
    public Object injectionDetector() {
        // This will be implemented by modules that need it
        // Using Object type to avoid circular dependencies
        return new Object() {
            public boolean detectInjectionAttempt(String input) {
                // Basic implementation - modules can override
                if (input == null) return false;
                String lowerInput = input.toLowerCase();
                return lowerInput.contains("script") || 
                       lowerInput.contains("select") || 
                       lowerInput.contains("union") ||
                       lowerInput.contains("drop") ||
                       lowerInput.contains("delete") ||
                       lowerInput.contains("insert") ||
                       lowerInput.contains("update") ||
                       lowerInput.contains("exec") ||
                       lowerInput.contains("javascript:");
            }
        };
    }
    
    /**
     * Configure sanitization engine for input cleaning
     */
    @Bean
    @ConditionalOnMissingBean(name = "sanitizationEngine")
    public Object sanitizationEngine() {
        // This will be implemented by modules that need it
        // Using Object type to avoid circular dependencies
        return new Object() {
            public String sanitize(String input) {
                // Basic implementation - modules can override
                if (input == null) return null;
                return input.replaceAll("<script[^>]*>.*?</script>", "")
                           .replaceAll("<[^>]+>", "")
                           .replaceAll("javascript:", "")
                           .replaceAll("vbscript:", "")
                           .replaceAll("onload", "")
                           .replaceAll("onerror", "")
                           .replaceAll("onclick", "")
                           .trim();
            }
        };
    }
}