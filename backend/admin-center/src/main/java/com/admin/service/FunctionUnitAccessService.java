package com.admin.service;

import com.admin.dto.request.FunctionUnitAccessRequest;
import com.admin.dto.response.FunctionUnitAccessInfo;
import com.admin.entity.*;
import com.admin.repository.*;
import com.platform.security.entity.Role;
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
                .map(access -> {
                    FunctionUnitAccessInfo info = FunctionUnitAccessInfo.fromEntity(access);
                    
                    // 填充目标名称（角色名）和 code（供 user-portal 按 code 匹配）
                    if ("ROLE".equals(access.getTargetType())) {
                        roleRepository.findById(access.getTargetId())
                                .ifPresent(role -> {
                                    info.setTargetName(role.getName());
                                    info.setTargetCode(role.getCode());
                                });
                    }
                    
                    return info;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 添加访问配置（允许任意激活角色）
     */
    @Transactional
    public FunctionUnitAccessInfo addAccessConfig(String functionUnitId, FunctionUnitAccessRequest request) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new EntityNotFoundException("功能单元不存在: " + functionUnitId));
        
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new EntityNotFoundException("角色不存在: " + request.getRoleId()));
        
        // 检查是否已存在相同配置
        if (accessRepository.existsByFunctionUnitIdAndRoleId(functionUnitId, request.getRoleId())) {
            throw new IllegalArgumentException("This role has already been assigned access permission");
        }
        
        FunctionUnitAccess access = FunctionUnitAccess.builder()
                .functionUnit(functionUnit)
                .accessType("USER")  // 用户访问类型
                .targetType("ROLE")  // 目标类型为角色
                .targetId(request.getRoleId())  // 角色ID
                .build();
        
        access = accessRepository.save(access);
        log.info("Added access config for function unit {}: roleId={}", functionUnitId, request.getRoleId());
        
        FunctionUnitAccessInfo info = FunctionUnitAccessInfo.fromEntity(access);
        info.setTargetName(role.getName());
        info.setTargetCode(role.getCode());
        return info;
    }
    
    /**
     * 删除访问配置
     */
    @Transactional
    public void removeAccessConfig(String functionUnitId, String accessId) {
        FunctionUnitAccess access = accessRepository.findById(accessId)
                .orElseThrow(() -> new EntityNotFoundException("访问配置不存在: " + accessId));
        
        if (!access.getFunctionUnit().getId().equals(functionUnitId)) {
            throw new IllegalArgumentException("Access config does not belong to this function unit");
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
            // 验证角色存在（不限制角色类型）
            roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new EntityNotFoundException("角色不存在: " + request.getRoleId()));
            
            FunctionUnitAccess access = FunctionUnitAccess.builder()
                    .functionUnit(functionUnit)
                    .accessType("USER")  // 用户访问类型
                    .targetType("ROLE")  // 目标类型为角色
                    .targetId(request.getRoleId())  // 角色ID
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
     * Copy access configs from the highest-version sibling (same code) that has any access rows.
     * Used when DW publish creates a new catalog row so Admin Center Access is preserved across deploys.
     *
     * @return number of access rows copied
     */
    @Transactional
    public int copyAccessFromSiblingVersions(String code, String targetFunctionUnitId) {
        if (code == null || code.isBlank()) {
            return 0;
        }
        List<FunctionUnitAccess> sourceConfigs = findLatestSiblingAccessConfigs(code, targetFunctionUnitId);
        if (sourceConfigs.isEmpty()) {
            return 0;
        }

        FunctionUnit target = functionUnitRepository.findById(targetFunctionUnitId)
                .orElseThrow(() -> new EntityNotFoundException("功能单元不存在: " + targetFunctionUnitId));

        int copied = 0;
        for (FunctionUnitAccess source : sourceConfigs) {
            if ("ROLE".equals(source.getTargetType())
                    && accessRepository.existsByFunctionUnitIdAndRoleId(targetFunctionUnitId, source.getTargetId())) {
                continue;
            }
            accessRepository.save(FunctionUnitAccess.builder()
                    .functionUnit(target)
                    .accessType(source.getAccessType())
                    .targetType(source.getTargetType())
                    .targetId(source.getTargetId())
                    .build());
            copied++;
        }
        if (copied > 0) {
            log.info("Copied {} access config(s) for code {} onto catalog row {}", copied, code, targetFunctionUnitId);
        }
        return copied;
    }

    private List<FunctionUnitAccess> findLatestSiblingAccessConfigs(String code, String targetFunctionUnitId) {
        for (FunctionUnit sibling : functionUnitRepository.findByCodeOrderByVersionDesc(code)) {
            if (targetFunctionUnitId.equals(sibling.getId())) {
                continue;
            }
            List<FunctionUnitAccess> configs = accessRepository.findByFunctionUnitId(sibling.getId());
            if (!configs.isEmpty()) {
                return configs;
            }
        }
        return List.of();
    }

    /**
     * 检查用户是否有权限访问功能单元
     */
    @Transactional(readOnly = true)
    public boolean hasAccess(String functionUnitId, String userId) {
        List<FunctionUnitAccess> configs = accessRepository.findByFunctionUnitId(functionUnitId);
        
        // 未配置访问权限 → 拒绝（须在 Admin Center 显式分配角色，与 Relation Table 一致）
        if (configs.isEmpty()) {
            return false;
        }
        
        // 获取用户的所有角色ID列表（包含所有类型）
        List<String> userRoleIds = getUserAllRoleIds(userId);
        
        // 检查用户是否有任何被分配的角色
        for (FunctionUnitAccess config : configs) {
            if ("ROLE".equals(config.getTargetType()) && userRoleIds.contains(config.getTargetId())) {
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
        
        // 获取用户的所有角色ID列表（包含所有类型）
        List<String> userRoleIds = getUserAllRoleIds(userId);
        
        if (!userRoleIds.isEmpty()) {
            accessibleIds.addAll(accessRepository.findAccessibleFunctionUnitIdsByRoles(userRoleIds));
        }

        return accessibleIds;
    }
    
    /**
     * 获取业务角色列表（用于功能单元访问配置）
     * 包括 BU_BOUNDED 和 BU_UNBOUNDED 类型
     */
    @Transactional(readOnly = true)
    public List<Role> getBusinessRoles() {
        List<Role> buBounded = roleRepository.findByType("BU_BOUNDED");
        List<Role> buUnbounded = roleRepository.findByType("BU_UNBOUNDED");
        List<Role> result = new ArrayList<>(buBounded);
        result.addAll(buUnbounded);
        return result;
    }
    
    /**
     * 获取用户的所有角色ID列表（包含所有类型）
     * 包括通过虚拟组分配的角色
     */
    private List<String> getUserAllRoleIds(String userId) {
        return userRoleRepository.findAllRoleIdsByUserId(userId);
    }
}
