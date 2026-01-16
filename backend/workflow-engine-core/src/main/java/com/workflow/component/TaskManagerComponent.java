package com.workflow.component;

import com.workflow.aspect.AuditAspect.Auditable;
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

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
    
    /**
     * 查询用户的待办任务（包括直接分配、委托、认领的任务）
     * 支持多维度任务分配类型
     * 
     * 直接从 Flowable TaskService 查询任务，确保能看到所有任务
     * 包括未分配的任务（可以被任何人认领）
     */
    public TaskListResult getUserTasks(String userId, int page, int size) {
        try {
            // 验证参数
            validateUserId(userId);
            
            List<Task> allTasks = new ArrayList<>();
            
            // 1. 查询直接分配给用户的任务
            List<Task> assignedTasks = taskService.createTaskQuery()
                .taskAssignee(userId)
                .list();
            allTasks.addAll(assignedTasks);
            
            // 2. 查询用户是候选人的任务
            List<Task> candidateTasks = taskService.createTaskQuery()
                .taskCandidateUser(userId)
                .list();
            allTasks.addAll(candidateTasks);
            
            // 3. 查询未分配的任务（可以被任何人认领）
            List<Task> unassignedTasks = taskService.createTaskQuery()
                .taskUnassigned()
                .list();
            allTasks.addAll(unassignedTasks);
            
            // 去重并排序
            List<Task> uniqueTasks = allTasks.stream()
                .collect(java.util.stream.Collectors.toMap(
                    Task::getId, 
                    t -> t, 
                    (t1, t2) -> t1))
                .values()
                .stream()
                .sorted((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()))
                .toList();
            
            long totalCount = uniqueTasks.size();
            
            // 分页
            int start = page * size;
            int end = Math.min(start + size, uniqueTasks.size());
            List<Task> pagedTasks = start < uniqueTasks.size() 
                ? uniqueTasks.subList(start, end) 
                : Collections.emptyList();
            
            // 转换为结果对象
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
                "查询用户待办任务失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将 Flowable Task 转换为 TaskInfo
     */
    private TaskListResult.TaskInfo convertFlowableTaskToTaskInfo(Task task) {
        // 从 processDefinitionId 中提取 processDefinitionKey
        // 格式: key:version:uuid (例如: Process_PurchaseRequest:2:b550b1fe-f0b0-11f0-b82f-00ff197375e0)
        String processDefinitionId = task.getProcessDefinitionId();
        String processDefinitionKey = extractProcessDefinitionKey(processDefinitionId);
        
        // 获取流程定义名称
        String processDefinitionName = getProcessDefinitionName(processDefinitionId);
        
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
        
        return TaskListResult.TaskInfo.builder()
            .taskId(task.getId())
            .taskName(task.getName())
            .taskDescription(task.getDescription())
            .processInstanceId(task.getProcessInstanceId())
            .processDefinitionId(processDefinitionId)
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionName(processDefinitionName)
            .assignmentType(task.getAssignee() != null ? AssignmentType.USER : AssignmentType.VIRTUAL_GROUP)
            .assignmentTarget(task.getAssignee())
            .currentAssignee(currentAssignee)
            .currentAssigneeName(currentAssigneeName)
            .priority(task.getPriority())
            .status("PENDING")
            .createdTime(task.getCreateTime() != null ? 
                LocalDateTime.ofInstant(task.getCreateTime().toInstant(), java.time.ZoneId.systemDefault()) : null)
            .dueDate(task.getDueDate() != null ? 
                LocalDateTime.ofInstant(task.getDueDate().toInstant(), java.time.ZoneId.systemDefault()) : null)
            .formKey(task.getFormKey())
            .initiatorId(initiatorId)
            .initiatorName(initiatorName)
            .build();
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
            // 忽略异常，返回 null
        }
        return extractProcessDefinitionKey(processDefinitionId);
    }
    
    /**
     * 从 processDefinitionId 中提取 processDefinitionKey
     * 格式: key:version:uuid (例如: Process_PurchaseRequest:2:b550b1fe-f0b0-11f0-b82f-00ff197375e0)
     */
    private String extractProcessDefinitionKey(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        int colonIndex = processDefinitionId.indexOf(':');
        if (colonIndex > 0) {
            return processDefinitionId.substring(0, colonIndex);
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
                "按流程实例查询任务失败: " + e.getMessage(), e);
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
            // 验证参数
            validateUserId(userId);
            
            List<Task> allTasks = new ArrayList<>();
            
            // 1. 查询直接分配给用户的任务
            List<Task> assignedTasks = taskService.createTaskQuery()
                .taskAssignee(userId)
                .list();
            allTasks.addAll(assignedTasks);
            
            // 2. 查询用户是候选人的任务
            List<Task> candidateTasks = taskService.createTaskQuery()
                .taskCandidateUser(userId)
                .list();
            allTasks.addAll(candidateTasks);
            
            // 3. 查询用户所属组的任务
            if (groupIds != null && !groupIds.isEmpty()) {
                for (String groupId : groupIds) {
                    List<Task> groupTasks = taskService.createTaskQuery()
                        .taskCandidateGroup(groupId)
                        .list();
                    allTasks.addAll(groupTasks);
                }
            }
            
            // 4. 查询未分配的任务（没有 assignee 的任务）
            List<Task> unassignedTasks = taskService.createTaskQuery()
                .taskUnassigned()
                .list();
            allTasks.addAll(unassignedTasks);
            
            // 去重
            List<Task> uniqueTasks = allTasks.stream()
                .collect(java.util.stream.Collectors.toMap(
                    Task::getId, 
                    t -> t, 
                    (t1, t2) -> t1))
                .values()
                .stream()
                .sorted((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()))
                .toList();
            
            long totalCount = uniqueTasks.size();
            
            // 分页
            int start = page * size;
            int end = Math.min(start + size, uniqueTasks.size());
            List<Task> pagedTasks = start < uniqueTasks.size() 
                ? uniqueTasks.subList(start, end) 
                : Collections.emptyList();
            
            // 转换为结果对象
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
                "查询用户可见任务失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 分配任务（支持多种分配类型）
     */
    @Auditable(
        operationType = AuditOperationType.ASSIGN_TASK,
        resourceType = AuditResourceType.TASK,
        description = "分配任务",
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
                        "taskId", "任务不存在", taskId)));
            }
            
            // 检查任务是否已完成（通过查询历史任务）
            boolean isCompleted = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult() == null; // 如果运行时任务不存在，说明已完成
            
            if (isCompleted) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务已完成，无法重新分配", taskId)));
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
                "任务分配成功");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            return TaskAssignmentResult.failure(
                taskId, 
                request.getAssignmentType(), 
                request.getAssignmentTarget(),
                request.getOperatorUserId(),
                "任务分配失败: " + e.getMessage());
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
                        "taskId", "任务不存在", taskId))));
            
            // 验证委托权限
            validateDelegationPermission(extendedTaskInfo, request.getDelegatedBy());
            
            // 检查任务是否已完成
            if (extendedTaskInfo.isCompleted()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务已完成，无法委托", taskId)));
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
                "任务委托成功");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_DELEGATION_ERROR", 
                "任务委托失败: " + e.getMessage(), e);
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
                        "taskId", "任务不存在", taskId)));
            }
            
            // 检查任务是否已被认领（有 assignee）
            if (flowableTask.getAssignee() != null && !flowableTask.getAssignee().isEmpty()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务已被认领", taskId)));
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
                            "taskId", "任务已完成，无法认领", taskId)));
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
                "任务认领成功");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_CLAIM_ERROR", 
                "任务认领失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 取消认领任务
     */
    public TaskAssignmentResult unclaimTask(String taskId, String userId) {
        try {
            // 验证参数
            validateUserId(userId);
            
            // 查找扩展任务信息
            ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务不存在", taskId))));
            
            // 检查任务是否已完成
            if (extendedTaskInfo.isCompleted()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务已完成，无法取消认领", taskId)));
            }
            
            // 检查任务是否已被认领
            if (!extendedTaskInfo.isClaimed()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务未被认领", taskId)));
            }
            
            // 验证是否是当前认领人
            if (!userId.equals(extendedTaskInfo.getClaimedBy())) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "userId", "只有认领人才能取消认领", userId)));
            }
            
            // 执行取消认领操作
            extendedTaskInfo.unclaimTask();
            
            // 更新Flowable任务的分配人
            taskService.unclaim(taskId);
            
            // 保存扩展任务信息
            extendedTaskInfo = extendedTaskInfoRepository.save(extendedTaskInfo);
            
            return TaskAssignmentResult.success(
                taskId, 
                extendedTaskInfo.getAssignmentType(),
                extendedTaskInfo.getAssignmentTarget(),
                userId,
                "取消认领成功");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_UNCLAIM_ERROR", 
                "取消认领失败: " + e.getMessage(), e);
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
            
            // 查找扩展任务信息
            ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务不存在", taskId))));
            
            // 检查任务是否已完成
            if (extendedTaskInfo.isCompleted()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务已完成，无法转办", taskId)));
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
            
            // 更新Flowable任务的分配人
            taskService.setAssignee(taskId, toUserId);
            
            // 保存扩展任务信息
            extendedTaskInfo = extendedTaskInfoRepository.save(extendedTaskInfo);
            
            return TaskAssignmentResult.success(
                taskId, 
                AssignmentType.USER,
                toUserId,
                fromUserId,
                "任务转办成功");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_TRANSFER_ERROR", 
                "任务转办失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 完成任务（支持委托人代表原分配人完成）
     * 
     * 优先从 Flowable TaskService 查询任务，确保能完成所有任务
     * 即使任务没有在 ExtendedTaskInfo 表中也能完成
     */
    public TaskAssignmentResult completeTask(String taskId, String userId, 
                                           java.util.Map<String, Object> variables) {
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
                        "taskId", "任务不存在", taskId)));
            }
            
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
                            "taskId", "任务已完成", taskId)));
                }
            }
            
            // 完成Flowable任务
            if (variables != null && !variables.isEmpty()) {
                taskService.complete(taskId, variables);
            } else {
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
                publishTaskCompleteEvent(extendedTaskInfo, userId, variables);
            }
            
            return TaskAssignmentResult.success(
                taskId, 
                assignmentType,
                currentAssignee,
                userId,
                "任务完成成功");
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_COMPLETE_ERROR", 
                "任务完成失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 回退任务到指定的历史节点
     * 使用 Flowable 的 createChangeActivityStateBuilder 实现任务回退
     */
    @Auditable(
        operationType = AuditOperationType.RETURN_TASK,
        resourceType = AuditResourceType.TASK,
        description = "回退任务",
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
                        "taskId", "任务不存在", taskId)));
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
                        "targetActivityId", "目标节点不是有效的历史节点", targetActivityId)));
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
                "任务回退成功，已回退到节点: " + targetActivityId);
                
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_RETURN_ERROR", 
                "任务回退失败: " + e.getMessage(), e);
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
                        "taskId", "任务不存在", taskId)));
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
                "查询可回退节点失败: " + e.getMessage(), e);
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
                    "taskId", "任务不存在", taskId)));
            
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "查询任务详情失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从 Flowable Task 构建 TaskInfo
     */
    private TaskListResult.TaskInfo buildTaskInfoFromFlowableTask(Task task) {
        // 从 processDefinitionId 提取 processDefinitionKey
        String processDefinitionKey = null;
        String processDefinitionId = task.getProcessDefinitionId();
        if (processDefinitionId != null && processDefinitionId.contains(":")) {
            processDefinitionKey = processDefinitionId.substring(0, processDefinitionId.indexOf(':'));
        }
        
        // 获取流程定义名称
        String processDefinitionName = getProcessDefinitionName(processDefinitionId);
        
        // 确定分配类型：如果有 assignee，则为 USER 类型（包括认领后的任务）
        AssignmentType assignmentType = AssignmentType.USER;
        String assignmentTarget = task.getAssignee();
        
        if (task.getAssignee() == null || task.getAssignee().isEmpty()) {
            // 没有直接分配，检查候选人/候选组
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
        
        return TaskListResult.TaskInfo.builder()
            .taskId(task.getId())
            .taskName(task.getName())
            .taskDescription(task.getDescription())
            .processInstanceId(task.getProcessInstanceId())
            .processDefinitionId(processDefinitionId)
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionName(processDefinitionName)
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
            .status("ACTIVE")
            .initiatorId(initiatorId)
            .initiatorName(initiatorName)
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
                    "userId", "用户ID不能为空", userId)));
        }
    }
    
    /**
     * 验证任务分配请求
     */
    private void validateTaskAssignmentRequest(TaskAssignmentRequest request) {
        if (request == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", "请求参数不能为空", null)));
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
                    "request", "请求参数不能为空", null)));
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
                    "request", "请求参数不能为空", null)));
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
                    "request", "请求参数不能为空", null)));
        }
        
        if (!StringUtils.hasText(request.getTargetActivityId())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "targetActivityId", "目标节点ID不能为空", null)));
        }
        
        if (!StringUtils.hasText(request.getUserId())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "userId", "用户ID不能为空", null)));
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
                // 分配给虚拟组，清除个人分配
                taskService.setAssignee(flowableTask.getId(), null);
                // 这里可以设置候选组，但Flowable的候选组概念与我们的虚拟组不完全一致
                // 我们主要通过扩展表来管理虚拟组分配
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
                    "delegatedBy", "用户没有委托此任务的权限", delegatedBy)));
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
                    "taskId", "直接分配的任务不能被认领", task.getTaskId())));
        }
        
        // 验证用户是否有权限认领此任务
        boolean hasPermission = userPermissionService.hasTaskPermission(
                claimedBy, 
                task.getAssignmentType(), 
                task.getAssignmentTarget());
        
        if (!hasPermission) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "claimedBy", "用户没有认领此任务的权限", claimedBy)));
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
                        "userId", "用户没有完成此任务的权限", userId)));
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
                    "userId", "用户没有完成此任务的权限", userId)));
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
    // ==================== 事件发布方法 ====================
    
    /**
     * 发布任务分配事件
     */
    private void publishTaskAssignmentEvent(ExtendedTaskInfo task, TaskAssignmentRequest request) {
        // TODO: 实现事件发布逻辑
        // 这里应该发布到消息队列或事件总线，通知其他模块任务已分配
        System.out.println("任务分配事件: 任务 " + task.getTaskId() + 
                          " 已分配给 " + request.getAssignmentTarget() + 
                          " (类型: " + request.getAssignmentType() + ")");
    }
    
    /**
     * 发布任务委托事件
     */
    private void publishTaskDelegationEvent(ExtendedTaskInfo task, TaskDelegationRequest request) {
        // TODO: 实现事件发布逻辑
        System.out.println("任务委托事件: 任务 " + task.getTaskId() + 
                          " 已委托给 " + request.getDelegatedTo() + 
                          " (委托人: " + request.getDelegatedBy() + ")");
    }
    
    /**
     * 发布任务认领事件
     */
    private void publishTaskClaimEvent(ExtendedTaskInfo task, TaskClaimRequest request) {
        // TODO: 实现事件发布逻辑
        System.out.println("任务认领事件: 任务 " + task.getTaskId() + 
                          " 已被 " + request.getClaimedBy() + " 认领");
    }
    
    /**
     * 发布任务完成事件
     */
    private void publishTaskCompleteEvent(ExtendedTaskInfo task, String userId, 
                                        java.util.Map<String, Object> variables) {
        // TODO: 实现事件发布逻辑
        System.out.println("任务完成事件: 任务 " + task.getTaskId() + 
                          " 已被 " + userId + " 完成");
    }
    
    /**
     * 发布任务回退事件
     */
    private void publishTaskReturnEvent(String taskId, String processInstanceId, 
                                        String fromActivityId, String toActivityId,
                                        TaskReturnRequest request) {
        // TODO: 实现事件发布逻辑
        System.out.println("任务回退事件: 任务 " + taskId + 
                          " 从节点 " + fromActivityId + " 回退到节点 " + toActivityId +
                          " (操作人: " + request.getUserId() + ", 原因: " + request.getReason() + ")");
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
                "统计用户任务数量失败: " + e.getMessage(), e);
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
                "统计用户过期任务数量失败: " + e.getMessage(), e);
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
                "查询过期任务失败: " + e.getMessage(), e);
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
                "查询高优先级任务失败: " + e.getMessage(), e);
        }
    }
}