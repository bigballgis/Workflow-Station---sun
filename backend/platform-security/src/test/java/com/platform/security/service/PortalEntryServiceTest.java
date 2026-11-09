package com.platform.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Portal entry against real SQL (H2 in PostgreSQL mode), platform access model §7.1. */
class PortalEntryServiceTest {

    private JdbcTemplate jdbc;
    private PortalEntryService service;

    @BeforeEach
    void createSchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sys_users (id VARCHAR(64) PRIMARY KEY, status VARCHAR(20), deleted BOOLEAN, "
                + "locked_until TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_virtual_groups (id VARCHAR(64) PRIMARY KEY, type VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_virtual_group_members (id VARCHAR(64) PRIMARY KEY, group_id VARCHAR(64), "
                + "user_id VARCHAR(64), list_type VARCHAR(20), source VARCHAR(20))");
        group("g-custom", "CUSTOM", "ACTIVE");
        group("g-custom-off", "CUSTOM", "INACTIVE");
        group("g-system", "SYSTEM", "ACTIVE");
        group("g-team", "DEVELOPER", "ACTIVE");
        service = new PortalEntryService(jdbc);
    }

    @Test
    void anActiveCustomGroupMemberHasEntryWhateverTheSource() {
        user("u1", "ACTIVE", false);
        member("g-custom", "u1", "MEMBER", "AD");
        user("u2", "ACTIVE", false);
        member("g-custom", "u2", "MEMBER", "MANUAL");

        assertThat(service.withPortalEntry(List.of("u1", "u2"))).containsExactlyInAnyOrder("u1", "u2");
    }

    @Test
    void systemAndTeamGroupsGiveNoEntry() {
        user("admin", "ACTIVE", false);
        member("g-system", "admin", "MEMBER", "MANUAL");
        member("g-team", "admin", "TECH_LEAD", "MANUAL");

        assertThat(service.hasPortalEntry("admin")).isFalse();
    }

    @Test
    void aDisabledGroupGivesNoEntryAndAnotherActiveOneKeepsIt() {
        user("u1", "ACTIVE", false);
        member("g-custom-off", "u1", "MEMBER", "MANUAL");
        assertThat(service.hasPortalEntry("u1")).isFalse();

        member("g-custom", "u1", "MEMBER", "MANUAL");
        assertThat(service.hasPortalEntry("u1")).isTrue();
    }

    @Test
    void aDisabledDeletedOrLockedAccountHasNoEntry() {
        for (String[] account : new String[][]{{"off", "INACTIVE"}, {"locked", "LOCKED"}}) {
            user(account[0], account[1], false);
            member("g-custom", account[0], "MEMBER", "MANUAL");
        }
        user("gone", "ACTIVE", true);
        member("g-custom", "gone", "MEMBER", "MANUAL");

        assertThat(service.withPortalEntry(List.of("off", "locked", "gone"))).isEmpty();
    }

    @Test
    void aShortSignInLockDoesNotTakeEntryAway() {
        user("u1", "ACTIVE", false);
        jdbc.update("UPDATE sys_users SET locked_until = DATEADD('MINUTE', 30, CURRENT_TIMESTAMP) WHERE id = 'u1'");
        member("g-custom", "u1", "MEMBER", "MANUAL");

        assertThat(service.hasPortalEntry("u1")).isTrue();
    }

    @Test
    void noIdsMeansNoQueryAndBlankIdsAreIgnored() {
        assertThat(service.withPortalEntry(List.of())).isEmpty();
        assertThat(service.hasPortalEntry(" ")).isFalse();
        assertThat(service.hasPortalEntry(null)).isFalse();
    }

    private void group(String id, String type, String status) {
        jdbc.update("INSERT INTO sys_virtual_groups VALUES (?, ?, ?)", id, type, status);
    }

    private void user(String id, String status, boolean deleted) {
        jdbc.update("INSERT INTO sys_users (id, status, deleted) VALUES (?, ?, ?)", id, status, deleted);
    }

    private void member(String groupId, String userId, String listType, String source) {
        jdbc.update("INSERT INTO sys_virtual_group_members VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), groupId, userId, listType, source);
    }
}
