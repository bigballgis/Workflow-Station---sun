package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 行级权限控制执行器
 *
 * <p>由 {@link DataAccessSecurityComponent} 拆分而来，承载"行级权限控制"职责。
 * 逻辑/异常/日志逐字保留，行为零变化。</p>
 */
@Slf4j
@Component
public class RowLevelPolicyEnforcer {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityManagerComponent securityManagerComponent;

    // 与 SecurityEventMonitor 之间使用 @Lazy 字段注入破环（行级拒绝需记录安全事件）
    @Lazy
    @Autowired
    private SecurityEventMonitor securityEventMonitor;

    public RowLevelPolicyEnforcer(StringRedisTemplate stringRedisTemplate,
                                  ObjectMapper objectMapper,
                                  SecurityManagerComponent securityManagerComponent) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.securityManagerComponent = securityManagerComponent;
    }

    /**
     * 由门面在无 Spring 装配（如直接 {@code new} 的测试场景）时注入共享的安全事件监控器，
     * 确保行级拒绝事件落入与门面一致的单实例，行为零变化。
     */
    void setSecurityEventMonitor(SecurityEventMonitor securityEventMonitor) {
        this.securityEventMonitor = securityEventMonitor;
    }

    // 缓存键前缀
    private static final String ROW_POLICY_PREFIX = "security:row_policy:";

    // 内存缓存
    private final Map<String, RowLevelPolicy> rowPolicies = new ConcurrentHashMap<>();

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
                    securityEventMonitor.recordSecurityEvent(username, "ROW_ACCESS_DENIED",
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
}
