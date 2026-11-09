package com.admin.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VirtualGroupMemberRepository#isActiveUserOnLists} against real SQL (H2 in PostgreSQL mode): a team list
 * counts only for an active, not deleted account while its role is active (platform access model §4.1, D-20).
 */
class VirtualGroupMemberListSqlTest {

    private static final List<String> TEAM_LISTS = List.of("TECH_LEAD", "DEVELOPER");

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
        jdbc.execute("CREATE TABLE sys_virtual_group_members (id VARCHAR(64) PRIMARY KEY, group_id VARCHAR(64), "
                + "user_id VARCHAR(64), list_type VARCHAR(20), source VARCHAR(20))");
        jdbc.update("INSERT INTO sys_roles VALUES ('r-tl', 'TECH_LEAD', 'ACTIVE'), ('r-dev', 'DEVELOPER', 'ACTIVE')");
    }

    @Test
    void anActiveAccountOnEitherListCounts() {
        user("u1", "ACTIVE", false);
        member("g-a", "u1", "DEVELOPER");

        assertThat(onLists("g-a", "u1", TEAM_LISTS)).isTrue();
        assertThat(onLists("g-a", "u1", List.of("TECH_LEAD"))).isFalse();
        assertThat(onLists("g-b", "u1", TEAM_LISTS)).isFalse();
    }

    @Test
    void aDisabledOrDeletedAccountCountsOnNoList() {
        user("off", "INACTIVE", false);
        member("g-a", "off", "TECH_LEAD");
        user("gone", "ACTIVE", true);
        member("g-a", "gone", "TECH_LEAD");

        assertThat(onLists("g-a", "off", TEAM_LISTS)).isFalse();
        assertThat(onLists("g-a", "gone", TEAM_LISTS)).isFalse();
    }

    @Test
    void aDisabledRoleGivesNothingThroughItsList() {
        user("u1", "ACTIVE", false);
        member("g-a", "u1", "TECH_LEAD");
        jdbc.update("UPDATE sys_roles SET status = 'INACTIVE' WHERE code = 'TECH_LEAD'");

        assertThat(onLists("g-a", "u1", TEAM_LISTS)).isFalse();
    }

    private boolean onLists(String groupId, String userId, List<String> lists) {
        return Boolean.TRUE.equals(named.queryForObject(VirtualGroupMemberRepository.ACTIVE_USER_ON_LISTS_SQL,
                new MapSqlParameterSource().addValue("groupId", groupId).addValue("userId", userId)
                        .addValue("listTypes", lists), Boolean.class));
    }

    private void user(String id, String status, boolean deleted) {
        jdbc.update("INSERT INTO sys_users VALUES (?, ?, ?)", id, status, deleted);
    }

    private void member(String groupId, String userId, String listType) {
        jdbc.update("INSERT INTO sys_virtual_group_members VALUES (?, ?, ?, ?, 'MANUAL')",
                UUID.randomUUID().toString(), groupId, userId, listType);
    }
}
