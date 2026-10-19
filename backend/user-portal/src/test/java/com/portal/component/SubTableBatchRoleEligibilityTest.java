package com.portal.component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTableBatchRoleEligibilityTest {
    private final EmbeddedDatabase db = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
    private final JdbcTemplate jdbc = spy(new JdbcTemplate(db));
    private final SubTableBatchRoleEligibility.Target target = new SubTableBatchRoleEligibility.Target("BU-CODE", "ROLE-CODE");
    SubTableBatchRoleEligibilityTest() {
        jdbc.execute("CREATE TABLE sys_business_units(id VARCHAR(100), code VARCHAR(100), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_roles(id VARCHAR(100), code VARCHAR(100), type VARCHAR(30), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_business_unit_roles(business_unit_id VARCHAR(100), role_id VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_user_business_unit_roles(user_id VARCHAR(100), business_unit_id VARCHAR(100), role_id VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_users(id VARCHAR(100), status VARCHAR(20), deleted BOOLEAN)");
        jdbc.update("INSERT INTO sys_business_units VALUES('bu-id', 'BU-CODE', 'ACTIVE')");
        jdbc.update("INSERT INTO sys_roles VALUES('role-id', 'ROLE-CODE', 'BU_BOUNDED', 'ACTIVE')");
        jdbc.update("INSERT INTO sys_business_unit_roles VALUES('bu-id', 'role-id')");
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES('person-id', 'bu-id', 'role-id')");
        jdbc.update("INSERT INTO sys_users VALUES('person-id', 'ACTIVE', false)");
        clearInvocations(jdbc);
    }
    @AfterEach void close() { db.shutdown(); }
    @Test void exactCodesAndOneActiveMemberAreEligibleWithoutConvertingTheTarget() {
        assertThatCode(() -> check(List.of(target, target), Map.of())).doesNotThrowAnyException();
        assertThat(target.roleCode()).isEqualTo("ROLE-CODE");
        verify(jdbc, times(1)).query(any(org.springframework.jdbc.core.PreparedStatementCreator.class), any(org.springframework.jdbc.core.RowMapper.class));
    }
    @Test void idsAreNotCodesAndMissingOrSqlShapedTargetsAreRejected() {
        for (var candidate : List.of(new SubTableBatchRoleEligibility.Target("bu-id", "role-id"),
                new SubTableBatchRoleEligibility.Target("BU-CODE", "missing"),
                new SubTableBatchRoleEligibility.Target("BU-CODE", "' OR '1'='1"))) {
            assertThatThrownBy(() -> check(List.of(candidate), Map.of())).hasMessageContaining("role target");
        }
    }
    @Test void admissionAndCurrentDirectoryStateAreRequired() {
        for (String sql : List.of("UPDATE sys_business_units SET status='DISABLED'", "UPDATE sys_roles SET status='INACTIVE'",
                "UPDATE sys_roles SET type='BU_UNBOUNDED'", "DELETE FROM sys_business_unit_roles",
                "UPDATE sys_users SET status='INACTIVE'", "UPDATE sys_users SET deleted=true",
                "DELETE FROM sys_user_business_unit_roles")) {
            new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(db)).executeWithoutResult(status -> {
                jdbc.execute(sql);
                assertThatThrownBy(() -> check(List.of(target), Map.of())).hasMessageContaining("role target");
                assertThatThrownBy(() -> SubTableBatchRoleEligibility.resolvePools(List.of(target), Map.of(), jdbc)).hasMessageContaining("role target");
                status.setRollbackOnly();
            });
        }
    }
    @Test void optionalBuFieldUsesOnlyServerInstanceWorkspaceId() {
        var contextTarget = new SubTableBatchRoleEligibility.Target(null, "ROLE-CODE");
        assertThatCode(() -> check(List.of(contextTarget), Map.of("activeBusinessUnitId", "bu-id"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> check(List.of(contextTarget), Map.of())).hasMessageContaining("role target");
        assertThatThrownBy(() -> check(List.of(contextTarget), Map.of("activeBusinessUnitId", "BU-CODE"))).hasMessageContaining("role target");
    }
    @Test void directoryFailureIsNotAnEmptyPool() {
        jdbc.execute("DROP TABLE sys_business_unit_roles");
        assertThatThrownBy(() -> check(List.of(target), Map.of())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void personalOnlySubmissionDoesNotReadTheRoleDirectory() {
        check(List.of(), Map.of());
        assertThat(SubTableBatchRoleEligibility.resolvePools(List.of(), Map.of(), jdbc)).isEmpty();
        verifyNoInteractions(jdbc);
    }
    @Test void poolsCaptureOnlyDistinctActiveMembersAndRemainImmutable() {
        jdbc.update("INSERT INTO sys_users VALUES('second-person', 'ACTIVE', false), ('inactive-person', 'INACTIVE', false), ('deleted-person', 'ACTIVE', true)");
        for (String user : List.of("person-id", "second-person", "inactive-person", "deleted-person")) {
            jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES(?, 'bu-id', 'role-id')", user);
        }
        clearInvocations(jdbc);
        var pools = SubTableBatchRoleEligibility.resolvePools(List.of(target, target), Map.of(), jdbc);
        assertThat(pools.get(target).members()).containsExactlyInAnyOrder("person-id", "second-person");
        assertThat(pools.get(target).resolved()).isEqualTo(target);
        verify(jdbc, times(1)).query(any(org.springframework.jdbc.core.PreparedStatementCreator.class), any(org.springframework.jdbc.core.RowMapper.class));
        assertThatThrownBy(() -> pools.get(target).members().clear()).isInstanceOf(UnsupportedOperationException.class);
        jdbc.update("UPDATE sys_users SET status='INACTIVE'");
        assertThat(pools.get(target).members()).hasSize(2);
        assertThatThrownBy(() -> SubTableBatchRoleEligibility.resolvePools(List.of(target), Map.of(), jdbc)).hasMessageContaining("role target");
    }
    @Test void contextPoolRecordsResolvedCodeButKeepsTheOriginalLookupKey() {
        var context = new SubTableBatchRoleEligibility.Target(null, "ROLE-CODE");
        var pool = SubTableBatchRoleEligibility.resolvePools(List.of(context), Map.of("activeBusinessUnitId", "bu-id"), jdbc).get(context);
        assertThat(pool.resolved()).isEqualTo(target);
        assertThat(pool.members()).containsExactly("person-id");
        assertThatThrownBy(() -> SubTableBatchRoleEligibility.resolvePools(List.of(context), Map.of(), jdbc)).hasMessageContaining("role target");
    }
    @Test void memberQueryReadsExactRequestedPairsRatherThanTheirCartesianProduct() {
        jdbc.update("INSERT INTO sys_business_units VALUES('second-bu', 'SECOND-BU', 'ACTIVE')");
        jdbc.update("INSERT INTO sys_roles VALUES('second-role', 'SECOND-ROLE', 'BU_BOUNDED', 'ACTIVE')");
        jdbc.update("INSERT INTO sys_business_unit_roles VALUES('second-bu', 'second-role'), ('bu-id', 'second-role')");
        jdbc.update("INSERT INTO sys_users VALUES('second-person', 'ACTIVE', false), (' ', 'ACTIVE', false)");
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES('second-person', 'second-bu', 'second-role'), (' ', 'bu-id', 'second-role')");
        var second = new SubTableBatchRoleEligibility.Target("SECOND-BU", "SECOND-ROLE");
        var pools = SubTableBatchRoleEligibility.resolvePools(List.of(target, second), Map.of(), jdbc);
        assertThat(pools.keySet()).containsExactlyInAnyOrder(target, second);
        assertThat(pools.get(target).members()).containsExactly("person-id");
        assertThat(pools.get(second).members()).containsExactly("second-person");
    }
    private void check(List<SubTableBatchRoleEligibility.Target> targets, Map<String, Object> baseline) {
        SubTableBatchRoleEligibility.requireEligible(targets, baseline, jdbc);
    }
}
