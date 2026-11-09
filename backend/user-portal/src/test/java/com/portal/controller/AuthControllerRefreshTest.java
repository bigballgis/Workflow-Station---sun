package com.portal.controller;

import com.platform.common.i18n.I18nService;
import com.platform.security.config.JwtProperties;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import com.platform.security.repository.UserRepository;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.UserRoleService;
import com.portal.component.FunctionUnitAccessComponent;
import com.portal.dto.SwitchWorkspaceRequest;
import com.portal.repository.LoginAuditQueryRepository;
import com.platform.security.service.PortalEntryService;
import com.portal.service.PortalSessionIssuerService;
import com.portal.service.PortalWorkspaceAuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Refresh and workspace switch must re-check the account; cookies and refresh token are sized to the session. */
class AuthControllerRefreshTest {

    private static final String SECRET = "test-only-portal-jwt-secret-with-enough-length-0123456789";
    private static final String USER_ID = "user-1";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PortalEntryService portalEntryService = mock(PortalEntryService.class);
    private final PortalWorkspaceAuthService portalWorkspaceAuthService = mock(PortalWorkspaceAuthService.class);
    private final JwtProperties jwtProperties = new JwtProperties();
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private AuthController controller;

    @BeforeEach
    void setUp() {
        jwtProperties.setExpirationMs(900_000);
        jwtProperties.setRefreshExpirationMs(28_800_000);
        controller = new AuthController(
                userRepository,
                mock(PasswordEncoder.class),
                mock(UserRoleService.class),
                mock(I18nService.class),
                jwtTokenService,
                portalWorkspaceAuthService,
                portalEntryService,
                mock(PortalSessionIssuerService.class),
                mock(LoginAuditQueryRepository.class),
                jwtProperties,
                mock(FunctionUnitAccessComponent.class));
        ReflectionTestUtils.setField(controller, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(controller, "jwtExpiration", 900_000L);
        when(portalEntryService.hasPortalEntry(USER_ID)).thenReturn(true);
    }

    @Test
    void activeUserGetsRefreshCookieAndTokenSizedFromConfiguration() {
        givenUser(UserStatus.ACTIVE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.refresh(Map.of("refreshToken", refreshToken()), new MockHttpServletRequest(), response);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        Cookie refreshCookie = response.getCookie(jwtProperties.getRefreshCookieName());
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getMaxAge()).isEqualTo(28_800);
        assertThat(response.getCookie(jwtProperties.getPrimaryCookieName()).getMaxAge()).isEqualTo(28_800);
        Date expiry = Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims((String) result.getBody().get("refreshToken")).getPayload().getExpiration();
        long lifetimeMs = expiry.getTime() - System.currentTimeMillis();
        assertThat(lifetimeMs).isBetween(28_700_000L, 28_800_000L);
    }

    @Test
    void disabledAccountIsRefusedWithoutNewCookies() {
        givenUser(UserStatus.INACTIVE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.refresh(Map.of("refreshToken", refreshToken()), new MockHttpServletRequest(), response);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getCookies()).isEmpty();
    }

    @Test
    void lockedAccountIsRefused() {
        givenUser(UserStatus.LOCKED);

        var result = controller.refresh(
                Map.of("refreshToken", refreshToken()), new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(result.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void disabledAccountCannotSwitchWorkspace() {
        givenUser(UserStatus.INACTIVE);
        when(portalWorkspaceAuthService.hasContext(USER_ID, "bu-1", "role-1")).thenReturn(true);
        SwitchWorkspaceRequest body = new SwitchWorkspaceRequest();
        body.setBusinessUnitId("bu-1");
        body.setRoleId("role-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.switchWorkspace(
                "Bearer " + accessToken("FULL"), new MockHttpServletRequest(), response, body);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getCookies()).isEmpty();
    }

    @Test
    void aPortalSessionSwitchesWorkspace() {
        givenUser(UserStatus.ACTIVE);
        when(portalWorkspaceAuthService.hasContext(USER_ID, "bu-1", "role-1")).thenReturn(true);

        var result = controller.switchWorkspace(
                "Bearer " + accessToken("FULL"), new MockHttpServletRequest(), new MockHttpServletResponse(), workspace());

        assertThat(result.getStatusCode().value()).isEqualTo(200);
    }

    /** A Developer Workstation or Admin Center token carries no Portal access mode (platform access model §7.1). */
    @Test
    void aTokenFromAnotherAppCannotOpenAPortalSession() {
        givenUser(UserStatus.ACTIVE);
        when(portalWorkspaceAuthService.hasContext(USER_ID, "bu-1", "role-1")).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.switchWorkspace(
                "Bearer " + accessToken(null), new MockHttpServletRequest(), response, workspace());

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getCookies()).isEmpty();
        assertThat(controller.getCurrentUser("Bearer " + accessToken(null), new MockHttpServletRequest())
                .getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void aLoggedOutTokenCannotOpenANewSession() {
        givenUser(UserStatus.ACTIVE);
        when(portalWorkspaceAuthService.hasContext(USER_ID, "bu-1", "role-1")).thenReturn(true);
        String token = accessToken("FULL");
        when(jwtTokenService.isBlacklisted(token)).thenReturn(true);

        var result = controller.switchWorkspace(
                "Bearer " + token, new MockHttpServletRequest(), new MockHttpServletResponse(), workspace());

        assertThat(result.getStatusCode().value()).isEqualTo(401);
    }

    private static SwitchWorkspaceRequest workspace() {
        SwitchWorkspaceRequest body = new SwitchWorkspaceRequest();
        body.setBusinessUnitId("bu-1");
        body.setRoleId("role-1");
        return body;
    }

    private void givenUser(UserStatus status) {
        User user = User.builder().id(USER_ID).username("u1").status(status).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private static String refreshToken() {
        Date now = new Date();
        return Jwts.builder()
                .issuer("platform")
                .subject(USER_ID)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(key())
                .compact();
    }

    private static String accessToken(String portalAccessMode) {
        Date now = new Date();
        return Jwts.builder()
                .issuer("platform")
                .subject(USER_ID)
                .claim("portalAccessMode", portalAccessMode)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(key())
                .compact();
    }

    private static javax.crypto.SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
