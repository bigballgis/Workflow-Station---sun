package com.developer.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for SecurityAuditLogger.
 * 
 * **Feature: security-permission-system, Property 12: Error Logging**
 * **Feature: security-permission-system, Property 13: Security Event Logging**
 * **Feature: security-permission-system, Property 14: Log Categorization**
 * **Feature: security-permission-system, Property 15: Data Privacy in Logging**
 * 
 * **Validates: Requirements 7.1, 7.2, 7.3, 7.5**
 */
@Tag("property-test")
public class SecurityAuditLoggerPropertyTest {
    
    private SecurityAuditLogger auditLogger;
    private Logger mockLogger;
    
    @BeforeEach
    void setUp() {
        // Create audit logger with mock logger
        mockLogger = mock(Logger.class);
        auditLogger = new SecurityAuditLogger(mockLogger);
    }
    
    /**
     * Property 12: Error Logging
     * For any database error during permission checks, the system should log detailed error information
     */
    @Test
    void property_databaseErrorsAreLoggedWithDetails() {
        // Test with various error types and user/resource combinations
        String[] usernames = {"user1", "admin", "test@example.com", "user with spaces", null};
        String[] operations = {"permission_check", "role_check", "permission_evaluation"};
        String[] resources = {"READ_USERS", "ADMIN_ACCESS", "resource with spaces", null};
        Exception[] errors = {
            new RuntimeException("Database connection failed"),
            new IllegalStateException("Invalid state"),
            new NullPointerException("Null pointer"),
            new Exception("Generic error")
        };
        
        for (String username : usernames) {
            for (String operation : operations) {
                for (String resource : resources) {
                    for (Exception error : errors) {
                        // Reset mock for each iteration
                        reset(mockLogger);
                        
                        // Execute the logging
                        auditLogger.logDatabaseError(username, operation, resource, error);
                        
                        // Verify error logging occurred
                        verify(mockLogger, atLeastOnce()).error(anyString(), 
                                eq("DATABASE_ERROR"), anyString(), anyString(), anyString(), anyString(), any(Throwable.class));
                        
                        // Verify structured logging occurred
                        verify(mockLogger, atLeastOnce()).info(contains("AUDIT_EVENT"), 
                                eq("DATABASE_ERROR"), anyString(), anyString(), anyString(), anyString(), anyString());
                        
                        // Verify no sensitive data is logged (usernames should be sanitized)
                        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
                        verify(mockLogger, atLeastOnce()).error(logCaptor.capture(), 
                                anyString(), anyString(), anyString(), anyString(), anyString(), any(Throwable.class));
                        
                        List<String> logMessages = logCaptor.getAllValues();
                        for (String message : logMessages) {
                            // Should not contain line breaks or control characters
                            assertFalse(message.contains("\n"), "Log message should not contain line breaks");
                            assertFalse(message.contains("\r"), "Log message should not contain carriage returns");
                            assertFalse(message.contains("\t"), "Log message should not contain tabs");
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Property 13: Security Event Logging
     * For any unauthorized access attempt, the system should log security events with user and resource details
     */
    @Test
    void property_unauthorizedAccessAttemptsAreLogged() {
        // Test with various unauthorized access scenarios
        String[] usernames = {"attacker", "user1", "admin", "test@example.com", null};
        String[] operations = {"permission_check", "role_check", "resource_access"};
        String[] resources = {"ADMIN_PANEL", "USER_DATA", "SENSITIVE_INFO", null};
        String[] reasons = {"insufficient_privileges", "account_locked", "invalid_token", null};
        
        for (String username : usernames) {
            for (String operation : operations) {
                for (String resource : resources) {
                    for (String reason : reasons) {
                        // Reset mock for each iteration
                        reset(mockLogger);
                        
                        // Execute the logging
                        auditLogger.logUnauthorizedAccess(username, operation, resource, reason);
                        
                        // Verify security event logging occurred
                        verify(mockLogger, atLeastOnce()).warn(anyString(), 
                                eq("SECURITY_EVENT"), anyString(), anyString(), anyString(), anyString());
                        
                        // Verify structured logging occurred
                        verify(mockLogger, atLeastOnce()).info(contains("AUDIT_EVENT"), 
                                eq("SECURITY_EVENT"), anyString(), anyString(), anyString(), anyString(), anyString());
                        
                        // Verify user and resource details are included
                        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
                        verify(mockLogger, atLeastOnce()).info(logCaptor.capture(), 
                                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
                        
                        List<String> logMessages = logCaptor.getAllValues();
                        boolean foundUserInfo = false;
                        boolean foundResourceInfo = false;
                        
                        for (String message : logMessages) {
                            if (message.contains("user=")) foundUserInfo = true;
                            if (message.contains("resource=")) foundResourceInfo = true;
                        }
                        
                        assertTrue(foundUserInfo, "Security event should include user information");
                        assertTrue(foundResourceInfo, "Security event should include resource information");
                    }
                }
            }
        }
    }
    
    /**
     * Property 14: Log Categorization
     * For any log message, the system should distinguish between system errors and legitimate access denials
     */
    @Test
    void property_logMessagesAreCategorizedCorrectly() {
        String username = "testuser";
        String operation = "permission_check";
        String resource = "TEST_RESOURCE";
        Exception error = new RuntimeException("Test error");
        
        // Test database error categorization
        reset(mockLogger);
        auditLogger.logDatabaseError(username, operation, resource, error);
        
        // Verify the structured logging contains the correct category
        verify(mockLogger, atLeastOnce()).info(eq("AUDIT_EVENT category={} operation={} user={} resource={} success=false error_type={} timestamp={}"), 
                eq("DATABASE_ERROR"), anyString(), anyString(), anyString(), anyString(), anyString());
        
        // Test access denied categorization
        reset(mockLogger);
        auditLogger.logAccessDenied(username, operation, resource);
        
        // Verify the structured logging contains the correct category
        verify(mockLogger, atLeastOnce()).info(eq("AUDIT_EVENT category={} operation={} user={} resource={} success=false reason=insufficient_privileges timestamp={}"), 
                eq("ACCESS_DENIED"), anyString(), anyString(), anyString(), anyString());
        
        // Test system error categorization
        reset(mockLogger);
        auditLogger.logSystemError(username, operation, error);
        
        // Verify the structured logging contains the correct category
        verify(mockLogger, atLeastOnce()).info(eq("AUDIT_EVENT category={} operation={} user={} success=false error_type={} timestamp={}"), 
                eq("SYSTEM_ERROR"), anyString(), anyString(), anyString(), anyString());
        
        // Test security event categorization
        reset(mockLogger);
        auditLogger.logUnauthorizedAccess(username, operation, resource, "test_reason");
        
        // Verify the structured logging contains the correct category
        verify(mockLogger, atLeastOnce()).info(eq("AUDIT_EVENT category={} operation={} user={} resource={} success=false reason={} timestamp={}"), 
                eq("SECURITY_EVENT"), anyString(), anyString(), anyString(), anyString(), anyString());
    }
    
    /**
     * Property 15: Data Privacy in Logging
     * For any log message, the system should not include sensitive user data or credentials
     */
    @Test
    void property_sensitiveDataIsNotLoggedInAnyMessage() {
        // Test with potentially sensitive data
        String[] sensitiveUsernames = {
            "user\nwith\nnewlines",
            "user\rwith\rcarriage\rreturns", 
            "user\twith\ttabs",
            "user'with'quotes",
            "user\"with\"doublequotes",
            "user;with;semicolons",
            "user--with--comments",
            "user/*with*/comments",
            "password123",
            "secret_token_abc123"
        };
        
        String operation = "permission_check";
        String resource = "TEST_RESOURCE";
        Exception error = new RuntimeException("Test error");
        
        for (String sensitiveUsername : sensitiveUsernames) {
            // Test all logging methods with sensitive data
            reset(mockLogger);
            
            auditLogger.logDatabaseError(sensitiveUsername, operation, resource, error);
            auditLogger.logUnauthorizedAccess(sensitiveUsername, operation, resource, "test_reason");
            auditLogger.logAccessDenied(sensitiveUsername, operation, resource);
            auditLogger.logSystemError(sensitiveUsername, operation, error);
            auditLogger.logSuccessfulAccess(sensitiveUsername, operation, resource, false);
            
            // Capture all log messages
            ArgumentCaptor<String> allLogsCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeastOnce()).error(allLogsCaptor.capture(), 
                    anyString(), anyString(), anyString(), anyString(), anyString(), any(Throwable.class));
            verify(mockLogger, atLeastOnce()).warn(allLogsCaptor.capture(), 
                    anyString(), anyString(), anyString(), anyString(), anyString());
            verify(mockLogger, atLeastOnce()).info(allLogsCaptor.capture(), 
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
            verify(mockLogger, atLeastOnce()).debug(allLogsCaptor.capture(), 
                    anyString(), anyString(), anyString(), anyString(), any(Boolean.class));
            
            List<String> allLogMessages = allLogsCaptor.getAllValues();
            
            for (String logMessage : allLogMessages) {
                // Verify no line breaks, carriage returns, or tabs in log messages
                assertFalse(logMessage.contains("\n"), 
                        "Log message should not contain newlines: " + logMessage);
                assertFalse(logMessage.contains("\r"), 
                        "Log message should not contain carriage returns: " + logMessage);
                assertFalse(logMessage.contains("\t"), 
                        "Log message should not contain tabs: " + logMessage);
                
                // Verify no SQL injection patterns
                assertFalse(logMessage.contains("'"), 
                        "Log message should not contain single quotes: " + logMessage);
                assertFalse(logMessage.contains("\""), 
                        "Log message should not contain double quotes: " + logMessage);
                assertFalse(logMessage.contains(";"), 
                        "Log message should not contain semicolons: " + logMessage);
                assertFalse(logMessage.contains("--"), 
                        "Log message should not contain SQL comment markers: " + logMessage);
                assertFalse(logMessage.contains("/*"), 
                        "Log message should not contain SQL comment start: " + logMessage);
                assertFalse(logMessage.contains("*/"), 
                        "Log message should not contain SQL comment end: " + logMessage);
            }
        }
    }
    
    /**
     * Test that audit statistics are properly generated
     */
    @Test
    void property_auditStatisticsAreGenerated() {
        Map<String, Object> stats = auditLogger.getAuditStatistics();
        
        assertNotNull(stats, "Audit statistics should not be null");
        assertTrue(stats.containsKey("audit_logger_active"), "Should contain audit_logger_active");
        assertTrue(stats.containsKey("log_categories"), "Should contain log_categories");
        assertTrue(stats.containsKey("timestamp"), "Should contain timestamp");
        
        assertEquals(true, stats.get("audit_logger_active"), "Audit logger should be active");
        
        String[] categories = (String[]) stats.get("log_categories");
        assertNotNull(categories, "Log categories should not be null");
        assertTrue(categories.length > 0, "Should have at least one log category");
        
        // Verify expected categories are present
        List<String> categoryList = List.of(categories);
        assertTrue(categoryList.contains("DATABASE_ERROR"), "Should contain DATABASE_ERROR category");
        assertTrue(categoryList.contains("SECURITY_EVENT"), "Should contain SECURITY_EVENT category");
        assertTrue(categoryList.contains("ACCESS_DENIED"), "Should contain ACCESS_DENIED category");
        assertTrue(categoryList.contains("SYSTEM_ERROR"), "Should contain SYSTEM_ERROR category");
    }
    
    /**
     * Test configuration validation logging
     */
    @Test
    void property_configurationValidationIsLogged() {
        String[] configParams = {"jwt.secret", "cache.timeout", "database.url", null, ""};
        boolean[] validStates = {true, false};
        String[] errorMessages = {"Invalid format", "Missing value", null, ""};
        
        for (String param : configParams) {
            for (boolean isValid : validStates) {
                for (String errorMsg : errorMessages) {
                    reset(mockLogger);
                    
                    auditLogger.logConfigurationValidation(param, isValid, errorMsg);
                    
                    // Verify appropriate logging level was used
                    if (isValid) {
                        verify(mockLogger, atLeastOnce()).info(anyString(), any(Object.class));
                    } else {
                        verify(mockLogger, atLeastOnce()).error(anyString(), any(Object.class), any(Object.class));
                    }
                    
                    // Verify structured logging
                    verify(mockLogger, atLeastOnce()).info(eq("CONFIG_EVENT parameter={} valid={} error={} timestamp={}"), 
                            anyString(), eq(isValid), anyString(), anyString());
                    
                    ArgumentCaptor<String> configCaptor = ArgumentCaptor.forClass(String.class);
                    verify(mockLogger, atLeastOnce()).info(configCaptor.capture(), 
                            anyString(), eq(isValid), anyString(), anyString());
                    
                    List<String> configMessages = configCaptor.getAllValues();
                    boolean foundConfigEvent = configMessages.stream()
                            .anyMatch(msg -> msg.equals("CONFIG_EVENT parameter={} valid={} error={} timestamp={}"));
                    assertTrue(foundConfigEvent, "Configuration validation should be logged with correct validity");
                }
            }
        }
    }
}