package com.admin.service.impl;

import com.admin.dto.request.DepartmentRoleTaskRequest;
import com.admin.dto.response.DepartmentRoleUserInfo;
import com.admin.dto.response.GroupTaskInfo;
import com.admin.entity.*;
import com.admin.enums.TaskActionType;
import com.admin.enums.UserStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.DepartmentNotFoundException;
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
 * 部门角色任务服务实现
 * 实现"部门+角色"组合任务分配的动态用户匹配和权限验证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentRoleTaskServiceImpl implements DepartmentRoleTaskService {
    
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final VirtualGroupTaskHistoryRepository taskHistoryRepository;
    
    @Override
    public List<DepartmentRoleUserInfo> getMatchingUsers(String departmentId, String roleId) {
        log.info("Getting matching users for department {} and role {}", departmentId, roleId);
        
        // 验证部门存在
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
        
        // 验证角色存在
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        
        // 获取该部门的所有用户
        List<User> departmentUsers = userRepository.findByDepartmentId(departmentId);
        
        // 过滤出拥有该角色的活跃用户
        List<DepartmentRoleUserInfo> matchingUsers = new ArrayList<>();
        for (User user : departmentUsers) {
            if (user.getStatus() != UserStatus.ACTIVE) {
                continue;
            }
            
            // 检查用户是否拥有该角色
            boolean hasRole = userRoleRepository.existsByUserIdAndRoleId(user.getId(), roleId);
            if (hasRole) {
                // 检查角色分配是否有效
                userRoleRepository.findByUserIdAndRoleId(user.getId(), roleId)
                        .filter(UserRole::isValid)
                        .ifPresent(ur -> matchingUsers.add(buildDepartmentRoleUserInfo(user, department, role)));
            }
        }
        
        log.info("Found {} matching users for department {} and role {}", 
                matchingUsers.size(), departmentId, roleId);
        return matchingUsers;
    }

    
    @Override
    public boolean isUserMatchingDepartmentRole(String userId, String departmentId, String roleId) {
        log.debug("Checking if user {} matches department {} and role {}", userId, departmentId, roleId);
        
        // 获取用户
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return false;
        }
        
        // 检查用户是否属于该部门
        if (!departmentId.equals(user.getDepartmentId())) {
            return false;
        }
        
        // 检查用户是否拥有该角色且角色分配有效
        return userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .map(UserRole::isValid)
                .orElse(false);
    }
    
    @Override
    public List<GroupTaskInfo> getDepartmentRoleTasks(String departmentId, String roleId, String userId) {
        log.info("Getting tasks for department {} and role {} by user {}", departmentId, roleId, userId);
        
        // 验证部门存在
        if (!departmentRepository.existsById(departmentId)) {
            throw new DepartmentNotFoundException(departmentId);
        }
        
        // 验证角色存在
        if (!roleRepository.existsById(roleId)) {
            throw new RoleNotFoundException(roleId);
        }
        
        // 验证用户是否匹配部门角色
        if (!isUserMatchingDepartmentRole(userId, departmentId, roleId)) {
            throw new AdminBusinessException("NOT_MATCHING_DEPT_ROLE", 
                    "用户不匹配该部门角色组合");
        }
        
        // 这里应该调用工作流引擎获取分配给该部门角色的任务
        // 由于工作流引擎是独立模块，这里返回模拟数据结构
        return getTasksAssignedToDepartmentRole(departmentId, roleId);
    }
    
    @Override
    public List<GroupTaskInfo> getUserVisibleDepartmentRoleTasks(String userId) {
        log.info("Getting all visible department role tasks for user {}", userId);
        
        // 获取用户
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return new ArrayList<>();
        }
        
        String departmentId = user.getDepartmentId();
        if (departmentId == null) {
            return new ArrayList<>();
        }
        
        // 获取用户的所有有效角色
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        List<String> validRoleIds = userRoles.stream()
                .filter(UserRole::isValid)
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toList());
        
        // 获取每个角色对应的部门角色任务
        List<GroupTaskInfo> allTasks = new ArrayList<>();
        for (String roleId : validRoleIds) {
            allTasks.addAll(getTasksAssignedToDepartmentRole(departmentId, roleId));
        }
        
        return allTasks;
    }
    
    @Override
    @Transactional
    public void claimDepartmentRoleTask(String userId, DepartmentRoleTaskRequest request) {
        log.info("User {} claiming department role task {} for dept {} role {}", 
                userId, request.getTaskId(), request.getDepartmentId(), request.getRoleId());
        
        // 验证用户可以认领该任务
        if (!canUserClaimDepartmentRoleTask(userId, request.getTaskId(), 
                request.getDepartmentId(), request.getRoleId())) {
            throw new AdminBusinessException("CANNOT_CLAIM", "用户无法认领该部门角色任务");
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
        
        log.info("Department role task {} claimed by user {}", request.getTaskId(), userId);
    }
    
    @Override
    public boolean canUserClaimDepartmentRoleTask(String userId, String taskId, 
            String departmentId, String roleId) {
        // 验证用户匹配部门角色
        if (!isUserMatchingDepartmentRole(userId, departmentId, roleId)) {
            return false;
        }
        
        // 验证任务是分配给该部门角色的且未被认领
        return isTaskAssignedToDepartmentRoleAndUnclaimed(taskId, departmentId, roleId);
    }
    
    // ==================== 辅助方法 ====================
    
    private DepartmentRoleUserInfo buildDepartmentRoleUserInfo(User user, Department department, Role role) {
        return DepartmentRoleUserInfo.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .departmentId(department.getId())
                .departmentName(department.getName())
                .roleId(role.getId())
                .roleName(role.getName())
                .roleCode(role.getCode())
                .active(user.isActive())
                .build();
    }
    
    // ==================== 工作流引擎集成方法（需要实际实现） ====================
    
    /**
     * 获取分配给部门角色的任务
     * 实际实现时需要与工作流引擎集成
     */
    private List<GroupTaskInfo> getTasksAssignedToDepartmentRole(String departmentId, String roleId) {
        // TODO: 与工作流引擎集成，获取分配给该部门角色的任务
        log.debug("Getting tasks assigned to department {} role {}", departmentId, roleId);
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
     * 检查任务是否分配给部门角色且未被认领
     * 实际实现时需要与工作流引擎集成
     */
    private boolean isTaskAssignedToDepartmentRoleAndUnclaimed(String taskId, 
            String departmentId, String roleId) {
        // TODO: 与工作流引擎集成
        // 检查是否有认领记录
        List<VirtualGroupTaskHistory> claimHistory = taskHistoryRepository.findClaimHistoryByTaskId(taskId);
        return claimHistory.isEmpty();
    }
}
