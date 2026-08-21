package com.portal.controller;

import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ChangeHistorySensitiveMaskResolver;
import com.portal.component.ProcessComponent;
import com.platform.common.dto.ApiResponse;
import com.portal.dto.ChangeHistoryRecord;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.security.CurrentUserId;
import com.platform.common.i18n.I18nService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Change History REST API 控制器
 * 提供流程变更历史查询
 *
 * <p>Both endpoints expose another user's request data, so they carry the same
 * participant/admin/view/audit gate as {@code GET /processes/{id}} — see
 * {@link ProcessComponent#canAuditProcessDetail(String, ProcessInstanceInfo)}.
 */
@Slf4j
@RestController
@RequestMapping("/processes")
@RequiredArgsConstructor
@Tag(name = "Change History", description = "Process change history retrieval")
public class ChangeHistoryController {

    private final ChangeHistoryComponent changeHistoryComponent;
    private final ChangeHistorySensitiveMaskResolver sensitiveMaskResolver;
    private final ProcessComponent processComponent;
    private final I18nService i18nService;

    @GetMapping("/{processInstanceId}/change-history")
    @Operation(summary = "获取流程变更历史")
    public ApiResponse<List<ChangeHistoryRecord>> getChangeHistory(
            @CurrentUserId String userId,
            @PathVariable String processInstanceId,
            @RequestParam(required = false) String rowIdentifier,
            @RequestParam(required = false) String taskId) {
        log.debug("GET /processes/{}/change-history, rowIdentifier={}, taskId={}", processInstanceId, rowIdentifier, taskId);
        if (!canReadProcess(userId, processInstanceId)) {
            return ApiResponse.error("403", i18nService.getMessage("portal.process_detail_access_denied"));
        }
        List<ChangeHistoryRecord> history = changeHistoryComponent.getChangeHistory(processInstanceId, rowIdentifier, taskId);
        return ApiResponse.success(history);
    }

    @GetMapping("/{processInstanceId}/change-history/sensitive-masks")
    @Operation(summary = "变更历史字段敏感打码配置（仅展示）")
    public ApiResponse<Map<String, Map<String, Object>>> getChangeHistorySensitiveMasks(
            @CurrentUserId String userId,
            @PathVariable String processInstanceId) {
        if (!canReadProcess(userId, processInstanceId)) {
            return ApiResponse.error("403", i18nService.getMessage("portal.process_detail_access_denied"));
        }
        return ApiResponse.success(sensitiveMaskResolver.resolveByProcessInstanceId(processInstanceId));
    }

    /**
     * Same gate as {@code ProcessController#getProcessDetail}: an unknown instance
     * stays readable (empty result) so missing data does not masquerade as a denial,
     * but a resolvable instance must pass the participant/admin/view check.
     */
    private boolean canReadProcess(String userId, String processInstanceId) {
        if (userId == null || userId.isBlank()) {
            log.warn("Anonymous attempt to read change history of process {}", processInstanceId);
            return false;
        }
        ProcessInstanceInfo detail = processComponent.getProcessDetail(processInstanceId);
        if (detail == null) {
            return true;
        }
        if (!processComponent.canAuditProcessDetail(userId, detail)) {
            log.warn("User {} attempted to read change history of process {} without detail access",
                    userId, processInstanceId);
            return false;
        }
        return true;
    }
}
