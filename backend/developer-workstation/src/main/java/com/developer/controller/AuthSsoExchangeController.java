package com.developer.controller;

import com.developer.client.AdminCenterSsoClient;
import com.developer.dto.LoginResponse;
import com.developer.entity.User;
import com.developer.repository.UserRepository;
import com.developer.security.FunctionUnitWorkspaceAccessDeniedException;
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
 * Used only for local/DEV: unified /login callback to exchange for designer JWT.
 * Can be ignored when this service is not deployed in SIT/UAT/PROD.
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
    public ResponseEntity<LoginResponse> exchange(@RequestBody SsoExchangeBody body,
                                                  HttpServletRequest httpRequest,
                                                  HttpServletResponse response) {
        if (body == null || body.getCode() == null || body.getCode().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            AdminCenterSsoClient.SsoRedeemResult redeemed = adminCenterSsoClient.redeemDeveloperCode(body.getCode());
            User user = userRepository.findById(redeemed.userId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            // Same account rule as password sign-in: a locked or non-ACTIVE account gets no session.
            if (!"ACTIVE".equals(user.getStatus()) || user.isLocked()) {
                return ResponseEntity.badRequest().build();
            }
            // Entry is checked before any cookie is written; a refused exchange leaves no sign-in record.
            LoginResponse session = developerSsoExchangeService.issueSession(user, response);
            user.resetFailedLoginCount();
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(getClientIpAddress(httpRequest));
            userRepository.save(user);
            return ResponseEntity.ok(session);
        } catch (FunctionUnitWorkspaceAccessDeniedException e) {
            log.warn("Developer SSO exchange denied: {}", e.getMessage());
            return ResponseEntity.status(403).build();
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
