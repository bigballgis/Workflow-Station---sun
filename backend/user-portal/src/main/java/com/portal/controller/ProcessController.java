package com.portal.controller;

import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.ProcessComponent;
import com.portal.dto.*;
import com.portal.entity.ProcessDraft;
import com.platform.common.i18n.I18nService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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

    @GetMapping("/definitions")
    @Operation(summary = "获取可发起的流程定义列表")
    public ApiResponse<List<ProcessDefinitionInfo>> getDefinitions(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        List<ProcessDefinitionInfo> definitions = processComponent.getAvailableProcessDefinitions(userId, category, keyword);
        return ApiResponse.success(definitions);
    }
    
    @GetMapping("/startable")
    @Operation(summary = "获取可发起的流程列表", description = "获取所有已部署且启用的流程")
    public ApiResponse<List<ProcessDefinitionInfo>> getStartableProcesses(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        String effectiveUserId = userId != null ? userId : "anonymous";
        List<ProcessDefinitionInfo> definitions = processComponent.getAvailableProcessDefinitions(effectiveUserId, category, keyword);
        return ApiResponse.success(definitions);
    }
    
    @GetMapping("/function-units/{functionUnitId}/content")
    @Operation(summary = "获取功能单元完整内容", description = "获取功能单元的BPMN流程、表单定义等完整内容。注意：此端点不检查功能单元访问权限，因为任务处理权限由任务分配机制控制")
    public ApiResponse<Map<String, Object>> getFunctionUnitContent(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String functionUnitId) {
        // 不检查功能单元访问权限，因为：
        // 1. 任务处理权限由 Flowable 的任务分配机制控制（assignee、候选人、候选组）
        // 2. 功能单元权限只应控制"谁可以发起流程"，而不是"谁可以处理任务"
        // 3. 用户能看到任务，说明任务已经分配给他或他所在的组
        Map<String, Object> content = processComponent.getFunctionUnitContent(functionUnitId);
        return ApiResponse.success(content);
    }
    
    @GetMapping("/function-units/{functionUnitId}/contents")
    @Operation(summary = "获取功能单元特定类型的内容", description = "获取功能单元的特定类型内容（如表单、流程等），用于表单弹窗等场景")
    public ApiResponse<List<Map<String, Object>>> getFunctionUnitContentsByType(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String functionUnitId,
            @RequestParam String contentType) {
        List<Map<String, Object>> contents = processComponent.getFunctionUnitContents(functionUnitId, contentType);
        return ApiResponse.success(contents);
    }
    
    @GetMapping("/fu-data/{functionUnitId}")
    @Operation(summary = "获取功能单元特定类型的内容", description = "获取功能单元的特定类型内容（如表单、流程等），用于表单弹窗等场景")
    public ApiResponse<List<Map<String, Object>>> getFunctionUnitData(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String functionUnitId,
            @RequestParam String contentType) {
        List<Map<String, Object>> contents = processComponent.getFunctionUnitContents(functionUnitId, contentType);
        return ApiResponse.success(contents);
    }
    
    @GetMapping("/function-unit-contents/{functionUnitId}")
    @Operation(summary = "获取功能单元特定类型的内容", description = "获取功能单元的特定类型内容（如表单、流程等），用于表单弹窗等场景")
    public ApiResponse<List<Map<String, Object>>> getFunctionUnitContents(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String functionUnitId,
            @RequestParam String contentType) {
        List<Map<String, Object>> contents = processComponent.getFunctionUnitContents(functionUnitId, contentType);
        return ApiResponse.success(contents);
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

    @PostMapping("/{processKey}/start")
    @Operation(summary = "发起流程")
    public ApiResponse<ProcessInstanceInfo> startProcess(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String processKey,
            @RequestBody ProcessStartRequest request) {
        ProcessInstanceInfo instance = processComponent.startProcess(userId, processKey, request);
        return ApiResponse.success(instance);
    }

    @GetMapping("/my-applications")
    @Operation(summary = "获取我的申请列表")
    public ApiResponse<PageResponse<ProcessInstanceInfo>> getMyApplications(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ProcessInstanceInfo> result = processComponent.getMyApplications(userId, status, PageRequest.of(page, size));
        return ApiResponse.success(PageResponse.of(result));
    }

    @GetMapping("/{processId}")
    @Operation(summary = "获取流程详情")
    public ApiResponse<ProcessInstanceInfo> getProcessDetail(@PathVariable String processId) {
        ProcessInstanceInfo detail = processComponent.getProcessDetail(processId);
        return ApiResponse.success(detail);
    }

    @PostMapping("/{processId}/withdraw")
    @Operation(summary = "撤回流程")
    public ApiResponse<Void> withdrawProcess(
            @RequestHeader("X-User-Id") String userId,
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
            @RequestHeader("X-User-Id") String userId,
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
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String processKey) {
        boolean isFavorite = processComponent.toggleFavorite(userId, processKey);
        return ApiResponse.success(isFavorite);
    }

    @PostMapping("/{processKey}/draft")
    @Operation(summary = "保存草稿")
    public ApiResponse<ProcessDraft> saveDraft(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String processKey,
            @RequestBody Map<String, Object> formData) {
        ProcessDraft draft = processComponent.saveDraft(userId, processKey, formData);
        return ApiResponse.success(draft);
    }

    @GetMapping("/{processKey}/draft")
    @Operation(summary = "获取草稿")
    public ApiResponse<ProcessDraft> getDraft(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String processKey) {
        return processComponent.getDraft(userId, processKey)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null));
    }

    @DeleteMapping("/{processKey}/draft")
    @Operation(summary = "删除草稿")
    public ApiResponse<Void> deleteDraft(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String processKey) {
        processComponent.deleteDraft(userId, processKey);
        return ApiResponse.success(null);
    }
    
    @GetMapping("/drafts")
    @Operation(summary = "获取草稿列表")
    public ApiResponse<List<Map<String, Object>>> getDraftList(
            @RequestHeader("X-User-Id") String userId) {
        List<Map<String, Object>> drafts = processComponent.getDraftList(userId);
        return ApiResponse.success(drafts);
    }
    
    @DeleteMapping("/drafts/{draftId}")
    @Operation(summary = "根据ID删除草稿")
    public ApiResponse<Void> deleteDraftById(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long draftId) {
        processComponent.deleteDraftById(userId, draftId);
        return ApiResponse.success(null);
    }
    
    @GetMapping("/{processId}/history")
    @Operation(summary = "获取流程历史记录")
    public ApiResponse<List<Map<String, Object>>> getProcessHistory(
            @PathVariable String processId) {
        log.info("=== ProcessController.getProcessHistory called with processId: {}", processId);
        List<Map<String, Object>> history = processComponent.getProcessHistory(processId);
        log.info("=== ProcessController.getProcessHistory returning {} records", history.size());
        return ApiResponse.success(history);
    }
    
    @PostMapping("/{processId}/complete")
    @Operation(summary = "流程完成通知", description = "由 workflow-engine 调用，通知流程已完成")
    public ApiResponse<Void> processCompleted(
            @PathVariable String processId,
            @RequestBody Map<String, Object> request) {
        log.info("=== ProcessController.processCompleted called for processId: {}", processId);
        String lastActivityName = (String) request.get("lastActivityName");
        processComponent.markProcessAsCompleted(processId, lastActivityName);
        return ApiResponse.success(null);
    }
}

