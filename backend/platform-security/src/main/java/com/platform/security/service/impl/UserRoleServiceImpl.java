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
 * 
 * Note: Department-based role assignment has been removed.
 * Roles are now assigned through:
 * 1. Direct user assignment (USER type)
 * 2. Virtual group membership (VIRTUAL_GROUP type)
 * 
 * For workflow task assignment, use the AssigneeType enum in workflow-engine-core
 * which supports BusinessUnit-based role resolution.
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
            addRoleFromAssignment(roleMap, assignment, AssignmentTargetType.USER, userId, "Direct Assignment");
        }
        
        // 2. 查询用户所属虚拟组被分配的角色 (VIRTUAL_GROUP类型)
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
    
    @Override
    public List<String> getPermissionsForRoleCodes(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of("basic:access");
        }
        
        Set<String> permissions = new HashSet<>();
        for (String roleCode : roleCodes) {
            permissions.addAll(getPermissionsForRole(roleCode));
        }
        
        if (permissions.isEmpty()) {
            permissions.add("basic:access");
        }
        
        return new ArrayList<>(permissions);
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
     * 获取角色的权限列表（从数据库查询）
     */
    private List<String> getPermissionsForRole(String roleCode) {
        try {
            // 从数据库查询角色对应的权限
            // 通过 sys_roles -> sys_role_permissions -> sys_permissions 关联查询
            List<String> permissions = jdbcTemplate.queryForList(
                "SELECT p.code FROM sys_permissions p " +
                "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
                "JOIN sys_roles r ON rp.role_id = r.id " +
                "WHERE r.code = ? AND r.status = 'ACTIVE'",
                String.class,
                roleCode
            );
            
            if (!permissions.isEmpty()) {
                log.debug("Found {} permissions for role {} from database", permissions.size(), roleCode);
                return permissions;
            }
            
            // 如果数据库中没有配置权限，返回基本权限
            log.debug("No permissions found in database for role {}, returning basic access", roleCode);
            return List.of("basic:access");
            
        } catch (Exception e) {
            log.warn("Failed to get permissions for role {}: {}", roleCode, e.getMessage());
            return List.of("basic:access");
        }
    }
}
