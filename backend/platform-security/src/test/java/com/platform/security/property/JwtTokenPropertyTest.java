package com.platform.security.property;

import com.platform.common.dto.UserPrincipal;
import com.platform.security.config.JwtProperties;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.impl.JwtTokenServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.List;

/**
 * Property-based tests for JWT Token Service.
 * Feature: platform-architecture, Property 3: JWT令牌跨模块有效性
 * Feature: platform-architecture, Property 4: 令牌过期和刷新正确性
 * Validates: Requirements 3.1, 3.2, 3.3
 */
class JwtTokenPropertyTest {
    
    private final JwtTokenService jwtTokenService;
    
    JwtTokenPropertyTest() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-key-for-jwt-token-generation-minimum-256-bits-required");
        properties.setExpirationMs(3600000); // 1 hour
        properties.setRefreshExpirationMs(604800000); // 7 days
        properties.setIssuer("test-platform");
        this.jwtTokenService = new JwtTokenServiceImpl(properties);
    }
    
    /**
     * Property 3: JWT令牌跨模块有效性
     * For any valid user principal, generating a token and extracting the principal
     * should return equivalent user information.
     */
    @Property(tries = 100)
    void tokenGenerationAndExtractionRoundTrip(
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 36) String userId,
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 50) String username,
            @ForAll List<@AlphaNumeric @StringLength(min = 1, max = 20) String> roles,
            @ForAll List<@AlphaNumeric @StringLength(min = 1, max = 30) String> permissions,
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 36) String departmentId,
            @ForAll("languages") String language
    ) {
        // Generate token
        String token = jwtTokenService.generateToken(
                userId, username, roles, permissions, departmentId, language
        );
        
        // Token should be valid
        assert jwtTokenService.validateToken(token) : "Generated token should be valid";
        
        // Extract principal
        UserPrincipal extracted = jwtTokenService.extractUserPrincipal(token);
        
        // Verify round-trip consistency
        assert userId.equals(extracted.getUserId()) : 
                "User ID should match: expected " + userId + ", got " + extracted.getUserId();
        assert username.equals(extracted.getUsername()) : 
                "Username should match: expected " + username + ", got " + extracted.getUsername();
        assert departmentId.equals(extracted.getDepartmentId()) : 
                "Department ID should match";
        assert language.equals(extracted.getLanguage()) : 
                "Language should match";
        
        // Roles and permissions should match (order may differ)
        assert extracted.getRoles().containsAll(roles) && roles.containsAll(extracted.getRoles()) :
                "Roles should match";
        assert extracted.getPermissions().containsAll(permissions) && permissions.containsAll(extracted.getPermissions()) :
                "Permissions should match";
    }
    
    /**
     * Property 3: JWT令牌跨模块有效性
     * For any valid token, extracting user ID should return the same ID used to generate it.
     */
    @Property(tries = 100)
    void extractUserIdConsistency(
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 36) String userId
    ) {
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of(), List.of(), null, "en"
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
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 36) String userId
    ) {
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of(), List.of(), null, "en"
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
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 36) String userId
    ) {
        String token = jwtTokenService.generateToken(
                userId, "testuser", List.of(), List.of(), null, "en"
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
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 36) String userId
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
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 36) String userId,
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 50) String username,
            @ForAll("languages") String language
    ) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .username(username)
                .roles(List.of("USER"))
                .permissions(List.of("READ"))
                .departmentId("dept1")
                .language(language)
                .build();
        
        String tokenFromPrincipal = jwtTokenService.generateToken(principal);
        String tokenFromFields = jwtTokenService.generateToken(
                userId, username, List.of("USER"), List.of("READ"), "dept1", language
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
