package com.admin.service.impl;

import com.admin.dto.request.DepartmentRoleTaskRequest;
import com.admin.dto.response.DepartmentRoleUserInfo;
import com.admin.dto.response.GroupTaskInfo;
import com.admin.entity.*;
import com.admin.enums.TaskActionType;
import com.admin.enums.UserStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.BusinessUnitNotFoundException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.*;
import com.admin.service.DepartmentRoleTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 业务单元角色任务服务实现
 * 实现"业务单元+角色"组合任务分配的动态用户匹配和权限验证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentRoleTaskServiceImpl implements DepartmentRoleTaskService {
    
    private final BusinessUnitRepository businessUnitRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final VirtualGroupTaskHistoryRepository taskHistoryRepository;
    
    @Override
    public List<DepartmentRoleUserInfo> getMatchingUsers(String businessUnitId, String roleId) {
        log.info("Getting matching users for business unit {} and role {}", businessUnitId, roleId);
        
        // 验证业务单元存在
        BusinessUnit businessUnit = businessUnitRepository.findById(businessUnitId)
                .orElseThrow(() -> new BusinessUnitNotFoundException(businessUnitId));
        
        // 验证角色存在
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        // 获取该业务单元的所有用户
        List<User> businessUnitUsers = userRepository.findByBusinessUnitId(businessUnitId);
        
        // 过滤出拥有该角色的活跃用户
        List<DepartmentRoleUserInfo> matchingUsers = new ArrayList<>();
        for (User user : businessUnitUsers) {
            if (user.getStatus() != UserStatus.ACTIVE) {
                continue;
            }
            
            // 检查用户是否拥有该角色
            boolean hasRole = userRoleRepository.existsByUserIdAndRoleId(user.getId(), roleId);
            if (hasRole) {
                // 检查角色分配是否有效
                userRoleRepository.findByUserIdAndRoleId(user.getId(), roleId)
                        .filter(UserRole::isValid)
                        .ifPresent(ur -> matchingUsers.add(buildBusinessUnitRoleUserInfo(user, businessUnit, role)));
            }
        }
        
        log.info("Found {} matching users for business unit {} and role {}", 
                matchingUsers.size(), businessUnitId, roleId);
        return matchingUsers;
    }

    
    @Override
    public boolean isUserMatchingBusinessUnitRole(String userId, String businessUnitId, String roleId) {
        log.debug("Checking if user {} matches business unit {} and role {}", userId, businessUnitId, roleId);
        
        // 获取用户
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return false;
        }
        
        // 检查用户是否属于该业务单元
        if (!businessUnitId.equals(user.getBusinessUnitId())) {
            return false;
        }
        
        // 检查用户是否拥有该角色且角色分配有效
        return userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .map(UserRole::isValid)
                .orElse(false);
    }
    
    @Override
    public List<GroupTaskInfo> getBusinessUnitRoleTasks(String businessUnitId, String roleId, String userId) {
        log.info("Getting tasks for business unit {} and role {} by user {}", businessUnitId, roleId, userId);
        
        // 验证业务单元存在
        if (!businessUnitRepository.existsById(businessUnitId)) {
            throw new BusinessUnitNotFoundException(businessUnitId);
        }
        
        // 验证角色存在
        if (!roleRepository.existsById(roleId)) {
            throw new RoleNotFoundException(roleId);
        }
        
        // 验证用户是否匹配业务单元角色
        if (!isUserMatchingBusinessUnitRole(userId, businessUnitId, roleId)) {
            throw new AdminBusinessException("NOT_MATCHING_BU_ROLE", 
                    "用户不匹配该业务单元角色组合");
        }
        
        // 这里应该调用工作流引擎获取分配给该业务单元角色的任务
        // 由于工作流引擎是独立模块，这里返回模拟数据结构
        return getTasksAssignedToBusinessUnitRole(businessUnitId, roleId);
    }
    
    @Override
    public List<GroupTaskInfo> getUserVisibleBusinessUnitRoleTasks(String userId) {
        log.info("Getting all visible business unit role tasks for user {}", userId);
        
        // 获取用户
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return new ArrayList<>();
        }
        
        String businessUnitId = user.getBusinessUnitId();
        if (businessUnitId == null) {
            return new ArrayList<>();
        }
        
        // 获取用户的所有有效角色
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        List<String> validRoleIds = userRoles.stream()
                .filter(UserRole::isValid)
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toList());
        
        // 获取每个角色对应的业务单元角色任务
        List<GroupTaskInfo> allTasks = new ArrayList<>();
        for (String roleId : validRoleIds) {
            allTasks.addAll(getTasksAssignedToBusinessUnitRole(businessUnitId, roleId));
        }
        
        return allTasks;
    }
    
    @Override
    @Transactional
    public void claimBusinessUnitRoleTask(String userId, DepartmentRoleTaskRequest request) {
        log.info("User {} claiming business unit role task {} for bu {} role {}", 
                userId, request.getTaskId(), request.getBusinessUnitId(), request.getRoleId());
        
        // 验证用户可以认领该任务
        if (!canUserClaimBusinessUnitRoleTask(userId, request.getTaskId(), 
                request.getBusinessUnitId(), request.getRoleId())) {
            throw new AdminBusinessException("CANNOT_CLAIM", "用户无法认领该业务单元角色任务");
        }
        
        // 记录认领历史
        VirtualGroupTaskHistory history = VirtualGroupTaskHistory.builder()
                .id(UUID.randomUUID().toString())
                .taskId(request.getTaskId())
                .actionType(TaskActionType.CLAIMED)
                .toUserId(userId)
                .comment(request.getComment())
                .createdAt(Instant.now())
                .build();
        
        taskHistoryRepository.save(history);
        
        // 这里应该调用工作流引擎将任务分配给用户
        claimTaskInWorkflowEngine(request.getTaskId(), userId);
        
        log.info("Business unit role task {} claimed by user {}", request.getTaskId(), userId);
    }
    
    @Override
    public boolean canUserClaimBusinessUnitRoleTask(String userId, String taskId, 
            String businessUnitId, String roleId) {
        // 验证用户匹配业务单元角色
        if (!isUserMatchingBusinessUnitRole(userId, businessUnitId, roleId)) {
            return false;
        }
        
        // 验证任务是分配给该业务单元角色的且未被认领
        return isTaskAssignedToBusinessUnitRoleAndUnclaimed(taskId, businessUnitId, roleId);
    }
    
    // ==================== 辅助方法 ====================
    
    private DepartmentRoleUserInfo buildBusinessUnitRoleUserInfo(User user, BusinessUnit businessUnit, Role role) {
        return DepartmentRoleUserInfo.builder()
                .userId(user.getId().toString())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .businessUnitId(businessUnit.getId())
                .businessUnitName(businessUnit.getName())
                .roleId(role.getId())
                .roleName(role.getName())
                .roleCode(role.getCode())
                .active(user.isActive())
                .build();
    }
    
    // ==================== 工作流引擎集成方法（需要实际实现） ====================
    
    /**
     * 获取分配给业务单元角色的任务
     * 实际实现时需要与工作流引擎集成
     */
    private List<GroupTaskInfo> getTasksAssignedToBusinessUnitRole(String businessUnitId, String roleId) {
        // TODO: 与工作流引擎集成，获取分配给该业务单元角色的任务
        log.debug("Getting tasks assigned to business unit {} role {}", businessUnitId, roleId);
        return new ArrayList<>();
    }
    
    /**
     * 在工作流引擎中认领任务
     * 实际实现时需要与工作流引擎集成
     */
    private void claimTaskInWorkflowEngine(String taskId, String userId) {
        // TODO: 与工作流引擎集成，将任务分配给用户
        log.info("Claiming task {} for user {} in workflow engine", taskId, userId);
    }
    
    /**
     * 检查任务是否分配给业务单元角色且未被认领
     * 实际实现时需要与工作流引擎集成
     */
    private boolean isTaskAssignedToBusinessUnitRoleAndUnclaimed(String taskId, 
            String businessUnitId, String roleId) {
        // TODO: 与工作流引擎集成
        // 检查是否有认领记录
        List<VirtualGroupTaskHistory> claimHistory = taskHistoryRepository.findClaimHistoryByTaskId(taskId);
        return claimHistory.isEmpty();
    }
}
