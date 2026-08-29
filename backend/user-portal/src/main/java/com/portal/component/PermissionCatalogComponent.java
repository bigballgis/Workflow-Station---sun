package com.portal.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限相关只读目录与「可申请项」查询。
 * 从 {@link PermissionComponent} 拆出，行为与原实现逐字一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionCatalogComponent {

    private final RoleAccessComponent roleAccessComponent;
    private final VirtualGroupAccessComponent virtualGroupAccessComponent;
    private final FunctionUnitAccessComponent functionUnitAccessComponent;

    /**
     * 获取用户可申请的业务角色（排除已拥有的）
     */
    public List<Map<String, Object>> getAvailableRoles(String userId) {
        // 获取所有业务角色
        List<Map<String, Object>> allRoles = roleAccessComponent.getBusinessRoles();

        // 获取用户已有的角色ID
        List<Map<String, Object>> userRoles = roleAccessComponent.getUserBusinessRoles(userId);
        Set<String> userRoleIds = userRoles.stream()
                .map(r -> (String) r.get("id"))
                .collect(Collectors.toSet());

        // 过滤掉已有的角色
        return allRoles.stream()
                .filter(r -> !userRoleIds.contains(r.get("id")))
                .collect(Collectors.toList());
    }

    /**
     * 获取用户可加入的虚拟组（排除已加入的）
     */
    public List<Map<String, Object>> getAvailableVirtualGroups(String userId) {
        // 获取所有虚拟组
        List<Map<String, Object>> allGroups = virtualGroupAccessComponent.getVirtualGroups();

        // 获取用户已加入的虚拟组ID
        List<Map<String, Object>> userGroups = virtualGroupAccessComponent.getUserVirtualGroups(userId);
        Set<String> userGroupIds = userGroups.stream()
                .map(g -> (String) g.get("groupId"))
                .collect(Collectors.toSet());

        // 过滤掉已加入的、以及非 ACTIVE 的虚拟组（不可再申请加入）
        return allGroups.stream()
                .filter(g -> !userGroupIds.contains(g.get("id")))
                .filter(g -> "ACTIVE".equalsIgnoreCase(String.valueOf(g.get("status"))))
                .collect(Collectors.toList());
    }

    /**
     * Business units shown in Apply Permission, including ones the user already joined.
     * Joined units must stay selectable so the user can apply for another eligible role
     * or upgrade MEMBER → LEADER. Duplicate same-tier UBR is rejected on submit.
     */
    public List<Map<String, Object>> getAvailableBusinessUnits(String userId) {
        log.debug("Available business units for apply, user {}", userId);
        return virtualGroupAccessComponent.getBusinessUnits();
    }

    /**
     * 业务单元全量目录（扁平列表，供成员管理等场景下拉）
     */
    public List<Map<String, Object>> getBusinessUnitsCatalog() {
        return virtualGroupAccessComponent.getBusinessUnits();
    }

    /**
     * 业务单元树（保留层级，供级联选择器）。
     */
    public List<Map<String, Object>> getBusinessUnitsTree() {
        return virtualGroupAccessComponent.getBusinessUnitsTree();
    }

    /**
     * 指定业务单元已绑定的业务角色
     */
    public List<Map<String, Object>> getBusinessUnitRoles(String businessUnitId) {
        return virtualGroupAccessComponent.getBusinessUnitBoundRoles(businessUnitId);
    }

    /**
     * 按功能单元聚合受益人当前可发起「移除业务单元角色」申请的分配行：
     * 仅包含已在功能单元访问配置上绑定了业务角色的功能单元；未配置角色门槛的单元不在此聚合（避免重复罗列全部角色）。
     * 其余分配单独放在 otherAssignments。
     */
    public Map<String, Object> buildRoleRemovalOptionsByFunctionUnit(String beneficiaryUserId) {
        assertActiveBeneficiary(beneficiaryUserId);
        List<Map<String, Object>> allBuRoles = virtualGroupAccessComponent.listAllUserBusinessUnitRoles(beneficiaryUserId);
        List<Map<String, Object>> units = functionUnitAccessComponent.fetchLatestDeployedFunctionUnits();
        List<Map<String, Object>> groups = new ArrayList<>();
        Set<String> groupedKey = new LinkedHashSet<>();

        for (Map<String, Object> unit : units) {
            Object idObj = unit.get("id");
            if (idObj == null) {
                continue;
            }
            String unitId = idObj.toString();
            Boolean enabled = (Boolean) unit.get("enabled");
            if (Boolean.FALSE.equals(enabled)) {
                continue;
            }
            Set<String> allowed = functionUnitAccessComponent.getFunctionUnitAllowedRoles(unitId);
            if (allowed == null || allowed.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> assignments = new ArrayList<>();
            for (Map<String, Object> row : allBuRoles) {
                String roleId = row.get("roleId") != null ? row.get("roleId").toString() : null;
                if (roleId == null || !allowed.contains(roleId)) {
                    continue;
                }
                String buId = row.get("businessUnitId") != null ? row.get("businessUnitId").toString() : null;
                if (buId == null) {
                    continue;
                }
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("assignmentId", row.get("id") != null ? row.get("id").toString() : null);
                a.put("businessUnitId", buId);
                a.put("businessUnitName", row.get("businessUnitName"));
                a.put("roleId", roleId);
                a.put("roleName", row.get("roleName"));
                assignments.add(a);
                groupedKey.add(buId + "\0" + roleId);
            }
            if (!assignments.isEmpty()) {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("functionUnitId", unitId);
                g.put("functionUnitName", unit.get("name"));
                g.put("functionUnitCode", unit.get("code"));
                g.put("assignments", assignments);
                groups.add(g);
            }
        }

        List<Map<String, Object>> other = new ArrayList<>();
        for (Map<String, Object> row : allBuRoles) {
            String buId = row.get("businessUnitId") != null ? row.get("businessUnitId").toString() : null;
            String roleId = row.get("roleId") != null ? row.get("roleId").toString() : null;
            if (buId == null || roleId == null) {
                continue;
            }
            if (groupedKey.contains(buId + "\0" + roleId)) {
                continue;
            }
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("assignmentId", row.get("id") != null ? row.get("id").toString() : null);
            a.put("businessUnitId", buId);
            a.put("businessUnitName", row.get("businessUnitName"));
            a.put("roleId", roleId);
            a.put("roleName", row.get("roleName"));
            other.add(a);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("functionUnitGroups", groups);
        result.put("otherAssignments", other);
        return result;
    }

    /**
     * 获取用户当前的角色列表
     */
    public List<Map<String, Object>> getUserCurrentRoles(String userId) {
        return roleAccessComponent.getUserBusinessRoles(userId);
    }

    /**
     * 获取用户当前的虚拟组成员身份
     */
    public List<Map<String, Object>> getUserCurrentVirtualGroups(String userId) {
        return virtualGroupAccessComponent.getUserVirtualGroups(userId);
    }

    private void assertActiveBeneficiary(String beneficiaryUserId) {
        if (!roleAccessComponent.isActivePortalUser(beneficiaryUserId)) {
            throw new IllegalArgumentException("Beneficiary does not exist or account is not available");
        }
    }
}
