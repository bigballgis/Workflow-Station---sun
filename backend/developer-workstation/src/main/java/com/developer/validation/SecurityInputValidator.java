package com.developer.validation;

import com.developer.dto.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Security-focused input validator that detects and prevents injection attacks.
 * This validator implements comprehensive security patterns to detect SQL injection,
 * XSS attacks, command injection, and other malicious input patterns.
 */
@Component
@Slf4j
public class SecurityInputValidator implements InputValidator<String> {
    
    private final List<ValidationRule> securityRules;
    private final SanitizationEngine sanitizationEngine;
    private final InjectionDetector injectionDetector;
    
    @Autowired
    public SecurityInputValidator(SanitizationEngine sanitizationEngine, InjectionDetector injectionDetector) {
        this.securityRules = initializeSecurityRules();
        this.sanitizationEngine = sanitizationEngine;
        this.injectionDetector = injectionDetector;
    }
    
    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = ValidationResult.builder().build();
        
        if (!StringUtils.hasText(input)) {
            return result; // Empty input is considered safe
        }
        
        // Apply all security rules
        for (ValidationRule rule : securityRules) {
            if (rule.isEnabled() && rule.matches(input)) {
                result.addError(
                    "SECURITY_" + rule.getName().toUpperCase(),
                    rule.getErrorMessage(),
                    "input"
                );
                
                // Log security violation for monitoring
                log.warn("Security validation failed for rule '{}': {}", 
                    rule.getName(), rule.getErrorMessage());
            }
        }
        
        return result;
    }
    
    @Override
    public String sanitize(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        return sanitizationEngine.sanitize(input, SanitizationStrategy.COMPREHENSIVE);
    }
    
    @Override
    public boolean isValid(String input) {
        return validate(input).isValid();
    }
    
    /**
     * Checks if input contains potential injection patterns using the injection detector
     * 
     * @param input The input to check
     * @return true if injection patterns are detected
     */
    public boolean detectInjectionAttempt(String input) {
        if (!StringUtils.hasText(input)) {
            return false;
        }
        
        InjectionDetectionResult result = injectionDetector.detectInjection(input);
        return result.hasThreats();
    }
    
    /**
     * Gets detailed injection detection results
     * 
     * @param input The input to analyze
     * @return InjectionDetectionResult with detailed threat information
     */
    public InjectionDetectionResult getInjectionDetectionResult(String input) {
        return injectionDetector.detectInjection(input);
    }
    
    /**
     * Initializes comprehensive security validation rules
     */
    private List<ValidationRule> initializeSecurityRules() {
        List<ValidationRule> rules = new ArrayList<>();
        
        // SQL Injection patterns
        rules.add(ValidationRule.createSecurityRule(
            "SQL_INJECTION_UNION",
            "(?i).*\\b(union|select|insert|update|delete|drop|create|alter|exec|execute)\\b.*",
            "Potential SQL injection detected: SQL keywords found"
        ));
        
        rules.add(ValidationRule.createSecurityRule(
            "SQL_INJECTION_COMMENT",
            "(?i).*(--|/\\*|\\*/|#).*",
            "Potential SQL injection detected: SQL comment patterns found"
        ));
        
        rules.add(ValidationRule.createSecurityRule(
            "SQL_INJECTION_QUOTES",
            "(?i).*('|(\\\\x27)|(\\\\x2D\\\\x2D)).*",
            "Potential SQL injection detected: Quote manipulation patterns found"
        ));
        
        // XSS patterns
        rules.add(ValidationRule.createSecurityRule(
            "XSS_SCRIPT_TAG",
            "(?i).*<\\s*script[^>]*>.*",
            "Potential XSS attack detected: Script tag found"
        ));
        
        rules.add(ValidationRule.createSecurityRule(
            "XSS_EVENT_HANDLER",
            "(?i).*(onload|onerror|onclick|onmouseover|onfocus|onblur)\\s*=.*",
            "Potential XSS attack detected: Event handler found"
        ));
        
        rules.add(ValidationRule.createSecurityRule(
            "XSS_JAVASCRIPT_PROTOCOL",
            "(?i).*javascript\\s*:.*",
            "Potential XSS attack detected: JavaScript protocol found"
        ));
        
        rules.add(ValidationRule.createSecurityRule(
            "XSS_DATA_PROTOCOL",
            "(?i).*data\\s*:.*",
            "Potential XSS attack detected: Data protocol found"
        ));
        
        // Command injection patterns
        rules.add(ValidationRule.createSecurityRule(
            "COMMAND_INJECTION_PIPE",
            ".*[|&;`$(){}\\[\\]<>].*",
            "Potential command injection detected: Shell metacharacters found"
        ));
        
        rules.add(ValidationRule.createSecurityRule(
            "COMMAND_INJECTION_KEYWORDS",
            "(?i).*(cmd|powershell|bash|sh|exec|system|eval|wget|curl).*",
            "Potential command injection detected: System command keywords found"
        ));
        
        // Path traversal patterns
        rules.add(ValidationRule.createSecurityRule(
            "PATH_TRAVERSAL",
            ".*(\\.\\.[\\\\/]|[\\\\/]\\.\\.[\\\\/]).*",
            "Potential path traversal attack detected"
        ));
        
        // LDAP injection patterns
        rules.add(ValidationRule.createSecurityRule(
            "LDAP_INJECTION",
            ".*[()=*!&|].*",
            "Potential LDAP injection detected: LDAP metacharacters found"
        ));
        
        // XML injection patterns
        rules.add(ValidationRule.createSecurityRule(
            "XML_INJECTION",
            "(?i).*<!\\[CDATA\\[|<!DOCTYPE|<!ENTITY.*",
            "Potential XML injection detected: XML entity or CDATA found"
        ));
        
        return rules;
    }
}