package com.admin.service;

import com.admin.dto.request.DepartmentRoleTaskRequest;
import com.admin.dto.request.TaskClaimRequest;
import com.admin.dto.response.DepartmentRoleUserInfo;
import com.admin.dto.response.GroupTaskInfo;

import java.util.List;

/**
 * 业务单元角色任务服务接口
 * 负责业务单元+角色组合的任务分配、查询和认领
 */
public interface DepartmentRoleTaskService {
    
    /**
     * 获取业务单元角色组合的所有匹配用户
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID
     * @return 匹配的用户列表
     */
    List<DepartmentRoleUserInfo> getMatchingUsers(String businessUnitId, String roleId);
    
    /**
     * 检查用户是否匹配业务单元角色组合
     * @param userId 用户ID
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID
     * @return 是否匹配
     */
    boolean isUserMatchingBusinessUnitRole(String userId, String businessUnitId, String roleId);
    
    /**
     * 获取分配给业务单元角色的任务
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID
     * @param userId 当前用户ID（用于权限验证）
     * @return 任务列表
     */
    List<GroupTaskInfo> getBusinessUnitRoleTasks(String businessUnitId, String roleId, String userId);
    
    /**
     * 获取用户可见的所有业务单元角色任务
     * @param userId 用户ID
     * @return 任务列表
     */
    List<GroupTaskInfo> getUserVisibleBusinessUnitRoleTasks(String userId);
    
    /**
     * 认领业务单元角色任务
     * @param userId 用户ID
     * @param request 认领请求
     */
    void claimBusinessUnitRoleTask(String userId, DepartmentRoleTaskRequest request);
    
    /**
     * 检查用户是否可以认领业务单元角色任务
     * @param userId 用户ID
     * @param taskId 任务ID
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID
     * @return 是否可以认领
     */
    boolean canUserClaimBusinessUnitRoleTask(String userId, String taskId, String businessUnitId, String roleId);
}
