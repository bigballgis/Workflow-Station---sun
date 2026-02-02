package com.admin.service.impl;

import com.admin.dto.request.TaskClaimRequest;
import com.admin.dto.request.TaskDelegationRequest;
import com.admin.dto.response.GroupTaskInfo;
import com.admin.dto.response.TaskHistoryInfo;
import com.platform.security.entity.VirtualGroup;
import com.admin.entity.VirtualGroupTaskHistory;
import com.admin.enums.TaskActionType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.VirtualGroupNotFoundException;
import com.admin.helper.VirtualGroupHelper;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupTaskHistoryRepository;
import com.admin.service.VirtualGroupTaskService;
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
 * 虚拟组任务服务实现
 * 负责虚拟组任务的可见性、认领、委托等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualGroupTaskServiceImpl implements VirtualGroupTaskService {
    
    private final VirtualGroupRepository virtualGroupRepository;
    private final VirtualGroupMemberRepository virtualGroupMemberRepository;
    private final VirtualGroupTaskHistoryRepository taskHistoryRepository;
    private final VirtualGroupHelper virtualGroupHelper;

    
    @Override
    public List<GroupTaskInfo> getGroupTasks(String groupId, String userId) {
        log.info("Getting tasks for group {} by user {}", groupId, userId);
        
        // 验证虚拟组存在
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        // 验证用户是组成员
        if (!virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId)) {
            throw new AdminBusinessException("NOT_GROUP_MEMBER", "用户不是该虚拟组成员");
        }
        
        // 验证虚拟组有效
        if (!virtualGroupHelper.isValid(group)) {
            throw new AdminBusinessException("GROUP_INVALID", "虚拟组已失效或过期");
        }
        
        // 这里应该调用工作流引擎获取分配给该组的任务
        // 由于工作流引擎是独立模块，这里返回模拟数据结构
        // 实际实现时需要与工作流引擎集成
        return getTasksAssignedToGroup(groupId);
    }
    
    @Override
    public List<GroupTaskInfo> getUserVisibleGroupTasks(String userId) {
        log.info("Getting all visible group tasks for user {}", userId);
        
        // 获取用户所属的所有有效虚拟组
        List<VirtualGroup> userGroups = virtualGroupRepository.findByUserId(userId);
        
        List<GroupTaskInfo> allTasks = new ArrayList<>();
        for (VirtualGroup group : userGroups) {
            if (virtualGroupHelper.isValid(group)) {
                allTasks.addAll(getTasksAssignedToGroup(group.getId()));
            }
        }
        
        return allTasks;
    }
    
    @Override
    @Transactional
    public void claimTask(String userId, TaskClaimRequest request) {
        log.info("User {} claiming task {} from group {}", userId, request.getTaskId(), request.getGroupId());
        
        // 验证用户可以认领该任务
        if (!canUserClaimTask(userId, request.getTaskId(), request.getGroupId())) {
            throw new AdminBusinessException("CANNOT_CLAIM", "用户无法认领该任务");
        }
        
        // 记录认领历史
        VirtualGroupTaskHistory history = VirtualGroupTaskHistory.builder()
                .id(UUID.randomUUID().toString())
                .taskId(request.getTaskId())
                .groupId(request.getGroupId())
                .actionType(TaskActionType.CLAIMED)
                .toUserId(userId)
                .comment(request.getComment())
                .createdAt(Instant.now())
                .build();
        
        taskHistoryRepository.save(history);
        
        // 这里应该调用工作流引擎将任务分配给用户
        // 实际实现时需要与工作流引擎集成
        claimTaskInWorkflowEngine(request.getTaskId(), userId);
        
        log.info("Task {} claimed by user {} from group {}", request.getTaskId(), userId, request.getGroupId());
    }
    
    @Override
    @Transactional
    public void delegateTask(String userId, TaskDelegationRequest request) {
        log.info("User {} delegating task {} to user {}", userId, request.getTaskId(), request.getToUserId());
        
        // 验证任务存在且用户有权限委托
        // 这里应该检查用户是否是任务的当前处理人
        
        // 记录委托历史
        VirtualGroupTaskHistory history = VirtualGroupTaskHistory.builder()
                .id(UUID.randomUUID().toString())
                .taskId(request.getTaskId())
                .actionType(TaskActionType.DELEGATED)
                .fromUserId(userId)
                .toUserId(request.getToUserId())
                .reason(request.getReason())
                .comment(request.getComment())
                .createdAt(Instant.now())
                .build();
        
        taskHistoryRepository.save(history);
        
        // 这里应该调用工作流引擎将任务委托给目标用户
        // 实际实现时需要与工作流引擎集成
        delegateTaskInWorkflowEngine(request.getTaskId(), userId, request.getToUserId());
        
        log.info("Task {} delegated from user {} to user {}", request.getTaskId(), userId, request.getToUserId());
    }

    
    @Override
    public List<TaskHistoryInfo> getTaskHistory(String taskId) {
        log.info("Getting history for task {}", taskId);
        
        return taskHistoryRepository.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(TaskHistoryInfo::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<TaskHistoryInfo> getGroupTaskHistory(String groupId) {
        log.info("Getting task history for group {}", groupId);
        
        if (!virtualGroupRepository.existsById(groupId)) {
            throw new VirtualGroupNotFoundException(groupId);
        }
        
        return taskHistoryRepository.findByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .map(TaskHistoryInfo::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean canUserSeeTask(String userId, String taskId) {
        // 检查用户是否是任务的直接处理人
        if (isTaskAssignedToUser(taskId, userId)) {
            return true;
        }
        
        // 检查用户是否属于任务分配的虚拟组
        String groupId = getTaskAssignedGroup(taskId);
        if (groupId != null) {
            return virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId);
        }
        
        return false;
    }
    
    @Override
    public boolean canUserClaimTask(String userId, String taskId, String groupId) {
        // 验证虚拟组存在且有效
        VirtualGroup group = virtualGroupRepository.findById(groupId).orElse(null);
        if (group == null || !virtualGroupHelper.isValid(group)) {
            return false;
        }
        
        // 验证用户是组成员
        if (!virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(groupId, userId)) {
            return false;
        }
        
        // 验证任务是分配给该组的且未被认领
        return isTaskAssignedToGroupAndUnclaimed(taskId, groupId);
    }
    
    // ==================== 工作流引擎集成方法（需要实际实现） ====================
    
    /**
     * 获取分配给虚拟组的任务
     * 实际实现时需要与工作流引擎集成
     */
    private List<GroupTaskInfo> getTasksAssignedToGroup(String groupId) {
        // TODO: 与工作流引擎集成，获取分配给该组的任务
        // 这里返回空列表，实际实现时需要调用工作流引擎API
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
     * 在工作流引擎中委托任务
     * 实际实现时需要与工作流引擎集成
     */
    private void delegateTaskInWorkflowEngine(String taskId, String fromUserId, String toUserId) {
        // TODO: 与工作流引擎集成，将任务委托给目标用户
        log.info("Delegating task {} from {} to {} in workflow engine", taskId, fromUserId, toUserId);
    }
    
    /**
     * 检查任务是否直接分配给用户
     * 实际实现时需要与工作流引擎集成
     */
    private boolean isTaskAssignedToUser(String taskId, String userId) {
        // TODO: 与工作流引擎集成
        return false;
    }
    
    /**
     * 获取任务分配的虚拟组ID
     * 实际实现时需要与工作流引擎集成
     */
    private String getTaskAssignedGroup(String taskId) {
        // TODO: 与工作流引擎集成
        return null;
    }
    
    /**
     * 检查任务是否分配给虚拟组且未被认领
     * 实际实现时需要与工作流引擎集成
     */
    private boolean isTaskAssignedToGroupAndUnclaimed(String taskId, String groupId) {
        // TODO: 与工作流引擎集成
        // 检查是否有认领记录
        List<VirtualGroupTaskHistory> claimHistory = taskHistoryRepository.findClaimHistoryByTaskId(taskId);
        return claimHistory.isEmpty();
    }
}
