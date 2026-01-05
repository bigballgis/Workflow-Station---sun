package com.portal.controller;

import com.portal.component.TaskProcessComponent;
import com.portal.component.TaskQueryComponent;
import com.portal.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "查询待办任务列表")
    @PostMapping("/query")
    public ApiResponse<PageResponse<TaskInfo>> queryTasks(@RequestBody TaskQueryRequest request) {
        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{taskId}")
    public ApiResponse<TaskInfo> getTaskDetail(@PathVariable String taskId) {
        TaskInfo task = taskQueryComponent.getTaskById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        return ApiResponse.success(task);
    }

    @Operation(summary = "认领任务")
    @PostMapping("/{taskId}/claim")
    public ApiResponse<TaskInfo> claimTask(
            @PathVariable String taskId,
            @RequestHeader("X-User-Id") String userId) {
        TaskInfo task = taskProcessComponent.claimTask(taskId, userId);
        return ApiResponse.success("任务认领成功", task);
    }

    @Operation(summary = "取消认领任务")
    @PostMapping("/{taskId}/unclaim")
    public ApiResponse<TaskInfo> unclaimTask(
            @PathVariable String taskId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String originalAssignmentType,
            @RequestParam String originalAssignee) {
        TaskInfo task = taskProcessComponent.unclaimTask(taskId, userId, originalAssignmentType, originalAssignee);
        return ApiResponse.success("取消认领成功", task);
    }

    @Operation(summary = "完成任务")
    @PostMapping("/{taskId}/complete")
    public ApiResponse<Void> completeTask(
            @PathVariable String taskId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody TaskCompleteRequest request) {
        request.setTaskId(taskId);
        taskProcessComponent.completeTask(request, userId);
        return ApiResponse.success("任务处理成功", null);
    }

    @Operation(summary = "委托任务")
    @PostMapping("/{taskId}/delegate")
    public ApiResponse<Void> delegateTask(
            @PathVariable String taskId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String delegateId,
            @RequestParam(required = false) String reason) {
        taskProcessComponent.delegateTask(taskId, userId, delegateId, reason);
        return ApiResponse.success("任务委托成功", null);
    }

    @Operation(summary = "转办任务")
    @PostMapping("/{taskId}/transfer")
    public ApiResponse<Void> transferTask(
            @PathVariable String taskId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String toUserId,
            @RequestParam(required = false) String reason) {
        taskProcessComponent.transferTask(taskId, userId, toUserId, reason);
        return ApiResponse.success("任务转办成功", null);
    }
}
