package com.admin.component;

import com.admin.enums.RoleType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.AdminForbiddenException;
import com.admin.exception.RoleNotFoundException;
import com.admin.helper.RoleHelper;
import com.admin.repository.*;
import com.admin.util.EntityTypeConverter;
import com.platform.security.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.platform.common.i18n.I18nService;
import java.util.*;

/**
 * 角色权限管理组件
 * 负责角色的创建、修改、删除
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RolePermissionManagerComponent {

    private final RoleRepository roleRepository;
    private final RoleHelper roleHelper;
    private final RoleReferenceFinder roleReferenceFinder;
    private final I18nService i18nService;
    
    /**
     * 创建角色
     */
    @Transactional
    public Role createRole(String name, String code, RoleType type, String displayName) {
        log.info("Creating role: {}", code);

        // The four platform roles are fixed and every other role is held per business unit (D-14).
        if (type != RoleType.BU_BOUNDED) {
            throw new AdminBusinessException("ROLE_TYPE_NOT_ALLOWED",
                    i18nService.getMessage("admin.role.only_bu_bounded_creatable"));
        }
        
        // 验证编码唯一性
        if (roleRepository.existsByCode(code)) {
            throw new AdminBusinessException("CODE_EXISTS", "Role code already exists: " + code);
        }
        
        // 使用 EntityTypeConverter 转换类型
        String typeStr = EntityTypeConverter.fromRoleType(type);
        
        Role role = Role.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .code(code)
                .type(typeStr)
                .displayName(displayName)
                .status("ACTIVE")
                .build();
        
        roleRepository.save(role);
        
        log.info("Role created successfully: {}", role.getId());
        return role;
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
        return roleRepository.findAll();
    }
    
    /**
     * 根据类型获取角色
     * Note: Role.type is String, not enum
     */
    public List<Role> getRolesByType(String type) {
        return roleRepository.findByType(type);
    }

    /**
     * 分页获取角色，支持按类型筛选。
     * 不使用 findByConditions 因为其 LOWER() 在 PostgreSQL bytea 列上报错。
     */
    public org.springframework.data.domain.Page<Role> getRolesPaged(int page, int size, String type) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "name"));
        if (type != null && !type.isEmpty()) {
            return roleRepository.findByType(type, pageable);
        }
        return roleRepository.findAll(pageable);
    }
    
    /**
     * 获取所有业务角色（BU_BOUNDED，平台访问模型 D-14 / D-64）
     */
    public List<Role> getBusinessRoles() {
        return roleRepository.findByType("BU_BOUNDED");
    }
    
    /**
     * 获取所有开发角色
     */
    public List<Role> getDeveloperRoles() {
        return roleRepository.findByType("DEVELOPER");
    }
    
    /**
     * 获取所有管理角色
     */
    public List<Role> getAdminRoles() {
        return roleRepository.findByType("ADMIN");
    }
    
    /**
     * 删除角色
     */
    @Transactional
    public void deleteRole(String roleId) {
        log.info("Deleting role: {}", roleId);
        
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        // Platform (System list) roles are changed by nobody: a refusal of the operation itself, 403 (AC-13).
        if (roleHelper.isSystemRole(role)) {
            throw new AdminForbiddenException(i18nService.getMessage("admin.role.system_role_read_only"));
        }
        
        // Deleting would cascade away BU memberships and leave processes and access rules pointing at
        // nothing (platform access model §4.3): refuse and say where the role is used.
        List<String> references = roleReferenceFinder.find(role);
        if (!references.isEmpty()) {
            throw new AdminBusinessException("ROLE_IN_USE",
                    i18nService.getMessage("admin.role.in_use", String.join("; ", references)));
        }
        
        roleRepository.delete(role);
        
        log.info("Role deleted successfully: {}", roleId);
    }
    
    /**
     * 更新角色
     */
    @Transactional
    public Role updateRole(String roleId, String name, String displayName, String status) {
        log.info("Updating role: {}", roleId);
        
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        // Platform (System list) roles are changed by nobody: a refusal of the operation itself, 403 (AC-13).
        if (roleHelper.isSystemRole(role)) {
            throw new AdminForbiddenException(i18nService.getMessage("admin.role.system_role_read_only"));
        }
        
        if (name != null) {
            role.setName(name);
        }
        if (displayName != null) {
            role.setDisplayName(displayName);
        }
        if (status != null) {
            role.setStatus(status);
        }
        
        roleRepository.save(role);
        
        log.info("Role updated successfully: {}", roleId);
        return role;
    }
}
