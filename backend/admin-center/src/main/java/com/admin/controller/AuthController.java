package com.admin.controller;

import com.admin.dto.request.LoginRequest;
import com.admin.dto.response.ErrorResponse;
import com.admin.dto.response.LoginResponse;
import com.admin.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录（需 Admin Center 角色）
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String path = safeRequestPath(httpRequest);
        String ipAddress = getClientIpAddress(httpRequest);

        try {
            LoginResponse response = authService.login(request, ipAddress, httpRequest.getHeader("User-Agent"));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(safeError("LOGIN_FAILED", e.getMessage(), path));
        } catch (Exception e) {
            String user = (request != null && request.getUsername() != null) ? request.getUsername() : "?";
            log.error("Login error for user {}: {}", user, e.getMessage(), e);
            return ResponseEntity.badRequest().body(safeError("LOGIN_FAILED", "登录失败，请稍后重试", path));
        }
    }

    /**
     * Developer Workstation 登录（仅校验用户名密码，不要求 Admin Center 角色）
     */
    @PostMapping("/developer-login")
    public ResponseEntity<?> developerLogin(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String path = safeRequestPath(httpRequest);
        String ipAddress = getClientIpAddress(httpRequest);

        try {
            LoginResponse response = authService.loginForDeveloper(request, ipAddress, httpRequest.getHeader("User-Agent"));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("Developer login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(safeError("LOGIN_FAILED", e.getMessage(), path));
        } catch (Exception e) {
            String user = (request != null && request.getUsername() != null) ? request.getUsername() : "?";
            log.error("Developer login error for user {}: {}", user, e.getMessage(), e);
            return ResponseEntity.badRequest().body(safeError("LOGIN_FAILED", "登录失败，请稍后重试", path));
        }
    }

    private static String safeRequestPath(HttpServletRequest req) {
        try {
            return req != null && req.getRequestURI() != null ? req.getRequestURI() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static ErrorResponse safeError(String code, String message, String path) {
        return ErrorResponse.builder()
                .code(code)
                .message(message != null ? message : "登录失败")
                .timestamp(Instant.now())
                .path(path)
                .build();
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        
        return ResponseEntity.ok().build();
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @RequestBody Map<String, String> request) {
        
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            LoginResponse.UserLoginInfo userInfo = authService.refreshToken(refreshToken);
            return ResponseEntity.ok(Map.of(
                    "accessToken", refreshToken, // 简化实现
                    "expiresIn", 86400,
                    "user", userInfo
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).build();
        }
    }

    /**
     * 获取当前用户信息
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
     * 验证令牌
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
