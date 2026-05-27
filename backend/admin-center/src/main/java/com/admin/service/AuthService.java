package com.admin.service;

import com.admin.dto.request.LoginRequest;
import com.admin.dto.response.LoginResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authentication service interface
 */
public interface AuthService {
    
    /**
     * User login
     */
    LoginResponse login(LoginRequest request, String ipAddress, String userAgent, HttpServletResponse response);
    
    /**
     * User logout
     */
    void logout(String token);
    
    /**
     * Refresh session: revoke old refresh token and issue new access + refresh tokens (rotation).
     */
    LoginResponse refreshLogin(String refreshToken, HttpServletResponse response);
    
    /**
     * Get current user info
     */
    LoginResponse.UserLoginInfo getCurrentUser(String token);
    
    /**
     * Validate token
     */
    boolean validateToken(String token);

    /**
     * Change current user password; on success, current access token is invalidated and re-login is required.
     */
    void changePassword(String accessToken, String oldPassword, String newPassword);
    
    /**
     * Generate a new access token for user info
     */
    String generateAccessTokenForUser(LoginResponse.UserLoginInfo userInfo);

    /**
     * Issue a local session after unified login (SSO) has verified identity (must have admin access role).
     */
    LoginResponse issueSsoSession(String userId, String ipAddress, String userAgent, HttpServletResponse response);
}
