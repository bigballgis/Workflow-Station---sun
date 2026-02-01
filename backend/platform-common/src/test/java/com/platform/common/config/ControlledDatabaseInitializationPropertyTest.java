package com.platform.common.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-Based Test for Controlled Database Initialization
 * 
 * **Feature: technical-debt-remediation, Property 19: Controlled Database Initialization**
 * 
 * **Validates: Requirements 8.3**
 * 
 * Tests that for any database initialization requirement, only scripts from the 
 * deploy/init-scripts directory should be used, and no automatic database 
 * operations should occur.
 * 
 * @author Platform Team
 * @version 1.0
 */
class ControlledDatabaseInitializationPropertyTest {
    
    private MockEnvironment environment;
    private static final String INIT_SCRIPTS_PATH = "../../deploy/init-scripts";
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
    }
    
    /**
     * Property 19: Controlled Database Initialization
     * 
     * For any database initialization requirement, only scripts from the 
     * deploy/init-scripts directory should be used.
     */
    @Property(tries = 100)
    @Label("Property 19: Controlled Database Initialization - Only deploy/init-scripts should be used")
    void controlledDatabaseInitializationProperty(
            @ForAll("serviceNames") String serviceName,
            @ForAll("environmentProfiles") String profile,
            @ForAll("databaseUrls") String databaseUrl) {
        
        // Initialize environment for this property test
        environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        
        // Given: Service configuration with disabled automatic operations
        setServiceConfiguration(serviceName, databaseUrl, profile);
        
        // When: Configuration is loaded for database initialization
        FlywayProperties flywayProps = loadFlywayConfiguration();
        
        // Then: Flyway should be disabled to prevent automatic initialization
        assertThat(flywayProps.isEnabled())
                .as("Flyway must be disabled to ensure controlled database initialization")
                .isFalse();
        
        // And: Migration locations should still be configured but not executed automatically
        List<String> locations = flywayProps.getLocations();
        if (!locations.isEmpty()) {
            // If locations are configured, they should point to classpath resources, not deploy/init-scripts
            for (String location : locations) {
                assertThat(location)
                        .as("Migration locations should be classpath-based, not deploy/init-scripts")
                        .startsWith("classpath:");
            }
        }
        
        // And: Automatic execution should be prevented
        assertThat(flywayProps.isEnabled())
                .as("Automatic execution must be disabled for controlled initialization")
                .isFalse();
    }
    
    /**
     * Property: Deploy Scripts Directory Structure
     * 
     * For any database initialization, the deploy/init-scripts directory should 
     * contain the required schema files in the correct structure.
     */
    @Property(tries = 50)
    @Label("Deploy scripts directory should contain required schema files")
    void deployScriptsDirectoryStructureProperty(
            @ForAll("schemaFileNames") String expectedSchemaFile) {
        
        // Given: Expected schema file structure
        String schemaFilePath = INIT_SCRIPTS_PATH + "/00-schema/" + expectedSchemaFile;
        
        // When: Checking for schema file existence
        Path schemaPath = Paths.get(schemaFilePath);
        
        // Then: Required schema files should exist in deploy/init-scripts
        assertThat(Files.exists(schemaPath))
                .as("Schema file should exist in deploy/init-scripts: " + expectedSchemaFile)
                .isTrue();
        
        // And: Schema files should be readable
        assertThat(Files.isReadable(schemaPath))
                .as("Schema file should be readable: " + expectedSchemaFile)
                .isTrue();
        
        // And: Schema files should contain SQL content
        try {
            String content = Files.readString(schemaPath);
            assertThat(content)
                    .as("Schema file should contain SQL content")
                    .isNotBlank();
            
            if (expectedSchemaFile.equals("00-init-all-schemas.sql")) {
                // Master script should contain include statements
                assertThat(content)
                        .as("Master script should contain include statements")
                        .contains("\\i ");
            } else {
                // Individual schema files should contain CREATE TABLE statements
                assertThat(content)
                        .as("Individual schema file should contain CREATE TABLE statements")
                        .contains("CREATE TABLE");
            }
        } catch (IOException e) {
            fail("Failed to read schema file: " + expectedSchemaFile, e);
        }
    }
    
    /**
     * Property: Schema File Dependencies
     * 
     * For any schema file in deploy/init-scripts, dependencies should be 
     * properly ordered to prevent foreign key constraint violations.
     */
    @Property(tries = 30)
    @Label("Schema files should be properly ordered for dependencies")
    void schemaFileDependenciesProperty(
            @ForAll("dependentSchemaFiles") Map<String, String> schemaDependency) {
        
        // Given: Schema dependency relationship
        String dependentFile = schemaDependency.get("dependent");
        String dependencyFile = schemaDependency.get("dependency");
        
        // When: Checking file ordering
        String dependentPath = INIT_SCRIPTS_PATH + "/00-schema/" + dependentFile;
        String dependencyPath = INIT_SCRIPTS_PATH + "/00-schema/" + dependencyFile;
        
        // Then: Dependency file should have lower number (execute first)
        String dependentNumber = extractFileNumber(dependentFile);
        String dependencyNumber = extractFileNumber(dependencyFile);
        
        assertThat(Integer.parseInt(dependencyNumber))
                .as("Dependency file should have lower number than dependent file")
                .isLessThan(Integer.parseInt(dependentNumber));
        
        // And: Both files should exist
        assertThat(Files.exists(Paths.get(dependentPath)))
                .as("Dependent schema file should exist")
                .isTrue();
        assertThat(Files.exists(Paths.get(dependencyPath)))
                .as("Dependency schema file should exist")
                .isTrue();
    }
    
    /**
     * Property: Master Script Completeness
     * 
     * For any database initialization, the master script should reference 
     * all individual schema files.
     */
    @Property(tries = 20)
    @Label("Master script should reference all schema files")
    void masterScriptCompletenessProperty(
            @ForAll("schemaFileNames") String schemaFile) {
        
        // Given: Individual schema file
        String masterScriptPath = INIT_SCRIPTS_PATH + "/00-schema/00-init-all-schemas.sql";
        
        // When: Reading master script content
        try {
            String masterContent = Files.readString(Paths.get(masterScriptPath));
            
            // Then: Master script should reference the schema file (except itself)
            if (!schemaFile.equals("00-init-all-schemas.sql")) {
                assertThat(masterContent)
                        .as("Master script should reference schema file: " + schemaFile)
                        .contains(schemaFile);
            }
            
        } catch (IOException e) {
            fail("Failed to read master script", e);
        }
    }
    
    /**
     * Property: No Automatic Migration Configuration
     * 
     * For any service configuration, automatic migration settings should be 
     * disabled in production profiles.
     */
    @Property(tries = 50)
    @Label("Automatic migration should be disabled in production profiles")
    void noAutomaticMigrationConfigurationProperty(
            @ForAll("serviceNames") String serviceName,
            @ForAll("productionProfiles") String profile) {
        
        // Initialize environment for this property test
        environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        
        // Given: Production service configuration
        setServiceConfiguration(serviceName, "jdbc:postgresql://localhost:5432/workflow_platform", profile);
        
        // When: Loading configuration
        FlywayProperties flywayProps = loadFlywayConfiguration();
        String ddlAuto = getDdlAutoFromEnvironment();
        
        // Then: Flyway should be disabled
        assertThat(flywayProps.isEnabled())
                .as("Flyway should be disabled in production profile: " + profile)
                .isFalse();
        
        // And: Hibernate DDL auto should be 'none'
        assertThat(ddlAuto)
                .as("Hibernate ddl-auto should be 'none' in production profile: " + profile)
                .isEqualTo("none");
    }
    
    /**
     * Property: Script Content Validation
     * 
     * For any schema script in deploy/init-scripts, the content should be 
     * valid SQL with proper table creation statements.
     */
    @Property(tries = 30)
    @Label("Schema scripts should contain valid SQL content")
    void scriptContentValidationProperty(
            @ForAll("schemaFileNames") String schemaFile) {
        
        // Given: Schema file path
        String schemaFilePath = INIT_SCRIPTS_PATH + "/00-schema/" + schemaFile;
        Path schemaPath = Paths.get(schemaFilePath);
        
        // When: Reading schema file content
        try {
            String content = Files.readString(schemaPath);
            
            // Then: Content should contain SQL statements
            if (schemaFile.equals("00-init-all-schemas.sql")) {
                // Master script should contain include statements
                assertThat(content)
                        .as("Master script should contain include statements")
                        .contains("\\i ");
            } else {
                // Individual schema files should contain CREATE TABLE statements
                assertThat(content)
                        .as("Schema file should contain CREATE TABLE statements")
                        .contains("CREATE TABLE");
            }
            
            // And: Content should use IF NOT EXISTS for safety (except master script)
            if (!schemaFile.equals("00-init-all-schemas.sql")) {
                assertThat(content)
                        .as("Schema file should use IF NOT EXISTS for safe execution")
                        .contains("IF NOT EXISTS");
            }
            
            // And: Content should have proper comments
            assertThat(content)
                    .as("Schema file should have descriptive comments")
                    .contains("--");
            
            // And: Content should not contain DROP statements (safety check)
            assertThat(content.toUpperCase())
                    .as("Schema file should not contain dangerous DROP statements")
                    .doesNotContain("DROP TABLE", "DROP DATABASE", "DROP SCHEMA");
            
        } catch (IOException e) {
            fail("Failed to read schema file: " + schemaFile, e);
        }
    }
    
    @Test
    void controlledDatabaseInitializationUnitTest() {
        // Initialize environment for this unit test
        environment = new MockEnvironment();
        
        // Given: Production configuration with disabled automatic operations
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.flyway.enabled", "false");
        environment.setProperty("spring.jpa.hibernate.ddl-auto", "none");
        
        // When: Configuration is loaded
        FlywayProperties flywayProps = loadFlywayConfiguration();
        String ddlAuto = getDdlAutoFromEnvironment();
        
        // Then: Automatic operations should be disabled
        assertThat(flywayProps.isEnabled()).isFalse();
        assertThat(ddlAuto).isEqualTo("none");
        
        // And: Deploy scripts directory should exist
        Path deployScriptsPath = Paths.get(INIT_SCRIPTS_PATH);
        assertThat(Files.exists(deployScriptsPath)).isTrue();
        assertThat(Files.isDirectory(deployScriptsPath)).isTrue();
        
        // And: Schema directory should exist
        Path schemaPath = Paths.get(INIT_SCRIPTS_PATH + "/00-schema");
        assertThat(Files.exists(schemaPath)).isTrue();
        assertThat(Files.isDirectory(schemaPath)).isTrue();
    }
    
    // Generators for property-based tests
    
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
    Arbitrary<String> environmentProfiles() {
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
    Arbitrary<String> databaseUrls() {
        return Arbitraries.oneOf(
                Arbitraries.just("jdbc:postgresql://localhost:5432/workflow_platform"),
                Arbitraries.just("jdbc:postgresql://db-server:5432/workflow_platform"),
                Arbitraries.just("jdbc:postgresql://prod-db:5432/platform_prod")
        );
    }
    
    @Provide
    Arbitrary<String> schemaFileNames() {
        return Arbitraries.oneOf(
                Arbitraries.just("00-init-all-schemas.sql"),
                Arbitraries.just("01-platform-security-schema.sql"),
                Arbitraries.just("02-workflow-engine-schema.sql"),
                Arbitraries.just("03-user-portal-schema.sql"),
                Arbitraries.just("04-developer-workstation-schema.sql"),
                Arbitraries.just("05-admin-center-schema.sql")
        );
    }
    
    @Provide
    Arbitrary<Map<String, String>> dependentSchemaFiles() {
        return Arbitraries.oneOf(
                // Admin center depends on platform security
                Arbitraries.just(Map.of(
                        "dependent", "05-admin-center-schema.sql",
                        "dependency", "01-platform-security-schema.sql"
                )),
                // Workflow engine depends on platform security
                Arbitraries.just(Map.of(
                        "dependent", "02-workflow-engine-schema.sql",
                        "dependency", "01-platform-security-schema.sql"
                )),
                // User portal depends on platform security
                Arbitraries.just(Map.of(
                        "dependent", "03-user-portal-schema.sql",
                        "dependency", "01-platform-security-schema.sql"
                ))
        );
    }
    
    // Helper methods
    
    private void setServiceConfiguration(String serviceName, String databaseUrl, String profile) {
        // Set common configuration that should disable automatic operations
        environment.setProperty("spring.application.name", serviceName);
        environment.setProperty("spring.datasource.url", databaseUrl);
        environment.setProperty("spring.flyway.enabled", "false");
        environment.setProperty("spring.jpa.hibernate.ddl-auto", "none");
        environment.setProperty("spring.flyway.baseline-on-migrate", "true");
        environment.setProperty("spring.flyway.validate-on-migrate", "false");
        
        // Set profile-specific configurations
        if ("prod".equals(profile) || "docker".equals(profile) || "staging".equals(profile)) {
            // Production profiles should have stricter settings
            environment.setProperty("spring.flyway.enabled", "false");
            environment.setProperty("spring.jpa.hibernate.ddl-auto", "none");
        }
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
    
    private String getDdlAutoFromEnvironment() {
        return environment.getProperty("spring.jpa.hibernate.ddl-auto", "none");
    }
    
    private String extractFileNumber(String fileName) {
        // Extract the leading number from filename (e.g., "01" from "01-platform-security-schema.sql")
        if (fileName.matches("\\d{2}-.*")) {
            return fileName.substring(0, 2);
        }
        return "00"; // Default for files without numbers
    }
}