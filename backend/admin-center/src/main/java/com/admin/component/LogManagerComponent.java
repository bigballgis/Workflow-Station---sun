package com.admin.component;

import com.admin.entity.LogRetentionPolicy;
import com.admin.entity.SystemLog;
import com.admin.enums.LogLevel;
import com.admin.enums.LogType;
import com.admin.repository.LogRetentionPolicyRepository;
import com.admin.repository.SystemLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 日志管理组件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogManagerComponent {
    
    private final SystemLogRepository logRepository;
    private final LogRetentionPolicyRepository policyRepository;
    
    // ==================== 日志记录 ====================
    
    @Transactional
    public SystemLog createLog(LogCreateRequest request) {
        SystemLog logEntry = SystemLog.builder()
                .id(UUID.randomUUID().toString())
                .logType(request.getLogType())
                .logLevel(request.getLogLevel())
                .module(request.getModule())
                .action(request.getAction())
                .message(request.getMessage())
                .stackTrace(request.getStackTrace())
                .userId(request.getUserId())
                .userName(request.getUserName())
                .ipAddress(request.getIpAddress())
                .userAgent(request.getUserAgent())
                .requestUrl(request.getRequestUrl())
                .requestMethod(request.getRequestMethod())
                .responseTime(request.getResponseTime())
                .responseStatus(request.getResponseStatus())
                .requestBody(request.getRequestBody())
                .responseBody(request.getResponseBody())
                .extraData(request.getExtraData())
                .build();
        return logRepository.save(logEntry);
    }
    
    // ==================== 日志查询 ====================
    
    public Page<SystemLog> queryLogs(LogQueryRequest request, Pageable pageable) {
        Specification<SystemLog> spec = buildSpecification(request);
        return logRepository.findAll(spec, pageable);
    }
    
    private Specification<SystemLog> buildSpecification(LogQueryRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (request.getLogType() != null) {
                predicates.add(cb.equal(root.get("logType"), request.getLogType()));
            }
            if (request.getLogLevel() != null) {
                predicates.add(cb.equal(root.get("logLevel"), request.getLogLevel()));
            }
            if (request.getModule() != null) {
                predicates.add(cb.equal(root.get("module"), request.getModule()));
            }
            if (request.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), request.getUserId()));
            }
            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                String pattern = "%" + request.getKeyword() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("message"), pattern),
                        cb.like(root.get("action"), pattern)
                ));
            }
            if (request.getStartTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), request.getStartTime()));
            }
            if (request.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), request.getEndTime()));
            }
            if (request.getIpAddress() != null) {
                predicates.add(cb.equal(root.get("ipAddress"), request.getIpAddress()));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    public Page<SystemLog> searchLogs(String keyword, Pageable pageable) {
        return logRepository.searchByKeyword(keyword, pageable);
    }
    
    public Page<SystemLog> getLogsByUser(String userId, Pageable pageable) {
        return logRepository.findByUserId(userId, pageable);
    }
    
    public Page<SystemLog> getLogsByType(LogType logType, Pageable pageable) {
        return logRepository.findByLogType(logType, pageable);
    }
    
    // ==================== 用户行为追踪 ====================
    
    public List<SystemLog> getUserActivityTrace(String userId, Instant startTime, Instant endTime) {
        LogQueryRequest request = LogQueryRequest.builder()
                .userId(userId)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        return queryLogs(request, Pageable.unpaged()).getContent();
    }
    
    public UserBehaviorAnalysis analyzeUserBehavior(String userId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<SystemLog> logs = logRepository.findByUserId(userId, Pageable.unpaged()).getContent()
                .stream()
                .filter(l -> l.getTimestamp().isAfter(since))
                .toList();
        
        Map<String, Long> actionCounts = logs.stream()
                .filter(l -> l.getAction() != null)
                .collect(Collectors.groupingBy(SystemLog::getAction, Collectors.counting()));
        
        Map<String, Long> moduleCounts = logs.stream()
                .filter(l -> l.getModule() != null)
                .collect(Collectors.groupingBy(SystemLog::getModule, Collectors.counting()));
        
        Set<String> ipAddresses = logs.stream()
                .map(SystemLog::getIpAddress)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        return UserBehaviorAnalysis.builder()
                .userId(userId)
                .totalActions(logs.size())
                .actionCounts(actionCounts)
                .moduleCounts(moduleCounts)
                .uniqueIpAddresses(ipAddresses)
                .analyzedDays(days)
                .build();
    }
    
    // ==================== 日志分析 ====================
    
    public LogStatistics getLogStatistics(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        
        Map<LogLevel, Long> levelCounts = new EnumMap<>(LogLevel.class);
        for (LogLevel level : LogLevel.values()) {
            levelCounts.put(level, logRepository.countByLogLevelAndTimestampAfter(level, since));
        }
        
        Map<LogType, Long> typeCounts = new EnumMap<>(LogType.class);
        for (LogType type : LogType.values()) {
            typeCounts.put(type, logRepository.countByLogTypeAndTimestampAfter(type, since));
        }
        
        List<Object[]> errorTrend = logRepository.getErrorTrendByModule(since);
        Map<String, Long> errorsByModule = new LinkedHashMap<>();
        for (Object[] row : errorTrend) {
            errorsByModule.put((String) row[0], (Long) row[1]);
        }
        
        return LogStatistics.builder()
                .levelCounts(levelCounts)
                .typeCounts(typeCounts)
                .errorsByModule(errorsByModule)
                .analyzedDays(days)
                .build();
    }
    
    public List<ErrorTrendPoint> getErrorTrend(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> trend = logRepository.getErrorTrendByDate(since);
        
        return trend.stream()
                .map(row -> ErrorTrendPoint.builder()
                        .date(row[0].toString())
                        .count((Long) row[1])
                        .build())
                .toList();
    }
    
    public List<PerformanceBottleneck> detectPerformanceBottlenecks(long thresholdMs, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        LogQueryRequest request = LogQueryRequest.builder()
                .startTime(since)
                .build();
        
        List<SystemLog> logs = queryLogs(request, Pageable.unpaged()).getContent();
        
        Map<String, List<Long>> responseTimesByUrl = logs.stream()
                .filter(l -> l.getRequestUrl() != null && l.getResponseTime() != null)
                .collect(Collectors.groupingBy(
                        SystemLog::getRequestUrl,
                        Collectors.mapping(SystemLog::getResponseTime, Collectors.toList())
                ));
        
        return responseTimesByUrl.entrySet().stream()
                .map(entry -> {
                    List<Long> times = entry.getValue();
                    double avg = times.stream().mapToLong(Long::longValue).average().orElse(0);
                    long max = times.stream().mapToLong(Long::longValue).max().orElse(0);
                    long slowCount = times.stream().filter(t -> t > thresholdMs).count();
                    
                    return PerformanceBottleneck.builder()
                            .url(entry.getKey())
                            .avgResponseTime(avg)
                            .maxResponseTime(max)
                            .requestCount(times.size())
                            .slowRequestCount(slowCount)
                            .build();
                })
                .filter(b -> b.getAvgResponseTime() > thresholdMs || b.getSlowRequestCount() > 0)
                .sorted((a, b) -> Double.compare(b.getAvgResponseTime(), a.getAvgResponseTime()))
                .limit(20)
                .toList();
    }
    
    // ==================== 日志保留策略 ====================
    
    @Transactional
    public LogRetentionPolicy createRetentionPolicy(RetentionPolicyRequest request) {
        LogRetentionPolicy policy = LogRetentionPolicy.builder()
                .id(UUID.randomUUID().toString())
                .logType(request.getLogType())
                .retentionDays(request.getRetentionDays())
                .archiveAfterDays(request.getArchiveAfterDays())
                .archiveLocation(request.getArchiveLocation())
                .compressionEnabled(request.getCompressionEnabled())
                .enabled(true)
                .build();
        return policyRepository.save(policy);
    }
    
    @Transactional
    public LogRetentionPolicy updateRetentionPolicy(String id, RetentionPolicyRequest request, String userId) {
        LogRetentionPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
        policy.setRetentionDays(request.getRetentionDays());
        policy.setArchiveAfterDays(request.getArchiveAfterDays());
        policy.setArchiveLocation(request.getArchiveLocation());
        policy.setCompressionEnabled(request.getCompressionEnabled());
        policy.setUpdatedBy(userId);
        return policyRepository.save(policy);
    }
    
    public List<LogRetentionPolicy> getRetentionPolicies() {
        return policyRepository.findAll();
    }
    
    @Transactional
    public void applyRetentionPolicies() {
        List<LogRetentionPolicy> policies = policyRepository.findByEnabled(true);
        for (LogRetentionPolicy policy : policies) {
            Instant cutoff = Instant.now().minus(policy.getRetentionDays(), ChronoUnit.DAYS);
            logRepository.deleteByTimestampBefore(cutoff);
            log.info("Applied retention policy for {}: deleted logs before {}", policy.getLogType(), cutoff);
        }
    }
    
    // ==================== 日志导出 ====================
    
    public LogExportResult exportLogs(LogQueryRequest request, String format) {
        List<SystemLog> logs = queryLogs(request, Pageable.unpaged()).getContent();
        
        String content;
        String filename;
        String contentType;
        
        if ("csv".equalsIgnoreCase(format)) {
            content = exportToCsv(logs);
            filename = "logs_export_" + Instant.now().toEpochMilli() + ".csv";
            contentType = "text/csv";
        } else {
            content = exportToJson(logs);
            filename = "logs_export_" + Instant.now().toEpochMilli() + ".json";
            contentType = "application/json";
        }
        
        return LogExportResult.builder()
                .content(content)
                .filename(filename)
                .contentType(contentType)
                .recordCount(logs.size())
                .build();
    }
    
    private String exportToCsv(List<SystemLog> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID,Type,Level,Module,Action,Message,UserId,Timestamp\n");
        for (SystemLog log : logs) {
            sb.append(String.format("%s,%s,%s,%s,%s,\"%s\",%s,%s\n",
                    log.getId(),
                    log.getLogType(),
                    log.getLogLevel(),
                    log.getModule() != null ? log.getModule() : "",
                    log.getAction() != null ? log.getAction() : "",
                    log.getMessage() != null ? log.getMessage().replace("\"", "\"\"") : "",
                    log.getUserId() != null ? log.getUserId() : "",
                    log.getTimestamp()
            ));
        }
        return sb.toString();
    }
    
    private String exportToJson(List<SystemLog> logs) {
        // 简化的JSON导出
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < logs.size(); i++) {
            SystemLog log = logs.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"type\":\"%s\",\"level\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                    log.getId(), log.getLogType(), log.getLogLevel(),
                    log.getMessage() != null ? log.getMessage().replace("\"", "\\\"") : "",
                    log.getTimestamp()));
        }
        sb.append("]");
        return sb.toString();
    }
    
    // ==================== 内部类 ====================
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LogCreateRequest {
        private LogType logType;
        private LogLevel logLevel;
        private String module;
        private String action;
        private String message;
        private String stackTrace;
        private String userId;
        private String userName;
        private String ipAddress;
        private String userAgent;
        private String requestUrl;
        private String requestMethod;
        private Long responseTime;
        private Integer responseStatus;
        private String requestBody;
        private String responseBody;
        private String extraData;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LogQueryRequest {
        private LogType logType;
        private LogLevel logLevel;
        private String module;
        private String userId;
        private String keyword;
        private Instant startTime;
        private Instant endTime;
        private String ipAddress;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserBehaviorAnalysis {
        private String userId;
        private int totalActions;
        private Map<String, Long> actionCounts;
        private Map<String, Long> moduleCounts;
        private Set<String> uniqueIpAddresses;
        private int analyzedDays;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LogStatistics {
        private Map<LogLevel, Long> levelCounts;
        private Map<LogType, Long> typeCounts;
        private Map<String, Long> errorsByModule;
        private int analyzedDays;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ErrorTrendPoint {
        private String date;
        private Long count;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PerformanceBottleneck {
        private String url;
        private double avgResponseTime;
        private long maxResponseTime;
        private int requestCount;
        private long slowRequestCount;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RetentionPolicyRequest {
        private LogType logType;
        private Integer retentionDays;
        private Integer archiveAfterDays;
        private String archiveLocation;
        private Boolean compressionEnabled;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LogExportResult {
        private String content;
        private String filename;
        private String contentType;
        private int recordCount;
    }
}
