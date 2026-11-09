package com.admin.service.impl;

import com.admin.config.AdminAuthProperties;
import com.admin.dto.request.LoginRequest;
import com.admin.dto.response.LoginResponse;
import com.admin.ldap.LdapAuthenticator;
import com.admin.repository.LoginAuditQueryRepository;
import com.admin.repository.UserRepository;
import com.admin.security.AdminCapabilities;
import com.admin.service.TaskAssignmentQueryService;
import com.platform.security.config.JwtProperties;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.impl.UserRoleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Technical Lead of an active team signs in to Admin Center; a Developer, a lead whose team is disabled (AC-05) and
 * one taken off the list (AC-15) do not, at login, SSO and refresh alike (platform access model §8.1, WP9). Roles come
 * from the real platform role resolution over H2.
 */
class AuthServiceImplTechLeadAccessTest {

    private static final String REFRESH_TOKEN = "refresh-token";

    private JdbcTemplate jdbc;
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private AuthServiceImpl authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
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
        jdbc.update("INSERT INTO sys_roles VALUES ('role-tl', 'TECH_LEAD', 'Technical Lead', 'DEVELOPER', 'ACTIVE'),"
                + " ('role-dev', 'DEVELOPER', 'Developer', 'DEVELOPER', 'ACTIVE')");
        jdbc.update("INSERT INTO sys_virtual_groups VALUES ('vg-team-a', 'Team A', 'DEVELOPER', 'ACTIVE')");

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setExpirationMs(900_000);
        jwtProperties.setRefreshExpirationMs(28_800_000);
        ObjectProvider<LdapAuthenticator> noLdap = mock(ObjectProvider.class);
        authService = new AuthServiceImpl(userRepository, passwordEncoder, new UserRoleServiceImpl(jdbc),
                mock(JdbcTemplate.class), mock(TaskAssignmentQueryService.class), jwtTokenService, jwtProperties,
                noLdap, new AdminAuthProperties(), mock(LoginAuditQueryRepository.class));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(jwtTokenService.validateToken(REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenService.generateToken(anyString(), anyString(), any(), any(), anyList(), anyList(), any()))
                .thenReturn("access");
        when(jwtTokenService.generateRefreshToken(anyString())).thenReturn("refresh");
    }

    @Test
    void aTechnicalLeadOfAnActiveTeamSignsInAndGetsTheTeamCapabilities() {
        account("u-tl");
        member("u-tl", "TECH_LEAD");

        LoginResponse login = login("u-tl");
        assertThat(login.getUser().getRoles()).containsExactly("TECH_LEAD");
        assertThat(login.getUser().getAdminAccess())
                .containsEntry("functionUnits", AdminCapabilities.Level.TEAM)
                .containsEntry("organization", AdminCapabilities.Level.WRITE)
                .containsEntry("organizationMembers", AdminCapabilities.Level.READ)
                .containsEntry("systemConfig", AdminCapabilities.Level.READ);
        assertThat(authService.issueSsoSession("u-tl", "ip", "ua", new MockHttpServletResponse()).getUser()
                .getAdminAccess()).containsEntry("functionUnits", AdminCapabilities.Level.TEAM);
        assertThat(refresh("u-tl").getUser().getAdminAccess())
                .containsEntry("functionUnits", AdminCapabilities.Level.TEAM);
    }

    @Test
    void aDeveloperIsRefusedEverywhere() {
        account("u-dev");
        member("u-dev", "DEVELOPER");

        assertRefusedEverywhere("u-dev");
    }

    @Test
    void aLeadWhoseTeamIsDisabledIsRefused() {
        account("u-tl");
        member("u-tl", "TECH_LEAD");
        jdbc.update("UPDATE sys_virtual_groups SET status = 'INACTIVE' WHERE id = 'vg-team-a'");

        assertRefusedEverywhere("u-tl");
    }

    @Test
    void aLeadTakenOffTheListIsRefusedAtTheNextLoginAndRefresh() {
        account("u-tl");
        member("u-tl", "TECH_LEAD");
        assertThat(login("u-tl").getUser().getRoles()).contains("TECH_LEAD");

        jdbc.update("DELETE FROM sys_virtual_group_members WHERE user_id = 'u-tl'");
        assertRefusedEverywhere("u-tl");
    }

    private void assertRefusedEverywhere(String userId) {
        assertThatThrownBy(() -> login(userId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authService.issueSsoSession(userId, "ip", "ua", new MockHttpServletResponse()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> refresh(userId)).hasMessage("Token refresh failed");
    }

    private LoginResponse login(String userId) {
        LoginRequest request = new LoginRequest();
        request.setUsername(userId);
        request.setPassword("secret");
        return authService.login(request, "ip", "ua", new MockHttpServletResponse());
    }

    private LoginResponse refresh(String userId) {
        when(jwtTokenService.extractUserId(REFRESH_TOKEN)).thenReturn(userId);
        return authService.refreshLogin(REFRESH_TOKEN, new MockHttpServletResponse());
    }

    private void account(String id) {
        jdbc.update("INSERT INTO sys_users VALUES (?, 'ACTIVE', false)", id);
        User user = User.builder().id(id).username(id).passwordHash("hash").status(UserStatus.ACTIVE).build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.findByUsername(id)).thenReturn(Optional.of(user));
    }

    private void member(String userId, String listType) {
        jdbc.update("INSERT INTO sys_virtual_group_members VALUES (?, 'vg-team-a', ?, ?, 'MANUAL')",
                UUID.randomUUID().toString(), userId, listType);
    }
}
