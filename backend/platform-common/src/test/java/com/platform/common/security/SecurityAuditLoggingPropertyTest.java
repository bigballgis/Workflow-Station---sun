package com.platform.common.security;

import com.platform.common.config.SecurityConfig;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-Based Tests for Security Audit Logging
 * 
 * **Feature: technical-debt-remediation, Property 3: Security Audit Logging**
 * 
 * **Validates: Requirements 1.5, 7.5**
 * 
 * Tests universal properties of security audit logging including:
 * - Security event logging without sensitive data exposure
 * - Audit trail maintenance and retention
 * - Security metrics tracking and monitoring
 * - Event search and retrieval functionality
 * - Risk level assessment and alerting
 * 
 * @author Platform Team
 * @version 1.0
 */
class SecurityAuditLoggingPropertyTest {
    
    private SecurityConfig securityConfig;
    private SecurityAuditLogger securityAuditLogger;
    
    @BeforeProperty
    void setUp() {
        // Configure security settings with audit logging enabled
        securityConfig = new SecurityConfig();
        securityConfig.setEnableSecurityAuditLogging(true);
        securityConfig.setEnableSecurityEventNotification(true);
        
        securityAuditLogger = new SecurityAuditLogger(securityConfig);
    }
    
    /**
     * Property: Security events should always be logged when audit logging is enabled
     * **Validates: Requirements 1.5, 7.5**
     */
    @Property(tries = 100)
    @Label("Security events are always logged when audit logging is enabled")
    void securityEventsAreAlwaysLoggedWhenEnabled(@ForAll("validEventTypes") String eventType,
                                                 @ForAll("validDescriptions") String description,
                                                 @ForAll("validUsernames") String username) {
        Map<String, String> metadata = Map.of(
                "username", username,
                "ipAddress", "192.168.1.100",
                "resource", "TEST_RESOURCE",
                "action", "TEST_ACTION"
        );
        
        // Get initial metrics
        Map<String, Object> initialMetrics = securityAuditLogger.getSecurityMetrics();
        long initialEventCount = ((Number) initialMetrics.get("totalSecurityEvents")).longValue();
        
        // Log security event
        securityAuditLogger.logSecurityEvent(eventType, description, metadata);
        
        // Verify event was logged
        Map<String, Object> updatedMetrics = securityAuditLogger.getSecurityMetrics();
        long updatedEventCount = ((Number) updatedMetrics.get("totalSecurityEvents")).longValue();
        
        assertThat(updatedEventCount)
                .as("Security event count should increase after logging")
                .isEqualTo(initialEventCount + 1);
        
        // Verify event appears in audit trail
        List<SecurityAuditLogger.SecurityAuditEvent> auditTrail = securityAuditLogger.getAuditTrail(10);
        assertThat(auditTrail)
                .as("Audit trail should contain the logged event")
                .isNotEmpty();
        
        SecurityAuditLogger.SecurityAuditEvent lastEvent = auditTrail.get(auditTrail.size() - 1);
        assertThat(lastEvent.getEventType())
                .as("Last event should match logged event type")
                .isEqualTo(eventType);
        
        assertThat(lastEvent.getDescription())
                .as("Last event should match logged description")
                .isEqualTo(description);
        
        assertThat(lastEvent.getUsername())
                .as("Last event should match logged username")
                .isEqualTo(username);
    }
    
    /**
     * Property: Authentication events should always be properly categorized and tracked
     * **Validates: Requirements 1.5, 7.5**
     */
    @Property(tries = 100)
    @Label("Authentication events are properly categorized and tracked")
    void authenticationEventsAreProperlyTracked(@ForAll("validUsernames") String username,
                                              @ForAll("validIpAddresses") String ipAddress,
                                              @ForAll("validUserAgents") String userAgent,
                                              @ForAll boolean success) {
        String eventType = success ? "AUTHENTICATION_SUCCESS" : "AUTHENTICATION_FAILED";
        String description = success ? "User authenticated successfully" : "Authentication failed";
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("sessionId", "test-session-" + System.currentTimeMillis());
        if (!success) {
            metadata.put("reason", "Invalid credentials");
        }
        
        // Get initial authentication event count
        Map<String, Object> initialMetrics = securityAuditLogger.getSecurityMetrics();
        long initialAuthEvents = ((Number) initialMetrics.get("authenticationEvents")).longValue();
        
        // Log authentication event
        securityAuditLogger.logAuthenticationEvent(eventType, description, username, 
                                                  ipAddress, userAgent, success, metadata);
        
        // Verify authentication event count increased
        Map<String, Object> updatedMetrics = securityAuditLogger.getSecurityMetrics();
        long updatedAuthEvents = ((Number) updatedMetrics.get("authenticationEvents")).longValue();
        
        assertThat(updatedAuthEvents)
                .as("Authentication event count should increase")
                .isEqualTo(initialAuthEvents + 1);
        
        // Verify event details in audit trail
        List<SecurityAuditLogger.SecurityAuditEvent> auditTrail = securityAuditLogger.getAuditTrail(1);
        assertThat(auditTrail)
                .as("Audit trail should contain the authentication event")
                .hasSize(1);
        
        SecurityAuditLogger.SecurityAuditEvent event = auditTrail.get(0);
        assertThat(event.getEventType())
                .as("Event type should match")
                .isEqualTo(eventType);
        
        assertThat(event.getUsername())
                .as("Username should match")
                .isEqualTo(username);
        
        assertThat(event.getIpAddress())
                .as("IP address should match")
                .isEqualTo(ipAddress);
        
        assertThat(event.isSuccess())
                .as("Success flag should match")
                .isEqualTo(success);
        
        assertThat(event.getRiskLevel())
                .as("Risk level should be assigned")
                .isNotNull()
                .isIn("LOW", "MEDIUM", "HIGH", "CRITICAL");
    }
    
    /**
     * Property: Authorization events should always be properly categorized and tracked
     * **Validates: Requirements 1.5, 7.5**
     */
    @Property(tries = 100)
    @Label("Authorization events are properly categorized and tracked")
    void authorizationEventsAreProperlyTracked(@ForAll("validUsernames") String username,
                                             @ForAll("validResources") String resource,
                                             @ForAll("validActions") String action,
                                             @ForAll boolean success) {
        String eventType = success ? "AUTHORIZATION_SUCCESS" : "AUTHORIZATION_DENIED";
        String description = success ? "Access granted" : "Access denied";
        
        Map<String, String> metadata = new HashMap<>();
        if (success) {
            metadata.put("grantedBy", "TEST_ROLE");
        } else {
            metadata.put("reason", "Insufficient permissions");
        }
        
        // Get initial authorization event count
        Map<String, Object> initialMetrics = securityAuditLogger.getSecurityMetrics();
        long initialAuthzEvents = ((Number) initialMetrics.get("authorizationEvents")).longValue();
        long initialViolations = ((Number) initialMetrics.get("securityViolations")).longValue();
        
        // Log authorization event
        securityAuditLogger.logAuthorizationEvent(eventType, description, username, 
                                                 resource, action, success, metadata);
        
        // Verify authorization event count increased
        Map<String, Object> updatedMetrics = securityAuditLogger.getSecurityMetrics();
        long updatedAuthzEvents = ((Number) updatedMetrics.get("authorizationEvents")).longValue();
        long updatedViolations = ((Number) updatedMetrics.get("securityViolations")).longValue();
        
        assertThat(updatedAuthzEvents)
                .as("Authorization event count should increase")
                .isEqualTo(initialAuthzEvents + 1);
        
        if (!success) {
            assertThat(updatedViolations)
                    .as("Security violations should increase for failed authorization")
                    .isEqualTo(initialViolations + 1);
        }
        
        // Verify event details in audit trail
        List<SecurityAuditLogger.SecurityAuditEvent> auditTrail = securityAuditLogger.getAuditTrail(1);
        assertThat(auditTrail)
                .as("Audit trail should contain the authorization event")
                .hasSize(1);
        
        SecurityAuditLogger.SecurityAuditEvent event = auditTrail.get(0);
        assertThat(event.getEventType())
                .as("Event type should match")
                .isEqualTo(eventType);
        
        assertThat(event.getUsername())
                .as("Username should match")
                .isEqualTo(username);
        
        assertThat(event.getResource())
                .as("Resource should match")
                .isEqualTo(resource);
        
        assertThat(event.getAction())
                .as("Action should match")
                .isEqualTo(action);
        
        assertThat(event.isSuccess())
                .as("Success flag should match")
                .isEqualTo(success);
    }
    
    /**
     * Property: Sensitive data should never be exposed in audit logs
     * **Validates: Requirements 1.5, 7.5**
     */
    @Property(tries = 100)
    @Label("Sensitive data is never exposed in audit logs")
    void sensitiveDataIsNeverExposedInAuditLogs(@ForAll("validUsernames") String username,
                                              @ForAll("sensitiveData") String sensitiveValue) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("username", username);
        metadata.put("password", sensitiveValue);
        metadata.put("secret", sensitiveValue);
        metadata.put("token", sensitiveValue);
        metadata.put("key", sensitiveValue);
        metadata.put("normalData", "this-is-not-sensitive");
        
        // Log security event with sensitive data
        securityAuditLogger.logSecurityEvent("TEST_EVENT", "Test event with sensitive data", metadata);
        
        // Retrieve the logged event
        List<SecurityAuditLogger.SecurityAuditEvent> auditTrail = securityAuditLogger.getAuditTrail(1);
        assertThat(auditTrail)
                .as("Audit trail should contain the event")
                .hasSize(1);
        
        SecurityAuditLogger.SecurityAuditEvent event = auditTrail.get(0);
        Map<String, String> eventMetadata = event.getMetadata();
        
        // Verify sensitive data is masked
        assertThat(eventMetadata.get("password"))
                .as("Password should be masked")
                .doesNotContain(sensitiveValue)
                .matches(".*\\*\\*\\*.*|\\[MASKED\\]|\\[EMPTY\\]");
        
        assertThat(eventMetadata.get("secret"))
                .as("Secret should be masked")
                .doesNotContain(sensitiveValue)
                .matches(".*\\*\\*\\*.*|\\[MASKED\\]|\\[EMPTY\\]");
        
        assertThat(eventMetadata.get("token"))
                .as("Token should be masked")
                .doesNotContain(sensitiveValue)
                .matches(".*\\*\\*\\*.*|\\[MASKED\\]|\\[EMPTY\\]");
        
        assertThat(eventMetadata.get("key"))
                .as("Key should be masked")
                .doesNotContain(sensitiveValue)
                .matches(".*\\*\\*\\*.*|\\[MASKED\\]|\\[EMPTY\\]");
        
        // Verify non-sensitive data is preserved
        assertThat(eventMetadata.get("normalData"))
                .as("Non-sensitive data should be preserved")
                .isEqualTo("this-is-not-sensitive");
        
        assertThat(eventMetadata.get("username"))
                .as("Username should be preserved (not considered sensitive in this context)")
                .isEqualTo(username);
    }
    
    /**
     * Property: Risk levels should be correctly assigned based on event characteristics
     * **Validates: Requirements 1.5, 7.5**
     */
    @Property(tries = 100)
    @Label("Risk levels are correctly assigned based on event characteristics")
    void riskLevelsAreCorrectlyAssigned(@ForAll("riskEventTypes") String eventType,
                                       @ForAll boolean success) {
        String username = "testuser";
        String description = "Test event for risk assessment";
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("username", username);
        metadata.put("success", String.valueOf(success));
        
        // Log security event
        securityAuditLogger.logSecurityEvent(eventType, description, metadata);
        
        // Retrieve the logged event
        List<SecurityAuditLogger.SecurityAuditEvent> auditTrail = securityAuditLogger.getAuditTrail(1);
        assertThat(auditTrail)
                .as("Audit trail should contain the event")
                .hasSize(1);
        
        SecurityAuditLogger.SecurityAuditEvent event = auditTrail.get(0);
        String riskLevel = event.getRiskLevel();
        
        // Verify risk level assignment logic
        if (eventType.contains("HIJACKING") || eventType.contains("INJECTION") || 
            eventType.contains("VIOLATION") || eventType.contains("BREACH")) {
            assertThat(riskLevel)
                    .as("Critical events should have CRITICAL risk level")
                    .isEqualTo("CRITICAL");
        } else if (!success && (eventType.contains("AUTHENTICATION") || eventType.contains("AUTHORIZATION"))) {
            assertThat(riskLevel)
                    .as("Failed auth events should have HIGH risk level")
                    .isEqualTo("HIGH");
        } else if (eventType.contains("LOCKOUT") || eventType.contains("BLOCKED") || eventType.contains("FAILED")) {
            assertThat(riskLevel)
                    .as("Lockout/blocked events should have HIGH risk level")
                    .isEqualTo("HIGH");
        } else {
            assertThat(riskLevel)
                    .as("Risk level should be valid")
                    .isIn("LOW", "MEDIUM", "HIGH", "CRITICAL");
        }
    }
    
    /**
     * Property: Audit trail search should always return matching events
     * **Validates: Requirements 1.5, 7.5**
     */
    @Property(tries = 50)
    @Label("Audit trail search returns matching events")
    void auditTrailSearchReturnsMatchingEvents(@ForAll("validUsernames") String username,
                                             @ForAll("validEventTypes") String eventType) {
        // Clear audit trail for clean test
        securityAuditLogger.clearAuditTrail();
        
        // Log multiple events with different characteristics
        securityAuditLogger.logSecurityEvent(eventType, "Target event", 
                Map.of("username", username));
        
        securityAuditLogger.logSecurityEvent("OTHER_EVENT", "Other event", 
                Map.of("username", "otheruser"));
        
        securityAuditLogger.logSecurityEvent(eventType, "Another target event", 
                Map.of("username", "differentuser"));
        
        // Search by username
        List<SecurityAuditLogger.SecurityAuditEvent> usernameResults = 
                securityAuditLogger.searchAuditTrail(username, null, null, null);
        
        assertThat(usernameResults)
                .as("Search by username should return matching events")
                .hasSize(1)
                .allMatch(event -> username.equals(event.getUsername()));
        
        // Search by event type
        List<SecurityAuditLogger.SecurityAuditEvent> eventTypeResults = 
                securityAuditLogger.searchAuditTrail(null, eventType, null, null);
        
        assertThat(eventTypeResults)
                .as("Search by event type should return matching events")
                .hasSize(2)
                .allMatch(event -> eventType.equals(event.getEventType()));
        
        // Search by both username and event type
        List<SecurityAuditLogger.SecurityAuditEvent> combinedResults = 
                securityAuditLogger.searchAuditTrail(username, eventType, null, null);
        
        assertThat(combinedResults)
                .as("Search by username and event type should return matching events")
                .hasSize(1)
                .allMatch(event -> username.equals(event.getUsername()) && 
                                 eventType.equals(event.getEventType()));
    }
    
    /**
     * Property: Security metrics should always be accurate and consistent
     * **Validates: Requirements 1.5, 7.5**
     */
    @Property(tries = 50)
    @Label("Security metrics are accurate and consistent")
    void securityMetricsAreAccurateAndConsistent(@ForAll("smallPositiveIntegers") int eventCount) {
        // Reset metrics for clean test
        securityAuditLogger.resetMetrics();
        securityAuditLogger.clearAuditTrail();
        
        // Log various types of events
        int authEvents = 0;
        int authzEvents = 0;
        int violations = 0;
        
        for (int i = 0; i < eventCount; i++) {
            if (i % 3 == 0) {
                securityAuditLogger.logAuthenticationEvent("AUTH_EVENT", "Auth event", 
                        "user" + i, "192.168.1." + (i % 255), "TestAgent", true, Map.of());
                authEvents++;
            } else if (i % 3 == 1) {
                securityAuditLogger.logAuthorizationEvent("AUTHZ_EVENT", "Authz event", 
                        "user" + i, "resource", "action", false, Map.of());
                authzEvents++;
                violations++; // Failed authorization counts as violation
            } else {
                securityAuditLogger.logSecurityEvent("GENERAL_EVENT", "General event", 
                        Map.of("username", "user" + i));
            }
        }
        
        // Verify metrics accuracy
        Map<String, Object> metrics = securityAuditLogger.getSecurityMetrics();
        
        assertThat(((Number) metrics.get("totalSecurityEvents")).longValue())
                .as("Total events should match logged count")
                .isEqualTo(eventCount);
        
        assertThat(((Number) metrics.get("authenticationEvents")).longValue())
                .as("Authentication events should match logged count")
                .isEqualTo(authEvents);
        
        assertThat(((Number) metrics.get("authorizationEvents")).longValue())
                .as("Authorization events should match logged count")
                .isEqualTo(authzEvents);
        
        assertThat(((Number) metrics.get("securityViolations")).longValue())
                .as("Security violations should match logged count")
                .isEqualTo(violations);
        
        // Verify audit trail size matches
        assertThat(((Number) metrics.get("auditTrailSize")).longValue())
                .as("Audit trail size should match event count")
                .isEqualTo(eventCount);
    }
    
    // ==================== Arbitraries for Test Data Generation ====================
    
    @Provide
    Arbitrary<String> validEventTypes() {
        return Arbitraries.oneOf(
                Arbitraries.just("AUTHENTICATION_SUCCESS"),
                Arbitraries.just("AUTHENTICATION_FAILED"),
                Arbitraries.just("AUTHORIZATION_SUCCESS"),
                Arbitraries.just("AUTHORIZATION_DENIED"),
                Arbitraries.just("SESSION_CREATED"),
                Arbitraries.just("SESSION_INVALIDATED"),
                Arbitraries.just("SECURITY_VIOLATION"),
                Arbitraries.just("CONFIG_ACCESS"),
                Arbitraries.just("ADMIN_ACTION")
        );
    }
    
    @Provide
    Arbitrary<String> riskEventTypes() {
        return Arbitraries.oneOf(
                // Critical risk events
                Arbitraries.just("SESSION_HIJACKING_DETECTED"),
                Arbitraries.just("SQL_INJECTION_ATTEMPT"),
                Arbitraries.just("SECURITY_VIOLATION_BREACH"),
                // High risk events
                Arbitraries.just("AUTHENTICATION_FAILED"),
                Arbitraries.just("AUTHORIZATION_FAILED"),
                Arbitraries.just("ACCOUNT_LOCKOUT"),
                Arbitraries.just("ACCESS_BLOCKED"),
                // Medium/Low risk events
                Arbitraries.just("SESSION_TIMEOUT"),
                Arbitraries.just("CONFIG_ACCESS"),
                Arbitraries.just("USER_LOGIN")
        );
    }
    
    @Provide
    Arbitrary<String> validDescriptions() {
        return Arbitraries.oneOf(
                Arbitraries.just("User authentication successful"),
                Arbitraries.just("Access denied due to insufficient permissions"),
                Arbitraries.just("Session created for user"),
                Arbitraries.just("Security policy violation detected"),
                Arbitraries.just("Configuration accessed by administrator"),
                Arbitraries.just("Suspicious activity detected")
        );
    }
    
    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> s + Arbitraries.integers().between(1, 999).sample());
    }
    
    @Provide
    Arbitrary<String> validIpAddresses() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 255),
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(1, 255)
        ).as((a, b, c, d) -> a + "." + b + "." + c + "." + d);
    }
    
    @Provide
    Arbitrary<String> validUserAgents() {
        return Arbitraries.oneOf(
                Arbitraries.just("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                Arbitraries.just("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"),
                Arbitraries.just("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")
        );
    }
    
    @Provide
    Arbitrary<String> validResources() {
        return Arbitraries.oneOf(
                Arbitraries.just("USER"),
                Arbitraries.just("TASK"),
                Arbitraries.just("PROCESS"),
                Arbitraries.just("REPORT"),
                Arbitraries.just("ADMIN"),
                Arbitraries.just("CONFIG")
        );
    }
    
    @Provide
    Arbitrary<String> validActions() {
        return Arbitraries.oneOf(
                Arbitraries.just("VIEW"),
                Arbitraries.just("CREATE"),
                Arbitraries.just("UPDATE"),
                Arbitraries.just("DELETE"),
                Arbitraries.just("EXECUTE"),
                Arbitraries.just("APPROVE")
        );
    }
    
    @Provide
    Arbitrary<String> sensitiveData() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(8)
                .ofMaxLength(32);
    }
    
    @Provide
    Arbitrary<Integer> smallPositiveIntegers() {
        return Arbitraries.integers().between(1, 20);
    }
}