package com.admin.component;

import com.admin.entity.ColumnPermission;
import com.admin.entity.DataPermissionRule;
import com.admin.enums.DataPermissionType;
import com.admin.enums.DataScopeType;
import com.admin.repository.ColumnPermissionRepository;
import com.admin.repository.DataPermissionRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据权限管理组件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPermissionManagerComponent {
    
    private final DataPermissionRuleRepository ruleRepository;
    private final ColumnPermissionRepository columnPermissionRepository;
    
    // ==================== 规则管理 ====================
    
    @Transactional
    public DataPermissionRule createRule(DataPermissionRuleRequest request) {
        DataPermissionRule rule = DataPermissionRule.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .permissionType(request.getPermissionType())
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .resourceType(request.getResourceType())
                .dataScope(request.getDataScope())
                .customFilter(request.getCustomFilter())
                .priority(request.getPriority() != null ? request.getPriority() : 100)
                .enabled(true)
                .build();
        return ruleRepository.save(rule);
    }
    
    public List<DataPermissionRule> getRulesForTarget(String targetType, String targetId) {
        return ruleRepository.findByTargetTypeAndTargetIdAndEnabledOrderByPriority(targetType, targetId, true);
    }
    
    public List<DataPermissionRule> getApplicableRules(String resourceType, List<String> roleIds, 
                                                        List<String> deptIds, String userId) {
        return ruleRepository.findApplicableRules(resourceType, roleIds, deptIds, userId);
    }

    
    // ==================== 数据过滤 ====================
    
    /**
     * 生成数据过滤SQL条件
     */
    public String generateFilterCondition(String resourceType, DataPermissionContext context) {
        List<DataPermissionRule> rules = getApplicableRules(
                resourceType, context.getRoleIds(), context.getDeptIds(), context.getUserId());
        
        if (rules.isEmpty()) {
            return "1=1"; // 无规则时返回全部
        }
        
        // 取最高优先级规则
        DataPermissionRule rule = rules.get(0);
        return buildFilterSql(rule, context);
    }
    
    private String buildFilterSql(DataPermissionRule rule, DataPermissionContext context) {
        return switch (rule.getDataScope()) {
            case ALL -> "1=1";
            case SELF -> String.format("created_by = '%s'", context.getUserId());
            case DEPARTMENT -> String.format("department_id = '%s'", context.getCurrentDeptId());
            case DEPARTMENT_AND_CHILDREN -> buildDeptHierarchyFilter(context.getCurrentDeptId(), context.getChildDeptIds());
            case CUSTOM -> rule.getCustomFilter() != null ? rule.getCustomFilter() : "1=1";
        };
    }
    
    private String buildDeptHierarchyFilter(String deptId, List<String> childDeptIds) {
        List<String> allDepts = new ArrayList<>();
        allDepts.add(deptId);
        if (childDeptIds != null) {
            allDepts.addAll(childDeptIds);
        }
        String deptList = allDepts.stream().map(d -> "'" + d + "'").collect(Collectors.joining(","));
        return String.format("department_id IN (%s)", deptList);
    }
    
    // ==================== 列级权限 ====================
    
    @Transactional
    public ColumnPermission addColumnPermission(String ruleId, ColumnPermissionRequest request) {
        ColumnPermission permission = ColumnPermission.builder()
                .id(UUID.randomUUID().toString())
                .ruleId(ruleId)
                .columnName(request.getColumnName())
                .visible(request.getVisible() != null ? request.getVisible() : true)
                .masked(request.getMasked() != null ? request.getMasked() : false)
                .maskType(request.getMaskType())
                .maskExpression(request.getMaskExpression())
                .build();
        return columnPermissionRepository.save(permission);
    }
    
    public List<ColumnPermission> getColumnPermissions(String ruleId) {
        return columnPermissionRepository.findByRuleId(ruleId);
    }
    
    /**
     * 获取用户对资源的列权限
     */
    public Map<String, ColumnPermissionInfo> getColumnPermissionsForUser(
            String resourceType, DataPermissionContext context) {
        List<DataPermissionRule> rules = getApplicableRules(
                resourceType, context.getRoleIds(), context.getDeptIds(), context.getUserId());
        
        Map<String, ColumnPermissionInfo> result = new HashMap<>();
        for (DataPermissionRule rule : rules) {
            List<ColumnPermission> permissions = columnPermissionRepository.findByRuleId(rule.getId());
            for (ColumnPermission perm : permissions) {
                result.putIfAbsent(perm.getColumnName(), ColumnPermissionInfo.builder()
                        .columnName(perm.getColumnName())
                        .visible(perm.getVisible())
                        .masked(perm.getMasked())
                        .maskType(perm.getMaskType())
                        .build());
            }
        }
        return result;
    }
    
    // ==================== 数据脱敏 ====================
    
    public String maskValue(String value, String maskType) {
        if (value == null || value.isEmpty()) return value;
        
        return switch (maskType) {
            case "phone" -> maskPhone(value);
            case "email" -> maskEmail(value);
            case "idcard" -> maskIdCard(value);
            case "name" -> maskName(value);
            case "bankcard" -> maskBankCard(value);
            default -> maskDefault(value);
        };
    }
    
    private String maskPhone(String phone) {
        if (phone.length() < 7) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
    
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***" + email.substring(atIndex);
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
    
    private String maskIdCard(String idCard) {
        if (idCard.length() < 10) return "******";
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }
    
    private String maskName(String name) {
        if (name == null || name.isEmpty()) return "*";
        if (name.length() == 1) return "*";
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }
    
    private String maskBankCard(String card) {
        if (card == null || card.length() < 8) return "****";
        return card.substring(0, 4) + " **** **** " + card.substring(card.length() - 4);
    }
    
    private String maskDefault(String value) {
        if (value == null || value.isEmpty()) return "*";
        if (value.length() == 1) return "*";
        if (value.length() == 2) return value.charAt(0) + "*";
        return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
    }

    
    // ==================== 内部类 ====================
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DataPermissionRuleRequest {
        private String name;
        private DataPermissionType permissionType;
        private String targetType;
        private String targetId;
        private String resourceType;
        private DataScopeType dataScope;
        private String customFilter;
        private Integer priority;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ColumnPermissionRequest {
        private String columnName;
        private Boolean visible;
        private Boolean masked;
        private String maskType;
        private String maskExpression;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DataPermissionContext {
        private String userId;
        private List<String> roleIds;
        private String currentDeptId;
        private List<String> deptIds;
        private List<String> childDeptIds;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ColumnPermissionInfo {
        private String columnName;
        private Boolean visible;
        private Boolean masked;
        private String maskType;
    }
}
