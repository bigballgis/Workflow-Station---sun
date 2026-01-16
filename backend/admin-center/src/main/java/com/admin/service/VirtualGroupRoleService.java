package com.admin.service;

import com.admin.entity.Role;
import com.admin.entity.VirtualGroup;
import com.admin.entity.VirtualGroupRole;
import com.admin.enums.RoleType;
import com.admin.enums.VirtualGroupType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.RoleNotFoundException;
import com.admin.exception.VirtualGroupNotFoundException;
import com.admin.repository.RoleRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 虚拟组角色绑定服务
 * 每个虚拟组只能绑定一个角色（单角色绑定）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualGroupRoleService {
    
    private final VirtualGroupRoleRepository virtualGroupRoleRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final RoleRepository roleRepository;
    
    /**
     * 绑定角色到虚拟组（单角色绑定，会替换现有绑定）
     */
    @Transactional
    public void bindRole(String virtualGroupId, String roleId) {
        log.info("Binding role {} to virtual group {}", roleId, virtualGroupId);
        
        // 验证虚拟组存在
        VirtualGroup virtualGroup = virtualGroupRepository.findById(virtualGroupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(virtualGroupId));
        
        // 检查是否为系统内置虚拟组
        if (virtualGroup.getType() == VirtualGroupType.SYSTEM) {
            throw new AdminBusinessException("SYSTEM_GROUP_PROTECTED", 
                    "系统内置虚拟组的角色绑定不可修改");
        }
        
        // 验证角色存在且为业务角色
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        validateBusinessRole(role);
        
        // 检查是否已有绑定，如果有则删除（单角色绑定，替换现有绑定）
        Optional<VirtualGroupRole> existingBinding = virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId);
        if (existingBinding.isPresent()) {
            log.info("Replacing existing role binding for virtual group {}", virtualGroupId);
            virtualGroupRoleRepository.delete(existingBinding.get());
        }
        
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        
        virtualGroupRoleRepository.save(binding);
        log.info("Role {} bound to virtual group {} successfully", roleId, virtualGroupId);
    }
    
    /**
     * 解绑虚拟组的角色
     */
    @Transactional
    public void unbindRole(String virtualGroupId) {
        log.info("Unbinding role from virtual group {}", virtualGroupId);
        
        // 验证虚拟组存在并检查是否为系统内置虚拟组
        VirtualGroup virtualGroup = virtualGroupRepository.findById(virtualGroupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(virtualGroupId));
        
        if (virtualGroup.getType() == VirtualGroupType.SYSTEM) {
            throw new AdminBusinessException("SYSTEM_GROUP_PROTECTED", 
                    "系统内置虚拟组的角色绑定不可修改");
        }
        
        VirtualGroupRole binding = virtualGroupRoleRepository
                .findByVirtualGroupId(virtualGroupId)
                .orElseThrow(() -> new AdminBusinessException("NOT_BOUND", "该虚拟组未绑定角色"));
        
        virtualGroupRoleRepository.delete(binding);
        log.info("Role unbound from virtual group {} successfully", virtualGroupId);
    }
    
    /**
     * 获取虚拟组绑定的角色（单个）
     */
    public Optional<Role> getBoundRole(String virtualGroupId) {
        // 验证虚拟组存在
        if (!virtualGroupRepository.existsById(virtualGroupId)) {
            throw new VirtualGroupNotFoundException(virtualGroupId);
        }
        
        return virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId)
                .map(binding -> roleRepository.findById(binding.getRoleId()).orElse(null));
    }
    
    /**
     * 获取虚拟组绑定的角色ID（单个）
     */
    public Optional<String> getBoundRoleId(String virtualGroupId) {
        return virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId)
                .map(VirtualGroupRole::getRoleId);
    }
    
    /**
     * 检查虚拟组是否已绑定角色
     */
    public boolean hasRole(String virtualGroupId) {
        return virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId).isPresent();
    }
    
    /**
     * 验证角色是否为业务角色（BU_BOUNDED 或 BU_UNBOUNDED）
     */
    public void validateBusinessRole(Role role) {
        if (!role.getType().isBusinessRole()) {
            throw new AdminBusinessException("INVALID_ROLE_TYPE", 
                    "只能绑定业务角色（BU-Bounded 或 BU-Unbounded），当前角色类型: " + role.getType());
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
