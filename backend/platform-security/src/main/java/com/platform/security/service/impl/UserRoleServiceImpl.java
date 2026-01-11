package com.platform.security.service.impl;

import com.platform.security.dto.RoleSource;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.entity.RoleAssignment;
import com.platform.security.enums.AssignmentTargetType;
import com.platform.security.repository.RoleAssignmentRepository;
import com.platform.security.resolver.TargetResolverFactory;
import com.platform.security.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户角色服务实现
 * 统一计算用户的有效角色，供三个前端后端使用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {
    
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final TargetResolverFactory targetResolverFactory;
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public List<UserEffectiveRole> getEffectiveRolesForUser(String userId) {
        log.debug("Calculating effective roles for user: {}", userId);
        
        // 用于存储角色ID到UserEffectiveRole的映射（用于合并来源）
        Map<String, UserEffectiveRole> roleMap = new LinkedHashMap<>();
        
        // 1. 查询直接分配给用户的角色 (USER类型)
        List<RoleAssignment> userAssignments = roleAssignmentRepository.findValidUserAssignments(userId);
        for (RoleAssignment assignment : userAssignments) {
            addRoleFromAssignment(roleMap, assignment, AssignmentTargetType.USER, userId, "直接分配");
        }
        
        // 2. 查询用户所在部门被分配的角色 (DEPARTMENT类型)
        String departmentId = getUserDepartmentId(userId);
        if (departmentId != null) {
            List<RoleAssignment> deptAssignments = roleAssignmentRepository.findValidDepartmentAssignments(departmentId);
            String deptName = getDepartmentName(departmentId);
            for (RoleAssignment assignment : deptAssignments) {
                addRoleFromAssignment(roleMap, assignment, AssignmentTargetType.DEPARTMENT, departmentId, deptName);
            }
        }
        
        // 3. 查询用户所在部门的祖先部门被分配的角色 (DEPARTMENT_HIERARCHY类型)
        List<String> ancestorDeptIds = getAncestorDepartmentIds(userId);
        if (!ancestorDeptIds.isEmpty()) {
            List<RoleAssignment> hierarchyAssignments = roleAssignmentRepository.findValidDepartmentHierarchyAssignments(ancestorDeptIds);
            for (RoleAssignment assignment : hierarchyAssignments) {
                String deptName = getDepartmentName(assignment.getTargetId()) + " (及下级)";
                addRoleFromAssignment(roleMap, assignment, AssignmentTargetType.DEPARTMENT_HIERARCHY, 
                        assignment.getTargetId(), deptName);
            }
        }
        
        // 4. 查询用户所属虚拟组被分配的角色 (VIRTUAL_GROUP类型)
        List<String> groupIds = getUserVirtualGroupIds(userId);
        if (!groupIds.isEmpty()) {
            List<RoleAssignment> groupAssignments = roleAssignmentRepository.findValidVirtualGroupAssignments(groupIds);
            for (RoleAssignment assignment : groupAssignments) {
                String groupName = getVirtualGroupName(assignment.getTargetId());
                addRoleFromAssignment(roleMap, assignment, AssignmentTargetType.VIRTUAL_GROUP, 
                        assignment.getTargetId(), groupName);
            }
        }
        
        List<UserEffectiveRole> result = new ArrayList<>(roleMap.values());
        log.debug("User {} has {} effective roles", userId, result.size());
        return result;
    }
    
    @Override
    public List<String> getEffectiveRoleCodesForUser(String userId) {
        return getEffectiveRolesForUser(userId).stream()
                .map(UserEffectiveRole::getRoleCode)
                .distinct()
                .collect(Collectors.toList());
    }
    
    @Override
    public List<String> getPermissionsForUser(String userId) {
        List<String> roleCodes = getEffectiveRoleCodesForUser(userId);
        if (roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 根据角色代码获取权限
        Set<String> permissions = new HashSet<>();
        for (String roleCode : roleCodes) {
            permissions.addAll(getPermissionsForRole(roleCode));
        }
        
        return new ArrayList<>(permissions);
    }
    
    @Override
    public boolean hasRole(String userId, String roleCode) {
        return getEffectiveRoleCodesForUser(userId).contains(roleCode);
    }
    
    @Override
    public boolean hasPermission(String userId, String permission) {
        return getPermissionsForUser(userId).contains(permission);
    }
    
    /**
     * 从分配记录添加角色到映射
     */
    private void addRoleFromAssignment(Map<String, UserEffectiveRole> roleMap, 
                                       RoleAssignment assignment,
                                       AssignmentTargetType sourceType,
                                       String sourceId,
                                       String sourceName) {
        String roleId = assignment.getRoleId();
        
        // 获取角色信息
        Map<String, Object> roleInfo = getRoleInfo(roleId);
        if (roleInfo == null) {
            return;
        }
        
        String roleCode = (String) roleInfo.get("code");
        String roleName = (String) roleInfo.get("name");
        String roleType = (String) roleInfo.get("type");
        
        // 创建来源信息
        RoleSource source = RoleSource.builder()
                .sourceType(sourceType)
                .sourceId(sourceId)
                .sourceName(sourceName)
                .assignmentId(assignment.getId())
                .build();
        
        // 如果角色已存在，添加来源；否则创建新的角色记录
        if (roleMap.containsKey(roleId)) {
            roleMap.get(roleId).addSource(source);
        } else {
            UserEffectiveRole effectiveRole = UserEffectiveRole.builder()
                    .roleId(roleId)
                    .roleCode(roleCode)
                    .roleName(roleName)
                    .roleType(roleType)
                    .sources(new ArrayList<>(List.of(source)))
                    .build();
            roleMap.put(roleId, effectiveRole);
        }
    }
    
    /**
     * 获取角色信息
     */
    private Map<String, Object> getRoleInfo(String roleId) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT id, code, name, type FROM sys_roles WHERE id = ? AND status = 'ACTIVE'",
                roleId
            );
        } catch (Exception e) {
            log.warn("Failed to get role info: {}", roleId, e);
            return null;
        }
    }
    
    /**
     * 获取用户显示名
     */
    private String getUserDisplayName(String userId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COALESCE(display_name, full_name, username) FROM sys_users WHERE id = ?",
                String.class,
                userId
            );
        } catch (Exception e) {
            return userId;
        }
    }
    
    /**
     * 获取用户所在部门ID
     */
    private String getUserDepartmentId(String userId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT department_id FROM sys_users WHERE id = ? AND deleted = false",
                String.class,
                userId
            );
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取部门名称
     */
    private String getDepartmentName(String departmentId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT name FROM sys_departments WHERE id = ?",
                String.class,
                departmentId
            );
        } catch (Exception e) {
            return departmentId;
        }
    }
    
    /**
     * 获取用户所在部门的所有祖先部门ID（包括自己）
     */
    private List<String> getAncestorDepartmentIds(String userId) {
        try {
            // 先检查用户是否有部门
            String departmentId = getUserDepartmentId(userId);
            if (departmentId == null) {
                return Collections.emptyList();
            }
            
            // 获取用户所在部门的path
            String path = null;
            try {
                path = jdbcTemplate.queryForObject(
                    "SELECT path FROM sys_departments WHERE id = ?",
                    String.class,
                    departmentId
                );
            } catch (Exception e) {
                return Collections.emptyList();
            }
            
            if (path == null || path.isEmpty()) {
                return Collections.emptyList();
            }
            
            // 从path中提取所有祖先部门ID
            // path格式: /root/parent/current
            List<String> ancestorIds = new ArrayList<>();
            String[] parts = path.split("/");
            StringBuilder currentPath = new StringBuilder();
            
            for (String part : parts) {
                if (part.isEmpty()) continue;
                currentPath.append("/").append(part);
                
                // 根据path查找部门ID
                try {
                    String deptId = jdbcTemplate.queryForObject(
                        "SELECT id FROM sys_departments WHERE path = ?",
                        String.class,
                        currentPath.toString()
                    );
                    if (deptId != null) {
                        ancestorIds.add(deptId);
                    }
                } catch (Exception ignored) {
                    // 部门可能不存在
                }
            }
            
            // 也添加用户直接所在的部门
            String userDeptId = getUserDepartmentId(userId);
            if (userDeptId != null && !ancestorIds.contains(userDeptId)) {
                ancestorIds.add(userDeptId);
            }
            
            return ancestorIds;
        } catch (Exception e) {
            log.warn("Failed to get ancestor department IDs for user: {}", userId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取用户所属的虚拟组ID列表
     */
    private List<String> getUserVirtualGroupIds(String userId) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT group_id FROM sys_virtual_group_members " +
                "WHERE user_id = ?",
                String.class,
                userId
            );
        } catch (Exception e) {
            log.warn("Failed to get virtual group IDs for user: {}", userId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取虚拟组名称
     */
    private String getVirtualGroupName(String groupId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT name FROM sys_virtual_groups WHERE id = ?",
                String.class,
                groupId
            );
        } catch (Exception e) {
            return groupId;
        }
    }
    
    /**
     * 获取角色的权限列表
     */
    private List<String> getPermissionsForRole(String roleCode) {
        // 基于角色代码返回权限
        // 这里使用硬编码的权限映射，实际项目中应该从数据库查询
        return switch (roleCode) {
            case "SYS_ADMIN", "SUPER_ADMIN", "ADMIN" -> List.of(
                    "user:read", "user:write", "user:delete",
                    "role:read", "role:write", "role:delete",
                    "system:admin"
            );
            case "SYSTEM_ADMIN" -> List.of(
                    "user:read", "user:write",
                    "role:read", "role:write",
                    "system:config"
            );
            case "TENANT_ADMIN" -> List.of(
                    "user:read", "user:write",
                    "tenant:admin"
            );
            case "AUDITOR" -> List.of(
                    "audit:read", "log:read"
            );
            case "Manager" -> List.of(
                    "workflow:approve", "workflow:reject",
                    "task:assign", "task:reassign"
            );
            case "User" -> List.of(
                    "workflow:submit", "workflow:view",
                    "task:view", "task:complete"
            );
            default -> List.of("basic:access");
        };
    }
}
