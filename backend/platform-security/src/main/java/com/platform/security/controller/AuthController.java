package com.platform.security.controller;

import com.platform.security.dto.*;
import com.platform.security.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication REST controller.
 * Validates: Requirements 2.1, 3.1, 4.1
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    /**
     * Login endpoint.
     * POST /api/v1/auth/login
     * Validates: Requirements 2.1
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        log.debug("Login request from IP: {}", ipAddress);
        
        LoginResponse response = authenticationService.login(
                request.username(),
                request.password(),
                ipAddress,
                userAgent
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint.
     * POST /api/v1/auth/logout
     * Validates: Requirements 3.1
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {
        
        String token = extractToken(authHeader);
        String ipAddress = getClientIpAddress(httpRequest);
        
        authenticationService.logout(token, ipAddress);
        
        return ResponseEntity.ok().build();
    }

    /**
     * Refresh token endpoint.
     * POST /api/v1/auth/refresh
     * Validates: Requirements 4.1
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody RefreshRequest request) {
        
        TokenResponse response = authenticationService.refreshToken(request.refreshToken());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get current user info endpoint.
     * GET /api/v1/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfo> getCurrentUser(
            @RequestHeader("Authorization") String authHeader) {
        
        String token = extractToken(authHeader);
        UserInfo userInfo = authenticationService.getCurrentUser(token);
        
        return ResponseEntity.ok(userInfo);
    }

    /**
     * Validate token endpoint.
     * GET /api/v1/auth/validate
     */
    @GetMapping("/validate")
    public ResponseEntity<Boolean> validateToken(
            @RequestHeader("Authorization") String authHeader) {
        
        String token = extractToken(authHeader);
        boolean isValid = authenticationService.isTokenValid(token);
        
        return ResponseEntity.ok(isValid);
    }

    /**
     * Extract token from Authorization header.
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }

    /**
     * Get client IP address from request.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
