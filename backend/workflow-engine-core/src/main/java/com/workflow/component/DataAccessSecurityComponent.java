package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.response.PermissionCheckResult;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import com.workflow.exception.WorkflowBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据访问安全组件
 * 
 * 负责行级和列级数据权限控制、数据脱敏和匿名化处理、
 * 安全事件监控和告警功能
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataAccessSecurityComponent {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AuditManagerComponent auditManagerComponent;
    private final SecurityManagerComponent securityManagerComponent;
    
    // 缓存键前缀
    private static final String ROW_POLICY_PREFIX = "security:row_policy:";
    private static final String COLUMN_POLICY_PREFIX = "security:column_policy:";
    private static final String MASK_RULE_PREFIX = "security:mask_rule:";
    private static final String SECURITY_EVENT_PREFIX = "security:event:";
    private static final String ALERT_PREFIX = "security:alert:";

    // 内存缓存
    private final Map<String, RowLevelPolicy> rowPolicies = new ConcurrentHashMap<>();
    private final Map<String, ColumnLevelPolicy> columnPolicies = new ConcurrentHashMap<>();
    private final Map<String, DataMaskRule> maskRules = new ConcurrentHashMap<>();
    private final Map<String, AlertRule> alertRules = new ConcurrentHashMap<>();
    
    // 敏感数据模式
    private static final Map<String, Pattern> SENSITIVE_PATTERNS = new HashMap<>();
    
    static {
        SENSITIVE_PATTERNS.put("PHONE", Pattern.compile("1[3-9]\\d{9}"));
        SENSITIVE_PATTERNS.put("ID_CARD", Pattern.compile("\\d{17}[\\dXx]"));
        SENSITIVE_PATTERNS.put("EMAIL", Pattern.compile("[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}"));
        SENSITIVE_PATTERNS.put("BANK_CARD", Pattern.compile("\\d{16,19}"));
        SENSITIVE_PATTERNS.put("PASSWORD", Pattern.compile("(?i)(password|pwd|secret|key)"));
    }

    // ==================== 行级权限控制 ====================

    /**
     * 行级权限策略
     */
    public static class RowLevelPolicy {
        private String policyId;
        private String tableName;
        private String conditionExpression;
        private Set<String> allowedRoles;
        private Set<String> allowedUsers;
        private String description;
        private boolean enabled;
        private LocalDateTime createdTime;
        
        // Getters and Setters
        public String getPolicyId() { return policyId; }
        public void setPolicyId(String policyId) { this.policyId = policyId; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getConditionExpression() { return conditionExpression; }
        public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }
        public Set<String> getAllowedRoles() { return allowedRoles; }
        public void setAllowedRoles(Set<String> allowedRoles) { this.allowedRoles = allowedRoles; }
        public Set<String> getAllowedUsers() { return allowedUsers; }
        public void setAllowedUsers(Set<String> allowedUsers) { this.allowedUsers = allowedUsers; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public LocalDateTime getCreatedTime() { return createdTime; }
        public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    }

    /**
     * 定义行级权限策略
     */
    public void defineRowLevelPolicy(RowLevelPolicy policy) {
        log.info("定义行级权限策略: policyId={}, tableName={}", policy.getPolicyId(), policy.getTableName());
        
        if (policy.getCreatedTime() == null) {
            policy.setCreatedTime(LocalDateTime.now());
        }
        
        rowPolicies.put(policy.getPolicyId(), policy);
        
        // 缓存到Redis
        try {
            String cacheKey = ROW_POLICY_PREFIX + policy.getPolicyId();
            String policyJson = objectMapper.writeValueAsString(policy);
            stringRedisTemplate.opsForValue().set(cacheKey, policyJson, Duration.ofDays(7));
        } catch (JsonProcessingException e) {
            log.error("缓存行级权限策略失败: policyId={}", policy.getPolicyId(), e);
        }
        
        log.info("行级权限策略定义成功: policyId={}", policy.getPolicyId());
    }

    /**
     * 检查行级访问权限
     */
    public RowAccessResult checkRowAccess(String username, String tableName, Map<String, Object> rowData) {
        log.debug("检查行级访问权限: username={}, tableName={}", username, tableName);
        
        // 获取用户角色
        Set<String> userRoles = securityManagerComponent.getUserRoles(username);
        
        // 查找适用的行级策略
        List<RowLevelPolicy> applicablePolicies = rowPolicies.values().stream()
                .filter(p -> p.isEnabled() && p.getTableName().equals(tableName))
                .collect(Collectors.toList());
        
        if (applicablePolicies.isEmpty()) {
            // 没有策略，默认允许
            return RowAccessResult.allowed();
        }
        
        for (RowLevelPolicy policy : applicablePolicies) {
            // 检查用户是否在允许列表中
            if (policy.getAllowedUsers() != null && policy.getAllowedUsers().contains(username)) {
                continue; // 用户被明确允许
            }
            
            // 检查角色是否在允许列表中
            boolean roleAllowed = false;
            if (policy.getAllowedRoles() != null) {
                for (String role : userRoles) {
                    if (policy.getAllowedRoles().contains(role)) {
                        roleAllowed = true;
                        break;
                    }
                }
            }
            
            if (!roleAllowed) {
                // 评估条件表达式
                boolean conditionMet = evaluateCondition(policy.getConditionExpression(), rowData, username);
                if (!conditionMet) {
                    recordSecurityEvent(username, "ROW_ACCESS_DENIED", 
                            "行级访问被拒绝: table=" + tableName + ", policy=" + policy.getPolicyId());
                    return RowAccessResult.denied(policy.getPolicyId(), "行级权限策略不允许访问此数据");
                }
            }
        }
        
        return RowAccessResult.allowed();
    }

    /**
     * 生成行级过滤SQL条件
     */
    public String generateRowFilterCondition(String username, String tableName) {
        log.debug("生成行级过滤条件: username={}, tableName={}", username, tableName);
        
        Set<String> userRoles = securityManagerComponent.getUserRoles(username);
        
        List<RowLevelPolicy> applicablePolicies = rowPolicies.values().stream()
                .filter(p -> p.isEnabled() && p.getTableName().equals(tableName))
                .collect(Collectors.toList());
        
        if (applicablePolicies.isEmpty()) {
            return "1=1"; // 无限制
        }
        
        List<String> conditions = new ArrayList<>();
        
        for (RowLevelPolicy policy : applicablePolicies) {
            // 检查用户是否被明确允许
            if (policy.getAllowedUsers() != null && policy.getAllowedUsers().contains(username)) {
                continue; // 跳过此策略
            }
            
            // 检查角色是否被允许
            boolean roleAllowed = false;
            if (policy.getAllowedRoles() != null) {
                for (String role : userRoles) {
                    if (policy.getAllowedRoles().contains(role)) {
                        roleAllowed = true;
                        break;
                    }
                }
            }
            
            if (!roleAllowed && policy.getConditionExpression() != null) {
                // 替换条件中的用户变量
                String condition = policy.getConditionExpression()
                        .replace("${username}", "'" + username + "'")
                        .replace("${userId}", "'" + username + "'");
                conditions.add("(" + condition + ")");
            }
        }
        
        if (conditions.isEmpty()) {
            return "1=1";
        }
        
        return String.join(" AND ", conditions);
    }

    // ==================== 列级权限控制 ====================

    /**
     * 列级权限策略
     */
    public static class ColumnLevelPolicy {
        private String policyId;
        private String tableName;
        private Set<String> visibleColumns;
        private Set<String> hiddenColumns;
        private Set<String> maskedColumns;
        private Set<String> allowedRoles;
        private Set<String> allowedUsers;
        private String description;
        private boolean enabled;
        private LocalDateTime createdTime;
        
        // Getters and Setters
        public String getPolicyId() { return policyId; }
        public void setPolicyId(String policyId) { this.policyId = policyId; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public Set<String> getVisibleColumns() { return visibleColumns; }
        public void setVisibleColumns(Set<String> visibleColumns) { this.visibleColumns = visibleColumns; }
        public Set<String> getHiddenColumns() { return hiddenColumns; }
        public void setHiddenColumns(Set<String> hiddenColumns) { this.hiddenColumns = hiddenColumns; }
        public Set<String> getMaskedColumns() { return maskedColumns; }
        public void setMaskedColumns(Set<String> maskedColumns) { this.maskedColumns = maskedColumns; }
        public Set<String> getAllowedRoles() { return allowedRoles; }
        public void setAllowedRoles(Set<String> allowedRoles) { this.allowedRoles = allowedRoles; }
        public Set<String> getAllowedUsers() { return allowedUsers; }
        public void setAllowedUsers(Set<String> allowedUsers) { this.allowedUsers = allowedUsers; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public LocalDateTime getCreatedTime() { return createdTime; }
        public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    }

    /**
     * 定义列级权限策略
     */
    public void defineColumnLevelPolicy(ColumnLevelPolicy policy) {
        log.info("定义列级权限策略: policyId={}, tableName={}", policy.getPolicyId(), policy.getTableName());
        
        if (policy.getCreatedTime() == null) {
            policy.setCreatedTime(LocalDateTime.now());
        }
        
        columnPolicies.put(policy.getPolicyId(), policy);
        
        // 缓存到Redis
        try {
            String cacheKey = COLUMN_POLICY_PREFIX + policy.getPolicyId();
            String policyJson = objectMapper.writeValueAsString(policy);
            stringRedisTemplate.opsForValue().set(cacheKey, policyJson, Duration.ofDays(7));
        } catch (JsonProcessingException e) {
            log.error("缓存列级权限策略失败: policyId={}", policy.getPolicyId(), e);
        }
        
        log.info("列级权限策略定义成功: policyId={}", policy.getPolicyId());
    }

    /**
     * 获取用户可见列
     */
    public Set<String> getVisibleColumns(String username, String tableName, Set<String> allColumns) {
        log.debug("获取可见列: username={}, tableName={}", username, tableName);
        
        Set<String> userRoles = securityManagerComponent.getUserRoles(username);
        Set<String> visibleColumns = new HashSet<>(allColumns);
        
        List<ColumnLevelPolicy> applicablePolicies = columnPolicies.values().stream()
                .filter(p -> p.isEnabled() && p.getTableName().equals(tableName))
                .collect(Collectors.toList());
        
        for (ColumnLevelPolicy policy : applicablePolicies) {
            // 检查用户是否被明确允许
            if (policy.getAllowedUsers() != null && policy.getAllowedUsers().contains(username)) {
                continue;
            }
            
            // 检查角色是否被允许
            boolean roleAllowed = false;
            if (policy.getAllowedRoles() != null) {
                for (String role : userRoles) {
                    if (policy.getAllowedRoles().contains(role)) {
                        roleAllowed = true;
                        break;
                    }
                }
            }
            
            if (!roleAllowed) {
                // 应用隐藏列
                if (policy.getHiddenColumns() != null) {
                    visibleColumns.removeAll(policy.getHiddenColumns());
                }
                
                // 如果定义了可见列，则只保留这些列
                if (policy.getVisibleColumns() != null && !policy.getVisibleColumns().isEmpty()) {
                    visibleColumns.retainAll(policy.getVisibleColumns());
                }
            }
        }
        
        return visibleColumns;
    }

    /**
     * 获取需要脱敏的列
     */
    public Set<String> getMaskedColumns(String username, String tableName) {
        log.debug("获取脱敏列: username={}, tableName={}", username, tableName);
        
        Set<String> userRoles = securityManagerComponent.getUserRoles(username);
        Set<String> maskedColumns = new HashSet<>();
        
        List<ColumnLevelPolicy> applicablePolicies = columnPolicies.values().stream()
                .filter(p -> p.isEnabled() && p.getTableName().equals(tableName))
                .collect(Collectors.toList());
        
        for (ColumnLevelPolicy policy : applicablePolicies) {
            // 检查用户是否被明确允许
            if (policy.getAllowedUsers() != null && policy.getAllowedUsers().contains(username)) {
                continue;
            }
            
            // 检查角色是否被允许
            boolean roleAllowed = false;
            if (policy.getAllowedRoles() != null) {
                for (String role : userRoles) {
                    if (policy.getAllowedRoles().contains(role)) {
                        roleAllowed = true;
                        break;
                    }
                }
            }
            
            if (!roleAllowed && policy.getMaskedColumns() != null) {
                maskedColumns.addAll(policy.getMaskedColumns());
            }
        }
        
        return maskedColumns;
    }

    // ==================== 数据脱敏和匿名化 ====================

    /**
     * 数据脱敏规则
     */
    public static class DataMaskRule {
        private String ruleId;
        private String ruleName;
        private String dataType; // PHONE, ID_CARD, EMAIL, BANK_CARD, NAME, ADDRESS, CUSTOM
        private String maskPattern; // 脱敏模式
        private String replacement; // 替换字符
        private int keepStart; // 保留开头字符数
        private int keepEnd; // 保留结尾字符数
        private boolean enabled;
        
        // Getters and Setters
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public String getMaskPattern() { return maskPattern; }
        public void setMaskPattern(String maskPattern) { this.maskPattern = maskPattern; }
        public String getReplacement() { return replacement; }
        public void setReplacement(String replacement) { this.replacement = replacement; }
        public int getKeepStart() { return keepStart; }
        public void setKeepStart(int keepStart) { this.keepStart = keepStart; }
        public int getKeepEnd() { return keepEnd; }
        public void setKeepEnd(int keepEnd) { this.keepEnd = keepEnd; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /**
     * 定义数据脱敏规则
     */
    public void defineDataMaskRule(DataMaskRule rule) {
        log.info("定义数据脱敏规则: ruleId={}, dataType={}", rule.getRuleId(), rule.getDataType());
        maskRules.put(rule.getRuleId(), rule);
        
        try {
            String cacheKey = MASK_RULE_PREFIX + rule.getRuleId();
            String ruleJson = objectMapper.writeValueAsString(rule);
            stringRedisTemplate.opsForValue().set(cacheKey, ruleJson, Duration.ofDays(30));
        } catch (JsonProcessingException e) {
            log.error("缓存脱敏规则失败: ruleId={}", rule.getRuleId(), e);
        }
    }

    /**
     * 脱敏数据
     */
    public String maskData(String data, String dataType) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        // 查找适用的脱敏规则
        DataMaskRule rule = maskRules.values().stream()
                .filter(r -> r.isEnabled() && r.getDataType().equals(dataType))
                .findFirst()
                .orElse(null);
        
        if (rule != null) {
            return applyMaskRule(data, rule);
        }
        
        // 使用默认脱敏策略
        return applyDefaultMask(data, dataType);
    }

    /**
     * 应用脱敏规则
     */
    private String applyMaskRule(String data, DataMaskRule rule) {
        if (data.length() <= rule.getKeepStart() + rule.getKeepEnd()) {
            return data;
        }
        
        String replacement = rule.getReplacement() != null ? rule.getReplacement() : "*";
        int maskLength = data.length() - rule.getKeepStart() - rule.getKeepEnd();
        String mask = replacement.repeat(Math.max(1, maskLength));
        
        return data.substring(0, rule.getKeepStart()) + mask + 
               data.substring(data.length() - rule.getKeepEnd());
    }

    /**
     * 应用默认脱敏策略
     */
    private String applyDefaultMask(String data, String dataType) {
        switch (dataType) {
            case "PHONE":
                // 手机号：保留前3后4
                if (data.length() >= 11) {
                    return data.substring(0, 3) + "****" + data.substring(data.length() - 4);
                }
                break;
            case "ID_CARD":
                // 身份证：保留前6后4
                if (data.length() >= 18) {
                    return data.substring(0, 6) + "********" + data.substring(data.length() - 4);
                }
                break;
            case "EMAIL":
                // 邮箱：保留@前2字符和域名
                int atIndex = data.indexOf('@');
                if (atIndex > 2) {
                    return data.substring(0, 2) + "***" + data.substring(atIndex);
                }
                break;
            case "BANK_CARD":
                // 银行卡：保留前4后4
                if (data.length() >= 16) {
                    return data.substring(0, 4) + " **** **** " + data.substring(data.length() - 4);
                }
                break;
            case "NAME":
                // 姓名：保留姓
                if (data.length() >= 2) {
                    return data.charAt(0) + "*".repeat(data.length() - 1);
                }
                break;
            case "ADDRESS":
                // 地址：保留前6字符
                if (data.length() > 6) {
                    return data.substring(0, 6) + "****";
                }
                break;
        }
        
        // 默认：保留前后各1/4
        int keepLength = Math.max(1, data.length() / 4);
        if (data.length() > keepLength * 2) {
            return data.substring(0, keepLength) + "***" + data.substring(data.length() - keepLength);
        }
        
        return data;
    }

    /**
     * 批量脱敏数据
     */
    public Map<String, Object> maskRowData(Map<String, Object> rowData, Set<String> columnsToMask, 
                                           Map<String, String> columnDataTypes) {
        Map<String, Object> maskedData = new HashMap<>(rowData);
        
        for (String column : columnsToMask) {
            if (maskedData.containsKey(column)) {
                Object value = maskedData.get(column);
                if (value instanceof String) {
                    String dataType = columnDataTypes.getOrDefault(column, "DEFAULT");
                    maskedData.put(column, maskData((String) value, dataType));
                }
            }
        }
        
        return maskedData;
    }

    /**
     * 自动检测并脱敏敏感数据
     */
    public String autoMaskSensitiveData(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        String result = data;
        
        for (Map.Entry<String, Pattern> entry : SENSITIVE_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(result).find()) {
                result = entry.getValue().matcher(result)
                        .replaceAll(match -> maskData(match.group(), entry.getKey()));
            }
        }
        
        return result;
    }

    /**
     * 匿名化数据
     */
    public String anonymizeData(String data, String dataType) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        // 生成匿名化标识
        String anonymousId = generateAnonymousId(data);
        
        // 记录映射关系（可选，用于需要还原的场景）
        recordAnonymousMapping(data, anonymousId, dataType);
        
        return anonymousId;
    }

    /**
     * 生成匿名化ID
     */
    private String generateAnonymousId(String data) {
        return "ANON_" + securityManagerComponent.hashPassword(data).substring(0, 16);
    }

    /**
     * 记录匿名化映射
     */
    private void recordAnonymousMapping(String original, String anonymous, String dataType) {
        try {
            String cacheKey = "security:anonymous:" + anonymous;
            Map<String, String> mapping = new HashMap<>();
            mapping.put("original", securityManagerComponent.encryptData(original));
            mapping.put("dataType", dataType);
            mapping.put("createdTime", LocalDateTime.now().toString());
            
            stringRedisTemplate.opsForHash().putAll(cacheKey, mapping);
            stringRedisTemplate.expire(cacheKey, Duration.ofDays(365));
        } catch (Exception e) {
            log.error("记录匿名化映射失败", e);
        }
    }

    // ==================== 安全事件监控和告警 ====================

    /**
     * 告警规则
     */
    public static class AlertRule {
        private String ruleId;
        private String ruleName;
        private String eventType;
        private int threshold; // 阈值
        private int timeWindowMinutes; // 时间窗口（分钟）
        private String severity; // HIGH, MEDIUM, LOW
        private List<String> notifyChannels; // EMAIL, SMS, WEBHOOK
        private List<String> notifyUsers;
        private boolean enabled;
        
        // Getters and Setters
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public int getThreshold() { return threshold; }
        public void setThreshold(int threshold) { this.threshold = threshold; }
        public int getTimeWindowMinutes() { return timeWindowMinutes; }
        public void setTimeWindowMinutes(int timeWindowMinutes) { this.timeWindowMinutes = timeWindowMinutes; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public List<String> getNotifyChannels() { return notifyChannels; }
        public void setNotifyChannels(List<String> notifyChannels) { this.notifyChannels = notifyChannels; }
        public List<String> getNotifyUsers() { return notifyUsers; }
        public void setNotifyUsers(List<String> notifyUsers) { this.notifyUsers = notifyUsers; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /**
     * 安全事件
     */
    public static class SecurityEvent {
        private String eventId;
        private String eventType;
        private String username;
        private String ipAddress;
        private String resource;
        private String action;
        private String result;
        private String description;
        private Map<String, Object> details;
        private LocalDateTime eventTime;
        
        // Getters and Setters
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Object> getDetails() { return details; }
        public void setDetails(Map<String, Object> details) { this.details = details; }
        public LocalDateTime getEventTime() { return eventTime; }
        public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    }

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

    // ==================== 辅助方法 ====================

    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(String expression, Map<String, Object> rowData, String username) {
        if (expression == null || expression.isEmpty()) {
            return true;
        }
        
        // 简化的条件评估
        // 支持格式: column = value, column = ${username}
        try {
            String[] parts = expression.split("\\s*=\\s*");
            if (parts.length == 2) {
                String column = parts[0].trim();
                String expectedValue = parts[1].trim();
                
                // 替换变量
                if (expectedValue.contains("${username}")) {
                    expectedValue = expectedValue.replace("${username}", username);
                }
                
                // 移除引号
                expectedValue = expectedValue.replaceAll("^['\"]|['\"]$", "");
                
                Object actualValue = rowData.get(column);
                if (actualValue != null) {
                    return actualValue.toString().equals(expectedValue);
                }
            }
        } catch (Exception e) {
            log.error("评估条件表达式失败: expression={}", expression, e);
        }
        
        return false;
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

    // ==================== 结果类 ====================

    /**
     * 行访问结果
     */
    public static class RowAccessResult {
        private boolean allowed;
        private String policyId;
        private String message;
        
        public static RowAccessResult allowed() {
            RowAccessResult result = new RowAccessResult();
            result.allowed = true;
            result.message = "访问允许";
            return result;
        }
        
        public static RowAccessResult denied(String policyId, String message) {
            RowAccessResult result = new RowAccessResult();
            result.allowed = false;
            result.policyId = policyId;
            result.message = message;
            return result;
        }
        
        public boolean isAllowed() { return allowed; }
        public String getPolicyId() { return policyId; }
        public String getMessage() { return message; }
    }
}
