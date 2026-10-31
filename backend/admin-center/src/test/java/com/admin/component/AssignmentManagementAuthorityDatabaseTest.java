package com.admin.component;

import com.platform.common.jdbc.SqlIdentifiers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.Properties;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentManagementAuthorityDatabaseTest {
    JdbcTemplate admin,jdbc;
    AssignmentManagementAuthority authority;
    String schema;
    @BeforeAll void open() {
        String url=System.getenv("ASSIGNMENT_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/assignment_p0")) throw new IllegalArgumentException("Owned PostgreSQL fixture required");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",""));
        schema=SqlIdentifiers.requireIdentifier("assignment_management_"+UUID.randomUUID().toString().replace("-",""));
        admin.execute("CREATE SCHEMA "+schema);
        var source=new DriverManagerDataSource(url,"postgres",""); var properties=new Properties();
        properties.setProperty("currentSchema",schema); source.setConnectionProperties(properties); jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_roles(id text,code text,type text,status text)");
        jdbc.execute("CREATE TABLE sys_user_roles(user_id text,role_id text,valid_from timestamp,valid_to timestamp)");
        jdbc.execute("CREATE TABLE sys_role_assignments(role_id text,target_type text,target_id text,valid_from timestamp,valid_to timestamp)");
        jdbc.execute("CREATE TABLE sys_virtual_groups(id text,status text,code text)");
        jdbc.execute("CREATE TABLE sys_virtual_group_members(group_id text,user_id text)");
        jdbc.execute("CREATE TABLE sys_virtual_group_roles(virtual_group_id text,role_id text)");
        jdbc.execute("CREATE TABLE sys_user_business_unit_roles(user_id text,role_id text,business_unit_id text)");
        jdbc.execute("CREATE TABLE sys_business_units(id text,code text,status text)");
        authority=new AssignmentManagementAuthority(jdbc);
    }
    @BeforeEach void clear() {
        for(String table:java.util.List.of("sys_roles","sys_user_roles","sys_role_assignments","sys_virtual_groups",
                "sys_virtual_group_members","sys_virtual_group_roles","sys_user_business_unit_roles","sys_business_units")) jdbc.update("DELETE FROM "+SqlIdentifiers.requireIdentifier(table));
        jdbc.update("INSERT INTO sys_roles VALUES ('r','SYS_ADMIN','ADMIN','ACTIVE')");
    }
    @AfterAll void close() { if(admin!=null && schema!=null) admin.execute("DROP SCHEMA "+SqlIdentifiers.requireIdentifier(schema)+" CASCADE"); }
    @Test void alreadyExpiredLegacyRoleIsNotAnAdministratorAlthoughLegacyProfileStillFindsIt() {
        jdbc.update("INSERT INTO sys_user_roles VALUES ('u','r',NULL,clock_timestamp()-interval '1 second')");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM sys_user_roles WHERE user_id='u'",Integer.class));
        assertFalse(authority.systemAdministrator("u"));
    }
    @Test void grantsUseHalfOpenValidityForBothAssociationFormats() {
        jdbc.update("INSERT INTO sys_user_roles VALUES ('u','r',clock_timestamp()+interval '1 hour',NULL)");
        jdbc.update("INSERT INTO sys_role_assignments VALUES ('r','USER','u',NULL,clock_timestamp()-interval '1 second')");
        assertFalse(authority.systemAdministrator("u"));
        jdbc.update("UPDATE sys_role_assignments SET valid_to=clock_timestamp()+interval '1 hour'");
        assertTrue(authority.systemAdministrator("u"));
        assertFalse(authority.systemAdministrator("other"));
    }
    @Test void naturalExpiryRevokesWithoutAnyDatabaseWrite() throws Exception {
        jdbc.update("INSERT INTO sys_user_roles VALUES ('u','r',NULL,clock_timestamp()+interval '2 seconds')");
        assertTrue(authority.systemAdministrator("u"));
        Thread.sleep(2100);
        assertFalse(authority.systemAdministrator("u"));
    }
    @Test void groupGrantsRequireAnActiveGroupForLegacyAndUnifiedSources() {
        jdbc.update("INSERT INTO sys_virtual_groups VALUES ('g','INACTIVE','GROUP')");
        jdbc.update("INSERT INTO sys_virtual_group_members VALUES ('g','u')");
        jdbc.update("INSERT INTO sys_virtual_group_roles VALUES ('g','r')");
        jdbc.update("INSERT INTO sys_role_assignments VALUES ('r','VIRTUAL_GROUP','g',NULL,NULL)");
        assertFalse(authority.systemAdministrator("u"));
        jdbc.update("UPDATE sys_virtual_groups SET status='ACTIVE'");
        assertTrue(authority.systemAdministrator("u"));
        jdbc.update("DELETE FROM sys_virtual_group_roles");
        assertTrue(authority.systemAdministrator("u"));
        jdbc.update("UPDATE sys_roles SET status='INACTIVE'");
        assertFalse(authority.systemAdministrator("u"));
    }
    @Test void timelessDirectAndBusinessUnitAssignmentsRetainTheirExistingMeaning() {
        jdbc.update("INSERT INTO sys_user_roles VALUES ('u','r',NULL,NULL)");
        assertTrue(authority.systemAdministrator("u"));
        jdbc.update("DELETE FROM sys_user_roles");
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u','r','bu')");
        assertTrue(authority.systemAdministrator("u"));
        jdbc.update("UPDATE sys_roles SET type='BU_BOUNDED'");
        assertFalse(authority.systemAdministrator("u"));
    }
    @Test void disabledBusinessUnitOrRoleEmptiesProtectedPoolWhileMembershipRemains() {
        jdbc.update("UPDATE sys_roles SET code='REVIEW',type='BU_BOUNDED'");
        jdbc.update("INSERT INTO sys_business_units VALUES ('bu','BU','ACTIVE')");
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u','r','bu')");
        var candidates=new AssignmentActiveCandidates(jdbc);
        var request=new AssignmentActiveCandidates.Request("BU_ROLE","BU","REVIEW",null);
        assertEquals(java.util.List.of("u"),candidates.resolve(request));
        jdbc.update("UPDATE sys_business_units SET status='INACTIVE'");
        assertTrue(candidates.resolve(request).isEmpty());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM sys_user_business_unit_roles",Integer.class));
        jdbc.update("UPDATE sys_business_units SET status='ACTIVE'");
        jdbc.update("UPDATE sys_roles SET status='INACTIVE'");
        assertTrue(candidates.resolve(request).isEmpty());
    }
    @Test void disabledGroupEmptiesBothProtectedGroupAndUnboundedRoleSources() {
        jdbc.update("UPDATE sys_roles SET code='REVIEW',type='BU_UNBOUNDED'");
        jdbc.update("INSERT INTO sys_virtual_groups VALUES ('g','ACTIVE','GROUP')");
        jdbc.update("INSERT INTO sys_virtual_group_members VALUES ('g','u')");
        jdbc.update("INSERT INTO sys_virtual_group_roles VALUES ('g','r')");
        var candidates=new AssignmentActiveCandidates(jdbc);
        var group=new AssignmentActiveCandidates.Request("VIRTUAL_GROUP",null,null,"GROUP");
        var role=new AssignmentActiveCandidates.Request("ROLE",null,"REVIEW",null);
        assertEquals(java.util.List.of("u"),candidates.resolve(group));
        assertEquals(java.util.List.of("u"),candidates.resolve(role));
        jdbc.update("UPDATE sys_virtual_groups SET status='INACTIVE'");
        assertTrue(candidates.resolve(group).isEmpty()); assertTrue(candidates.resolve(role).isEmpty());
        jdbc.update("UPDATE sys_virtual_groups SET status='ACTIVE'");
        jdbc.update("UPDATE sys_roles SET status='INACTIVE'");
        assertEquals(java.util.List.of("u"),candidates.resolve(group));
        assertTrue(candidates.resolve(role).isEmpty());
    }
}
