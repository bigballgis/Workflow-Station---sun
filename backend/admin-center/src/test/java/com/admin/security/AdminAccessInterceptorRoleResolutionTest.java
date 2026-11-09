package com.admin.security;

import com.admin.config.InternalServiceAuthenticator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constant.PlatformConstants;
import com.platform.common.i18n.I18nService;
import com.platform.security.service.impl.UserRoleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * A service call acting for a user (DW deployment, Portal approvals) is judged by that user's roles as the
 * shared platform role resolution computes them from the database (platform access model WP2 review, WP3):
 * an inactive group or a direct user assignment grants nothing, a team list grants TECH_LEAD / DEVELOPER.
 */
class AdminAccessInterceptorRoleResolutionTest {

    private static final String SERVICE_TOKEN = "svc-secret";

    static class Fixture {
        @AdminAccess(AdminAudience.SYS_ADMIN) public void sysAdminOnly() { }
        @AdminAccess(AdminAudience.WORKSTATION) public void workstation() { }
        @AdminAccess(AdminAudience.DEPLOYER) public void deploy() { }
    }

    private JdbcTemplate jdbc;
    private AdminAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE sys_users (id VARCHAR(64) PRIMARY KEY, status VARCHAR(20), deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE sys_roles (id VARCHAR(64) PRIMARY KEY, code VARCHAR(50), name VARCHAR(100), "
                + "type VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_virtual_groups (id VARCHAR(64) PRIMARY KEY, name VARCHAR(100), "
                + "type VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_virtual_group_members (id VARCHAR(64) PRIMARY KEY, group_id VARCHAR(64), "
                + "user_id VARCHAR(64), list_type VARCHAR(20), source VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_role_assignments (id VARCHAR(64) PRIMARY KEY, role_id VARCHAR(64), "
                + "target_type VARCHAR(20), target_id VARCHAR(64), valid_from TIMESTAMP, valid_to TIMESTAMP)");
        jdbc.update("INSERT INTO sys_roles VALUES ('role-sys-admin', 'SYS_ADMIN', 'System Administrator', 'ADMIN', 'ACTIVE'),"
                + " ('role-developer', 'DEVELOPER', 'Developer', 'DEVELOPER', 'ACTIVE')");
        jdbc.update("INSERT INTO sys_virtual_groups VALUES ('vg-sys-admins', 'System Administrators', 'SYSTEM', 'ACTIVE'),"
                + " ('vg-team-a', 'Team A', 'DEVELOPER', 'ACTIVE')");
        jdbc.update("INSERT INTO sys_role_assignments (id, role_id, target_type, target_id)"
                + " VALUES ('ra-sys-admin', 'role-sys-admin', 'VIRTUAL_GROUP', 'vg-sys-admins')");
        interceptor = new AdminAccessInterceptor(new InternalServiceAuthenticator(SERVICE_TOKEN),
                new UserRoleServiceImpl(jdbc), mock(AdminTeamScope.class), mock(I18nService.class),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void aSystemAdministratorsMemberActsAsSysAdminUntilTheGroupIsDisabled() throws Exception {
        user("u-admin", "ACTIVE");
        member("vg-sys-admins", "u-admin", "MEMBER");
        assertThat(callFor("u-admin", "sysAdminOnly")).isEqualTo(200);

        jdbc.update("UPDATE sys_virtual_groups SET status = 'INACTIVE' WHERE id = 'vg-sys-admins'");
        assertThat(callFor("u-admin", "sysAdminOnly")).isEqualTo(403);
    }

    @Test
    void aDirectAssignmentOfSysAdminIsNotEnough() throws Exception {
        user("u-direct", "ACTIVE");
        jdbc.update("INSERT INTO sys_role_assignments (id, role_id, target_type, target_id)"
                + " VALUES ('ra-direct', 'role-sys-admin', 'USER', 'u-direct')");

        assertThat(callFor("u-direct", "sysAdminOnly")).isEqualTo(403);
    }

    @Test
    void aTeamDeveloperCanDeployThroughTheWorkstationButIsNoSysAdmin() throws Exception {
        user("u-dev", "ACTIVE");
        member("vg-team-a", "u-dev", "DEVELOPER");

        assertThat(callFor("u-dev", "deploy")).isEqualTo(200);
        assertThat(callFor("u-dev", "workstation")).isEqualTo(200);
        assertThat(callFor("u-dev", "sysAdminOnly")).isEqualTo(403);
    }

    @Test
    void aLockedAccountActsWithNoRole() throws Exception {
        user("u-locked", "LOCKED");
        member("vg-sys-admins", "u-locked", "MEMBER");
        member("vg-team-a", "u-locked", "DEVELOPER");

        assertThat(callFor("u-locked", "deploy")).isEqualTo(403);
    }

    private void user(String id, String status) {
        jdbc.update("INSERT INTO sys_users VALUES (?, ?, false)", id, status);
    }

    private void member(String groupId, String userId, String listType) {
        jdbc.update("INSERT INTO sys_virtual_group_members VALUES (?, ?, ?, ?, 'MANUAL')",
                UUID.randomUUID().toString(), groupId, userId, listType);
    }

    private int callFor(String userId, String handler) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x");
        request.setServletPath("/x");
        request.addHeader(PlatformConstants.HEADER_SERVICE_TOKEN, SERVICE_TOKEN);
        request.addHeader(PlatformConstants.HEADER_USER_ID, userId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new HandlerMethod(new Fixture(), Fixture.class.getMethod(handler)));
        return response.getStatus();
    }
}
