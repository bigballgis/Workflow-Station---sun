package com.admin.service;

import com.admin.dto.request.TaskClaimRequest;
import com.admin.dto.request.TaskDelegationRequest;
import com.admin.dto.response.GroupTaskInfo;
import com.admin.dto.response.TaskHistoryInfo;

import java.util.List;

/**
 * 虚拟组任务服务接口
 * 负责虚拟组任务的可见性、认领、委托等功能
 */
public interface VirtualGroupTaskService {
    
    /**
     * 获取虚拟组的待办任务列表
     * 所有组成员都能看到分配给该组的任务
     */
    List<GroupTaskInfo> getGroupTasks(String groupId, String userId);
    
    /**
     * 获取用户在所有虚拟组中可见的任务
     */
    List<GroupTaskInfo> getUserVisibleGroupTasks(String userId);
    
    /**
     * 认领虚拟组任务
     * 认领后任务变为该成员的直接任务
     */
    void claimTask(String userId, TaskClaimRequest request);
    
    /**
     * 委托任务给其他用户
     * 可以委托给组外用户
     */
    void delegateTask(String userId, TaskDelegationRequest request);
    
    /**
     * 获取任务处理历史
     */
    List<TaskHistoryInfo> getTaskHistory(String taskId);
    
    /**
     * 获取虚拟组的任务处理历史
     */
    List<TaskHistoryInfo> getGroupTaskHistory(String groupId);
    
    /**
     * 检查用户是否可以看到某个任务
     */
    boolean canUserSeeTask(String userId, String taskId);
    
    /**
     * 检查用户是否可以认领某个任务
     */
    boolean canUserClaimTask(String userId, String taskId, String groupId);
}
