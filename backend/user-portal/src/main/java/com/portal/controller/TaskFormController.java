package com.portal.controller;

import com.portal.component.ActionFormPopupSubmitComponent;
import com.portal.component.ActionTableReadComponent;
import com.portal.component.TaskFormComponent;
import com.portal.component.TaskProcessComponent;
import com.portal.component.TaskQueryComponent;
import com.platform.common.dto.ApiResponse;
import com.portal.dto.ActionTableRowsDTO;
import com.portal.dto.TaskInfo;
import com.portal.security.CurrentUserId;
import com.platform.security.util.SecurityContextUtils;
import com.portal.dto.ActionFormPopupSubmitRequest;
import com.portal.dto.CompletedTaskFormData;
import com.portal.dto.TaskFormData;
import com.portal.dto.TaskFormSubmitRequest;
import com.portal.exception.PortalException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task Form REST API 控制器
 * 提供 Task Form 数据获取、提交、已完成任务快照查询
 */
@Slf4j
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@Tag(name = "Task Form", description = "Task Form data retrieval, submission, and completed task snapshots")
public class TaskFormController {

    private final TaskFormComponent taskFormComponent;
    private final TaskQueryComponent taskQueryComponent;
    private final TaskProcessComponent taskProcessComponent;
    private final ActionFormPopupSubmitComponent actionFormPopupSubmitComponent;
    private final ActionTableReadComponent actionTableReadComponent;

    @GetMapping("/{taskId}/form-data")
    @Operation(summary = "获取 Task Form 布局 + 当前流程变量值（字段子集）")
    public ApiResponse<TaskFormData> getTaskFormData(
            @PathVariable String taskId,
            @CurrentUserId String userId) {
        log.debug("GET /tasks/{}/form-data", taskId);
        requireTaskFormAccess(taskId, userId);
        TaskFormData data = taskFormComponent.getTaskFormData(taskId);
        return ApiResponse.success(data);
    }

    @PostMapping("/{taskId}/submit")
    @Operation(summary = "提交 Task Form 数据")
    public ApiResponse<Void> submitTaskForm(
            @PathVariable String taskId,
            @CurrentUserId String userId,
            @Valid @RequestBody TaskFormSubmitRequest request) {
        log.debug("POST /tasks/{}/submit by user {}", taskId, userId);
        TaskInfo task = requireTaskFormAccess(taskId, userId);
        if (!taskProcessComponent.canProcessTask(task, userId,
                SecurityContextUtils.getCurrentUsername().orElse(null))) {
            throw new PortalException("403", "You do not have permission to submit this task form");
        }
        Map<String, Object> formData = new HashMap<>(request.getFormData());
        if (request.getSubTableData() != null && !request.getSubTableData().isEmpty()) {
            Map<String, Object> subTables = new HashMap<>();
            Object existing = formData.get("__subTables__");
            if (existing instanceof Map<?, ?> existingMap) {
                existingMap.forEach((key, value) -> subTables.put(String.valueOf(key), value));
            }
            request.getSubTableData().forEach(subTables::put);
            formData.put("__subTables__", subTables);
        }
        taskFormComponent.submitTaskForm(taskId, userId, formData, request.getBaselineValues(),
                request.getEmptiedSubTableKeys());
        return ApiResponse.success(null);
    }

    @PostMapping("/{taskId}/actions/{actionId}/form-popup-submit")
    @Operation(summary = "提交 FORM_POPUP 动作表单数据（写入其绑定的 ACTION 表）")
    public ApiResponse<Void> submitActionFormPopup(
            @PathVariable String taskId,
            @PathVariable String actionId,
            @CurrentUserId String userId,
            @Valid @RequestBody ActionFormPopupSubmitRequest request) {
        log.debug("POST /tasks/{}/actions/{}/form-popup-submit by user {}", taskId, actionId, userId);
        TaskInfo task = requireTaskFormAccess(taskId, userId);
        if (!taskProcessComponent.canProcessTask(task, userId,
                SecurityContextUtils.getCurrentUsername().orElse(null))) {
            throw new PortalException("403", "You do not have permission to submit this action form");
        }
        actionFormPopupSubmitComponent.submit(task, actionId, request.getFormData(), userId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{taskId}/action-table-rows")
    @Operation(summary = "获取当前请求下所有已挂载 ACTION 表绑定的只读行数据（如 Meeting Remark 历史记录）")
    public ApiResponse<List<ActionTableRowsDTO>> getActionTableRows(
            @PathVariable String taskId,
            @CurrentUserId String userId) {
        log.debug("GET /tasks/{}/action-table-rows", taskId);
        TaskInfo task = requireTaskFormAccess(taskId, userId);
        return ApiResponse.success(actionTableReadComponent.getActionTableRows(task));
    }

    @GetMapping("/{taskId}/completed-form")
    @Operation(summary = "获取已完成 Task 的快照 + 实时值")
    public ApiResponse<CompletedTaskFormData> getCompletedTaskFormData(
            @PathVariable String taskId,
            @CurrentUserId String userId) {
        log.debug("GET /tasks/{}/completed-form", taskId);
        requireTaskFormAccess(taskId, userId);
        CompletedTaskFormData data = taskFormComponent.getCompletedTaskFormData(taskId);
        return ApiResponse.success(data);
    }

    /** 校验当前用户是否可查看该任务表单（含发起人/assignee/处理权限）。 */
    private TaskInfo requireTaskFormAccess(String taskId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new PortalException("403", "Authentication required");
        }
        TaskInfo task = taskQueryComponent.getTaskById(taskId)
                .orElseThrow(() -> new PortalException("404", "Task not found: " + taskId));
        if (!taskProcessComponent.canViewTaskForm(task, userId,
                SecurityContextUtils.getCurrentUsername().orElse(null))) {
            throw new PortalException("403", "You do not have permission to access this task form");
        }
        return task;
    }

    /**
     * 处理 PortalException — 根据 code 返回对应 HTTP 状态码
     */
    @ExceptionHandler(PortalException.class)
    public ApiResponse<Void> handlePortalException(PortalException e,
            jakarta.servlet.http.HttpServletResponse response) {
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
