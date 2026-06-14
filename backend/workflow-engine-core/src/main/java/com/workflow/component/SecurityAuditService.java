package com.workflow.component;

import com.workflow.dto.response.SecurityAuditResult;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import com.workflow.exception.WorkflowBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 安全审计协作类
 *
 * 从 {@link SecurityManagerComponent} 拆分而来，负责安全事件记录、安全审计报告生成、
 * 可疑活动检测与安全评分。纯结构搬迁，行为与原实现逐字一致。
 *
 * <p>审计日志写入委托 {@link AuditManagerComponent}；事件统计基于 Redis 中的事件记录。
 */
@Slf4j
@Component
public class SecurityAuditService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AuditManagerComponent auditManagerComponent;

    public SecurityAuditService(StringRedisTemplate stringRedisTemplate,
                                AuditManagerComponent auditManagerComponent) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.auditManagerComponent = auditManagerComponent;
    }

    /**
     * 获取安全审计报告
     */
    public SecurityAuditResult getSecurityAuditReport(LocalDateTime startTime, LocalDateTime endTime) {
        log.info("生成安全审计报告: startTime={}, endTime={}", startTime, endTime);

        try {
            SecurityAuditResult result = SecurityAuditResult.builder()
                    .reportTime(LocalDateTime.now())
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();

            // 统计登录事件
            long successfulLogins = countSecurityEvents("LOGIN_SUCCESS", startTime, endTime);
            long failedLogins = countSecurityEvents("LOGIN_FAILED", startTime, endTime);
            result.setSuccessfulLogins(successfulLogins);
            result.setFailedLogins(failedLogins);

            // 统计权限变更
            long roleAssignments = countSecurityEvents("ROLE_ASSIGNED", startTime, endTime);
            long roleRevocations = countSecurityEvents("ROLE_REVOKED", startTime, endTime);
            result.setRoleAssignments(roleAssignments);
            result.setRoleRevocations(roleRevocations);

            // 检测可疑活动
            List<SecurityAuditResult.SuspiciousActivity> suspiciousActivities =
                    detectSuspiciousActivities(startTime, endTime);
            result.setSuspiciousActivities(suspiciousActivities);

            // 计算安全评分
            int securityScore = calculateSecurityScore(result);
            result.setSecurityScore(securityScore);

            log.info("安全审计报告生成完成: securityScore={}", securityScore);

            return result;

        } catch (Exception e) {
            log.error("生成安全审计报告失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("AUDIT_REPORT_FAILED", "Failed to generate security audit report");
        }
    }

    /**
     * 检测可疑活动
     */
    private List<SecurityAuditResult.SuspiciousActivity> detectSuspiciousActivities(
            LocalDateTime startTime, LocalDateTime endTime) {

        List<SecurityAuditResult.SuspiciousActivity> activities = new ArrayList<>();

        // 检测暴力破解尝试（同一用户多次登录失败）
        Map<String, Long> failedLoginsByUser = getFailedLoginsByUser(startTime, endTime);
        for (Map.Entry<String, Long> entry : failedLoginsByUser.entrySet()) {
            if (entry.getValue() >= 5) {
                activities.add(SecurityAuditResult.SuspiciousActivity.builder()
                        .type("BRUTE_FORCE_ATTEMPT")
                        .description("用户 " + entry.getKey() + " 在时间段内有 " + entry.getValue() + " 次登录失败")
                        .severity("HIGH")
                        .username(entry.getKey())
                        .detectedTime(LocalDateTime.now())
                        .build());
            }
        }

        // 检测异常IP登录
        Map<String, Set<String>> userIpMap = getUserLoginIps(startTime, endTime);
        for (Map.Entry<String, Set<String>> entry : userIpMap.entrySet()) {
            if (entry.getValue().size() > 5) {
                activities.add(SecurityAuditResult.SuspiciousActivity.builder()
                        .type("MULTIPLE_IP_LOGIN")
                        .description("用户 " + entry.getKey() + " 从 " + entry.getValue().size() + " 个不同IP登录")
                        .severity("MEDIUM")
                        .username(entry.getKey())
                        .detectedTime(LocalDateTime.now())
                        .build());
            }
        }

        return activities;
    }

    /**
     * 计算安全评分
     */
    private int calculateSecurityScore(SecurityAuditResult result) {
        int score = 100;

        // 登录失败率影响评分
        long totalLogins = result.getSuccessfulLogins() + result.getFailedLogins();
        if (totalLogins > 0) {
            double failureRate = (double) result.getFailedLogins() / totalLogins;
            if (failureRate > 0.3) {
                score -= 20;
            } else if (failureRate > 0.1) {
                score -= 10;
            }
        }

        // 可疑活动影响评分
        if (result.getSuspiciousActivities() != null) {
            for (SecurityAuditResult.SuspiciousActivity activity : result.getSuspiciousActivities()) {
                if ("HIGH".equals(activity.getSeverity())) {
                    score -= 15;
                } else if ("MEDIUM".equals(activity.getSeverity())) {
                    score -= 10;
                } else {
                    score -= 5;
                }
            }
        }

        return Math.max(0, Math.min(100, score));
    }

    /**
     * 记录安全事件
     */
    public void recordSecurityEvent(String username, String eventType, String description, String ipAddress) {
        try {
            // 存储到Redis用于统计
            String eventKey = "security:event:" + eventType + ":" + username + ":" + System.currentTimeMillis();
            Map<String, String> eventData = new HashMap<>();
            eventData.put("username", username);
            eventData.put("eventType", eventType);
            eventData.put("description", description);
            eventData.put("ipAddress", ipAddress != null ? ipAddress : "unknown");
            eventData.put("timestamp", LocalDateTime.now().toString());

            stringRedisTemplate.opsForHash().putAll(eventKey, eventData);
            stringRedisTemplate.expire(eventKey, Duration.ofDays(30));

            // 记录到审计日志
            AuditOperationType operationType = mapEventTypeToAuditOperation(eventType);
            if (operationType != null) {
                auditManagerComponent.recordAuditLog(
                        operationType,
                        AuditResourceType.USER,
                        username,
                        username,
                        eventType.contains("SUCCESS") ? "SUCCESS" : "FAILED"
                );
            }

        } catch (Exception e) {
            log.error("记录安全事件失败: username={}, eventType={}", username, eventType, e);
        }
    }

    /**
     * 统计安全事件数量
     */
    private long countSecurityEvents(String eventType, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            Set<String> keys = stringRedisTemplate.keys("security:event:" + eventType + ":*");
            if (keys == null) {
                return 0;
            }

            long count = 0;
            for (String key : keys) {
                Object timestampObj = stringRedisTemplate.opsForHash().get(key, "timestamp");
                if (timestampObj != null) {
                    LocalDateTime eventTime = LocalDateTime.parse(timestampObj.toString());
                    if (!eventTime.isBefore(startTime) && !eventTime.isAfter(endTime)) {
                        count++;
                    }
                }
            }

            return count;

        } catch (Exception e) {
            log.error("统计安全事件失败: eventType={}", eventType, e);
            return 0;
        }
    }

    /**
     * 获取用户登录失败次数
     */
    private Map<String, Long> getFailedLoginsByUser(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Long> result = new HashMap<>();

        try {
            Set<String> keys = stringRedisTemplate.keys("security:event:LOGIN_FAILED:*");
            if (keys == null) {
                return result;
            }

            for (String key : keys) {
                Map<Object, Object> eventData = stringRedisTemplate.opsForHash().entries(key);
                Object timestampObj = eventData.get("timestamp");
                Object usernameObj = eventData.get("username");

                if (timestampObj != null && usernameObj != null) {
                    LocalDateTime eventTime = LocalDateTime.parse(timestampObj.toString());
                    if (!eventTime.isBefore(startTime) && !eventTime.isAfter(endTime)) {
                        String username = usernameObj.toString();
                        result.merge(username, 1L, Long::sum);
                    }
                }
            }

        } catch (Exception e) {
            log.error("获取用户登录失败次数失败", e);
        }

        return result;
    }

    /**
     * 获取用户登录IP
     */
    private Map<String, Set<String>> getUserLoginIps(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Set<String>> result = new HashMap<>();

        try {
            Set<String> keys = stringRedisTemplate.keys("security:event:LOGIN_SUCCESS:*");
            if (keys == null) {
                return result;
            }

            for (String key : keys) {
                Map<Object, Object> eventData = stringRedisTemplate.opsForHash().entries(key);
                Object timestampObj = eventData.get("timestamp");
                Object usernameObj = eventData.get("username");
                Object ipAddressObj = eventData.get("ipAddress");

                if (timestampObj != null && usernameObj != null && ipAddressObj != null) {
                    LocalDateTime eventTime = LocalDateTime.parse(timestampObj.toString());
                    if (!eventTime.isBefore(startTime) && !eventTime.isAfter(endTime)) {
                        String username = usernameObj.toString();
                        String ipAddress = ipAddressObj.toString();
                        result.computeIfAbsent(username, k -> new HashSet<>()).add(ipAddress);
                    }
                }
            }

        } catch (Exception e) {
            log.error("获取用户登录IP失败", e);
        }

        return result;
    }

    /**
     * 映射事件类型到审计操作类型
     */
    private AuditOperationType mapEventTypeToAuditOperation(String eventType) {
        return switch (eventType) {
            case "LOGIN_SUCCESS", "LDAP_LOGIN_SUCCESS", "SSO_LOGIN_SUCCESS" -> AuditOperationType.LOGIN;
            case "LOGOUT" -> AuditOperationType.LOGOUT;
            case "ROLE_ASSIGNED" -> AuditOperationType.ASSIGN_ROLE;
            case "ROLE_REVOKED" -> AuditOperationType.REVOKE_ROLE;
            default -> null;
        };
    }
}
