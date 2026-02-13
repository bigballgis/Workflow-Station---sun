package com.portal.controller;

import com.portal.component.TaskProcessComponent;
import com.portal.component.TaskQueryComponent;
import com.portal.dto.*;
import com.platform.common.i18n.I18nService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务管理API
 */
@Tag(name = "任务管理", description = "任务查询、处理、委托等操作")
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskQueryComponent taskQueryComponent;
    private final TaskProcessComponent taskProcessComponent;
    private final I18nService i18nService;

    @Operation(summary = "查询待办任务列表")
    @PostMapping("/query")
    public ApiResponse<PageResponse<TaskInfo>> queryTasks(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody TaskQueryRequest request) {
        // 如果请求中没有userId，使用header中的
        if (request.getUserId() == null && userId != null) {
            request.setUserId(userId);
        }
        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{taskId}")
    public ApiResponse<TaskInfo> getTaskDetail(@PathVariable String taskId) {
        TaskInfo task = taskQueryComponent.getTaskById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
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
            @RequestHeader("X-User-Id") String userId) {
        TaskInfo task = taskProcessComponent.claimTask(taskId, userId);
        return ApiResponse.success(i18nService.getMessage("portal.task_claimed"), task);
    }

    @Operation(summary = "取消认领任务")
    @PostMapping("/{taskId}/unclaim")
    public ApiResponse<TaskInfo> unclaimTask(
            @PathVariable String taskId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String originalAssignmentType,
            @RequestParam String originalAssignee) {
        TaskInfo task = taskProcessComponent.unclaimTask(taskId, userId, originalAssignmentType, originalAssignee);
        return ApiResponse.success(i18nService.getMessage("portal.task_unclaimed"), task);
    }

    @Operation(summary = "完成任务")
    @PostMapping("/{taskId}/complete")
    public ApiResponse<Void> completeTask(
            @PathVariable String taskId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody TaskCompleteRequest request) {
        request.setTaskId(taskId);
        taskProcessComponent.completeTask(request, userId);
        return ApiResponse.success(i18nService.getMessage("portal.task_completed"), null);
    }

    @Operation(summary = "委托任务")
    @PostMapping("/{taskId}/delegate")
    public ApiResponse<Void> delegateTask(
            @PathVariable String taskId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String delegateId,
            @RequestParam(required = false) String reason) {
        taskProcessComponent.delegateTask(taskId, userId, delegateId, reason);
        return ApiResponse.success(i18nService.getMessage("portal.task_delegated"), null);
    }

    @Operation(summary = "转办任务")
    @PostMapping("/{taskId}/transfer")
    public ApiResponse<Void> transferTask(
            @PathVariable String taskId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String toUserId,
            @RequestParam(required = false) String reason) {
        taskProcessComponent.transferTask(taskId, userId, toUserId, reason);
        return ApiResponse.success(i18nService.getMessage("portal.task_transferred"), null);
    }

    @Operation(summary = "催办任务")
    @PostMapping("/{taskId}/urge")
    public ApiResponse<Void> urgeTask(
            @PathVariable String taskId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String message) {
        taskProcessComponent.urgeTask(taskId, userId, message);
        return ApiResponse.success(i18nService.getMessage("portal.task_urged"), null);
    }

    @Operation(summary = "批量催办任务")
    @PostMapping("/batch/urge")
    public ApiResponse<Void> batchUrgeTasks(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody TaskBatchUrgeRequest request) {
        taskProcessComponent.batchUrgeTasks(request.getTaskIds(), userId, request.getMessage());
        return ApiResponse.success(i18nService.getMessage("portal.batch_urged"), null);
    }

    @Operation(summary = "获取任务统计")
    @GetMapping("/statistics")
    public ApiResponse<TaskStatistics> getTaskStatistics(
            @RequestHeader("X-User-Id") String userId) {
        TaskStatistics statistics = taskQueryComponent.getTaskStatistics(userId);
        return ApiResponse.success(statistics);
    }
    
    @Operation(summary = "查询已处理任务列表")
    @PostMapping("/completed/query")
    public ApiResponse<PageResponse<TaskInfo>> queryCompletedTasks(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody TaskQueryRequest request) {
        // 如果请求中没有userId，使用header中的
        if (request.getUserId() == null && userId != null) {
            request.setUserId(userId);
        }
        PageResponse<TaskInfo> result = taskQueryComponent.queryCompletedTasks(request);
        return ApiResponse.success(result);
    }
}
