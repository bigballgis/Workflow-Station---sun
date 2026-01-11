package com.platform.security.service.impl;

import com.platform.security.model.LoginAudit;
import com.platform.security.repository.LoginAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for recording login audit events.
 * Validates: Requirements 2.5, 3.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAuditService {

    private final LoginAuditRepository loginAuditRepository;

    /**
     * Record successful login.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginSuccess(String userId, String username, String ipAddress, String userAgent) {
        try {
            LoginAudit audit = LoginAudit.builder()
                    .userId(userId)
                    .username(username)
                    .action(LoginAudit.AuditAction.LOGIN)
                    .ipAddress(ipAddress)
                    .userAgent(truncateUserAgent(userAgent))
                    .success(true)
                    .build();
            loginAuditRepository.save(audit);
            log.debug("Recorded login success for user: {}", username);
        } catch (Exception e) {
            log.error("Failed to record login success audit: {}", e.getMessage());
        }
    }

    /**
     * Record failed login attempt.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(String username, String ipAddress, String userAgent, String reason) {
        try {
            LoginAudit audit = LoginAudit.builder()
                    .username(username)
                    .action(LoginAudit.AuditAction.LOGIN)
                    .ipAddress(ipAddress)
                    .userAgent(truncateUserAgent(userAgent))
                    .success(false)
                    .failureReason(reason)
                    .build();
            loginAuditRepository.save(audit);
            log.debug("Recorded login failure for user: {}, reason: {}", username, reason);
        } catch (Exception e) {
            log.error("Failed to record login failure audit: {}", e.getMessage());
        }
    }

    /**
     * Record logout.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogout(String userId, String ipAddress) {
        try {
            LoginAudit audit = LoginAudit.builder()
                    .userId(userId)
                    .username(userId) // Will be updated if we have username
                    .action(LoginAudit.AuditAction.LOGOUT)
                    .ipAddress(ipAddress)
                    .success(true)
                    .build();
            loginAuditRepository.save(audit);
            log.debug("Recorded logout for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to record logout audit: {}", e.getMessage());
        }
    }

    /**
     * Record token refresh.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTokenRefresh(String userId, String ipAddress) {
        try {
            LoginAudit audit = LoginAudit.builder()
                    .userId(userId)
                    .username(userId)
                    .action(LoginAudit.AuditAction.TOKEN_REFRESH)
                    .ipAddress(ipAddress)
                    .success(true)
                    .build();
            loginAuditRepository.save(audit);
            log.debug("Recorded token refresh for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to record token refresh audit: {}", e.getMessage());
        }
    }

    /**
     * Truncate user agent to prevent database overflow.
     */
    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }
}
