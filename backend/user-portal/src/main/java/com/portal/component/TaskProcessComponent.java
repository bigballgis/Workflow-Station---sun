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
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
            fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "TaskProcessComponent.java:111", "message", "completeTask entry", "data", java.util.Map.of("taskId", request.getTaskId() != null ? request.getTaskId() : "null", "userId", userId, "action", request.getAction() != null ? request.getAction() : "null"), "timestamp", System.currentTimeMillis()).toString() + "\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        
        String taskId = request.getTaskId();
        TaskInfo task = getTaskOrThrow(taskId);

        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
            fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "TaskProcessComponent.java:116", "message", "Before permission check", "data", java.util.Map.of("taskId", taskId, "assignmentType", task.getAssignmentType() != null ? task.getAssignmentType() : "null", "assignee", task.getAssignee() != null ? task.getAssignee() : "null", "status", task.getStatus() != null ? task.getStatus() : "null"), "timestamp", System.currentTimeMillis()).toString() + "\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion

        // 检查任务状态：如果任务已完成，不允许再次完成
        if (task.getStatus() != null && ("COMPLETED".equals(task.getStatus()) || "COMPLETE".equals(task.getStatus()))) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "TaskProcessComponent.java:132", "message", "Task already completed", "data", java.util.Map.of("taskId", taskId, "status", task.getStatus()), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            throw new PortalException("400", "任务已完成，无法再次处理");
        }

        // 验证用户是否有权限处理任务
        if (!canProcessTask(task, userId)) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "TaskProcessComponent.java:117", "message", "Permission check failed", "data", java.util.Map.of("taskId", taskId, "userId", userId), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            throw new PortalException("403", "您没有权限处理此任务");
        }

        String action = request.getAction();
        
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
            fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "TaskProcessComponent.java:122", "message", "Before action switch", "data", java.util.Map.of("taskId", taskId, "action", action), "timestamp", System.currentTimeMillis()).toString() + "\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        
        switch (action) {
            case "APPROVE", "REJECT" -> handleApproval(task, request, userId);
            case "TRANSFER" -> handleTransfer(task, request, userId);
            case "DELEGATE" -> handleDelegate(task, request, userId);
            case "RETURN" -> handleReturn(task, request, userId);
            default -> throw new PortalException("400", "不支持的操作类型: " + action);
        }
        
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
            fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "TaskProcessComponent.java:128", "message", "completeTask exit success", "data", java.util.Map.of("taskId", taskId), "timestamp", System.currentTimeMillis()).toString() + "\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
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
        String assignmentTarget = task.getAssignmentTarget();

        return switch (assignmentType) {
            case "CANDIDATE_USER" -> {
                // 候选用户任务，检查用户是否在候选用户列表中
                if (assignmentTarget != null && !assignmentTarget.isEmpty()) {
                    String[] candidateUsers = assignmentTarget.split(",");
                    for (String candidateUserId : candidateUsers) {
                        if (userId.equals(candidateUserId.trim())) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
            case "VIRTUAL_GROUP" -> isUserInVirtualGroup(userId, assignmentTarget);
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

        // 候选用户任务（未认领的情况）
        if ("CANDIDATE_USER".equals(assignmentType)) {
            // assignmentTarget 是逗号分隔的候选用户ID列表
            String assignmentTarget = task.getAssignmentTarget();
            if (assignmentTarget != null && !assignmentTarget.isEmpty()) {
                String[] candidateUsers = assignmentTarget.split(",");
                for (String candidateUserId : candidateUsers) {
                    if (userId.equals(candidateUserId.trim())) {
                        return true;
                    }
                }
            }
        }

        // 虚拟组任务（未认领的情况）
        // 对于 VIRTUAL_GROUP 类型，使用 assignmentTarget（虚拟组ID）而不是 assignee
        if ("VIRTUAL_GROUP".equals(assignmentType)) {
            String assignmentTarget = task.getAssignmentTarget();
            if (assignmentTarget != null && !assignmentTarget.isEmpty()) {
                return isUserInVirtualGroup(userId, assignmentTarget);
            } else if (assignee != null && !assignee.isEmpty()) {
                // 兼容旧数据：如果没有 assignmentTarget，使用 assignee
                return isUserInVirtualGroup(userId, assignee);
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
        
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
            fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "TaskProcessComponent.java:310", "message", "handleApproval entry", "data", java.util.Map.of("taskId", taskId, "userId", userId, "action", action), "timestamp", System.currentTimeMillis()).toString() + "\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }
        
        log.info("Using Flowable engine to complete task: {} with action: {}", taskId, action);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("action", action);
        variables.put("comment", request.getComment());
        if (request.getFormData() != null) {
            variables.putAll(request.getFormData());
        }
        
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
            fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "TaskProcessComponent.java:327", "message", "Before completeTask call", "data", java.util.Map.of("taskId", taskId, "variables", variables.toString()), "timestamp", System.currentTimeMillis()).toString() + "\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        
        Optional<Map<String, Object>> result = workflowEngineClient.completeTask(taskId, userId, action, variables);
        
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
            fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "TaskProcessComponent.java:329", "message", "After completeTask call", "data", java.util.Map.of("taskId", taskId, "resultIsEmpty", result.isEmpty()), "timestamp", System.currentTimeMillis()).toString() + "\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        
        if (result.isEmpty()) {
            log.error("Failed to complete task {}: workflow engine returned empty result", taskId);
            throw new PortalException("500", "完成任务失败: " + taskId);
        }
        
        try {
            Map<String, Object> responseBody = result.get();
            
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "B", "location", "TaskProcessComponent.java:336", "message", "Response body received", "data", java.util.Map.of("taskId", taskId, "responseBodyKeys", responseBody.keySet().toString(), "hasData", responseBody.containsKey("data")), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            log.debug("Workflow engine response for task {}: {}", taskId, responseBody);
            
            // 检查响应结构：可能是 {success: true, data: {...}} 或直接是 {success: true, ...}
            @SuppressWarnings("unchecked")
            Map<String, Object> data = responseBody.containsKey("data") && responseBody.get("data") instanceof Map
                ? (Map<String, Object>) responseBody.get("data")
                : responseBody;
            
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "B", "location", "TaskProcessComponent.java:343", "message", "Data extracted", "data", java.util.Map.of("taskId", taskId, "dataKeys", data.keySet().toString()), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            // 检查 success 字段（可能在顶层或 data 中）
            // 安全地转换 success 字段，支持 Boolean、String、Integer 等类型
            Object successObj = data.get("success") != null ? data.get("success") : responseBody.get("success");
            
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "C", "location", "TaskProcessComponent.java:347", "message", "Before convertToBoolean", "data", java.util.Map.of("taskId", taskId, "successObj", successObj != null ? successObj.toString() : "null", "successObjType", successObj != null ? successObj.getClass().getName() : "null"), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            Boolean success = convertToBoolean(successObj);
            
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "C", "location", "TaskProcessComponent.java:349", "message", "After convertToBoolean", "data", java.util.Map.of("taskId", taskId, "success", success), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            log.debug("Task {} completion success check: successObj={}, success={}", taskId, successObj, success);
            
            if (!Boolean.TRUE.equals(success)) {
                String message = data.get("message") != null ? String.valueOf(data.get("message")) 
                    : (responseBody.get("message") != null ? String.valueOf(responseBody.get("message")) : "完成任务失败");
                String errorCode = data.get("code") != null ? String.valueOf(data.get("code"))
                    : (responseBody.get("code") != null ? String.valueOf(responseBody.get("code")) : "500");
                
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                    fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "D", "location", "TaskProcessComponent.java:357", "message", "Throwing PortalException", "data", java.util.Map.of("taskId", taskId, "errorCode", errorCode, "message", message), "timestamp", System.currentTimeMillis()).toString() + "\n");
                    fw.close();
                } catch (Exception e) {}
                // #endregion
                
                log.error("Failed to complete task {}: {} - {}", taskId, errorCode, message);
                throw new PortalException(errorCode, message);
            }
            
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "E", "location", "TaskProcessComponent.java:360", "message", "Task completed successfully", "data", java.util.Map.of("taskId", taskId), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            log.info("Task {} completed via Flowable by user {} with action {}", taskId, userId, action);
        } catch (PortalException e) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "D", "location", "TaskProcessComponent.java:362", "message", "PortalException caught and rethrown", "data", java.util.Map.of("taskId", taskId, "code", e.getCode(), "message", e.getMessage()), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            throw e;
        } catch (Exception e) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "E", "location", "TaskProcessComponent.java:364", "message", "Unexpected exception caught", "data", java.util.Map.of("taskId", taskId, "exceptionType", e.getClass().getName(), "exceptionMessage", e.getMessage(), "stackTrace", java.util.Arrays.toString(e.getStackTrace()).substring(0, Math.min(500, java.util.Arrays.toString(e.getStackTrace()).length()))), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            log.error("Unexpected error processing workflow engine response for task {}: {}", taskId, e.getMessage(), e);
            throw new PortalException("500", "处理任务完成响应时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 安全地将对象转换为 Boolean
     */
    private Boolean convertToBoolean(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            String str = ((String) obj).trim().toLowerCase();
            return "true".equals(str) || "1".equals(str) || "yes".equals(str);
        }
        if (obj instanceof Number) {
            return ((Number) obj).intValue() != 0;
        }
        return false;
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
