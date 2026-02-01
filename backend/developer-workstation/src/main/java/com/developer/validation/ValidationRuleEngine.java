package com.developer.validation;

import com.developer.dto.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configurable validation rule engine that manages and applies validation rules.
 * This engine allows for dynamic rule management and provides a centralized
 * validation framework for the application.
 */
@Component
@Slf4j
public class ValidationRuleEngine {
    
    private final Map<String, List<ValidationRule>> ruleGroups;
    private final List<ValidationRule> globalRules;
    
    public ValidationRuleEngine() {
        this.ruleGroups = new ConcurrentHashMap<>();
        this.globalRules = new ArrayList<>();
        initializeDefaultRules();
    }
    
    /**
     * Validates input against a specific rule group
     * 
     * @param input The input to validate
     * @param ruleGroupName The name of the rule group to apply
     * @return ValidationResult containing validation status and errors
     */
    public ValidationResult validateWithRuleGroup(String input, String ruleGroupName) {
        ValidationResult result = ValidationResult.builder().build();
        
        if (input == null) {
            return result;
        }
        
        // Apply global rules first
        applyRules(input, globalRules, result);
        
        // Apply specific rule group
        List<ValidationRule> rules = ruleGroups.get(ruleGroupName);
        if (rules != null) {
            applyRules(input, rules, result);
        } else {
            log.warn("Rule group '{}' not found", ruleGroupName);
        }
        
        return result;
    }
    
    /**
     * Validates input against all available rules
     * 
     * @param input The input to validate
     * @return ValidationResult containing validation status and errors
     */
    public ValidationResult validateWithAllRules(String input) {
        ValidationResult result = ValidationResult.builder().build();
        
        if (input == null) {
            return result;
        }
        
        // Apply global rules
        applyRules(input, globalRules, result);
        
        // Apply all rule groups
        for (Map.Entry<String, List<ValidationRule>> entry : ruleGroups.entrySet()) {
            applyRules(input, entry.getValue(), result);
        }
        
        return result;
    }
    
    /**
     * Adds a validation rule to a specific group
     * 
     * @param ruleGroupName The name of the rule group
     * @param rule The validation rule to add
     */
    public void addRuleToGroup(String ruleGroupName, ValidationRule rule) {
        ruleGroups.computeIfAbsent(ruleGroupName, k -> new ArrayList<>()).add(rule);
        log.debug("Added rule '{}' to group '{}'", rule.getName(), ruleGroupName);
    }
    
    /**
     * Adds a global validation rule that applies to all validations
     * 
     * @param rule The validation rule to add globally
     */
    public void addGlobalRule(ValidationRule rule) {
        globalRules.add(rule);
        log.debug("Added global rule '{}'", rule.getName());
    }
    
    /**
     * Removes a rule from a specific group
     * 
     * @param ruleGroupName The name of the rule group
     * @param ruleName The name of the rule to remove
     * @return true if the rule was removed, false if not found
     */
    public boolean removeRuleFromGroup(String ruleGroupName, String ruleName) {
        List<ValidationRule> rules = ruleGroups.get(ruleGroupName);
        if (rules != null) {
            boolean removed = rules.removeIf(rule -> rule.getName().equals(ruleName));
            if (removed) {
                log.debug("Removed rule '{}' from group '{}'", ruleName, ruleGroupName);
            }
            return removed;
        }
        return false;
    }
    
    /**
     * Enables or disables a specific rule in a group
     * 
     * @param ruleGroupName The name of the rule group
     * @param ruleName The name of the rule
     * @param enabled Whether the rule should be enabled
     * @return true if the rule was found and updated, false otherwise
     */
    public boolean setRuleEnabled(String ruleGroupName, String ruleName, boolean enabled) {
        List<ValidationRule> rules = ruleGroups.get(ruleGroupName);
        if (rules != null) {
            for (ValidationRule rule : rules) {
                if (rule.getName().equals(ruleName)) {
                    rule.setEnabled(enabled);
                    log.debug("Set rule '{}' in group '{}' to enabled: {}", 
                        ruleName, ruleGroupName, enabled);
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Gets all rules in a specific group
     * 
     * @param ruleGroupName The name of the rule group
     * @return List of validation rules, or empty list if group doesn't exist
     */
    public List<ValidationRule> getRulesInGroup(String ruleGroupName) {
        return new ArrayList<>(ruleGroups.getOrDefault(ruleGroupName, new ArrayList<>()));
    }
    
    /**
     * Gets all available rule group names
     * 
     * @return Set of rule group names
     */
    public java.util.Set<String> getRuleGroupNames() {
        return ruleGroups.keySet();
    }
    
    /**
     * Applies a list of validation rules to input and updates the result
     */
    private void applyRules(String input, List<ValidationRule> rules, ValidationResult result) {
        for (ValidationRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            
            try {
                if (rule.shouldFail(input)) {
                    if (rule.getSeverity() == ValidationSeverity.ERROR || 
                        rule.getSeverity() == ValidationSeverity.CRITICAL) {
                        result.addError(
                            "RULE_" + rule.getName().toUpperCase(),
                            rule.getErrorMessage(),
                            "input"
                        );
                    } else if (rule.getSeverity() == ValidationSeverity.WARNING) {
                        result.addWarning(
                            "RULE_" + rule.getName().toUpperCase(),
                            rule.getErrorMessage(),
                            "input"
                        );
                    }
                }
            } catch (Exception e) {
                log.error("Error applying validation rule '{}': {}", rule.getName(), e.getMessage());
                result.addError(
                    "RULE_ENGINE_ERROR",
                    "Validation rule processing failed",
                    "input"
                );
            }
        }
    }
    
    /**
     * Initializes default validation rule groups
     */
    private void initializeDefaultRules() {
        // Initialize common validation rule groups
        initializeSecurityRules();
        initializeFormatRules();
        initializeBusinessRules();
    }
    
    /**
     * Initializes security-related validation rules
     */
    private void initializeSecurityRules() {
        List<ValidationRule> securityRules = new ArrayList<>();
        
        // Basic security rules (subset of SecurityInputValidator rules for general use)
        securityRules.add(ValidationRule.createSecurityRule(
            "BASIC_XSS",
            "(?i).*<\\s*script.*",
            "Potential XSS: Script tags not allowed"
        ));
        
        securityRules.add(ValidationRule.createSecurityRule(
            "BASIC_SQL_INJECTION",
            "(?i).*(union|select|insert|update|delete)\\s+(from|into|set).*",
            "Potential SQL injection: SQL keywords detected"
        ));
        
        ruleGroups.put("security", securityRules);
    }
    
    /**
     * Initializes format validation rules
     */
    private void initializeFormatRules() {
        List<ValidationRule> formatRules = new ArrayList<>();
        
        formatRules.add(ValidationRule.createFormatRule(
            "EMAIL_FORMAT",
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            "Invalid email format"
        ));
        
        formatRules.add(ValidationRule.createFormatRule(
            "PHONE_FORMAT",
            "^[+]?[1-9]?[0-9]{7,15}$",
            "Invalid phone number format"
        ));
        
        ruleGroups.put("format", formatRules);
    }
    
    /**
     * Initializes business logic validation rules
     */
    private void initializeBusinessRules() {
        List<ValidationRule> businessRules = new ArrayList<>();
        
        businessRules.add(ValidationRule.createFormatRule(
            "USERNAME_FORMAT",
            "^[a-zA-Z0-9_]{3,20}$",
            "Username must be 3-20 characters, alphanumeric and underscore only"
        ));
        
        ruleGroups.put("business", businessRules);
    }
}