package com.portal.component;

import com.portal.debug.AgentDebugLog;
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
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        return claimTask(taskId, userId, null);
    }

    /**
     * 认领任务（支持 JWT userId 与 Flowable 侧 assignee/候选人使用 username 时不一致的场景）
     */
    @Transactional
    public TaskInfo claimTask(String taskId, String userId, String portalUsername) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        TaskInfo taskBefore = getTaskOrThrow(taskId);
        String enginePrincipal = resolveEnginePrincipalForWorkflow(taskBefore, userId, portalUsername);

        log.info("Using Flowable engine to claim task: {} by engine principal: {} (portal userId: {})", taskId, enginePrincipal, userId);
        Optional<Map<String, Object>> result = workflowEngineClient.claimTask(taskId, enginePrincipal);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to claim task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to claim task";
            throw new PortalException("500", message);
        }
        
        // 任务状态已在 Flowable 中更新，重新获取最新状态
        TaskInfo task = getTaskOrThrow(taskId);
        
        // 更新流程实例的当前处理人（门户侧统一记 JWT userId）
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
        return unclaimTask(taskId, userId, originalAssignmentType, originalAssignee, null);
    }

    @Transactional
    public TaskInfo unclaimTask(String taskId, String userId, String originalAssignmentType, String originalAssignee,
                                String portalUsername) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        TaskInfo taskBefore = getTaskOrThrow(taskId);
        String enginePrincipal = resolveEnginePrincipalForWorkflow(taskBefore, userId, portalUsername);

        log.info("Using Flowable engine to unclaim task: {} by engine principal: {} (portal userId: {})", taskId, enginePrincipal, userId);
        Optional<Map<String, Object>> result = workflowEngineClient.unclaimTask(taskId, enginePrincipal);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to unclaim task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to unclaim task";
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
        completeTask(request, userId, null);
    }

    @Transactional
    public void completeTask(TaskCompleteRequest request, String userId, String portalUsername) {
        String taskId = request.getTaskId();
        TaskInfo task = getTaskOrThrow(taskId);

        // 验证用户是否有权限处理任务
        if (!canProcessTask(task, userId, portalUsername)) {
            throw new PortalException("403", "You do not have permission to process this task");
        }

        // 自动认领：虚拟组或 Flowable 候选人池任务且尚未有 assignee
        if (("VIRTUAL_GROUP".equals(task.getAssignmentType()) || "CANDIDATE_USERS".equals(task.getAssignmentType()))
                && (task.getAssignee() == null || task.getAssignee().isEmpty())) {
            log.info("Auto-claiming pool task {} (type {}) for user {}", taskId, task.getAssignmentType(), userId);
            claimTask(taskId, userId, portalUsername);
            task = getTaskOrThrow(taskId); // 认领后刷新任务状态
        }

        String action = request.getAction();
        switch (action) {
            case "APPROVE", "REJECT" -> handleApproval(task, request, userId);
            case "TRANSFER" -> handleTransfer(task, request, userId);
            case "DELEGATE" -> handleDelegate(task, request, userId);
            case "RETURN" -> handleReturn(task, request, userId);
            default -> throw new PortalException("400", "Unsupported action type: " + action);
        }
    }

    /**
     * 委托任务
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    @Transactional
    public void delegateTask(String taskId, String delegatorId, String delegateId, String reason) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        log.info("Using Flowable engine to delegate task: {} from {} to {}", taskId, delegatorId, delegateId);
        Optional<Map<String, Object>> result = workflowEngineClient.delegateTask(taskId, delegatorId, delegateId, reason);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to delegate task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to delegate task";
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
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        log.info("Using Flowable engine to transfer task: {} from {} to {}", taskId, fromUserId, toUserId);
        Optional<Map<String, Object>> result = workflowEngineClient.transferTask(taskId, fromUserId, toUserId, reason);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to transfer task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to transfer task";
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
     * 为子表行分配处理人（多实例子流程前置任务），经 {@link WorkflowEngineClient} 调用引擎。
     */
    @Transactional
    public Map<String, Object> assignSubTableRow(String taskId, Long rowId, String assigneeId, String userId) {
        return assignSubTableRow(taskId, rowId, assigneeId, userId, null);
    }

    @Transactional
    public Map<String, Object> assignSubTableRow(String taskId, Long rowId, String assigneeId, String userId,
                                                 String portalUsername) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        TaskInfo task = getTaskOrThrow(taskId);
        if (!canProcessTask(task, userId, portalUsername)) {
            throw new PortalException("403", "You do not have permission to process this task");
        }

        if (("VIRTUAL_GROUP".equals(task.getAssignmentType()) || "CANDIDATE_USERS".equals(task.getAssignmentType()))
                && (task.getAssignee() == null || task.getAssignee().isEmpty())) {
            log.info("Auto-claiming pool task {} (type {}) for sub-table assign by user {}",
                    taskId, task.getAssignmentType(), userId);
            claimTask(taskId, userId, portalUsername);
        }

        Optional<Map<String, Object>> result = workflowEngineClient.assignSubTableRow(taskId, rowId, assigneeId);
        // #region agent log
        {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("engineResultEmpty", result.isEmpty());
            result.ifPresent(m -> d.put("engineKeys", m.keySet().toString()));
            result.ifPresent(m -> d.put("engineSuccess", m.get("success")));
            AgentDebugLog.ff0c74("TaskProcessComponent.assignSubTableRow", "H4", "after_engine_client", d);
        }
        // #endregion
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to assign sub-table row: " + taskId);
        }

        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? String.valueOf(data.get("message")) : "Assignment failed";
            throw new PortalException("400", message);
        }
        return data;
    }

    /**
     * JWT 与引擎侧用户 ID 比较：trim，避免首尾空格导致误判。
     */
    private static boolean samePortalUserId(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equals(b.trim());
    }

    /**
     * 引擎返回的 assignee / 候选人可能是 username，JWT {@code userId} 为用户主键 UUID。
     */
    private static boolean matchesPortalIdentity(String engineSideRef, String portalUserId, String portalUsername) {
        if (engineSideRef == null || engineSideRef.isBlank() || portalUserId == null) {
            return false;
        }
        if (samePortalUserId(portalUserId, engineSideRef)) {
            return true;
        }
        if (portalUsername != null && !portalUsername.isBlank()
                && portalUsername.trim().equals(engineSideRef.trim())) {
            return true;
        }
        return false;
    }

    /**
     * 认领 / 取消认领时须传入与 Flowable IdentityLink 一致的字符串（候选人常为 username）。
     */
    private static String resolveEnginePrincipalForWorkflow(TaskInfo task, String portalUserId, String portalUsername) {
        if (portalUserId == null || portalUserId.isBlank()) {
            return portalUserId != null ? portalUserId.trim() : "";
        }
        String pu = portalUserId.trim();
        String puName = portalUsername != null ? portalUsername.trim() : "";

        String assignee = task.getAssignee();
        if (assignee != null && !assignee.isBlank() && matchesPortalIdentity(assignee, portalUserId, portalUsername)) {
            return assignee.trim();
        }
        List<String> candidates = task.getCandidateUserIds();
        if (candidates != null) {
            for (String c : candidates) {
                if (c == null || c.isBlank()) {
                    continue;
                }
                if (pu.equals(c.trim())) {
                    return c.trim();
                }
            }
            if (!puName.isEmpty()) {
                for (String c : candidates) {
                    if (c != null && puName.equals(c.trim())) {
                        return c.trim();
                    }
                }
            }
        }
        return pu;
    }

    private static boolean candidateUserIdsContain(List<String> candidateUserIds, String userId, String portalUsername) {
        if (candidateUserIds == null || userId == null) {
            return false;
        }
        for (String id : candidateUserIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (matchesPortalIdentity(id.trim(), userId, portalUsername)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证用户是否可以认领任务
     */
    public boolean canClaimTask(TaskInfo task, String userId) {
        return canClaimTask(task, userId, null);
    }

    public boolean canClaimTask(TaskInfo task, String userId, String portalUsername) {
        String assignmentType = task.getAssignmentType();
        String assignee = task.getAssignee();

        return switch (assignmentType != null ? assignmentType : "") {
            case "CANDIDATE_USERS" ->
                    candidateUserIdsContain(task.getCandidateUserIds(), userId, portalUsername);
            case "VIRTUAL_GROUP" -> {
                if (assignee != null && !assignee.isEmpty()) {
                    yield isUserInVirtualGroup(userId, assignee);
                }
                if (task.getCandidateGroupIds() != null) {
                    for (String g : task.getCandidateGroupIds()) {
                        if (isUserInVirtualGroup(userId, g)) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
            default -> false;
        };
    }

    /**
     * 验证用户是否可以处理任务
     */
    public boolean canProcessTask(TaskInfo task, String userId) {
        return canProcessTask(task, userId, null);
    }

    public boolean canProcessTask(TaskInfo task, String userId, String portalUsername) {
        String assignmentType = task.getAssignmentType();
        String assignee = task.getAssignee();

        // 如果任务已分配给当前用户（包括认领后的任务），允许处理
        if (assignee != null && matchesPortalIdentity(assignee, userId, portalUsername)) {
            return true;
        }

        // 直接分配给用户
        if ("USER".equals(assignmentType) && assignee != null && matchesPortalIdentity(assignee, userId, portalUsername)) {
            return true;
        }

        // 委托任务
        if ("DELEGATED".equals(assignmentType) && assignee != null && matchesPortalIdentity(assignee, userId, portalUsername)) {
            return true;
        }

        // Flowable 候选人池：必须在候选人列表中
        if ("CANDIDATE_USERS".equals(assignmentType)) {
            return candidateUserIdsContain(task.getCandidateUserIds(), userId, portalUsername);
        }

        // 实体管理者任务（ENTITY_MANAGER）
        if ("ENTITY_MANAGER".equals(assignmentType)) {
            log.info("Entity manager task {} for user {}, allowing process (permission verified by query)", task.getTaskId(), userId);
            return true;
        }

        // 虚拟组：必须能证明组成员身份（assignee 存组 ID，或引擎返回 candidateGroupIds）
        if ("VIRTUAL_GROUP".equals(assignmentType)) {
            if (assignee != null && !assignee.isEmpty() && isUserInVirtualGroup(userId, assignee)) {
                return true;
            }
            if (task.getCandidateGroupIds() != null) {
                for (String g : task.getCandidateGroupIds()) {
                    if (isUserInVirtualGroup(userId, g)) {
                        return true;
                    }
                }
            }
        }

        // 检查是否有委托权限
        if (assignee != null) {
            List<DelegationRule> delegations = delegationRuleRepository
                    .findActiveDelegationsForDelegate(userId, LocalDateTime.now());
            for (DelegationRule delegation : delegations) {
                if (samePortalUserId(assignee, delegation.getDelegatorId())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 是否可查看任务表单（待办/已办快照）：处理人规则 + 发起人 + 当前 assignee（含已办仍带回 assignee 的场景）。
     */
    public boolean canViewTaskForm(TaskInfo task, String userId) {
        return canViewTaskForm(task, userId, null);
    }

    public boolean canViewTaskForm(TaskInfo task, String userId, String portalUsername) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (canProcessTask(task, userId, portalUsername)) {
            return true;
        }
        if (samePortalUserId(userId, task.getInitiatorId())) {
            return true;
        }
        if (task.getAssignee() != null && matchesPortalIdentity(task.getAssignee(), userId, portalUsername)) {
            return true;
        }
        return false;
    }

    /**
     * 获取任务或抛出异常
     */
    private TaskInfo getTaskOrThrow(String taskId) {
        return taskQueryComponent.getTaskById(taskId)
                .orElseThrow(() -> new PortalException("404", "Task not found: " + taskId));
    }

    /**
     * 处理审批操作
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    private void handleApproval(TaskInfo task, TaskCompleteRequest request, String userId) {
        String taskId = task.getTaskId();
        String action = request.getAction();
        
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
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

        // 如果是"分配参与人"任务，从子表数据构建多实例集合变量
        if ("Task_AssignParticipants".equals(task.getTaskDefinitionKey())) {
            buildParticipantsCollection(variables);
        }
        
        log.info("Variables before calling workflowEngineClient: {}", variables);
        
        Optional<Map<String, Object>> result = workflowEngineClient.completeTask(taskId, userId, action, variables);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to complete task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to complete task";
            throw new PortalException("500", message);
        }
        
        log.info("Task {} completed via Flowable by user {} with action {} (approvalStatus: {})", 
                taskId, userId, action, variables.get("approvalStatus"));
        
        // 将审批变量同步回本地 ProcessInstance，确保 Completed Tasks / My Requests 能看到
        // 注意：必须创建新的 HashMap 而非原地修改旧 Map，否则 Hibernate 对 JSON 列的脏检测
        // 会因新旧引用相同而误判为"未变更"，导致 UPDATE 语句不被执行
        try {
            String syncProcessId = task.getProcessInstanceId();
            Optional<ProcessInstance> syncOpt = processInstanceRepository.findById(syncProcessId);
            if (syncOpt.isPresent()) {
                ProcessInstance syncInstance = syncOpt.get();
                Map<String, Object> existingVars = syncInstance.getVariables();
                Map<String, Object> mergedVars = new HashMap<>();
                if (existingVars != null) {
                    mergedVars.putAll(existingVars);
                }
                mergedVars.putAll(variables);
                syncInstance.setVariables(mergedVars);
                processInstanceRepository.save(syncInstance);
                log.info("Synced {} approval variables back to local ProcessInstance {}", 
                        mergedVars.size(), syncProcessId);
            }
        } catch (Exception e) {
            log.warn("Failed to sync approval variables to local ProcessInstance: {}", e.getMessage());
        }
        
        // 任务完成后，检查流程是否还有活动任务，如果没有则流程可能已完成
        // 这是一个补偿机制，防止 ProcessCompletionListener 通知失败导致状态不同步
        try {
            String processInstanceId = task.getProcessInstanceId();
            
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
                            LocalDateTime finishedAt = LocalDateTime.now();
                            instance.setEndTime(finishedAt);
                            instance.setCompletedAt(finishedAt);
                            instance.setCurrentNode(lastActivityName != null ? lastActivityName : "Completed");
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
                    } else {
                        // 没有下一个用户任务，可能流程已经到达非用户任务节点（如结束事件）
                        // 尝试获取当前活动节点
                        log.info("No next user task found for process {}, checking for current activity", processInstanceId);
                        Optional<Map<String, Object>> currentActivity = getCurrentActivity(processInstanceId);
                        if (currentActivity.isPresent()) {
                            String currentActivityName = (String) currentActivity.get().get("activityName");
                            String currentActivityType = (String) currentActivity.get().get("activityType");
                            log.info("Current activity for process {}: {} (type: {})", 
                                    processInstanceId, currentActivityName, currentActivityType);
                            
                            // 跳过 SequenceFlow 类型，其 name 是条件标签（如 "Yes"/"No"），不应作为 currentNode
                            if ("SequenceFlow".equals(currentActivityType)) {
                                log.warn("Current activity is SequenceFlow (name: {}), skipping currentNode update for process {}", 
                                        currentActivityName, processInstanceId);
                            } else {
                                // 更新流程实例的当前节点
                                Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processInstanceId);
                                if (optInstance.isPresent()) {
                                    ProcessInstance instance = optInstance.get();
                                    instance.setCurrentNode(currentActivityName);
                                    instance.setCurrentAssignee(null);
                                    
                                    // 如果当前活动是结束事件，则流程已完成
                                    if ("endEvent".equals(currentActivityType) || "EndEvent".equals(currentActivityType)) {
                                        log.info("Current activity is end event, marking process {} as COMPLETED", processInstanceId);
                                        instance.setStatus("COMPLETED");
                                        LocalDateTime finishedAt = LocalDateTime.now();
                                        instance.setEndTime(finishedAt);
                                        instance.setCompletedAt(finishedAt);
                                    }
                                    
                                    processInstanceRepository.save(instance);
                                    log.info("Updated process instance {} currentNode to: {}, status: {}", 
                                            processInstanceId, instance.getCurrentNode(), instance.getStatus());
                                }
                            }
                        }
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
            throw new PortalException("400", "Transfer target user cannot be empty");
        }
        transferTask(task.getTaskId(), userId, targetUserId, request.getComment());
    }

    /**
     * 处理委托操作
     */
    private void handleDelegate(TaskInfo task, TaskCompleteRequest request, String userId) {
        String targetUserId = request.getTargetUserId();
        if (targetUserId == null || targetUserId.isEmpty()) {
            throw new PortalException("400", "Delegate target user cannot be empty");
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
            throw new PortalException("400", "Return target activity cannot be empty");
        }
        
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }
        
        log.info("Using Flowable engine to return task: {} to activity: {}", taskId, targetActivityId);
        Optional<Map<String, Object>> result = workflowEngineClient.returnTask(
            taskId, targetActivityId, userId, request.getComment());
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to return task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to return task";
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
            // checkTaskPermission 的第一参数为 taskId，不可传入虚拟组 ID
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
            throw new PortalException("403", "You do not have permission to urge this task");
        }

        // 获取任务处理人
        String assignee = task.getAssignee();
        String assigneeName = task.getAssigneeName();

        // 发送催办通知（实际应调用消息服务）
        String urgeMessage = message != null ? message : "Please process the task as soon as possible: " + task.getTaskName();
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

        log.info("User {} urged task {}, assignee: {}", urgerId, taskId, assignee);
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
                log.warn("Failed to urge task {}: {}", taskId, e.getMessage());
            }
        }
    }

    /**
     * 验证用户是否可以催办任务
     */
    private boolean canUrgeTask(TaskInfo task, String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (task.getInitiatorId() != null && userId.equals(task.getInitiatorId())) {
            return true;
        }
        return SecurityContextUtils.isSuperAdmin();
    }

    /**
     * 发送催办通知
     */
    private void sendUrgeNotification(String taskId, String assignee, String urgerId, String message) {
        // 实际应调用消息服务发送通知
        // 这里只记录日志
        log.info("Sending urge notification: task={}, assignee={}, urger={}, message={}", taskId, assignee, urgerId, message);
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

    /**
     * 获取流程实例的当前活动节点
     */
    private Optional<Map<String, Object>> getCurrentActivity(String processInstanceId) {
        try {
            if (!workflowEngineClient.isAvailable()) {
                return Optional.empty();
            }
            
            // 调用 workflow-engine 获取当前活动节点
            return workflowEngineClient.getCurrentActivity(processInstanceId);
        } catch (Exception e) {
            log.warn("Failed to get current activity for process {}: {}", processInstanceId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从 __subTables__ 中解析 participants 行列表：优先表名 {@code participants}，否则取第一个「像子表行」的 List（含 id/rowId/assignee 等）。
     */
    @SuppressWarnings("unchecked")
    private List<Object> resolveParticipantsRows(Map<String, Object> subTables) {
        Object named = subTables.get("participants");
        if (named instanceof List && !((List<?>) named).isEmpty()) {
            return (List<Object>) named;
        }
        for (Object v : subTables.values()) {
            if (!(v instanceof List<?> list) || list.isEmpty()) {
                continue;
            }
            Object first = list.get(0);
            if (first instanceof Map<?, ?> m) {
                if (m.containsKey("assignee_user_id") || m.containsKey("assigneeId")
                        || m.containsKey("id") || m.containsKey("rowId")) {
                    return (List<Object>) v;
                }
            }
        }
        return List.of();
    }

    /**
     * 从 __subTables__.participants 构建多实例集合变量
     * 每个元素包含 rowId 和 assignee_user_id，供多实例子流程使用
     */
    @SuppressWarnings("unchecked")
    private void buildParticipantsCollection(Map<String, Object> variables) {
        try {
            Object subTablesObj = variables.get("__subTables__");
            if (!(subTablesObj instanceof Map)) {
                log.warn("[MultiInstance] No __subTables__ found, setting empty participants collection");
                variables.put("multiInstance_participants_collection", List.of());
                return;
            }
            Map<String, Object> subTables = (Map<String, Object>) subTablesObj;
            // 设计器/脚本可能用表名 participants；门户前端常以 bindingId 为 key，需兼容两种结构
            List<Object> rows = resolveParticipantsRows(subTables);
            if (rows.isEmpty()) {
                log.warn("[MultiInstance] No participants sub-table rows found, setting empty collection");
                variables.put("multiInstance_participants_collection", List.of());
                return;
            }
            List<Map<String, Object>> collection = new java.util.ArrayList<>();
            for (Object rowObj : rows) {
                if (!(rowObj instanceof Map)) continue;
                Map<String, Object> row = (Map<String, Object>) rowObj;
                Map<String, Object> item = new HashMap<>();
                // rowId is used by the sub-process to identify which row to update
                Object rowId = row.get("rowId");
                if (rowId == null) rowId = row.get("id");
                item.put("rowId", rowId);
                item.put("assignee_user_id", row.get("assignee_user_id"));
                collection.add(item);
            }
            variables.put("multiInstance_participants_collection", collection);
            log.info("[MultiInstance] Built participants collection with {} items", collection.size());
        } catch (Exception e) {
            log.warn("[MultiInstance] Failed to build participants collection: {}", e.getMessage());
            variables.put("multiInstance_participants_collection", List.of());
        }
    }
}
