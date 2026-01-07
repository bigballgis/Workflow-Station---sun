package com.platform.security.property;

import com.platform.common.dto.UserPrincipal;
import com.platform.security.config.JwtProperties;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.impl.JwtTokenServiceImpl;
import io.jsonwebtoken.JwtException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for Token Refresh.
 * Feature: authentication, Property 5: Token Refresh Round-Trip
 * Validates: Requirements 4.1, 4.5
 */
class TokenRefreshPropertyTest {

    private final JwtProperties jwtProperties;

    TokenRefreshPropertyTest() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-jwt-token-generation-minimum-256-bits-required");
        jwtProperties.setExpirationMs(3600000);
        jwtProperties.setRefreshExpirationMs(604800000);
        jwtProperties.setIssuer("test-platform");
    }

    /**
     * Property 5: Token Refresh Round-Trip
     * For any valid refresh token, the refresh operation SHALL return a new access_token
     * containing the same user_id as the original.
     * Validates: Requirements 4.1, 4.5
     */
    @Property(tries = 100)
    void refreshShouldPreserveUserId(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(false);

        JwtTokenService jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);

        // Generate refresh token
        String refreshToken = jwtTokenService.generateRefreshToken(userId);

        // Refresh to get new access token
        String newAccessToken = jwtTokenService.refreshToken(refreshToken);

        // Extract user ID from new token
        String extractedUserId = jwtTokenService.extractUserId(newAccessToken);

        // User ID should be preserved
        assertThat(extractedUserId).isEqualTo(userId);
    }

    /**
     * Property 5: Token Refresh Round-Trip
     * For any valid refresh token, the new access_token SHALL be valid.
     * Validates: Requirements 4.1
     */
    @Property(tries = 100)
    void refreshedTokenShouldBeValid(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(false);

        JwtTokenService jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);

        // Generate refresh token
        String refreshToken = jwtTokenService.generateRefreshToken(userId);

        // Refresh to get new access token
        String newAccessToken = jwtTokenService.refreshToken(refreshToken);

        // New token should be valid
        assertThat(jwtTokenService.validateToken(newAccessToken)).isTrue();
        assertThat(jwtTokenService.isTokenExpired(newAccessToken)).isFalse();
    }

    /**
     * Property 5: Token Refresh Round-Trip
     * For any valid refresh token, the new access_token SHALL have fresh expiration.
     * Validates: Requirements 4.1
     */
    @Property(tries = 100)
    void refreshedTokenShouldHaveFreshExpiration(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(false);

        JwtTokenService jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);

        // Generate refresh token
        String refreshToken = jwtTokenService.generateRefreshToken(userId);

        // Refresh to get new access token
        String newAccessToken = jwtTokenService.refreshToken(refreshToken);

        // New token should have approximately full expiration time
        long remainingSeconds = jwtTokenService.getRemainingValiditySeconds(newAccessToken);
        long expectedSeconds = jwtProperties.getExpirationMs() / 1000;

        // Allow 10 second tolerance
        assertThat(remainingSeconds).isGreaterThan(expectedSeconds - 10);
    }

    /**
     * Property 6: Invalid Token Rejection
     * For any invalid refresh token, the service SHALL throw an exception.
     * Validates: Requirements 4.3
     */
    @Property(tries = 100)
    void invalidRefreshTokenShouldBeRejected(
            @ForAll @StringLength(min = 10, max = 100) String invalidToken
    ) {
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(false);

        JwtTokenService jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);

        // Invalid token should throw exception
        assertThatThrownBy(() -> jwtTokenService.refreshToken(invalidToken))
                .isInstanceOf(JwtException.class);
    }

    /**
     * Property 6: Invalid Token Rejection
     * Using an access token as refresh token SHALL be rejected.
     * Validates: Requirements 4.3
     */
    @Property(tries = 100)
    void accessTokenShouldNotBeUsedAsRefreshToken(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(false);

        JwtTokenService jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);

        // Generate access token (not refresh token)
        String accessToken = jwtTokenService.generateToken(
                userId, "testuser", List.of("USER"), List.of(), null, "en"
        );

        // Using access token as refresh token should throw exception
        assertThatThrownBy(() -> jwtTokenService.refreshToken(accessToken))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("not a refresh token");
    }

    /**
     * Property 5: Token Refresh Round-Trip
     * Multiple refreshes should all produce valid tokens with same user ID.
     * Validates: Requirements 4.1, 4.5
     */
    @Property(tries = 50)
    void multipleRefreshesShouldAllBeValid(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(false);

        JwtTokenService jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);

        // Generate refresh token
        String refreshToken = jwtTokenService.generateRefreshToken(userId);

        // Refresh multiple times
        for (int i = 0; i < 5; i++) {
            String newAccessToken = jwtTokenService.refreshToken(refreshToken);
            
            assertThat(jwtTokenService.validateToken(newAccessToken)).isTrue();
            assertThat(jwtTokenService.extractUserId(newAccessToken)).isEqualTo(userId);
        }
    }
}
