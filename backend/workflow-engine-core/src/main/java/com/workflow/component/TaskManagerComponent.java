package com.workflow.component;

import com.workflow.aspect.AuditAspect.Auditable;
import com.workflow.dto.request.TaskAssignmentRequest;
import com.workflow.dto.request.TaskClaimRequest;
import com.workflow.dto.request.TaskDelegationRequest;
import com.workflow.dto.response.TaskAssignmentResult;
import com.workflow.dto.response.TaskListResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;

import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 任务管理组件
 * 负责多维度任务分配、查询、委托和完成功能
 * 支持用户、虚拟组、部门角色三种分配类型
 */
@Component
@Transactional
public class TaskManagerComponent {
    
    @Autowired
    private TaskService taskService;
    
    @Autowired
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    
    /**
     * 查询用户的待办任务（包括直接分配、委托、认领的任务）
     * 支持多维度任务分配类型
     */
    public TaskListResult getUserTasks(String userId, int page, int size) {
        try {
            // 验证参数
            validateUserId(userId);
            
            // 创建分页参数
            Pageable pageable = PageRequest.of(page, size, 
                Sort.by(Sort.Direction.DESC, "priority")
                    .and(Sort.by(Sort.Direction.ASC, "createdTime")));
            
            // 查询用户的直接待办任务
            Page<ExtendedTaskInfo> taskPage = extendedTaskInfoRepository
                .findUserTodoTasks(userId, pageable);
            
            // 转换为结果对象
            List<TaskListResult.TaskInfo> taskInfos = taskPage.getContent().stream()
                .map(this::convertToTaskInfo)
                .toList();
            
            return TaskListResult.builder()
                .tasks(taskInfos)
                .totalCount(taskPage.getTotalElements())
                .currentPage(page)
                .pageSize(size)
                .totalPages(taskPage.getTotalPages())
                .build();
                
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "查询用户待办任务失败: " + e.getMessage(), e);
        }
    }
    /**
     * 查询用户的所有可见任务（包括虚拟组和部门角色任务）
     */
    public TaskListResult getUserAllVisibleTasks(String userId, List<String> groupIds, 
                                               List<String> deptRoles, int page, int size) {
        try {
            // 验证参数
            validateUserId(userId);
            
            // 如果没有提供组和角色信息，则只查询直接任务
            if ((groupIds == null || groupIds.isEmpty()) && 
                (deptRoles == null || deptRoles.isEmpty())) {
                return getUserTasks(userId, page, size);
            }
            
            // 创建分页参数
            Pageable pageable = PageRequest.of(page, size, 
                Sort.by(Sort.Direction.DESC, "priority")
                    .and(Sort.by(Sort.Direction.ASC, "createdTime")));
            
            // 查询用户的所有可见任务
            Page<ExtendedTaskInfo> taskPage = extendedTaskInfoRepository
                .findUserAllVisibleTasks(userId, 
                    groupIds != null ? groupIds : Collections.emptyList(),
                    deptRoles != null ? deptRoles : Collections.emptyList(),
                    pageable);
            
            // 转换为结果对象
            List<TaskListResult.TaskInfo> taskInfos = taskPage.getContent().stream()
                .map(this::convertToTaskInfo)
                .toList();
            
            return TaskListResult.builder()
                .tasks(taskInfos)
                .totalCount(taskPage.getTotalElements())
                .currentPage(page)
                .pageSize(size)
                .totalPages(taskPage.getTotalPages())
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
     */
    public TaskAssignmentResult claimTask(String taskId, TaskClaimRequest request) {
        try {
            // 验证请求参数
            validateTaskClaimRequest(request);
            
            // 查找扩展任务信息
            ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务不存在", taskId))));
            
            // 验证认领权限
            validateClaimPermission(extendedTaskInfo, request.getClaimedBy());
            
            // 检查任务是否已完成
            if (extendedTaskInfo.isCompleted()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务已完成，无法认领", taskId)));
            }
            
            // 检查任务是否已被认领
            if (extendedTaskInfo.isClaimed()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务已被认领", taskId)));
            }
            
            // 执行认领操作
            extendedTaskInfo.claimTask(request.getClaimedBy());
            
            // 更新Flowable任务的分配人
            taskService.claim(taskId, request.getClaimedBy());
            
            // 保存扩展任务信息
            extendedTaskInfo = extendedTaskInfoRepository.save(extendedTaskInfo);
            
            // 发布任务认领事件
            publishTaskClaimEvent(extendedTaskInfo, request);
            
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
     * 完成任务（支持委托人代表原分配人完成）
     */
    public TaskAssignmentResult completeTask(String taskId, String userId, 
                                           java.util.Map<String, Object> variables) {
        try {
            // 验证参数
            validateUserId(userId);
            
            // 查找扩展任务信息
            ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务不存在", taskId))));
            
            // 验证完成权限
            validateCompletePermission(extendedTaskInfo, userId);
            
            // 检查任务是否已完成
            if (extendedTaskInfo.isCompleted()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务已完成", taskId)));
            }
            
            // 完成Flowable任务
            if (variables != null && !variables.isEmpty()) {
                taskService.complete(taskId, variables);
            } else {
                taskService.complete(taskId);
            }
            
            // 更新扩展任务信息
            extendedTaskInfo.completeTask(userId);
            extendedTaskInfo = extendedTaskInfoRepository.save(extendedTaskInfo);
            
            // 发布任务完成事件
            publishTaskCompleteEvent(extendedTaskInfo, userId, variables);
            
            return TaskAssignmentResult.success(
                taskId, 
                extendedTaskInfo.getAssignmentType(),
                extendedTaskInfo.getCurrentAssignee(),
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
     * 获取任务详情
     */
    public TaskListResult.TaskInfo getTaskInfo(String taskId) {
        try {
            // 查找扩展任务信息
            ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "任务不存在", taskId))));
            
            return convertToTaskInfo(extendedTaskInfo);
            
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR", 
                "查询任务详情失败: " + e.getMessage(), e);
        }
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
            case DEPT_ROLE:
                // 分配给部门角色，清除个人分配
                taskService.setAssignee(flowableTask.getId(), null);
                // 同样主要通过扩展表来管理部门角色分配
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
        boolean hasPermission = false;
        
        switch (task.getAssignmentType()) {
            case USER:
                // 如果任务直接分配给用户，只有该用户可以委托
                hasPermission = task.getAssignmentTarget().equals(delegatedBy);
                break;
            case VIRTUAL_GROUP:
                // 如果任务分配给虚拟组，虚拟组成员可以委托
                // 这里需要调用用户服务验证用户是否为虚拟组成员
                // 暂时简化处理，假设有权限
                hasPermission = true; // TODO: 实现虚拟组成员验证
                break;
            case DEPT_ROLE:
                // 如果任务分配给部门角色，该部门该角色的用户可以委托
                // 这里需要调用用户服务验证用户是否拥有该部门角色
                // 暂时简化处理，假设有权限
                hasPermission = true; // TODO: 实现部门角色验证
                break;
        }
        
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
        
        boolean hasPermission = false;
        
        switch (task.getAssignmentType()) {
            case VIRTUAL_GROUP:
                // 验证用户是否为虚拟组成员
                // 这里需要调用用户服务验证
                // 暂时简化处理，假设有权限
                hasPermission = true; // TODO: 实现虚拟组成员验证
                break;
            case DEPT_ROLE:
                // 验证用户是否拥有该部门角色
                // 这里需要调用用户服务验证
                // 暂时简化处理，假设有权限
                hasPermission = true; // TODO: 实现部门角色验证
                break;
        }
        
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
        boolean hasPermission = false;
        
        switch (task.getAssignmentType()) {
            case USER:
                hasPermission = task.getAssignmentTarget().equals(userId);
                break;
            case VIRTUAL_GROUP:
                // 虚拟组成员可以完成任务
                // 这里需要调用用户服务验证
                hasPermission = true; // TODO: 实现虚拟组成员验证
                break;
            case DEPT_ROLE:
                // 拥有该部门角色的用户可以完成任务
                // 这里需要调用用户服务验证
                hasPermission = true; // TODO: 实现部门角色验证
                break;
        }
        
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
        return TaskListResult.TaskInfo.builder()
            .taskId(extendedTaskInfo.getTaskId())
            .taskName(extendedTaskInfo.getTaskName())
            .taskDescription(extendedTaskInfo.getTaskDescription())
            .processInstanceId(extendedTaskInfo.getProcessInstanceId())
            .processDefinitionId(extendedTaskInfo.getProcessDefinitionId())
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