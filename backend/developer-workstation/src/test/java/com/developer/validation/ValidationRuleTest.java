package com.developer.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationRule
 */
class ValidationRuleTest {
    
    @Test
    @DisplayName("Should create and test format rule correctly")
    void shouldCreateAndTestFormatRuleCorrectly() {
        // Given
        ValidationRule emailRule = ValidationRule.createFormatRule(
            "EMAIL_FORMAT",
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            "Invalid email format"
        );
        
        String validEmail = "test@example.com";
        String invalidEmail = "invalid-email";
        
        // When
        boolean validMatches = emailRule.matches(validEmail);
        boolean invalidMatches = emailRule.matches(invalidEmail);
        
        boolean validShouldFail = emailRule.shouldFail(validEmail);
        boolean invalidShouldFail = emailRule.shouldFail(invalidEmail);
        
        // Then
        System.out.println("Valid email matches: " + validMatches);
        System.out.println("Invalid email matches: " + invalidMatches);
        System.out.println("Valid email should fail: " + validShouldFail);
        System.out.println("Invalid email should fail: " + invalidShouldFail);
        
        assertTrue(validMatches, "Valid email should match pattern");
        assertFalse(invalidMatches, "Invalid email should not match pattern");
        assertFalse(validShouldFail, "Valid email should not fail validation");
        assertTrue(invalidShouldFail, "Invalid email should fail validation");
    }
    
    @Test
    @DisplayName("Should create and test security rule correctly")
    void shouldCreateAndTestSecurityRuleCorrectly() {
        // Given
        ValidationRule sqlRule = ValidationRule.createSecurityRule(
            "SQL_INJECTION",
            "(?i).*\\b(union|select)\\b.*",
            "Potential SQL injection detected"
        );
        
        String safeInput = "Hello World";
        String dangerousInput = "'; SELECT * FROM users; --";
        
        // When
        boolean safeMatches = sqlRule.matches(safeInput);
        boolean dangerousMatches = sqlRule.matches(dangerousInput);
        
        boolean safeShouldFail = sqlRule.shouldFail(safeInput);
        boolean dangerousShouldFail = sqlRule.shouldFail(dangerousInput);
        
        // Then
        System.out.println("Safe input matches: " + safeMatches);
        System.out.println("Dangerous input matches: " + dangerousMatches);
        System.out.println("Safe input should fail: " + safeShouldFail);
        System.out.println("Dangerous input should fail: " + dangerousShouldFail);
        
        assertFalse(safeMatches, "Safe input should not match security pattern");
        assertTrue(dangerousMatches, "Dangerous input should match security pattern");
        assertFalse(safeShouldFail, "Safe input should not fail validation");
        assertTrue(dangerousShouldFail, "Dangerous input should fail validation");
    }
}