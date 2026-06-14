package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全事件监控与告警器
 *
 * <p>由 {@link DataAccessSecurityComponent} 拆分而来，承载"安全事件监控和告警"职责。
 * 逻辑/异常/日志逐字保留，行为零变化。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityEventMonitor {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AuditManagerComponent auditManagerComponent;

    // 缓存键前缀
    private static final String SECURITY_EVENT_PREFIX = "security:event:";
    private static final String ALERT_PREFIX = "security:alert:";

    // 内存缓存
    private final Map<String, AlertRule> alertRules = new ConcurrentHashMap<>();

    /**
     * 定义告警规则
     */
    public void defineAlertRule(AlertRule rule) {
        log.info("定义告警规则: ruleId={}, eventType={}, threshold={}",
                rule.getRuleId(), rule.getEventType(), rule.getThreshold());
        alertRules.put(rule.getRuleId(), rule);

        try {
            String cacheKey = ALERT_PREFIX + "rule:" + rule.getRuleId();
            String ruleJson = objectMapper.writeValueAsString(rule);
            stringRedisTemplate.opsForValue().set(cacheKey, ruleJson, Duration.ofDays(30));
        } catch (JsonProcessingException e) {
            log.error("缓存告警规则失败: ruleId={}", rule.getRuleId(), e);
        }
    }

    /**
     * 记录安全事件
     */
    public void recordSecurityEvent(String username, String eventType, String description) {
        recordSecurityEvent(username, eventType, description, null, null, null);
    }

    /**
     * 记录安全事件（完整版）
     */
    public void recordSecurityEvent(String username, String eventType, String description,
                                    String ipAddress, String resource, Map<String, Object> details) {
        log.info("记录安全事件: username={}, eventType={}", username, eventType);

        SecurityEvent event = new SecurityEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(eventType);
        event.setUsername(username);
        event.setIpAddress(ipAddress);
        event.setResource(resource);
        event.setDescription(description);
        event.setDetails(details);
        event.setEventTime(LocalDateTime.now());

        // 存储事件
        try {
            String eventKey = SECURITY_EVENT_PREFIX + eventType + ":" + event.getEventId();
            String eventJson = objectMapper.writeValueAsString(event);
            stringRedisTemplate.opsForValue().set(eventKey, eventJson, Duration.ofDays(30));

            // 更新事件计数
            String countKey = SECURITY_EVENT_PREFIX + "count:" + eventType + ":" + username;
            stringRedisTemplate.opsForValue().increment(countKey);
            stringRedisTemplate.expire(countKey, Duration.ofHours(1));

        } catch (JsonProcessingException e) {
            log.error("存储安全事件失败: eventId={}", event.getEventId(), e);
        }

        // 检查是否需要触发告警
        checkAndTriggerAlerts(event);

        // 记录到审计日志
        auditManagerComponent.recordAuditLog(
                AuditOperationType.SECURITY_EVENT,
                AuditResourceType.SYSTEM,
                resource != null ? resource : "SECURITY",
                username,
                eventType
        );
    }

    /**
     * 检查并触发告警
     */
    private void checkAndTriggerAlerts(SecurityEvent event) {
        for (AlertRule rule : alertRules.values()) {
            if (!rule.isEnabled() || !rule.getEventType().equals(event.getEventType())) {
                continue;
            }

            // 获取时间窗口内的事件计数
            int eventCount = getEventCount(event.getEventType(), event.getUsername(),
                    rule.getTimeWindowMinutes());

            if (eventCount >= rule.getThreshold()) {
                triggerAlert(rule, event, eventCount);
            }
        }
    }

    /**
     * 获取事件计数
     */
    private int getEventCount(String eventType, String username, int timeWindowMinutes) {
        try {
            String countKey = SECURITY_EVENT_PREFIX + "count:" + eventType + ":" + username;
            String countStr = stringRedisTemplate.opsForValue().get(countKey);
            return countStr != null ? Integer.parseInt(countStr) : 0;
        } catch (Exception e) {
            log.error("获取事件计数失败", e);
            return 0;
        }
    }

    /**
     * 触发告警
     */
    private void triggerAlert(AlertRule rule, SecurityEvent event, int eventCount) {
        log.warn("触发安全告警: ruleId={}, eventType={}, username={}, count={}",
                rule.getRuleId(), event.getEventType(), event.getUsername(), eventCount);

        // 创建告警记录
        Map<String, Object> alertData = new HashMap<>();
        alertData.put("alertId", UUID.randomUUID().toString());
        alertData.put("ruleId", rule.getRuleId());
        alertData.put("ruleName", rule.getRuleName());
        alertData.put("eventType", event.getEventType());
        alertData.put("username", event.getUsername());
        alertData.put("eventCount", eventCount);
        alertData.put("threshold", rule.getThreshold());
        alertData.put("severity", rule.getSeverity());
        alertData.put("triggeredTime", LocalDateTime.now().toString());
        alertData.put("description", String.format("用户 %s 在 %d 分钟内触发了 %d 次 %s 事件，超过阈值 %d",
                event.getUsername(), rule.getTimeWindowMinutes(), eventCount,
                event.getEventType(), rule.getThreshold()));

        // 存储告警
        try {
            String alertKey = ALERT_PREFIX + "triggered:" + alertData.get("alertId");
            String alertJson = objectMapper.writeValueAsString(alertData);
            stringRedisTemplate.opsForValue().set(alertKey, alertJson, Duration.ofDays(7));
        } catch (JsonProcessingException e) {
            log.error("存储告警失败", e);
        }

        // 发送通知
        sendAlertNotifications(rule, alertData);
    }

    /**
     * 发送告警通知
     */
    private void sendAlertNotifications(AlertRule rule, Map<String, Object> alertData) {
        if (rule.getNotifyChannels() == null || rule.getNotifyChannels().isEmpty()) {
            return;
        }

        for (String channel : rule.getNotifyChannels()) {
            switch (channel) {
                case "EMAIL":
                    sendEmailAlert(rule, alertData);
                    break;
                case "SMS":
                    sendSmsAlert(rule, alertData);
                    break;
                case "WEBHOOK":
                    sendWebhookAlert(rule, alertData);
                    break;
                default:
                    log.warn("未知的通知渠道: {}", channel);
            }
        }
    }

    /**
     * 发送邮件告警
     */
    private void sendEmailAlert(AlertRule rule, Map<String, Object> alertData) {
        log.info("发送邮件告警: ruleId={}, users={}", rule.getRuleId(), rule.getNotifyUsers());
        // 实际实现中应该调用邮件服务
    }

    /**
     * 发送短信告警
     */
    private void sendSmsAlert(AlertRule rule, Map<String, Object> alertData) {
        log.info("发送短信告警: ruleId={}, users={}", rule.getRuleId(), rule.getNotifyUsers());
        // 实际实现中应该调用短信服务
    }

    /**
     * 发送Webhook告警
     */
    private void sendWebhookAlert(AlertRule rule, Map<String, Object> alertData) {
        log.info("发送Webhook告警: ruleId={}", rule.getRuleId());
        // 实际实现中应该调用HTTP客户端
    }

    /**
     * 查询安全事件
     */
    public List<SecurityEvent> querySecurityEvents(String eventType, String username,
                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                   int limit) {
        log.info("查询安全事件: eventType={}, username={}", eventType, username);

        List<SecurityEvent> events = new ArrayList<>();

        try {
            String pattern = SECURITY_EVENT_PREFIX + (eventType != null ? eventType : "*") + ":*";
            Set<String> keys = stringRedisTemplate.keys(pattern);

            if (keys == null || keys.isEmpty()) {
                return events;
            }

            for (String key : keys) {
                String eventJson = stringRedisTemplate.opsForValue().get(key);
                if (eventJson != null) {
                    SecurityEvent event = objectMapper.readValue(eventJson, SecurityEvent.class);

                    // 过滤条件
                    if (username != null && !username.equals(event.getUsername())) {
                        continue;
                    }
                    if (startTime != null && event.getEventTime().isBefore(startTime)) {
                        continue;
                    }
                    if (endTime != null && event.getEventTime().isAfter(endTime)) {
                        continue;
                    }

                    events.add(event);

                    if (events.size() >= limit) {
                        break;
                    }
                }
            }

            // 按时间排序
            events.sort((e1, e2) -> e2.getEventTime().compareTo(e1.getEventTime()));

        } catch (Exception e) {
            log.error("查询安全事件失败", e);
        }

        return events;
    }

    /**
     * 获取告警列表
     */
    public List<Map<String, Object>> getAlerts(String severity, LocalDateTime startTime,
                                               LocalDateTime endTime, int limit) {
        log.info("获取告警列表: severity={}", severity);

        List<Map<String, Object>> alerts = new ArrayList<>();

        try {
            Set<String> keys = stringRedisTemplate.keys(ALERT_PREFIX + "triggered:*");

            if (keys == null || keys.isEmpty()) {
                return alerts;
            }

            for (String key : keys) {
                String alertJson = stringRedisTemplate.opsForValue().get(key);
                if (alertJson != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> alert = objectMapper.readValue(alertJson, Map.class);

                    // 过滤条件
                    if (severity != null && !severity.equals(alert.get("severity"))) {
                        continue;
                    }

                    String triggeredTimeStr = (String) alert.get("triggeredTime");
                    if (triggeredTimeStr != null) {
                        LocalDateTime triggeredTime = LocalDateTime.parse(triggeredTimeStr);
                        if (startTime != null && triggeredTime.isBefore(startTime)) {
                            continue;
                        }
                        if (endTime != null && triggeredTime.isAfter(endTime)) {
                            continue;
                        }
                    }

                    alerts.add(alert);

                    if (alerts.size() >= limit) {
                        break;
                    }
                }
            }

        } catch (Exception e) {
            log.error("获取告警列表失败", e);
        }

        return alerts;
    }

    /**
     * 获取安全监控统计
     */
    public Map<String, Object> getSecurityMonitoringStats(LocalDateTime startTime, LocalDateTime endTime) {
        log.info("获取安全监控统计: startTime={}, endTime={}", startTime, endTime);

        Map<String, Object> stats = new HashMap<>();

        // 统计各类事件数量
        Map<String, Long> eventCounts = new HashMap<>();
        String[] eventTypes = {"LOGIN_FAILED", "ROW_ACCESS_DENIED", "COLUMN_ACCESS_DENIED",
                              "PERMISSION_DENIED", "SUSPICIOUS_ACTIVITY"};

        for (String eventType : eventTypes) {
            List<SecurityEvent> events = querySecurityEvents(eventType, null, startTime, endTime, 10000);
            eventCounts.put(eventType, (long) events.size());
        }
        stats.put("eventCounts", eventCounts);

        // 统计告警数量
        List<Map<String, Object>> highAlerts = getAlerts("HIGH", startTime, endTime, 1000);
        List<Map<String, Object>> mediumAlerts = getAlerts("MEDIUM", startTime, endTime, 1000);
        List<Map<String, Object>> lowAlerts = getAlerts("LOW", startTime, endTime, 1000);

        Map<String, Long> alertCounts = new HashMap<>();
        alertCounts.put("HIGH", (long) highAlerts.size());
        alertCounts.put("MEDIUM", (long) mediumAlerts.size());
        alertCounts.put("LOW", (long) lowAlerts.size());
        stats.put("alertCounts", alertCounts);

        // 计算安全评分
        int securityScore = calculateSecurityScore(eventCounts, alertCounts);
        stats.put("securityScore", securityScore);

        stats.put("startTime", startTime);
        stats.put("endTime", endTime);
        stats.put("generatedTime", LocalDateTime.now());

        return stats;
    }

    /**
     * 计算安全评分
     */
    private int calculateSecurityScore(Map<String, Long> eventCounts, Map<String, Long> alertCounts) {
        int score = 100;

        // 登录失败影响
        long loginFailed = eventCounts.getOrDefault("LOGIN_FAILED", 0L);
        if (loginFailed > 100) {
            score -= 20;
        } else if (loginFailed > 50) {
            score -= 10;
        } else if (loginFailed > 10) {
            score -= 5;
        }

        // 访问拒绝影响
        long accessDenied = eventCounts.getOrDefault("ROW_ACCESS_DENIED", 0L) +
                          eventCounts.getOrDefault("COLUMN_ACCESS_DENIED", 0L) +
                          eventCounts.getOrDefault("PERMISSION_DENIED", 0L);
        if (accessDenied > 50) {
            score -= 15;
        } else if (accessDenied > 20) {
            score -= 8;
        }

        // 告警影响
        long highAlerts = alertCounts.getOrDefault("HIGH", 0L);
        long mediumAlerts = alertCounts.getOrDefault("MEDIUM", 0L);

        score -= highAlerts * 10;
        score -= mediumAlerts * 5;

        return Math.max(0, Math.min(100, score));
    }

    /**
     * 初始化默认告警规则
     */
    public void initializeDefaultAlertRules() {
        // 登录失败告警
        AlertRule loginFailedRule = new AlertRule();
        loginFailedRule.setRuleId("LOGIN_FAILED_ALERT");
        loginFailedRule.setRuleName("登录失败告警");
        loginFailedRule.setEventType("LOGIN_FAILED");
        loginFailedRule.setThreshold(5);
        loginFailedRule.setTimeWindowMinutes(10);
        loginFailedRule.setSeverity("HIGH");
        loginFailedRule.setNotifyChannels(Arrays.asList("EMAIL"));
        loginFailedRule.setEnabled(true);
        defineAlertRule(loginFailedRule);

        // 权限拒绝告警
        AlertRule permissionDeniedRule = new AlertRule();
        permissionDeniedRule.setRuleId("PERMISSION_DENIED_ALERT");
        permissionDeniedRule.setRuleName("权限拒绝告警");
        permissionDeniedRule.setEventType("PERMISSION_DENIED");
        permissionDeniedRule.setThreshold(10);
        permissionDeniedRule.setTimeWindowMinutes(30);
        permissionDeniedRule.setSeverity("MEDIUM");
        permissionDeniedRule.setNotifyChannels(Arrays.asList("EMAIL"));
        permissionDeniedRule.setEnabled(true);
        defineAlertRule(permissionDeniedRule);

        log.info("默认告警规则初始化完成");
    }
}
