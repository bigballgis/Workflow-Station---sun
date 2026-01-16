package com.platform.security.property;

import com.platform.common.dto.UserPrincipal;
import com.platform.security.config.JwtProperties;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.impl.JwtTokenServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Property-based tests for JWT Token Service.
 * Feature: authentication, Property 2: Token Content Completeness
 * Feature: platform-architecture, Property 3: JWT令牌跨模块有效性
 * Feature: platform-architecture, Property 4: 令牌过期和刷新正确性
 * Validates: Requirements 2.6, 2.7, 2.8, 3.1, 3.2, 3.3
 */
class JwtTokenPropertyTest {
    
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    
    JwtTokenPropertyTest() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-jwt-token-generation-minimum-256-bits-required");
        jwtProperties.setExpirationMs(3600000); // 1 hour
        jwtProperties.setRefreshExpirationMs(604800000); // 7 days
        jwtProperties.setIssuer("test-platform");
        StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
        this.jwtTokenService = new JwtTokenServiceImpl(jwtProperties, mockRedisTemplate);
    }
    
    /**
     * Property 2: Token Content Completeness
     * For any successfully authenticated user, the generated access_token SHALL contain
     * all required claims (user_id, username, roles, permissions, language)
     * and the token expiration SHALL be set to the configured duration.
     * Validates: Requirements 2.6, 2.7, 2.8
     */
    @Property(tries = 100)
    void tokenShouldContainAllRequiredClaims(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId,
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 50) String username,
            @ForAll List<@CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 20) String> roles,
            @ForAll List<@CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 30) String> permissions,
            @ForAll("languages") String language
    ) {
        // Generate token
        String token = jwtTokenService.generateToken(
                userId, username, roles, permissions, language
        );
        
        // Token should be valid
        assert jwtTokenService.validateToken(token) : "Generated token should be valid";
        
        // Extract principal and verify all claims are present
        UserPrincipal extracted = jwtTokenService.extractUserPrincipal(token);
        
        assert extracted.getUserId() != null : "Token should contain user_id";
        assert extracted.getUsername() != null || username == null : "Token should contain username";
        assert extracted.getRoles() != null : "Token should contain roles";
        assert extracted.getPermissions() != null : "Token should contain permissions";
        
        // Verify values match
        assert userId.equals(extracted.getUserId()) : "User ID should match";
        assert username.equals(extracted.getUsername()) : "Username should match";
        assert language.equals(extracted.getLanguage()) : "Language should match";
    }
    
    /**
     * Property 2: Token Content Completeness - Expiration Time
     * Access token expiration should be set to configured duration (1 hour by default).
     * Validates: Requirements 2.7
     */
    @Property(tries = 100)
    void accessTokenExpirationShouldBeConfigured(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of(), List.of(), "en"
        );
        
        long expirationTime = jwtTokenService.getExpirationTime(token);
        long now = System.currentTimeMillis();
        long expectedExpiration = now + jwtProperties.getExpirationMs();
        
        // Allow 5 second tolerance for test execution time
        long tolerance = 5000;
        assert Math.abs(expirationTime - expectedExpiration) < tolerance :
                "Token expiration should be approximately " + jwtProperties.getExpirationMs() + "ms from now";
    }
    
    /**
     * Property 2: Token Content Completeness - Refresh Token Expiration
     * Refresh token expiration should be set to configured duration (7 days by default).
     * Validates: Requirements 2.8
     */
    @Property(tries = 100)
    void refreshTokenExpirationShouldBeConfigured(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        String refreshToken = jwtTokenService.generateRefreshToken(userId);
        
        long expirationTime = jwtTokenService.getExpirationTime(refreshToken);
        long now = System.currentTimeMillis();
        long expectedExpiration = now + jwtProperties.getRefreshExpirationMs();
        
        // Allow 5 second tolerance for test execution time
        long tolerance = 5000;
        assert Math.abs(expirationTime - expectedExpiration) < tolerance :
                "Refresh token expiration should be approximately " + jwtProperties.getRefreshExpirationMs() + "ms from now";
    }
    
    /**
     * Property 3: JWT令牌跨模块有效性
     * For any valid token, extracting user ID should return the same ID used to generate it.
     */
    @Property(tries = 100)
    void extractUserIdConsistency(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of(), List.of(), "en"
        );
        
        String extractedUserId = jwtTokenService.extractUserId(token);
        
        assert userId.equals(extractedUserId) : 
                "Extracted user ID should match original: expected " + userId + ", got " + extractedUserId;
    }
    
    /**
     * Property 4: 令牌过期和刷新正确性
     * For any newly generated token, it should not be expired.
     */
    @Property(tries = 100)
    void newlyGeneratedTokenShouldNotBeExpired(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of(), List.of(), "en"
        );
        
        assert !jwtTokenService.isTokenExpired(token) : 
                "Newly generated token should not be expired";
    }
    
    /**
     * Property 4: 令牌过期和刷新正确性
     * For any newly generated token, remaining validity should be positive.
     */
    @Property(tries = 100)
    void newlyGeneratedTokenShouldHavePositiveValidity(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of(), List.of(), "en"
        );
        
        long remainingSeconds = jwtTokenService.getRemainingValiditySeconds(token);
        
        assert remainingSeconds > 0 : 
                "Newly generated token should have positive remaining validity, got: " + remainingSeconds;
    }
    
    /**
     * Property 4: 令牌过期和刷新正确性
     * For any valid refresh token, refreshing should produce a new valid access token.
     */
    @Property(tries = 100)
    void refreshTokenShouldProduceValidAccessToken(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId
    ) {
        String refreshToken = jwtTokenService.generateRefreshToken(userId);
        
        assert jwtTokenService.validateToken(refreshToken) : 
                "Refresh token should be valid";
        
        String newAccessToken = jwtTokenService.refreshToken(refreshToken);
        
        assert jwtTokenService.validateToken(newAccessToken) : 
                "New access token from refresh should be valid";
        assert !jwtTokenService.isTokenExpired(newAccessToken) : 
                "New access token should not be expired";
    }
    
    /**
     * Property: Invalid tokens should fail validation.
     */
    @Property(tries = 100)
    void invalidTokensShouldFailValidation(
            @ForAll @StringLength(min = 10, max = 100) String randomString
    ) {
        // Random strings should not be valid JWT tokens
        assert !jwtTokenService.validateToken(randomString) : 
                "Random string should not be a valid token";
    }
    
    /**
     * Property: Token from UserPrincipal should be equivalent to token from individual fields.
     */
    @Property(tries = 100)
    void tokenFromPrincipalEquivalence(
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 36) String userId,
            @ForAll @CharRange(from = 'a', to = 'z') @StringLength(min = 1, max = 50) String username,
            @ForAll("languages") String language
    ) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .username(username)
                .roles(List.of("USER"))
                .permissions(List.of("READ"))
                .language(language)
                .build();
        
        String tokenFromPrincipal = jwtTokenService.generateToken(principal);
        String tokenFromFields = jwtTokenService.generateToken(
                userId, username, List.of("USER"), List.of("READ"), language
        );
        
        // Both tokens should be valid
        assert jwtTokenService.validateToken(tokenFromPrincipal) : 
                "Token from principal should be valid";
        assert jwtTokenService.validateToken(tokenFromFields) : 
                "Token from fields should be valid";
        
        // Extracted principals should be equivalent
        UserPrincipal extracted1 = jwtTokenService.extractUserPrincipal(tokenFromPrincipal);
        UserPrincipal extracted2 = jwtTokenService.extractUserPrincipal(tokenFromFields);
        
        assert extracted1.getUserId().equals(extracted2.getUserId()) : 
                "User IDs should match";
        assert extracted1.getUsername().equals(extracted2.getUsername()) : 
                "Usernames should match";
    }
    
    @Provide
    Arbitrary<String> languages() {
        return Arbitraries.of("en", "zh-CN", "zh-TW");
    }
}
