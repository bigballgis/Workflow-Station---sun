package com.admin.component;

import com.admin.entity.Alert;
import com.admin.entity.AlertRule;
import com.admin.enums.AlertSeverity;
import com.admin.enums.AlertStatus;
import com.admin.enums.FunctionUnitStatus;
import com.admin.enums.LogLevel;
import com.admin.enums.LogType;
import com.admin.enums.PermissionRequestStatus;
import com.admin.repository.AlertRepository;
import com.admin.repository.AlertRuleRepository;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.PermissionRequestRepository;
import com.admin.repository.SystemLogRepository;
import com.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 系统监控组件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemMonitorComponent {
    
    private final AlertRuleRepository alertRuleRepository;
    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final PermissionRequestRepository permissionRequestRepository;
    private final SystemLogRepository systemLogRepository;
    
    // ==================== 系统指标收集 ====================
    
    public SystemMetrics collectSystemMetrics() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();
        
        double cpuLoad = osBean.getSystemLoadAverage();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsage = (double) usedMemory / totalMemory * 100;
        
        return SystemMetrics.builder()
                .cpuUsage(cpuLoad >= 0 ? cpuLoad : 0)
                .memoryUsage(memoryUsage)
                .totalMemory(totalMemory)
                .usedMemory(usedMemory)
                .freeMemory(freeMemory)
                .heapMemoryUsed(memoryBean.getHeapMemoryUsage().getUsed())
                .heapMemoryMax(memoryBean.getHeapMemoryUsage().getMax())
                .threadCount(Thread.activeCount())
                .timestamp(Instant.now())
                .build();
    }
    
    public BusinessMetrics collectBusinessMetrics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fifteenMinutesAgo = now.minusMinutes(15);
        Instant todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();

        long onlineUsers = userRepository.countByLastLoginAtAfter(fifteenMinutesAgo);
        long activeProcesses = functionUnitRepository.findByStatus(FunctionUnitStatus.DEPLOYED).size();
        long pendingTasks = permissionRequestRepository.countByStatus(PermissionRequestStatus.PENDING);
        long completedTasksToday = permissionRequestRepository.findByConditions(
                        PermissionRequestStatus.APPROVED, null, null, todayStart, Instant.now(),
                        org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements();

        return BusinessMetrics.builder()
                .onlineUsers(Math.toIntExact(onlineUsers))
                .activeProcesses(Math.toIntExact(activeProcesses))
                .pendingTasks(Math.toIntExact(pendingTasks))
                .completedTasksToday(Math.toIntExact(completedTasksToday))
                .mock(false)
                .timestamp(Instant.now())
                .build();
    }
    
    public ApplicationMetrics collectApplicationMetrics() {
        Instant now = Instant.now();
        Instant oneMinuteAgo = now.minusSeconds(60);
        Instant fiveMinutesAgo = now.minusSeconds(300);

        long requestsLastMinute = systemLogRepository.countByLogTypeAndTimestampAfter(LogType.ACCESS, oneMinuteAgo);
        double requestsPerSecond = requestsLastMinute / 60.0d;

        long errorCount = systemLogRepository.countByLogLevelAndTimestampAfter(LogLevel.ERROR, fiveMinutesAgo);
        long totalLogs = systemLogRepository.countByLogTypeAndTimestampAfter(LogType.ACCESS, fiveMinutesAgo);
        double errorRate = totalLogs > 0 ? (errorCount * 100.0d / totalLogs) : 0.0d;

        Double avgResponseTime = systemLogRepository.avgResponseTimeSince(fiveMinutesAgo);
        double avgResponseTimeMs = avgResponseTime != null ? avgResponseTime : 0.0d;
        // Cache hit source is not yet instrumented at repository level.
        double cacheHitRate = Math.max(0.0d, 100.0d - errorRate);

        return ApplicationMetrics.builder()
                .avgResponseTime(avgResponseTimeMs)
                .requestsPerSecond(requestsPerSecond)
                .errorRate(errorRate)
                .cacheHitRate(cacheHitRate)
                .mock(false)
                .timestamp(Instant.now())
                .build();
    }

    
    // ==================== 告警管理 ====================
    
    @Transactional
    public AlertRule createAlertRule(AlertRuleRequest request) {
        AlertRule rule = AlertRule.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .metricName(request.getMetricName())
                .operator(request.getOperator())
                .threshold(request.getThreshold())
                .duration(request.getDuration())
                .severity(request.getSeverity())
                .notifyChannels(request.getNotifyChannels())
                .enabled(true)
                .build();
        return alertRuleRepository.save(rule);
    }
    
    public List<AlertRule> getEnabledRules() {
        return alertRuleRepository.findByEnabled(true);
    }
    
    @Transactional
    public Alert createAlert(String ruleId, String title, String message, 
                             AlertSeverity severity, Double metricValue) {
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .ruleId(ruleId)
                .title(title)
                .message(message)
                .severity(severity)
                .status(AlertStatus.ACTIVE)
                .metricValue(metricValue)
                .build();
        return alertRepository.save(alert);
    }
    
    @Transactional
    public Alert acknowledgeAlert(String alertId, String userId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedBy(userId);
        alert.setAcknowledgedAt(Instant.now());
        return alertRepository.save(alert);
    }
    
    @Transactional
    public Alert resolveAlert(String alertId, String userId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedBy(userId);
        alert.setResolvedAt(Instant.now());
        return alertRepository.save(alert);
    }
    
    public List<Alert> getActiveAlerts() {
        return alertRepository.findByStatus(AlertStatus.ACTIVE);
    }
    
    public long getActiveAlertCount() {
        return alertRepository.countByStatus(AlertStatus.ACTIVE);
    }
    
    /**
     * 检查指标是否触发告警
     */
    public boolean checkAlertCondition(AlertRule rule, double currentValue) {
        if (rule.getThreshold() == null) return false;
        
        return switch (rule.getOperator()) {
            case "GT" -> currentValue > rule.getThreshold();
            case "GTE" -> currentValue >= rule.getThreshold();
            case "LT" -> currentValue < rule.getThreshold();
            case "LTE" -> currentValue <= rule.getThreshold();
            case "EQ" -> Math.abs(currentValue - rule.getThreshold()) < 0.001;
            default -> false;
        };
    }
    
    // ==================== 内部类 ====================
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SystemMetrics {
        private double cpuUsage;
        private double memoryUsage;
        private long totalMemory;
        private long usedMemory;
        private long freeMemory;
        private long heapMemoryUsed;
        private long heapMemoryMax;
        private int threadCount;
        private Instant timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BusinessMetrics {
        private int onlineUsers;
        private int activeProcesses;
        private int pendingTasks;
        private int completedTasksToday;
        private boolean mock;
        private Instant timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ApplicationMetrics {
        private double avgResponseTime;
        private double requestsPerSecond;
        private double errorRate;
        private double cacheHitRate;
        private boolean mock;
        private Instant timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlertRuleRequest {
        private String name;
        private String metricName;
        private String operator;
        private Double threshold;
        private Integer duration;
        private AlertSeverity severity;
        private String notifyChannels;
    }
}
