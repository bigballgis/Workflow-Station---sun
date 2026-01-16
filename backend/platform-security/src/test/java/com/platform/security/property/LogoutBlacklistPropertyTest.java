package com.platform.security.property;

import com.platform.security.config.JwtProperties;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.impl.JwtTokenServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Logout and Token Blacklist.
 * Feature: authentication, Property 4: Logout and Blacklist Enforcement
 * Validates: Requirements 3.1, 3.2, 3.3
 */
class LogoutBlacklistPropertyTest {

    private final JwtProperties jwtProperties;

    LogoutBlacklistPropertyTest() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-jwt-token-generation-minimum-256-bits-required");
        jwtProperties.setExpirationMs(3600000);
        jwtProperties.setRefreshExpirationMs(604800000);
        jwtProperties.setIssuer("test-platform");
    }

    /**
     * Property 4: Logout and Blacklist Enforcement
     * For any valid logout operation, the token SHALL be added to the blacklist.
     * Validates: Requirements 3.1, 3.2
     */
    @Property(tries = 100)
    void logoutShouldAddTokenToBlacklist(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        // Setup mock Redis
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(mockRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(false);

        JwtTokenService jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);

        // Generate a token
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of("USER"), List.of(), "en"
        );

        // Blacklist the token (simulating logout)
        jwtTokenService.blacklistToken(token);

        // Verify Redis was called to store the blacklist entry
        verify(valueOps).set(
                argThat(key -> key.startsWith("auth:blacklist:")),
                eq("1"),
                anyLong(),
                eq(TimeUnit.SECONDS)
        );
    }

    /**
     * Property 4: Logout and Blacklist Enforcement
     * For any blacklisted token, subsequent validation SHALL return false.
     * Validates: Requirements 3.3
     */
    @Property(tries = 100)
    void blacklistedTokenShouldFailValidation(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        // Setup mock Redis that returns true for blacklist check
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(mockRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(true); // Token is blacklisted

        JwtTokenService jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);

        // Generate a token
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of("USER"), List.of(), "en"
        );

        // Validate should return false because token is blacklisted
        boolean isValid = jwtTokenService.validateToken(token);

        assertThat(isValid).isFalse();
    }

    /**
     * Property 4: Logout and Blacklist Enforcement
     * For any non-blacklisted valid token, validation SHALL return true.
     * Validates: Requirements 3.3
     */
    @Property(tries = 100)
    void nonBlacklistedTokenShouldPassValidation(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        // Setup mock Redis that returns false for blacklist check
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(false); // Token is NOT blacklisted

        JwtTokenService jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);

        // Generate a token
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of("USER"), List.of(), "en"
        );

        // Validate should return true because token is valid and not blacklisted
        boolean isValid = jwtTokenService.validateToken(token);

        assertThat(isValid).isTrue();
    }

    /**
     * Property 4: Logout and Blacklist Enforcement
     * For any token, blacklisting should use the token's remaining validity as TTL.
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    void blacklistTTLShouldMatchTokenRemainingValidity(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        // Setup mock Redis
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(mockRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(false);

        JwtTokenService jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);

        // Generate a token
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of("USER"), List.of(), "en"
        );

        // Get remaining validity before blacklisting
        long remainingSeconds = jwtTokenService.getRemainingValiditySeconds(token);

        // Blacklist the token
        jwtTokenService.blacklistToken(token);

        // Verify TTL is approximately the remaining validity (allow 5 second tolerance)
        verify(valueOps).set(
                anyString(),
                eq("1"),
                longThat(ttl -> Math.abs(ttl - remainingSeconds) <= 5),
                eq(TimeUnit.SECONDS)
        );
    }

    /**
     * Property 4: Logout and Blacklist Enforcement
     * Multiple logouts of the same token should be idempotent.
     * Validates: Requirements 3.1
     */
    @Property(tries = 50)
    void multipleLogoutsShouldBeIdempotent(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        // Setup mock Redis
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(mockRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(false);

        JwtTokenService jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);

        // Generate a token
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of("USER"), List.of(), "en"
        );

        // Blacklist the token multiple times
        jwtTokenService.blacklistToken(token);
        jwtTokenService.blacklistToken(token);
        jwtTokenService.blacklistToken(token);

        // Should not throw any exceptions - operation is idempotent
        // Verify Redis was called (at least once)
        verify(valueOps, atLeast(1)).set(
                anyString(),
                eq("1"),
                anyLong(),
                eq(TimeUnit.SECONDS)
        );
    }

    /**
     * Property 4: Logout and Blacklist Enforcement
     * Blacklisting should use a hash of the token, not the full token.
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    void blacklistKeyShouldBeHashedToken(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        // Setup mock Redis
        Set<String> storedKeys = new HashSet<>();
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(mockRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(mockRedisTemplate.hasKey(anyString())).thenReturn(false);
        doAnswer(invocation -> {
            storedKeys.add(invocation.getArgument(0));
            return null;
        }).when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        JwtTokenService jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);

        // Generate a token
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of("USER"), List.of(), "en"
        );

        // Blacklist the token
        jwtTokenService.blacklistToken(token);

        // Verify the key is a hash (shorter than the full token)
        assertThat(storedKeys).hasSize(1);
        String storedKey = storedKeys.iterator().next();
        assertThat(storedKey).startsWith("auth:blacklist:");
        // The hash part should be much shorter than the full JWT token
        String hashPart = storedKey.replace("auth:blacklist:", "");
        assertThat(hashPart.length()).isLessThan(token.length());
    }
}
