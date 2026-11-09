package com.developer.repository;

import com.developer.dto.DevGroupOptionDTO;
import com.developer.repository.VirtualGroupMembershipDao.TeamMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Team lists against real SQL (H2 in PostgreSQL mode), platform access model §4.4, §5.4, §6.1. */
class VirtualGroupMembershipDaoTest {

    private static final String PUBLIC = "vg-dev-public";

    private JdbcTemplate jdbc;
    private VirtualGroupMembershipDao dao;

    @BeforeEach
    void createSchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sys_users (id VARCHAR(64) PRIMARY KEY, status VARCHAR(20), deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE sys_roles (id VARCHAR(64) PRIMARY KEY, code VARCHAR(50), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_virtual_groups (id VARCHAR(64) PRIMARY KEY, code VARCHAR(50), "
                + "name VARCHAR(100), type VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_virtual_group_members (id VARCHAR(64) PRIMARY KEY, group_id VARCHAR(64), "
                + "user_id VARCHAR(64), list_type VARCHAR(20), source VARCHAR(20))");
        jdbc.update("INSERT INTO sys_roles VALUES ('r-tl', 'TECH_LEAD', 'ACTIVE'), ('r-dev', 'DEVELOPER', 'ACTIVE')");
        group(PUBLIC, "DEV_TEAM_PUBLIC", "Public", "DEVELOPER", "ACTIVE");
        group("g-a", "TEAM_A", "Team A", "DEVELOPER", "ACTIVE");
        group("g-b", "TEAM_B", "Team B", "DEVELOPER", "INACTIVE");
        group("g-custom", "CREDIT", "Credit", "CUSTOM", "ACTIVE");
        user("u1", "ACTIVE", false);
        dao = new VirtualGroupMembershipDao(jdbc);
    }

    @Test
    void someoneOnBothListsOfATeamIsItsTechnicalLead() {
        member("g-a", "u1", "DEVELOPER");
        member("g-a", "u1", "TECH_LEAD");

        assertThat(dao.findTeamMemberships("u1", PUBLIC))
                .containsExactly(new TeamMembership("g-a", "Team A", "ACTIVE", true, true));
    }

    @Test
    void onlyTheTwoListsOfDeveloperGroupsCountAndDisabledTeamsKeepTheirStatus() {
        member("g-b", "u1", "DEVELOPER");
        member("g-custom", "u1", "MEMBER");
        member(PUBLIC, "u1", "DEVELOPER");

        List<TeamMembership> teams = dao.findTeamMemberships("u1", PUBLIC);

        assertThat(teams).containsExactly(new TeamMembership("g-b", "Team B", "INACTIVE", false, true));
        assertThat(teams.get(0).active()).isFalse();
    }

    @Test
    void aDisabledOrDeletedAccountIsOnNoList() {
        member("g-a", "u1", "TECH_LEAD");
        user("u2", "INACTIVE", false);
        member("g-a", "u2", "TECH_LEAD");
        user("u3", "ACTIVE", true);
        member("g-a", "u3", "DEVELOPER");

        assertThat(dao.findTeamMemberships("u1", PUBLIC)).hasSize(1);
        assertThat(dao.findTeamMemberships("u2", PUBLIC)).isEmpty();
        assertThat(dao.findTeamMemberships("u3", PUBLIC)).isEmpty();
    }

    @Test
    void aDisabledRoleGivesNothingThroughItsList() {
        member("g-a", "u1", "TECH_LEAD");
        jdbc.update("UPDATE sys_roles SET status = 'INACTIVE' WHERE code = 'TECH_LEAD'");

        assertThat(dao.findTeamMemberships("u1", PUBLIC)).isEmpty();
    }

    @Test
    void allTeamsAreTheDeveloperGroupsExceptPublic() {
        assertThat(dao.findAllTeams(PUBLIC)).extracting(DevGroupOptionDTO::getName)
                .containsExactly("Team A", "Team B");
    }

    @Test
    void aGroupIsFoundByItsCode() {
        assertThat(dao.findGroupByCode("TEAM_A")).get()
                .isEqualTo(new VirtualGroupMembershipDao.WorkspaceGroup("g-a", "TEAM_A", "Team A", "DEVELOPER", "ACTIVE"));
        assertThat(dao.findGroupByCode("NO_SUCH_TEAM")).isEmpty();
        assertThat(dao.findGroups(List.of("g-a", "gone"))).containsOnlyKeys("g-a");
    }

    private void group(String id, String code, String name, String type, String status) {
        jdbc.update("INSERT INTO sys_virtual_groups VALUES (?, ?, ?, ?, ?)", id, code, name, type, status);
    }

    private void user(String id, String status, boolean deleted) {
        jdbc.update("INSERT INTO sys_users VALUES (?, ?, ?)", id, status, deleted);
    }

    private void member(String groupId, String userId, String listType) {
        jdbc.update("INSERT INTO sys_virtual_group_members VALUES (?, ?, ?, ?, 'MANUAL')",
                UUID.randomUUID().toString(), groupId, userId, listType);
    }
}
