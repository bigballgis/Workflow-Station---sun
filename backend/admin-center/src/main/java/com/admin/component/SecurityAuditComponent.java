package com.admin.component;

import com.admin.entity.AuditLog;
import com.admin.entity.SecurityPolicy;
import com.admin.enums.AuditAction;
import com.admin.repository.AuditLogRepository;
import com.admin.repository.SecurityPolicyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.regex.Pattern;

/**
 * 安全审计组件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAuditComponent {
    
    private final SecurityPolicyRepository policyRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    
    // ==================== 安全策略管理 ====================
    
    @Transactional
    public SecurityPolicy createOrUpdatePolicy(String policyType, PolicyConfig config, String userId) {
        SecurityPolicy policy = policyRepository.findByPolicyType(policyType)
                .orElse(SecurityPolicy.builder()
                        .id(UUID.randomUUID().toString())
                        .policyType(policyType)
                        .build());
        
        policy.setPolicyName(config.getName());
        try {
            policy.setPolicyConfig(objectMapper.writeValueAsString(config));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize policy config", e);
        }
        policy.setEnabled(true);
        policy.setUpdatedBy(userId);
        
        return policyRepository.save(policy);
    }
    
    public SecurityPolicy getPolicy(String policyType) {
        return policyRepository.findByPolicyType(policyType)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyType));
    }
    
    public List<SecurityPolicy> getAllPolicies() {
        return policyRepository.findAll();
    }
    
    public <T extends PolicyConfig> T getPolicyConfig(String policyType, Class<T> configClass) {
        SecurityPolicy policy = getPolicy(policyType);
        try {
            return objectMapper.readValue(policy.getPolicyConfig(), configClass);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse policy config", e);
        }
    }
    
    // ==================== 密码策略 ====================
    
    public PasswordPolicyConfig getPasswordPolicy() {
        try {
            return getPolicyConfig("PASSWORD", PasswordPolicyConfig.class);
        } catch (Exception e) {
            return PasswordPolicyConfig.defaultPolicy();
        }
    }
    
    public PasswordValidationResult validatePassword(String password) {
        PasswordPolicyConfig policy = getPasswordPolicy();
        List<String> violations = new ArrayList<>();
        
        if (password.length() < policy.getMinLength()) {
            violations.add("密码长度不能少于" + policy.getMinLength() + "个字符");
        }
        if (password.length() > policy.getMaxLength()) {
            violations.add("密码长度不能超过" + policy.getMaxLength() + "个字符");
        }
        if (policy.getRequireUppercase() && !Pattern.compile("[A-Z]").matcher(password).find()) {
            violations.add("密码必须包含大写字母");
        }
        if (policy.getRequireLowercase() && !Pattern.compile("[a-z]").matcher(password).find()) {
            violations.add("密码必须包含小写字母");
        }
        if (policy.getRequireDigit() && !Pattern.compile("\\d").matcher(password).find()) {
            violations.add("密码必须包含数字");
        }
        if (policy.getRequireSpecialChar() && !Pattern.compile("[!@#$%^&*(),.?\":{}|<>]").matcher(password).find()) {
            violations.add("密码必须包含特殊字符");
        }
        
        return PasswordValidationResult.builder()
                .valid(violations.isEmpty())
                .violations(violations)
                .build();
    }
    
    // ==================== 登录策略 ====================
    
    public LoginPolicyConfig getLoginPolicy() {
        try {
            return getPolicyConfig("LOGIN", LoginPolicyConfig.class);
        } catch (Exception e) {
            return LoginPolicyConfig.defaultPolicy();
        }
    }
    
    public boolean shouldLockAccount(String userId) {
        LoginPolicyConfig policy = getLoginPolicy();
        Instant since = Instant.now().minus(policy.getLockoutWindowMinutes(), ChronoUnit.MINUTES);
        long failedAttempts = auditLogRepository.countByUserIdAndActionAndTimestampAfter(
                userId, AuditAction.USER_LOGIN_FAILED, since);
        return failedAttempts >= policy.getMaxFailedAttempts();
    }
    
    public boolean isIpWhitelisted(String ipAddress) {
        LoginPolicyConfig policy = getLoginPolicy();
        if (policy.getIpWhitelist() == null || policy.getIpWhitelist().isEmpty()) {
            return true;  // 没有白名单则允许所有
        }
        return policy.getIpWhitelist().contains(ipAddress);
    }
    
    // ==================== 会话策略 ====================
    
    public SessionPolicyConfig getSessionPolicy() {
        try {
            return getPolicyConfig("SESSION", SessionPolicyConfig.class);
        } catch (Exception e) {
            return SessionPolicyConfig.defaultPolicy();
        }
    }
    
    // ==================== 审计日志记录 ====================
    
    @Transactional
    public AuditLog recordAudit(AuditLogRequest request) {
        AuditLog auditLog = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .action(request.getAction())
                .resourceType(request.getResourceType())
                .resourceId(request.getResourceId())
                .resourceName(request.getResourceName())
                .userId(request.getUserId())
                .userName(request.getUserName())
                .ipAddress(request.getIpAddress())
                .userAgent(request.getUserAgent())
                .oldValue(request.getOldValue())
                .newValue(request.getNewValue())
                .changeDetails(request.getChangeDetails())
                .success(request.getSuccess())
                .failureReason(request.getFailureReason())
                .build();
        return auditLogRepository.save(auditLog);
    }
    
    // ==================== 审计日志查询 ====================
    
    public Page<AuditLog> queryAuditLogs(AuditQueryRequest request, Pageable pageable) {
        Specification<AuditLog> spec = buildSpecification(request);
        return auditLogRepository.findAll(spec, pageable);
    }
    
    private Specification<AuditLog> buildSpecification(AuditQueryRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (request.getAction() != null) {
                predicates.add(cb.equal(root.get("action"), request.getAction()));
            }
            if (request.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), request.getUserId()));
            }
            if (request.getResourceType() != null) {
                predicates.add(cb.equal(root.get("resourceType"), request.getResourceType()));
            }
            if (request.getResourceId() != null) {
                predicates.add(cb.equal(root.get("resourceId"), request.getResourceId()));
            }
            if (request.getStartTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), request.getStartTime()));
            }
            if (request.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), request.getEndTime()));
            }
            if (request.getSuccess() != null) {
                predicates.add(cb.equal(root.get("success"), request.getSuccess()));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    public Page<AuditLog> getAuditLogsByUser(String userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable);
    }
    
    public Page<AuditLog> getAuditLogsByResource(String resourceType, String resourceId, Pageable pageable) {
        return auditLogRepository.findByResourceTypeAndResourceId(resourceType, resourceId, pageable);
    }
    
    // ==================== 异常行为检测 ====================
    
    public List<AnomalyDetectionResult> detectAnomalies(int days) {
        List<AnomalyDetectionResult> results = new ArrayList<>();
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        
        // 检测可疑登录尝试
        List<Object[]> suspiciousLogins = auditLogRepository.findSuspiciousLoginAttempts(since, 5);
        for (Object[] row : suspiciousLogins) {
            results.add(AnomalyDetectionResult.builder()
                    .type("SUSPICIOUS_LOGIN")
                    .userId((String) row[0])
                    .count((Long) row[1])
                    .description("用户在" + days + "天内有" + row[1] + "次登录失败")
                    .severity("HIGH")
                    .build());
        }
        
        // 检测安全事件
        List<AuditAction> securityActions = List.of(
                AuditAction.USER_LOCKED,
                AuditAction.PASSWORD_RESET,
                AuditAction.PERMISSION_GRANTED,
                AuditAction.PERMISSION_REVOKED
        );
        List<AuditLog> securityEvents = auditLogRepository.findSecurityEvents(securityActions, since);
        
        // 按用户分组检测异常权限变更
        Map<String, Long> permissionChanges = new HashMap<>();
        for (AuditLog event : securityEvents) {
            if (event.getAction() == AuditAction.PERMISSION_GRANTED || 
                event.getAction() == AuditAction.PERMISSION_REVOKED) {
                permissionChanges.merge(event.getUserId(), 1L, Long::sum);
            }
        }
        
        for (Map.Entry<String, Long> entry : permissionChanges.entrySet()) {
            if (entry.getValue() > 10) {
                results.add(AnomalyDetectionResult.builder()
                        .type("EXCESSIVE_PERMISSION_CHANGES")
                        .userId(entry.getKey())
                        .count(entry.getValue())
                        .description("用户在" + days + "天内有" + entry.getValue() + "次权限变更")
                        .severity("MEDIUM")
                        .build());
            }
        }
        
        return results;
    }
    
    // ==================== 合规报告 ====================
    
    public ComplianceReport generateComplianceReport(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        
        // 统计各类操作
        List<Object[]> actionCounts = auditLogRepository.countByActionSince(since);
        Map<String, Long> actionStats = new HashMap<>();
        for (Object[] row : actionCounts) {
            actionStats.put(row[0].toString(), (Long) row[1]);
        }
        
        // 检测异常
        List<AnomalyDetectionResult> anomalies = detectAnomalies(days);
        
        // 策略合规检查
        List<PolicyComplianceItem> policyCompliance = new ArrayList<>();
        for (SecurityPolicy policy : policyRepository.findAll()) {
            policyCompliance.add(PolicyComplianceItem.builder()
                    .policyType(policy.getPolicyType())
                    .policyName(policy.getPolicyName())
                    .enabled(policy.getEnabled())
                    .compliant(policy.getEnabled())
                    .build());
        }
        
        return ComplianceReport.builder()
                .reportId(UUID.randomUUID().toString())
                .generatedAt(Instant.now())
                .periodDays(days)
                .actionStatistics(actionStats)
                .anomalies(anomalies)
                .policyCompliance(policyCompliance)
                .totalAuditRecords(auditLogRepository.count())
                .build();
    }
    
    // ==================== 内部类 ====================
    
    public interface PolicyConfig {
        String getName();
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PasswordPolicyConfig implements PolicyConfig {
        private String name;
        private Integer minLength;
        private Integer maxLength;
        private Boolean requireUppercase;
        private Boolean requireLowercase;
        private Boolean requireDigit;
        private Boolean requireSpecialChar;
        private Integer expirationDays;
        private Integer historyCount;
        
        public static PasswordPolicyConfig defaultPolicy() {
            return PasswordPolicyConfig.builder()
                    .name("默认密码策略")
                    .minLength(8)
                    .maxLength(128)
                    .requireUppercase(true)
                    .requireLowercase(true)
                    .requireDigit(true)
                    .requireSpecialChar(false)
                    .expirationDays(90)
                    .historyCount(5)
                    .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LoginPolicyConfig implements PolicyConfig {
        private String name;
        private Integer maxFailedAttempts;
        private Integer lockoutDurationMinutes;
        private Integer lockoutWindowMinutes;
        private List<String> ipWhitelist;
        private Boolean requireCaptchaAfterFailures;
        private Integer captchaThreshold;
        
        public static LoginPolicyConfig defaultPolicy() {
            return LoginPolicyConfig.builder()
                    .name("默认登录策略")
                    .maxFailedAttempts(5)
                    .lockoutDurationMinutes(30)
                    .lockoutWindowMinutes(15)
                    .ipWhitelist(new ArrayList<>())
                    .requireCaptchaAfterFailures(true)
                    .captchaThreshold(3)
                    .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SessionPolicyConfig implements PolicyConfig {
        private String name;
        private Integer sessionTimeoutMinutes;
        private Integer maxConcurrentSessions;
        private Boolean forceLogoutOnNewSession;
        
        public static SessionPolicyConfig defaultPolicy() {
            return SessionPolicyConfig.builder()
                    .name("默认会话策略")
                    .sessionTimeoutMinutes(30)
                    .maxConcurrentSessions(3)
                    .forceLogoutOnNewSession(false)
                    .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PasswordValidationResult {
        private boolean valid;
        private List<String> violations;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AuditLogRequest {
        private AuditAction action;
        private String resourceType;
        private String resourceId;
        private String resourceName;
        private String userId;
        private String userName;
        private String ipAddress;
        private String userAgent;
        private String oldValue;
        private String newValue;
        private String changeDetails;
        private Boolean success;
        private String failureReason;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AuditQueryRequest {
        private AuditAction action;
        private String userId;
        private String resourceType;
        private String resourceId;
        private Instant startTime;
        private Instant endTime;
        private Boolean success;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AnomalyDetectionResult {
        private String type;
        private String userId;
        private Long count;
        private String description;
        private String severity;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PolicyComplianceItem {
        private String policyType;
        private String policyName;
        private Boolean enabled;
        private Boolean compliant;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ComplianceReport {
        private String reportId;
        private Instant generatedAt;
        private int periodDays;
        private Map<String, Long> actionStatistics;
        private List<AnomalyDetectionResult> anomalies;
        private List<PolicyComplianceItem> policyCompliance;
        private long totalAuditRecords;
    }
}
