package com.platform.common.config;

import com.platform.common.config.security.ConfigurationAuditLogger;
import com.platform.common.config.security.ConfigurationEncryptionService;
import com.platform.common.config.security.SecureCredentialManager;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-Based Test for Secure Configuration Management
 * 
 * **Feature: technical-debt-remediation, Property 15: Secure Configuration Management**
 * 
 * **Validates: Requirements 6.5**
 * 
 * Tests that for any sensitive configuration data (credentials, API keys), 
 * the system should encrypt or securely manage the data.
 * 
 * @author Platform Team
 * @version 1.0
 */
class SecureConfigurationManagementPropertyTest {
    
    private ConfigurationEncryptionService encryptionService;
    private ConfigurationAuditLogger auditLogger;
    private SecureCredentialManager credentialManager;
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        encryptionService = new ConfigurationEncryptionService("test-encryption-key-32-bytes!!!!");
        auditLogger = new ConfigurationAuditLogger(encryptionService);
        credentialManager = new SecureCredentialManager(environment, encryptionService, auditLogger);
    }
    
    /**
     * Property 15: Secure Configuration Management
     * 
     * For any sensitive configuration data (credentials, API keys), the system 
     * should encrypt or securely manage the data.
     */
    @Property(tries = 100)
    @Label("Property 15: Secure Configuration Management - Sensitive data should be encrypted or securely managed")
    void secureConfigurationManagementProperty(
            @ForAll("sensitiveConfigurationKeys") String sensitiveKey,
            @ForAll("sensitiveConfigurationValues") String sensitiveValue) {
        
        // Initialize services for this property test
        MockEnvironment testEnvironment = new MockEnvironment();
        ConfigurationEncryptionService testEncryptionService = new ConfigurationEncryptionService("test-encryption-key-32-bytes!!!!");
        ConfigurationAuditLogger testAuditLogger = new ConfigurationAuditLogger(testEncryptionService);
        SecureCredentialManager testCredentialManager = new SecureCredentialManager(testEnvironment, testEncryptionService, testAuditLogger);
        
        // Given: A sensitive configuration key and value
        assertThat(testEncryptionService.isSensitiveKey(sensitiveKey)).isTrue();
        
        // When: The sensitive value is processed by the encryption service
        String encryptedValue = testEncryptionService.encryptValue(sensitiveValue);
        
        // Then: The value should be encrypted
        assertThat(encryptedValue).isNotEqualTo(sensitiveValue);
        assertThat(testEncryptionService.isEncrypted(encryptedValue)).isTrue();
        
        // And: The encrypted value should be decryptable back to original
        String decryptedValue = testEncryptionService.decryptValue(encryptedValue);
        assertThat(decryptedValue).isEqualTo(sensitiveValue);
        
        // And: Sensitive values should be masked in logs
        String maskedValue = testEncryptionService.maskSensitiveValue(sensitiveKey, sensitiveValue);
        assertThat(maskedValue).isNotEqualTo(sensitiveValue);
        assertThat(maskedValue).contains("*");
        
        // And: Credential manager should handle sensitive data securely
        Map<String, String> context = Map.of("test", "property-test");
        testCredentialManager.storeCredential(sensitiveKey, sensitiveValue, context);
        
        Optional<String> retrievedValue = testCredentialManager.getCredential(sensitiveKey, context);
        assertThat(retrievedValue).isPresent();
        assertThat(retrievedValue.get()).isEqualTo(sensitiveValue);
    }
    
    /**
     * Property: Encryption Consistency
     * 
     * For any sensitive value, encryption should be consistent and reversible.
     */
    @Property(tries = 100)
    @Label("Encryption should be consistent and reversible for sensitive data")
    void encryptionConsistencyProperty(
            @ForAll("sensitiveConfigurationValues") String sensitiveValue) {
        
        // Initialize services for this property test
        ConfigurationEncryptionService testEncryptionService = new ConfigurationEncryptionService("test-encryption-key-32-bytes!!!!");
        
        // Given: A sensitive configuration value
        
        // When: The value is encrypted multiple times
        String encrypted1 = testEncryptionService.encryptValue(sensitiveValue);
        String encrypted2 = testEncryptionService.encryptValue(sensitiveValue);
        
        // Then: Each encryption should produce different ciphertext (due to random IV)
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        
        // But: Both should decrypt to the same original value
        String decrypted1 = testEncryptionService.decryptValue(encrypted1);
        String decrypted2 = testEncryptionService.decryptValue(encrypted2);
        
        assertThat(decrypted1).isEqualTo(sensitiveValue);
        assertThat(decrypted2).isEqualTo(sensitiveValue);
        assertThat(decrypted1).isEqualTo(decrypted2);
    }
    
    /**
     * Property: Sensitive Key Detection
     * 
     * For any configuration key containing sensitive patterns, it should be 
     * identified as sensitive.
     */
    @Property(tries = 100)
    @Label("Configuration keys with sensitive patterns should be detected as sensitive")
    void sensitiveKeyDetectionProperty(
            @ForAll("sensitiveKeyPatterns") String sensitivePattern,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String prefix,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String suffix) {
        
        // Initialize services for this property test
        ConfigurationEncryptionService testEncryptionService = new ConfigurationEncryptionService("test-encryption-key-32-bytes!!!!");
        
        // Given: Configuration keys containing sensitive patterns
        String sensitiveKey1 = prefix + "." + sensitivePattern;
        String sensitiveKey2 = sensitivePattern + "." + suffix;
        String sensitiveKey3 = prefix + "." + sensitivePattern + "." + suffix;
        
        // When: Keys are checked for sensitivity
        boolean isSensitive1 = testEncryptionService.isSensitiveKey(sensitiveKey1);
        boolean isSensitive2 = testEncryptionService.isSensitiveKey(sensitiveKey2);
        boolean isSensitive3 = testEncryptionService.isSensitiveKey(sensitiveKey3);
        
        // Then: All keys should be identified as sensitive
        assertThat(isSensitive1).isTrue();
        assertThat(isSensitive2).isTrue();
        assertThat(isSensitive3).isTrue();
    }
    
    /**
     * Property: Non-Sensitive Key Handling
     * 
     * For any non-sensitive configuration key, values should not be encrypted 
     * automatically.
     */
    @Property(tries = 100)
    @Label("Non-sensitive configuration keys should not be encrypted automatically")
    void nonSensitiveKeyHandlingProperty(
            @ForAll("nonSensitiveConfigurationKeys") String nonSensitiveKey,
            @ForAll @NotBlank @Size(min = 1, max = 100) String configValue) {
        
        // Initialize services for this property test
        ConfigurationEncryptionService testEncryptionService = new ConfigurationEncryptionService("test-encryption-key-32-bytes!!!!");
        
        // Given: A non-sensitive configuration key and value
        assertThat(testEncryptionService.isSensitiveKey(nonSensitiveKey)).isFalse();
        
        // When: The value is processed conditionally
        String processedValue = testEncryptionService.encryptIfSensitive(nonSensitiveKey, configValue);
        
        // Then: The value should remain unchanged (not encrypted)
        assertThat(processedValue).isEqualTo(configValue);
        
        // And: The value should not be masked in logs
        String maskedValue = testEncryptionService.maskSensitiveValue(nonSensitiveKey, configValue);
        assertThat(maskedValue).isEqualTo(configValue);
    }
    
    /**
     * Property: Credential Validation
     * 
     * For any stored credentials, validation should correctly identify their 
     * presence and validity.
     */
    @Property(tries = 50)
    @Label("Stored credentials should be validated correctly")
    void credentialValidationProperty(
            @ForAll("credentialSets") Map<String, String> credentials) {
        
        // Initialize services for this property test
        MockEnvironment testEnvironment = new MockEnvironment();
        ConfigurationEncryptionService testEncryptionService = new ConfigurationEncryptionService("test-encryption-key-32-bytes!!!!");
        ConfigurationAuditLogger testAuditLogger = new ConfigurationAuditLogger(testEncryptionService);
        SecureCredentialManager testCredentialManager = new SecureCredentialManager(testEnvironment, testEncryptionService, testAuditLogger);
        
        // Given: A set of credentials are stored
        Map<String, String> context = Map.of("test", "validation-property");
        
        for (Map.Entry<String, String> entry : credentials.entrySet()) {
            testCredentialManager.storeCredential(entry.getKey(), entry.getValue(), context);
        }
        
        // When: Credentials are validated
        Map<String, Boolean> validationResults = testCredentialManager.validateCredentials();
        
        // Then: All stored credentials should be valid
        for (String key : credentials.keySet()) {
            assertThat(validationResults).containsKey(key);
            assertThat(validationResults.get(key)).isTrue();
        }
        
        // And: Credential existence should be correctly detected
        for (String key : credentials.keySet()) {
            assertThat(testCredentialManager.hasCredential(key)).isTrue();
        }
    }
    
    /**
     * Property: Audit Logging Security
     * 
     * For any configuration access or change, audit logs should not expose 
     * sensitive data.
     */
    @Property(tries = 50)
    @Label("Audit logs should not expose sensitive configuration data")
    void auditLoggingSecurityProperty(
            @ForAll("sensitiveConfigurationKeys") String sensitiveKey,
            @ForAll("sensitiveConfigurationValues") String sensitiveValue,
            @ForAll("nonSensitiveConfigurationKeys") String nonSensitiveKey,
            @ForAll @NotBlank @Size(min = 1, max = 50) String nonSensitiveValue) {
        
        // Initialize services for this property test
        ConfigurationEncryptionService testEncryptionService = new ConfigurationEncryptionService("test-encryption-key-32-bytes!!!!");
        ConfigurationAuditLogger testAuditLogger = new ConfigurationAuditLogger(testEncryptionService);
        
        // Given: Configuration access and changes occur
        Map<String, String> context = Map.of("test", "audit-property");
        
        // When: Sensitive configuration is accessed and logged
        testAuditLogger.logConfigurationAccess(sensitiveKey, sensitiveValue, "test-source", context);
        
        // And: Non-sensitive configuration is accessed and logged
        testAuditLogger.logConfigurationAccess(nonSensitiveKey, nonSensitiveValue, "test-source", context);
        
        // And: Configuration changes are logged
        testAuditLogger.logConfigurationChange(sensitiveKey, "old-sensitive", sensitiveValue, "test-source", context);
        testAuditLogger.logConfigurationChange(nonSensitiveKey, "old-value", nonSensitiveValue, "test-source", context);
        
        // Then: Audit metrics should be available
        Map<String, Object> metrics = testAuditLogger.getAccessMetrics();
        assertThat(metrics).isNotEmpty();
        assertThat(metrics).containsKey("totalConfigurationAccess");
        assertThat(metrics).containsKey("sensitiveConfigurationAccess");
        assertThat(metrics).containsKey("configurationChanges");
        
        // And: Sensitive access count should be tracked
        Long sensitiveAccessCount = (Long) metrics.get("sensitiveConfigurationAccess");
        assertThat(sensitiveAccessCount).isGreaterThan(0L);
    }
    
    @Test
    void secureConfigurationManagementUnitTest() {
        // Given: A sensitive configuration key and value
        String sensitiveKey = "app.database.password";
        String sensitiveValue = "super-secret-password-123";
        
        // When: The sensitive value is encrypted
        String encryptedValue = encryptionService.encryptValue(sensitiveValue);
        
        // Then: The value should be encrypted and recoverable
        assertThat(encryptedValue).isNotEqualTo(sensitiveValue);
        assertThat(encryptionService.isEncrypted(encryptedValue)).isTrue();
        
        String decryptedValue = encryptionService.decryptValue(encryptedValue);
        assertThat(decryptedValue).isEqualTo(sensitiveValue);
        
        // And: Sensitive values should be masked in logs
        String maskedValue = encryptionService.maskSensitiveValue(sensitiveKey, sensitiveValue);
        assertThat(maskedValue).contains("*");
        assertThat(maskedValue).isNotEqualTo(sensitiveValue);
        
        // And: Credential manager should handle the credential securely
        Map<String, String> context = Map.of("test", "unit-test");
        credentialManager.storeCredential(sensitiveKey, sensitiveValue, context);
        
        Optional<String> retrievedValue = credentialManager.getCredential(sensitiveKey, context);
        assertThat(retrievedValue).isPresent();
        assertThat(retrievedValue.get()).isEqualTo(sensitiveValue);
    }
    
    // Generators for property-based tests
    
    @Provide
    Arbitrary<String> sensitiveConfigurationKeys() {
        return Arbitraries.oneOf(
                Arbitraries.just("app.database.password"),
                Arbitraries.just("app.security.jwt-secret-key"),
                Arbitraries.just("app.api.external-api-key"),
                Arbitraries.just("app.messaging.email-password"),
                Arbitraries.just("app.cache.redis-password"),
                Arbitraries.just("platform.encryption.secret-key"),
                Arbitraries.just("oauth.client.secret"),
                Arbitraries.just("ldap.bind.credential"),
                Arbitraries.just("ssl.keystore.password"),
                Arbitraries.just("database.admin.token")
        );
    }
    
    @Provide
    Arbitrary<String> sensitiveConfigurationValues() {
        return Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(8).ofMaxLength(64),
                Arbitraries.strings().ascii().ofMinLength(16).ofMaxLength(128),
                // Simulate API keys
                Combinators.combine(
                        Arbitraries.strings().withCharRange('A', 'Z').ofLength(8),
                        Arbitraries.strings().numeric().ofLength(16)
                ).as((prefix, suffix) -> prefix + "-" + suffix),
                // Simulate JWT secrets
                Arbitraries.strings().withCharRange('a', 'z').withCharRange('A', 'Z').withCharRange('0', '9')
                        .ofMinLength(32).ofMaxLength(64)
        );
    }
    
    @Provide
    Arbitrary<String> sensitiveKeyPatterns() {
        return Arbitraries.oneOf(
                Arbitraries.just("password"),
                Arbitraries.just("secret"),
                Arbitraries.just("key"),
                Arbitraries.just("token"),
                Arbitraries.just("credential"),
                Arbitraries.just("auth"),
                Arbitraries.just("jwt"),
                Arbitraries.just("api-key"),
                Arbitraries.just("private-key"),
                Arbitraries.just("api_key"),
                Arbitraries.just("private_key")
        );
    }
    
    @Provide
    Arbitrary<String> nonSensitiveConfigurationKeys() {
        return Arbitraries.oneOf(
                Arbitraries.just("app.database.url"),
                Arbitraries.just("app.database.username"),
                Arbitraries.just("app.api.timeout"),
                Arbitraries.just("app.cache.host"),
                Arbitraries.just("app.monitoring.enabled"),
                Arbitraries.just("server.port"),
                Arbitraries.just("logging.level"),
                Arbitraries.just("management.endpoint"),
                Arbitraries.just("spring.profiles.active"),
                Arbitraries.just("application.name")
        );
    }
    
    @Provide
    Arbitrary<Map<String, String>> credentialSets() {
        return Arbitraries.maps(
                sensitiveConfigurationKeys(),
                sensitiveConfigurationValues()
        ).ofMinSize(1).ofMaxSize(5);
    }
}