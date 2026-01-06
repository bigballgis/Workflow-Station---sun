package com.platform.security.service;

import com.platform.security.dto.*;

/**
 * Authentication service interface for login, logout, and token management.
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 3.1, 4.1
 */
public interface AuthenticationService {

    /**
     * Authenticate user with username and password.
     *
     * @param username  the username
     * @param password  the password
     * @param ipAddress the client IP address for audit
     * @param userAgent the client user agent for audit
     * @return LoginResponse containing tokens and user info
     * @throws com.platform.security.exception.AuthenticationException if authentication fails
     */
    LoginResponse login(String username, String password, String ipAddress, String userAgent);

    /**
     * Logout user and invalidate the token.
     *
     * @param token     the access token to invalidate
     * @param ipAddress the client IP address for audit
     */
    void logout(String token, String ipAddress);

    /**
     * Refresh access token using refresh token.
     *
     * @param refreshToken the refresh token
     * @return TokenResponse containing new access token
     * @throws com.platform.security.exception.AuthenticationException if refresh fails
     */
    TokenResponse refreshToken(String refreshToken);

    /**
     * Get current user information from token.
     *
     * @param token the access token
     * @return UserInfo containing user details
     */
    UserInfo getCurrentUser(String token);

    /**
     * Check if a token is valid and not blacklisted.
     *
     * @param token the token to validate
     * @return true if token is valid
     */
    boolean isTokenValid(String token);
}
