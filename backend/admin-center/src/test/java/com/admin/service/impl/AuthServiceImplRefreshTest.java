package com.admin.service.impl;

import com.admin.config.AdminAuthProperties;
import com.admin.ldap.LdapAuthenticator;
import com.admin.repository.LoginAuditQueryRepository;
import com.admin.repository.UserRepository;
import com.admin.service.TaskAssignmentQueryService;
import com.platform.security.config.JwtProperties;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.UserRoleService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Refresh must re-check the account and Admin access, and size both auth cookies to the session lifetime. */
class AuthServiceImplRefreshTest {

    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String USER_ID = "user-1";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleService userRoleService = mock(UserRoleService.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final JwtProperties jwtProperties = new JwtProperties();
    private AuthServiceImpl authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jwtProperties.setExpirationMs(900_000);
        jwtProperties.setRefreshExpirationMs(28_800_000);
        authService = new AuthServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                userRoleService,
                mock(JdbcTemplate.class),
                mock(TaskAssignmentQueryService.class),
                jwtTokenService,
                jwtProperties,
                (ObjectProvider<LdapAuthenticator>) mock(ObjectProvider.class),
                new AdminAuthProperties(),
                mock(LoginAuditQueryRepository.class));
        when(jwtTokenService.validateToken(REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenService.extractUserId(REFRESH_TOKEN)).thenReturn(USER_ID);
        when(jwtTokenService.generateToken(anyString(), anyString(), any(), any(), anyList(), anyList(), any()))
                .thenReturn("new-access");
        when(jwtTokenService.generateRefreshToken(USER_ID)).thenReturn("new-refresh");
    }

    @Test
    void activeSysAdminGetsNewTokensWithCookiesSizedToTheSession() {
        givenUser(UserStatus.ACTIVE, "SYS_ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(authService.refreshLogin(REFRESH_TOKEN, response).getRefreshToken()).isEqualTo("new-refresh");

        Cookie refreshCookie = response.getCookie(jwtProperties.getRefreshCookieName());
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getMaxAge()).isEqualTo(28_800);
        // The access cookie outlives its 15-minute token so an expired token still reaches the filter and gets a 401.
        assertThat(response.getCookie(jwtProperties.getPrimaryCookieName()).getMaxAge()).isEqualTo(28_800);
    }

    @Test
    void disabledAccountIsRefused() {
        givenUser(UserStatus.INACTIVE, "SYS_ADMIN");

        assertThatThrownBy(() -> authService.refreshLogin(REFRESH_TOKEN, new MockHttpServletResponse()))
                .isInstanceOf(RuntimeException.class);
        verify(jwtTokenService, never()).generateRefreshToken(anyString());
    }

    @Test
    void lockedAccountIsRefused() {
        givenUser(UserStatus.LOCKED, "SYS_ADMIN");

        assertThatThrownBy(() -> authService.refreshLogin(REFRESH_TOKEN, new MockHttpServletResponse()))
                .isInstanceOf(RuntimeException.class);
        verify(jwtTokenService, never()).generateRefreshToken(anyString());
    }

    @Test
    void userWhoLostAdminAccessIsRefused() {
        givenUser(UserStatus.ACTIVE, "DEVELOPER");

        assertThatThrownBy(() -> authService.refreshLogin(REFRESH_TOKEN, new MockHttpServletResponse()))
                .isInstanceOf(RuntimeException.class);
        verify(jwtTokenService, never()).generateRefreshToken(anyString());
    }

    private void givenUser(UserStatus status, String roleCode) {
        User user = User.builder().id(USER_ID).username("u1").status(status).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRoleService.getEffectiveRolesForUser(USER_ID))
                .thenReturn(List.of(UserEffectiveRole.builder().roleCode(roleCode).sources(new java.util.ArrayList<>()).build()));
    }
}
