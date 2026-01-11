package com.admin.service;

import com.admin.entity.DeveloperRolePermission;
import com.admin.entity.Role;
import com.admin.entity.User;
import com.admin.enums.DeveloperPermission;
import com.admin.enums.RoleType;
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
        // 技术主管：所有权限
        DEFAULT_ROLE_PERMISSIONS.put("TECH_DIRECTOR", EnumSet.allOf(DeveloperPermission.class));
        
        // 技术组长：创建、更新、删除、查看、开发权限
        DEFAULT_ROLE_PERMISSIONS.put("TEAM_LEADER", EnumSet.of(
            DeveloperPermission.FUNCTION_UNIT_CREATE,
            DeveloperPermission.FUNCTION_UNIT_UPDATE,
            DeveloperPermission.FUNCTION_UNIT_DELETE,
            DeveloperPermission.FUNCTION_UNIT_VIEW,
            DeveloperPermission.FUNCTION_UNIT_DEVELOP,
            DeveloperPermission.FUNCTION_UNIT_PUBLISH,
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
        ));
        
        // 开发工程师：查看、开发权限（不能创建、删除功能单元）
        DEFAULT_ROLE_PERMISSIONS.put("DEVELOPER", EnumSet.of(
            DeveloperPermission.FUNCTION_UNIT_VIEW,
            DeveloperPermission.FUNCTION_UNIT_DEVELOP,
            DeveloperPermission.FORM_VIEW,
            DeveloperPermission.FORM_UPDATE,
            DeveloperPermission.PROCESS_VIEW,
            DeveloperPermission.PROCESS_UPDATE,
            DeveloperPermission.TABLE_VIEW,
            DeveloperPermission.ACTION_VIEW,
            DeveloperPermission.ACTION_UPDATE
        ));
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
        
        // 首先检查用户是否是管理员（ADMIN 角色拥有所有权限）
        List<String> allRoleIds = userRoleRepository.findRoleIdsByUserId(userId);
        for (String roleId : allRoleIds) {
            Role role = roleRepository.findById(roleId).orElse(null);
            if (role != null && "ADMIN".equals(role.getCode())) {
                // 管理员拥有所有开发权限
                return EnumSet.allOf(DeveloperPermission.class);
            }
        }
        
        // 获取用户的开发角色ID列表
        List<String> developerRoleIds = getUserDeveloperRoleIds(userId);
        
        if (developerRoleIds.isEmpty()) {
            return Collections.emptySet();
        }
        
        // 从数据库获取权限
        Set<DeveloperPermission> permissions = permissionRepository.findPermissionsByRoleIds(developerRoleIds);
        
        // 如果数据库没有配置，使用默认权限
        if (permissions.isEmpty()) {
            permissions = new HashSet<>();
            for (String roleId : developerRoleIds) {
                Role role = roleRepository.findById(roleId).orElse(null);
                if (role != null && DEFAULT_ROLE_PERMISSIONS.containsKey(role.getCode())) {
                    permissions.addAll(DEFAULT_ROLE_PERMISSIONS.get(role.getCode()));
                }
            }
        }
        
        return permissions;
    }
    
    /**
     * 解析用户ID（支持 username 或 userId）
     */
    private String resolveUserId(String userIdOrUsername) {
        // 如果是 UUID 格式，直接返回
        if (userIdOrUsername != null && userIdOrUsername.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return userIdOrUsername;
        }
        
        // 否则尝试通过 username 查找
        return userRepository.findByUsername(userIdOrUsername)
            .map(user -> user.getId().toString())
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
    
    /**
     * 获取用户的开发角色ID列表
     */
    private List<String> getUserDeveloperRoleIds(String userId) {
        List<String> allRoleIds = userRoleRepository.findRoleIdsByUserId(userId);
        
        return allRoleIds.stream()
            .filter(roleId -> {
                Role role = roleRepository.findById(roleId).orElse(null);
                return role != null && role.getType() == RoleType.DEVELOPER;
            })
            .collect(Collectors.toList());
    }
}
