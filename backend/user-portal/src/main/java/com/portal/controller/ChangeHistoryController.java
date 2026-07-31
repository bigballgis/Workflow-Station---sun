package com.portal.controller;

import com.portal.component.ChangeHistoryComponent;
import com.portal.component.ChangeHistorySensitiveMaskResolver;
import com.platform.common.dto.ApiResponse;
import com.portal.dto.ChangeHistoryRecord;
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
 */
@Slf4j
@RestController
@RequestMapping("/processes")
@RequiredArgsConstructor
@Tag(name = "Change History", description = "Process change history retrieval")
public class ChangeHistoryController {

    private final ChangeHistoryComponent changeHistoryComponent;
    private final ChangeHistorySensitiveMaskResolver sensitiveMaskResolver;

    @GetMapping("/{processInstanceId}/change-history")
    @Operation(summary = "获取流程变更历史")
    public ApiResponse<List<ChangeHistoryRecord>> getChangeHistory(
            @PathVariable String processInstanceId,
            @RequestParam(required = false) String rowIdentifier,
            @RequestParam(required = false) String taskId) {
        log.debug("GET /processes/{}/change-history, rowIdentifier={}, taskId={}", processInstanceId, rowIdentifier, taskId);
        List<ChangeHistoryRecord> history = changeHistoryComponent.getChangeHistory(processInstanceId, rowIdentifier, taskId);
        return ApiResponse.success(history);
    }

    @GetMapping("/{processInstanceId}/change-history/sensitive-masks")
    @Operation(summary = "变更历史字段敏感打码配置（仅展示）")
    public ApiResponse<Map<String, Map<String, Object>>> getChangeHistorySensitiveMasks(
            @PathVariable String processInstanceId) {
        return ApiResponse.success(sensitiveMaskResolver.resolveByProcessInstanceId(processInstanceId));
    }
}
