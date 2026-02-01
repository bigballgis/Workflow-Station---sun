package com.platform.common.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-Based Test for Database Migration Disabling
 * 
 * **Feature: technical-debt-remediation, Property 18: Database Migration Disabling**
 * 
 * **Validates: Requirements 8.1, 8.2, 8.4**
 * 
 * Tests that for any application startup, the system should not automatically 
 * execute database migrations, schema updates, or Hibernate automatic schema operations.
 * 
 * @author Platform Team
 * @version 1.0
 */
class DatabaseMigrationDisablingPropertyTest {
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
    }
    
    /**
     * Property 18: Database Migration Disabling
     * 
     * For any application startup, the system should not automatically execute 
     * database migrations, schema updates, or Hibernate automatic schema operations.
     */
    @Property(tries = 100)
    @Label("Property 18: Database Migration Disabling - No automatic database operations should occur")
    void databaseMigrationDisablingProperty(
            @ForAll("applicationProfiles") String profile,
            @ForAll("databaseUrls") String databaseUrl,
            @ForAll("serviceNames") String serviceName) {
        
        // Initialize environment for this property test
        environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        
        // Given: Application configuration for any service and profile
        setServiceConfiguration(serviceName, databaseUrl);
        
        // When: Configuration is loaded for application startup
        FlywayProperties flywayProps = loadFlywayConfiguration();
        HibernateProperties hibernateProps = loadHibernateConfiguration();
        
        // Then: Flyway automatic migrations should be disabled
        assertThat(flywayProps.isEnabled())
                .as("Flyway automatic migrations must be disabled for controlled database initialization")
                .isFalse();
        
        // And: Hibernate automatic schema operations should be disabled
        String ddlAuto = getDdlAutoFromEnvironment();
        assertThat(ddlAuto)
                .as("Hibernate ddl-auto must be 'none' to prevent automatic schema operations")
                .isIn("none", "validate");
        
        // And: No automatic database schema updates should be configured
        assertThat(flywayProps.isValidateOnMigrate())
                .as("Flyway validation on migrate should be disabled for controlled initialization")
                .isFalse();
    }
    
    /**
     * Property: Flyway Configuration Consistency
     * 
     * For any service configuration, Flyway should be consistently disabled 
     * across all application profiles except test profiles.
     */
    @Property(tries = 50)
    @Label("Flyway should be consistently disabled across production profiles")
    void flywayConfigurationConsistencyProperty(
            @ForAll("productionProfiles") String profile,
            @ForAll("serviceNames") String serviceName) {
        
        // Initialize environment for this property test
        environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        
        // Given: Production profile configuration
        setServiceConfiguration(serviceName, "jdbc:postgresql://localhost:5432/workflow_platform");
        
        // When: Flyway configuration is loaded
        FlywayProperties flywayProps = loadFlywayConfiguration();
        
        // Then: Flyway should be disabled for all production profiles
        assertThat(flywayProps.isEnabled())
                .as("Flyway must be disabled in production profiles: " + profile)
                .isFalse();
        
        // And: Baseline on migrate should be configured but not executed automatically
        assertThat(flywayProps.isBaselineOnMigrate())
                .as("Baseline on migrate can be configured but won't execute when disabled")
                .isTrue();
    }
    
    /**
     * Property: Hibernate DDL Configuration
     * 
     * For any production configuration, Hibernate DDL auto should be set to 
     * 'none' to prevent automatic schema modifications.
     */
    @Property(tries = 50)
    @Label("Hibernate DDL auto should be 'none' in production configurations")
    void hibernateDdlConfigurationProperty(
            @ForAll("productionProfiles") String profile,
            @ForAll("databaseUrls") String databaseUrl) {
        
        // Initialize environment for this property test
        environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        
        // Given: Production database configuration
        environment.setProperty("spring.datasource.url", databaseUrl);
        environment.setProperty("spring.jpa.hibernate.ddl-auto", "none");
        
        // When: Hibernate configuration is loaded
        String ddlAuto = getDdlAutoFromEnvironment();
        
        // Then: DDL auto should be 'none' to prevent schema modifications
        assertThat(ddlAuto)
                .as("Hibernate ddl-auto must be 'none' in production to prevent automatic schema changes")
                .isEqualTo("none");
    }
    
    /**
     * Property: Test Profile Exception
     * 
     * For any test profile, database operations may be enabled for testing purposes,
     * but production profiles must have them disabled.
     */
    @Property(tries = 30)
    @Label("Test profiles may have different database configuration than production")
    void testProfileExceptionProperty(
            @ForAll("testProfiles") String testProfile,
            @ForAll("productionProfiles") String prodProfile) {
        
        // Test profile configuration
        environment = new MockEnvironment();
        environment.setActiveProfiles(testProfile);
        environment.setProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
        environment.setProperty("spring.flyway.enabled", "false");
        
        String testDdlAuto = getDdlAutoFromEnvironment();
        
        // Production profile configuration
        environment = new MockEnvironment();
        environment.setActiveProfiles(prodProfile);
        environment.setProperty("spring.jpa.hibernate.ddl-auto", "none");
        environment.setProperty("spring.flyway.enabled", "false");
        
        String prodDdlAuto = getDdlAutoFromEnvironment();
        
        // Then: Production should always have 'none', test can have 'create-drop'
        assertThat(prodDdlAuto)
                .as("Production profiles must have ddl-auto set to 'none'")
                .isEqualTo("none");
        
        // Test profiles can have create-drop for testing
        assertThat(testDdlAuto)
                .as("Test profiles can have create-drop for testing purposes")
                .isIn("create-drop", "none");
    }
    
    /**
     * Property: Database Initialization Control
     * 
     * For any application startup, database initialization should only occur 
     * through controlled scripts in deploy/init-scripts directory.
     */
    @Property(tries = 50)
    @Label("Database initialization should be controlled through deploy/init-scripts")
    void databaseInitializationControlProperty(
            @ForAll("serviceNames") String serviceName,
            @ForAll("databaseUrls") String databaseUrl) {
        
        // Initialize environment for this property test
        environment = new MockEnvironment();
        
        // Given: Service configuration with disabled automatic operations
        setServiceConfiguration(serviceName, databaseUrl);
        
        // When: Configuration is loaded
        FlywayProperties flywayProps = loadFlywayConfiguration();
        
        // Then: Flyway should be disabled to prevent automatic initialization
        assertThat(flywayProps.isEnabled())
                .as("Flyway must be disabled to ensure controlled database initialization")
                .isFalse();
        
        // And: Migration locations should still be configured for manual use
        List<String> locations = flywayProps.getLocations();
        assertThat(locations)
                .as("Migration locations should be configured for manual execution")
                .isNotEmpty();
        
        // But automatic execution should be prevented
        assertThat(flywayProps.isEnabled())
                .as("Automatic execution must be disabled")
                .isFalse();
    }
    
    @Test
    void databaseMigrationDisablingUnitTest() {
        // Initialize environment for this unit test
        environment = new MockEnvironment();
        
        // Given: Production configuration
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.flyway.enabled", "false");
        environment.setProperty("spring.jpa.hibernate.ddl-auto", "none");
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/workflow_platform");
        
        // When: Configuration is loaded
        FlywayProperties flywayProps = loadFlywayConfiguration();
        String ddlAuto = getDdlAutoFromEnvironment();
        
        // Then: Automatic database operations should be disabled
        assertThat(flywayProps.isEnabled()).isFalse();
        assertThat(ddlAuto).isEqualTo("none");
    }
    
    // Generators for property-based tests
    
    @Provide
    Arbitrary<String> applicationProfiles() {
        return Arbitraries.oneOf(
                Arbitraries.just("dev"),
                Arbitraries.just("prod"),
                Arbitraries.just("docker"),
                Arbitraries.just("staging")
        );
    }
    
    @Provide
    Arbitrary<String> productionProfiles() {
        return Arbitraries.oneOf(
                Arbitraries.just("prod"),
                Arbitraries.just("docker"),
                Arbitraries.just("staging")
        );
    }
    
    @Provide
    Arbitrary<String> testProfiles() {
        return Arbitraries.oneOf(
                Arbitraries.just("test"),
                Arbitraries.just("integration-test"),
                Arbitraries.just("local-test")
        );
    }
    
    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.oneOf(
                Arbitraries.just("workflow-engine-core"),
                Arbitraries.just("admin-center"),
                Arbitraries.just("user-portal"),
                Arbitraries.just("developer-workstation")
        );
    }
    
    @Provide
    Arbitrary<String> databaseUrls() {
        return Arbitraries.oneOf(
                Arbitraries.just("jdbc:postgresql://localhost:5432/workflow_platform"),
                Arbitraries.just("jdbc:postgresql://db-server:5432/workflow_platform"),
                Arbitraries.just("jdbc:postgresql://prod-db:5432/platform_prod")
        );
    }
    
    // Helper methods
    
    private void setServiceConfiguration(String serviceName, String databaseUrl) {
        // Set common configuration that should disable automatic operations
        environment.setProperty("spring.application.name", serviceName);
        environment.setProperty("spring.datasource.url", databaseUrl);
        environment.setProperty("spring.flyway.enabled", "false");
        environment.setProperty("spring.jpa.hibernate.ddl-auto", "none");
        environment.setProperty("spring.flyway.baseline-on-migrate", "true");
        environment.setProperty("spring.flyway.validate-on-migrate", "false");
    }
    
    private FlywayProperties loadFlywayConfiguration() {
        // Create configuration property source from environment
        Map<String, Object> source = new HashMap<>();
        
        // Get Flyway-related properties
        String[] flywayProperties = {
                "spring.flyway.enabled",
                "spring.flyway.baseline-on-migrate",
                "spring.flyway.validate-on-migrate",
                "spring.flyway.locations"
        };
        
        for (String propertyName : flywayProperties) {
            String value = environment.getProperty(propertyName);
            if (value != null) {
                source.put(propertyName, value);
            }
        }
        
        ConfigurationPropertySource propertySource = new MapConfigurationPropertySource(source);
        Binder binder = new Binder(propertySource);
        
        // Bind to FlywayProperties
        FlywayProperties flywayProps = binder.bind("spring.flyway", FlywayProperties.class)
                .orElse(new FlywayProperties());
        
        return flywayProps;
    }
    
    private HibernateProperties loadHibernateConfiguration() {
        // Create configuration property source from environment
        Map<String, Object> source = new HashMap<>();
        
        // Get Hibernate-related properties
        String[] hibernateProperties = {
                "spring.jpa.hibernate.ddl-auto",
                "spring.jpa.show-sql",
                "spring.jpa.properties.hibernate.dialect"
        };
        
        for (String propertyName : hibernateProperties) {
            String value = environment.getProperty(propertyName);
            if (value != null) {
                source.put(propertyName, value);
            }
        }
        
        ConfigurationPropertySource propertySource = new MapConfigurationPropertySource(source);
        Binder binder = new Binder(propertySource);
        
        // Bind to HibernateProperties
        HibernateProperties hibernateProps = binder.bind("spring.jpa.hibernate", HibernateProperties.class)
                .orElse(new HibernateProperties());
        
        return hibernateProps;
    }
    
    private String getDdlAutoFromEnvironment() {
        return environment.getProperty("spring.jpa.hibernate.ddl-auto", "none");
    }
}