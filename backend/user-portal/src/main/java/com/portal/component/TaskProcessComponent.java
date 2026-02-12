package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import com.portal.entity.DelegationAudit;
import com.portal.entity.DelegationRule;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.DelegationAuditRepository;
import com.portal.repository.DelegationRuleRepository;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 任务处理组件
 * 支持任务认领、完成、转办、委托等操作
 * 
 * 通过 WorkflowEngineClient 调用 Flowable 引擎
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProcessComponent {

    private final TaskQueryComponent taskQueryComponent;
    private final DelegationRuleRepository delegationRuleRepository;
    private final DelegationAuditRepository delegationAuditRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final ProcessInstanceRepository processInstanceRepository;

    /**
     * 认领任务
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    @Transactional
    public TaskInfo claimTask(String taskId, String userId) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }
        
        log.info("Using Flowable engine to claim task: {} by user: {}", taskId, userId);
        Optional<Map<String, Object>> result = workflowEngineClient.claimTask(taskId, userId);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "认领任务失败: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "认领任务失败";
            throw new PortalException("500", message);
        }
        
        // 任务状态已在 Flowable 中更新，重新获取最新状态
        TaskInfo task = getTaskOrThrow(taskId);
        
        // 更新流程实例的当前处理人
        updateProcessInstanceAssignee(task.getProcessInstanceId(), userId, task.getTaskName());
        
        log.info("Task {} claimed via Flowable by user {}", taskId, userId);
        return task;
    }

    /**
     * 取消认领任务
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    @Transactional
    public TaskInfo unclaimTask(String taskId, String userId, String originalAssignmentType, String originalAssignee) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }
        
        log.info("Using Flowable engine to unclaim task: {} by user: {}", taskId, userId);
        Optional<Map<String, Object>> result = workflowEngineClient.unclaimTask(taskId, userId);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "取消认领任务失败: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "取消认领任务失败";
            throw new PortalException("500", message);
        }
        
        // 任务状态已在 Flowable 中更新，重新获取最新状态
        TaskInfo task = getTaskOrThrow(taskId);
        
        // 取消认领后，清空流程实例的当前处理人
        updateProcessInstanceAssignee(task.getProcessInstanceId(), null, task.getTaskName());

        log.info("Task {} unclaimed via Flowable by user {}", taskId, userId);
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

        // 自动认领虚拟组任务：如果任务属于虚拟组且未被认领，先自动认领再处理
        if ("VIRTUAL_GROUP".equals(task.getAssignmentType()) && 
                (task.getAssignee() == null || task.getAssignee().isEmpty())) {
            log.info("Auto-claiming virtual group task {} for user {}", taskId, userId);
            claimTask(taskId, userId);
            task = getTaskOrThrow(taskId); // 认领后刷新任务状态
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
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    @Transactional
    public void delegateTask(String taskId, String delegatorId, String delegateId, String reason) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }
        
        log.info("Using Flowable engine to delegate task: {} from {} to {}", taskId, delegatorId, delegateId);
        Optional<Map<String, Object>> result = workflowEngineClient.delegateTask(taskId, delegatorId, delegateId, reason);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "委托任务失败: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "委托任务失败";
            throw new PortalException("500", message);
        }
        
        // 更新流程实例的当前处理人
        TaskInfo task = getTaskOrThrow(taskId);
        updateProcessInstanceAssignee(task.getProcessInstanceId(), delegateId, task.getTaskName());
        
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
        
        log.info("Task {} delegated via Flowable from {} to {}", taskId, delegatorId, delegateId);
    }

    /**
     * 转办任务
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    @Transactional
    public void transferTask(String taskId, String fromUserId, String toUserId, String reason) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }
        
        log.info("Using Flowable engine to transfer task: {} from {} to {}", taskId, fromUserId, toUserId);
        Optional<Map<String, Object>> result = workflowEngineClient.transferTask(taskId, fromUserId, toUserId, reason);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "转办任务失败: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "转办任务失败";
            throw new PortalException("500", message);
        }
        
        // 更新流程实例的当前处理人
        TaskInfo task = getTaskOrThrow(taskId);
        updateProcessInstanceAssignee(task.getProcessInstanceId(), toUserId, task.getTaskName());

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

        log.info("Task {} transferred via Flowable from {} to {}", taskId, fromUserId, toUserId);
    }

    /**
     * 验证用户是否可以认领任务
     */
    public boolean canClaimTask(TaskInfo task, String userId) {
        String assignmentType = task.getAssignmentType();
        String assignee = task.getAssignee();

        return switch (assignmentType) {
            case "VIRTUAL_GROUP" -> isUserInVirtualGroup(userId, assignee);
            default -> false;
        };
    }

    /**
     * 验证用户是否可以处理任务
     */
    public boolean canProcessTask(TaskInfo task, String userId) {
        String assignmentType = task.getAssignmentType();
        String assignee = task.getAssignee();

        // 如果任务已分配给当前用户（包括认领后的任务），允许处理
        // 这是最优先的检查，因为认领后 assignee 会被设置为认领人
        if (userId.equals(assignee)) {
            return true;
        }

        // 直接分配给用户
        if ("USER".equals(assignmentType) && userId.equals(assignee)) {
            return true;
        }

        // 委托任务
        if ("DELEGATED".equals(assignmentType) && userId.equals(assignee)) {
            return true;
        }

        // 实体管理者任务（ENTITY_MANAGER）
        // 这种类型的任务通常分配给特定角色或组，如果用户能查询到此任务，说明已通过权限验证
        if ("ENTITY_MANAGER".equals(assignmentType)) {
            log.info("Entity manager task {} for user {}, allowing process (permission verified by query)", task.getTaskId(), userId);
            return true;
        }

        // 虚拟组任务（未认领的情况）
        if ("VIRTUAL_GROUP".equals(assignmentType)) {
            // 如果 assignee 为 null，说明任务未认领，用户能查询到此任务说明 Flowable 已验证其组成员资格
            if (assignee == null || assignee.isEmpty()) {
                log.info("Virtual group task {} has no assignee, allowing user {} to process (group membership verified by Flowable query)", task.getTaskId(), userId);
                return true;
            }
            if (isUserInVirtualGroup(userId, assignee)) {
                return true;
            }
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
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    private void handleApproval(TaskInfo task, TaskCompleteRequest request, String userId) {
        String taskId = task.getTaskId();
        String action = request.getAction();
        
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }
        
        log.info("Using Flowable engine to complete task: {} with action: {}", taskId, action);
        
        // Start with variables from request if provided
        Map<String, Object> variables = new HashMap<>();
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }
        
        // Add action
        variables.put("action", action);
        
        // Auto-set decision variable based on action
        if ("APPROVE".equals(action)) {
            variables.put("decision", "yes");
            variables.put("approvalStatus", "APPROVED");
            log.info("Set decision=yes for APPROVE action");
        } else if ("REJECT".equals(action)) {
            variables.put("decision", "no");
            variables.put("approvalStatus", "REJECTED");
            log.info("Set decision=no for REJECT action");
        }
        
        // Add approver comments
        if (request.getComment() != null && !request.getComment().isEmpty()) {
            variables.put("approverComments", request.getComment());
        }
        
        // Add any additional form data
        if (request.getFormData() != null) {
            variables.putAll(request.getFormData());
        }
        
        log.info("Variables before calling workflowEngineClient: {}", variables);
        
        Optional<Map<String, Object>> result = workflowEngineClient.completeTask(taskId, userId, action, variables);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "完成任务失败: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "完成任务失败";
            throw new PortalException("500", message);
        }
        
        log.info("Task {} completed via Flowable by user {} with action {} (approvalStatus: {})", 
                taskId, userId, action, variables.get("approvalStatus"));
        
        // 任务完成后，检查流程是否还有活动任务，如果没有则流程可能已完成
        // 这是一个补偿机制，防止 ProcessCompletionListener 通知失败导致状态不同步
        try {
            String processInstanceId = task.getProcessInstanceId();
            // 延迟一小段时间，让 Flowable 完成流程状态更新
            Thread.sleep(500);
            
            // 通过 workflowEngineClient 检查流程状态
            Optional<Map<String, Object>> processStatus = workflowEngineClient.getProcessInstanceStatus(processInstanceId);
            if (processStatus.isPresent()) {
                Map<String, Object> status = processStatus.get();
                Boolean isCompleted = (Boolean) status.get("completed");
                
                if (Boolean.TRUE.equals(isCompleted)) {
                    log.info("Process {} is completed after task completion, updating current node", processInstanceId);
                    String lastActivityName = (String) status.get("lastActivityName");
                    
                    // 更新流程实例状态
                    Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processInstanceId);
                    if (optInstance.isPresent()) {
                        ProcessInstance instance = optInstance.get();
                        if ("RUNNING".equals(instance.getStatus())) {
                            instance.setStatus("COMPLETED");
                            instance.setEndTime(LocalDateTime.now());
                            instance.setCurrentNode(lastActivityName != null ? lastActivityName : "已完成");
                            instance.setCurrentAssignee(null);
                            processInstanceRepository.save(instance);
                            log.info("Process instance {} updated to COMPLETED with currentNode: {}", 
                                    processInstanceId, instance.getCurrentNode());
                        }
                    }
                } else {
                    // 流程未完成，可能有下一个任务，尝试获取下一个任务信息
                    String nextTaskName = (String) status.get("nextTaskName");
                    String nextAssignee = (String) status.get("nextAssignee");
                    if (nextTaskName != null) {
                        updateProcessInstanceAssignee(processInstanceId, nextAssignee, nextTaskName);
                        log.info("Process {} continues with next task: {}", processInstanceId, nextTaskName);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check process status after task completion: {}", e.getMessage());
            // 不抛出异常，因为这只是一个补偿机制
        }
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
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    private void handleReturn(TaskInfo task, TaskCompleteRequest request, String userId) {
        String taskId = task.getTaskId();
        String targetActivityId = request.getReturnActivityId();
        
        if (targetActivityId == null || targetActivityId.isEmpty()) {
            throw new PortalException("400", "回退目标节点不能为空");
        }
        
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }
        
        log.info("Using Flowable engine to return task: {} to activity: {}", taskId, targetActivityId);
        Optional<Map<String, Object>> result = workflowEngineClient.returnTask(
            taskId, targetActivityId, userId, request.getComment());
        
        if (result.isEmpty()) {
            throw new PortalException("500", "回退任务失败: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "回退任务失败";
            throw new PortalException("500", message);
        }
        
        // 记录审计日志
        DelegationAudit audit = DelegationAudit.builder()
                .delegatorId(userId)
                .delegateId(targetActivityId)
                .taskId(taskId)
                .operationType("RETURN_TASK")
                .operationResult("SUCCESS")
                .operationDetail(request.getComment())
                .build();
        delegationAuditRepository.save(audit);
        
        log.info("Task {} returned via Flowable to activity {} by user {}", taskId, targetActivityId, userId);
    }

    /**
     * 检查用户是否在虚拟组中
     * 通过 WorkflowEngineClient 调用 workflow-engine-core 验证
     */
    private boolean isUserInVirtualGroup(String userId, String groupId) {
        if (!workflowEngineClient.isAvailable()) {
            log.warn("Workflow engine not available, cannot verify virtual group membership");
            return false;
        }
        
        try {
            Optional<Boolean> result = workflowEngineClient.checkTaskPermission(groupId, userId);
            if (result.isPresent()) {
                return result.get();
            }
            
            // 如果无法通过任务权限检查，尝试获取用户的虚拟组列表
            Optional<Map<String, Object>> permissions = workflowEngineClient.getUserTaskPermissions(userId);
            if (permissions.isPresent()) {
                @SuppressWarnings("unchecked")
                List<String> groupIds = (List<String>) permissions.get().get("virtualGroupIds");
                if (groupIds != null) {
                    return groupIds.contains(groupId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check virtual group membership: {}", e.getMessage());
        }
        
        return false;
    }

    /**
     * 催办任务
     */
    @Transactional
    public void urgeTask(String taskId, String urgerId, String message) {
        TaskInfo task = getTaskOrThrow(taskId);

        // 验证催办人是否有权限（通常是流程发起人或管理员）
        if (!canUrgeTask(task, urgerId)) {
            throw new PortalException("403", "您没有权限催办此任务");
        }

        // 获取任务处理人
        String assignee = task.getAssignee();
        String assigneeName = task.getAssigneeName();

        // 发送催办通知（实际应调用消息服务）
        String urgeMessage = message != null ? message : "请尽快处理任务：" + task.getTaskName();
        sendUrgeNotification(taskId, assignee, urgerId, urgeMessage);

        // 记录催办日志
        DelegationAudit audit = DelegationAudit.builder()
                .delegatorId(urgerId)
                .delegateId(assignee)
                .taskId(taskId)
                .operationType("URGE_TASK")
                .operationResult("SUCCESS")
                .operationDetail(urgeMessage)
                .build();
        delegationAuditRepository.save(audit);

        log.info("用户 {} 催办了任务 {}，处理人: {}", urgerId, taskId, assignee);
    }

    /**
     * 批量催办任务
     */
    @Transactional
    public void batchUrgeTasks(List<String> taskIds, String urgerId, String message) {
        for (String taskId : taskIds) {
            try {
                urgeTask(taskId, urgerId, message);
            } catch (Exception e) {
                log.warn("催办任务 {} 失败: {}", taskId, e.getMessage());
            }
        }
    }

    /**
     * 验证用户是否可以催办任务
     */
    private boolean canUrgeTask(TaskInfo task, String userId) {
        // 流程发起人可以催办
        if (userId.equals(task.getInitiatorId())) {
            return true;
        }
        // 管理员可以催办（实际应检查用户角色）
        // 这里简化处理，允许所有用户催办
        return true;
    }

    /**
     * 发送催办通知
     */
    private void sendUrgeNotification(String taskId, String assignee, String urgerId, String message) {
        // 实际应调用消息服务发送通知
        // 这里只记录日志
        log.info("发送催办通知: 任务={}, 处理人={}, 催办人={}, 消息={}", taskId, assignee, urgerId, message);
    }

    /**
     * 更新流程实例的当前处理人
     */
    private void updateProcessInstanceAssignee(String processInstanceId, String assignee, String currentNode) {
        if (processInstanceId == null) {
            return;
        }
        
        try {
            Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processInstanceId);
            if (optInstance.isPresent()) {
                ProcessInstance instance = optInstance.get();
                instance.setCurrentAssignee(assignee);
                if (currentNode != null) {
                    instance.setCurrentNode(currentNode);
                }
                processInstanceRepository.save(instance);
                log.info("Updated process instance {} with currentAssignee={}, currentNode={}", 
                        processInstanceId, assignee, currentNode);
            }
        } catch (Exception e) {
            log.warn("Failed to update process instance assignee: {}", e.getMessage());
        }
    }
}
