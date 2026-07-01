package com.portal.controller;

import com.platform.security.entity.LoginAudit;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import com.platform.security.repository.UserRepository;
import com.portal.client.AdminCenterSsoClient;
import com.portal.dto.LoginResponse;
import com.portal.dto.SsoExchangeRequest;
import com.portal.repository.LoginAuditQueryRepository;
import com.portal.service.PortalSessionIssuerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified /login callback: exchanges admin-center-issued code for portal JWT;
 * workspace selection logic runs here (same as password login).
 */
@Slf4j
@RestController
@RequestMapping("/auth/sso")
@RequiredArgsConstructor
public class AuthSsoExchangeController {

    private static final long PENDING_TTL_SECONDS = 300;

    private final AdminCenterSsoClient adminCenterSsoClient;
    private final UserRepository userRepository;
    private final LoginAuditQueryRepository loginAuditQueryRepository;
    private final PortalSessionIssuerService portalSessionIssuerService;

    private final ConcurrentHashMap<String, PendingRedeem> pendingRedeems = new ConcurrentHashMap<>();

    @PostMapping("/exchange")
    public ResponseEntity<LoginResponse> exchange(
            @Valid @RequestBody SsoExchangeRequest body,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        try {
            String userId = resolveUserId(body.getCode());
            User user = userRepository.findById(userId)
                    .orElse(null);

            if (user == null) {
                recordLoginAuditFailure(null, userId, ipAddress, userAgent, "User not found");
                throw new IllegalArgumentException("User not found");
            }
            if (user.isLocked()) {
                recordLoginAuditFailure(user.getId(), user.getUsername(), ipAddress, userAgent,
                        "Account is locked");
                return ResponseEntity.badRequest().body(LoginResponse.builder()
                        .message("Account is locked")
                        .build());
            }
            if (UserStatus.INACTIVE.equals(user.getStatus())) {
                recordLoginAuditFailure(user.getId(), user.getUsername(), ipAddress, userAgent,
                        "Account is disabled");
                return ResponseEntity.badRequest().body(LoginResponse.builder()
                        .message("Account is disabled")
                        .build());
            }

            user.resetFailedLoginCount();
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(ipAddress);
            userRepository.save(user);

            // Record successful SSO login audit
            recordLoginAuditSuccess(user.getId(), user.getUsername(), ipAddress, userAgent);

            ResponseEntity<LoginResponse> response = portalSessionIssuerService.issuePortalSession(
                    user,
                    body.getWorkspaceBusinessUnitId(),
                    body.getWorkspaceRoleId(),
                    httpRequest,
                    httpResponse,
                    user.getUsername());

            LoginResponse responseBody = response.getBody();
            if (responseBody != null && "WORKSPACE_CONTEXT_REQUIRED".equals(responseBody.getLoginErrorCode())) {
                pendingRedeems.put(body.getCode(), new PendingRedeem(userId, Instant.now()));
            } else {
                pendingRedeems.remove(body.getCode());
            }

            return response;
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Portal SSO exchange failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(LoginResponse.builder()
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Resolves userId from pending cache first (already redeemed on first exchange
     * but workspace selection needed), falls back to admin-center redeem on cache miss.
     */
    private String resolveUserId(String code) {
        evictExpired();
        PendingRedeem cached = pendingRedeems.get(code);
        if (cached != null && !cached.isExpired()) {
            return cached.userId;
        }
        pendingRedeems.remove(code);
        AdminCenterSsoClient.SsoRedeemResult redeemed = adminCenterSsoClient.redeemPortalCode(code);
        return redeemed.userId();
    }

    private void evictExpired() {
        pendingRedeems.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private record PendingRedeem(String userId, Instant createdAt) {
        boolean isExpired() {
            return Instant.now().getEpochSecond() - createdAt.getEpochSecond() > PENDING_TTL_SECONDS;
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Record a successful login audit entry.
     */
    private void recordLoginAuditSuccess(String userId, String username, String ipAddress, String userAgent) {
        try {
            LoginAudit audit = LoginAudit.builder()
                    .userId(userId)
                    .username(username)
                    .action(LoginAudit.AuditAction.LOGIN)
                    .ipAddress(ipAddress)
                    .userAgent(truncateUserAgent(userAgent))
                    .success(true)
                    .build();
            loginAuditQueryRepository.save(audit);
            log.debug("Recorded SSO login success audit for user: {}", username);
        } catch (Exception e) {
            log.error("Failed to record SSO login success audit: {}", e.getMessage());
        }
    }

    private void recordLoginAuditFailure(String userId, String username, String ipAddress,
                                         String userAgent, String reason) {
        try {
            LoginAudit audit = LoginAudit.builder()
                    .userId(userId)
                    .username(username)
                    .action(LoginAudit.AuditAction.LOGIN)
                    .ipAddress(ipAddress)
                    .userAgent(truncateUserAgent(userAgent))
                    .success(false)
                    .failureReason(reason != null && reason.length() > 255
                            ? reason.substring(0, 255) : reason)
                    .build();
            loginAuditQueryRepository.save(audit);
            log.debug("Recorded SSO login failure audit for user: {}, reason: {}", username, reason);
        } catch (Exception e) {
            log.error("Failed to record SSO login failure audit: {}", e.getMessage());
        }
    }

    /**
     * Truncate user agent string to prevent database overflow.
     */
    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }
}
