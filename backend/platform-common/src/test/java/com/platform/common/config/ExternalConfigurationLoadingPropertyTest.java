package com.platform.common.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-Based Test for External Configuration Loading
 * 
 * **Feature: technical-debt-remediation, Property 12: External Configuration Loading**
 * 
 * **Validates: Requirements 6.1**
 * 
 * Tests that for any application startup, all configuration settings should be 
 * loaded from external configuration files or environment variables rather than 
 * hardcoded values.
 * 
 * @author Platform Team
 * @version 1.0
 */
class ExternalConfigurationLoadingPropertyTest {
    
    private Validator validator;
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        environment = new MockEnvironment();
    }
    
    /**
     * Property 12: External Configuration Loading
     * 
     * For any application startup, all configuration settings should be loaded 
     * from external configuration files or environment variables rather than 
     * hardcoded values.
     */
    @Property(tries = 100)
    @Label("Property 12: External Configuration Loading - All configuration should come from external sources")
    void externalConfigurationLoadingProperty(
            @ForAll("validDatabaseConfigs") Map<String, String> databaseConfig,
            @ForAll("validSecurityConfigs") Map<String, String> securityConfig,
            @ForAll("validApiConfigs") Map<String, String> apiConfig) {
        
        // Initialize environment and validator for this property test
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        environment = new MockEnvironment();
        
        // Given: External configuration properties are set
        setEnvironmentProperties(databaseConfig);
        setEnvironmentProperties(securityConfig);
        setEnvironmentProperties(apiConfig);
        
        // When: Configuration is loaded from external sources
        ApplicationConfiguration appConfig = loadConfigurationFromEnvironment();
        
        // Then: All configuration values should be loaded from external sources
        assertThat(appConfig).isNotNull();
        
        // Verify database configuration is loaded externally
        DatabaseConfig dbConfig = appConfig.getDatabase();
        assertThat(dbConfig.getUrl()).isEqualTo(databaseConfig.get("app.database.url"));
        assertThat(dbConfig.getUsername()).isEqualTo(databaseConfig.get("app.database.username"));
        assertThat(dbConfig.getMaxConnections()).isEqualTo(Integer.parseInt(databaseConfig.get("app.database.max-connections")));
        
        // Verify security configuration is loaded externally
        SecurityConfig secConfig = appConfig.getSecurity();
        assertThat(secConfig.getPasswordMinLength()).isEqualTo(Integer.parseInt(securityConfig.get("app.security.password-min-length")));
        assertThat(secConfig.getMaxFailedAttempts()).isEqualTo(Integer.parseInt(securityConfig.get("app.security.max-failed-attempts")));
        
        // Verify API configuration is loaded externally
        ApiConfig apiConf = appConfig.getApi();
        assertThat(apiConf.getWorkflowEngineUrl()).isEqualTo(apiConfig.get("app.api.workflow-engine-url"));
        assertThat(apiConf.getRequestTimeoutMs()).isEqualTo(Long.parseLong(apiConfig.get("app.api.request-timeout-ms")));
        
        // Verify configuration is valid
        Set<ConstraintViolation<ApplicationConfiguration>> violations = validator.validate(appConfig);
        assertThat(violations).isEmpty();
    }
    
    /**
     * Property: Configuration Loading Consistency
     * 
     * For any valid external configuration, loading it multiple times should 
     * produce identical configuration objects.
     */
    @Property(tries = 50)
    @Label("Configuration loading should be consistent across multiple loads")
    void configurationLoadingConsistencyProperty(
            @ForAll("validCompleteConfigs") Map<String, String> externalConfig) {
        
        // Initialize environment and validator for this property test
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        environment = new MockEnvironment();
        
        // Given: External configuration is set
        setEnvironmentProperties(externalConfig);
        
        // When: Configuration is loaded multiple times
        ApplicationConfiguration config1 = loadConfigurationFromEnvironment();
        ApplicationConfiguration config2 = loadConfigurationFromEnvironment();
        
        // Then: Both configurations should be identical
        assertThat(config1.getDatabase().getUrl()).isEqualTo(config2.getDatabase().getUrl());
        assertThat(config1.getSecurity().getPasswordMinLength()).isEqualTo(config2.getSecurity().getPasswordMinLength());
        assertThat(config1.getApi().getWorkflowEngineUrl()).isEqualTo(config2.getApi().getWorkflowEngineUrl());
        
        // Both should be valid
        Set<ConstraintViolation<ApplicationConfiguration>> violations1 = validator.validate(config1);
        Set<ConstraintViolation<ApplicationConfiguration>> violations2 = validator.validate(config2);
        assertThat(violations1).isEmpty();
        assertThat(violations2).isEmpty();
    }
    
    /**
     * Property: Environment Variable Override
     * 
     * For any configuration property, environment variables should override 
     * default values when present.
     */
    @Property(tries = 50)
    @Label("Environment variables should override default configuration values")
    void environmentVariableOverrideProperty(
            @ForAll("validDatabaseUrls") String customDatabaseUrl,
            @ForAll @IntRange(min = 1, max = 100) int customMaxConnections,
            @ForAll @IntRange(min = 8, max = 64) int customPasswordMinLength) {
        
        // Initialize environment and validator for this property test
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        environment = new MockEnvironment();
        
        // Given: Environment variables are set to override defaults
        environment.setProperty("app.database.url", customDatabaseUrl);
        environment.setProperty("app.database.max-connections", String.valueOf(customMaxConnections));
        environment.setProperty("app.security.password-min-length", String.valueOf(customPasswordMinLength));
        
        // When: Configuration is loaded
        ApplicationConfiguration config = loadConfigurationFromEnvironment();
        
        // Then: Environment variables should override defaults
        assertThat(config.getDatabase().getUrl()).isEqualTo(customDatabaseUrl);
        assertThat(config.getDatabase().getMaxConnections()).isEqualTo(customMaxConnections);
        assertThat(config.getSecurity().getPasswordMinLength()).isEqualTo(customPasswordMinLength);
        
        // Configuration should remain valid
        Set<ConstraintViolation<ApplicationConfiguration>> violations = validator.validate(config);
        assertThat(violations).isEmpty();
    }
    
    /**
     * Property: Configuration Validation
     * 
     * For any invalid external configuration, loading should fail with 
     * appropriate validation errors.
     */
    @Property(tries = 30)
    @Label("Invalid external configuration should fail validation")
    void configurationValidationProperty(
            @ForAll("invalidConfigs") Map<String, String> invalidConfig) {
        
        // Initialize environment and validator for this property test
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        environment = new MockEnvironment();
        
        // Given: Invalid external configuration is set
        setEnvironmentProperties(invalidConfig);
        
        // When: Configuration is loaded
        ApplicationConfiguration config = loadConfigurationFromEnvironment();
        
        // Then: Validation should fail
        Set<ConstraintViolation<ApplicationConfiguration>> violations = validator.validate(config);
        assertThat(violations).isNotEmpty();
        
        // Violations should contain meaningful error messages
        for (ConstraintViolation<ApplicationConfiguration> violation : violations) {
            assertThat(violation.getMessage()).isNotBlank();
            assertThat(violation.getPropertyPath()).isNotNull();
        }
    }
    
    @Test
    void externalConfigurationLoadingUnitTest() {
        // Initialize environment and validator for this unit test
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        environment = new MockEnvironment();
        
        // Given: Specific external configuration
        environment.setProperty("app.database.url", "jdbc:postgresql://external-db:5432/test_db");
        environment.setProperty("app.database.username", "external_user");
        environment.setProperty("app.database.max-connections", "50");
        environment.setProperty("app.security.password-min-length", "12");
        environment.setProperty("app.api.workflow-engine-url", "http://external-workflow:8081");
        
        // When: Configuration is loaded
        ApplicationConfiguration config = loadConfigurationFromEnvironment();
        
        // Then: Values should come from external sources
        assertThat(config.getDatabase().getUrl()).isEqualTo("jdbc:postgresql://external-db:5432/test_db");
        assertThat(config.getDatabase().getUsername()).isEqualTo("external_user");
        assertThat(config.getDatabase().getMaxConnections()).isEqualTo(50);
        assertThat(config.getSecurity().getPasswordMinLength()).isEqualTo(12);
        assertThat(config.getApi().getWorkflowEngineUrl()).isEqualTo("http://external-workflow:8081");
        
        // Configuration should be valid
        Set<ConstraintViolation<ApplicationConfiguration>> violations = validator.validate(config);
        assertThat(violations).isEmpty();
    }
    
    // Generators for property-based tests
    
    @Provide
    Arbitrary<Map<String, String>> validDatabaseConfigs() {
        return Combinators.combine(
                validDatabaseUrls(),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
                Arbitraries.integers().between(1, 100)
        ).as((url, username, maxConn) -> {
            Map<String, String> config = new HashMap<>();
            config.put("app.database.url", url);
            config.put("app.database.username", username);
            config.put("app.database.max-connections", String.valueOf(maxConn));
            return config;
        });
    }
    
    @Provide
    Arbitrary<Map<String, String>> validSecurityConfigs() {
        return Combinators.combine(
                Arbitraries.integers().between(4, 64),
                Arbitraries.integers().between(1, 20)
        ).as((minLength, maxAttempts) -> {
            Map<String, String> config = new HashMap<>();
            config.put("app.security.password-min-length", String.valueOf(minLength));
            config.put("app.security.max-failed-attempts", String.valueOf(maxAttempts));
            return config;
        });
    }
    
    @Provide
    Arbitrary<Map<String, String>> validApiConfigs() {
        return Combinators.combine(
                validServiceUrls(),
                Arbitraries.longs().between(1000L, 300000L)
        ).as((url, timeout) -> {
            Map<String, String> config = new HashMap<>();
            config.put("app.api.workflow-engine-url", url);
            config.put("app.api.request-timeout-ms", String.valueOf(timeout));
            return config;
        });
    }
    
    @Provide
    Arbitrary<Map<String, String>> validCompleteConfigs() {
        return Combinators.combine(
                validDatabaseConfigs(),
                validSecurityConfigs(),
                validApiConfigs()
        ).as((db, sec, api) -> {
            Map<String, String> config = new HashMap<>();
            config.putAll(db);
            config.putAll(sec);
            config.putAll(api);
            return config;
        });
    }
    
    @Provide
    Arbitrary<Map<String, String>> invalidConfigs() {
        return Arbitraries.oneOf(
                // Invalid database configuration
                Arbitraries.just(Map.of(
                        "app.database.url", "invalid-url",
                        "app.database.max-connections", "-1"
                )),
                // Invalid security configuration
                Arbitraries.just(Map.of(
                        "app.security.password-min-length", "0",
                        "app.security.max-failed-attempts", "0"
                )),
                // Invalid API configuration
                Arbitraries.just(Map.of(
                        "app.api.workflow-engine-url", "",
                        "app.api.request-timeout-ms", "0"
                ))
        );
    }
    
    @Provide
    Arbitrary<String> validDatabaseUrls() {
        return Arbitraries.oneOf(
                Arbitraries.just("jdbc:postgresql://localhost:5432/test_db"),
                Arbitraries.just("jdbc:postgresql://db-server:5432/workflow_platform"),
                Arbitraries.just("jdbc:postgresql://external-db:5432/platform_test")
        );
    }
    
    @Provide
    Arbitrary<String> validServiceUrls() {
        return Arbitraries.oneOf(
                Arbitraries.just("http://localhost:8081"),
                Arbitraries.just("http://workflow-engine:8081"),
                Arbitraries.just("http://external-service:9090")
        );
    }
    
    // Helper methods
    
    private void setEnvironmentProperties(Map<String, String> properties) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            environment.setProperty(entry.getKey(), entry.getValue());
        }
    }
    
    private ApplicationConfiguration loadConfigurationFromEnvironment() {
        // Create configuration property source from environment
        Map<String, Object> source = new HashMap<>();
        
        // Get all properties from MockEnvironment
        // MockEnvironment doesn't have getPropertyNames(), so we'll use a different approach
        String[] commonProperties = {
                "app.database.url", "app.database.username", "app.database.max-connections",
                "app.security.password-min-length", "app.security.max-failed-attempts",
                "app.api.workflow-engine-url", "app.api.request-timeout-ms"
        };
        
        for (String propertyName : commonProperties) {
            String value = environment.getProperty(propertyName);
            if (value != null) {
                source.put(propertyName, value);
            }
        }
        
        ConfigurationPropertySource propertySource = new MapConfigurationPropertySource(source);
        Binder binder = new Binder(propertySource);
        
        // Bind to ApplicationConfiguration
        ApplicationConfiguration config = binder.bind("app", ApplicationConfiguration.class)
                .orElse(new ApplicationConfiguration());
        
        return config;
    }
}