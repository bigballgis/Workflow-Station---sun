package com.platform.security.service.impl;

import com.platform.common.dto.UserPrincipal;
import com.platform.security.config.JwtProperties;
import com.platform.security.dto.*;
import com.platform.security.exception.AuthErrorCode;
import com.platform.security.exception.AuthenticationException;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import com.platform.security.repository.UserRepository;
import com.platform.security.service.AuthenticationService;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Authentication service implementation.
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 3.1, 4.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;
    private final LoginAuditService loginAuditService;
    private final UserRoleService userRoleService;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password, String ipAddress, String userAgent) {
        log.debug("Login attempt for user: {}", username);

        // Find user by username
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    loginAuditService.recordLoginFailure(username, ipAddress, userAgent, "User not found");
                    return new AuthenticationException(AuthErrorCode.AUTH_001);
                });

        // Check account status
        if (user.isLocked()) {
            loginAuditService.recordLoginFailure(username, ipAddress, userAgent, "Account locked");
            throw new AuthenticationException(AuthErrorCode.AUTH_002);
        }

        if (user.isInactive()) {
            loginAuditService.recordLoginFailure(username, ipAddress, userAgent, "Account inactive");
            throw new AuthenticationException(AuthErrorCode.AUTH_003);
        }

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAuditService.recordLoginFailure(username, ipAddress, userAgent, "Invalid password");
            throw new AuthenticationException(AuthErrorCode.AUTH_001);
        }

        // Generate tokens
        List<String> roles = new ArrayList<>(user.getRoles());
        List<String> permissions = getPermissionsForRoles(roles);

        String accessToken = ((JwtTokenServiceImpl) jwtTokenService).generateToken(
                user.getId().toString(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                roles,
                permissions,
                user.getLanguage()
        );

        String refreshToken = jwtTokenService.generateRefreshToken(user.getId().toString());

        // Record successful login
        loginAuditService.recordLoginSuccess(user.getId(), username, ipAddress, userAgent);

        log.info("User {} logged in successfully from {}", username, ipAddress);

        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtProperties.getExpirationMs() / 1000,
                UserInfo.fromUser(user, permissions)
        );
    }

    @Override
    public void logout(String token, String ipAddress) {
        try {
            String userId = jwtTokenService.extractUserId(token);
            jwtTokenService.blacklistToken(token);
            loginAuditService.recordLogout(userId, ipAddress);
            log.info("User {} logged out from {}", userId, ipAddress);
        } catch (Exception e) {
            log.warn("Logout failed: {}", e.getMessage());
            // Still blacklist the token even if we can't extract user info
            jwtTokenService.blacklistToken(token);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse refreshToken(String refreshToken) {
        // Validate refresh token
        if (!jwtTokenService.validateToken(refreshToken)) {
            if (jwtTokenService.isTokenExpired(refreshToken)) {
                throw new AuthenticationException(AuthErrorCode.AUTH_007);
            }
            throw new AuthenticationException(AuthErrorCode.AUTH_008);
        }

        // Extract user ID and fetch fresh user data
        String userId = jwtTokenService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException(AuthErrorCode.AUTH_009));

        // Check account status
        if (!user.isActive()) {
            throw new AuthenticationException(
                    user.isLocked() ? AuthErrorCode.AUTH_002 : AuthErrorCode.AUTH_003
            );
        }

        // Generate new access token with fresh user data
        List<String> roles = new ArrayList<>(user.getRoles());
        List<String> permissions = getPermissionsForRoles(roles);

        String newAccessToken = jwtTokenService.generateToken(
                user.getId().toString(),
                user.getUsername(),
                roles,
                permissions,
                user.getLanguage()
        );

        log.debug("Token refreshed for user: {}", user.getUsername());

        return new TokenResponse(
                newAccessToken,
                jwtProperties.getExpirationMs() / 1000
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfo getCurrentUser(String token) {
        if (!jwtTokenService.validateToken(token)) {
            if (jwtTokenService.isTokenExpired(token)) {
                throw new AuthenticationException(AuthErrorCode.AUTH_004);
            }
            if (jwtTokenService.isBlacklisted(token)) {
                throw new AuthenticationException(AuthErrorCode.AUTH_006);
            }
            throw new AuthenticationException(AuthErrorCode.AUTH_005);
        }

        String userId = jwtTokenService.extractUserId(token);
        
        // Fetch fresh user data from database
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException(AuthErrorCode.AUTH_001));
        
        List<String> roles = new ArrayList<>(user.getRoles());
        List<String> permissions = getPermissionsForRoles(roles);
        
        return new UserInfo(
                user.getId().toString(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                roles,
                permissions,
                user.getLanguage()
        );
    }

    @Override
    public boolean isTokenValid(String token) {
        return jwtTokenService.validateToken(token);
    }

    /**
     * Get permissions for the given roles from database.
     * Queries permissions from sys_role_permissions table based on role codes.
     */
    private List<String> getPermissionsForRoles(List<String> roles) {
        return userRoleService.getPermissionsForRoleCodes(roles);
    }
}
