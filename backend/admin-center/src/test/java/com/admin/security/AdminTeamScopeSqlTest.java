package com.admin.security;

import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The team-scope SQL against real SQL (H2 in PostgreSQL mode): which teams a user leads, which codes those teams own
 * (latest import decides), and which units a relation table is linked to (platform access model §8.2, WP9).
 */
class AdminTeamScopeSqlTest {

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate named;

    @BeforeEach
    void createSchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        named = new NamedParameterJdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sys_users (id VARCHAR(64) PRIMARY KEY, status VARCHAR(20), deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE sys_roles (id VARCHAR(64) PRIMARY KEY, code VARCHAR(50), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_virtual_groups (id VARCHAR(64) PRIMARY KEY, code VARCHAR(64), "
                + "type VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_virtual_group_members (id VARCHAR(64) PRIMARY KEY, group_id VARCHAR(64), "
                + "user_id VARCHAR(64), list_type VARCHAR(20), source VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_function_units (id VARCHAR(64) PRIMARY KEY, code VARCHAR(64), "
                + "team_code VARCHAR(64), imported_at TIMESTAMP, created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE rt_table_function_units (id VARCHAR(64) PRIMARY KEY, relation_table_id BIGINT, "
                + "function_unit_id VARCHAR(64))");
        jdbc.update("INSERT INTO sys_roles VALUES ('r-tl', 'TECH_LEAD', 'ACTIVE'), ('r-dev', 'DEVELOPER', 'ACTIVE')");
        jdbc.update("INSERT INTO sys_virtual_groups VALUES ('vg-a', 'TEAM_A', 'DEVELOPER', 'ACTIVE'),"
                + " ('vg-b', 'TEAM_B', 'DEVELOPER', 'ACTIVE'), ('vg-off', 'TEAM_OFF', 'DEVELOPER', 'INACTIVE'),"
                + " ('vg-auditors', 'AUDITORS', 'SYSTEM', 'ACTIVE')");
    }

    @Test
    void onlyTheTechnicalLeadListOfAnActiveTeamCounts() {
        user("u1", "ACTIVE", false);
        member("vg-a", "u1", "TECH_LEAD");
        member("vg-b", "u1", "DEVELOPER");
        member("vg-off", "u1", "TECH_LEAD");
        member("vg-auditors", "u1", "TECH_LEAD");

        assertThat(ledTeamCodes("u1")).containsExactly("TEAM_A");
    }

    @Test
    void aDisabledOrDeletedAccountOrADisabledRoleLeadsNothing() {
        user("off", "INACTIVE", false);
        member("vg-a", "off", "TECH_LEAD");
        user("gone", "ACTIVE", true);
        member("vg-a", "gone", "TECH_LEAD");
        user("u1", "ACTIVE", false);
        member("vg-a", "u1", "TECH_LEAD");

        assertThat(ledTeamCodes("off")).isEmpty();
        assertThat(ledTeamCodes("gone")).isEmpty();
        assertThat(ledTeamCodes("u1")).containsExactly("TEAM_A");
        jdbc.update("UPDATE sys_roles SET status = 'INACTIVE' WHERE code = 'TECH_LEAD'");
        assertThat(ledTeamCodes("u1")).isEmpty();
    }

    @Test
    void anAdLeadAndAManualLeadOfTheSameTeamGiveTheTeamOnce() {
        user("u1", "ACTIVE", false);
        member("vg-a", "u1", "TECH_LEAD");
        jdbc.update("INSERT INTO sys_virtual_group_members VALUES (?, 'vg-a', 'u1', 'TECH_LEAD', 'AD')",
                UUID.randomUUID().toString());

        assertThat(ledTeamCodes("u1")).containsExactly("TEAM_A");
    }

    @Test
    void theLatestImportedRowDecidesTheTeamOfACode() {
        version("FU_A-1", "FU_A", "TEAM_A", 1);
        version("FU_MOVED-1", "FU_MOVED", "TEAM_A", 1);
        version("FU_MOVED-2", "FU_MOVED", "TEAM_B", 2);
        version("FU_BACK-1", "FU_BACK", "TEAM_B", 1);
        version("FU_BACK-2", "FU_BACK", "TEAM_A", 2);
        version("FU_OLD-1", "FU_OLD", null, 1);
        version("FU_LOST-1", "FU_LOST", "TEAM_A", 1);
        version("FU_LOST-2", "FU_LOST", null, 2);

        assertThat(codesOf(List.of("TEAM_A"))).containsExactlyInAnyOrder("FU_A", "FU_BACK");
        assertThat(codesOf(List.of("TEAM_B"))).containsExactly("FU_MOVED");
        assertThat(codesOf(List.of("TEAM_A", "TEAM_B"))).containsExactlyInAnyOrder("FU_A", "FU_BACK", "FU_MOVED");
    }

    @Test
    void aLinkToARemovedCatalogRowYieldsANullCodeNotNothing() {
        version("fu-a1", "FU_A", "TEAM_A", 1);
        link(1L, "fu-a1");
        link(1L, "fu-removed");

        List<String> codes = jdbc.queryForList(AdminTeamScope.RELATION_TABLE_OWNER_CODES_SQL, String.class, 1L);
        assertThat(codes).containsExactlyInAnyOrder("FU_A", null);
        assertThat(jdbc.queryForList(AdminTeamScope.RELATION_TABLE_OWNER_CODES_SQL, String.class, 2L)).isEmpty();
    }

    /** The four kinds of table (own, shared, another team's, Common) through the list visibility filter (D-47). */
    @Test
    void aTechnicalLeadListsCommonTablesAndTablesLinkedToTheirUnits() {
        jdbc.execute("CREATE TABLE rt_table_definitions (id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO rt_table_definitions VALUES (1), (2), (3), (4)");
        version("fu-a1", "FU_A", "TEAM_A", 1);
        version("fu-b1", "FU_B", "TEAM_B", 1);
        link(1L, "fu-a1");
        link(2L, "fu-a1");
        link(2L, "fu-b1");
        link(3L, "fu-b1");

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        AdminCaller.ofUser("tl", List.of("TECH_LEAD")).storeOn(request);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            FunctionUnitRepository units = org.mockito.Mockito.mock(FunctionUnitRepository.class);
            org.mockito.Mockito.when(units.findCodesOfTeams(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(List.of("FU_A"));
            VirtualGroupMemberRepository members = org.mockito.Mockito.mock(VirtualGroupMemberRepository.class);
            VirtualGroupMemberRepository.LedTeam teamA = new VirtualGroupMemberRepository.LedTeam() {
                public String getId() { return "vg-a"; }
                public String getCode() { return "TEAM_A"; }
            };
            org.mockito.Mockito.when(members.findTeamsLedBy("tl")).thenReturn(List.of(teamA));
            AdminTeamScope scope = new AdminTeamScope(members,
                    org.mockito.Mockito.mock(com.admin.repository.VirtualGroupRepository.class), units, jdbc,
                    org.mockito.Mockito.mock(com.admin.servicetask.ApWorkspaceSql.class),
                    new com.admin.servicetask.config.ServiceTaskProperties(),
                    org.mockito.Mockito.mock(com.admin.bi.repository.BiDataViewMetadataRepository.class),
                    org.mockito.Mockito.mock(com.platform.common.i18n.I18nService.class));

            StringBuilder where = new StringBuilder(" FROM rt_table_definitions t WHERE 1=1");
            List<Object> params = new java.util.ArrayList<>();
            scope.appendVisibleRelationTables(where, params, "t.id");

            assertThat(jdbc.queryForList("SELECT t.id" + where + " ORDER BY t.id", Long.class, params.toArray()))
                    .containsExactly(1L, 2L, 4L);
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    private List<String> ledTeamCodes(String userId) {
        return named.query(VirtualGroupMemberRepository.LED_TEAMS_SQL,
                new MapSqlParameterSource("userId", userId), (rs, n) -> rs.getString("code"));
    }

    private List<String> codesOf(List<String> teamCodes) {
        return named.queryForList(FunctionUnitRepository.CODES_OF_TEAMS_SQL,
                new MapSqlParameterSource("teamCodes", teamCodes), String.class);
    }

    private void user(String id, String status, boolean deleted) {
        jdbc.update("INSERT INTO sys_users VALUES (?, ?, ?)", id, status, deleted);
    }

    private void member(String groupId, String userId, String listType) {
        jdbc.update("INSERT INTO sys_virtual_group_members VALUES (?, ?, ?, ?, 'MANUAL')",
                UUID.randomUUID().toString(), groupId, userId, listType);
    }

    private void version(String id, String code, String teamCode, int importDay) {
        Timestamp at = Timestamp.valueOf(LocalDateTime.of(2026, 11, importDay, 10, 0));
        jdbc.update("INSERT INTO sys_function_units VALUES (?, ?, ?, ?, ?)", id, code, teamCode, at, at);
    }

    private void link(long tableId, String functionUnitId) {
        jdbc.update("INSERT INTO rt_table_function_units VALUES (?, ?, ?)", UUID.randomUUID().toString(), tableId,
                functionUnitId);
    }
}
