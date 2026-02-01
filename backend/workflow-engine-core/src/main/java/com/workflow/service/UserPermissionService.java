package com.workflow.service;

import com.workflow.client.AdminCenterClient;
import com.workflow.enums.AssignmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 用户权限服务
 * 封装用户权限验证逻辑，用于任务分配和操作权限检查
 * 
 * 注意：此服务使用旧的 AssignmentType 枚举（USER, VIRTUAL_GROUP）
 * 新的任务分配机制使用 AssigneeType 枚举和 TaskAssigneeResolver 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionService {
    
    private final AdminCenterClient adminCenterClient;
    
    /**
     * 验证用户是否有权限操作指定分配类型的任务
     * @param userId 用户ID
     * @param assignmentType 分配类型
     * @param assignmentTarget 分配目标（用户ID/虚拟组ID/候选用户列表）
     * @return 是否有权限
     */
    public boolean hasTaskPermission(String userId, AssignmentType assignmentType, String assignmentTarget) {
        if (userId == null) {
            return false;
        }
        
        // 对于 CANDIDATE_USER 类型，assignmentTarget 可能为 null（如果没有候选用户）
        if (assignmentTarget == null && assignmentType != AssignmentType.CANDIDATE_USER) {
            return false;
        }
        
        switch (assignmentType) {
            case USER:
                // 直接分配给用户的任务，只有该用户有权限
                return userId.equals(assignmentTarget);
                
            case CANDIDATE_USER:
                // 分配给候选用户的任务，检查用户是否在候选用户列表中
                if (assignmentTarget == null || assignmentTarget.isEmpty()) {
                    return false;
                }
                // assignmentTarget 是逗号分隔的用户ID列表
                String[] candidateUsers = assignmentTarget.split(",");
                for (String candidateUserId : candidateUsers) {
                    if (userId.equals(candidateUserId.trim())) {
                        return true;
                    }
                }
                return false;
                
            case VIRTUAL_GROUP:
                // 分配给虚拟组的任务，虚拟组成员有权限
                return isUserInVirtualGroup(userId, assignmentTarget);
                
            default:
                log.warn("Unknown or unsupported assignment type: {}", assignmentType);
                return false;
        }
    }
    
    /**
     * 检查用户是否是虚拟组成员
     * @param userId 用户ID
     * @param groupId 虚拟组ID
     * @return 是否是成员
     */
    public boolean isUserInVirtualGroup(String userId, String groupId) {
        try {
            return adminCenterClient.isUserInVirtualGroup(userId, groupId);
        } catch (Exception e) {
            log.error("Failed to check virtual group membership: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取用户所属的所有虚拟组ID
     * @param userId 用户ID
     * @return 虚拟组ID列表
     */
    public List<String> getUserVirtualGroupIds(String userId) {
        return adminCenterClient.getUserVirtualGroupIds(userId);
    }
    
    /**
     * 获取用户的角色列表
     * @param userId 用户ID
     * @return 角色编码列表
     */
    public List<String> getUserRoles(String userId) {
        return adminCenterClient.getUserRoles(userId);
    }
    
    /**
     * 获取用户的角色ID列表（用于任务候选组查询）
     * @param userId 用户ID
     * @return 角色ID列表
     */
    public List<String> getUserRoleIds(String userId) {
        return adminCenterClient.getUserRoleIds(userId);
    }
}
