package com.platform.security.service.impl;

import com.platform.security.dto.RoleSource;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.enums.AssignmentTargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform role resolution against real SQL (H2 in PostgreSQL mode), platform access model §4.1:
 * SYS_ADMIN / AUDITOR only from the fixed binding of an active SYSTEM group, TECH_LEAD / DEVELOPER only
 * from a list of an active team, nothing for an inactive or deleted user.
 */
class UserRoleServiceImplTest {

    private JdbcTemplate jdbc;
    private UserRoleServiceImpl service;

    @BeforeEach
    void createSchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sys_users (id VARCHAR(64) PRIMARY KEY, status VARCHAR(20), deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE sys_roles (id VARCHAR(64) PRIMARY KEY, code VARCHAR(50), name VARCHAR(100), "
                + "type VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_virtual_groups (id VARCHAR(64) PRIMARY KEY, name VARCHAR(100), "
                + "type VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_virtual_group_members (id VARCHAR(64) PRIMARY KEY, group_id VARCHAR(64), "
                + "user_id VARCHAR(64), list_type VARCHAR(20), source VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_role_assignments (id VARCHAR(64) PRIMARY KEY, role_id VARCHAR(64), "
                + "target_type VARCHAR(20), target_id VARCHAR(64), valid_from TIMESTAMP, valid_to TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_user_roles (id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64), role_id VARCHAR(64))");
        role("role-sys-admin", "SYS_ADMIN", "ADMIN");
        role("role-auditor", "AUDITOR", "AUDITOR");
        role("role-tech-lead", "TECH_LEAD", "DEVELOPER");
        role("role-developer", "DEVELOPER", "DEVELOPER");
        role("role-manager", "MANAGER", "BU_BOUNDED");
        group("vg-sys-admins", "System Administrators", "SYSTEM", "ACTIVE");
        group("vg-auditors", "Auditors", "SYSTEM", "ACTIVE");
        assign("ra-sys-admin", "role-sys-admin", "VIRTUAL_GROUP", "vg-sys-admins");
        assign("ra-auditor", "role-auditor", "VIRTUAL_GROUP", "vg-auditors");
        user("u1", "ACTIVE", false);
        service = new UserRoleServiceImpl(jdbc);
    }

    @Test
    void systemGroupMembersGetTheFixedBindingOfTheirGroup() {
        member("vg-sys-admins", "u1", "MEMBER", "MANUAL");

        List<UserEffectiveRole> roles = service.getEffectiveRolesForUser("u1");

        assertThat(roles).extracting(UserEffectiveRole::getRoleCode).containsExactly("SYS_ADMIN");
        RoleSource source = roles.get(0).getSources().get(0);
        assertThat(source.getSourceType()).isEqualTo(AssignmentTargetType.VIRTUAL_GROUP);
        assertThat(source.getSourceId()).isEqualTo("vg-sys-admins");
        assertThat(source.getSourceName()).isEqualTo("System Administrators");
        assertThat(source.getAssignmentId()).isEqualTo("ra-sys-admin");
    }

    @Test
    void anInactiveGroupGrantsNothing() {
        member("vg-sys-admins", "u1", "MEMBER", "MANUAL");
        jdbc.update("UPDATE sys_virtual_groups SET status = 'INACTIVE' WHERE id = 'vg-sys-admins'");
        group("vg-team-a", "Team A", "DEVELOPER", "INACTIVE");
        member("vg-team-a", "u1", "TECH_LEAD", "MANUAL");

        assertThat(service.getEffectiveRoleCodesForUser("u1")).isEmpty();
    }

    @Test
    void directGrantsOfPlatformRolesGrantNothing() {
        assign("ra-direct", "role-sys-admin", "USER", "u1");
        jdbc.update("INSERT INTO sys_user_roles VALUES ('ur1', 'u1', 'role-tech-lead')");

        assertThat(service.getEffectiveRoleCodesForUser("u1")).isEmpty();
    }

    @Test
    void groupsGrantOnlyTheRolesTheirTypeAllows() {
        // A leftover SYSTEM group bound to TECH_LEAD, and a CUSTOM group bound to SYS_ADMIN and MANAGER.
        group("vg-tech-leads", "Technical Leads", "SYSTEM", "ACTIVE");
        assign("ra-tl", "role-tech-lead", "VIRTUAL_GROUP", "vg-tech-leads");
        group("vg-custom", "Finance", "CUSTOM", "ACTIVE");
        assign("ra-custom-admin", "role-sys-admin", "VIRTUAL_GROUP", "vg-custom");
        assign("ra-custom-manager", "role-manager", "VIRTUAL_GROUP", "vg-custom");
        member("vg-tech-leads", "u1", "MEMBER", "MANUAL");
        member("vg-custom", "u1", "MEMBER", "MANUAL");

        assertThat(service.getEffectiveRoleCodesForUser("u1")).isEmpty();
    }

    @Test
    void teamListsGrantTheirRoleWithTheTeamAsSource() {
        group("vg-team-a", "Team A", "DEVELOPER", "ACTIVE");
        group("vg-team-b", "Team B", "DEVELOPER", "ACTIVE");
        member("vg-team-a", "u1", "TECH_LEAD", "AD");
        member("vg-team-b", "u1", "DEVELOPER", "MANUAL");

        List<UserEffectiveRole> roles = service.getEffectiveRolesForUser("u1");

        assertThat(roles).extracting(UserEffectiveRole::getRoleCode).containsExactly("DEVELOPER", "TECH_LEAD");
        assertThat(roles.get(0).getSources()).extracting(RoleSource::getSourceId).containsExactly("vg-team-b");
        assertThat(roles.get(1).getSources()).extracting(RoleSource::getSourceId).containsExactly("vg-team-a");
        assertThat(service.hasRole("u1", "TECH_LEAD")).isTrue();
        assertThat(service.hasRole("u1", "SYS_ADMIN")).isFalse();
    }

    @Test
    void aTeamMemberOffBothListsGetsNoRole() {
        group("vg-team-a", "Team A", "DEVELOPER", "ACTIVE");
        member("vg-team-a", "u1", "MEMBER", "MANUAL");

        assertThat(service.getEffectiveRoleCodesForUser("u1")).isEmpty();
    }

    @Test
    void bothSourcesOfOneListGiveOneSource() {
        group("vg-team-a", "Team A", "DEVELOPER", "ACTIVE");
        member("vg-team-a", "u1", "DEVELOPER", "AD");
        member("vg-team-a", "u1", "DEVELOPER", "MANUAL");
        member("vg-auditors", "u1", "MEMBER", "AD");
        member("vg-auditors", "u1", "MEMBER", "MANUAL");

        List<UserEffectiveRole> roles = service.getEffectiveRolesForUser("u1");

        assertThat(roles).extracting(UserEffectiveRole::getRoleCode).containsExactly("AUDITOR", "DEVELOPER");
        assertThat(roles).allSatisfy(role -> assertThat(role.getSources()).hasSize(1));
    }

    @Test
    void anInactiveOrDeletedUserGetsNothing() {
        user("u-locked", "LOCKED", false);
        user("u-deleted", "ACTIVE", true);
        member("vg-sys-admins", "u-locked", "MEMBER", "MANUAL");
        member("vg-sys-admins", "u-deleted", "MEMBER", "MANUAL");

        assertThat(service.getEffectiveRoleCodesForUser("u-locked")).isEmpty();
        assertThat(service.getEffectiveRoleCodesForUser("u-deleted")).isEmpty();
    }

    @Test
    void anExpiredBindingOrInactiveRoleGrantsNothing() {
        member("vg-sys-admins", "u1", "MEMBER", "MANUAL");
        member("vg-auditors", "u1", "MEMBER", "MANUAL");
        jdbc.update("UPDATE sys_role_assignments SET valid_to = TIMESTAMP '2000-01-01 00:00:00' WHERE id = 'ra-sys-admin'");
        jdbc.update("UPDATE sys_roles SET status = 'INACTIVE' WHERE id = 'role-auditor'");

        assertThat(service.getEffectiveRoleCodesForUser("u1")).isEmpty();
    }

    private void user(String id, String status, boolean deleted) {
        jdbc.update("INSERT INTO sys_users VALUES (?, ?, ?)", id, status, deleted);
    }

    private void role(String id, String code, String type) {
        jdbc.update("INSERT INTO sys_roles VALUES (?, ?, ?, ?, 'ACTIVE')", id, code, code, type);
    }

    private void group(String id, String name, String type, String status) {
        jdbc.update("INSERT INTO sys_virtual_groups VALUES (?, ?, ?, ?)", id, name, type, status);
    }

    private void member(String groupId, String userId, String listType, String source) {
        jdbc.update("INSERT INTO sys_virtual_group_members VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), groupId, userId, listType, source);
    }

    private void assign(String id, String roleId, String targetType, String targetId) {
        jdbc.update("INSERT INTO sys_role_assignments (id, role_id, target_type, target_id) VALUES (?, ?, ?, ?)",
                id, roleId, targetType, targetId);
    }
}
