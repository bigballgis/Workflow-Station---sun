package com.platform.security.property;

import com.platform.common.dto.UserPrincipal;
import com.platform.security.config.JwtProperties;
import com.platform.security.filter.JwtAuthenticationFilter;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.impl.JwtTokenServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Preservation Property Tests for platform-security JwtAuthenticationFilter.
 * Feature: kong-authn-authz-fix
 *
 * **Property 7: Preservation** — 现有登录、路由和权限检查流程不变
 *
 * For all valid JWT claims, platform-security JwtAuthenticationFilter successfully
 * parses token and sets UserPrincipal with fields matching the claims.
 * This is the standard filter behavior we're adopting for all modules.
 *
 * These tests MUST PASS on unfixed code (they verify baseline behavior to preserve).
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
 */
class JwtFilterPreservationPropertyTest {

    private final JwtTokenService jwtTokenService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    JwtFilterPreservationPropertyTest() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-jwt-preservation-tests-minimum-256-bits-required");
        jwtProperties.setExpirationMs(3600000);
        jwtProperties.setRefreshExpirationMs(604800000);
        jwtProperties.setIssuer("platform");
        jwtProperties.setValidateIssuer(true);

        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        // No tokens are blacklisted in this test
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(false);

        this.jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);
        this.jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenService);
    }

    /**
     * Property 7: Preservation — JwtAuthenticationFilter parses valid JWT and sets UserPrincipal
     *
     * For all valid JWT claims (userId, username, roles, permissions, language),
     * the filter SHALL parse the token and set a UserPrincipal in SecurityContext
     * with fields matching the original claims.
     *
     * **Validates: Requirements 3.2**
     */
    @Property(tries = 100)
    void filterShouldSetUserPrincipalMatchingClaims(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId,
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 50) String username,
            @ForAll @IntRange(min = 0, max = 3) int roleCount,
            @ForAll @IntRange(min = 0, max = 5) int permCount,
            @ForAll("languages") String language
    ) throws ServletException, IOException {
        // Build roles and permissions lists
        List<String> roles = IntStream.range(0, roleCount)
                .mapToObj(i -> "role" + i)
                .collect(Collectors.toList());
        List<String> permissions = IntStream.range(0, permCount)
                .mapToObj(i -> "perm" + i)
                .collect(Collectors.toList());

        // Generate a valid token using JwtTokenService
        String token = jwtTokenService.generateToken(userId, username, roles, permissions, language);

        // Set up mock request with Authorization header
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.setRequestURI("/api/v1/some-endpoint");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        // Clear SecurityContext before each run
        SecurityContextHolder.clearContext();

        try {
            // Execute the filter
            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            // Verify SecurityContext has authentication
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assert auth != null : "Authentication should be set in SecurityContext";
            assert auth.isAuthenticated() : "Authentication should be marked as authenticated";

            // Verify principal is UserPrincipal
            Object principal = auth.getPrincipal();
            assert principal instanceof UserPrincipal :
                    "Principal should be UserPrincipal, got: " + principal.getClass().getName();

            UserPrincipal userPrincipal = (UserPrincipal) principal;

            // Verify fields match the original claims
            assert userId.equals(userPrincipal.getUserId()) :
                    "userId mismatch: expected " + userId + ", got " + userPrincipal.getUserId();
            assert username.equals(userPrincipal.getUsername()) :
                    "username mismatch: expected " + username + ", got " + userPrincipal.getUsername();
            assert language.equals(userPrincipal.getLanguage()) :
                    "language mismatch: expected " + language + ", got " + userPrincipal.getLanguage();
            assert roles.equals(userPrincipal.getRoles()) :
                    "roles mismatch: expected " + roles + ", got " + userPrincipal.getRoles();
            assert permissions.equals(userPrincipal.getPermissions()) :
                    "permissions mismatch: expected " + permissions + ", got " + userPrincipal.getPermissions();

            // Verify authorities include roles with ROLE_ prefix and permissions
            var authorities = auth.getAuthorities();
            for (String role : roles) {
                boolean hasRoleAuthority = authorities.stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
                assert hasRoleAuthority : "Should have ROLE_" + role + " authority";
            }
            for (String perm : permissions) {
                boolean hasPermAuthority = authorities.stream()
                        .anyMatch(a -> a.getAuthority().equals(perm));
                assert hasPermAuthority : "Should have " + perm + " authority";
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Property 7: Preservation — Filter skips requests without Authorization header
     *
     * For any request without an Authorization header, the filter SHALL NOT
     * set any authentication in SecurityContext (pass-through behavior).
     *
     * **Validates: Requirements 3.2**
     */
    @Property(tries = 100)
    void filterShouldSkipRequestsWithoutAuthHeader(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 50) String path
    ) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/" + path);
        // No Authorization header

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        SecurityContextHolder.clearContext();

        try {
            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assert auth == null : "Authentication should be null when no Authorization header";
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Provide
    Arbitrary<String> languages() {
        return Arbitraries.of("en", "zh-CN", "zh-TW");
    }
}
