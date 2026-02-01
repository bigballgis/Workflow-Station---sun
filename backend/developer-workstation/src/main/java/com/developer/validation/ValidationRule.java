package com.developer.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * Represents a configurable validation rule with pattern matching and error messaging.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRule {
    
    /**
     * Unique name identifier for this validation rule
     */
    private String name;
    
    /**
     * Regular expression pattern for validation
     */
    private Pattern pattern;
    
    /**
     * Error message to display when validation fails
     */
    private String errorMessage;
    
    /**
     * Severity level of validation failure
     */
    @Builder.Default
    private ValidationSeverity severity = ValidationSeverity.ERROR;
    
    /**
     * Whether this rule should be applied (allows for dynamic rule enabling/disabling)
     */
    @Builder.Default
    private boolean enabled = true;
    
    /**
     * Description of what this rule validates
     */
    private String description;
    
    /**
     * Type of validation rule - determines how pattern matching is interpreted
     */
    @Builder.Default
    private ValidationRuleType ruleType = ValidationRuleType.SECURITY;
    
    /**
     * Validates input against this rule's pattern
     * 
     * @param input The input to validate
     * @return true if input matches the pattern (for positive rules) or doesn't match (for negative rules)
     */
    public boolean matches(String input) {
        if (!enabled || pattern == null || input == null) {
            return false; // Return false for disabled/invalid rules so they don't interfere
        }
        
        // For format rules, we want full string matching
        // For security rules, we want to find patterns anywhere in the string
        if (ruleType == ValidationRuleType.FORMAT) {
            return pattern.matcher(input).matches(); // Full string match
        } else {
            return pattern.matcher(input).find(); // Pattern found anywhere
        }
    }
    
    /**
     * Determines if this rule should cause validation failure based on match result
     * 
     * @param input The input being validated
     * @return true if validation should fail
     */
    public boolean shouldFail(String input) {
        boolean matches = matches(input);
        
        // For security rules, a match means failure
        // For format rules, no match means failure
        return ruleType == ValidationRuleType.SECURITY ? matches : !matches;
    }
    
    /**
     * Creates a validation rule that fails when the pattern is found (security rules)
     * 
     * @param name Rule name
     * @param pattern Pattern to detect
     * @param errorMessage Error message
     * @return ValidationRule configured as a security rule
     */
    public static ValidationRule createSecurityRule(String name, String pattern, String errorMessage) {
        return ValidationRule.builder()
                .name(name)
                .pattern(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE))
                .errorMessage(errorMessage)
                .severity(ValidationSeverity.ERROR)
                .ruleType(ValidationRuleType.SECURITY)
                .description("Security validation rule: " + name)
                .build();
    }
    
    /**
     * Creates a validation rule that passes when the pattern is found (format rules)
     * 
     * @param name Rule name
     * @param pattern Pattern to match
     * @param errorMessage Error message when pattern doesn't match
     * @return ValidationRule configured as a format rule
     */
    public static ValidationRule createFormatRule(String name, String pattern, String errorMessage) {
        return ValidationRule.builder()
                .name(name)
                .pattern(Pattern.compile(pattern))
                .errorMessage(errorMessage)
                .severity(ValidationSeverity.ERROR)
                .ruleType(ValidationRuleType.FORMAT)
                .description("Format validation rule: " + name)
                .build();
    }
}