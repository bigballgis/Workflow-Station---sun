package com.admin.controller;

import com.admin.dto.request.ChangePasswordRequest;
import com.admin.dto.request.LoginRequest;
import com.admin.dto.response.LoginResponse;
import com.admin.service.AuthService;
import com.platform.security.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication controller
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    /**
     * User login
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        log.debug("Login request from IP: {}", ipAddress);
        
        try {
            LoginResponse response = authService.login(request, ipAddress, userAgent, httpResponse);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("Login failed for user {}: {}", request.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(
                    LoginResponse.builder()
                            .error(e.getMessage())
                            .build()
            );
        }
    }

    /**
     * User logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // If Authorization header present, blacklist the provided bearer token
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }

        // Also inspect cookies (access + refresh) and blacklist them if present
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            List<String> accessNames = jwtProperties.getCookieNames();
            String refreshName = jwtProperties.getRefreshCookieName();
            for (jakarta.servlet.http.Cookie c : cookies) {
                String name = c.getName();
                String val = c.getValue();
                if (val == null || val.isBlank()) continue;
                if (accessNames != null && accessNames.contains(name)) {
                    try { authService.logout(val); } catch (Exception ignored) {}
                }
                if (refreshName != null && refreshName.equals(name)) {
                    try { authService.logout(val); } catch (Exception ignored) {}
                }
            }
        }

        // Clear authentication cookies for this service (access + refresh)
        clearAuthCookies(response);

        return ResponseEntity.ok().build();
    }

    private void clearAuthCookies(HttpServletResponse response) {
        List<String> names = jwtProperties.getCookieNames();
        if (names != null) {
            for (String n : names) {
                jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(n, "");
                cookie.setPath("/");
                cookie.setHttpOnly(true);
                cookie.setMaxAge(0);
                cookie.setSecure(false);
                cookie.setAttribute("SameSite", "Lax");
                response.addCookie(cookie);
            }
        }
        String refresh = jwtProperties.getRefreshCookieName();
        if (refresh != null) {
            jakarta.servlet.http.Cookie rc = new jakarta.servlet.http.Cookie(refresh, "");
            rc.setPath("/");
            rc.setHttpOnly(true);
            rc.setMaxAge(0);
            rc.setSecure(false);
            rc.setAttribute("SameSite", "Lax");
            response.addCookie(rc);
        }
    }

    /**
     * Refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @RequestBody Map<String, String> request,
            HttpServletResponse httpResponse) {
        
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            return ResponseEntity.ok(authService.refreshLogin(refreshToken, httpResponse));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).build();
        }
    }

    /**
     * Get current user info
     */
    @GetMapping("/me")
    public ResponseEntity<LoginResponse.UserLoginInfo> getCurrentUser(
            @RequestHeader("Authorization") String authHeader) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        
        String token = authHeader.substring(7);
        
        try {
            LoginResponse.UserLoginInfo userInfo = authService.getCurrentUser(token);
            return ResponseEntity.ok(userInfo);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).build();
        }
    }

    /**
     * Validate token
     */
    @GetMapping("/validate")
    public ResponseEntity<Boolean> validateToken(
            @RequestHeader("Authorization") String authHeader) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.ok(false);
        }
        
        String token = authHeader.substring(7);
        boolean isValid = authService.validateToken(token);
        
        return ResponseEntity.ok(isValid);
    }

    /**
     * Change current user password; on success, current access token is invalidated and re-login is required.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody ChangePasswordRequest body) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);
        try {
            authService.changePassword(token, body.getOldPassword(), body.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if ("INVALID_OLD_PASSWORD".equals(msg) || "USER_NOT_FOUND".equals(msg)) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.status(401).build();
        }
    }

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
