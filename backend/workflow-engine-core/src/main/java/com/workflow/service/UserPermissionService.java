package com.workflow.service;

import com.workflow.client.AdminCenterClient;
import com.workflow.enums.AssignmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户权限服务
 * 封装用户权限验证逻辑，用于任务分配和操作权限检查
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
     * @param assignmentTarget 分配目标（用户ID/虚拟组ID/部门角色）
     * @return 是否有权限
     */
    public boolean hasTaskPermission(String userId, AssignmentType assignmentType, String assignmentTarget) {
        if (userId == null || assignmentTarget == null) {
            return false;
        }
        
        switch (assignmentType) {
            case USER:
                // 直接分配给用户的任务，只有该用户有权限
                return userId.equals(assignmentTarget);
                
            case VIRTUAL_GROUP:
                // 分配给虚拟组的任务，虚拟组成员有权限
                return isUserInVirtualGroup(userId, assignmentTarget);
                
            case DEPT_ROLE:
                // 分配给部门角色的任务，拥有该部门角色的用户有权限
                return hasUserDepartmentRole(userId, assignmentTarget);
                
            default:
                log.warn("Unknown assignment type: {}", assignmentType);
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
     * 检查用户是否拥有指定的部门角色
     * 部门角色格式：departmentId:roleCode
     * @param userId 用户ID
     * @param deptRole 部门角色（格式：departmentId:roleCode）
     * @return 是否拥有该部门角色
     */
    public boolean hasUserDepartmentRole(String userId, String deptRole) {
        try {
            if (deptRole == null || !deptRole.contains(":")) {
                log.warn("Invalid department role format: {}", deptRole);
                return false;
            }
            
            String[] parts = deptRole.split(":", 2);
            String departmentId = parts[0];
            String roleCode = parts[1];
            
            return adminCenterClient.hasUserDepartmentRole(userId, departmentId, roleCode);
        } catch (Exception e) {
            log.error("Failed to check department role: {}", e.getMessage());
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
     * 获取用户的部门角色列表
     * @param userId 用户ID
     * @return 部门角色列表（格式：departmentId:roleCode）
     */
    public List<String> getUserDepartmentRoles(String userId) {
        return adminCenterClient.getUserDepartmentRoles(userId);
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
     * 检查用户是否在部门层级中
     * @param userId 用户ID
     * @param departmentId 部门ID
     * @return 是否在部门层级中
     */
    public boolean isUserInDepartmentHierarchy(String userId, String departmentId) {
        return adminCenterClient.isUserInDepartmentHierarchy(userId, departmentId);
    }
}
