package com.workflow.assignment;

import com.platform.common.jdbc.SqlIdentifiers;
import com.platform.common.jdbc.PostgresTransactionLock;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Properties;
import java.util.UUID;

/** P0 runs in an owned schema, never in the application's runtime schema. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AssignmentPostgresFixture {
    JdbcTemplate admin;
    JdbcTemplate jdbc;
    DriverManagerDataSource source;
    TransactionTemplate transaction;
    ProcessEngine engine;
    String schema;

    @BeforeAll
    void openDatabase() throws java.sql.SQLException {
        String url = System.getenv("ASSIGNMENT_TEST_JDBC_URL");
        if (url == null || !url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/assignment_p0")) {
            throw new IllegalArgumentException("P0 requires a dedicated local assignment_p0 database");
        }
        var root = new DriverManagerDataSource(url, "postgres", "");
        admin = new JdbcTemplate(root);
        schema = SqlIdentifiers.requireIdentifier("assignment_p0_" + UUID.randomUUID().toString().replace("-", ""));
        admin.execute("CREATE SCHEMA " + schema);
        source = new DriverManagerDataSource(url, "postgres", "");
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setConnectionProperties(properties);
        jdbc = new JdbcTemplate(source);
        var script = new FileSystemResource(java.nio.file.Path.of("../../deploy/init-scripts/00-schema/100-assignment-exclusion.sql")
                .toAbsolutePath().normalize());
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, script);
            ScriptUtils.executeSqlScript(connection, script);
            ScriptUtils.executeSqlScript(connection,new FileSystemResource("../../deploy/init-scripts/00-schema/106-assignment-definition-retention.sql"));
        }
        jdbc.execute("CREATE TABLE p0_case(id text PRIMARY KEY, revision bigint NOT NULL)");
        jdbc.execute("CREATE TABLE p0_responsibility(task_id text PRIMARY KEY, actor text NOT NULL)");
        jdbc.execute("CREATE TABLE p0_saved(id text PRIMARY KEY, value text NOT NULL)");
        jdbc.execute("CREATE TABLE p0_operation(id text PRIMARY KEY, task_id text UNIQUE NOT NULL, digest text NOT NULL)");
        jdbc.execute("CREATE TABLE p0_sync(id text PRIMARY KEY, digest text NOT NULL)");
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setTimeout(10);
    }

    void openEngine() {
        var configuration = new SpringProcessEngineConfiguration();
        configuration.setDataSource(source);
        configuration.setTransactionManager(transaction.getTransactionManager());
        configuration.setDatabaseSchema(schema);
        configuration.setDatabaseSchemaUpdate("true");
        configuration.setAsyncExecutorActivate(false);
        configuration.setDisableIdmEngine(true);
        configuration.setDisableEventRegistry(true);
        configuration.setBeans(engineBeans());
        configureEngine(configuration);
        engine = configuration.buildProcessEngine();
    }

    void configureEngine(SpringProcessEngineConfiguration configuration) { }

    java.util.Map<Object,Object> engineBeans() {
        return java.util.Map.of("fuCallRows", new com.workflow.delegate.CallActivityRows());
    }

    void lockCase(String id) {
        PostgresTransactionLock.exclusive(jdbc, "assignment", id);
    }

    @AfterAll
    void closeDatabase() {
        if (engine != null) engine.close();
        if (admin != null && schema != null) {
            admin.execute("DROP SCHEMA " + SqlIdentifiers.requireIdentifier(schema) + " CASCADE");
        }
    }
}
