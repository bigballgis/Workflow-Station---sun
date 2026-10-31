package com.admin.component;

import com.platform.common.jdbc.SqlIdentifiers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import java.util.Properties;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentAuthorityTimeWindowDatabaseTest {
    JdbcTemplate admin,jdbc;
    AssignmentAuthorityTimeWindow windows;
    String schema;
    @BeforeAll void open() throws Exception {
        String url=System.getenv("ASSIGNMENT_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/assignment_p0")) throw new IllegalArgumentException("Owned PostgreSQL fixture required");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));
        schema=SqlIdentifiers.requireIdentifier("assignment_time_"+UUID.randomUUID().toString().replace("-",""));
        admin.execute("CREATE SCHEMA "+schema);
        var source=new DriverManagerDataSource(url,"postgres",""); var properties=new Properties();
        properties.setProperty("currentSchema",schema); source.setConnectionProperties(properties); jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_role_assignments(valid_from timestamp,valid_to timestamp)");
        jdbc.execute("CREATE TABLE sys_user_roles(valid_from timestamp,valid_to timestamp)");
        try(var connection=source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource("../../deploy/init-scripts/00-schema/107-assignment-authority-time-boundaries.sql"));
        }
        windows=new AssignmentAuthorityTimeWindow(jdbc);
    }
    @BeforeEach void clear() { jdbc.update("DELETE FROM sys_role_assignments"); jdbc.update("DELETE FROM sys_user_roles"); }
    @AfterAll void close() { if(admin!=null && schema!=null) admin.execute("DROP SCHEMA "+SqlIdentifiers.requireIdentifier(schema)+" CASCADE"); }
    @Test void noTemporalGrantsMeansNoPeriodicRoleRefreshDeadline() {
        var result=windows.read(); assertNotNull(result.observedAt()); assertNull(result.previousBoundary()); assertNull(result.nextBoundary());
    }
    @Test void nearestFutureAndPreviousBoundariesCoverBothRoleAssociationTables() {
        jdbc.update("INSERT INTO sys_role_assignments VALUES (clock_timestamp()-interval '1 minute',clock_timestamp()+interval '1 minute')");
        jdbc.update("INSERT INTO sys_user_roles VALUES (clock_timestamp()-interval '2 minutes',clock_timestamp()+interval '10 seconds')");
        var result=windows.read();
        assertEquals(jdbc.queryForObject("SELECT valid_from::timestamptz FROM sys_role_assignments",java.sql.Timestamp.class).toInstant(),result.previousBoundary());
        assertEquals(jdbc.queryForObject("SELECT valid_to::timestamptz FROM sys_user_roles",java.sql.Timestamp.class).toInstant(),result.nextBoundary());
    }
    @Test void crossingAnExpiryIsVisibleWithoutAnyWriteOrRevisionIncrement() throws Exception {
        jdbc.update("INSERT INTO sys_role_assignments(valid_to) VALUES (clock_timestamp()+interval '2 seconds')");
        var before=windows.read(); assertNotNull(before.nextBoundary()); assertNull(before.previousBoundary());
        Thread.sleep(2100);
        var after=windows.read(); assertNull(after.nextBoundary()); assertEquals(before.nextBoundary(),after.previousBoundary());
        assertTrue(after.observedAt().isAfter(after.previousBoundary()));
    }
}
