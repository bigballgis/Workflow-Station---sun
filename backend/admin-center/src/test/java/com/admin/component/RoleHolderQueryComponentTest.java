package com.admin.component;

import com.admin.dto.response.RoleHolder;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.RoleRepository;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Holders of each kind of role against real SQL (H2 in PostgreSQL mode): SYSTEM group members, the role's list of
 * every active team, and business unit memberships (platform access model WP9, Q10 / D-52).
 */
class RoleHolderQueryComponentTest {

    private JdbcTemplate jdbc;
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final I18nService i18nService = mock(I18nService.class);
    private RoleHolderQueryComponent holders;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE sys_users (id VARCHAR(64) PRIMARY KEY, username VARCHAR(64), full_name VARCHAR(64), "
                + "employee_id VARCHAR(64), email VARCHAR(64), status VARCHAR(20), deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE sys_virtual_groups (id VARCHAR(64) PRIMARY KEY, name VARCHAR(100), "
                + "type VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_virtual_group_members (id VARCHAR(64) PRIMARY KEY, group_id VARCHAR(64), "
                + "user_id VARCHAR(64), list_type VARCHAR(20), source VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_role_assignments (id VARCHAR(64) PRIMARY KEY, role_id VARCHAR(64), "
                + "target_type VARCHAR(20), target_id VARCHAR(64), valid_from TIMESTAMP, valid_to TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_business_units (id VARCHAR(64) PRIMARY KEY, name VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_user_business_unit_roles (id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64), "
                + "business_unit_id VARCHAR(64), role_id VARCHAR(64))");
        when(i18nService.getMessage(eq("admin.role.type_unknown"), any(Object[].class)))
                .thenAnswer(inv -> "Role " + inv.getArgument(1) + " has an unknown type: " + inv.getArgument(2));
        holders = new RoleHolderQueryComponent(roleRepository, jdbc, i18nService);

        for (String user : List.of("ann", "bob", "cid", "dan", "gone", "off")) {
            jdbc.update("INSERT INTO sys_users VALUES (?, ?, ?, NULL, NULL, ?, ?)", user, user,
                    user.toUpperCase(), "off".equals(user) ? "INACTIVE" : "ACTIVE", "gone".equals(user));
        }
        jdbc.update("INSERT INTO sys_virtual_groups VALUES ('vg-sa', 'System Administrators', 'SYSTEM', 'ACTIVE'),"
                + " ('vg-a', 'Team A', 'DEVELOPER', 'ACTIVE'), ('vg-b', 'Team B', 'DEVELOPER', 'ACTIVE'),"
                + " ('vg-off', 'Team Off', 'DEVELOPER', 'INACTIVE')");
        jdbc.update("INSERT INTO sys_virtual_groups VALUES ('vg-old', 'Former Admins', 'SYSTEM', 'ACTIVE'),"
                + " ('vg-next', 'Future Admins', 'SYSTEM', 'ACTIVE')");
        jdbc.update("INSERT INTO sys_role_assignments VALUES ('ra1', 'r-sa', 'VIRTUAL_GROUP', 'vg-sa', NULL, NULL),"
                + " ('ra2', 'r-sa', 'VIRTUAL_GROUP', 'vg-old', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP)),"
                + " ('ra3', 'r-sa', 'VIRTUAL_GROUP', 'vg-next', DATEADD('DAY', 1, CURRENT_TIMESTAMP), NULL)");
        jdbc.update("INSERT INTO sys_business_units VALUES ('bu-fin', 'Finance'), ('bu-hr', 'HR')");
    }

    @Test
    void sysAdminHoldersAreTheSystemGroupMembers() {
        role("r-sa", "SYS_ADMIN", "ADMIN");
        member("vg-sa", "ann", "MEMBER");
        member("vg-sa", "gone", "MEMBER");
        member("vg-sa", "off", "MEMBER");
        member("vg-a", "bob", "TECH_LEAD");
        member("vg-old", "cid", "MEMBER");
        member("vg-next", "dan", "MEMBER");

        // Deleted or inactive users and assignments outside their validity hold nothing (UserRoleServiceImpl).
        assertThat(holders.holders("r-sa")).extracting(h -> h.username() + "@" + h.scopeName())
                .containsExactly("ann@System Administrators");
    }

    @Test
    void anInactiveRoleIsHeldByNobody() {
        role("r-mgr", "MANAGER", "BU_BOUNDED", "INACTIVE");
        role("r-tl", "TECH_LEAD", "DEVELOPER", "INACTIVE");
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u1', 'cid', 'bu-hr', 'r-mgr')");
        member("vg-a", "bob", "TECH_LEAD");

        assertThat(holders.holders("r-mgr")).isEmpty();
        assertThat(holders.holders("r-tl")).isEmpty();
    }

    @Test
    void anUnknownRoleTypeIsABadRequestNotAServerError() {
        role("r-x", "ODD", "SOMETHING_ELSE");

        assertThatThrownBy(() -> holders.holders("r-x")).isInstanceOfSatisfying(AdminBusinessException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo("ROLE_TYPE_UNKNOWN");
            assertThat(e.getMessage()).isEqualTo("Role ODD has an unknown type: SOMETHING_ELSE");
        });
    }

    @Test
    void teamRoleHoldersAreTheRoleListsOfActiveTeamsWithTheTeam() {
        role("r-tl", "TECH_LEAD", "DEVELOPER");
        member("vg-a", "bob", "TECH_LEAD");
        member("vg-b", "bob", "TECH_LEAD");
        member("vg-b", "bob", "TECH_LEAD");
        member("vg-a", "cid", "DEVELOPER");
        member("vg-off", "dan", "TECH_LEAD");

        List<RoleHolder> leads = holders.holders("r-tl");
        assertThat(leads).extracting(h -> h.username() + "@" + h.scopeName())
                .containsExactly("bob@Team A", "bob@Team B");
        assertThat(leads).allMatch(h -> "TEAM".equals(h.scopeType()));
    }

    @Test
    void businessRoleHoldersAreItsBusinessUnitMemberships() {
        role("r-mgr", "MANAGER", "BU_BOUNDED");
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u1', 'cid', 'bu-hr', 'r-mgr'),"
                + " ('u2', 'ann', 'bu-fin', 'r-mgr'), ('u3', 'dan', 'bu-fin', 'r-other'), ('u4', 'off', 'bu-hr', 'r-mgr')");

        assertThat(holders.holders("r-mgr")).extracting(h -> h.username() + "@" + h.scopeName())
                .containsExactly("ann@Finance", "cid@HR");
    }

    private void role(String id, String code, String type) {
        role(id, code, type, "ACTIVE");
    }

    private void role(String id, String code, String type, String status) {
        when(roleRepository.findById(id)).thenReturn(Optional.of(
                Role.builder().id(id).code(code).name(code).type(type).status(status).build()));
    }

    private void member(String groupId, String userId, String listType) {
        jdbc.update("INSERT INTO sys_virtual_group_members VALUES (?, ?, ?, ?, 'MANUAL')",
                UUID.randomUUID().toString(), groupId, userId, listType);
    }
}
