package com.admin.service;

import com.admin.dto.request.FunctionUnitAccessRequest;
import com.admin.dto.response.FunctionUnitAccessInfo;
import com.admin.entity.*;
import com.admin.enums.RoleType;
import com.admin.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 功能单元访问权限服务
 * 简化后只支持业务角色分配
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionUnitAccessService {
    
    private final FunctionUnitAccessRepository accessRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    
    /**
     * 获取功能单元的所有访问配置
     */
    @Transactional(readOnly = true)
    public List<FunctionUnitAccessInfo> getAccessConfigs(String functionUnitId) {
        return accessRepository.findByFunctionUnitId(functionUnitId)
                .stream()
                .map(FunctionUnitAccessInfo::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * 添加访问配置（只允许业务角色）
     */
    @Transactional
    public FunctionUnitAccessInfo addAccessConfig(String functionUnitId, FunctionUnitAccessRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new EntityNotFoundException("功能单元不存在: " + functionUnitId));
        
        // 验证角色存在且为业务角色
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new EntityNotFoundException("角色不存在: " + request.getRoleId()));
        
        if (role.getType() != RoleType.BUSINESS) {
            throw new IllegalArgumentException("只能将功能单元分配给业务角色，当前角色类型: " + role.getType());
        }
        
        // 检查是否已存在相同配置
        if (accessRepository.existsByFunctionUnitIdAndRoleId(functionUnitId, request.getRoleId())) {
            throw new IllegalArgumentException("该角色已被分配访问权限");
        }
        
        FunctionUnitAccess access = FunctionUnitAccess.builder()
                .functionUnit(functionUnit)
                .roleId(request.getRoleId())
                .roleName(role.getName())
                .build();
        
        access = accessRepository.save(access);
        log.info("Added access config for function unit {}: roleId={}", functionUnitId, request.getRoleId());
        
        return FunctionUnitAccessInfo.fromEntity(access);
    }
    
    /**
     * 删除访问配置
     */
    @Transactional
    public void removeAccessConfig(String functionUnitId, String accessId) {
        FunctionUnitAccess access = accessRepository.findById(accessId)
                .orElseThrow(() -> new EntityNotFoundException("访问配置不存在: " + accessId));
        
        if (!access.getFunctionUnit().getId().equals(functionUnitId)) {
            throw new IllegalArgumentException("访问配置不属于该功能单元");
        }
        
        accessRepository.delete(access);
        log.info("Removed access config {} from function unit {}", accessId, functionUnitId);
    }
    
    /**
     * 删除功能单元的所有访问配置
     */
    @Transactional
    public void deleteAllAccessConfigs(String functionUnitId) {
        accessRepository.deleteByFunctionUnitId(functionUnitId);
        log.info("Deleted all access configs for function unit {}", functionUnitId);
    }
    
    /**
     * 批量设置访问配置（替换现有配置）
     */
    @Transactional
    public List<FunctionUnitAccessInfo> setAccessConfigs(String functionUnitId, List<FunctionUnitAccessRequest> requests) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new EntityNotFoundException("功能单元不存在: " + functionUnitId));
        
        // 删除现有配置
        accessRepository.deleteByFunctionUnitId(functionUnitId);
        
        // 添加新配置
        List<FunctionUnitAccess> newConfigs = new ArrayList<>();
        for (FunctionUnitAccessRequest request : requests) {
            // 验证角色存在且为业务角色
            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new EntityNotFoundException("角色不存在: " + request.getRoleId()));
            
            if (role.getType() != RoleType.BUSINESS) {
                throw new IllegalArgumentException("只能将功能单元分配给业务角色: " + role.getName());
            }
            
            FunctionUnitAccess access = FunctionUnitAccess.builder()
                    .functionUnit(functionUnit)
                    .roleId(request.getRoleId())
                    .roleName(role.getName())
                    .build();
            newConfigs.add(access);
        }
        
        List<FunctionUnitAccess> saved = accessRepository.saveAll(newConfigs);
        log.info("Set {} access configs for function unit {}", saved.size(), functionUnitId);
        
        return saved.stream()
                .map(FunctionUnitAccessInfo::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * 检查用户是否有权限访问功能单元
     */
    @Transactional(readOnly = true)
    public boolean hasAccess(String functionUnitId, String userId) {
        List<FunctionUnitAccess> configs = accessRepository.findByFunctionUnitId(functionUnitId);
        
        // 如果没有配置任何访问权限，默认所有人可访问
        if (configs.isEmpty()) {
            return true;
        }
        
        // 获取用户的业务角色ID列表
        List<String> userBusinessRoleIds = getUserBusinessRoleIds(userId);
        
        // 检查用户是否有任何被分配的角色
        for (FunctionUnitAccess config : configs) {
            if (userBusinessRoleIds.contains(config.getRoleId())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 获取用户可访问的所有功能单元ID
     */
    @Transactional(readOnly = true)
    public Set<String> getAccessibleFunctionUnitIds(String userId) {
        Set<String> accessibleIds = new HashSet<>();
        
        // 获取用户的业务角色ID列表
        List<String> userBusinessRoleIds = getUserBusinessRoleIds(userId);
        
        if (!userBusinessRoleIds.isEmpty()) {
            accessibleIds.addAll(accessRepository.findAccessibleFunctionUnitIdsByRoles(userBusinessRoleIds));
        }
        
        // 获取没有配置访问权限的功能单元（默认所有人可访问）
        accessibleIds.addAll(accessRepository.findFunctionUnitIdsWithoutAccess());
        
        return accessibleIds;
    }
    
    /**
     * 获取业务角色列表（用于功能单元访问配置）
     */
    @Transactional(readOnly = true)
    public List<Role> getBusinessRoles() {
        return roleRepository.findByType(RoleType.BUSINESS);
    }
    
    /**
     * 获取用户的业务角色ID列表
     */
    private List<String> getUserBusinessRoleIds(String userId) {
        List<String> allRoleIds = userRoleRepository.findRoleIdsByUserId(userId);
        
        return allRoleIds.stream()
            .filter(roleId -> {
                Role role = roleRepository.findById(roleId).orElse(null);
                return role != null && role.getType() == RoleType.BUSINESS;
            })
            .collect(Collectors.toList());
    }
}
