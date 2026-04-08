package com.portal.controller;

import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import com.platform.security.repository.UserRepository;
import com.portal.client.AdminCenterSsoClient;
import com.portal.dto.LoginResponse;
import com.portal.dto.SsoExchangeRequest;
import com.portal.service.PortalSessionIssuerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 统一 /login 回调：用 admin-center 签发的 code 换门户 JWT；工作台多选逻辑在本接口完成（与密码登录一致）。
 */
@Slf4j
@RestController
@RequestMapping("/auth/sso")
@RequiredArgsConstructor
public class AuthSsoExchangeController {

    private final AdminCenterSsoClient adminCenterSsoClient;
    private final UserRepository userRepository;
    private final PortalSessionIssuerService portalSessionIssuerService;

    @PostMapping("/exchange")
    public ResponseEntity<LoginResponse> exchange(
            @Valid @RequestBody SsoExchangeRequest body,
            HttpServletRequest httpRequest) {
        try {
            AdminCenterSsoClient.SsoRedeemResult redeemed = adminCenterSsoClient.redeemPortalCode(body.getCode());
            User user = userRepository.findById(redeemed.userId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            if (user.isLocked()) {
                return ResponseEntity.badRequest().body(LoginResponse.builder()
                        .message("Account is locked")
                        .build());
            }
            if (UserStatus.INACTIVE.equals(user.getStatus())) {
                return ResponseEntity.badRequest().body(LoginResponse.builder()
                        .message("Account is disabled")
                        .build());
            }

            user.resetFailedLoginCount();
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(getClientIpAddress(httpRequest));
            userRepository.save(user);

            return portalSessionIssuerService.issuePortalSession(
                    user,
                    body.getWorkspaceBusinessUnitId(),
                    body.getWorkspaceRoleId(),
                    httpRequest,
                    user.getUsername());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Portal SSO exchange failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(LoginResponse.builder()
                    .message(e.getMessage())
                    .build());
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
