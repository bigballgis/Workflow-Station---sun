package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 列级权限控制执行器
 *
 * <p>由 {@link DataAccessSecurityComponent} 拆分而来，承载"列级权限控制"职责。
 * 逻辑/异常/日志逐字保留，行为零变化。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ColumnLevelPolicyEnforcer {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityManagerComponent securityManagerComponent;

    // 缓存键前缀
    private static final String COLUMN_POLICY_PREFIX = "security:column_policy:";

    // 内存缓存
    private final Map<String, ColumnLevelPolicy> columnPolicies = new ConcurrentHashMap<>();

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
}
