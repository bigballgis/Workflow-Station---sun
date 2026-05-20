package com.developer.controller;

import com.developer.client.AdminCenterSsoClient;
import com.developer.dto.LoginResponse;
import com.developer.entity.User;
import com.developer.repository.UserRepository;
import com.developer.service.DeveloperSsoExchangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 仅用于本地/DEV：统一 /login 回调换设计器 JWT。SIT/UAT/PROD 不部署本服务时可忽略。
 */
@Slf4j
@RestController
@RequestMapping("/auth/sso")
@RequiredArgsConstructor
@ConditionalOnBean(AdminCenterSsoClient.class)
public class AuthSsoExchangeController {

    private final AdminCenterSsoClient adminCenterSsoClient;
    private final UserRepository userRepository;
    private final DeveloperSsoExchangeService developerSsoExchangeService;

    @PostMapping("/exchange")
    public ResponseEntity<LoginResponse> exchange(@RequestBody SsoExchangeBody body, HttpServletRequest httpRequest) {
        if (body == null || body.getCode() == null || body.getCode().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            AdminCenterSsoClient.SsoRedeemResult redeemed = adminCenterSsoClient.redeemDeveloperCode(body.getCode());
            User user = userRepository.findById(redeemed.userId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            if (user.isLocked()) {
                return ResponseEntity.badRequest().build();
            }
            if ("DISABLED".equals(user.getStatus())) {
                return ResponseEntity.badRequest().build();
            }
            user.resetFailedLoginCount();
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(getClientIpAddress(httpRequest));
            userRepository.save(user);
            return ResponseEntity.ok(developerSsoExchangeService.issueSession(user));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Developer SSO exchange failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Data
    public static class SsoExchangeBody {
        private String code;
        private String state;
    }
}
