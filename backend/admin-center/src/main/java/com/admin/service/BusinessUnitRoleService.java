package com.admin.service;

import com.platform.security.entity.BusinessUnitRole;
import com.platform.security.entity.Role;
import com.admin.enums.RoleType;
import com.admin.helper.RoleHelper;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.BusinessUnitNotFoundException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.BusinessUnitRoleRepository;
import com.admin.repository.RoleRepository;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 业务单元角色绑定服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessUnitRoleService {
    
    private final BusinessUnitRoleRepository businessUnitRoleRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final RoleRepository roleRepository;
    private final RoleHelper roleHelper;
    private final I18nService i18nService;
    
    /**
     * 绑定角色到业务单元
     */
    @Transactional
    public void bindRole(String businessUnitId, String roleId) {
        log.info("Binding role {} to business unit {}", roleId, businessUnitId);
        
        // 验证业务单元存在
        if (!businessUnitRepository.existsById(businessUnitId)) {
            throw new BusinessUnitNotFoundException(businessUnitId);
        }
        
        // 验证角色存在且为业务角色
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        validateBusinessRole(role);
        
        // 检查是否已绑定
        if (businessUnitRoleRepository.existsByBusinessUnitIdAndRoleId(businessUnitId, roleId)) {
            throw new AdminBusinessException("ALREADY_BOUND", i18nService.getMessage("admin.role_already_bound"));
        }
        
        BusinessUnitRole binding = BusinessUnitRole.builder()
                .id(UUID.randomUUID().toString())
                .businessUnitId(businessUnitId)
                .roleId(roleId)
                .build();
        
        businessUnitRoleRepository.save(binding);
        log.info("Role {} bound to business unit {} successfully", roleId, businessUnitId);
    }
    
    /**
     * 解绑业务单元的角色
     */
    @Transactional
    public void unbindRole(String businessUnitId, String roleId) {
        log.info("Unbinding role {} from business unit {}", roleId, businessUnitId);
        
        BusinessUnitRole binding = businessUnitRoleRepository
                .findByBusinessUnitIdAndRoleId(businessUnitId, roleId)
                .orElseThrow(() -> new AdminBusinessException("NOT_BOUND", i18nService.getMessage("admin.role_not_bound")));
        
        businessUnitRoleRepository.delete(binding);
        log.info("Role {} unbound from business unit {} successfully", roleId, businessUnitId);
    }
    
    /**
     * 获取业务单元绑定的角色列表
     */
    public List<Role> getBoundRoles(String businessUnitId) {
        // 验证业务单元存在
        if (!businessUnitRepository.existsById(businessUnitId)) {
            throw new BusinessUnitNotFoundException(businessUnitId);
        }
        
        // Get role IDs and fetch roles explicitly
        List<String> roleIds = businessUnitRoleRepository.findByBusinessUnitId(businessUnitId)
                .stream()
                .map(BusinessUnitRole::getRoleId)
                .collect(Collectors.toList());
        
        return roleRepository.findAllById(roleIds);
    }
    
    /**
     * 获取业务单元绑定的角色ID列表
     */
    public List<String> getBoundRoleIds(String businessUnitId) {
        return businessUnitRoleRepository.findByBusinessUnitId(businessUnitId)
                .stream()
                .map(BusinessUnitRole::getRoleId)
                .collect(Collectors.toList());
    }
    
    /**
     * 验证角色是否为业务角色（BU_BOUNDED 或 BU_UNBOUNDED）
     */
    public void validateBusinessRole(Role role) {
        if (!roleHelper.isBusinessRole(role)) {
            throw new AdminBusinessException("INVALID_ROLE_TYPE", 
                    i18nService.getMessage("admin.only_business_role", role.getType()));
        }
    }
    
    /**
     * 验证角色ID是否为业务角色
     */
    public void validateBusinessRoleById(String roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        validateBusinessRole(role);
    }
}
