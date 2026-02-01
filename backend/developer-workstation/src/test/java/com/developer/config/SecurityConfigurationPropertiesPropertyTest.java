package com.developer.config;

import com.developer.security.SecurityAuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for SecurityConfigurationProperties.
 * 
 * **Feature: security-permission-system, Property 16: Configuration Validation**
 * 
 * **Validates: Requirements 8.5**
 */
@Tag("property-test")
public class SecurityConfigurationPropertiesPropertyTest {
    
    @Mock
    private SecurityAuditLogger mockAuditLogger;
    
    private SecurityConfigurationProperties configProperties;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        configProperties = new SecurityConfigurationProperties(mockAuditLogger);
    }
    
    /**
     * Property 16: Configuration Validation
     * For any configuration parameter at startup, the system should validate the parameter and report invalid settings
     */
    @Test
    void property_configurationParametersAreValidatedAtStartup() {
        // Test valid configuration - should not throw exception
        SecurityConfigurationProperties validConfig = new SecurityConfigurationProperties(mockAuditLogger);
        
        // Set valid values
        validConfig.getCache().setSessionTimeoutMinutes(30);
        validConfig.getCache().setMaxSize(1000);
        validConfig.getCache().setEnabled(true);
        validConfig.getCache().setCleanupIntervalMinutes(15);
        
        validConfig.getDatabase().setQueryTimeoutSeconds(30);
        validConfig.getDatabase().setRetryAttempts(2);
        validConfig.getDatabase().setRetryDelayMs(1000);
        validConfig.getDatabase().setConnectionPoolingEnabled(true);
        
        validConfig.getPermission().setResolutionStrategy(SecurityConfigurationProperties.ResolutionStrategy.DATABASE_FIRST);
        validConfig.getPermission().setStrictChecking(true);
        validConfig.getPermission().setAuditLogging(true);
        validConfig.getPermission().setMaxPermissionNameLength(100);
        validConfig.getPermission().setMaxRoleNameLength(100);
        
        // Should not throw exception
        assertDoesNotThrow(() -> validConfig.validateConfiguration());
        
        // Verify successful validation was logged
        verify(mockAuditLogger, atLeastOnce()).logConfigurationValidation(eq("security.cache"), eq(true), isNull());
        verify(mockAuditLogger, atLeastOnce()).logConfigurationValidation(eq("security.database"), eq(true), isNull());
        verify(mockAuditLogger, atLeastOnce()).logConfigurationValidation(eq("security.permission"), eq(true), isNull());
    }
    
    @Test
    void property_invalidCacheConfigurationIsRejected() {
        // Test various invalid cache configurations
        int[] invalidTimeouts = {0, -1, -10, 1441}; // Outside valid range 1-1440
        int[] invalidSizes = {0, -1, -100, 100001}; // Outside valid range 10-100000
        int[] invalidCleanupIntervals = {0, -1, 61}; // Outside valid range 1-60
        
        for (int timeout : invalidTimeouts) {
            SecurityConfigurationProperties invalidConfig = new SecurityConfigurationProperties(mockAuditLogger);
            invalidConfig.getCache().setSessionTimeoutMinutes(timeout);
            
            Exception exception = assertThrows(Exception.class, () -> invalidConfig.validateConfiguration());
            assertTrue(exception.getMessage().contains("timeout") || exception.getMessage().contains("positive"),
                    "Should reject invalid timeout: " + timeout);
            
            // Verify failure was logged
            verify(mockAuditLogger, atLeastOnce()).logConfigurationValidation(eq("security"), eq(false), anyString());
        }
        
        for (int size : invalidSizes) {
            reset(mockAuditLogger);
            SecurityConfigurationProperties invalidConfig = new SecurityConfigurationProperties(mockAuditLogger);
            invalidConfig.getCache().setMaxSize(size);
            
            Exception exception = assertThrows(Exception.class, () -> invalidConfig.validateConfiguration());
            assertTrue(exception.getMessage().contains("size") || exception.getMessage().contains("positive"),
                    "Should reject invalid cache size: " + size);
            
            // Verify failure was logged
            verify(mockAuditLogger, atLeastOnce()).logConfigurationValidation(eq("security"), eq(false), anyString());
        }
    }
    
    @Test
    void property_invalidDatabaseConfigurationIsRejected() {
        // Test various invalid database configurations
        int[] invalidTimeouts = {0, -1, -30}; // Must be positive
        int[] invalidRetryAttempts = {-1, -5, 6}; // Outside valid range 0-5
        int[] invalidRetryDelays = {0, -1, 10001}; // Outside valid range 100-10000
        
        for (int timeout : invalidTimeouts) {
            reset(mockAuditLogger);
            SecurityConfigurationProperties invalidConfig = new SecurityConfigurationProperties(mockAuditLogger);
            invalidConfig.getDatabase().setQueryTimeoutSeconds(timeout);
            
            Exception exception = assertThrows(Exception.class, () -> invalidConfig.validateConfiguration());
            assertTrue(exception.getMessage().contains("timeout") || exception.getMessage().contains("positive"),
                    "Should reject invalid query timeout: " + timeout);
            
            // Verify failure was logged
            verify(mockAuditLogger, atLeastOnce()).logConfigurationValidation(eq("security"), eq(false), anyString());
        }
        
        for (int retryAttempts : invalidRetryAttempts) {
            reset(mockAuditLogger);
            SecurityConfigurationProperties invalidConfig = new SecurityConfigurationProperties(mockAuditLogger);
            invalidConfig.getDatabase().setRetryAttempts(retryAttempts);
            
            Exception exception = assertThrows(Exception.class, () -> invalidConfig.validateConfiguration());
            assertTrue(exception.getMessage().contains("retry") || exception.getMessage().contains("negative"),
                    "Should reject invalid retry attempts: " + retryAttempts);
            
            // Verify failure was logged
            verify(mockAuditLogger, atLeastOnce()).logConfigurationValidation(eq("security"), eq(false), anyString());
        }
    }
    
    @Test
    void property_invalidPermissionConfigurationIsRejected() {
        // Test invalid permission configurations
        int[] invalidNameLengths = {0, -1, 9, 256}; // Outside valid range 10-255
        
        for (int nameLength : invalidNameLengths) {
            reset(mockAuditLogger);
            SecurityConfigurationProperties invalidConfig = new SecurityConfigurationProperties(mockAuditLogger);
            invalidConfig.getPermission().setMaxPermissionNameLength(nameLength);
            
            Exception exception = assertThrows(Exception.class, () -> invalidConfig.validateConfiguration());
            assertTrue(exception.getMessage().contains("length") || exception.getMessage().contains("positive"),
                    "Should reject invalid permission name length: " + nameLength);
            
            // Verify failure was logged
            verify(mockAuditLogger, atLeastOnce()).logConfigurationValidation(eq("security"), eq(false), anyString());
        }
        
        // Test incompatible strategy and cache settings
        reset(mockAuditLogger);
        SecurityConfigurationProperties incompatibleConfig = new SecurityConfigurationProperties(mockAuditLogger);
        incompatibleConfig.getCache().setEnabled(false);
        incompatibleConfig.getPermission().setResolutionStrategy(SecurityConfigurationProperties.ResolutionStrategy.CACHE_ONLY);
        
        Exception exception = assertThrows(Exception.class, () -> incompatibleConfig.validateConfiguration());
        assertTrue(exception.getMessage().contains("CACHE_ONLY") && exception.getMessage().contains("disabled"),
                "Should reject CACHE_ONLY strategy when caching is disabled");
        
        // Verify failure was logged
        verify(mockAuditLogger, atLeastOnce()).logConfigurationValidation(eq("security"), eq(false), anyString());
    }
    
    @Test
    void property_permissionAndRoleNameValidationWorks() {
        SecurityConfigurationProperties config = new SecurityConfigurationProperties(mockAuditLogger);
        config.getPermission().setMaxPermissionNameLength(50);
        config.getPermission().setMaxRoleNameLength(50);
        
        // Test valid names
        assertTrue(config.isValidPermissionName("READ_USERS"), "Should accept valid permission name");
        assertTrue(config.isValidRoleName("ADMIN"), "Should accept valid role name");
        
        // Test invalid names - null or empty
        assertFalse(config.isValidPermissionName(null), "Should reject null permission name");
        assertFalse(config.isValidPermissionName(""), "Should reject empty permission name");
        assertFalse(config.isValidPermissionName("   "), "Should reject whitespace-only permission name");
        
        assertFalse(config.isValidRoleName(null), "Should reject null role name");
        assertFalse(config.isValidRoleName(""), "Should reject empty role name");
        assertFalse(config.isValidRoleName("   "), "Should reject whitespace-only role name");
        
        // Test invalid names - too long
        String longName = "A".repeat(51);
        assertFalse(config.isValidPermissionName(longName), "Should reject too long permission name");
        assertFalse(config.isValidRoleName(longName), "Should reject too long role name");
        
        // Test invalid names - containing control characters
        assertFalse(config.isValidPermissionName("PERMISSION\nWITH\nNEWLINES"), "Should reject permission name with newlines");
        assertFalse(config.isValidPermissionName("PERMISSION\rWITH\rCR"), "Should reject permission name with carriage returns");
        assertFalse(config.isValidPermissionName("PERMISSION\tWITH\tTABS"), "Should reject permission name with tabs");
        
        assertFalse(config.isValidRoleName("ROLE\nWITH\nNEWLINES"), "Should reject role name with newlines");
        assertFalse(config.isValidRoleName("ROLE\rWITH\rCR"), "Should reject role name with carriage returns");
        assertFalse(config.isValidRoleName("ROLE\tWITH\tTABS"), "Should reject role name with tabs");
    }
    
    @Test
    void property_configurationSummaryIsGenerated() {
        SecurityConfigurationProperties config = new SecurityConfigurationProperties(mockAuditLogger);
        
        String summary = config.getConfigurationSummary();
        
        assertNotNull(summary, "Configuration summary should not be null");
        assertFalse(summary.isEmpty(), "Configuration summary should not be empty");
        
        // Verify summary contains key configuration elements
        assertTrue(summary.contains("cache.enabled"), "Summary should contain cache enabled status");
        assertTrue(summary.contains("cache.timeout"), "Summary should contain cache timeout");
        assertTrue(summary.contains("cache.maxSize"), "Summary should contain cache max size");
        assertTrue(summary.contains("db.queryTimeout"), "Summary should contain database query timeout");
        assertTrue(summary.contains("db.retryAttempts"), "Summary should contain database retry attempts");
        assertTrue(summary.contains("permission.strategy"), "Summary should contain permission strategy");
        assertTrue(summary.contains("permission.strict"), "Summary should contain strict checking status");
    }
    
    @Test
    void property_restartRequirementIsDetectedCorrectly() {
        SecurityConfigurationProperties config1 = new SecurityConfigurationProperties(mockAuditLogger);
        SecurityConfigurationProperties config2 = new SecurityConfigurationProperties(mockAuditLogger);
        
        // Initially identical configurations should not require restart
        assertFalse(config1.requiresRestart(config2), "Identical configurations should not require restart");
        
        // Change cache enabled status - should require restart
        config2.getCache().setEnabled(false);
        assertTrue(config1.requiresRestart(config2), "Changing cache enabled status should require restart");
        
        // Reset and change database connection pooling - should require restart
        config2.getCache().setEnabled(true);
        config2.getDatabase().setConnectionPoolingEnabled(false);
        assertTrue(config1.requiresRestart(config2), "Changing connection pooling should require restart");
        
        // Reset and change permission resolution strategy - should require restart
        config2.getDatabase().setConnectionPoolingEnabled(true);
        config2.getPermission().setResolutionStrategy(SecurityConfigurationProperties.ResolutionStrategy.CACHE_FIRST);
        assertTrue(config1.requiresRestart(config2), "Changing resolution strategy should require restart");
        
        // Reset and change non-critical setting - should not require restart
        config2.getPermission().setResolutionStrategy(SecurityConfigurationProperties.ResolutionStrategy.DATABASE_FIRST);
        config2.getCache().setSessionTimeoutMinutes(60);
        assertFalse(config1.requiresRestart(config2), "Changing session timeout should not require restart");
        
        // Null comparison should require restart
        assertTrue(config1.requiresRestart(null), "Null configuration should require restart");
    }
    
    @Test
    void property_allResolutionStrategiesAreValidated() {
        SecurityConfigurationProperties.ResolutionStrategy[] strategies = 
                SecurityConfigurationProperties.ResolutionStrategy.values();
        
        for (SecurityConfigurationProperties.ResolutionStrategy strategy : strategies) {
            SecurityConfigurationProperties config = new SecurityConfigurationProperties(mockAuditLogger);
            config.getPermission().setResolutionStrategy(strategy);
            
            if (strategy == SecurityConfigurationProperties.ResolutionStrategy.CACHE_ONLY) {
                // CACHE_ONLY requires caching to be enabled
                config.getCache().setEnabled(true);
                assertDoesNotThrow(() -> config.validateConfiguration(), 
                        "CACHE_ONLY strategy should be valid when caching is enabled");
                
                config.getCache().setEnabled(false);
                assertThrows(Exception.class, () -> config.validateConfiguration(),
                        "CACHE_ONLY strategy should be invalid when caching is disabled");
            } else {
                // Other strategies should work regardless of cache setting
                config.getCache().setEnabled(true);
                assertDoesNotThrow(() -> config.validateConfiguration(), 
                        strategy + " strategy should be valid when caching is enabled");
                
                config.getCache().setEnabled(false);
                assertDoesNotThrow(() -> config.validateConfiguration(), 
                        strategy + " strategy should be valid when caching is disabled");
            }
        }
    }
}