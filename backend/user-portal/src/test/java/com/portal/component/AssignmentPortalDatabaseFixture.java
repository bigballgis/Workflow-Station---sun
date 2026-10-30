package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.jdbc.SqlIdentifiers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Properties;
import java.util.UUID;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AssignmentPortalDatabaseFixture {
    final ObjectMapper json = new ObjectMapper();
    JdbcTemplate admin, jdbc;
    TransactionTemplate transaction;
    String schema;
    @org.junit.jupiter.api.BeforeEach void authenticateSyntheticActor() {
        var principal=com.platform.common.dto.UserPrincipal.builder().userId("actor").username("synthetic").build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,java.util.List.of()));
    }
    @org.junit.jupiter.api.AfterEach void clearSyntheticActor() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    @BeforeAll void database() throws Exception {
        String url = System.getenv("ASSIGNMENT_TEST_JDBC_URL");
        if (url == null || !url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/assignment_p0")) throw new IllegalArgumentException("Owned local test database required");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", ""));
        schema = SqlIdentifiers.requireIdentifier("assignment_portal_" + UUID.randomUUID().toString().replace("-", ""));
        admin.execute("CREATE SCHEMA " + schema);
        var source = new DriverManagerDataSource(url, "postgres", "");
        var properties = new Properties(); properties.setProperty("currentSchema", schema); source.setConnectionProperties(properties);
        jdbc = new JdbcTemplate(source);
        try (var connection = source.getConnection()) {
            var script = new org.springframework.core.io.FileSystemResource("../../deploy/init-scripts/00-schema/100-assignment-exclusion.sql");
            ScriptUtils.executeSqlScript(connection, script); ScriptUtils.executeSqlScript(connection, script);
        }
        jdbc.execute("CREATE TABLE up_process_instance(id varchar(64) PRIMARY KEY,process_definition_id varchar(255),variables jsonb,lock_version bigint,updated_at timestamptz)");
        String presence = java.nio.file.Files.readString(java.nio.file.Path.of("../../deploy/init-scripts/00-schema/102-assignment-portal-source-presence.sql"));
        jdbc.execute(presence); jdbc.execute(presence);
        String postAction=java.nio.file.Files.readString(java.nio.file.Path.of("../../deploy/init-scripts/00-schema/103-assignment-post-action-context.sql"));
        jdbc.execute(postAction); jdbc.execute(postAction);
        String observation=java.nio.file.Files.readString(java.nio.file.Path.of("../../deploy/init-scripts/00-schema/104-assignment-completion-observation.sql"));
        jdbc.execute(observation); jdbc.execute(observation);
        try (var connection=source.getConnection()) {
            for (String file:java.util.List.of("19-add-up-change-history.sql","99-up-change-history-batches.sql")) {
                ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource("../../deploy/init-scripts/00-schema/"+file));
            }
        }
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    @AfterAll void cleanup() {
        if (admin != null && schema != null) admin.execute("DROP SCHEMA " + SqlIdentifiers.requireIdentifier(schema) + " CASCADE");
    }
}
