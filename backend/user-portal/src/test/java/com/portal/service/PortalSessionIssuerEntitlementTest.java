package com.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import com.platform.security.config.JwtProperties;
import com.platform.security.service.PortalEntryService;
import com.platform.security.entity.User;
import com.platform.security.service.UserRoleService;
import com.portal.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortalSessionIssuerService entitlement gate")
class PortalSessionIssuerEntitlementTest {

    @Mock
    private UserRoleService userRoleService;
    @Mock
    private I18nService i18nService;
    @Mock
    private PortalWorkspaceAuthService portalWorkspaceAuthService;
    @Mock
    private PortalEntryService portalEntryService;
    @Mock
    private JwtProperties jwtProperties;

    private PortalSessionIssuerService issuer;

    @BeforeEach
    void setUp() {
        issuer = new PortalSessionIssuerService(
                userRoleService,
                i18nService,
                portalWorkspaceAuthService,
                portalEntryService,
                jwtProperties);
        ReflectionTestUtils.setField(issuer, "jwtSecret", "unit-test-secret-key-at-least-32-bytes!!");
        ReflectionTestUtils.setField(issuer, "jwtExpiration", 3600_000L);
    }

    @Test
    void issuePortalSession_deniesWithoutEligibleVirtualGroup() {
        User user = User.builder().id("u1").username("alice").build();
        when(portalEntryService.hasPortalEntry("u1")).thenReturn(false);
        when(i18nService.getMessage("auth.portal_entitlement_denied")).thenReturn("no vg");

        ResponseEntity<LoginResponse> response = issuer.issuePortalSession(
                user, null, null, mock(HttpServletRequest.class), mock(HttpServletResponse.class), "alice");

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getLoginErrorCode())
                .isEqualTo(PortalSessionIssuerService.LOGIN_ERROR_PORTAL_ENTITLEMENT_DENIED);
        assertThat(response.getBody().getMessage()).isEqualTo("no vg");
        assertThat(response.getBody().getAccessToken()).isNull();
        verify(portalWorkspaceAuthService, never()).listWorkspaceContexts(anyString());
    }

    @Test
    void issuePortalSession_continuesWhenEligible() {
        User user = User.builder().id("u1").username("alice").displayName("Alice").build();
        when(portalEntryService.hasPortalEntry("u1")).thenReturn(true);
        when(portalWorkspaceAuthService.listWorkspaceContexts("u1")).thenReturn(List.of());
        when(jwtProperties.getPrimaryCookieName()).thenReturn("up_access_token");
        when(jwtProperties.getRefreshCookieName()).thenReturn("up_refresh_token");
        when(userRoleService.getEffectiveRolesForUser("u1")).thenReturn(List.of());
        when(userRoleService.getPermissionsForUser("u1")).thenReturn(List.of("basic:access"));

        ResponseEntity<LoginResponse> response = issuer.issuePortalSession(
                user, null, null, mock(HttpServletRequest.class), mock(HttpServletResponse.class), "alice");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotBlank();
        assertThat(response.getBody().getLoginErrorCode()).isNull();
    }

    /**
     * Platform access model D-05: the Portal session carries no System Administrator flag, so the
     * frontend has nothing to bypass Allowed Roles or any other check with.
     */
    @Test
    void issuePortalSession_userInfoCarriesNoSystemAdministratorFlag() throws Exception {
        User user = User.builder().id("u1").username("alice").displayName("Alice").build();
        when(portalEntryService.hasPortalEntry("u1")).thenReturn(true);
        when(portalWorkspaceAuthService.listWorkspaceContexts("u1")).thenReturn(List.of());
        when(jwtProperties.getPrimaryCookieName()).thenReturn("up_access_token");
        when(jwtProperties.getRefreshCookieName()).thenReturn("up_refresh_token");
        when(userRoleService.getEffectiveRolesForUser("u1")).thenReturn(List.of());
        when(userRoleService.getPermissionsForUser("u1")).thenReturn(List.of("basic:access"));

        ResponseEntity<LoginResponse> response = issuer.issuePortalSession(
                user, null, null, mock(HttpServletRequest.class), mock(HttpServletResponse.class), "alice");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        String userJson = new ObjectMapper().writeValueAsString(response.getBody().getUser());
        assertThat(userJson).contains("\"userId\":\"u1\"").doesNotContain("systemAdministrator");
    }
}
