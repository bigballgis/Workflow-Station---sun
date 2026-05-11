package com.workflow.controller;

import com.platform.common.config.ConfigurationManager;
import com.platform.common.config.WorkflowConfig;
import com.platform.common.security.SecurityIntegrationService;
import com.workflow.component.HistoryManagerComponent;
import com.workflow.component.TaskManagerComponent;
import com.workflow.dto.request.HistoryQueryRequest;
import com.workflow.dto.request.TaskAssignmentRequest;
import com.workflow.dto.request.TaskClaimRequest;
import com.workflow.dto.request.TaskDelegationRequest;
import com.workflow.dto.request.TaskReturnRequest;
import com.workflow.dto.request.AssignSubTableRowRequest;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.HistoryQueryResult;
import com.workflow.dto.response.TaskAssignmentResult;
import com.workflow.dto.response.TaskListResult;
import com.workflow.dto.response.AssignSubTableRowResponse;
import com.workflow.component.SubTableAssignmentHandler;
import com.workflow.enums.AssignmentType;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.service.UserPermissionService;
import com.workflow.util.WorkflowActorResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 任务管理控制器
 * 
 * 提供任务查询、完成、委托、转办等RESTful API接口
 * 通过 TaskManagerComponent 调用 Flowable 引擎
 * 集成了安全验证、输入验证和错误处理框架
 * 
 * **Validates: Requirements 4.2**
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
    @Tag(name = "Task Management", description = "Workflow task management API")
public class TaskController {

    private final TaskManagerComponent taskManagerComponent;
    private final UserPermissionService userPermissionService;
    private final HistoryService historyService;
    private final TaskService taskService;
    private final ConfigurationManager configurationManager;
    private final SecurityIntegrationService securityIntegrationService;
    private final com.workflow.client.AdminCenterClient adminCenterClient;
    private final SubTableAssignmentHandler subTableAssignmentHandler;
    private final com.workflow.component.MultiInstanceDataResolver multiInstanceDataResolver;

    /**
     * 查询任务列表
     */
    @GetMapping
    @Operation(summary = "Query Task List", description = "Query tasks by criteria")
    public ResponseEntity<ApiResponse<TaskListResult>> getTasks(
            @Parameter(description = "User ID")
            @RequestParam(value = "userId", required = false) String userId,
            @Parameter(description = "Process instance ID")
            @RequestParam(value = "processInstanceId", required = false) String processInstanceId,
            @Parameter(description = "Page number")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", required = false) Integer size,
            @Parameter(description = "Virtual group ID list")
            @RequestParam(value = "groupIds", required = false) List<String> groupIds,
            @Parameter(description = "Department role list")
            @RequestParam(value = "deptRoles", required = false) List<String> deptRoles,
            @Parameter(description = "Portal current workspace business unit (optional; filters pending tasks where FIXED_BU_ROLE is inconsistent with JWT)")
            @RequestParam(value = "activeBusinessUnitId", required = false) String activeBusinessUnitId) {
        
        // Validate and sanitize inputs using security integration service
        if (userId != null) {
            securityIntegrationService.validateAndAuditInput("userId", userId, "task_query");
        }
        if (processInstanceId != null) {
            securityIntegrationService.validateAndAuditInput("processInstanceId", processInstanceId, "task_query");
        }
        
        // Use externalized configuration for default page size
        WorkflowConfig workflowConfig = configurationManager.getConfiguration(WorkflowConfig.class);
        int pageSize = size != null ? size : workflowConfig.getDefaultPageSize();
        
        // Enforce maximum page size limit
        if (pageSize > workflowConfig.getMaxPageSize()) {
            pageSize = workflowConfig.getMaxPageSize();
        }

        Optional<String> actor = WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }
        if (userId != null && !userId.isEmpty() && !actor.get().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("FORBIDDEN", "userId must match the authenticated user"));
        }
        if (userId == null || userId.isEmpty()) {
            userId = actor.get();
        }
        
        log.info("Querying tasks for user: {}, processInstanceId: {}, page: {}, size: {}", userId, processInstanceId, page, pageSize);
        
        TaskListResult result;
        if (processInstanceId != null && !processInstanceId.isEmpty()) {
            // 按流程实例ID查询任务
            result = taskManagerComponent.getTasksByProcessInstance(processInstanceId, page, pageSize);
        } else {
            // 统一走 getUserAllVisibleTasks（含 repairOrphanBuRolePoolTasks）。
            // 当请求未带 groupIds（门户 filterVirtualGroupsForActiveWorkspace 过滤掉全部 VG 后）时，
            // 若误走 getUserTasks 会跳过 BU_ROLE 孤儿池修复，待办可能为空。
            List<String> gids = groupIds != null ? groupIds : Collections.emptyList();
            List<String> droles = deptRoles != null ? deptRoles : Collections.emptyList();
            result = taskManagerComponent.getUserAllVisibleTasks(userId, gids, droles, page, pageSize,
                    activeBusinessUnitId);
        }
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "Get Task Details", description = "Get task details by ID")
    public ResponseEntity<ApiResponse<TaskListResult.TaskInfo>> getTask(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId) {
        
        log.info("Getting task details: {}", taskId);
        TaskListResult.TaskInfo taskInfo = taskManagerComponent.getTaskInfo(taskId);
        return ResponseEntity.ok(ApiResponse.success(taskInfo));
    }
    
    /**
     * 获取任务流转历史
     */
    @GetMapping("/{taskId}/history")
    @Operation(summary = "Get Task Flow History", description = "Get the flow history of a task's process instance")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTaskHistory(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId) {
        
        log.info("Getting task history for task: {}", taskId);
        
        // 先获取任务信息以获取流程实例ID
        TaskListResult.TaskInfo taskInfo = taskManagerComponent.getTaskInfo(taskId);
        String processInstanceId = taskInfo.getProcessInstanceId();
        
        return getProcessInstanceHistory(processInstanceId);
    }
    
    /**
     * 获取流程实例流转历史（通过流程实例ID）
     */
    @GetMapping("/process/{processInstanceId}/history")
    @Operation(summary = "Get Process Instance Flow History", description = "Get the complete flow history of a process instance with user name resolution")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProcessInstanceHistory(
            @Parameter(description = "Process instance ID", required = true)
            @PathVariable String processInstanceId) {
        
        log.info("Getting process instance history for: {}", processInstanceId);
        
        // 查询流程实例的活动历史
        List<HistoricActivityInstance> activities = historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(processInstanceId)
            .orderByHistoricActivityInstanceStartTime().asc()
            .list();
        
        // 查询任务历史以获取 deleteReason
        List<HistoricTaskInstance> tasks = historyService
            .createHistoricTaskInstanceQuery()
            .processInstanceId(processInstanceId)
            .list();
        
        // 创建 taskId 到 deleteReason 的映射
        Map<String, String> taskDeleteReasons = tasks.stream()
            .filter(task -> task.getDeleteReason() != null)
            .collect(Collectors.toMap(
                HistoricTaskInstance::getId,
                HistoricTaskInstance::getDeleteReason,
                (existing, replacement) -> existing
            ));

        // Build taskId → comment mapping from Flowable's native comment system (ACT_HI_COMMENT).
        // For each task, take the latest comment message.
        // Transfer-typed comments are tracked separately so we can inject synthetic TRANSFER entries.
        Map<String, String> taskComments = new HashMap<>();
        // Same runtime userTask keeps one taskId across multiple transfers; keep every transfer comment.
        Map<String, List<Comment>> taskTransferCommentsByTaskId = new HashMap<>();
        try {
            List<Comment> allComments = taskService.getProcessInstanceComments(processInstanceId);
            if (allComments != null) {
                for (Comment c : allComments) {
                    if (c.getTaskId() == null) continue;
                    if ("transfer".equals(c.getType())) {
                        taskTransferCommentsByTaskId.computeIfAbsent(c.getTaskId(), k -> new ArrayList<>()).add(c);
                    } else if (c.getFullMessage() != null && !c.getFullMessage().isBlank()) {
                        taskComments.put(c.getTaskId(), c.getFullMessage());
                    }
                }
                for (List<Comment> transferComments : taskTransferCommentsByTaskId.values()) {
                    transferComments.sort(Comparator.comparing(Comment::getTime, Comparator.nullsLast(Comparator.naturalOrder())));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load Flowable comments for process {}: {}", processInstanceId, e.getMessage());
        }
        
        // 获取流程实例信息（用于 startEvent 的发起人解析）
        HistoricProcessInstance processInstance = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        String processStartUserId = processInstance != null ? processInstance.getStartUserId() : null;

        // 转换为前端期望的格式
        List<Map<String, Object>> historyList = activities.stream()
            .filter(activity -> "userTask".equals(activity.getActivityType()) || 
                               "startEvent".equals(activity.getActivityType()) ||
                               "endEvent".equals(activity.getActivityType()) ||
                               "exclusiveGateway".equals(activity.getActivityType()) ||
                               "parallelGateway".equals(activity.getActivityType()) ||
                               "inclusiveGateway".equals(activity.getActivityType()))
            .map(activity -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", activity.getId());
                item.put("taskId", activity.getTaskId());
                item.put("taskName", activity.getActivityName());
                item.put("activityId", activity.getActivityId());
                item.put("activityName", activity.getActivityName());
                item.put("activityType", activity.getActivityType());
                
                // 根据活动类型和 deleteReason 设置操作类型
                String activityType = activity.getActivityType();
                String operationType = "PENDING";
                if (activity.getEndTime() != null) {
                    if ("startEvent".equals(activityType)) {
                        operationType = "SUBMIT";
                    } else if ("exclusiveGateway".equals(activityType) ||
                               "parallelGateway".equals(activityType) ||
                               "inclusiveGateway".equals(activityType)) {
                        operationType = "GATEWAY";
                    } else if ("userTask".equals(activityType)) {
                        // 检查 deleteReason 来判断是 APPROVE 还是 REJECT
                        String deleteReason = taskDeleteReasons.get(activity.getTaskId());
                        if (deleteReason != null) {
                            if (deleteReason.contains("rejected") || deleteReason.contains("REJECTED") ||
                                deleteReason.contains("reject") || deleteReason.contains("REJECT")) {
                                operationType = "REJECT";
                            } else if (deleteReason.contains("approved") || deleteReason.contains("APPROVED") ||
                                      deleteReason.contains("approve") || deleteReason.contains("APPROVE")) {
                                operationType = "APPROVE";
                            } else if (deleteReason.contains("transfer") || deleteReason.contains("TRANSFER")) {
                                operationType = "TRANSFER";
                            } else if (deleteReason.contains("delegate") || deleteReason.contains("DELEGATE")) {
                                operationType = "DELEGATE";
                            } else {
                                operationType = "APPROVE";
                            }
                        } else {
                            operationType = "APPROVE";
                        }
                    } else {
                        operationType = "APPROVE";
                    }
                }
                item.put("operationType", operationType);
                
                // startEvent 在 Flowable 中没有 assignee，使用流程实例的发起人
                String assignee = activity.getAssignee();
                if ((assignee == null || assignee.isEmpty()) && "startEvent".equals(activityType)) {
                    assignee = processStartUserId;
                }
                item.put("operatorId", assignee);
                
                // 解析用户显示名称
                String operatorName = assignee;
                if (assignee != null && !assignee.isEmpty()) {
                    try {
                        Map<String, Object> userInfo = adminCenterClient.getUserInfo(assignee);
                        if (userInfo != null) {
                            // 优先使用 fullName
                            String fullName = (String) userInfo.get("fullName");
                            if (fullName != null && !fullName.isEmpty()) {
                                operatorName = fullName;
                            } else {
                                // 其次使用 displayName
                                String displayName = (String) userInfo.get("displayName");
                                if (displayName != null && !displayName.isEmpty()) {
                                    operatorName = displayName;
                                } else {
                                    // 再次使用 username
                                    String username = (String) userInfo.get("username");
                                    if (username != null && !username.isEmpty()) {
                                        operatorName = username;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to resolve user display name for {}: {}", assignee, e.getMessage());
                    }
                }
                item.put("operatorName", operatorName);
                
                item.put("operationTime", activity.getEndTime() != null ? 
                    activity.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString() :
                    activity.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString());

                // Prefer Flowable native comment; fall back to deleteReason for legacy data
                String taskId = activity.getTaskId();
                String comment = taskId != null ? taskComments.get(taskId) : null;
                if (comment == null && taskId != null) {
                    comment = taskDeleteReasons.get(taskId);
                }
                item.put("comment", comment);
                item.put("duration", activity.getDurationInMillis());
                
                return item;
            })
            .collect(Collectors.toList());

        // Inject synthetic TRANSFER entries for tasks that have transfer comments.
        // Each transfer entry appears right before the corresponding task entry so the
        // timeline shows: ... → TRANSFER (by originator) → PENDING (current assignee).
        int transferCommentCount = taskTransferCommentsByTaskId.values().stream().mapToInt(List::size).sum();
        if (transferCommentCount > 0) {
            List<Map<String, Object>> enrichedList = new ArrayList<>(historyList.size() + transferCommentCount);
            for (Map<String, Object> item : historyList) {
                String tid = (String) item.get("taskId");
                List<Comment> transferComments = tid != null ? taskTransferCommentsByTaskId.get(tid) : null;
                if (transferComments != null) {
                    for (Comment tc : transferComments) {
                        Map<String, Object> transferItem = new HashMap<>();
                        String commentId = tc.getId() != null ? tc.getId() : "noid";
                        transferItem.put("id", item.get("id") + "_transfer_" + commentId);
                        transferItem.put("taskId", tid);
                        transferItem.put("taskName", item.get("taskName"));
                        transferItem.put("activityId", item.get("activityId"));
                        transferItem.put("activityName", item.get("activityName"));
                        transferItem.put("activityType", "userTask");
                        transferItem.put("operationType", "TRANSFER");

                        String transferUserId = tc.getUserId();
                        transferItem.put("operatorId", transferUserId);
                        String transferOperatorName = transferUserId;
                        if (transferUserId != null && !transferUserId.isEmpty()) {
                            try {
                                Map<String, Object> userInfo = adminCenterClient.getUserInfo(transferUserId);
                                if (userInfo != null) {
                                    String fn = (String) userInfo.get("fullName");
                                    if (fn != null && !fn.isEmpty()) { transferOperatorName = fn; }
                                    else {
                                        String dn = (String) userInfo.get("displayName");
                                        if (dn != null && !dn.isEmpty()) { transferOperatorName = dn; }
                                        else {
                                            String un = (String) userInfo.get("username");
                                            if (un != null && !un.isEmpty()) { transferOperatorName = un; }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Failed to resolve transfer user name for {}: {}", transferUserId, e.getMessage());
                            }
                        }
                        transferItem.put("operatorName", transferOperatorName);

                        transferItem.put("operationTime", tc.getTime() != null
                                ? tc.getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString()
                                : item.get("operationTime"));
                        String reason = tc.getFullMessage();
                        transferItem.put("comment", reason != null && !reason.isBlank() ? reason : null);
                        transferItem.put("duration", null);

                        enrichedList.add(transferItem);
                    }
                }
                enrichedList.add(item);
            }
            historyList = enrichedList;
        }
        
        return ResponseEntity.ok(ApiResponse.success(historyList));
    }

    /**
     * 分配任务
     */
    @PostMapping("/{taskId}/assign")
    @Operation(summary = "Assign Task", description = "Assign task to a user or group")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> assignTask(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId,
            @RequestBody @Valid TaskAssignmentRequest request) {

        Optional<String> actor = WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }
        request.setOperatorUserId(actor.get());

        log.info("Assigning task: {} to {} (type: {})", taskId, request.getAssignmentTarget(), request.getAssignmentType());
        try {
            TaskAssignmentResult result = taskManagerComponent.assignTask(taskId, request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (WorkflowBusinessException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
        }
    }

    /**
     * 认领任务
     */
    @PostMapping("/{taskId}/claim")
    @Operation(summary = "Claim Task", description = "Claim a virtual group or department role task")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> claimTask(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId,
            @RequestBody(required = false) TaskClaimRequest request) {
        
        if (request == null) {
            request = new TaskClaimRequest();
        }
        // 设置 taskId（从路径参数获取；勿对 body 使用 @Valid，否则在 setTaskId 之前校验会失败）
        request.setTaskId(taskId);

        Optional<String> actor = WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }
        request.setClaimedBy(actor.get());
        
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
    @Operation(summary = "Delegate Task", description = "Delegate task to another user")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> delegateTask(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId,
            @RequestBody @Valid TaskDelegationRequest request) {
        
        Optional<String> actor = WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }
        request.setDelegatedBy(actor.get());

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
    @Operation(summary = "Unclaim Task", description = "Unclaim a previously claimed task")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> unclaimTask(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId,
            @RequestBody Map<String, Object> request) {
        
        Optional<String> actor = WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }
        log.info("Unclaiming task: {} by user: {}", taskId, actor.get());
        TaskAssignmentResult result = taskManagerComponent.unclaimTask(taskId, actor.get());
        
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
    @Operation(summary = "Transfer Task", description = "Transfer task to another user")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> transferTask(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId,
            @RequestBody Map<String, Object> request) {
        
        Optional<String> actor = WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }
        String fromUserId = actor.get();
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
    @Operation(summary = "Complete Task", description = "Complete the specified task")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> completeTask(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId,
            @RequestBody Map<String, Object> request) {
        
        Optional<String> actor = WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }
        String userId = actor.get();
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) request.get("variables");
        boolean sendNotification = true;
        if (request.containsKey("sendNotification") && request.get("sendNotification") instanceof Boolean b) {
            sendNotification = b;
        }
        
        log.info("Completing task: {} by user: {}", taskId, userId);
        log.debug("Variables received (keys only): {}",
                variables != null ? variables.keySet() : null);
        
        TaskAssignmentResult result = taskManagerComponent.completeTask(taskId, userId, variables, sendNotification);
        
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
    @Operation(summary = "Return Task", description = "Return the task to a specified historical node")
    public ResponseEntity<ApiResponse<TaskAssignmentResult>> returnTask(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId,
            @RequestBody @Valid TaskReturnRequest request) {
        
        request.setTaskId(taskId);

        Optional<String> actor = WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }
        request.setUserId(actor.get());

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
    @Operation(summary = "Get Returnable Activities", description = "Get the list of historical nodes the task can be returned to")
    public ResponseEntity<ApiResponse<List<TaskListResult.TaskInfo>>> getReturnableActivities(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId) {
        
        log.info("Getting returnable activities for task: {}", taskId);
        List<TaskListResult.TaskInfo> activities = taskManagerComponent.getReturnableActivities(taskId);
        return ResponseEntity.ok(ApiResponse.success(activities));
    }

    /**
     * 批量完成任务
     */
    @PostMapping("/batch/complete")
    @Operation(summary = "Batch Complete Tasks", description = "Complete multiple tasks in batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchCompleteTasks(
            @RequestBody Map<String, Object> request) {
        
        Optional<String> actor = WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }
        String userId = actor.get();
        @SuppressWarnings("unchecked")
        List<String> taskIds = (List<String>) request.get("taskIds");
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) request.get("variables");
        boolean sendNotification = true;
        if (request.containsKey("sendNotification") && request.get("sendNotification") instanceof Boolean b) {
            sendNotification = b;
        }

        if (taskIds == null || taskIds.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_REQUEST", "taskIds is required"));
        }
        
        log.info("Batch completing {} tasks by user: {}", taskIds.size(), userId);
        
        List<String> successIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        
        for (String taskId : taskIds) {
            try {
                TaskAssignmentResult result = taskManagerComponent.completeTask(taskId, userId, variables, sendNotification);
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
    @Operation(summary = "Count User Tasks", description = "Count pending task count for the current authenticated user (userId query param is not trusted)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> countTasks() {

        Optional<String> actor = WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }
        String userId = actor.get();

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
    @Operation(summary = "Get User Task Permissions", description = "Get user's virtual group and role information for task queries")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserTaskPermissions(
            @Parameter(description = "User ID", required = true)
            @RequestParam("userId") String userId) {
        
        Optional<String> actor = WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }
        if (!actor.get().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("FORBIDDEN", "Can only query permissions for the authenticated user"));
        }

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
    @Operation(summary = "Check Task Permission", description = "Check if user has permission to operate on the specified task")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkTaskPermission(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId,
            @Parameter(description = "User ID", required = true)
            @RequestParam("userId") String userId) {
        
        Optional<String> actor = WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }
        if (!actor.get().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("FORBIDDEN", "Can only check permissions for the authenticated user"));
        }

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
    
    /**
     * 分配子表行处理人
     * 
     * 用于多实例子流程前置任务中，手动为子表行分配处理人。
     * 这是多实例任务分发流程的第一步：前置任务处理人通过 Assign 按钮为每个子表行指定处理人。
     */
    @PostMapping("/{taskId}/sub-table-rows/{rowId}/assign")
    @Operation(summary = "Assign Sub-Table Row Handler", description = "Assign handler for a sub-table row in a multi-instance sub-process")
    public ResponseEntity<ApiResponse<AssignSubTableRowResponse>> assignSubTableRow(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId,
            @Parameter(description = "Sub-table row ID", required = true)
            @PathVariable Long rowId,
            @RequestBody @Valid AssignSubTableRowRequest request) {
        
        log.info("Assigning sub-table row handler: taskId={}, rowId={}, assigneeId={}", 
            taskId, rowId, request.getAssigneeId());
        
        try {
            SubTableAssignmentHandler.AssignmentResponse handlerResponse =
                subTableAssignmentHandler.assign(taskId, rowId, request.getRowKey(), request.getAssigneeId());
            
            AssignSubTableRowResponse response = AssignSubTableRowResponse.builder()
                .success(handlerResponse.isSuccess())
                .rowId(handlerResponse.getRowId())
                .assigneeId(handlerResponse.getAssigneeId())
                .assigneeName(handlerResponse.getAssigneeName())
                .build();
            
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Failed to assign sub-table row handler: taskId={}, rowId={}", taskId, rowId, e);
            
            return ResponseEntity.badRequest().body(
                ApiResponse.error("ASSIGN_SUBTABLE_ROW_FAILED", e.getMessage())
            );
        }
    }
    
    /**
     * 加载子任务表单数据
     * 
     * 用于多实例子任务表单加载，返回主任务表单数据（只读）和子表数据行（可编辑）。
     * 
     * **Validates: Requirements 6.1**
     */
    @GetMapping("/{taskId}/sub-task-form-data")
    @Operation(summary = "Load Sub-Task Form Data", description = "Load multi-instance sub-task form data, including main task info and sub-table data rows")
    public ResponseEntity<ApiResponse<com.workflow.component.MultiInstanceDataResolver.SubTaskFormData>> getSubTaskFormData(
            @Parameter(description = "Task ID", required = true)
            @PathVariable String taskId) {
        
        log.info("Loading sub-task form data: taskId={}", taskId);
        
        try {
            com.workflow.component.MultiInstanceDataResolver.SubTaskFormData formData = 
                multiInstanceDataResolver.loadSubTaskFormData(taskId);
            
            return ResponseEntity.ok(ApiResponse.success(formData));
        } catch (Exception e) {
            log.error("Failed to load sub-task form data: taskId={}", taskId, e);
            
            return ResponseEntity.badRequest().body(
                ApiResponse.error("LOAD_SUBTASK_FORM_DATA_FAILED", e.getMessage())
            );
        }
    }
}