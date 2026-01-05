package com.portal.component;

import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import com.portal.entity.DelegationAudit;
import com.portal.entity.DelegationRule;
import com.portal.exception.PortalException;
import com.portal.repository.DelegationAuditRepository;
import com.portal.repository.DelegationRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 任务处理组件
 * 支持任务认领、完成、转办、委托等操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProcessComponent {

    private final TaskQueryComponent taskQueryComponent;
    private final DelegationRuleRepository delegationRuleRepository;
    private final DelegationAuditRepository delegationAuditRepository;

    /**
     * 认领任务
     */
    @Transactional
    public TaskInfo claimTask(String taskId, String userId) {
        TaskInfo task = getTaskOrThrow(taskId);

        // 验证任务是否可以被认领
        if ("USER".equals(task.getAssignmentType())) {
            throw new PortalException("400", "直接分配的任务不需要认领");
        }

        // 验证用户是否有权限认领
        if (!canClaimTask(task, userId)) {
            throw new PortalException("403", "您没有权限认领此任务");
        }

        // 更新任务分配
        task.setAssignmentType("USER");
        task.setAssignee(userId);
        taskQueryComponent.addTask(task);

        log.info("用户 {} 认领了任务 {}", userId, taskId);
        return task;
    }

    /**
     * 取消认领任务
     */
    @Transactional
    public TaskInfo unclaimTask(String taskId, String userId, String originalAssignmentType, String originalAssignee) {
        TaskInfo task = getTaskOrThrow(taskId);

        // 验证是否是当前处理人
        if (!userId.equals(task.getAssignee())) {
            throw new PortalException("403", "只有当前处理人才能取消认领");
        }

        // 恢复原始分配
        task.setAssignmentType(originalAssignmentType);
        task.setAssignee(originalAssignee);
        taskQueryComponent.addTask(task);

        log.info("用户 {} 取消认领了任务 {}", userId, taskId);
        return task;
    }

    /**
     * 完成任务
     */
    @Transactional
    public void completeTask(TaskCompleteRequest request, String userId) {
        String taskId = request.getTaskId();
        TaskInfo task = getTaskOrThrow(taskId);

        // 验证用户是否有权限处理任务
        if (!canProcessTask(task, userId)) {
            throw new PortalException("403", "您没有权限处理此任务");
        }

        String action = request.getAction();
        switch (action) {
            case "APPROVE", "REJECT" -> handleApproval(task, request, userId);
            case "TRANSFER" -> handleTransfer(task, request, userId);
            case "DELEGATE" -> handleDelegate(task, request, userId);
            case "RETURN" -> handleReturn(task, request, userId);
            default -> throw new PortalException("400", "不支持的操作类型: " + action);
        }
    }

    /**
     * 委托任务
     */
    @Transactional
    public void delegateTask(String taskId, String delegatorId, String delegateId, String reason) {
        TaskInfo task = getTaskOrThrow(taskId);

        // 验证委托人是否有权限
        if (!canProcessTask(task, delegatorId)) {
            throw new PortalException("403", "您没有权限委托此任务");
        }

        // 更新任务
        task.setDelegatorId(delegatorId);
        task.setDelegatorName(delegatorId); // 实际应查询用户名
        task.setAssignee(delegateId);
        task.setAssignmentType("DELEGATED");
        taskQueryComponent.addTask(task);

        // 记录审计日志
        DelegationAudit audit = DelegationAudit.builder()
                .delegatorId(delegatorId)
                .delegateId(delegateId)
                .taskId(taskId)
                .operationType("DELEGATE_TASK")
                .operationResult("SUCCESS")
                .operationDetail(reason)
                .build();
        delegationAuditRepository.save(audit);

        log.info("用户 {} 将任务 {} 委托给 {}", delegatorId, taskId, delegateId);
    }

    /**
     * 转办任务
     */
    @Transactional
    public void transferTask(String taskId, String fromUserId, String toUserId, String reason) {
        TaskInfo task = getTaskOrThrow(taskId);

        // 验证转办人是否有权限
        if (!canProcessTask(task, fromUserId)) {
            throw new PortalException("403", "您没有权限转办此任务");
        }

        // 更新任务
        task.setAssignee(toUserId);
        task.setAssignmentType("USER");
        task.setDelegatorId(null);
        task.setDelegatorName(null);
        taskQueryComponent.addTask(task);

        // 记录审计日志
        DelegationAudit audit = DelegationAudit.builder()
                .delegatorId(fromUserId)
                .delegateId(toUserId)
                .taskId(taskId)
                .operationType("TRANSFER_TASK")
                .operationResult("SUCCESS")
                .operationDetail(reason)
                .build();
        delegationAuditRepository.save(audit);

        log.info("用户 {} 将任务 {} 转办给 {}", fromUserId, taskId, toUserId);
    }

    /**
     * 验证用户是否可以认领任务
     */
    public boolean canClaimTask(TaskInfo task, String userId) {
        String assignmentType = task.getAssignmentType();
        String assignee = task.getAssignee();

        return switch (assignmentType) {
            case "VIRTUAL_GROUP" -> isUserInVirtualGroup(userId, assignee);
            case "DEPT_ROLE" -> isUserHasDeptRole(userId, assignee);
            default -> false;
        };
    }

    /**
     * 验证用户是否可以处理任务
     */
    public boolean canProcessTask(TaskInfo task, String userId) {
        String assignmentType = task.getAssignmentType();
        String assignee = task.getAssignee();

        // 直接分配给用户
        if ("USER".equals(assignmentType) && userId.equals(assignee)) {
            return true;
        }

        // 委托任务
        if ("DELEGATED".equals(assignmentType) && userId.equals(assignee)) {
            return true;
        }

        // 虚拟组任务
        if ("VIRTUAL_GROUP".equals(assignmentType) && isUserInVirtualGroup(userId, assignee)) {
            return true;
        }

        // 部门角色任务
        if ("DEPT_ROLE".equals(assignmentType) && isUserHasDeptRole(userId, assignee)) {
            return true;
        }

        // 检查是否有委托权限
        List<DelegationRule> delegations = delegationRuleRepository
                .findActiveDelegationsForDelegate(userId, LocalDateTime.now());
        for (DelegationRule delegation : delegations) {
            if (delegation.getDelegatorId().equals(assignee)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取任务或抛出异常
     */
    private TaskInfo getTaskOrThrow(String taskId) {
        return taskQueryComponent.getTaskById(taskId)
                .orElseThrow(() -> new PortalException("404", "任务不存在: " + taskId));
    }

    /**
     * 处理审批操作
     */
    private void handleApproval(TaskInfo task, TaskCompleteRequest request, String userId) {
        // 实际应调用workflow-engine-core完成任务
        taskQueryComponent.removeTask(task.getTaskId());
        log.info("用户 {} {} 了任务 {}", userId, request.getAction(), task.getTaskId());
    }

    /**
     * 处理转办操作
     */
    private void handleTransfer(TaskInfo task, TaskCompleteRequest request, String userId) {
        String targetUserId = request.getTargetUserId();
        if (targetUserId == null || targetUserId.isEmpty()) {
            throw new PortalException("400", "转办目标用户不能为空");
        }
        transferTask(task.getTaskId(), userId, targetUserId, request.getComment());
    }

    /**
     * 处理委托操作
     */
    private void handleDelegate(TaskInfo task, TaskCompleteRequest request, String userId) {
        String targetUserId = request.getTargetUserId();
        if (targetUserId == null || targetUserId.isEmpty()) {
            throw new PortalException("400", "委托目标用户不能为空");
        }
        delegateTask(task.getTaskId(), userId, targetUserId, request.getComment());
    }

    /**
     * 处理回退操作
     */
    private void handleReturn(TaskInfo task, TaskCompleteRequest request, String userId) {
        // 实际应调用workflow-engine-core回退任务
        log.info("用户 {} 回退了任务 {} 到节点 {}", userId, task.getTaskId(), request.getReturnActivityId());
    }

    /**
     * 检查用户是否在虚拟组中（模拟实现）
     */
    private boolean isUserInVirtualGroup(String userId, String groupId) {
        // 模拟实现，实际应调用admin-center服务
        return groupId.contains(userId) || "common_group".equals(groupId);
    }

    /**
     * 检查用户是否有部门角色（模拟实现）
     */
    private boolean isUserHasDeptRole(String userId, String deptRoleId) {
        // 模拟实现，实际应调用admin-center服务
        return deptRoleId.contains(userId) || "common_role".equals(deptRoleId);
    }
}
