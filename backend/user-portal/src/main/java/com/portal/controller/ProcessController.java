package com.portal.controller;

import com.portal.component.ProcessComponent;
import com.portal.dto.*;
import com.portal.entity.ProcessDraft;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/processes")
@RequiredArgsConstructor
@Tag(name = "流程管理", description = "流程发起和跟踪相关接口")
public class ProcessController {

    private final ProcessComponent processComponent;

    @GetMapping("/definitions")
    @Operation(summary = "获取可发起的流程定义列表")
    public ApiResponse<List<ProcessDefinitionInfo>> getDefinitions(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        List<ProcessDefinitionInfo> definitions = processComponent.getAvailableProcessDefinitions(userId, category, keyword);
        return ApiResponse.success(definitions);
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
        return ApiResponse.error("撤回失败");
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
        return ApiResponse.error("催办失败");
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
}
