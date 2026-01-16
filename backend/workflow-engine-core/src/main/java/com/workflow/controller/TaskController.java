package com.workflow.controller;

import com.workflow.component.HistoryManagerComponent;
import com.workflow.component.TaskManagerComponent;
import com.workflow.dto.request.HistoryQueryRequest;
import com.workflow.dto.request.TaskAssignmentRequest;
import com.workflow.dto.request.TaskClaimRequest;
import com.workflow.dto.request.TaskDelegationRequest;
import com.workflow.dto.request.TaskReturnRequest;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.HistoryQueryResult;
import com.workflow.dto.response.TaskAssignmentResult;
import com.workflow.dto.response.TaskListResult;
import com.workflow.enums.AssignmentType;
import com.workflow.service.UserPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务管理控制器
 * 
 * 提供任务查询、完成、委托、转办等RESTful API接口
 * 通过 TaskManagerComponent 调用 Flowable 引擎
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "任务管理", description = "工作流任务管理API")
public class TaskController {

    private final TaskManagerComponent taskManagerComponent;
    private final UserPermissionService userPermissionService;
    private final HistoryService historyService;

    /**
     * 查询任务列表
     */
    @GetMapping
    @Operation(summary = "查询任务列表", description = "根据条件查询任务列表")
    public ResponseEntity<ApiResponse<TaskListResult>> getTasks(
            @Parameter(description = "用户ID")
            @RequestParam(value = "userId", required = false) String userId,
            @Parameter(description = "流程实例ID")
            @RequestParam(value = "processInstanceId", required = false) String processInstanceId,
            @Parameter(description = "页码")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "每页大小")
            @RequestParam(value = "size", defaultValue = "10") int size,
            @Parameter(description = "虚拟组ID列表")
            @RequestParam(value = "groupIds", required = false) List<String> groupIds,
            @Parameter(description = "部门角色列表")
            @RequestParam(value = "deptRoles", required = false) List<String> deptRoles) {
        
        log.info("Querying tasks for user: {}, processInstanceId: {}, page: {}, size: {}", userId, processInstanceId, page, size);
        
        TaskListResult result;
        if (processInstanceId != null && !processInstanceId.isEmpty()) {
            // 按流程实例ID查询任务
            result = taskManagerComponent.getTasksByProcessInstance(processInstanceId, page, size);
        } else if (groupIds != null || deptRoles != null) {
            result = taskManagerComponent.getUserAllVisibleTasks(userId, groupIds, deptRoles, page, size);
        } else {
            result = taskManagerComponent.getUserTasks(userId, page, size);
        }
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取任务详情", description = "根据ID获取任务详情")
    public ResponseEntity<ApiResponse<TaskListResult.TaskInfo>> getTask(
            @Parameter(description = "任务ID", required = true)
            @PathVariable String taskId) {
        
        log.info("Getting task details: {}", taskId);
        TaskListResult.TaskInfo taskInfo = taskManagerComponent.getTaskInfo(taskId);
        return ResponseEntity.ok(ApiResponse.success(taskInfo));
    }
    
    /**
     * 获取任务流转历史
     */
    @GetMapping("/{taskId}/history")
    @Operation(summary = "获取任务流转历史", description = "获取任务所属流程实例的流转历史")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTaskHistory(
            @Parameter(description = "任务ID", required = true)
            @PathVariable String taskId) {
        
        log.info("Getting task history for task: {}", taskId);
        
        // 先获取任务信息以获取流程实例ID
        TaskListResult.TaskInfo taskInfo = taskManagerComponent.getTaskInfo(taskId);
        String processInstanceId = taskInfo.getProcessInstanceId();
        
        // 查询流程实例的活动历史
        List<HistoricActivityInstance> activities = historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(processInstanceId)
            .orderByHistoricActivityInstanceStartTime().asc()
            .list();
        
        // 转换为前端期望的格式
        List<Map<String, Object>> historyList = activities.stream()
            .filter(activity -> "userTask".equals(activity.getActivityType()) || 
                               "startEvent".equals(activity.getActivityType()) ||
                               "endEvent".equals(activity.getActivityType()))
            .map(activity -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", activity.getId());
                item.put("taskId", activity.getTaskId());
                item.put("taskName", activity.getActivityName());
                item.put("activityId", activity.getActivityId());
                item.put("activityName", activity.getActivityName());
                item.put("activityType", activity.getActivityType());
                
                // 根据活动类型设置操作类型
                String operationType = "PENDING";
                if (activity.getEndTime() != null) {
                    if ("startEvent".equals(activity.getActivityType())) {
                        operationType = "SUBMIT";
                    } else {
                        operationType = "APPROVE";
                    }
                }
                item.put("operationType", operationType);
                
                item.put("operatorId", activity.getAssignee());
                item.put("operatorName", activity.getAssignee()); // TODO: 从用户服务获取用户名
                item.put("operationTime", activity.getEndTime() != null ? 
                    activity.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString() :
                    activity.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString());
                item.put("comment", null); // TODO: 从评论服务获取评论
                item.put("duration", activity.getDurationInMillis());
                
                return item;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(historyList));
    }

    /**
     * 分配任务
     */
    @PostMapping("/{taskId}/assign")
    @Operation(summary = "分配任务", description = "将任务分配给用户或组")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> assignTask(
            @Parameter(description = "任务ID", required = true)
            @PathVariable String taskId,
            @RequestBody TaskAssignmentRequest request) {
        
        log.info("Assigning task: {} to {} (type: {})", taskId, request.getAssignmentTarget(), request.getAssignmentType());
        TaskAssignmentResult result = taskManagerComponent.assignTask(taskId, request);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("ASSIGN_FAILED", result.getMessage()));
        }
    }

    /**
     * 认领任务
     */
    @PostMapping("/{taskId}/claim")
    @Operation(summary = "认领任务", description = "认领虚拟组或部门角色任务")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> claimTask(
            @Parameter(description = "任务ID", required = true)
            @PathVariable String taskId,
            @RequestBody TaskClaimRequest request) {
        
        // 设置 taskId（从路径参数获取）
        request.setTaskId(taskId);
        
        log.info("Claiming task: {} by user: {}", taskId, request.getClaimedBy());
        TaskAssignmentResult result = taskManagerComponent.claimTask(taskId, request);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("CLAIM_FAILED", result.getMessage()));
        }
    }

    /**
     * 委托任务
     */
    @PostMapping("/{taskId}/delegate")
    @Operation(summary = "委托任务", description = "将任务委托给其他用户")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> delegateTask(
            @Parameter(description = "任务ID", required = true)
            @PathVariable String taskId,
            @RequestBody TaskDelegationRequest request) {
        
        log.info("Delegating task: {} from {} to {}", taskId, request.getDelegatedBy(), request.getDelegatedTo());
        TaskAssignmentResult result = taskManagerComponent.delegateTask(taskId, request);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("DELEGATE_FAILED", result.getMessage()));
        }
    }
    
    /**
     * 取消认领任务
     */
    @PostMapping("/{taskId}/unclaim")
    @Operation(summary = "取消认领任务", description = "取消认领已认领的任务")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> unclaimTask(
            @Parameter(description = "任务ID", required = true)
            @PathVariable String taskId,
            @RequestBody Map<String, Object> request) {
        
        String userId = (String) request.get("userId");
        log.info("Unclaiming task: {} by user: {}", taskId, userId);
        TaskAssignmentResult result = taskManagerComponent.unclaimTask(taskId, userId);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("UNCLAIM_FAILED", result.getMessage()));
        }
    }
    
    /**
     * 转办任务
     */
    @PostMapping("/{taskId}/transfer")
    @Operation(summary = "转办任务", description = "将任务转办给其他用户")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> transferTask(
            @Parameter(description = "任务ID", required = true)
            @PathVariable String taskId,
            @RequestBody Map<String, Object> request) {
        
        String fromUserId = (String) request.get("fromUserId");
        String toUserId = (String) request.get("toUserId");
        String reason = (String) request.get("reason");
        
        log.info("Transferring task: {} from {} to {}", taskId, fromUserId, toUserId);
        TaskAssignmentResult result = taskManagerComponent.transferTask(taskId, fromUserId, toUserId, reason);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("TRANSFER_FAILED", result.getMessage()));
        }
    }

    /**
     * 完成任务
     */
    @PostMapping("/{taskId}/complete")
    @Operation(summary = "完成任务", description = "完成指定的任务")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> completeTask(
            @Parameter(description = "任务ID", required = true)
            @PathVariable String taskId,
            @RequestBody Map<String, Object> request) {
        
        String userId = (String) request.get("userId");
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) request.get("variables");
        
        log.info("Completing task: {} by user: {}", taskId, userId);
        TaskAssignmentResult result = taskManagerComponent.completeTask(taskId, userId, variables);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("COMPLETE_FAILED", result.getMessage()));
        }
    }
    
    /**
     * 回退任务
     */
    @PostMapping("/{taskId}/return")
    @Operation(summary = "回退任务", description = "将任务回退到指定的历史节点")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> returnTask(
            @Parameter(description = "任务ID", required = true)
            @PathVariable String taskId,
            @RequestBody TaskReturnRequest request) {
        
        request.setTaskId(taskId);
        log.info("Returning task: {} to activity: {} by user: {}", 
                taskId, request.getTargetActivityId(), request.getUserId());
        
        TaskAssignmentResult result = taskManagerComponent.returnTask(taskId, request);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("RETURN_FAILED", result.getMessage()));
        }
    }
    
    /**
     * 获取可回退的历史节点
     */
    @GetMapping("/{taskId}/returnable-activities")
    @Operation(summary = "获取可回退节点", description = "获取任务可以回退到的历史节点列表")
    public ResponseEntity<ApiResponse<List<TaskListResult.TaskInfo>>> getReturnableActivities(
            @Parameter(description = "任务ID", required = true)
            @PathVariable String taskId) {
        
        log.info("Getting returnable activities for task: {}", taskId);
        List<TaskListResult.TaskInfo> activities = taskManagerComponent.getReturnableActivities(taskId);
        return ResponseEntity.ok(ApiResponse.success(activities));
    }

    /**
     * 批量完成任务
     */
    @PostMapping("/batch/complete")
    @Operation(summary = "批量完成任务", description = "批量完成多个任务")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchCompleteTasks(
            @RequestBody Map<String, Object> request) {
        
        @SuppressWarnings("unchecked")
        List<String> taskIds = (List<String>) request.get("taskIds");
        String userId = (String) request.get("userId");
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) request.get("variables");
        
        log.info("Batch completing {} tasks by user: {}", taskIds.size(), userId);
        
        List<String> successIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        
        for (String taskId : taskIds) {
            try {
                TaskAssignmentResult result = taskManagerComponent.completeTask(taskId, userId, variables);
                if (result.isSuccess()) {
                    successIds.add(taskId);
                } else {
                    failedIds.add(taskId);
                }
            } catch (Exception e) {
                log.warn("Failed to complete task {}: {}", taskId, e.getMessage());
                failedIds.add(taskId);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("successIds", successIds);
        result.put("failedIds", failedIds);
        result.put("completed", successIds.size());
        result.put("failed", failedIds.size());
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * 统计用户任务数量
     */
    @GetMapping("/count")
    @Operation(summary = "统计任务数量", description = "统计用户的待办任务数量")
    public ResponseEntity<ApiResponse<Map<String, Object>>> countTasks(
            @Parameter(description = "用户ID", required = true)
            @RequestParam("userId") String userId) {
        
        log.info("Counting tasks for user: {}", userId);
        
        long totalCount = taskManagerComponent.countUserTasks(userId);
        long overdueCount = taskManagerComponent.countUserOverdueTasks(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", totalCount);
        result.put("overdueCount", overdueCount);
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * 获取用户的任务权限信息
     * 返回用户所属的虚拟组ID列表和部门角色列表，用于任务查询
     */
    @GetMapping("/user-permissions")
    @Operation(summary = "获取用户任务权限", description = "获取用户的虚拟组和角色信息，用于任务查询")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserTaskPermissions(
            @Parameter(description = "用户ID", required = true)
            @RequestParam("userId") String userId) {
        
        log.info("Getting task permissions for user: {}", userId);
        
        List<String> virtualGroupIds = userPermissionService.getUserVirtualGroupIds(userId);
        List<String> roles = userPermissionService.getUserRoles(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("virtualGroupIds", virtualGroupIds);
        result.put("roles", roles);
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * 检查用户是否有任务操作权限
     */
    @GetMapping("/{taskId}/check-permission")
    @Operation(summary = "检查任务权限", description = "检查用户是否有操作指定任务的权限")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkTaskPermission(
            @Parameter(description = "任务ID", required = true)
            @PathVariable String taskId,
            @Parameter(description = "用户ID", required = true)
            @RequestParam("userId") String userId) {
        
        log.info("Checking task permission for user: {} on task: {}", userId, taskId);
        
        TaskListResult.TaskInfo taskInfo = taskManagerComponent.getTaskInfo(taskId);
        
        boolean hasPermission = userPermissionService.hasTaskPermission(
                userId, 
                taskInfo.getAssignmentType(), 
                taskInfo.getAssignmentTarget());
        
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("userId", userId);
        result.put("hasPermission", hasPermission);
        result.put("assignmentType", taskInfo.getAssignmentType());
        result.put("assignmentTarget", taskInfo.getAssignmentTarget());
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}