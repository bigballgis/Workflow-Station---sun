package com.workflow.assignment;

import com.platform.common.assignment.AssignmentAuthorityFence;
import com.platform.common.jdbc.SqlIdentifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native SQL follows the same epoch protocol as ORM, batch imports and Portal-owned delegation changes. */
@EnabledIfEnvironmentVariable(named = "ASSIGNMENT_TEST_JDBC_URL", matches = ".+")
class AssignmentAuthorityHooksDatabaseTest extends AssignmentPostgresFixture {
    final List<String> otherSources = List.of("sys_roles","sys_business_units","sys_user_roles","sys_role_assignments",
            "sys_business_unit_roles","sys_user_business_units","sys_user_business_unit_roles","sys_virtual_groups",
            "sys_virtual_group_members","sys_virtual_group_roles","sys_permissions","sys_role_permissions",
            "admin_permission_delegations","up_delegation_rule");

    @BeforeAll void installHooks() throws Exception {
        jdbc.execute("CREATE TABLE sys_users(id text PRIMARY KEY,username text,status text,deleted boolean,"
                + "entity_manager_id text,function_manager_id text,locked_until timestamptz,last_login_at timestamptz,display_name text)");
        for (String table : otherSources) jdbc.execute("CREATE TABLE " + SqlIdentifiers.requireIdentifier(table) + "(id text PRIMARY KEY)");
        String sql = Files.readString(Path.of("../../deploy/init-scripts/00-schema/101-assignment-authority-change-hooks.sql"));
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
            statement.execute(sql);
        }
    }

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM sys_users");
        for (String table : otherSources) jdbc.execute("DELETE FROM " + SqlIdentifiers.requireIdentifier(table));
        jdbc.update("UPDATE ac_assignment_authority_fence SET revision=0 WHERE scope='platform'");
    }

    @Test void everyDeclaredQualificationSourcePublishesItsNativeWrites() {
        long previous = revision();
        for (String table : otherSources) {
            jdbc.update("INSERT INTO " + SqlIdentifiers.requireIdentifier(table) + "(id) VALUES(?)", "synthetic");
            assertEquals(++previous, revision(), table);
        }
    }

    @Test void loginAndDisplayChangesDoNotInvalidateAllTaskDecisions() {
        jdbc.update("INSERT INTO sys_users(id,username,status,deleted) VALUES('actor','exact','ACTIVE',false)");
        long before = revision();
        jdbc.update("UPDATE sys_users SET last_login_at=now(),display_name='new label',username=username,status=status WHERE id='actor'");
        assertEquals(before, revision());
        jdbc.update("UPDATE sys_users SET status='INACTIVE' WHERE id='actor'");
        assertEquals(before + 1, revision());
    }

    @Test void oneTransactionWithManyQualificationChangesPublishesOnce() {
        transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO sys_users(id,username,status,deleted) VALUES('a','a','ACTIVE',false),('b','b','ACTIVE',false)");
            jdbc.update("INSERT INTO sys_user_roles(id) VALUES('role-link')");
            jdbc.update("UPDATE sys_users SET status='INACTIVE'");
            jdbc.update("INSERT INTO up_delegation_rule(id) VALUES('delegation')");
        });
        assertEquals(1, revision());
    }

    @Test void failedQualificationMutationDoesNotPublishACommittedVersion() {
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO up_delegation_rule(id) VALUES('delegation')");
            throw new IllegalStateException("synthetic delegation failure");
        }));
        assertEquals(0, revision());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM up_delegation_rule", Integer.class));
    }

    private long revision() { return new AssignmentAuthorityFence(jdbc).revision(); }
}
