package com.admin.service;

import com.admin.entity.DeveloperRolePermission;
import com.platform.security.entity.Role;
import com.platform.security.entity.User;
import com.admin.enums.DeveloperPermission;
import com.admin.repository.DeveloperRolePermissionRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 开发者权限服务
 * 管理开发角色的权限检查和分配
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeveloperPermissionService {
    
    private final DeveloperRolePermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    
    // 预定义的角色权限映射
    private static final Map<String, Set<DeveloperPermission>> DEFAULT_ROLE_PERMISSIONS = new HashMap<>();
    
    static {
        // Technical Lead (技术主管)：所有权限
        DEFAULT_ROLE_PERMISSIONS.put("TECH_LEAD", EnumSet.allOf(DeveloperPermission.class));

        // Team Lead：与 Developer 同一套设计/发布能力 + 功能单元创建/删除/分配开发组
        EnumSet<DeveloperPermission> teamLead = EnumSet.of(
            DeveloperPermission.FUNCTION_UNIT_CREATE,
            DeveloperPermission.FUNCTION_UNIT_UPDATE,
            DeveloperPermission.FUNCTION_UNIT_DELETE,
            DeveloperPermission.FUNCTION_UNIT_VIEW,
            DeveloperPermission.FUNCTION_UNIT_DEVELOP,
            DeveloperPermission.FUNCTION_UNIT_PUBLISH,
            DeveloperPermission.FUNCTION_UNIT_ASSIGN_DEV_GROUP,
            DeveloperPermission.FORM_CREATE,
            DeveloperPermission.FORM_UPDATE,
            DeveloperPermission.FORM_DELETE,
            DeveloperPermission.FORM_VIEW,
            DeveloperPermission.PROCESS_CREATE,
            DeveloperPermission.PROCESS_UPDATE,
            DeveloperPermission.PROCESS_DELETE,
            DeveloperPermission.PROCESS_VIEW,
            DeveloperPermission.TABLE_CREATE,
            DeveloperPermission.TABLE_UPDATE,
            DeveloperPermission.TABLE_DELETE,
            DeveloperPermission.TABLE_VIEW,
            DeveloperPermission.ACTION_CREATE,
            DeveloperPermission.ACTION_UPDATE,
            DeveloperPermission.ACTION_DELETE,
            DeveloperPermission.ACTION_VIEW
        );
        DEFAULT_ROLE_PERMISSIONS.put("TEAM_LEAD", teamLead);

        // Developer：与 Team Lead 相同的设计站权限，但不能创建/删除功能单元、不能分配开发组
        EnumSet<DeveloperPermission> developer = teamLead.clone();
        developer.remove(DeveloperPermission.FUNCTION_UNIT_CREATE);
        developer.remove(DeveloperPermission.FUNCTION_UNIT_DELETE);
        developer.remove(DeveloperPermission.FUNCTION_UNIT_ASSIGN_DEV_GROUP);
        DEFAULT_ROLE_PERMISSIONS.put("DEVELOPER", developer);

        DEFAULT_ROLE_PERMISSIONS.put("AUDITOR", EnumSet.of(DeveloperPermission.FUNCTION_UNIT_VIEW));
    }
    
    /**
     * 检查用户是否有指定权限
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(String userId, DeveloperPermission permission) {
        Set<DeveloperPermission> userPermissions = getUserPermissions(userId);
        return userPermissions.contains(permission);
    }
    
    /**
     * 检查用户是否有指定权限（通过权限代码）
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(String userId, String permissionCode) {
        try {
            DeveloperPermission permission = DeveloperPermission.fromCode(permissionCode);
            return hasPermission(userId, permission);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown permission code: {}", permissionCode);
            return false;
        }
    }
    
    /**
     * 获取用户的所有开发权限
     * @param userIdOrUsername 用户ID或用户名
     */
    @Transactional(readOnly = true)
    public Set<DeveloperPermission> getUserPermissions(String userIdOrUsername) {
        // 解析用户ID（支持 username 或 userId）
        String userId = resolveUserId(userIdOrUsername);
        if (userId == null) {
            log.warn("User not found: {}", userIdOrUsername);
            return Collections.emptySet();
        }
        
        List<Role> activeRoles = loadActiveRoles(userId);
        if (hasSysAdmin(activeRoles)) {
            log.info("User {} has SYS_ADMIN, granting all developer permissions", userId);
            return EnumSet.allOf(DeveloperPermission.class);
        }
        Set<DeveloperPermission> permissions = collectStudioPermissions(activeRoles);
        return clampWithoutDeveloperRole(activeRoles, permissions);
    }

    private List<Role> loadActiveRoles(String userId) {
        List<String> allRoleIds = userRoleRepository.findAllRoleIdsByUserId(userId);
        List<Role> roles = new ArrayList<>();
        for (String roleId : allRoleIds) {
            Role role = roleRepository.findById(roleId).orElse(null);
            if (role != null && "ACTIVE".equals(role.getStatus())) {
                roles.add(role);
            }
        }
        return roles;
    }

    private boolean hasSysAdmin(List<Role> roles) {
        return roles.stream().anyMatch(role -> "SYS_ADMIN".equals(role.getCode()));
    }

    private Set<DeveloperPermission> collectStudioPermissions(List<Role> roles) {
        List<String> studioRoleIds = roles.stream()
                .filter(role -> "DEVELOPER".equals(role.getType()) || "AUDITOR".equals(role.getType()))
                .map(Role::getId)
                .collect(Collectors.toList());
        if (studioRoleIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<DeveloperPermission> permissions = new HashSet<>(
                permissionRepository.findPermissionsByRoleIds(studioRoleIds));
        if (!permissions.isEmpty()) {
            return permissions;
        }
        for (Role role : roles) {
            if (DEFAULT_ROLE_PERMISSIONS.containsKey(role.getCode())) {
                permissions.addAll(DEFAULT_ROLE_PERMISSIONS.get(role.getCode()));
            }
        }
        return permissions;
    }

    private Set<DeveloperPermission> clampWithoutDeveloperRole(
            List<Role> roles, Set<DeveloperPermission> permissions) {
        boolean hasDeveloperType = roles.stream().anyMatch(role -> "DEVELOPER".equals(role.getType()));
        if (hasDeveloperType) {
            return permissions;
        }
        if (permissions.contains(DeveloperPermission.FUNCTION_UNIT_VIEW)) {
            return EnumSet.of(DeveloperPermission.FUNCTION_UNIT_VIEW);
        }
        return Collections.emptySet();
    }
    
    /**
     * 解析用户ID（支持 username 或 userId）
     */
    private String resolveUserId(String userIdOrUsername) {
        if (userIdOrUsername == null || userIdOrUsername.isEmpty()) {
            return null;
        }
        
        // 首先尝试直接作为 userId 查找
        if (userRepository.existsById(userIdOrUsername)) {
            return userIdOrUsername;
        }
        
        // 否则尝试通过 username 查找
        return userRepository.findByUsername(userIdOrUsername)
            .map(User::getId)
            .orElse(null);
    }
    
    /**
     * 获取用户的所有开发权限代码（字符串形式）
     */
    @Transactional(readOnly = true)
    public List<String> getUserPermissionCodes(String userId) {
        return getUserPermissions(userId).stream()
            .map(DeveloperPermission::getCode)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取角色的权限列表
     */
    @Transactional(readOnly = true)
    public Set<DeveloperPermission> getRolePermissions(String roleId) {
        Set<DeveloperPermission> permissions = permissionRepository.findPermissionsByRoleId(roleId);
        
        // 如果数据库没有配置，使用默认权限
        if (permissions.isEmpty()) {
            Role role = roleRepository.findById(roleId).orElse(null);
            if (role != null && DEFAULT_ROLE_PERMISSIONS.containsKey(role.getCode())) {
                return DEFAULT_ROLE_PERMISSIONS.get(role.getCode());
            }
        }
        
        return permissions;
    }
    
    /**
     * 为角色分配权限
     */
    @Transactional
    public void assignPermissions(String roleId, Set<DeveloperPermission> permissions) {
        // 先删除现有权限
        permissionRepository.deleteByRoleId(roleId);
        
        // 添加新权限
        List<DeveloperRolePermission> newPermissions = permissions.stream()
            .map(p -> DeveloperRolePermission.builder()
                .roleId(roleId)
                .permission(p)
                .build())
            .collect(Collectors.toList());
        
        permissionRepository.saveAll(newPermissions);
        log.info("Assigned {} permissions to role {}", permissions.size(), roleId);
    }
    
    /**
     * 初始化默认角色权限
     */
    @Transactional
    public void initializeDefaultPermissions() {
        for (Map.Entry<String, Set<DeveloperPermission>> entry : DEFAULT_ROLE_PERMISSIONS.entrySet()) {
            String roleCode = entry.getKey();
            Set<DeveloperPermission> permissions = entry.getValue();
            
            roleRepository.findByCode(roleCode).ifPresent(role -> {
                if (permissionRepository.findByRoleId(role.getId()).isEmpty()) {
                    assignPermissions(role.getId(), permissions);
                    log.info("Initialized default permissions for role: {}", roleCode);
                }
            });
        }
    }
}
