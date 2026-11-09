package com.developer.controller;

import com.developer.entity.User;
import com.developer.repository.UserRepository;
import com.platform.common.i18n.I18nService;
import com.platform.security.config.JwtProperties;
import com.platform.security.service.JwtTokenService;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.service.UserRoleService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Refresh must re-check the account status and Developer Workstation access (platform access model §6.1), and size
 * the refresh cookie and token from configuration.
 */
class AuthControllerRefreshTest {

    private static final String SECRET = "test-only-dw-jwt-secret-with-enough-length-0123456789";
    private static final String USER_ID = "user-1";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserRoleService userRoleService = mock(UserRoleService.class);
    private final JwtProperties jwtProperties = new JwtProperties();
    private AuthController controller;

    @BeforeEach
    void setUp() {
        jwtProperties.setExpirationMs(900_000);
        jwtProperties.setRefreshExpirationMs(28_800_000);
        controller = new AuthController(
                userRepository,
                passwordEncoder,
                userRoleService,
                mock(I18nService.class),
                mock(JwtTokenService.class),
                jwtProperties);
        ReflectionTestUtils.setField(controller, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(controller, "jwtExpiration", 900_000L);
    }

    @Test
    void activeUserGetsRefreshCookieAndTokenSizedFromConfiguration() {
        givenUser("ACTIVE");
        givenRoles("DEVELOPER");
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
        givenUser("INACTIVE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.refresh(Map.of("refreshToken", refreshToken()), new MockHttpServletRequest(), response);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getCookies()).isEmpty();
    }

    @Test
    void someoneNoLongerOnATeamListIsRefusedAtRefresh() {
        givenUser("ACTIVE");
        givenRoles("MANAGER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.refresh(Map.of("refreshToken", refreshToken()), new MockHttpServletRequest(), response);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getCookies()).isEmpty();
    }

    @Test
    void passwordLoginWithoutDeveloperWorkstationAccessIsForbidden() {
        User user = User.builder().id(USER_ID).username("u1").passwordHash("hash").status("ACTIVE").build();
        when(userRepository.findByUsername("u1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        givenRoles();
        com.developer.dto.LoginRequest request = new com.developer.dto.LoginRequest();
        request.setUsername("u1");
        request.setPassword("password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.login(request, new MockHttpServletRequest(), response);

        assertThat(result.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getCookies()).isEmpty();
    }

    @Test
    void lockedAccountIsRefused() {
        givenUser("LOCKED");

        var result = controller.refresh(
                Map.of("refreshToken", refreshToken()), new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(result.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void passwordLoginOfDisabledAccountIsRejected() {
        // Correct password, so only the account status can stop the login.
        User user = User.builder().id(USER_ID).username("u1").passwordHash("hash").status("INACTIVE").build();
        when(userRepository.findByUsername("u1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        com.developer.dto.LoginRequest request = new com.developer.dto.LoginRequest();
        request.setUsername("u1");
        request.setPassword("password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.login(request, new MockHttpServletRequest(), response);

        assertThat(result.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getCookies()).isEmpty();
    }

    private void givenRoles(String... roleCodes) {
        when(userRoleService.getEffectiveRolesForUser(USER_ID)).thenReturn(java.util.Arrays.stream(roleCodes)
                .map(code -> UserEffectiveRole.builder().roleCode(code).build())
                .toList());
    }

    private void givenUser(String status) {
        User user = User.builder().id(USER_ID).username("u1").status(status).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private static String refreshToken() {
        Date now = new Date();
        return Jwts.builder()
                .subject(USER_ID)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(key())
                .compact();
    }

    private static javax.crypto.SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
