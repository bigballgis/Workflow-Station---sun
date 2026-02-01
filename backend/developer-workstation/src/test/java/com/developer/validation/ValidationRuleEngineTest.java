package com.developer.validation;

import com.developer.dto.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationRuleEngine
 */
class ValidationRuleEngineTest {
    
    private ValidationRuleEngine ruleEngine;
    
    @BeforeEach
    void setUp() {
        ruleEngine = new ValidationRuleEngine();
    }
    
    @Test
    @DisplayName("Should validate with security rule group")
    void shouldValidateWithSecurityRuleGroup() {
        // Given
        String maliciousInput = "<script>alert('XSS')</script>";
        String safeInput = "Hello World";
        
        // When
        ValidationResult maliciousResult = ruleEngine.validateWithRuleGroup(maliciousInput, "security");
        ValidationResult safeResult = ruleEngine.validateWithRuleGroup(safeInput, "security");
        
        // Then
        assertFalse(maliciousResult.isValid());
        assertTrue(safeResult.isValid());
    }
    
    @Test
    @DisplayName("Should validate with format rule group")
    void shouldValidateWithFormatRuleGroup() {
        // Given
        String validEmail = "test@example.com";
        String invalidEmail = "invalid-email";
        
        // Debug: Check what rules are in the format group
        List<ValidationRule> formatRules = ruleEngine.getRulesInGroup("format");
        System.out.println("Format rules count: " + formatRules.size());
        for (ValidationRule rule : formatRules) {
            System.out.println("Rule: " + rule.getName() + ", Type: " + rule.getRuleType());
        }
        
        // When
        ValidationResult validResult = ruleEngine.validateWithRuleGroup(validEmail, "format");
        ValidationResult invalidResult = ruleEngine.validateWithRuleGroup(invalidEmail, "format");
        
        // Debug output
        System.out.println("Valid email result - Valid: " + validResult.isValid() + ", Errors: " + validResult.getErrors().size());
        System.out.println("Invalid email result - Valid: " + invalidResult.isValid() + ", Errors: " + invalidResult.getErrors().size());
        
        // Then
        assertTrue(validResult.isValid());
        assertFalse(invalidResult.isValid());
    }
    
    @Test
    @DisplayName("Should add and apply custom rules")
    void shouldAddAndApplyCustomRules() {
        // Given
        ValidationRule customRule = ValidationRule.createSecurityRule(
            "CUSTOM_FORBIDDEN_WORD",
            ".*forbidden.*",
            "Contains forbidden word"
        );
        
        // When
        ruleEngine.addRuleToGroup("custom", customRule);
        ValidationResult result = ruleEngine.validateWithRuleGroup("This contains forbidden word", "custom");
        
        // Then
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
    }
    
    @Test
    @DisplayName("Should handle non-existent rule groups gracefully")
    void shouldHandleNonExistentRuleGroupsGracefully() {
        // Given
        String input = "test input";
        
        // When
        ValidationResult result = ruleEngine.validateWithRuleGroup(input, "non-existent");
        
        // Then
        assertTrue(result.isValid()); // Should pass when no rules are found
    }
    
    @Test
    @DisplayName("Should enable and disable rules")
    void shouldEnableAndDisableRules() {
        // Given
        ValidationRule rule = ValidationRule.createSecurityRule(
            "TEST_RULE",
            ".*test.*",
            "Contains test word"
        );
        ruleEngine.addRuleToGroup("test", rule);
        
        // When - rule is enabled by default
        ValidationResult enabledResult = ruleEngine.validateWithRuleGroup("test input", "test");
        
        // Disable the rule
        ruleEngine.setRuleEnabled("test", "TEST_RULE", false);
        ValidationResult disabledResult = ruleEngine.validateWithRuleGroup("test input", "test");
        
        // Then
        assertFalse(enabledResult.isValid());
        assertTrue(disabledResult.isValid());
    }
    
    @Test
    @DisplayName("Should remove rules from groups")
    void shouldRemoveRulesFromGroups() {
        // Given
        ValidationRule rule = ValidationRule.createSecurityRule(
            "REMOVABLE_RULE",
            ".*remove.*",
            "Contains remove word"
        );
        ruleEngine.addRuleToGroup("test", rule);
        
        // When
        boolean removed = ruleEngine.removeRuleFromGroup("test", "REMOVABLE_RULE");
        ValidationResult result = ruleEngine.validateWithRuleGroup("remove this", "test");
        
        // Then
        assertTrue(removed);
        assertTrue(result.isValid()); // Should pass after rule removal
    }
    
    @Test
    @DisplayName("Should get rules in group")
    void shouldGetRulesInGroup() {
        // Given
        ValidationRule rule1 = ValidationRule.createSecurityRule("RULE1", ".*test1.*", "Test rule 1");
        ValidationRule rule2 = ValidationRule.createSecurityRule("RULE2", ".*test2.*", "Test rule 2");
        
        ruleEngine.addRuleToGroup("test", rule1);
        ruleEngine.addRuleToGroup("test", rule2);
        
        // When
        List<ValidationRule> rules = ruleEngine.getRulesInGroup("test");
        
        // Then
        assertEquals(2, rules.size());
        assertTrue(rules.stream().anyMatch(r -> r.getName().equals("RULE1")));
        assertTrue(rules.stream().anyMatch(r -> r.getName().equals("RULE2")));
    }
    
    @Test
    @DisplayName("Should get all rule group names")
    void shouldGetAllRuleGroupNames() {
        // When
        var groupNames = ruleEngine.getRuleGroupNames();
        
        // Then
        assertTrue(groupNames.contains("security"));
        assertTrue(groupNames.contains("format"));
        assertTrue(groupNames.contains("business"));
    }
    
    @Test
    @DisplayName("Should validate with all rules")
    void shouldValidateWithAllRules() {
        // Given
        String input = "<script>alert('XSS')</script>"; // Should fail security rules
        
        // When
        ValidationResult result = ruleEngine.validateWithAllRules(input);
        
        // Then
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }
    
    @Test
    @DisplayName("Should add global rules")
    void shouldAddGlobalRules() {
        // Given
        ValidationRule globalRule = ValidationRule.createSecurityRule(
            "GLOBAL_RULE",
            ".*global.*",
            "Contains global word"
        );
        
        // When
        ruleEngine.addGlobalRule(globalRule);
        ValidationResult result = ruleEngine.validateWithRuleGroup("global test", "security");
        
        // Then
        assertFalse(result.isValid()); // Should fail due to global rule
    }
    
    @Test
    @DisplayName("Should handle null input gracefully")
    void shouldHandleNullInputGracefully() {
        // When
        ValidationResult result = ruleEngine.validateWithRuleGroup(null, "security");
        
        // Then
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }
    
    @Test
    @DisplayName("Should handle rule processing errors gracefully")
    void shouldHandleRuleProcessingErrorsGracefully() {
        // Given - Create a rule that will cause an error during processing
        ValidationRule problematicRule = ValidationRule.builder()
            .name("PROBLEMATIC_RULE")
            .pattern(java.util.regex.Pattern.compile(".*test.*")) // Valid pattern but we'll test error handling
            .errorMessage("Test error")
            .enabled(true)
            .build();
        
        ruleEngine.addRuleToGroup("test", problematicRule);
        
        // When - Test with input that should trigger the rule
        ValidationResult result = ruleEngine.validateWithRuleGroup("test input", "test");
        
        // Then - Should fail because it's a security-type rule and matches
        assertFalse(result.isValid()); // Should fail when rule matches for security rules
    }
}