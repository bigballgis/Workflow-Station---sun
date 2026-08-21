package com.portal.controller;

import com.portal.component.ActionTableReadComponent;
import com.portal.component.ProcessComponent;
import com.portal.component.ProcessFormComponent;
import com.platform.common.dto.ApiResponse;
import com.portal.security.CurrentUserId;
import com.portal.dto.ActionTableRowsDTO;
import com.portal.dto.ProcessFormData;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.exception.PortalException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Process Form REST API 控制器
 * 提供 Process Form 布局 + 数据获取、退回状态下的更新提交
 */
@Slf4j
@RestController
@RequestMapping("/processes")
@RequiredArgsConstructor
@Tag(name = "Process Form", description = "Process Form data retrieval and update")
public class ProcessFormController {

    private final ProcessFormComponent processFormComponent;
    private final ProcessComponent processComponent;
    private final ActionTableReadComponent actionTableReadComponent;

    @GetMapping("/{processInstanceId}/action-table-rows")
    @Operation(summary = "获取该请求下所有已挂载 ACTION 表绑定的只读行数据（My Request / Application Detail 场景）")
    public ApiResponse<List<ActionTableRowsDTO>> getActionTableRows(
            @CurrentUserId String userId,
            @PathVariable String processInstanceId) {
        log.debug("GET /processes/{}/action-table-rows", processInstanceId);
        requireProcessReadAccess(userId, processInstanceId);
        return ApiResponse.success(actionTableReadComponent.getActionTableRows(processInstanceId));
    }

    @GetMapping("/{processInstanceId}/form")
    @Operation(summary = "获取 Process Form 布局 + 当前流程变量值")
    public ApiResponse<ProcessFormData> getProcessFormData(
            @CurrentUserId String userId,
            @PathVariable String processInstanceId) {
        log.debug("GET /processes/{}/form", processInstanceId);
        requireProcessReadAccess(userId, processInstanceId);
        ProcessFormData data = processFormComponent.getProcessFormData(processInstanceId);
        return ApiResponse.success(data);
    }

    @PutMapping("/{processInstanceId}/form")
    @Operation(summary = "提交 Process Form 更新（仅 Return_To_Requester 状态）")
    public ApiResponse<Void> submitProcessFormUpdate(
            @PathVariable String processInstanceId,
            @CurrentUserId String userId,
            @RequestBody Map<String, Object> formData) {
        log.debug("PUT /processes/{}/form by user {}", processInstanceId, userId);
        processFormComponent.submitProcessFormUpdate(processInstanceId, userId, formData);
        return ApiResponse.success(null);
    }

    /**
     * Process form layout carries another user's submitted values, so reading it
     * requires the same participant/admin/view/audit gate as the process detail itself.
     * An unresolvable instance is left to the component's own 404 handling rather
     * than being reported as a permission failure.
     */
    private void requireProcessReadAccess(String userId, String processInstanceId) {
        if (userId == null || userId.isBlank()) {
            log.warn("Anonymous attempt to read process form of {}", processInstanceId);
            throw new PortalException("403", "You do not have permission to view this process");
        }
        ProcessInstanceInfo detail = processComponent.getProcessDetail(processInstanceId);
        if (detail != null && !processComponent.canAuditProcessDetail(userId, detail)) {
            log.warn("User {} attempted to read process form of {} without detail access",
                    userId, processInstanceId);
            throw new PortalException("403", "You do not have permission to view this process");
        }
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
