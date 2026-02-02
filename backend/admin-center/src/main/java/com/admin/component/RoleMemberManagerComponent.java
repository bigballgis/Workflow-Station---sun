package com.admin.component;

import com.admin.dto.request.BatchRoleMemberRequest;
import com.admin.dto.response.BatchRoleMemberResult;
import com.admin.entity.*;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.*;
import com.platform.security.entity.User;
import com.platform.security.entity.Role;
import com.platform.security.entity.UserRole;
import com.platform.common.audit.Audited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 角色成员管理组件
 * 负责角色成员的添加、移除、批量操作和变更历史记录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleMemberManagerComponent {
    
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionChangeHistoryRepository changeHistoryRepository;
    
    /**
     * 为用户分配角色
     */
    @Transactional
    @Audited(action = "ROLE_ASSIGN", resourceType = "USER_ROLE", resourceId = "#userId")
    public void assignRoleToUser(String userId, String roleId, String assignedBy, String reason) {
        log.info("Assigning role {} to user {} by {}", roleId, userId, assignedBy);
        
        // 验证角色存在
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        // 验证用户存在
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AdminBusinessException("USER_NOT_FOUND", "用户不存在: " + userId));
        
        // 检查是否已分配
        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            throw new AdminBusinessException("ROLE_ALREADY_ASSIGNED", "用户已拥有该角色");
        }
        
        // 创建用户角色关联
        UserRole userRole = UserRole.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .roleId(roleId)
                .assignedAt(LocalDateTime.now())
                .assignedBy(assignedBy)
                .build();
        
        userRoleRepository.save(userRole);
        
        // 记录变更历史
        recordChangeHistory("ROLE_ASSIGNED", userId, roleId, null, null, role.getName(), reason, assignedBy);
        
        log.info("Role {} assigned to user {} successfully", roleId, userId);
    }
    
    /**
     * 移除用户角色
     */
    @Transactional
    @Audited(action = "ROLE_REMOVE", resourceType = "USER_ROLE", resourceId = "#userId")
    public void removeRoleFromUser(String userId, String roleId, String removedBy, String reason) {
        log.info("Removing role {} from user {} by {}", roleId, userId, removedBy);
        
        UserRole userRole = userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .orElseThrow(() -> new AdminBusinessException("ROLE_NOT_ASSIGNED", "用户没有该角色"));
        
        // Fetch role to get name
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        String roleName = role.getName();
        
        userRoleRepository.delete(userRole);
        
        // 记录变更历史
        recordChangeHistory("ROLE_REMOVED", userId, roleId, null, roleName, null, reason, removedBy);
        
        log.info("Role {} removed from user {} successfully", roleId, userId);
    }

    
    /**
     * 批量添加角色成员
     */
    @Transactional
    public BatchRoleMemberResult batchAddMembers(BatchRoleMemberRequest request, String operatedBy) {
        log.info("Batch adding {} members to role {}", request.getUserIds().size(), request.getRoleId());
        
        // 验证角色存在
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RoleNotFoundException(request.getRoleId()));
        
        BatchRoleMemberResult result = BatchRoleMemberResult.builder()
                .total(request.getUserIds().size())
                .build();
        
        for (String userId : request.getUserIds()) {
            try {
                // 验证用户存在
                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    result.addFailure(userId, "USER_NOT_FOUND", "用户不存在");
                    continue;
                }
                
                // 检查是否已分配
                if (userRoleRepository.existsByUserIdAndRoleId(userId, request.getRoleId())) {
                    result.addFailure(userId, "ROLE_ALREADY_ASSIGNED", "用户已拥有该角色");
                    continue;
                }
                
                // 创建用户角色关联
                UserRole userRole = UserRole.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .roleId(request.getRoleId())
                        .assignedAt(LocalDateTime.now())
                        .assignedBy(operatedBy)
                        .build();
                
                userRoleRepository.save(userRole);
                
                // 记录变更历史
                recordChangeHistory("ROLE_ASSIGNED", userId, request.getRoleId(), null, 
                        null, role.getName(), request.getReason(), operatedBy);
                
                result.addSuccess(userId);
                
            } catch (Exception e) {
                log.error("Failed to add member {} to role {}: {}", userId, request.getRoleId(), e.getMessage());
                result.addFailure(userId, "INTERNAL_ERROR", e.getMessage());
            }
        }
        
        log.info("Batch add members completed: {} success, {} failed", 
                result.getSuccessCount(), result.getFailureCount());
        
        return result;
    }
    
    /**
     * 批量移除角色成员
     */
    @Transactional
    public BatchRoleMemberResult batchRemoveMembers(BatchRoleMemberRequest request, String operatedBy) {
        log.info("Batch removing {} members from role {}", request.getUserIds().size(), request.getRoleId());
        
        // 验证角色存在
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RoleNotFoundException(request.getRoleId()));
        
        BatchRoleMemberResult result = BatchRoleMemberResult.builder()
                .total(request.getUserIds().size())
                .build();
        
        for (String userId : request.getUserIds()) {
            try {
                // 查找用户角色关联
                UserRole userRole = userRoleRepository.findByUserIdAndRoleId(userId, request.getRoleId())
                        .orElse(null);
                
                if (userRole == null) {
                    result.addFailure(userId, "ROLE_NOT_ASSIGNED", "用户没有该角色");
                    continue;
                }
                
                userRoleRepository.delete(userRole);
                
                // 记录变更历史
                recordChangeHistory("ROLE_REMOVED", userId, request.getRoleId(), null, 
                        role.getName(), null, request.getReason(), operatedBy);
                
                result.addSuccess(userId);
                
            } catch (Exception e) {
                log.error("Failed to remove member {} from role {}: {}", userId, request.getRoleId(), e.getMessage());
                result.addFailure(userId, "INTERNAL_ERROR", e.getMessage());
            }
        }
        
        log.info("Batch remove members completed: {} success, {} failed", 
                result.getSuccessCount(), result.getFailureCount());
        
        return result;
    }
    
    /**
     * 获取角色成员列表
     */
    public List<UserRole> getRoleMembers(String roleId) {
        return userRoleRepository.findByRoleId(roleId);
    }
    
    /**
     * 分页获取角色成员
     */
    public Page<UserRole> getRoleMembersPaged(String roleId, Pageable pageable) {
        return userRoleRepository.findByRoleIdPaged(roleId, pageable);
    }
    
    /**
     * 获取用户的角色列表
     */
    public List<Role> getUserRoles(String userId) {
        return roleRepository.findByUserId(userId);
    }
    
    /**
     * 获取角色成员数量
     */
    public long getRoleMemberCount(String roleId) {
        return userRoleRepository.countByRoleId(roleId);
    }

    
    /**
     * 获取用户的权限变更历史
     */
    public List<PermissionChangeHistory> getUserChangeHistory(String userId) {
        return changeHistoryRepository.findByTargetUserIdOrderByChangedAtDesc(userId);
    }
    
    /**
     * 分页获取用户的权限变更历史
     */
    public Page<PermissionChangeHistory> getUserChangeHistoryPaged(String userId, Pageable pageable) {
        return changeHistoryRepository.findByTargetUserId(userId, pageable);
    }
    
    /**
     * 获取角色的变更历史
     */
    public List<PermissionChangeHistory> getRoleChangeHistory(String roleId) {
        return changeHistoryRepository.findByTargetRoleIdOrderByChangedAtDesc(roleId);
    }
    
    /**
     * 分页获取角色的变更历史
     */
    public Page<PermissionChangeHistory> getRoleChangeHistoryPaged(String roleId, Pageable pageable) {
        return changeHistoryRepository.findByTargetRoleId(roleId, pageable);
    }
    
    /**
     * 获取时间范围内的变更历史
     */
    public List<PermissionChangeHistory> getChangeHistoryByTimeRange(Instant startTime, Instant endTime) {
        return changeHistoryRepository.findByTimeRange(startTime, endTime);
    }
    
    /**
     * 记录权限变更历史
     */
    private void recordChangeHistory(String changeType, String userId, String roleId, 
                                     String permissionId, String oldValue, String newValue, 
                                     String reason, String changedBy) {
        PermissionChangeHistory history = PermissionChangeHistory.builder()
                .id(UUID.randomUUID().toString())
                .changeType(changeType)
                .targetUserId(userId)
                .targetRoleId(roleId)
                .targetPermissionId(permissionId)
                .oldValue(oldValue)
                .newValue(newValue)
                .reason(reason)
                .changedBy(changedBy)
                .changedAt(Instant.now())
                .build();
        
        changeHistoryRepository.save(history);
        
        log.debug("Permission change history recorded: type={}, userId={}, roleId={}", 
                changeType, userId, roleId);
    }
    
    /**
     * 检查用户是否拥有指定角色
     */
    public boolean hasRole(String userId, String roleId) {
        return userRoleRepository.existsByUserIdAndRoleId(userId, roleId);
    }
    
    /**
     * 替换用户的所有角色
     */
    @Transactional
    public void replaceUserRoles(String userId, List<String> newRoleIds, String operatedBy, String reason) {
        log.info("Replacing all roles for user {} with {} new roles", userId, newRoleIds.size());
        
        // 获取当前角色
        List<UserRole> currentRoles = userRoleRepository.findByUserId(userId);
        
        // 移除所有当前角色
        for (UserRole userRole : currentRoles) {
            // Fetch role to get name
            Role role = roleRepository.findById(userRole.getRoleId())
                    .orElse(null);
            String roleName = role != null ? role.getName() : "Unknown";
            recordChangeHistory("ROLE_REMOVED", userId, userRole.getRoleId(), null, 
                    roleName, null, reason, operatedBy);
        }
        userRoleRepository.deleteAll(currentRoles);
        
        // 添加新角色
        for (String roleId : newRoleIds) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new RoleNotFoundException(roleId));
            
            UserRole userRole = UserRole.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .roleId(roleId)
                    .assignedAt(LocalDateTime.now())
                    .assignedBy(operatedBy)
                    .build();
            
            userRoleRepository.save(userRole);
            
            recordChangeHistory("ROLE_ASSIGNED", userId, roleId, null, 
                    null, role.getName(), reason, operatedBy);
        }
        
        log.info("User {} roles replaced successfully", userId);
    }
}
