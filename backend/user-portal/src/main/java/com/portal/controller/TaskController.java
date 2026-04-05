package com.portal.controller;

import com.portal.debug.AgentDebugLog;
import com.portal.component.TaskProcessComponent;
import com.platform.common.util.ApiResponseBodyUnwrap;
import com.portal.component.TaskQueryComponent;
import com.portal.dto.*;
import com.portal.exception.PortalException;
import com.portal.security.CurrentUserId;
import com.platform.common.i18n.I18nService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 任务管理API
 */
@Tag(name = "任务管理", description = "任务查询、处理、委托等操作")
@Slf4j
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskQueryComponent taskQueryComponent;
    private final TaskProcessComponent taskProcessComponent;
    private final I18nService i18nService;
    private final RestTemplate restTemplate;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    @Operation(summary = "查询待办任务列表")
    @PostMapping("/query")
    public ApiResponse<PageResponse<TaskInfo>> queryTasks(
            @CurrentUserId String userId,
            @RequestBody @Valid TaskQueryRequest request) {
        // 强制使用当前登录用户，禁止 body 伪造 userId（与 @CurrentUserId 一致）
        request.setUserId(userId);
        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{taskId}")
    public ApiResponse<TaskInfo> getTaskDetail(
            @CurrentUserId String userId,
            @PathVariable String taskId) {
        TaskInfo task = taskQueryComponent.getTaskById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        // 与 TaskFormController 一致：可查看详情 = canViewTaskForm（含发起人、处理人），非仅 canProcessTask
        if (userId != null && !taskProcessComponent.canViewTaskForm(task, userId)) {
            log.warn("User {} denied access to task {} (assignee={}, assignmentType={}, initiatorId={})",
                    userId, taskId, task.getAssignee(), task.getAssignmentType(), task.getInitiatorId());
            return ApiResponse.error("403", "You do not have permission to access this task");
        }
        return ApiResponse.success(task);
    }

    @Operation(summary = "获取任务流转历史")
    @GetMapping("/{taskId}/history")
    public ApiResponse<List<TaskHistoryInfo>> getTaskHistory(@PathVariable String taskId) {
        List<TaskHistoryInfo> history = taskQueryComponent.getTaskHistory(taskId);
        return ApiResponse.success(history);
    }

    @Operation(summary = "认领任务")
    @PostMapping("/{taskId}/claim")
    public ApiResponse<TaskInfo> claimTask(
            @PathVariable String taskId,
            @CurrentUserId String userId) {
        TaskInfo task = taskProcessComponent.claimTask(taskId, userId);
        return ApiResponse.success(i18nService.getMessage("portal.task_claimed"), task);
    }

    @Operation(summary = "取消认领任务")
    @PostMapping("/{taskId}/unclaim")
    public ApiResponse<TaskInfo> unclaimTask(
            @PathVariable String taskId,
            @CurrentUserId String userId,
            @RequestParam String originalAssignmentType,
            @RequestParam String originalAssignee) {
        TaskInfo task = taskProcessComponent.unclaimTask(taskId, userId, originalAssignmentType, originalAssignee);
        return ApiResponse.success(i18nService.getMessage("portal.task_unclaimed"), task);
    }

    @Operation(summary = "完成任务")
    @PostMapping("/{taskId}/complete")
    public ApiResponse<Void> completeTask(
            @PathVariable String taskId,
            @CurrentUserId String userId,
            @Valid @RequestBody TaskCompleteRequest request) {
        request.setTaskId(taskId);
        taskProcessComponent.completeTask(request, userId);
        return ApiResponse.success(i18nService.getMessage("portal.task_completed"), null);
    }

    @Operation(summary = "委托任务")
    @PostMapping("/{taskId}/delegate")
    public ApiResponse<Void> delegateTask(
            @PathVariable String taskId,
            @CurrentUserId String userId,
            @RequestParam String delegateId,
            @RequestParam(required = false) String reason) {
        taskProcessComponent.delegateTask(taskId, userId, delegateId, reason);
        return ApiResponse.success(i18nService.getMessage("portal.task_delegated"), null);
    }

    @Operation(summary = "转办任务")
    @PostMapping("/{taskId}/transfer")
    public ApiResponse<Void> transferTask(
            @PathVariable String taskId,
            @CurrentUserId String userId,
            @RequestParam String toUserId,
            @RequestParam(required = false) String reason) {
        taskProcessComponent.transferTask(taskId, userId, toUserId, reason);
        return ApiResponse.success(i18nService.getMessage("portal.task_transferred"), null);
    }

    @Operation(summary = "催办任务")
    @PostMapping("/{taskId}/urge")
    public ApiResponse<Void> urgeTask(
            @PathVariable String taskId,
            @CurrentUserId String userId,
            @RequestParam(required = false) String message) {
        taskProcessComponent.urgeTask(taskId, userId, message);
        return ApiResponse.success(i18nService.getMessage("portal.task_urged"), null);
    }

    @Operation(summary = "批量催办任务")
    @PostMapping("/batch/urge")
    public ApiResponse<Void> batchUrgeTasks(
            @CurrentUserId String userId,
            @RequestBody @Valid TaskBatchUrgeRequest request) {
        taskProcessComponent.batchUrgeTasks(request.getTaskIds(), userId, request.getMessage());
        return ApiResponse.success(i18nService.getMessage("portal.batch_urged"), null);
    }

    @Operation(summary = "获取任务统计")
    @GetMapping("/statistics")
    public ApiResponse<TaskStatistics> getTaskStatistics(
            @CurrentUserId String userId) {
        TaskStatistics statistics = taskQueryComponent.getTaskStatistics(userId);
        return ApiResponse.success(statistics);
    }
    
    @Operation(summary = "查询已处理任务列表")
    @PostMapping("/completed/query")
    public ApiResponse<PageResponse<TaskInfo>> queryCompletedTasks(
            @CurrentUserId String userId,
            @RequestBody @Valid TaskQueryRequest request) {
        request.setUserId(userId);
        PageResponse<TaskInfo> result = taskQueryComponent.queryCompletedTasks(request);
        return ApiResponse.success(result);
    }

    @Operation(summary = "搜索用户（用于转办、委托）")
    @GetMapping("/users/search")
    @SuppressWarnings("unchecked")
    public ApiResponse<List<Map<String, Object>>> searchUsers(
            @RequestParam(required = false, defaultValue = "") String keyword) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users?keyword=" + keyword + "&size=20";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = response.getBody();
            List<Map<String, Object>> users = body != null
                    ? ApiResponseBodyUnwrap.normalizeToListOfMaps(body)
                    : Collections.emptyList();
            return ApiResponse.success(users);
        } catch (Exception e) {
            log.error("Failed to search users from admin-center: {}", e.getMessage());
            return ApiResponse.success(Collections.emptyList());
        }
    }

    @Operation(summary = "分配子表行处理人", description = "多实例子流程前置任务：为子表某行指定处理人（转发至 workflow-engine）")
    @PostMapping("/{taskId}/sub-table-rows/{rowId}/assign")
    public ApiResponse<Map<String, Object>> assignSubTableRow(
            @PathVariable String taskId,
            @PathVariable Long rowId,
            @RequestBody @Valid SubTableRowAssignRequest request,
            @CurrentUserId String userId) {
        // #region agent log
        {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("taskIdLen", taskId != null ? taskId.length() : 0);
            d.put("rowId", rowId);
            d.put("assigneeIdLen", request.getAssigneeId() != null ? request.getAssigneeId().length() : 0);
            d.put("userIdLen", userId != null ? userId.length() : 0);
            AgentDebugLog.ff0c74("TaskController.assignSubTableRow", "H4", "assign_entry", d);
        }
        // #endregion
        Map<String, Object> data = taskProcessComponent.assignSubTableRow(taskId, rowId, request.getAssigneeId(), userId);
        // #region agent log
        {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("dataKeys", data != null ? data.keySet().toString() : "null");
            d.put("innerSuccess", data != null ? data.get("success") : null);
            AgentDebugLog.ff0c74("TaskController.assignSubTableRow", "H4", "assign_ok_before_response", d);
        }
        // #endregion
        return ApiResponse.success(data);
    }

    /**
     * 与 TaskFormController 一致：按 code 返回 HTTP 状态码，便于前端展示明确提示。
     */
    @ExceptionHandler(PortalException.class)
    public ApiResponse<Void> handlePortalException(PortalException e, HttpServletResponse response) {
        int statusCode = switch (e.getCode()) {
            case "404" -> HttpStatus.NOT_FOUND.value();
            case "403" -> HttpStatus.FORBIDDEN.value();
            case "400" -> HttpStatus.BAD_REQUEST.value();
            default -> HttpStatus.INTERNAL_SERVER_ERROR.value();
        };
        response.setStatus(statusCode);
        return ApiResponse.error(e.getCode(), e.getMessage());
    }
}
