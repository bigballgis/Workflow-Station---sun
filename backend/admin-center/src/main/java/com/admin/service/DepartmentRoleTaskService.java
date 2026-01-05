package com.admin.service;

import com.admin.dto.request.DepartmentRoleTaskRequest;
import com.admin.dto.request.TaskClaimRequest;
import com.admin.dto.response.DepartmentRoleUserInfo;
import com.admin.dto.response.GroupTaskInfo;

import java.util.List;

/**
 * 部门角色任务服务接口
 * 负责部门+角色组合的任务分配、查询和认领
 */
public interface DepartmentRoleTaskService {
    
    /**
     * 获取部门角色组合的所有匹配用户
     * @param departmentId 部门ID
     * @param roleId 角色ID
     * @return 匹配的用户列表
     */
    List<DepartmentRoleUserInfo> getMatchingUsers(String departmentId, String roleId);
    
    /**
     * 检查用户是否匹配部门角色组合
     * @param userId 用户ID
     * @param departmentId 部门ID
     * @param roleId 角色ID
     * @return 是否匹配
     */
    boolean isUserMatchingDepartmentRole(String userId, String departmentId, String roleId);
    
    /**
     * 获取分配给部门角色的任务
     * @param departmentId 部门ID
     * @param roleId 角色ID
     * @param userId 当前用户ID（用于权限验证）
     * @return 任务列表
     */
    List<GroupTaskInfo> getDepartmentRoleTasks(String departmentId, String roleId, String userId);
    
    /**
     * 获取用户可见的所有部门角色任务
     * @param userId 用户ID
     * @return 任务列表
     */
    List<GroupTaskInfo> getUserVisibleDepartmentRoleTasks(String userId);
    
    /**
     * 认领部门角色任务
     * @param userId 用户ID
     * @param request 认领请求
     */
    void claimDepartmentRoleTask(String userId, DepartmentRoleTaskRequest request);
    
    /**
     * 检查用户是否可以认领部门角色任务
     * @param userId 用户ID
     * @param taskId 任务ID
     * @param departmentId 部门ID
     * @param roleId 角色ID
     * @return 是否可以认领
     */
    boolean canUserClaimDepartmentRoleTask(String userId, String taskId, String departmentId, String roleId);
}
