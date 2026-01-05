package com.platform.security.service;

import com.platform.common.dto.UserPrincipal;

import java.util.List;

/**
 * JWT Token Service interface for generating and validating JWT tokens.
 * Validates: Requirements 3.1, 3.2, 3.3
 */
public interface JwtTokenService {
    
    /**
     * Generate a JWT access token for the user.
     * 
     * @param userId User's unique identifier
     * @param username User's username
     * @param roles List of role identifiers
     * @param permissions List of permission identifiers
     * @param departmentId User's department ID
     * @param language User's preferred language
     * @return JWT token string
     */
    String generateToken(String userId, String username, List<String> roles, 
                         List<String> permissions, String departmentId, String language);
    
    /**
     * Generate a JWT access token from UserPrincipal.
     * 
     * @param principal User principal containing user information
     * @return JWT token string
     */
    String generateToken(UserPrincipal principal);
    
    /**
     * Generate a refresh token for the user.
     * 
     * @param userId User's unique identifier
     * @return Refresh token string
     */
    String generateRefreshToken(String userId);
    
    /**
     * Validate a JWT token.
     * 
     * @param token JWT token to validate
     * @return true if token is valid, false otherwise
     */
    boolean validateToken(String token);
    
    /**
     * Check if a token is expired.
     * 
     * @param token JWT token to check
     * @return true if token is expired, false otherwise
     */
    boolean isTokenExpired(String token);
    
    /**
     * Extract user principal from a JWT token.
     * 
     * @param token JWT token
     * @return UserPrincipal extracted from the token
     */
    UserPrincipal extractUserPrincipal(String token);
    
    /**
     * Extract user ID from a JWT token.
     * 
     * @param token JWT token
     * @return User ID from the token
     */
    String extractUserId(String token);
    
    /**
     * Refresh an access token using a refresh token.
     * 
     * @param refreshToken Refresh token
     * @return New access token
     */
    String refreshToken(String refreshToken);
    
    /**
     * Get the expiration time of a token in milliseconds.
     * 
     * @param token JWT token
     * @return Expiration time in milliseconds since epoch
     */
    long getExpirationTime(String token);
    
    /**
     * Get the remaining validity time of a token in seconds.
     * 
     * @param token JWT token
     * @return Remaining validity time in seconds, or 0 if expired
     */
    long getRemainingValiditySeconds(String token);
}
