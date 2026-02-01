package com.platform.common.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-Based Test for Configuration Validation
 * 
 * **Feature: technical-debt-remediation, Property 14: Configuration Validation**
 * 
 * **Validates: Requirements 6.4**
 * 
 * Tests that for any configuration values at startup, invalid configurations 
 * should be detected and reported with clear error messages.
 * 
 * @author Platform Team
 * @version 1.0
 */
class ConfigurationValidationPropertyTest {
    
    private Validator validator;
    private ConfigurationValidator configurationValidator;
    
    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        configurationValidator = new ConfigurationValidator(validator);
    }
    
    /**
     * Property 14: Configuration Validation
     * 
     * For any configuration values at startup, invalid configurations should be 
     * detected and reported with clear error messages.
     */
    @Property(tries = 100)
    @Label("Property 14: Configuration Validation - Invalid configurations should be detected with clear error messages")
    void configurationValidationProperty(
            @ForAll("invalidDatabaseConfigs") DatabaseConfig invalidDbConfig,
            @ForAll("invalidSecurityConfigs") SecurityConfig invalidSecConfig,
            @ForAll("invalidApiConfigs") ApiConfig invalidApiConfig) {
        
        // Initialize validator for this property test
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        configurationValidator = new ConfigurationValidator(validator);
        
        // When: Invalid configurations are validated
        ConfigurationValidationResult dbResult = configurationValidator.validate(invalidDbConfig);
        ConfigurationValidationResult secResult = configurationValidator.validate(invalidSecConfig);
        ConfigurationValidationResult apiResult = configurationValidator.validate(invalidApiConfig);
        
        // Then: Validation should fail with clear error messages
        assertThat(dbResult.isValid()).isFalse();
        assertThat(secResult.isValid()).isFalse();
        assertThat(apiResult.isValid()).isFalse();
        
        // Errors should contain meaningful messages
        assertThat(dbResult.getErrors()).isNotEmpty();
        assertThat(secResult.getErrors()).isNotEmpty();
        assertThat(apiResult.getErrors()).isNotEmpty();
        
        // Each error should have a property path and message
        for (ConfigurationValidationError error : dbResult.getErrors()) {
            assertThat(error.getPropertyPath()).isNotBlank();
            assertThat(error.getMessage()).isNotBlank();
        }
        
        for (ConfigurationValidationError error : secResult.getErrors()) {
            assertThat(error.getPropertyPath()).isNotBlank();
            assertThat(error.getMessage()).isNotBlank();
        }
        
        for (ConfigurationValidationError error : apiResult.getErrors()) {
            assertThat(error.getPropertyPath()).isNotBlank();
            assertThat(error.getMessage()).isNotBlank();
        }
    }
    
    /**
     * Property: Valid Configuration Acceptance
     * 
     * For any valid configuration, validation should pass without errors.
     */
    @Property(tries = 50)
    @Label("Valid configurations should pass validation without errors")
    void validConfigurationAcceptanceProperty(
            @ForAll("validDatabaseConfigs") DatabaseConfig validDbConfig,
            @ForAll("validSecurityConfigs") SecurityConfig validSecConfig,
            @ForAll("validApiConfigs") ApiConfig validApiConfig) {
        
        // Initialize validator for this property test
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        configurationValidator = new ConfigurationValidator(validator);
        
        // When: Valid configurations are validated
        ConfigurationValidationResult dbResult = configurationValidator.validate(validDbConfig);
        ConfigurationValidationResult secResult = configurationValidator.validate(validSecConfig);
        ConfigurationValidationResult apiResult = configurationValidator.validate(validApiConfig);
        
        // Then: Validation should pass
        assertThat(dbResult.isValid()).isTrue();
        assertThat(secResult.isValid()).isTrue();
        assertThat(apiResult.isValid()).isTrue();
        
        // Should have no errors
        assertThat(dbResult.getErrors()).isEmpty();
        assertThat(secResult.getErrors()).isEmpty();
        assertThat(apiResult.getErrors()).isEmpty();
    }
    
    /**
     * Property: Configuration Validation Consistency
     * 
     * For any configuration, validating it multiple times should produce 
     * consistent results.
     */
    @Property(tries = 30)
    @Label("Configuration validation should be consistent across multiple validations")
    void configurationValidationConsistencyProperty(
            @ForAll("mixedValidityConfigs") DatabaseConfig config) {
        
        // Initialize validator for this property test
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        configurationValidator = new ConfigurationValidator(validator);
        
        // When: Configuration is validated multiple times
        ConfigurationValidationResult result1 = configurationValidator.validate(config);
        ConfigurationValidationResult result2 = configurationValidator.validate(config);
        
        // Then: Results should be consistent
        assertThat(result1.isValid()).isEqualTo(result2.isValid());
        assertThat(result1.getErrorCount()).isEqualTo(result2.getErrorCount());
        assertThat(result1.getWarningCount()).isEqualTo(result2.getWarningCount());
    }
    
    /**
     * Property: Startup Validation Exception Handling
     * 
     * For any invalid configuration at startup, validation should throw 
     * ConfigurationException with detailed error information.
     */
    @Property(tries = 30)
    @Label("Startup validation should throw ConfigurationException for invalid configurations")
    void startupValidationExceptionProperty(
            @ForAll("criticallyInvalidConfigs") SecurityConfig invalidConfig) {
        
        // Initialize validator for this property test
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        configurationValidator = new ConfigurationValidator(validator);
        
        // When/Then: Startup validation should throw exception for invalid config
        assertThatThrownBy(() -> configurationValidator.validateAtStartup(invalidConfig))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Configuration validation failed at startup")
                .hasMessageContaining("SecurityConfig");
    }
    
    @Test
    void configurationValidationUnitTest() {
        // Initialize validator for this unit test
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        configurationValidator = new ConfigurationValidator(validator);
        
        // Given: Invalid database configuration
        DatabaseConfig invalidConfig = new DatabaseConfig();
        invalidConfig.setUrl("invalid-url"); // Should start with jdbc:
        invalidConfig.setMaxConnections(-1); // Should be positive
        invalidConfig.setUsername(""); // Should not be blank
        
        // When: Configuration is validated
        ConfigurationValidationResult result = configurationValidator.validate(invalidConfig);
        
        // Then: Validation should fail with specific errors
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSizeGreaterThan(0);
        
        // Should have specific error messages
        boolean hasUrlError = result.getErrors().stream()
                .anyMatch(error -> error.getPropertyPath().contains("url"));
        boolean hasMaxConnectionsError = result.getErrors().stream()
                .anyMatch(error -> error.getPropertyPath().contains("maxConnections"));
        boolean hasUsernameError = result.getErrors().stream()
                .anyMatch(error -> error.getPropertyPath().contains("username"));
        
        assertThat(hasUrlError || hasMaxConnectionsError || hasUsernameError).isTrue();
    }
    
    // Generators for property-based tests
    
    @Provide
    Arbitrary<DatabaseConfig> invalidDatabaseConfigs() {
        return Arbitraries.oneOf(
                // Invalid URL
                Arbitraries.just(createDatabaseConfig("invalid-url", "user", 10)),
                // Negative max connections
                Arbitraries.just(createDatabaseConfig("jdbc:postgresql://localhost:5432/db", "user", -1)),
                // Empty username
                Arbitraries.just(createDatabaseConfig("jdbc:postgresql://localhost:5432/db", "", 10)),
                // Max connections less than min idle
                Arbitraries.just(createDatabaseConfigWithIdle("jdbc:postgresql://localhost:5432/db", "user", 5, 10))
        );
    }
    
    @Provide
    Arbitrary<SecurityConfig> invalidSecurityConfigs() {
        return Arbitraries.oneOf(
                // Password max length less than min length
                Arbitraries.just(createSecurityConfig(16, 8, 5)),
                // Negative max failed attempts
                Arbitraries.just(createSecurityConfig(8, 16, -1)),
                // Password min length too small
                Arbitraries.just(createSecurityConfig(2, 16, 5)),
                // Default JWT secret in production
                Arbitraries.just(createSecurityConfigWithJwt("default-jwt-secret-key-change-in-production"))
        );
    }
    
    @Provide
    Arbitrary<ApiConfig> invalidApiConfigs() {
        return Arbitraries.oneOf(
                // Invalid service URL
                Arbitraries.just(createApiConfig("invalid-url", 30000L)),
                // Zero timeout
                Arbitraries.just(createApiConfig("http://localhost:8081", 0L)),
                // Negative timeout
                Arbitraries.just(createApiConfig("http://localhost:8081", -1000L))
        );
    }
    
    @Provide
    Arbitrary<DatabaseConfig> validDatabaseConfigs() {
        return Combinators.combine(
                Arbitraries.oneOf(
                        Arbitraries.just("jdbc:postgresql://localhost:5432/db"), 
                        Arbitraries.just("jdbc:mysql://localhost:3306/db")
                ),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
                Arbitraries.integers().between(1, 100)
        ).as(this::createDatabaseConfig);
    }
    
    @Provide
    Arbitrary<SecurityConfig> validSecurityConfigs() {
        return Combinators.combine(
                Arbitraries.integers().between(8, 32),
                Arbitraries.integers().between(32, 128),
                Arbitraries.integers().between(1, 10)
        ).as(this::createSecurityConfig);
    }
    
    @Provide
    Arbitrary<ApiConfig> validApiConfigs() {
        return Combinators.combine(
                Arbitraries.oneOf(
                        Arbitraries.just("http://localhost:8081"), 
                        Arbitraries.just("https://api.example.com")
                ),
                Arbitraries.longs().between(1000L, 300000L)
        ).as(this::createApiConfig);
    }
    
    @Provide
    Arbitrary<DatabaseConfig> mixedValidityConfigs() {
        return Arbitraries.oneOf(
                validDatabaseConfigs(),
                invalidDatabaseConfigs()
        );
    }
    
    @Provide
    Arbitrary<SecurityConfig> criticallyInvalidConfigs() {
        return Arbitraries.oneOf(
                // Null required fields
                Arbitraries.just(createSecurityConfigWithNulls()),
                // Extremely invalid values
                Arbitraries.just(createSecurityConfig(-10, -20, -5))
        );
    }
    
    // Helper methods to create configuration objects
    
    private DatabaseConfig createDatabaseConfig(String url, String username, int maxConnections) {
        DatabaseConfig config = new DatabaseConfig();
        config.setUrl(url);
        config.setUsername(username);
        config.setMaxConnections(maxConnections);
        config.setPassword("password");
        config.setDriverClassName("org.postgresql.Driver");
        return config;
    }
    
    private DatabaseConfig createDatabaseConfigWithIdle(String url, String username, int maxConnections, int minIdle) {
        DatabaseConfig config = createDatabaseConfig(url, username, maxConnections);
        config.setMinIdleConnections(minIdle);
        return config;
    }
    
    private SecurityConfig createSecurityConfig(int minLength, int maxLength, int maxFailedAttempts) {
        SecurityConfig config = new SecurityConfig();
        config.setPasswordMinLength(minLength);
        config.setPasswordMaxLength(maxLength);
        config.setMaxFailedAttempts(maxFailedAttempts);
        config.setJwtSecretKey("valid-jwt-secret-key");
        return config;
    }
    
    private SecurityConfig createSecurityConfigWithJwt(String jwtSecret) {
        SecurityConfig config = new SecurityConfig();
        config.setPasswordMinLength(8);
        config.setPasswordMaxLength(128);
        config.setMaxFailedAttempts(5);
        config.setJwtSecretKey(jwtSecret);
        return config;
    }
    
    private SecurityConfig createSecurityConfigWithNulls() {
        SecurityConfig config = new SecurityConfig();
        // Leave required fields as null/default to trigger validation errors
        config.setPasswordMinLength(8);
        config.setPasswordMaxLength(128);
        config.setMaxFailedAttempts(5);
        // Don't set JWT secret key - will use default which should fail validation
        return config;
    }
    
    private ApiConfig createApiConfig(String workflowEngineUrl, long requestTimeout) {
        ApiConfig config = new ApiConfig();
        config.setWorkflowEngineUrl(workflowEngineUrl);
        config.setRequestTimeoutMs(requestTimeout);
        config.setUserServiceUrl("http://localhost:8082");
        config.setNotificationServiceUrl("http://localhost:8083");
        return config;
    }
}