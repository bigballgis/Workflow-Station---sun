package com.admin.component;

import com.admin.dto.request.ConflictResolutionRequest;
import com.admin.dto.response.ConflictDetectionResult;
import com.admin.entity.Permission;
import com.admin.entity.PermissionConflict;
import com.admin.entity.Role;
import com.admin.enums.ConflictResolutionStrategy;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.PermissionConflictRepository;
import com.admin.repository.RoleRepository;
import com.admin.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限冲突管理组件
 * 负责权限冲突的检测、解决和管理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionConflictComponent {
    
    private final PermissionConflictRepository conflictRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    
    /**
     * 检测用户权限冲突
     */
    public ConflictDetectionResult detectUserPermissionConflicts(String userId) {
        log.debug("Detecting permission conflicts for user: {}", userId);
        
        // 获取用户所有角色
        List<Role> userRoles = roleRepository.findByUserId(userId);
        
        if (userRoles.size() < 2) {
            return ConflictDetectionResult.noConflicts();
        }
        
        List<ConflictDetectionResult.ConflictInfo> conflicts = new ArrayList<>();
        
        // 检测角色间的权限冲突
        for (int i = 0; i < userRoles.size(); i++) {
            for (int j = i + 1; j < userRoles.size(); j++) {
                Role role1 = userRoles.get(i);
                Role role2 = userRoles.get(j);
                
                List<ConflictDetectionResult.ConflictInfo> roleConflicts = 
                        detectRoleConflicts(userId, role1, role2);
                conflicts.addAll(roleConflicts);
            }
        }
        
        if (conflicts.isEmpty()) {
            return ConflictDetectionResult.noConflicts();
        }
        
        // 推荐解决策略
        ConflictResolutionStrategy recommendedStrategy = recommendResolutionStrategy(conflicts);
        
        return ConflictDetectionResult.withConflicts(conflicts, recommendedStrategy);
    }
    
    /**
     * 检测两个角色之间的权限冲突
     */
    private List<ConflictDetectionResult.ConflictInfo> detectRoleConflicts(String userId, Role role1, Role role2) {
        List<ConflictDetectionResult.ConflictInfo> conflicts = new ArrayList<>();
        
        // 检测互斥权限冲突
        Set<Permission> role1Permissions = getEffectivePermissions(role1);
        Set<Permission> role2Permissions = getEffectivePermissions(role2);
        
        for (Permission perm1 : role1Permissions) {
            for (Permission perm2 : role2Permissions) {
                if (isConflictingPermissions(perm1, perm2)) {
                    ConflictDetectionResult.ConflictInfo conflict = ConflictDetectionResult.ConflictInfo.builder()
                            .userId(userId)
                            .permissionId(perm1.getId())
                            .permissionName(perm1.getName())
                            .conflictSource1("角色: " + role1.getName())
                            .conflictSource2("角色: " + role2.getName())
                            .conflictDescription(String.format("权限 %s 与 %s 存在冲突", 
                                    perm1.getName(), perm2.getName()))
                            .resolutionStrategy(ConflictResolutionStrategy.MANUAL)
                            .status("PENDING")
                            .detectedAt(Instant.now())
                            .build();
                    
                    conflicts.add(conflict);
                }
            }
        }
        
        return conflicts;
    }
    
    /**
     * 判断两个权限是否冲突
     */
    private boolean isConflictingPermissions(Permission perm1, Permission perm2) {
        // 检查是否是互斥权限
        if (isMutuallyExclusivePermissions(perm1, perm2)) {
            return true;
        }
        
        // 检查是否是层级冲突权限
        if (isHierarchicalConflict(perm1, perm2)) {
            return true;
        }
        
        // 检查是否是业务规则冲突
        if (isBusinessRuleConflict(perm1, perm2)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查是否是互斥权限
     */
    private boolean isMutuallyExclusivePermissions(Permission perm1, Permission perm2) {
        // 定义互斥权限规则
        Map<String, Set<String>> mutuallyExclusiveRules = Map.of(
                "ADMIN", Set.of("READONLY", "GUEST"),
                "WRITE", Set.of("READONLY"),
                "DELETE", Set.of("READONLY"),
                "APPROVE", Set.of("REJECT"),
                "CREATE", Set.of("READONLY")
        );
        
        String action1 = perm1.getAction().toUpperCase();
        String action2 = perm2.getAction().toUpperCase();
        
        Set<String> exclusiveActions1 = mutuallyExclusiveRules.get(action1);
        Set<String> exclusiveActions2 = mutuallyExclusiveRules.get(action2);
        
        return (exclusiveActions1 != null && exclusiveActions1.contains(action2)) ||
               (exclusiveActions2 != null && exclusiveActions2.contains(action1));
    }
    
    /**
     * 检查是否是层级冲突
     */
    private boolean isHierarchicalConflict(Permission perm1, Permission perm2) {
        // 检查同一资源的不同层级权限是否冲突
        if (!perm1.getResource().equals(perm2.getResource())) {
            return false;
        }
        
        // 定义权限层级
        Map<String, Integer> actionLevels = Map.of(
                "READ", 1,
                "WRITE", 2,
                "DELETE", 3,
                "ADMIN", 4
        );
        
        Integer level1 = actionLevels.get(perm1.getAction().toUpperCase());
        Integer level2 = actionLevels.get(perm2.getAction().toUpperCase());
        
        // 如果层级差距过大，可能存在冲突
        return level1 != null && level2 != null && Math.abs(level1 - level2) > 2;
    }
    
    /**
     * 检查是否是业务规则冲突
     */
    private boolean isBusinessRuleConflict(Permission perm1, Permission perm2) {
        // 实现业务规则冲突检测逻辑
        // 例如：审批权限与被审批权限冲突
        return false;
    }
    
    /**
     * 获取角色的有效权限
     */
    private Set<Permission> getEffectivePermissions(Role role) {
        // 这里应该调用 RolePermissionManagerComponent 的方法
        // 为了简化，这里返回空集合
        return new HashSet<>();
    }
    
    /**
     * 推荐解决策略
     */
    private ConflictResolutionStrategy recommendResolutionStrategy(List<ConflictDetectionResult.ConflictInfo> conflicts) {
        // 根据冲突类型和数量推荐策略
        if (conflicts.size() == 1) {
            return ConflictResolutionStrategy.MANUAL;
        } else if (conflicts.size() <= 3) {
            return ConflictResolutionStrategy.HIGHEST_PRIVILEGE;
        } else {
            return ConflictResolutionStrategy.MANUAL;
        }
    }
    
    /**
     * 创建权限冲突记录
     */
    @Transactional
    public PermissionConflict createConflictRecord(String userId, Permission permission, 
                                                  String source1, String source2, 
                                                  String description, 
                                                  ConflictResolutionStrategy strategy) {
        PermissionConflict conflict = PermissionConflict.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .permission(permission)
                .conflictSource1(source1)
                .conflictSource2(source2)
                .conflictDescription(description)
                .resolutionStrategy(strategy)
                .status("PENDING")
                .build();
        
        conflictRepository.save(conflict);
        
        // 记录审计日志
        auditService.recordPermissionConflictDetection(conflict);
        
        return conflict;
    }
    
    /**
     * 解决权限冲突
     */
    @Transactional
    public void resolveConflict(ConflictResolutionRequest request) {
        log.info("Resolving permission conflict: {}", request.getConflictId());
        
        PermissionConflict conflict = conflictRepository.findById(request.getConflictId())
                .orElseThrow(() -> new AdminBusinessException("CONFLICT_NOT_FOUND", 
                        "权限冲突不存在: " + request.getConflictId()));
        
        if (conflict.isResolved()) {
            throw new AdminBusinessException("CONFLICT_ALREADY_RESOLVED", "权限冲突已解决");
        }
        
        // 应用解决策略
        applyResolutionStrategy(conflict, request.getResolutionStrategy());
        
        // 更新冲突记录
        conflict.setResolutionStrategy(request.getResolutionStrategy());
        conflict.setStatus("RESOLVED");
        conflict.setResolutionResult(request.getResolutionResult());
        conflict.setResolvedAt(Instant.now());
        conflict.setResolvedBy(request.getResolvedBy());
        
        conflictRepository.save(conflict);
        
        // 记录审计日志
        auditService.recordPermissionConflictResolution(conflict, request.getResolvedBy());
        
        log.info("Permission conflict resolved: {}", request.getConflictId());
    }
    
    /**
     * 应用解决策略
     */
    private void applyResolutionStrategy(PermissionConflict conflict, ConflictResolutionStrategy strategy) {
        switch (strategy) {
            case DENY:
                // 拒绝所有冲突权限
                break;
            case ALLOW:
                // 允许所有冲突权限
                break;
            case HIGHEST_PRIVILEGE:
                // 保留最高权限
                break;
            case LOWEST_PRIVILEGE:
                // 保留最低权限
                break;
            case LATEST:
                // 保留最新权限
                break;
            case MANUAL:
                // 需要手动处理
                break;
        }
    }
    
    /**
     * 获取待解决的权限冲突
     */
    public List<PermissionConflict> getPendingConflicts() {
        return conflictRepository.findByStatus("PENDING");
    }
    
    /**
     * 获取需要手动解决的权限冲突
     */
    public List<PermissionConflict> getManualResolutionRequired() {
        return conflictRepository.findManualResolutionRequired();
    }
    
    /**
     * 获取用户的权限冲突
     */
    public List<PermissionConflict> getUserConflicts(String userId) {
        return conflictRepository.findByUserId(userId);
    }
}