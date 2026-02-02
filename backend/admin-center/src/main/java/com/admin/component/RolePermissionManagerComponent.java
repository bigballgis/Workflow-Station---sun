package com.admin.component;

import com.admin.dto.request.PermissionConfig;
import com.admin.dto.response.PermissionCheckResult;
import com.admin.entity.*;
import com.admin.enums.RoleType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.RoleNotFoundException;
import com.admin.helper.PermissionHelper;
import com.admin.helper.RoleHelper;
import com.admin.repository.*;
import com.admin.util.EntityTypeConverter;
import com.platform.security.entity.User;
import com.platform.security.entity.Role;
import com.platform.security.entity.Permission;
import com.platform.security.entity.UserRole;
import com.platform.security.entity.RolePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色权限管理组件
 * 负责角色的创建、权限配置、权限检查等核心功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RolePermissionManagerComponent {
    
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionDelegationComponent delegationComponent;
    private final PermissionConflictComponent conflictComponent;
    private final RoleHelper roleHelper;
    private final PermissionHelper permissionHelper;
    
    /**
     * 创建角色
     */
    @Transactional
    public Role createRole(String name, String code, RoleType type, String description) {
        log.info("Creating role: {}", code);
        
        // 验证编码唯一性
        if (roleRepository.existsByCode(code)) {
            throw new AdminBusinessException("CODE_EXISTS", "角色编码已存在: " + code);
        }
        
        // 使用 EntityTypeConverter 转换类型
        String typeStr = EntityTypeConverter.fromRoleType(type);
        
        Role role = Role.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .code(code)
                .type(typeStr)
                .description(description)
                .status("ACTIVE")
                .build();
        
        roleRepository.save(role);
        
        log.info("Role created successfully: {}", role.getId());
        return role;
    }
    
    /**
     * 配置角色权限
     */
    @Transactional
    public void configureRolePermissions(String roleId, List<PermissionConfig> permissions) {
        log.info("Configuring permissions for role: {}", roleId);
        
        // 验证角色存在
        roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        // 清除现有权限
        rolePermissionRepository.deleteByRoleId(roleId);
        
        // 添加新权限 - 使用ID字段
        for (PermissionConfig config : permissions) {
            // 验证权限存在
            permissionRepository.findById(config.getPermissionId())
                    .orElseThrow(() -> new AdminBusinessException("PERMISSION_NOT_FOUND", 
                            "权限不存在: " + config.getPermissionId()));
            
            RolePermission rp = RolePermission.builder()
                    .id(UUID.randomUUID().toString())
                    .roleId(roleId)
                    .permissionId(config.getPermissionId())
                    .conditionType(config.getConditionType())
                    .conditionValue(config.getConditionValue())
                    .grantedAt(LocalDateTime.now())
                    .build();
            
            rolePermissionRepository.save(rp);
        }
        
        log.info("Permissions configured for role: {}", roleId);
    }
    
    /**
     * 检查权限 - 支持条件权限和委托权限
     */
    public PermissionCheckResult checkPermission(String userId, String resource, String action) {
        log.debug("Checking permission: userId={}, resource={}, action={}", userId, resource, action);
        
        // 首先检查委托权限
        if (delegationComponent.hasDelegatedPermission(userId, getPermissionId(resource, action))) {
            return PermissionCheckResult.allowed("DELEGATED", "委托权限");
        }
        
        // 获取用户所有角色
        List<Role> roles = roleRepository.findByUserId(userId);
        
        if (roles.isEmpty()) {
            return PermissionCheckResult.denied("用户没有分配任何角色");
        }
        
        // 检查每个角色的权限
        for (Role role : roles) {
            PermissionCheckResult result = checkRolePermission(role, resource, action);
            if (result.isAllowed()) {
                return result;
            }
        }
        
        return PermissionCheckResult.denied();
    }
    
    /**
     * 根据资源和操作获取权限ID
     */
    private String getPermissionId(String resource, String action) {
        return permissionRepository.findByResourceAndAction(resource, action)
                .map(Permission::getId)
                .orElse(null);
    }
    
    /**
     * 检查角色权限
     */
    private PermissionCheckResult checkRolePermission(Role role, String resource, String action) {
        // 获取角色的有效权限（包括继承的）
        Set<Permission> effectivePermissions = getEffectivePermissions(role.getId());
        
        for (Permission permission : effectivePermissions) {
            // 使用 PermissionHelper 进行匹配
            if (permissionHelper.matches(permission, resource, action)) {
                return PermissionCheckResult.allowed(role.getId(), role.getName());
            }
        }
        
        return PermissionCheckResult.denied();
    }
    
    /**
     * 获取角色的有效权限
     */
    public Set<Permission> getEffectivePermissions(String roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        return new HashSet<>(permissionRepository.findByRoleId(roleId));
    }
    
    /**
     * 获取用户的所有有效权限
     */
    public Set<Permission> getUserEffectivePermissions(String userId) {
        List<Role> roles = roleRepository.findByUserId(userId);
        
        Set<Permission> allPermissions = new HashSet<>();
        for (Role role : roles) {
            allPermissions.addAll(getEffectivePermissions(role.getId()));
        }
        
        return allPermissions;
    }
    
    /**
     * 为用户分配角色
     */
    @Transactional
    public void assignRoleToUser(String userId, String roleId, String assignedBy) {
        log.info("Assigning role {} to user {}", roleId, userId);
        
        // 检查是否已分配
        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            throw new AdminBusinessException("ROLE_ALREADY_ASSIGNED", "用户已拥有该角色");
        }
        
        // 验证角色存在
        roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        // 使用ID字段构建
        UserRole userRole = UserRole.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .roleId(roleId)
                .assignedAt(LocalDateTime.now())
                .assignedBy(assignedBy)
                .build();
        
        userRoleRepository.save(userRole);
        
        log.info("Role assigned successfully");
    }
    
    /**
     * 移除用户角色
     */
    @Transactional
    public void removeRoleFromUser(String userId, String roleId) {
        log.info("Removing role {} from user {}", roleId, userId);
        
        UserRole userRole = userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .orElseThrow(() -> new AdminBusinessException("ROLE_NOT_ASSIGNED", "用户没有该角色"));
        
        userRoleRepository.delete(userRole);
        
        log.info("Role removed successfully");
    }
    
    /**
     * 获取角色详情
     */
    public Role getRole(String roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
    }
    
    /**
     * 获取所有角色
     */
    public List<Role> getAllRoles() {
        return roleRepository.findAllActive();
    }
    
    /**
     * 根据类型获取角色
     */
    public List<Role> getRolesByType(RoleType type) {
        return roleRepository.findByType(type);
    }
    
    /**
     * 获取所有业务角色（BU_BOUNDED 和 BU_UNBOUNDED）
     */
    public List<Role> getBusinessRoles() {
        List<Role> buBounded = roleRepository.findByType(RoleType.BU_BOUNDED);
        List<Role> buUnbounded = roleRepository.findByType(RoleType.BU_UNBOUNDED);
        List<Role> result = new java.util.ArrayList<>(buBounded);
        result.addAll(buUnbounded);
        return result;
    }
    
    /**
     * 获取所有开发角色
     */
    public List<Role> getDeveloperRoles() {
        return roleRepository.findByType(RoleType.DEVELOPER);
    }
    
    /**
     * 获取所有管理角色
     */
    public List<Role> getAdminRoles() {
        return roleRepository.findByType(RoleType.ADMIN);
    }
    
    /**
     * 获取角色的成员
     */
    public List<UserRole> getRoleMembers(String roleId) {
        return userRoleRepository.findByRoleId(roleId);
    }
    
    /**
     * 获取用户的角色
     */
    public List<Role> getUserRoles(String userId) {
        return roleRepository.findByUserId(userId);
    }
    
    /**
     * 删除角色
     */
    @Transactional
    public void deleteRole(String roleId) {
        log.info("Deleting role: {}", roleId);
        
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        // 使用 RoleHelper 检查系统角色
        if (roleHelper.isSystemRole(role)) {
            throw new AdminBusinessException("CANNOT_DELETE_SYSTEM_ROLE", "系统角色不能删除");
        }
        
        // 检查是否有用户
        long memberCount = userRoleRepository.countByRoleId(roleId);
        if (memberCount > 0) {
            throw new AdminBusinessException("ROLE_HAS_MEMBERS", "角色存在成员，无法删除");
        }
        
        roleRepository.delete(role);
        
        log.info("Role deleted successfully: {}", roleId);
    }
}
