package com.portal.controller;

import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.ProcessComponent;
import com.portal.dto.*;
import com.portal.exception.PortalException;
import com.portal.entity.ActionDefinition;
import com.portal.entity.ProcessDraft;
import com.portal.security.CurrentUserId;
import com.platform.common.i18n.I18nService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/processes")
@RequiredArgsConstructor
@Tag(name = "流程管理", description = "流程发起和跟踪相关接口")
public class ProcessController {

    private final ProcessComponent processComponent;
    private final I18nService i18nService;
    private final FunctionUnitAccessComponent functionUnitAccessComponent;

    @GetMapping("/definitions")
    @Operation(summary = "获取可发起的流程定义列表")
    public ApiResponse<List<ProcessDefinitionInfo>> getDefinitions(
            @CurrentUserId String userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        List<ProcessDefinitionInfo> definitions = processComponent.getAvailableProcessDefinitions(userId, category, keyword);
        return ApiResponse.success(definitions);
    }
    
    @GetMapping("/startable")
    @Operation(summary = "获取可发起的流程列表", description = "获取所有已部署且启用的流程")
    public ApiResponse<List<ProcessDefinitionInfo>> getStartableProcesses(
            @CurrentUserId String userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        String effectiveUserId = userId != null ? userId : "anonymous";
        List<ProcessDefinitionInfo> definitions = processComponent.getAvailableProcessDefinitions(effectiveUserId, category, keyword);
        return ApiResponse.success(definitions);
    }
    
    @GetMapping("/function-units/{functionUnitId}/content")
    @Operation(summary = "获取功能单元完整内容", description = "获取功能单元的 BPMN、表单等完整内容；需登录且满足功能单元业务角色访问策略（与可发起列表一致）")
    public ApiResponse<Map<String, Object>> getFunctionUnitContent(
            @CurrentUserId String userId,
            @PathVariable String functionUnitId) {
        requireFunctionUnitContentAccess(userId, functionUnitId);
        Map<String, Object> content = processComponent.getFunctionUnitContent(functionUnitId);
        return ApiResponse.success(content);
    }
    
    @GetMapping("/function-units/{functionUnitId}/contents")
    @Operation(summary = "获取功能单元特定类型的内容", description = "获取功能单元的特定类型内容（如表单、流程等），用于表单弹窗等场景")
    public ApiResponse<List<Map<String, Object>>> getFunctionUnitContentsByType(
            @CurrentUserId String userId,
            @PathVariable String functionUnitId,
            @RequestParam String contentType) {
        requireFunctionUnitContentAccess(userId, functionUnitId);
        List<Map<String, Object>> contents = processComponent.getFunctionUnitContents(functionUnitId, contentType);
        return ApiResponse.success(contents);
    }
    
    /**
     * @deprecated Use GET /function-units/{functionUnitId}/contents instead
     */
    @Deprecated
    @GetMapping("/fu-data/{functionUnitId}")
    @Operation(summary = "获取功能单元特定类型的内容", description = "获取功能单元的特定类型内容（如表单、流程等），用于表单弹窗等场景")
    public ApiResponse<List<Map<String, Object>>> getFunctionUnitData(
            @CurrentUserId String userId,
            @PathVariable String functionUnitId,
            @RequestParam String contentType) {
        requireFunctionUnitContentAccess(userId, functionUnitId);
        List<Map<String, Object>> contents = processComponent.getFunctionUnitContents(functionUnitId, contentType);
        return ApiResponse.success(contents);
    }
    
    /**
     * @deprecated Use GET /function-units/{functionUnitId}/contents instead
     */
    @Deprecated
    @GetMapping("/function-unit-contents/{functionUnitId}")
    @Operation(summary = "获取功能单元特定类型的内容", description = "获取功能单元的特定类型内容（如表单、流程等），用于表单弹窗等场景")
    public ApiResponse<List<Map<String, Object>>> getFunctionUnitContents(
            @CurrentUserId String userId,
            @PathVariable String functionUnitId,
            @RequestParam String contentType) {
        requireFunctionUnitContentAccess(userId, functionUnitId);
        List<Map<String, Object>> contents = processComponent.getFunctionUnitContents(functionUnitId, contentType);
        return ApiResponse.success(contents);
    }

    private void requireFunctionUnitContentAccess(String userId, String functionUnitIdOrCode) {
        if (userId == null || userId.isBlank()) {
            throw new FunctionUnitAccessComponent.FunctionUnitAccessDeniedException("Please login first before accessing function unit content");
        }
        functionUnitAccessComponent.checkFunctionUnitAccess(userId, functionUnitIdOrCode);
    }
    
    /**
     * 处理功能单元已禁用异常
     */
    @ExceptionHandler(FunctionUnitAccessComponent.FunctionUnitDisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleFunctionUnitDisabled(FunctionUnitAccessComponent.FunctionUnitDisabledException e) {
        return ApiResponse.error("403", e.getMessage());
    }
    
    /**
     * 处理功能单元访问被拒绝异常
     */
    @ExceptionHandler(FunctionUnitAccessComponent.FunctionUnitAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleFunctionUnitAccessDenied(FunctionUnitAccessComponent.FunctionUnitAccessDeniedException e) {
        return ApiResponse.error("403", e.getMessage());
    }

    /**
     * 处理流程发起业务异常（如 BPMN 获取失败、引擎不可用等）
     */
    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleProcessException(RuntimeException e) {
        log.error("Process operation failed: {}", e.getMessage(), e);
        return ApiResponse.error("500", e.getMessage());
    }

    /**
     * 流程相关 PortalException（如发起前工作台校验失败）— 按 code 返回 4xx，便于前端展示明确提示
     */
    @ExceptionHandler(PortalException.class)
    public ApiResponse<Void> handlePortalException(PortalException e, jakarta.servlet.http.HttpServletResponse response) {
        int statusCode = switch (e.getCode()) {
            case "404" -> HttpStatus.NOT_FOUND.value();
            case "403" -> HttpStatus.FORBIDDEN.value();
            case "400" -> HttpStatus.BAD_REQUEST.value();
            default -> HttpStatus.INTERNAL_SERVER_ERROR.value();
        };
        response.setStatus(statusCode);
        log.warn("Process PortalException code={} message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @GetMapping("/actions")
    @Operation(summary = "根据ID列表获取动作定义")
    public ApiResponse<List<ActionDefinition>> getActionsByIds(
            @RequestParam List<String> ids) {
        List<ActionDefinition> actions = processComponent.getActionsByIds(ids);
        return ApiResponse.success(actions);
    }

    @PostMapping("/{processKey}/start")
    @Operation(summary = "发起流程")
    public ApiResponse<ProcessInstanceInfo> startProcess(
            @CurrentUserId String userId,
            @PathVariable String processKey,
            @RequestBody @Valid ProcessStartRequest request) {
        if (!StringUtils.hasText(request.getProcessDefinitionKey())) {
            request.setProcessDefinitionKey(processKey);
        }
        ProcessInstanceInfo instance = processComponent.startProcess(userId, processKey, request);
        return ApiResponse.success(instance);
    }

    @GetMapping("/my-applications")
    @Operation(summary = "获取我的申请列表")
    public ApiResponse<PageResponse<ProcessInstanceInfo>> getMyApplications(
            @CurrentUserId String userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (userId == null || userId.isBlank()) {
            throw new FunctionUnitAccessComponent.FunctionUnitAccessDeniedException("Please login first before viewing your applications");
        }
        int safePage = Math.max(0, page);
        int safeSize = size < 1 ? 20 : Math.min(size, 100);
        Page<ProcessInstanceInfo> result = processComponent.getMyApplications(userId, status, PageRequest.of(safePage, safeSize));
        return ApiResponse.success(PageResponse.of(result));
    }

    @GetMapping("/{processId}")
    @Operation(summary = "获取流程详情")
    public ApiResponse<ProcessInstanceInfo> getProcessDetail(
            @CurrentUserId String userId,
            @PathVariable String processId) {
        if (userId == null || userId.isBlank()) {
            throw new FunctionUnitAccessComponent.FunctionUnitAccessDeniedException(
                    "Please login first before viewing process details");
        }
        ProcessInstanceInfo detail = processComponent.getProcessDetail(processId);
        if (detail != null) {
            boolean isParticipant = processComponent.isProcessParticipant(userId, detail);
            if (!isParticipant) {
                log.warn("User {} attempted to access process {} without being a participant", userId, processId);
                return ApiResponse.error("403", "You are not a participant of this process");
            }
        }
        return ApiResponse.success(detail);
    }

    @PostMapping("/{processId}/withdraw")
    @Operation(summary = "撤回流程")
    public ApiResponse<Void> withdrawProcess(
            @CurrentUserId String userId,
            @PathVariable String processId,
            @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        boolean success = processComponent.withdrawProcess(userId, processId, reason);
        if (success) {
            return ApiResponse.success(null);
        }
        return ApiResponse.error(i18nService.getMessage("portal.withdraw_failed"));
    }

    @PostMapping("/{processId}/urge")
    @Operation(summary = "催办流程")
    public ApiResponse<Void> urgeProcess(
            @CurrentUserId String userId,
            @PathVariable String processId) {
        boolean success = processComponent.urgeProcess(userId, processId);
        if (success) {
            return ApiResponse.success(null);
        }
        return ApiResponse.error(i18nService.getMessage("portal.urge_failed"));
    }

    @PostMapping("/{processKey}/favorite")
    @Operation(summary = "切换收藏状态")
    public ApiResponse<Boolean> toggleFavorite(
            @CurrentUserId String userId,
            @PathVariable String processKey) {
        boolean isFavorite = processComponent.toggleFavorite(userId, processKey);
        return ApiResponse.success(isFavorite);
    }

    @PostMapping("/{processKey}/draft")
    @Operation(summary = "保存草稿")
    public ApiResponse<ProcessDraft> saveDraft(
            @CurrentUserId String userId,
            @PathVariable String processKey,
            @RequestBody Map<String, Object> formData) {
        ProcessDraft draft = processComponent.saveDraft(userId, processKey, formData);
        return ApiResponse.success(draft);
    }

    @GetMapping("/{processKey}/draft")
    @Operation(summary = "获取草稿")
    public ApiResponse<ProcessDraft> getDraft(
            @CurrentUserId String userId,
            @PathVariable String processKey) {
        return processComponent.getDraft(userId, processKey)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null));
    }

    @DeleteMapping("/{processKey}/draft")
    @Operation(summary = "删除草稿")
    public ApiResponse<Void> deleteDraft(
            @CurrentUserId String userId,
            @PathVariable String processKey) {
        processComponent.deleteDraft(userId, processKey);
        return ApiResponse.success(null);
    }
    
    @GetMapping("/drafts")
    @Operation(summary = "获取草稿列表")
    public ApiResponse<List<Map<String, Object>>> getDraftList(
            @CurrentUserId String userId) {
        List<Map<String, Object>> drafts = processComponent.getDraftList(userId);
        return ApiResponse.success(drafts);
    }
    
    @DeleteMapping("/drafts/{draftId}")
    @Operation(summary = "根据ID删除草稿")
    public ApiResponse<Void> deleteDraftById(
            @CurrentUserId String userId,
            @PathVariable Long draftId) {
        processComponent.deleteDraftById(userId, draftId);
        return ApiResponse.success(null);
    }
    
    @GetMapping("/{processId}/history")
    @Operation(summary = "获取流程历史记录")
    public ApiResponse<List<Map<String, Object>>> getProcessHistory(
            @CurrentUserId String userId,
            @PathVariable String processId) {
        if (userId == null || userId.isBlank()) {
            throw new FunctionUnitAccessComponent.FunctionUnitAccessDeniedException(
                    "Please login first before viewing process history");
        }
        ProcessInstanceInfo detail = processComponent.getProcessDetail(processId);
        if (detail != null) {
            boolean isParticipant = processComponent.isProcessParticipant(userId, detail);
            if (!isParticipant) {
                log.warn("User {} attempted to access process history {} without being a participant", userId, processId);
                return ApiResponse.error("403", "You are not a participant of this process");
            }
        }
        log.debug("ProcessController.getProcessHistory called with processId: {}", processId);
        List<Map<String, Object>> history = processComponent.getProcessHistory(processId);
        log.debug("ProcessController.getProcessHistory returning {} records", history.size());
        return ApiResponse.success(history);
    }
    
    @PostMapping("/{processId}/complete")
    @Operation(summary = "流程完成通知", description = "由 workflow-engine 调用，通知流程已完成")
    public ApiResponse<Void> processCompleted(
            @PathVariable String processId,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken,
            @RequestBody Map<String, Object> request) {
        if (serviceToken == null || serviceToken.isBlank()) {
            log.warn("processCompleted called without X-Internal-Service-Token for processId: {}", processId);
            return ApiResponse.error("403", "Forbidden: missing internal service token");
        }
        log.debug("ProcessController.processCompleted called for processId: {}", processId);
        String lastActivityName = (String) request.get("lastActivityName");
        processComponent.markProcessAsCompleted(processId, lastActivityName);
        return ApiResponse.success(null);
    }
}

