package com.developer.property;

import com.developer.component.impl.SecurityComponentImpl;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.config.JwtProperties;
import com.platform.security.filter.JwtAuthenticationFilter;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.impl.JwtTokenServiceImpl;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import net.jqwik.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Bug Condition Exploration Property Tests — JWT 认证架构不一致导致认证失败
 *
 * <p>These tests encode the EXPECTED behavior (what should happen after fix).
 * They MUST FAIL on unfixed code — failure confirms the bugs exist.
 *
 * <p>Validates: Requirements 1.3, 1.4, 2.3
 */
public class JwtAuthBugConditionPropertyTest {

    private static final String JWT_SECRET = "your-256-bit-secret-key-for-development-only";

    /**
     * Create a platform-security JwtTokenService backed by JwtProperties and a stub RedisTemplate.
     */
    private JwtTokenService createJwtTokenService() {
        JwtProperties props = new JwtProperties();
        props.setSecret(JWT_SECRET);
        props.setExpirationMs(86400000L);
        props.setRefreshExpirationMs(604800000L);
        props.setIssuer("platform");
        props.setValidateIssuer(false); // AuthController tokens don't have issuer yet

        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);
        org.mockito.Mockito.when(redisTemplate.hasKey(org.mockito.Mockito.anyString())).thenReturn(false);
        org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);

        return new JwtTokenServiceImpl(props, redisTemplate);
    }

    /**
     * Create the platform-security JwtAuthenticationFilter using the standard JwtTokenService.
     */
    private JwtAuthenticationFilter createPlatformFilter() {
        return new JwtAuthenticationFilter(createJwtTokenService());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = JWT_SECRET.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate a valid JWT token with the given claims, signed with the default dev secret.
     */
    private String generateValidToken(String userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 86400000L);
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    // =========================================================================
    // Test C1: user-portal SecurityConfig 未注册 JwtAuthenticationFilter
    //
    // Expected behavior: When a valid JWT token is present, SecurityContextHolder
    // has Authentication with principal instanceof UserPrincipal.
    //
    // On unfixed code this will FAIL because the developer-workstation's custom
    // JwtAuthenticationFilter sets a String principal (not UserPrincipal).
    // This test simulates the same pattern: after the filter runs, we check
    // that principal is UserPrincipal — which it won't be on unfixed code.
    //
    // **Validates: Requirements 1.3**
    // =========================================================================

    /**
     * C1: After JWT filter processes a valid token, the SecurityContext principal
     * MUST be an instance of UserPrincipal (not String, not null).
     *
     * On unfixed code: FAILS — developer-workstation's custom filter sets String principal.
     */
    @Property(tries = 100)
    void c1_jwtFilterShouldSetUserPrincipalInSecurityContext(
            @ForAll("validUserIds") String userId,
            @ForAll("validUsernames") String username,
            @ForAll("validRoleLists") List<String> roles) throws ServletException, IOException {

        SecurityContextHolder.clearContext();

        try {
            // Generate a valid JWT token
            String token = generateValidToken(userId, username, roles);

            // Create the platform-security standard JwtAuthenticationFilter
            JwtAuthenticationFilter filter = createPlatformFilter();

            // Simulate an HTTP request with the JWT token
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + token);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = (req, res) -> { /* no-op */ };

            filter.doFilter(request, response, filterChain);

            // EXPECTED: principal should be UserPrincipal
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal())
                    .as("Principal should be UserPrincipal, not String")
                    .isInstanceOf(UserPrincipal.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // =========================================================================
    // Test C2: developer-workstation custom JwtAuthenticationFilter sets String principal
    //
    // Expected behavior: authentication.getPrincipal() instanceof UserPrincipal.
    // On unfixed code this will FAIL because principal is a String (username).
    //
    // **Validates: Requirements 1.4**
    // =========================================================================

    /**
     * C2: After JWT filter processes a valid token, the principal MUST contain
     * the correct userId (accessible via UserPrincipal.getUserId()).
     *
     * On unfixed code: FAILS — principal is a String (username), not UserPrincipal,
     * so getUserId() is not available.
     */
    @Property(tries = 100)
    void c2_jwtFilterPrincipalShouldContainUserId(
            @ForAll("validUserIds") String userId,
            @ForAll("validUsernames") String username,
            @ForAll("validRoleLists") List<String> roles) throws ServletException, IOException {

        SecurityContextHolder.clearContext();

        try {
            String token = generateValidToken(userId, username, roles);

            JwtAuthenticationFilter filter = createPlatformFilter();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + token);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = (req, res) -> { /* no-op */ };

            filter.doFilter(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();

            Object principal = auth.getPrincipal();
            assertThat(principal)
                    .as("Principal should be UserPrincipal with userId, not a plain String")
                    .isInstanceOf(UserPrincipal.class);

            UserPrincipal userPrincipal = (UserPrincipal) principal;
            assertThat(userPrincipal.getUserId())
                    .as("UserPrincipal.userId should match the token's sub claim")
                    .isEqualTo(userId);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // =========================================================================
    // Test C4: SecurityComponentImpl.validateToken() 签名绕过
    //
    // Expected behavior: A JWT token with invalid signature but valid payload
    // containing `sub` field should be rejected (validateToken returns false).
    //
    // On unfixed code this will FAIL because parseWorkflowEngineToken() bypasses
    // signature verification for 3-part tokens.
    //
    // **Validates: Requirements 2.3**
    // =========================================================================

    /**
     * C4: validateToken() MUST return false for a JWT token with an invalid signature,
     * even if the payload contains a valid `sub` field and unexpired `exp`.
     *
     * On unfixed code: FAILS — parseWorkflowEngineToken() decodes the payload
     * without verifying the signature for 3-part tokens, returning true.
     */
    @Property(tries = 100)
    void c4_validateTokenShouldRejectInvalidSignature(
            @ForAll("validUserIds") String userId,
            @ForAll("validUsernames") String username,
            @ForAll("validRoleLists") List<String> roles) {

        SecurityComponentImpl securityComponent = new SecurityComponentImpl();

        // Generate a valid token first, then tamper with the signature
        String validToken = generateValidToken(userId, username, roles);
        String[] parts = validToken.split("\\.");
        assertThat(parts).hasSize(3);

        // Replace the signature with a fake one (keeping header and payload intact)
        String forgedToken = parts[0] + "." + parts[1] + ".INVALID_SIGNATURE_AAAA";

        // EXPECTED: validateToken should return false for forged signature
        boolean result = securityComponent.validateToken(forgedToken);
        assertThat(result)
                .as("validateToken() should reject token with invalid signature, "
                        + "but parseWorkflowEngineToken() bypasses signature verification")
                .isFalse();
    }

    // =========================================================================
    // Generators
    // =========================================================================

    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(15)
                .map(s -> "uid_" + s);
    }

    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "user_" + s);
    }

    @Provide
    Arbitrary<List<String>> validRoleLists() {
        return Arbitraries.of("ADMIN", "USER", "DEVELOPER", "VIEWER")
                .list()
                .ofMinSize(1)
                .ofMaxSize(3)
                .uniqueElements();
    }
}
