package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据访问安全组件（门面）
 *
 * <p>负责行级和列级数据权限控制、数据脱敏和匿名化处理、
 * 安全事件监控和告警功能。</p>
 *
 * <p>本类已按职责拆分为以下同包协作组件，门面保留全部 public 方法签名并委托：
 * <ul>
 *     <li>{@link RowLevelPolicyEnforcer} —— 行级权限控制</li>
 *     <li>{@link ColumnLevelPolicyEnforcer} —— 列级权限控制</li>
 *     <li>{@link DataMaskingProcessor} —— 数据脱敏与匿名化</li>
 *     <li>{@link SecurityEventMonitor} —— 安全事件监控与告警</li>
 * </ul>
 * 行为零变化（安全敏感）：数据权限过滤/掩码判定结果与拆分前逐字一致。</p>
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
public class DataAccessSecurityComponent {

    // 原始依赖（构造注入），既供 Spring 注入也用于测试 new 构造的 lazy 兜底
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AuditManagerComponent auditManagerComponent;
    private final SecurityManagerComponent securityManagerComponent;

    // 协作组件使用 @Lazy 字段注入破环；测试以 new 构造门面时这些字段为 null，由 lazy accessor 兜底
    @Lazy
    @Autowired
    private RowLevelPolicyEnforcer rowLevelPolicyEnforcer;
    @Lazy
    @Autowired
    private ColumnLevelPolicyEnforcer columnLevelPolicyEnforcer;
    @Lazy
    @Autowired
    private DataMaskingProcessor dataMaskingProcessor;
    @Lazy
    @Autowired
    private SecurityEventMonitor securityEventMonitor;

    /**
     * 保留原构造签名不变（{@code @RequiredArgsConstructor} 生成的 4 参顺序），
     * 既兼容现有测试的直接 {@code new}，也供 Spring 装配。
     */
    public DataAccessSecurityComponent(StringRedisTemplate stringRedisTemplate,
                                       ObjectMapper objectMapper,
                                       AuditManagerComponent auditManagerComponent,
                                       SecurityManagerComponent securityManagerComponent) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.auditManagerComponent = auditManagerComponent;
        this.securityManagerComponent = securityManagerComponent;
    }

    // ==================== Lazy accessor 兜底 ====================
    // Spring 装配时返回注入的单例；测试 new 构造（无 Spring）时按需用原始依赖创建协作组件，
    // 确保单实例稳定（含其内部内存缓存）。

    private synchronized SecurityEventMonitor securityEventMonitor() {
        if (securityEventMonitor == null) {
            securityEventMonitor = new SecurityEventMonitor(
                    stringRedisTemplate, objectMapper, auditManagerComponent);
        }
        return securityEventMonitor;
    }

    private synchronized RowLevelPolicyEnforcer rowLevelPolicyEnforcer() {
        if (rowLevelPolicyEnforcer == null) {
            RowLevelPolicyEnforcer enforcer = new RowLevelPolicyEnforcer(
                    stringRedisTemplate, objectMapper, securityManagerComponent);
            // 无 Spring 装配时注入共享监控器，使行级拒绝事件与门面落入同一实例
            enforcer.setSecurityEventMonitor(securityEventMonitor());
            rowLevelPolicyEnforcer = enforcer;
        }
        return rowLevelPolicyEnforcer;
    }

    private synchronized ColumnLevelPolicyEnforcer columnLevelPolicyEnforcer() {
        if (columnLevelPolicyEnforcer == null) {
            columnLevelPolicyEnforcer = new ColumnLevelPolicyEnforcer(
                    stringRedisTemplate, objectMapper, securityManagerComponent);
        }
        return columnLevelPolicyEnforcer;
    }

    private synchronized DataMaskingProcessor dataMaskingProcessor() {
        if (dataMaskingProcessor == null) {
            dataMaskingProcessor = new DataMaskingProcessor(
                    stringRedisTemplate, objectMapper, securityManagerComponent);
        }
        return dataMaskingProcessor;
    }

    // ==================== 行级权限控制 ====================

    /**
     * 定义行级权限策略
     */
    public void defineRowLevelPolicy(RowLevelPolicy policy) {
        rowLevelPolicyEnforcer().defineRowLevelPolicy(policy);
    }

    /**
     * 检查行级访问权限
     */
    public RowAccessResult checkRowAccess(String username, String tableName, Map<String, Object> rowData) {
        return rowLevelPolicyEnforcer().checkRowAccess(username, tableName, rowData);
    }

    /**
     * 生成行级过滤SQL条件
     */
    public String generateRowFilterCondition(String username, String tableName) {
        return rowLevelPolicyEnforcer().generateRowFilterCondition(username, tableName);
    }

    // ==================== 列级权限控制 ====================

    /**
     * 定义列级权限策略
     */
    public void defineColumnLevelPolicy(ColumnLevelPolicy policy) {
        columnLevelPolicyEnforcer().defineColumnLevelPolicy(policy);
    }

    /**
     * 获取用户可见列
     */
    public Set<String> getVisibleColumns(String username, String tableName, Set<String> allColumns) {
        return columnLevelPolicyEnforcer().getVisibleColumns(username, tableName, allColumns);
    }

    /**
     * 获取需要脱敏的列
     */
    public Set<String> getMaskedColumns(String username, String tableName) {
        return columnLevelPolicyEnforcer().getMaskedColumns(username, tableName);
    }

    // ==================== 数据脱敏和匿名化 ====================

    /**
     * 定义数据脱敏规则
     */
    public void defineDataMaskRule(DataMaskRule rule) {
        dataMaskingProcessor().defineDataMaskRule(rule);
    }

    /**
     * 脱敏数据
     */
    public String maskData(String data, String dataType) {
        return dataMaskingProcessor().maskData(data, dataType);
    }

    /**
     * 批量脱敏数据
     */
    public Map<String, Object> maskRowData(Map<String, Object> rowData, Set<String> columnsToMask,
                                           Map<String, String> columnDataTypes) {
        return dataMaskingProcessor().maskRowData(rowData, columnsToMask, columnDataTypes);
    }

    /**
     * 自动检测并脱敏敏感数据
     */
    public String autoMaskSensitiveData(String data) {
        return dataMaskingProcessor().autoMaskSensitiveData(data);
    }

    /**
     * 匿名化数据
     */
    public String anonymizeData(String data, String dataType) {
        return dataMaskingProcessor().anonymizeData(data, dataType);
    }

    // ==================== 安全事件监控和告警 ====================

    /**
     * 定义告警规则
     */
    public void defineAlertRule(AlertRule rule) {
        securityEventMonitor().defineAlertRule(rule);
    }

    /**
     * 记录安全事件
     */
    public void recordSecurityEvent(String username, String eventType, String description) {
        securityEventMonitor().recordSecurityEvent(username, eventType, description);
    }

    /**
     * 记录安全事件（完整版）
     */
    public void recordSecurityEvent(String username, String eventType, String description,
                                    String ipAddress, String resource, Map<String, Object> details) {
        securityEventMonitor().recordSecurityEvent(username, eventType, description, ipAddress, resource, details);
    }

    /**
     * 查询安全事件
     */
    public List<SecurityEvent> querySecurityEvents(String eventType, String username,
                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                   int limit) {
        return securityEventMonitor().querySecurityEvents(eventType, username, startTime, endTime, limit);
    }

    /**
     * 获取告警列表
     */
    public List<Map<String, Object>> getAlerts(String severity, LocalDateTime startTime,
                                               LocalDateTime endTime, int limit) {
        return securityEventMonitor().getAlerts(severity, startTime, endTime, limit);
    }

    /**
     * 获取安全监控统计
     */
    public Map<String, Object> getSecurityMonitoringStats(LocalDateTime startTime, LocalDateTime endTime) {
        return securityEventMonitor().getSecurityMonitoringStats(startTime, endTime);
    }

    /**
     * 初始化默认告警规则
     */
    public void initializeDefaultAlertRules() {
        securityEventMonitor().initializeDefaultAlertRules();
    }
}
