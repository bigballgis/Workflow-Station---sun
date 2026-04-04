package com.workflow.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.aspect.WorkflowAuditAspect.Auditable;
import com.workflow.client.AdminCenterClient;
import com.workflow.dto.request.TaskAssignmentRequest;
import com.workflow.dto.request.TaskClaimRequest;
import com.workflow.dto.request.TaskDelegationRequest;
import com.workflow.dto.request.TaskReturnRequest;
import com.workflow.dto.response.TaskAssignmentResult;
import com.workflow.dto.response.TaskListResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.UserPermissionService;

import com.platform.messaging.support.NotificationDispatchHelper;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 任务管理组件
 * 负责多维度任务分配、查询、委托和完成功能
 * 支持用户、虚拟组、部门角色三种分配类型
 */
@Slf4j
@Component
@Transactional
public class TaskManagerComponent {
    
    @Autowired
    private TaskService taskService;
    
    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private HistoryService historyService;
    
    @Autowired
    private RepositoryService repositoryService;
    
    @Autowired
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    
    @Autowired
    private UserPermissionService userPermissionService;
    
    @Autowired
    private AdminCenterClient adminCenterClient;
    
    @Autowired
    private BpmnActionParser bpmnActionParser;
    
    @Autowired
    private SubTableDataInjector subTableDataInjector;
    
    @Autowired
    private MultiInstanceDataResolver multiInstanceDataResolver;
    
    @Autowired
    private MultiInstanceCanceller multiInstanceCanceller;
    
    @Autowired(required = false)
    private com.workflow.messaging.SubTableUpdatePublisher updatePublisher;

    @Autowired
    private NotificationDispatchHelper notificationDispatchHelper;
    
    // ==================== 任务查询 ====================

    /**
     * 查询用户的待办任务（包括直接分配、候选人任务）
     * 支持多维度任务分配类型
     */
    public TaskListResult getUserTasks(String userId, int page, int size) {
        try {
            validateUserId(userId);
            
            int fetchLimit = (page + 1) * size;
            
            List<Task> assignedTasks = taskService.createTaskQuery()
                .taskAssignee(userId)
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);
            
            List<Task> candidateTasks = taskService.createTaskQuery()
                .taskCandidateUser(userId)
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);
            
            java.util.LinkedHashMap<String, Task> taskMap = new java.util.LinkedHashMap<>();
            for (Task t : assignedTasks) taskMap.putIfAbsent(t.getId(), t);
            for (Task t : candidateTasks) taskMap.putIfAbsent(t.getId(), t);
            mergeOrphanInitiatorTasksRepair(userId, fetchLimit, taskMap);
            
            List<Task> uniqueTasks = new ArrayList<>(taskMap.values());
            uniqueTasks.sort((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()));
            
            long totalCount;
            if (uniqueTasks.size() < fetchLimit) {
                totalCount = uniqueTasks.size();
            } else {
                long assignedCount = taskService.createTaskQuery()
                    .taskAssignee(userId).count();
                long candidateCount = taskService.createTaskQuery()
                    .taskCandidateUser(userId).count();
                totalCount = assignedCount + candidateCount;
            }
            
            int start = page * size;
            int end = Math.min(start + size, uniqueTasks.size());
            List<Task> pagedTasks = start < uniqueTasks.size() 
                ? uniqueTasks.subList(start, end) 
                : Collections.emptyList();
            
            List<TaskListResult.TaskInfo> taskInfos = pagedTasks.stream()
                .map(this::convertFlowableTaskToTaskInfo)
                .toList();
            
            int totalPages = (int) Math.ceil((double) totalCount / size);
            
            return TaskListResult.builder()
                .tasks(taskInfos)
                .totalCount(totalCount)
                .currentPage(page)
                .pageSize(size)
                .totalPages(totalPages)
                .build();
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query user pending tasks: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将 Flowable Task 转换为 TaskInfo（与详情查询共用逻辑，含候选人/候选组）
     */
    private TaskListResult.TaskInfo convertFlowableTaskToTaskInfo(Task task) {
        return buildTaskInfoFromFlowableTask(task);
    }
    
    /**
     * 解析用户显示名称
     * 优先返回 fullName，其次 displayName，再次 username，最后返回 userId
     */
    private String resolveUserDisplayName(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> userInfo = adminCenterClient.getUserInfo(userId);
            if (userInfo != null) {
                // 优先使用 fullName
                String fullName = (String) userInfo.get("fullName");
                if (fullName != null && !fullName.isEmpty()) {
                    return fullName;
                }
                // 其次使用 displayName
                String displayName = (String) userInfo.get("displayName");
                if (displayName != null && !displayName.isEmpty()) {
                    return displayName;
                }
                // 再次使用 username
                String username = (String) userInfo.get("username");
                if (username != null && !username.isEmpty()) {
                    return username;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve user display name for {}: {}", userId, e.getMessage());
        }
        return userId;
    }
    
    /**
     * 获取流程定义名称
     */
    private String getProcessDefinitionName(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        try {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
            if (processDefinition != null) {
                return processDefinition.getName();
            }
        } catch (Exception e) {
            log.warn("Failed to get process definition name for id: {}", processDefinitionId, e);
        }
        return extractProcessDefinitionKey(processDefinitionId);
    }
    
    /**
     * 从 processDefinitionId 中提取 processDefinitionKey
     * 格式: key:version:uuid (例如: Process_PurchaseRequest:2:b550b1fe-f0b0-11f0-b82f-00ff197375e0)
     * Flowable 7.0 可能只返回 UUID，此时查询 repositoryService 获取真实 key
     */
    private String extractProcessDefinitionKey(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        // 标准格式: key:version:uuid
        int colonIndex = processDefinitionId.indexOf(':');
        if (colonIndex > 0) {
            return processDefinitionId.substring(0, colonIndex);
        }
        // Flowable 7.0 可能仅返回 UUID，查询 repositoryService 获取真实 key
        try {
            org.flowable.engine.repository.ProcessDefinition pd = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
            if (pd != null) {
                log.debug("Resolved process definition key via repositoryService: {} -> {}", processDefinitionId, pd.getKey());
                return pd.getKey();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve process definition key for ID {}: {}", processDefinitionId, e.getMessage());
        }
        return processDefinitionId;
    }
    
    /**
     * 按流程实例ID查询任务
     */
    public TaskListResult getTasksByProcessInstance(String processInstanceId, int page, int size) {
        try {
            // 查询流程实例的所有任务
            List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime()
                .desc()
                .listPage(page * size, size);
            
            long totalCount = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .count();
            
            // 转换为结果对象
            List<TaskListResult.TaskInfo> taskInfos = tasks.stream()
                .map(this::convertFlowableTaskToTaskInfo)
                .toList();
            
            int totalPages = (int) Math.ceil((double) totalCount / size);
            
            return TaskListResult.builder()
                .tasks(taskInfos)
                .totalCount(totalCount)
                .currentPage(page)
                .pageSize(size)
                .totalPages(totalPages)
                .build();
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query tasks by process instance: " + e.getMessage(), e);
        }
    }
    /**
     * 查询用户的所有可见任务（包括虚拟组和部门角色任务）
     * 
     * 直接从 Flowable TaskService 查询任务
     */
    public TaskListResult getUserAllVisibleTasks(String userId, List<String> groupIds, 
                                               List<String> deptRoles, int page, int size) {
        try {
            validateUserId(userId);
            
            int fetchLimit = (page + 1) * size;
            
            List<Task> assignedTasks = taskService.createTaskQuery()
                .taskAssignee(userId)
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);
            
            List<Task> candidateTasks = taskService.createTaskQuery()
                .taskCandidateUser(userId)
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);
            
            java.util.LinkedHashMap<String, Task> taskMap = new java.util.LinkedHashMap<>();
            for (Task t : assignedTasks) taskMap.putIfAbsent(t.getId(), t);
            for (Task t : candidateTasks) taskMap.putIfAbsent(t.getId(), t);
            
            if (groupIds != null && !groupIds.isEmpty()) {
                List<Task> groupTasks = taskService.createTaskQuery()
                    .taskCandidateGroupIn(groupIds)
                    .orderByTaskCreateTime().desc()
                    .listPage(0, fetchLimit);
                for (Task t : groupTasks) taskMap.putIfAbsent(t.getId(), t);
            }
            mergeOrphanInitiatorTasksRepair(userId, fetchLimit, taskMap);
            
            List<Task> uniqueTasks = new ArrayList<>(taskMap.values());
            uniqueTasks.sort((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()));
            
            long totalCount;
            if (uniqueTasks.size() < fetchLimit) {
                totalCount = uniqueTasks.size();
            } else {
                long assignedCount = taskService.createTaskQuery()
                    .taskAssignee(userId).count();
                long candidateCount = taskService.createTaskQuery()
                    .taskCandidateUser(userId).count();
                totalCount = assignedCount + candidateCount;
                if (groupIds != null && !groupIds.isEmpty()) {
                    totalCount += taskService.createTaskQuery()
                        .taskCandidateGroupIn(groupIds).count();
                }
            }
            
            int start = page * size;
            int end = Math.min(start + size, uniqueTasks.size());
            List<Task> pagedTasks = start < uniqueTasks.size() 
                ? uniqueTasks.subList(start, end) 
                : Collections.emptyList();
            
            List<TaskListResult.TaskInfo> taskInfos = pagedTasks.stream()
                .map(this::convertFlowableTaskToTaskInfo)
                .toList();
            
            int totalPages = (int) Math.ceil((double) totalCount / size);
            
            return TaskListResult.builder()
                .tasks(taskInfos)
                .totalCount(totalCount)
                .currentPage(page)
                .pageSize(size)
                .totalPages(totalPages)
                .build();
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query user visible tasks: " + e.getMessage(), e);
        }
    }

    /**
     * 合并「未指派但流程变量 initiator 为当前用户」的任务，并幂等写回 assignee。
     * 覆盖监听器未执行、变量类型为 Long、或 assignee 未写入等导致 taskAssignee 查询不到的情况。
     */
    private void mergeOrphanInitiatorTasksRepair(String userId, int fetchLimit,
            java.util.LinkedHashMap<String, Task> taskMap) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        String uid = userId.trim();
        try {
            appendUnassignedInitiatorTasks(uid, fetchLimit, taskMap, false);
            if (uid.matches("^-?\\d+$")) {
                appendUnassignedInitiatorTasks(uid, fetchLimit, taskMap, true);
            }
        } catch (Exception e) {
            log.warn("mergeOrphanInitiatorTasksRepair for user {}: {}", uid, e.getMessage());
        }
    }

    private void appendUnassignedInitiatorTasks(String userId, int fetchLimit,
            java.util.LinkedHashMap<String, Task> taskMap, boolean initiatorVarAsLong) {
        var query = taskService.createTaskQuery()
                .taskUnassigned()
                .orderByTaskCreateTime().desc();
        if (initiatorVarAsLong) {
            query.processVariableValueEquals("initiator", Long.parseLong(userId));
        } else {
            query.processVariableValueEquals("initiator", userId);
        }
        List<Task> orphans = query.listPage(0, fetchLimit);
        for (Task t : orphans) {
            try {
                taskService.setAssignee(t.getId(), userId);
                Task refreshed = taskService.createTaskQuery().taskId(t.getId()).singleResult();
                if (refreshed != null) {
                    taskMap.putIfAbsent(refreshed.getId(), refreshed);
                }
            } catch (Exception ex) {
                log.warn("Could not repair assignee for orphan task {}: {}", t.getId(), ex.getMessage());
                taskMap.putIfAbsent(t.getId(), t);
            }
        }
    }
    
    // ==================== 任务分配、委托、认领 ====================

    /**
     * 分配任务（支持多种分配类型）
     */
    @Auditable(
        operationType = AuditOperationType.ASSIGN_TASK,
        resourceType = AuditResourceType.TASK,
        description = "Assign task",
        captureArgs = true,
        captureResult = true
    )
    public TaskAssignmentResult assignTask(String taskId, TaskAssignmentRequest request) {
        try {
            // 验证请求参数
            validateTaskAssignmentRequest(request);
            
            // 验证任务是否存在
            Task flowableTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }
            
            // 查找或创建扩展任务信息
            ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId)
                .orElse(createExtendedTaskInfo(flowableTask, request));
            
            // 更新分配信息
            updateTaskAssignment(extendedTaskInfo, request);
            
            // 根据分配类型更新Flowable任务
            updateFlowableTaskAssignment(flowableTask, request);
            
            // 保存扩展任务信息
            extendedTaskInfo = extendedTaskInfoRepository.save(extendedTaskInfo);
            
            // 发布任务分配事件
            publishTaskAssignmentEvent(extendedTaskInfo, request);
            
            return TaskAssignmentResult.success(
                taskId, 
                request.getAssignmentType(), 
                request.getAssignmentTarget(),
                request.getOperatorUserId(),
                "Task assigned successfully");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_ASSIGN_ERROR",
                "Task assignment failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 委托任务（任何分配类型的任务都可以被委托）
     */
    public TaskAssignmentResult delegateTask(String taskId, TaskDelegationRequest request) {
        try {
            // 验证请求参数
            validateTaskDelegationRequest(request);
            
            // 查找扩展任务信息
            ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId))));
            
            // 验证委托权限
            validateDelegationPermission(extendedTaskInfo, request.getDelegatedBy());
            
            // 检查任务是否已完成
            if (extendedTaskInfo.isCompleted()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task already completed, cannot delegate", taskId)));
            }
            
            // 执行委托操作
            extendedTaskInfo.delegateTask(
                request.getDelegatedTo(), 
                request.getDelegatedBy(), 
                request.getEffectiveDelegationReason());
            
            // 更新Flowable任务的分配人
            taskService.setAssignee(taskId, request.getDelegatedTo());
            
            // 保存扩展任务信息
            extendedTaskInfo = extendedTaskInfoRepository.save(extendedTaskInfo);
            
            // 发布任务委托事件
            publishTaskDelegationEvent(extendedTaskInfo, request);
            
            return TaskAssignmentResult.success(
                taskId, 
                AssignmentType.USER, // 委托后变为用户分配
                request.getDelegatedTo(),
                request.getDelegatedBy(),
                "Task delegated successfully");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_DELEGATION_ERROR", 
                "Task delegation failed: " + e.getMessage(), e);
        }
    }
    /**
     * 认领任务（虚拟组和部门角色任务）
     * 
     * 优先从 Flowable TaskService 查询任务，确保能认领所有任务
     * 即使任务没有在 ExtendedTaskInfo 表中也能认领
     */
    public TaskAssignmentResult claimTask(String taskId, TaskClaimRequest request) {
        try {
            // 验证请求参数
            validateTaskClaimRequest(request);
            
            // 首先从 Flowable 查询任务是否存在
            Task flowableTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }
            
            // 检查任务是否已被认领（有 assignee）
            if (flowableTask.getAssignee() != null && !flowableTask.getAssignee().isEmpty()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task already claimed", taskId)));
            }
            
            // 查找扩展任务信息（可选）
            Optional<ExtendedTaskInfo> extendedTaskInfoOpt = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
            
            // 如果有扩展任务信息，进行额外验证
            if (extendedTaskInfoOpt.isPresent()) {
                ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoOpt.get();
                
                // 验证认领权限
                validateClaimPermission(extendedTaskInfo, request.getClaimedBy());
                
                // 检查任务是否已完成
                if (extendedTaskInfo.isCompleted()) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "taskId", "Task already completed, cannot claim", taskId)));
                }
                
                // 执行认领操作
                extendedTaskInfo.claimTask(request.getClaimedBy());
                extendedTaskInfoRepository.save(extendedTaskInfo);
                
                // 发布任务认领事件
                publishTaskClaimEvent(extendedTaskInfo, request);
            }
            
            // 更新Flowable任务的分配人
            taskService.claim(taskId, request.getClaimedBy());
            
            return TaskAssignmentResult.success(
                taskId, 
                AssignmentType.USER, // 认领后变为用户分配
                request.getClaimedBy(),
                request.getClaimedBy(),
                "Task claimed successfully");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_CLAIM_ERROR", 
                "Task claim failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 取消认领任务
     * 与 {@link #claimTask} 对称：以 Flowable 运行时任务为准，扩展表为可选同步
     */
    public TaskAssignmentResult unclaimTask(String taskId, String userId) {
        try {
            validateUserId(userId);

            Task flowableTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }

            String assignee = flowableTask.getAssignee();
            if (assignee == null || assignee.isEmpty()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not claimed", taskId)));
            }
            if (!userId.equals(assignee)) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "userId", "Only assignee can unclaim", userId)));
            }

            Optional<ExtendedTaskInfo> extendedTaskInfoOpt = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);

            AssignmentType resultType = AssignmentType.CANDIDATE_USERS;
            String resultTarget = null;

            if (extendedTaskInfoOpt.isPresent()) {
                ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoOpt.get();
                if (extendedTaskInfo.isCompleted()) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "taskId", "Task already completed, cannot unclaim", taskId)));
                }
                if (extendedTaskInfo.isClaimed() && extendedTaskInfo.getClaimedBy() != null
                        && !userId.equals(extendedTaskInfo.getClaimedBy())) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "userId", "Only the claimer can unclaim", userId)));
                }
                if (extendedTaskInfo.isClaimed()) {
                    extendedTaskInfo.unclaimTask();
                    extendedTaskInfoRepository.save(extendedTaskInfo);
                }
                resultType = extendedTaskInfo.getAssignmentType();
                resultTarget = extendedTaskInfo.getAssignmentTarget();
            }

            taskService.unclaim(taskId);

            return TaskAssignmentResult.success(
                taskId,
                resultType,
                resultTarget,
                userId,
                "Task unclaimed successfully");

        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_UNCLAIM_ERROR",
                "Task unclaim failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 转办任务
     */
    public TaskAssignmentResult transferTask(String taskId, String fromUserId, String toUserId, String reason) {
        try {
            // 验证参数
            validateUserId(fromUserId);
            validateUserId(toUserId);
            
            // 首先从 Flowable 查询任务是否存在
            Task flowableTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }
            
            // 查找扩展任务信息（可选）
            Optional<ExtendedTaskInfo> extendedTaskInfoOpt = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
            
            if (extendedTaskInfoOpt.isPresent()) {
                ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoOpt.get();
                
                // 检查任务是否已完成
                if (extendedTaskInfo.isCompleted()) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "taskId", "Task already completed, cannot transfer", taskId)));
                }
                
                // 验证转办权限
                validateCompletePermission(extendedTaskInfo, fromUserId);
                
                // 执行转办操作 - 直接更改分配人
                extendedTaskInfo.setAssignmentType(AssignmentType.USER);
                extendedTaskInfo.setAssignmentTarget(toUserId);
                extendedTaskInfo.setClaimedBy(null);
                extendedTaskInfo.setClaimedTime(null);
                extendedTaskInfo.setDelegatedTo(null);
                extendedTaskInfo.setDelegatedBy(null);
                extendedTaskInfo.setDelegatedTime(null);
                extendedTaskInfo.setDelegationReason(null);
                extendedTaskInfo.updateStatus("ASSIGNED", fromUserId);
                extendedTaskInfoRepository.save(extendedTaskInfo);
            }
            
            // 更新Flowable任务的分配人
            taskService.setAssignee(taskId, toUserId);

            String taskLabel = flowableTask.getName() != null ? flowableTask.getName() : taskId;
            notificationDispatchHelper.publishToUserAfterCommit(
                    toUserId,
                    "TASK",
                    "任务已转办给您",
                    String.format("用户 %s 将任务「%s」转办给您。%s",
                            fromUserId,
                            taskLabel,
                            reason != null && !reason.isBlank() ? "原因：" + reason : "").trim(),
                    taskLink(taskId),
                    "workflow-engine");
            
            return TaskAssignmentResult.success(
                taskId, 
                AssignmentType.USER,
                toUserId,
                fromUserId,
                "Task transferred successfully");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_TRANSFER_ERROR", 
                "Task transfer failed: " + e.getMessage(), e);
        }
    }
    
    // ==================== 任务完成与回退 ====================

    /**
     * 完成任务（支持委托人代表原分配人完成）
     * 
     * 优先从 Flowable TaskService 查询任务，确保能完成所有任务
     * 即使任务没有在 ExtendedTaskInfo 表中也能完成
     */
    public TaskAssignmentResult completeTask(String taskId, String userId,
                                           java.util.Map<String, Object> variables) {
        return completeTask(taskId, userId, variables, true);
    }

    /**
     * 完成任务，并可选择是否向流程发起人推送站内信。
     */
    public TaskAssignmentResult completeTask(String taskId, String userId,
                                           java.util.Map<String, Object> variables,
                                           boolean sendNotification) {
        try {
            // 验证参数
            validateUserId(userId);
            
            // 首先从 Flowable 查询任务是否存在
            Task flowableTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }

            String taskDisplayName = flowableTask.getName() != null ? flowableTask.getName() : taskId;
            
            // 查找扩展任务信息（可选，用于记录额外信息）
            Optional<ExtendedTaskInfo> extendedTaskInfoOpt = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
            
            // 如果有扩展任务信息，验证权限和状态
            if (extendedTaskInfoOpt.isPresent()) {
                ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoOpt.get();
                
                // 验证完成权限
                validateCompletePermission(extendedTaskInfo, userId);
                
                // 检查任务是否已完成
                if (extendedTaskInfo.isCompleted()) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "taskId", "Task already completed", taskId)));
                }
                
                // 【多实例扩展】检测当前任务是否为多实例子任务，如果是则回写数据到子表
                if (isMultiInstanceSubTask(extendedTaskInfo)) {
                    log.info("检测到多实例子任务，准备回写数据到子表: taskId={}", taskId);
                    handleMultiInstanceSubTaskCompletion(taskId, variables, extendedTaskInfo);
                }
            }

            String processInstanceId = flowableTask.getProcessInstanceId();
            String initiatorUserId = resolveInitiatorUserId(processInstanceId);
            if (processInstanceId != null) {
                // 下一任务创建时 TaskAssignmentListener 读取，用于 CURRENT_BU_ROLE 等「当前处理人」语义
                runtimeService.setVariable(processInstanceId, "currentUserId", userId);
            }

            // 合并保留 initiator：门户完成首任务时传入的表单变量可能不含 initiator，避免后续 INITIATOR 节点无法解析受理人
            if (variables != null && !variables.isEmpty() && processInstanceId != null) {
                Object existingInitiator = runtimeService.getVariable(processInstanceId, "initiator");
                if (existingInitiator != null
                        && (variables.get("initiator") == null
                        || variables.get("initiator").toString().isBlank())) {
                    variables.put("initiator", existingInitiator);
                }
            }
            
            // 设置流程变量到流程实例（在完成任务之前）
            if (variables != null && !variables.isEmpty()) {
                if (processInstanceId != null) {
                    log.debug("Setting {} variable keys on process instance {} before completing task {}",
                        variables.size(), processInstanceId, taskId);
                    runtimeService.setVariables(processInstanceId, variables);
                }
            } else {
                log.debug("No variables provided for task completion. TaskId: {}, UserId: {}", taskId, userId);
            }
            
            // 【多实例扩展】检测下一节点是否为多实例子流程，如果是则注入子表数据
            String processDefinitionId = flowableTask.getProcessDefinitionId();
            String taskDefinitionKey = flowableTask.getTaskDefinitionKey();
            
            detectAndInjectMultiInstanceData(processInstanceId, processDefinitionId, taskDefinitionKey);
            
            // 完成Flowable任务
            if (variables != null && !variables.isEmpty()) {
                log.info("Completing task {} with variables: {}", taskId, variables);
                taskService.complete(taskId, variables);
            } else {
                log.info("Completing task {} without variables", taskId);
                taskService.complete(taskId);
            }
            
            // 更新扩展任务信息（如果存在）
            AssignmentType assignmentType = AssignmentType.USER;
            String currentAssignee = userId;
            
            if (extendedTaskInfoOpt.isPresent()) {
                ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoOpt.get();
                extendedTaskInfo.completeTask(userId);
                extendedTaskInfoRepository.save(extendedTaskInfo);
                assignmentType = extendedTaskInfo.getAssignmentType();
                currentAssignee = extendedTaskInfo.getCurrentAssignee();
                
                // 发布任务完成事件
                publishTaskCompleteEvent(extendedTaskInfo, userId, variables, sendNotification,
                        taskDisplayName, initiatorUserId, processInstanceId, taskId);
            } else if (sendNotification) {
                publishTaskCompleteEvent(null, userId, variables, true,
                        taskDisplayName, initiatorUserId, processInstanceId, taskId);
            }
            
            return TaskAssignmentResult.success(
                taskId, 
                assignmentType,
                currentAssignee,
                userId,
                "Task completed successfully");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (MultiInstanceDataResolver.OptimisticLockException e) {
            // 直接抛出乐观锁异常，不包装
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_COMPLETE_ERROR", 
                "Task completion failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 回退任务到指定的历史节点
     * 使用 Flowable 的 createChangeActivityStateBuilder 实现任务回退
     */
    @Auditable(
        operationType = AuditOperationType.RETURN_TASK,
        resourceType = AuditResourceType.TASK,
        description = "Return task",
        captureArgs = true,
        captureResult = true
    )
    public TaskAssignmentResult returnTask(String taskId, TaskReturnRequest request) {
        try {
            // 验证请求参数
            validateTaskReturnRequest(request);
            
            // 查找当前任务
            Task currentTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (currentTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }
            
            String processInstanceId = currentTask.getProcessInstanceId();
            String currentActivityId = currentTask.getTaskDefinitionKey();
            String targetActivityId = request.getTargetActivityId();
            
            // 验证目标节点是否为历史节点
            List<HistoricActivityInstance> historicActivities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(targetActivityId)
                .finished()
                .orderByHistoricActivityInstanceEndTime()
                .desc()
                .list();
            
            if (historicActivities.isEmpty()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "targetActivityId", "Target activity is not a valid historic activity", targetActivityId)));
            }
            
            // 检查回退目标是否在多实例子流程之前，如果是则级联取消多实例子任务
            if (isReturnTargetBeforeMultiInstance(processInstanceId, currentActivityId, targetActivityId)) {
                log.info("回退目标在多实例子流程之前，开始级联取消多实例子任务: processInstanceId={}, targetActivityId={}", 
                    processInstanceId, targetActivityId);
                multiInstanceCanceller.cancelMultiInstanceTasks(processInstanceId);
            }
            
            // 使用 Flowable 的 createChangeActivityStateBuilder 进行回退
            runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstanceId)
                .moveActivityIdTo(currentActivityId, targetActivityId)
                .changeState();
            
            // 查找扩展任务信息并更新状态
            ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId)
                .orElse(null);
            
            if (extendedTaskInfo != null) {
                extendedTaskInfo.updateStatus("RETURNED", request.getUserId());
                extendedTaskInfo.setIsDeleted(true);
                extendedTaskInfoRepository.save(extendedTaskInfo);
            }
            
            // 发布任务回退事件
            publishTaskReturnEvent(taskId, processInstanceId, currentActivityId, targetActivityId, request);
            
            return TaskAssignmentResult.success(
                taskId,
                AssignmentType.USER,
                targetActivityId,
                request.getUserId(),
                "Task returned successfully, returned to activity: " + targetActivityId);
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_RETURN_ERROR", 
                "Task return failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查回退目标是否在多实例子流程之前
     * 
     * 简化实现：通过历史执行时间判断
     * - 如果存在活跃的多实例子任务
     * - 且目标活动的最后完成时间早于多实例子任务的创建时间
     * - 则认为回退目标在多实例子流程之前
     * 
     * @param processInstanceId 流程实例 ID
     * @param currentActivityId 当前活动 ID
     * @param targetActivityId 目标活动 ID
     * @return 如果回退目标在多实例子流程之前返回 true
     */
    private boolean isReturnTargetBeforeMultiInstance(String processInstanceId, 
                                                      String currentActivityId, 
                                                      String targetActivityId) {
        try {
            // 查询流程实例中所有活跃的多实例子任务
            List<ExtendedTaskInfo> activeMultiInstanceTasks = extendedTaskInfoRepository
                .findByProcessInstanceIdAndIsDeletedFalse(processInstanceId)
                .stream()
                .filter(this::isMultiInstanceTask)
                .filter(task -> !"COMPLETED".equals(task.getStatus()) && !"CANCELLED".equals(task.getStatus()))
                .toList();
            
            if (activeMultiInstanceTasks.isEmpty()) {
                log.debug("流程实例 {} 中没有活跃的多实例子任务", processInstanceId);
                return false;
            }
            
            // 获取最早的多实例子任务创建时间
            LocalDateTime earliestMultiInstanceTaskTime = activeMultiInstanceTasks.stream()
                .map(ExtendedTaskInfo::getCreatedTime)
                .min(LocalDateTime::compareTo)
                .orElse(null);
            
            if (earliestMultiInstanceTaskTime == null) {
                log.warn("无法获取多实例子任务的创建时间");
                return false;
            }
            
            // 查询目标活动的最后完成时间
            List<HistoricActivityInstance> targetActivities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(targetActivityId)
                .finished()
                .orderByHistoricActivityInstanceEndTime()
                .desc()
                .list();
            
            if (targetActivities.isEmpty()) {
                log.warn("未找到目标活动的历史记录: {}", targetActivityId);
                return false;
            }
            
            // 获取目标活动的最后完成时间
            java.util.Date targetEndDate = targetActivities.get(0).getEndTime();
            if (targetEndDate == null) {
                log.warn("目标活动 {} 的完成时间为空", targetActivityId);
                return false;
            }
            
            LocalDateTime targetEndTime = LocalDateTime.ofInstant(
                targetEndDate.toInstant(), 
                java.time.ZoneId.systemDefault()
            );
            
            // 如果目标活动的完成时间早于多实例子任务的创建时间，则认为回退目标在多实例之前
            boolean isBeforeMultiInstance = targetEndTime.isBefore(earliestMultiInstanceTaskTime);
            
            if (isBeforeMultiInstance) {
                log.info("检测到回退目标 {} (完成时间: {}) 在多实例子流程 (创建时间: {}) 之前", 
                    targetActivityId, targetEndTime, earliestMultiInstanceTaskTime);
            }
            
            return isBeforeMultiInstance;
            
        } catch (Exception e) {
            log.error("检查回退目标是否在多实例子流程之前时发生异常: processInstanceId={}", processInstanceId, e);
            // 发生异常时保守处理，不执行级联取消
            return false;
        }
    }
    
    /**
     * 检查任务是否为多实例子任务
     */
    private boolean isMultiInstanceTask(ExtendedTaskInfo task) {
        String extendedProperties = task.getExtendedProperties();
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return false;
        }
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> properties = objectMapper.readValue(
                extendedProperties, 
                new TypeReference<Map<String, Object>>() {}
            );
            
            Object multiInstance = properties.get("multiInstance");
            return multiInstance != null && Boolean.TRUE.equals(multiInstance);
        } catch (Exception e) {
            log.warn("解析 extendedProperties 失败: taskId={}", task.getTaskId(), e);
            return false;
        }
    }
    
    /**
     * 获取可回退的历史节点列表
     */
    public List<TaskListResult.TaskInfo> getReturnableActivities(String taskId) {
        try {
            // 查找当前任务
            Task currentTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (currentTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }
            
            String processInstanceId = currentTask.getProcessInstanceId();
            
            // 查询历史用户任务节点
            List<HistoricActivityInstance> historicActivities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityType("userTask")
                .finished()
                .orderByHistoricActivityInstanceEndTime()
                .desc()
                .list();
            
            // 转换为任务信息列表（去重）
            List<TaskListResult.TaskInfo> returnableActivities = new ArrayList<>();
            java.util.Set<String> seenActivityIds = new java.util.HashSet<>();
            
            for (HistoricActivityInstance activity : historicActivities) {
                if (!seenActivityIds.contains(activity.getActivityId())) {
                    seenActivityIds.add(activity.getActivityId());
                    
                    TaskListResult.TaskInfo taskInfo = TaskListResult.TaskInfo.builder()
                        .taskId(activity.getActivityId())
                        .taskName(activity.getActivityName())
                        .processInstanceId(processInstanceId)
                        .status("COMPLETED")
                        .build();
                    
                    returnableActivities.add(taskInfo);
                }
            }
            
            return returnableActivities;
            
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query returnable activities: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取任务详情
     * 优先从 Flowable TaskService 查询，如果找不到再从扩展表查询
     */
    public TaskListResult.TaskInfo getTaskInfo(String taskId) {
        try {
            // 1. 首先尝试从 Flowable 直接查询任务
            Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if (task != null) {
                // 从 Flowable 任务构建 TaskInfo
                return buildTaskInfoFromFlowableTask(task);
            }
            
            // 2. 如果 Flowable 中没有，尝试从扩展表查询（可能是已完成的任务）
            Optional<ExtendedTaskInfo> extendedTaskInfoOpt = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
            
            if (extendedTaskInfoOpt.isPresent()) {
                return convertToTaskInfo(extendedTaskInfoOpt.get());
            }
            
            // 3. 都找不到，抛出异常
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "taskId", "Task not found", taskId)));
            
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query task details: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从 Flowable Task 构建 TaskInfo
     */
    private TaskListResult.TaskInfo buildTaskInfoFromFlowableTask(Task task) {
        // 从 processDefinitionId 提取 processDefinitionKey
        // Flowable 7.0 可能只返回 UUID，使用 extractProcessDefinitionKey 会自动查询 repositoryService
        String processDefinitionId = task.getProcessDefinitionId();
        String processDefinitionKey = extractProcessDefinitionKey(processDefinitionId);
        
        // 获取流程定义名称
        String processDefinitionName = getProcessDefinitionName(processDefinitionId);
        
        List<String> candidateUserIds = new ArrayList<>();
        List<String> candidateGroupIds = new ArrayList<>();
        for (IdentityLink link : taskService.getIdentityLinksForTask(task.getId())) {
            if (!"candidate".equals(link.getType())) {
                continue;
            }
            if (link.getUserId() != null && !link.getUserId().isBlank()) {
                candidateUserIds.add(link.getUserId());
            }
            if (link.getGroupId() != null && !link.getGroupId().isBlank()) {
                candidateGroupIds.add(link.getGroupId());
            }
        }

        AssignmentType assignmentType;
        String assignmentTarget;
        if (task.getAssignee() != null && !task.getAssignee().isEmpty()) {
            assignmentType = AssignmentType.USER;
            assignmentTarget = task.getAssignee();
        } else if (!candidateUserIds.isEmpty()) {
            assignmentType = AssignmentType.CANDIDATE_USERS;
            assignmentTarget = String.join(",", candidateUserIds);
        } else if (!candidateGroupIds.isEmpty()) {
            assignmentType = AssignmentType.VIRTUAL_GROUP;
            assignmentTarget = String.join(",", candidateGroupIds);
        } else {
            assignmentType = AssignmentType.VIRTUAL_GROUP;
            assignmentTarget = null;
        }
        
        // 获取流程发起人信息
        String initiatorId = null;
        String initiatorName = null;
        if (task.getProcessInstanceId() != null) {
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
            if (processInstance != null) {
                initiatorId = processInstance.getStartUserId();
                if (initiatorId != null) {
                    initiatorName = resolveUserDisplayName(initiatorId);
                }
            }
        }
        
        // 获取当前处理人名称
        String currentAssignee = task.getAssignee();
        String currentAssigneeName = null;
        if (currentAssignee != null && !currentAssignee.isEmpty()) {
            currentAssigneeName = resolveUserDisplayName(currentAssignee);
        }
        
        // 获取流程变量（用于表单数据绑定）
        Map<String, Object> variables = null;
        if (task.getProcessInstanceId() != null) {
            try {
                variables = runtimeService.getVariables(task.getProcessInstanceId());
                log.debug("Retrieved {} variables for task {}", 
                    variables != null ? variables.size() : 0, task.getId());
            } catch (Exception e) {
                log.warn("Failed to get variables for process instance {}: {}", 
                    task.getProcessInstanceId(), e.getMessage());
                variables = new HashMap<>();
            }
        }
        
        List<String> extractedActionIds = bpmnActionParser.extractActionIds(task);
        
        return TaskListResult.TaskInfo.builder()
            .taskId(task.getId())
            .taskName(task.getName())
            .taskDescription(task.getDescription())
            .processInstanceId(task.getProcessInstanceId())
            .processDefinitionId(processDefinitionId)
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionName(processDefinitionName)
            .taskDefinitionKey(task.getTaskDefinitionKey())
            .currentAssignee(currentAssignee)
            .currentAssigneeName(currentAssigneeName)
            .assignmentType(assignmentType)
            .assignmentTarget(assignmentTarget)
            .priority(task.getPriority())
            .createdTime(task.getCreateTime() != null ? 
                LocalDateTime.ofInstant(task.getCreateTime().toInstant(), java.time.ZoneId.systemDefault()) : null)
            .dueDate(task.getDueDate() != null ? 
                LocalDateTime.ofInstant(task.getDueDate().toInstant(), java.time.ZoneId.systemDefault()) : null)
            .formKey(task.getFormKey())
            .status("PENDING")
            .initiatorId(initiatorId)
            .initiatorName(initiatorName)
            .variables(variables)
            .candidateUserIds(candidateUserIds.isEmpty() ? null : candidateUserIds)
            .candidateGroupIds(candidateGroupIds.isEmpty() ? null : candidateGroupIds)
            .actionIds(extractedActionIds)
            .build();
    }
    
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 验证用户ID
     */
    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "userId", "User ID cannot be empty", userId)));
        }
    }
    
    /**
     * 验证任务分配请求
     */
    private void validateTaskAssignmentRequest(TaskAssignmentRequest request) {
        if (request == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", "Request parameters cannot be empty", null)));
        }
        
        if (!request.isValid()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", request.getValidationError(), null)));
        }
    }
    
    /**
     * 验证任务委托请求
     */
    private void validateTaskDelegationRequest(TaskDelegationRequest request) {
        if (request == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", "Request parameters cannot be empty", null)));
        }
        
        if (!request.isValid()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", request.getValidationError(), null)));
        }
    }
    
    /**
     * 验证任务认领请求
     */
    private void validateTaskClaimRequest(TaskClaimRequest request) {
        if (request == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", "Request parameters cannot be empty", null)));
        }
        
        if (!request.isValid()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", request.getValidationError(), null)));
        }
    }
    
    /**
     * 验证任务回退请求
     */
    private void validateTaskReturnRequest(TaskReturnRequest request) {
        if (request == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", "Request parameters cannot be empty", null)));
        }
        
        if (!StringUtils.hasText(request.getTargetActivityId())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "targetActivityId", "Target activity ID cannot be empty", null)));
        }
        
        if (!StringUtils.hasText(request.getUserId())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "userId", "User ID cannot be empty", null)));
        }
    }
    
    /**
     * 创建扩展任务信息
     */
    private ExtendedTaskInfo createExtendedTaskInfo(Task flowableTask, TaskAssignmentRequest request) {
        return ExtendedTaskInfo.builder()
            .taskId(flowableTask.getId())
            .processInstanceId(flowableTask.getProcessInstanceId())
            .processDefinitionId(flowableTask.getProcessDefinitionId())
            .taskDefinitionKey(flowableTask.getTaskDefinitionKey())
            .taskName(flowableTask.getName())
            .taskDescription(flowableTask.getDescription())
            .assignmentType(request.getAssignmentType())
            .assignmentTarget(request.getAssignmentTarget())
            .priority(request.getEffectivePriority())
            .dueDate(request.getDueDate())
            .formKey(flowableTask.getFormKey())
            .status("ASSIGNED")
            .createdTime(LocalDateTime.now())
            .createdBy(request.getOperatorUserId())
            .tenantId(request.getTenantId())
            .isDeleted(false)
            .version(0L)
            .build();
    }
    
    /**
     * 更新任务分配信息
     */
    private void updateTaskAssignment(ExtendedTaskInfo extendedTaskInfo, TaskAssignmentRequest request) {
        extendedTaskInfo.setAssignmentType(request.getAssignmentType());
        extendedTaskInfo.setAssignmentTarget(request.getAssignmentTarget());
        extendedTaskInfo.setPriority(request.getEffectivePriority());
        extendedTaskInfo.setDueDate(request.getDueDate());
        extendedTaskInfo.updateStatus("ASSIGNED", request.getOperatorUserId());
        
        // 清除之前的委托和认领信息
        extendedTaskInfo.setDelegatedTo(null);
        extendedTaskInfo.setDelegatedBy(null);
        extendedTaskInfo.setDelegatedTime(null);
        extendedTaskInfo.setDelegationReason(null);
        extendedTaskInfo.setClaimedBy(null);
        extendedTaskInfo.setClaimedTime(null);
    }
    
    /**
     * 更新Flowable任务分配
     */
    private void updateFlowableTaskAssignment(Task flowableTask, TaskAssignmentRequest request) {
        switch (request.getAssignmentType()) {
            case USER:
                // 直接分配给用户
                taskService.setAssignee(flowableTask.getId(), request.getAssignmentTarget());
                break;
            case VIRTUAL_GROUP:
            case CANDIDATE_USERS:
                // 分配给虚拟组或候选人池，清除个人分配
                taskService.setAssignee(flowableTask.getId(), null);
                break;
        }
        
        // 设置优先级和到期时间
        if (request.getPriority() != null) {
            taskService.setPriority(flowableTask.getId(), request.getPriority());
        }
        if (request.getDueDate() != null) {
            taskService.setDueDate(flowableTask.getId(), 
                java.sql.Timestamp.valueOf(request.getDueDate()));
        }
    }
    /**
     * 验证委托权限
     */
    private void validateDelegationPermission(ExtendedTaskInfo task, String delegatedBy) {
        // 验证委托人是否有权限委托此任务
        boolean hasPermission = userPermissionService.hasTaskPermission(
                delegatedBy, 
                task.getAssignmentType(), 
                task.getAssignmentTarget());
        
        if (!hasPermission) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "delegatedBy", "User does not have permission to delegate this task", delegatedBy)));
        }
    }
    
    /**
     * 验证认领权限
     */
    private void validateClaimPermission(ExtendedTaskInfo task, String claimedBy) {
        // 只有虚拟组和部门角色任务可以被认领
        if (task.getAssignmentType() == AssignmentType.USER) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "taskId", "Directly assigned tasks cannot be claimed", task.getTaskId())));
        }
        
        // 验证用户是否有权限认领此任务
        boolean hasPermission = userPermissionService.hasTaskPermission(
                claimedBy, 
                task.getAssignmentType(), 
                task.getAssignmentTarget());
        
        if (!hasPermission) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "claimedBy", "User does not have permission to claim this task", claimedBy)));
        }
    }
    
    /**
     * 验证完成权限
     */
    private void validateCompletePermission(ExtendedTaskInfo task, String userId) {
        String currentAssignee = task.getCurrentAssignee();
        
        // 如果任务有明确的当前处理人（委托人或认领人），只有该用户可以完成
        if (currentAssignee != null) {
            if (!currentAssignee.equals(userId)) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "userId", "User does not have permission to complete this task", userId)));
            }
            return;
        }
        
        // 如果没有明确的当前处理人，根据分配类型验证权限
        boolean hasPermission = userPermissionService.hasTaskPermission(
                userId, 
                task.getAssignmentType(), 
                task.getAssignmentTarget());
        
        if (!hasPermission) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "userId", "User does not have permission to complete this task", userId)));
        }
    }
    
    /**
     * 转换为任务信息DTO
     */
    private TaskListResult.TaskInfo convertToTaskInfo(ExtendedTaskInfo extendedTaskInfo) {
        // 获取流程定义名称
        String processDefinitionName = getProcessDefinitionName(extendedTaskInfo.getProcessDefinitionId());
        
        return TaskListResult.TaskInfo.builder()
            .taskId(extendedTaskInfo.getTaskId())
            .taskName(extendedTaskInfo.getTaskName())
            .taskDescription(extendedTaskInfo.getTaskDescription())
            .processInstanceId(extendedTaskInfo.getProcessInstanceId())
            .processDefinitionId(extendedTaskInfo.getProcessDefinitionId())
            .processDefinitionName(processDefinitionName)
            .assignmentType(extendedTaskInfo.getAssignmentType())
            .assignmentTarget(extendedTaskInfo.getAssignmentTarget())
            .currentAssignee(extendedTaskInfo.getCurrentAssignee())
            .priority(extendedTaskInfo.getPriority())
            .dueDate(extendedTaskInfo.getDueDate())
            .status(extendedTaskInfo.getStatus())
            .createdTime(extendedTaskInfo.getCreatedTime())
            .isDelegated(extendedTaskInfo.isDelegated())
            .isClaimed(extendedTaskInfo.isClaimed())
            .isOverdue(extendedTaskInfo.isOverdue())
            .formKey(extendedTaskInfo.getFormKey())
            .businessKey(extendedTaskInfo.getBusinessKey())
            .build();
    }
    // ==================== 事件发布方法（Kafka 站内信 → user-portal）====================
    
    private static String taskLink(String taskId) {
        return "/tasks/" + taskId;
    }

    private String resolveInitiatorUserId(String processInstanceId) {
        if (processInstanceId == null) {
            return null;
        }
        try {
            Object v = runtimeService.getVariable(processInstanceId, "initiator");
            return v != null ? v.toString() : null;
        } catch (Exception e) {
            log.debug("Could not read initiator for process {}: {}", processInstanceId, e.getMessage());
            return null;
        }
    }

    private void publishTaskAssignmentEvent(ExtendedTaskInfo task, TaskAssignmentRequest request) {
        log.info("Task assignment event: taskId={}, assignmentTarget={}, assignmentType={}",
                task.getTaskId(), request.getAssignmentTarget(), request.getAssignmentType());
        if (!request.shouldSendNotification()) {
            return;
        }
        if (request.getAssignmentType() != AssignmentType.USER) {
            return;
        }
        String targetUser = request.getAssignmentTarget();
        if (!StringUtils.hasText(targetUser)) {
            return;
        }
        String label = task.getTaskName() != null ? task.getTaskName() : task.getTaskId();
        notificationDispatchHelper.publishToUserAfterCommit(
                targetUser.trim(),
                "TASK",
                "新任务分配",
                String.format("您有新的待办任务「%s」。操作人：%s", label, request.getOperatorUserId()),
                taskLink(task.getTaskId()),
                "workflow-engine");
    }
    
    private void publishTaskDelegationEvent(ExtendedTaskInfo task, TaskDelegationRequest request) {
        log.info("Task delegation event: taskId={}, delegatedTo={}, delegatedBy={}",
                task.getTaskId(), request.getDelegatedTo(), request.getDelegatedBy());
        if (!request.shouldSendNotification()) {
            return;
        }
        String label = task.getTaskName() != null ? task.getTaskName() : task.getTaskId();
        notificationDispatchHelper.publishToUserAfterCommit(
                request.getDelegatedTo(),
                "TASK",
                "任务已委托给您",
                String.format("用户 %s 将任务「%s」委托给您。%s",
                        request.getDelegatedBy(),
                        label,
                        request.getEffectiveDelegationReason() != null
                                ? "说明：" + request.getEffectiveDelegationReason()
                                : "").trim(),
                taskLink(task.getTaskId()),
                "workflow-engine");
    }
    
    private void publishTaskClaimEvent(ExtendedTaskInfo task, TaskClaimRequest request) {
        log.info("Task claim event: taskId={}, claimedBy={}",
                task.getTaskId(), request.getClaimedBy());
        // 认领人即操作者本人，不向本人发站内信以免噪音
    }
    
    private void publishTaskCompleteEvent(ExtendedTaskInfo task, String userId,
                                        java.util.Map<String, Object> variables,
                                        boolean sendNotification,
                                        String taskDisplayName,
                                        String initiatorUserId,
                                        String processInstanceId,
                                        String flowableTaskId) {
        String tid = task != null ? task.getTaskId() : flowableTaskId;
        log.info("Task completion event: taskId={}, completedBy={}", tid, userId);
        if (!sendNotification || !StringUtils.hasText(initiatorUserId) || initiatorUserId.equals(userId)) {
            return;
        }
        String label = taskDisplayName != null ? taskDisplayName : tid;
        String link = StringUtils.hasText(tid) ? taskLink(tid) : "/tasks";
        notificationDispatchHelper.publishToUserAfterCommit(
                initiatorUserId,
                "TASK",
                "任务已处理",
                String.format("用户 %s 已完成任务「%s」。", userId, label),
                link,
                "workflow-engine");
    }
    
    private void publishTaskReturnEvent(String taskId, String processInstanceId,
                                        String fromActivityId, String toActivityId,
                                        TaskReturnRequest request) {
        log.info("Task return event: taskId={}, from={}, to={}, userId={}, reason={}",
                taskId, fromActivityId, toActivityId, request.getUserId(), request.getReason());
        if (!request.shouldSendNotification()) {
            return;
        }
        String initiator = resolveInitiatorUserId(processInstanceId);
        if (!StringUtils.hasText(initiator)) {
            return;
        }
        notificationDispatchHelper.publishToUserAfterCommit(
                initiator,
                "PROCESS",
                "流程已回退",
                String.format("流程实例 %s 已由 %s 从节点 %s 回退至 %s。%s",
                        processInstanceId,
                        request.getUserId(),
                        fromActivityId,
                        toActivityId,
                        request.getReason() != null ? "原因：" + request.getReason() : "").trim(),
                "/tasks",
                "workflow-engine");
    }
    
    // ==================== 统计查询方法 ====================
    
    /**
     * 统计用户的任务数量
     */
    public long countUserTasks(String userId) {
        try {
            validateUserId(userId);
            return extendedTaskInfoRepository.countUserTodoTasks(userId);
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_COUNT_ERROR", 
                "Failed to count user tasks: " + e.getMessage(), e);
        }
    }
    
    /**
     * 统计用户的过期任务数量
     */
    public long countUserOverdueTasks(String userId) {
        try {
            validateUserId(userId);
            return extendedTaskInfoRepository.countUserOverdueTasks(userId, LocalDateTime.now());
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_COUNT_ERROR", 
                "Failed to count user overdue tasks: " + e.getMessage(), e);
        }
    }
    
    /**
     * 查询过期任务
     */
    public List<TaskListResult.TaskInfo> getOverdueTasks() {
        try {
            List<ExtendedTaskInfo> overdueTasks = extendedTaskInfoRepository
                .findOverdueTasks(LocalDateTime.now());
            
            return overdueTasks.stream()
                .map(this::convertToTaskInfo)
                .toList();
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query overdue tasks: " + e.getMessage(), e);
        }
    }
    
    /**
     * 查询高优先级任务
     */
    public List<TaskListResult.TaskInfo> getHighPriorityTasks(int minPriority) {
        try {
            List<ExtendedTaskInfo> highPriorityTasks = extendedTaskInfoRepository
                .findHighPriorityTasks(minPriority);
            
            return highPriorityTasks.stream()
                .map(this::convertToTaskInfo)
                .toList();
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "Failed to query high priority tasks: " + e.getMessage(), e);
        }
    }
    
    // ==================== 多实例子流程支持方法 ====================
    
    /**
     * 检测当前任务是否为多实例子任务
     * 通过 extendedProperties 中的 multiInstance 标记判断
     */
    private boolean isMultiInstanceSubTask(ExtendedTaskInfo extendedTaskInfo) {
        String extendedProperties = extendedTaskInfo.getExtendedProperties();
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return false;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> props = objectMapper.readValue(extendedProperties, Map.class);
            Object multiInstance = props.get("multiInstance");
            return multiInstance != null && Boolean.TRUE.equals(multiInstance);
        } catch (Exception e) {
            log.warn("解析 extendedProperties 失败: taskId={}", extendedTaskInfo.getTaskId(), e);
            return false;
        }
    }
    
    /**
     * 处理多实例子任务完成时的数据回写
     * 调用 MultiInstanceDataResolver 将表单数据回写到子表
     */
    private void handleMultiInstanceSubTaskCompletion(String taskId, Map<String, Object> variables, 
                                                      ExtendedTaskInfo extendedTaskInfo) {
        try {
            // 从 variables 中提取表单数据和 rowVersion
            if (variables == null || variables.isEmpty()) {
                log.warn("多实例子任务完成但未提供表单数据: taskId={}", taskId);
                return;
            }
            
            Object formDataObj = variables.get("formData");
            Object rowVersionObj = variables.get("rowVersion");
            
            if (formDataObj == null) {
                log.warn("多实例子任务完成但未提供 formData: taskId={}", taskId);
                return;
            }
            
            Map<String, Object> formData = (Map<String, Object>) formDataObj;
            Long rowVersion = rowVersionObj != null ? 
                ((Number) rowVersionObj).longValue() : 1L;
            
            log.info("调用 MultiInstanceDataResolver 回写数据: taskId={}, rowVersion={}", 
                taskId, rowVersion);
            
            multiInstanceDataResolver.writeBackSubTableRow(taskId, formData, rowVersion);
            
            log.info("多实例子任务数据回写成功: taskId={}", taskId);
            
            // 发布 WebSocket 更新通知
            publishMultiInstanceWebSocketUpdate(taskId, extendedTaskInfo);
            
        } catch (MultiInstanceDataResolver.OptimisticLockException e) {
            log.error("多实例子任务数据回写失败（乐观锁冲突）: taskId={}", taskId, e);
            // 直接抛出乐观锁异常，不包装
            throw e;
        } catch (WorkflowValidationException e) {
            // 直接抛出验证异常，不包装
            throw e;
        } catch (Exception e) {
            log.error("多实例子任务数据回写失败: taskId={}", taskId, e);
            throw new WorkflowBusinessException("MULTI_INSTANCE_DATA_WRITEBACK_ERROR", 
                "多实例子任务数据回写失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 发布多实例子任务 WebSocket 更新通知
     */
    private void publishMultiInstanceWebSocketUpdate(String taskId, ExtendedTaskInfo extendedTaskInfo) {
        if (updatePublisher == null) {
            return;
        }
        
        try {
            // 从 extendedProperties 中提取 rowId 和主任务 ID
            String extendedProperties = extendedTaskInfo.getExtendedProperties();
            if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
                return;
            }
            
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> props = objectMapper.readValue(extendedProperties, Map.class);
            
            Object rowIdObj = props.get("subTableRowId");
            if (rowIdObj == null) {
                log.warn("无法从 extendedProperties 中获取 subTableRowId: taskId={}", taskId);
                return;
            }
            
            Long rowId = ((Number) rowIdObj).longValue();
            
            // 获取主任务 ID（从流程实例中查找前置任务）
            String processInstanceId = extendedTaskInfo.getProcessInstanceId();
            String mainTaskId = findMainTaskIdForMultiInstance(processInstanceId);
            
            if (mainTaskId != null) {
                updatePublisher.publishUpdate(mainTaskId, rowId, null, "COMPLETED");
                log.debug("WebSocket 更新通知已发布: mainTaskId={}, rowId={}", mainTaskId, rowId);
            }
            
        } catch (Exception e) {
            // WebSocket 发布失败不应影响主流程
            log.warn("发布 WebSocket 更新通知失败: taskId={}", taskId, e);
        }
    }
    
    /**
     * 查找多实例子流程的主任务 ID
     * 通过查询流程实例的历史任务，找到多实例子流程之前的任务
     */
    private String findMainTaskIdForMultiInstance(String processInstanceId) {
        try {
            // 查询流程实例的所有历史任务，按创建时间倒序
            List<HistoricActivityInstance> activities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityType("userTask")
                .orderByHistoricActivityInstanceStartTime()
                .desc()
                .list();
            
            // 找到第一个非多实例子任务（即主任务）
            for (HistoricActivityInstance activity : activities) {
                String taskId = activity.getTaskId();
                if (taskId != null) {
                    Optional<ExtendedTaskInfo> extInfoOpt = extendedTaskInfoRepository
                        .findByTaskIdAndIsDeletedFalse(taskId);
                    
                    if (extInfoOpt.isPresent()) {
                        ExtendedTaskInfo extInfo = extInfoOpt.get();
                        if (!isMultiInstanceSubTask(extInfo)) {
                            return taskId;
                        }
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("查找主任务 ID 失败: processInstanceId={}", processInstanceId, e);
            return null;
        }
    }
    
    /**
     * 检测下一节点是否为多实例子流程，如果是则注入子表数据
     * 
     * 实现逻辑：
     * 1. 获取当前任务的 BPMN 模型
     * 2. 查找当前任务的出口连线（outgoing flows）
     * 3. 遍历出口连线的目标节点，检测是否为多实例子流程
     * 4. 如果是多实例子流程，从扩展属性中提取子表配置
     * 5. 调用 SubTableDataInjector 注入子表数据
     */
    private void detectAndInjectMultiInstanceData(String processInstanceId, 
                                                  String processDefinitionId, 
                                                  String taskDefinitionKey) {
        try {
            log.debug("检测下一节点是否为多实例子流程: processInstanceId={}, taskDefinitionKey={}", 
                processInstanceId, taskDefinitionKey);
            
            // 1. 获取 BPMN 模型
            org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
            if (bpmnModel == null) {
                log.warn("无法获取 BPMN 模型: processDefinitionId={}", processDefinitionId);
                return;
            }
            
            // 2. 获取当前任务节点
            org.flowable.bpmn.model.FlowElement currentElement = bpmnModel.getFlowElement(taskDefinitionKey);
            if (currentElement == null) {
                log.warn("无法找到当前任务节点: taskDefinitionKey={}", taskDefinitionKey);
                return;
            }
            
            if (!(currentElement instanceof org.flowable.bpmn.model.UserTask)) {
                log.debug("当前节点不是 UserTask: taskDefinitionKey={}", taskDefinitionKey);
                return;
            }
            
            org.flowable.bpmn.model.UserTask userTask = (org.flowable.bpmn.model.UserTask) currentElement;
            
            // 3. 获取出口连线
            List<org.flowable.bpmn.model.SequenceFlow> outgoingFlows = userTask.getOutgoingFlows();
            if (outgoingFlows == null || outgoingFlows.isEmpty()) {
                log.debug("当前任务没有出口连线: taskDefinitionKey={}", taskDefinitionKey);
                return;
            }
            
            // 4. 遍历出口连线，检测目标节点是否为多实例子流程
            for (org.flowable.bpmn.model.SequenceFlow flow : outgoingFlows) {
                String targetRef = flow.getTargetRef();
                org.flowable.bpmn.model.FlowElement targetElement = bpmnModel.getFlowElement(targetRef);
                
                if (targetElement instanceof org.flowable.bpmn.model.SubProcess) {
                    org.flowable.bpmn.model.SubProcess subProcess = 
                        (org.flowable.bpmn.model.SubProcess) targetElement;
                    
                    // 检测是否为多实例子流程
                    org.flowable.bpmn.model.MultiInstanceLoopCharacteristics loopCharacteristics = 
                        subProcess.getLoopCharacteristics();
                    
                    if (loopCharacteristics != null) {
                        log.info("检测到多实例子流程: subProcessId={}, processInstanceId={}", 
                            subProcess.getId(), processInstanceId);
                        
                        // 5. 提取多实例配置并注入数据
                        injectMultiInstanceSubTableData(processInstanceId, subProcess, loopCharacteristics);
                        
                        // 只处理第一个多实例子流程
                        return;
                    }
                }
            }
            
            log.debug("下一节点不是多实例子流程: taskDefinitionKey={}", taskDefinitionKey);
            
        } catch (Exception e) {
            log.error("检测多实例子流程失败: processInstanceId={}, taskDefinitionKey={}", 
                processInstanceId, taskDefinitionKey, e);
            // 不抛出异常，避免影响任务完成流程
        }
    }
    
    /**
     * 注入多实例子表数据
     * 从子流程的扩展属性中提取子表配置，调用 SubTableDataInjector 注入数据
     */
    private void injectMultiInstanceSubTableData(String processInstanceId, 
                                                 org.flowable.bpmn.model.SubProcess subProcess,
                                                 org.flowable.bpmn.model.MultiInstanceLoopCharacteristics loopCharacteristics) {
        try {
            // 从 loopCharacteristics 的扩展元素中获取 collection 和 elementVariable
            Map<String, List<org.flowable.bpmn.model.ExtensionElement>> extensionElements = 
                loopCharacteristics.getExtensionElements();
            
            String collectionVariableName = null;
            
            if (extensionElements != null) {
                List<org.flowable.bpmn.model.ExtensionElement> collectionElements = 
                    extensionElements.get("collection");
                if (collectionElements != null && !collectionElements.isEmpty()) {
                    collectionVariableName = collectionElements.get(0).getElementText();
                }
            }
            
            if (collectionVariableName == null || collectionVariableName.trim().isEmpty()) {
                log.warn("多实例子流程缺少 collection 配置: subProcessId={}", subProcess.getId());
                return;
            }
            
            log.info("多实例子流程 collection 变量名: {}", collectionVariableName);
            
            // 从子流程内部的 UserTask 中提取子表配置
            List<org.flowable.bpmn.model.FlowElement> flowElements = 
                (List<org.flowable.bpmn.model.FlowElement>) subProcess.getFlowElements();
            
            for (org.flowable.bpmn.model.FlowElement element : flowElements) {
                if (element instanceof org.flowable.bpmn.model.UserTask) {
                    org.flowable.bpmn.model.UserTask userTask = 
                        (org.flowable.bpmn.model.UserTask) element;
                    
                    // 从 UserTask 的扩展属性中提取子表配置
                    Map<String, Object> subTableConfig = extractSubTableConfig(userTask);
                    
                    if (subTableConfig != null && !subTableConfig.isEmpty()) {
                        String subTableName = (String) subTableConfig.get("subTableName");
                        String assigneeField = (String) subTableConfig.get("assigneeField");
                        String foreignKeyField = (String) subTableConfig.get("foreignKeyField");
                        Long mainRecordId = (Long) subTableConfig.get("mainRecordId");
                        
                        if (subTableName != null && assigneeField != null) {
                            log.info("准备注入子表数据: subTableName={}, assigneeField={}, collectionVar={}", 
                                subTableName, assigneeField, collectionVariableName);
                            
                            // 从流程变量中获取主表记录 ID（如果配置中没有）
                            if (mainRecordId == null) {
                                mainRecordId = getMainRecordIdFromProcessVariables(processInstanceId);
                            }
                            
                            // 从流程变量中获取外键字段名（如果配置中没有）
                            if (foreignKeyField == null) {
                                foreignKeyField = "main_record_id"; // 默认外键字段名
                            }
                            
                            // 调用 SubTableDataInjector 注入数据
                            subTableDataInjector.injectSubTableData(
                                processInstanceId,
                                subTableName,
                                foreignKeyField,
                                mainRecordId,
                                assigneeField,
                                collectionVariableName
                            );
                            
                            log.info("子表数据注入成功: processInstanceId={}, subTableName={}", 
                                processInstanceId, subTableName);
                            
                            // 只处理第一个 UserTask 的配置
                            return;
                        }
                    }
                }
            }
            
            log.warn("多实例子流程中未找到子表配置: subProcessId={}", subProcess.getId());
            
        } catch (Exception e) {
            log.error("注入多实例子表数据失败: processInstanceId={}, subProcessId={}", 
                processInstanceId, subProcess.getId(), e);
            throw new WorkflowBusinessException("MULTI_INSTANCE_DATA_INJECTION_ERROR", 
                "注入多实例子表数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从 UserTask 的扩展属性中提取子表配置
     * 查找 custom:properties 中的 subTableName、assigneeField 等属性
     */
    private Map<String, Object> extractSubTableConfig(org.flowable.bpmn.model.UserTask userTask) {
        Map<String, Object> config = new HashMap<>();
        
        Map<String, List<org.flowable.bpmn.model.ExtensionElement>> extensionElements = 
            userTask.getExtensionElements();
        
        if (extensionElements == null || extensionElements.isEmpty()) {
            return config;
        }
        
        // 查找 custom:properties 元素
        List<org.flowable.bpmn.model.ExtensionElement> propertiesElements = 
            extensionElements.get("properties");
        
        if (propertiesElements == null || propertiesElements.isEmpty()) {
            return config;
        }
        
        for (org.flowable.bpmn.model.ExtensionElement propertiesElement : propertiesElements) {
            List<org.flowable.bpmn.model.ExtensionElement> propertyElements = 
                propertiesElement.getChildElements().get("property");
            
            if (propertyElements != null) {
                for (org.flowable.bpmn.model.ExtensionElement propertyElement : propertyElements) {
                    String name = propertyElement.getAttributeValue(null, "name");
                    String value = propertyElement.getAttributeValue(null, "value");
                    
                    if (name != null && value != null) {
                        config.put(name, value);
                    }
                }
            }
        }
        
        return config;
    }
    
    /**
     * 从流程变量中获取主表记录 ID
     * 通常主表记录 ID 存储在流程变量 "mainRecordId" 或 "businessKey" 中
     */
    private Long getMainRecordIdFromProcessVariables(String processInstanceId) {
        try {
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            
            // 尝试从 mainRecordId 变量获取
            Object mainRecordIdObj = variables.get("mainRecordId");
            if (mainRecordIdObj != null) {
                return ((Number) mainRecordIdObj).longValue();
            }
            
            // 尝试从 businessKey 获取
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
            
            if (processInstance != null && processInstance.getBusinessKey() != null) {
                try {
                    return Long.parseLong(processInstance.getBusinessKey());
                } catch (NumberFormatException e) {
                    log.warn("无法将 businessKey 转换为 Long: {}", processInstance.getBusinessKey());
                }
            }
            
            log.warn("无法从流程变量中获取主表记录 ID: processInstanceId={}", processInstanceId);
            return null;
            
        } catch (Exception e) {
            log.error("获取主表记录 ID 失败: processInstanceId={}", processInstanceId, e);
            return null;
        }
    }
}