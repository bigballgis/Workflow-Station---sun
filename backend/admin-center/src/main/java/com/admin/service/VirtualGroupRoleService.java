package com.admin.service;

import com.platform.security.entity.Role;
import com.platform.security.entity.VirtualGroupRole;
import com.admin.exception.VirtualGroupNotFoundException;
import com.admin.repository.RoleRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

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
     * 获取虚拟组绑定的角色（单个）
     */
    public Optional<Role> getBoundRole(String virtualGroupId) {
        // 验证虚拟组存在
        if (!virtualGroupRepository.existsById(virtualGroupId)) {
            throw new VirtualGroupNotFoundException(virtualGroupId);
        }
        
        return virtualGroupRoleRepository.findSingleByVirtualGroupId(virtualGroupId)
                .map(binding -> roleRepository.findById(binding.getRoleId()).orElse(null));
    }
    
    /**
     * 获取虚拟组绑定的角色ID（单个）
     */
    public Optional<String> getBoundRoleId(String virtualGroupId) {
        return virtualGroupRoleRepository.findSingleByVirtualGroupId(virtualGroupId)
                .map(VirtualGroupRole::getRoleId);
    }
    
    /**
     * 检查虚拟组是否已绑定角色
     */
    public boolean hasRole(String virtualGroupId) {
        return virtualGroupRoleRepository.findSingleByVirtualGroupId(virtualGroupId).isPresent();
    }
}
